/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.keybind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.Serializable;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.X509CAInfo;
import org.cesecore.certificates.certificate.CertificateCreateSessionRemote;
import org.cesecore.certificates.certificate.InternalCertificateStoreSessionRemote;
import org.cesecore.certificates.certificate.request.PKCS10RequestMessage;
import org.cesecore.certificates.certificate.request.RequestMessage;
import org.cesecore.certificates.certificate.request.SimpleRequestMessage;
import org.cesecore.certificates.certificate.request.X509ResponseMessage;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.junit.util.CryptoTokenRunner;
import org.cesecore.keybind.impl.OcspKeyBinding;
import org.cesecore.keybinding.TestInternalKeyBindingMgmtSessionRemote;
import org.cesecore.keys.token.CryptoTokenManagementSessionRemote;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.util.EjbRemoteHelper;
import org.cesecore.util.TraceLogMethodsRule;
import org.cesecore.util.ui.DynamicUiProperty;
import org.ejbca.core.ejb.ca.sign.SignSessionRemote;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.keyfactor.util.CertTools;
import com.keyfactor.util.certificate.DnComponents;
import com.keyfactor.util.crypto.algorithm.AlgorithmConstants;
import com.keyfactor.util.keys.KeyTools;
import com.keyfactor.util.keys.token.KeyGenParams;

/**
 * @see InternalKeyBindingMgmtSession
 */
@RunWith(Parameterized.class)
public class InternalKeyBindingMgmtTest {

    @Parameters(name = "{0}")
    public static Collection<CryptoTokenRunner> runners() {
       return CryptoTokenRunner.defaultRunners;
    }
    
    private static final Logger log = Logger.getLogger(InternalKeyBindingMgmtTest.class);
    private static final AuthenticationToken alwaysAllowToken = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal(InternalKeyBindingMgmtTest.class.getSimpleName()));
    private static final CryptoTokenManagementSessionRemote cryptoTokenManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CryptoTokenManagementSessionRemote.class);
    private static final InternalKeyBindingMgmtSessionRemote internalKeyBindingMgmtSession = EjbRemoteHelper.INSTANCE.getRemoteSession(InternalKeyBindingMgmtSessionRemote.class);
    private static final CertificateCreateSessionRemote certificateCreateSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateCreateSessionRemote.class);
    private static final SignSessionRemote signSession = EjbRemoteHelper.INSTANCE.getRemoteSession(SignSessionRemote.class);
    private static final EndEntityManagementSessionRemote endEntityManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);

    private static final TestInternalKeyBindingMgmtSessionRemote testInternalKeyBindingMgmtSession = 
                            EjbRemoteHelper.INSTANCE.getRemoteSession(TestInternalKeyBindingMgmtSessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    private static final CertificateProfileSessionRemote certProfileSession = 
                            EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateProfileSessionRemote.class);
    private static final EndEntityProfileSessionRemote endEntityProfileSession = 
                            EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityProfileSessionRemote.class);
    
    private static final InternalCertificateStoreSessionRemote internalCertStoreSession = 
                            EjbRemoteHelper.INSTANCE.getRemoteSession(InternalCertificateStoreSessionRemote.class, EjbRemoteHelper.MODULE_TEST);

    private static final String TESTCLASSNAME = InternalKeyBindingMgmtTest.class.getSimpleName();
    private static final String KEYBINDING_TYPE_ALIAS = OcspKeyBinding.IMPLEMENTATION_ALIAS;
    private static final String PROPERTY_ALIAS = OcspKeyBinding.PROPERTY_NON_EXISTING_GOOD;
    
    private static final String CERT_PROFILE_OCSP = "OcspCertProfile" + TESTCLASSNAME;
    private static final String CERT_PROFILE_ENDUSER = "EndUserCertProfile" + TESTCLASSNAME;
    private static final String END_ENTITY_PROFILE = "EeProfile" + TESTCLASSNAME;

    @Rule
    public TestRule traceLogMethodsRule = new TraceLogMethodsRule();
    
    private X509CAInfo x509ca;
    private int cryptoTokenId;
    private int endUserCertProfileId;
    private int ocspCertProfileId;
    private int endEntityProfileId;
    
    private CryptoTokenRunner cryptoTokenRunner;
    
    @Rule
    public TestName testName = new TestName();

    public InternalKeyBindingMgmtTest(CryptoTokenRunner cryptoTokenRunner) throws Exception {
        this.cryptoTokenRunner = cryptoTokenRunner;
       
    }
    
    @Before
    public void before() throws Throwable {
        assumeTrue("Test with runner " + cryptoTokenRunner.getSimpleName() + " cannot run on this platform.", cryptoTokenRunner.canRun());
        x509ca = cryptoTokenRunner.createX509Ca("CN="+testName.getMethodName(), testName.getMethodName()); 
        cryptoTokenId = x509ca.getCAToken().getCryptoTokenId();
        
        final Collection<Integer> availCas = new ArrayList<Integer>();
        availCas.add(x509ca.getCAId());
        
        CertificateProfile profile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        endUserCertProfileId = certProfileSession.addCertificateProfile(alwaysAllowToken, 9991234, CERT_PROFILE_ENDUSER, profile);

        profile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_OCSPSIGNER);
        ocspCertProfileId = certProfileSession.addCertificateProfile(alwaysAllowToken, 9991235, CERT_PROFILE_OCSP, profile);
        
        final Collection<Integer> availCertProfiles = new ArrayList<Integer>();
        availCertProfiles.add(endUserCertProfileId);
        availCertProfiles.add(ocspCertProfileId);
        final EndEntityProfile eeprofile = new EndEntityProfile();
        eeprofile.setAvailableCAs(availCas);
        eeprofile.setAvailableCertificateProfileIds(availCertProfiles);
        endEntityProfileId = endEntityProfileSession.addEndEntityProfile(alwaysAllowToken, END_ENTITY_PROFILE, eeprofile);
        
    }
    
    @After
    public void afterClass() {
        cryptoTokenRunner.cleanUp();
        
        try {
            certProfileSession.removeCertificateProfile(alwaysAllowToken, CERT_PROFILE_ENDUSER);
        } catch (Exception e) { }
        
        try {
            certProfileSession.removeCertificateProfile(alwaysAllowToken, CERT_PROFILE_OCSP);
        } catch (Exception e) { }
        
        try {
            endEntityProfileSession.removeEndEntityProfile(alwaysAllowToken, END_ENTITY_PROFILE);
        } catch (Exception e) { }
    }
   
        
    @Test
    public void assertTestPreRequisites() throws Exception {
        // Request all available implementations from server and verify that the implementation we intend to use exists
        final Map<String, Map<String, DynamicUiProperty<? extends Serializable>>> availableTypesAndProperties = internalKeyBindingMgmtSession
                .getAvailableTypesAndProperties();
        final Map<String, DynamicUiProperty<? extends Serializable>> availableProperties = availableTypesAndProperties
                .get(KEYBINDING_TYPE_ALIAS);
        assertNotNull("Expected " + KEYBINDING_TYPE_ALIAS + " to exist on the server for this test.", availableProperties);
        // Verify that a property we intend to modify exists for our key binding implementation
        assertTrue("Expected property " + PROPERTY_ALIAS + " in " + KEYBINDING_TYPE_ALIAS + " to exist on the server for this test.",
                availableProperties.containsKey(PROPERTY_ALIAS));
    }

    @Test
    public void activationNotPossibleWithoutCertificateReference() throws Exception {
        final String TEST_METHOD_NAME = Thread.currentThread().getStackTrace()[1].getMethodName();
        final String KEY_BINDING_NAME = TEST_METHOD_NAME;
        final String KEY_PAIR_ALIAS = TEST_METHOD_NAME;
        removeInternalKeyBindingByName(alwaysAllowToken, TEST_METHOD_NAME);
        int internalKeyBindingId = 0;
        try {
            // First create a new CryptoToken
            cryptoTokenManagementSession.createKeyPair(alwaysAllowToken, cryptoTokenId, KEY_PAIR_ALIAS, KeyGenParams.builder("RSA1024").build());
            // Create a new InternalKeyBinding with a implementation specific property and bind it to the previously generated key
            final Map<String, Serializable> dataMap = new LinkedHashMap<String, Serializable>();
            dataMap.put(PROPERTY_ALIAS, Boolean.FALSE);
            internalKeyBindingId = internalKeyBindingMgmtSession.createInternalKeyBinding(alwaysAllowToken, KEYBINDING_TYPE_ALIAS,
                    KEY_BINDING_NAME, InternalKeyBindingStatus.ACTIVE, null, cryptoTokenId, KEY_PAIR_ALIAS, AlgorithmConstants.SIGALG_SHA1_WITH_RSA, dataMap, null);
            // Check that the status is not ACTIVE, despite our request (since no certificate reference was provided)
            final InternalKeyBinding internalKeyBinding = internalKeyBindingMgmtSession.getInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            assertEquals("Creation of active IKB with without a certificate reference was allowed.", InternalKeyBindingStatus.DISABLED.name(),
                       internalKeyBinding.getStatus().name());
            internalKeyBinding.setStatus(InternalKeyBindingStatus.ACTIVE);
            internalKeyBindingMgmtSession.persistInternalKeyBinding(alwaysAllowToken, internalKeyBinding);
            final InternalKeyBinding internalKeyBindingUpdated = internalKeyBindingMgmtSession.getInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            assertEquals("Update of active IKB with without a certificate reference was allowed.", InternalKeyBindingStatus.DISABLED.name(),
                    internalKeyBindingUpdated.getStatus().name());
        } finally {
            internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
        }
        
    }

    @Test
    public void workflowIssueCertFromPublicKeyAndUpdate() throws Exception {
        final String TEST_METHOD_NAME = Thread.currentThread().getStackTrace()[1].getMethodName();
        final String KEY_BINDING_NAME = TEST_METHOD_NAME;
        final String KEY_BINDING_NAME1 = TEST_METHOD_NAME+"1";
        final String KEY_BINDING_NAME2 = TEST_METHOD_NAME+"2";
        final String KEY_PAIR_ALIAS = TEST_METHOD_NAME;
        // Clean up old key binding
        removeInternalKeyBindingByName(alwaysAllowToken, TEST_METHOD_NAME);
        int internalKeyBindingId = 0;
        int internalKeyBindingId1 = 0;
        int internalKeyBindingId2 = 0;
        String certFpToDelete = null;
        try {
            // First create a new CryptoToken
            cryptoTokenManagementSession.createKeyPair(alwaysAllowToken, cryptoTokenId, KEY_PAIR_ALIAS, KeyGenParams.builder("RSA1024").build());
            // Create a new InternalKeyBinding with a implementation specific property and bind it to the previously generated key
            final Map<String, Serializable> dataMap = new LinkedHashMap<String, Serializable>();
            dataMap.put(PROPERTY_ALIAS, Boolean.FALSE);
            internalKeyBindingId = internalKeyBindingMgmtSession.createInternalKeyBinding(alwaysAllowToken, KEYBINDING_TYPE_ALIAS,
                    KEY_BINDING_NAME, InternalKeyBindingStatus.ACTIVE, null, cryptoTokenId, KEY_PAIR_ALIAS, AlgorithmConstants.SIGALG_SHA1_WITH_RSA, dataMap, null);
            // Get the public key for the key pair currently used in the binding
            PublicKey publicKey = KeyTools.getPublicKeyFromBytes(internalKeyBindingMgmtSession.getNextPublicKeyForInternalKeyBinding(alwaysAllowToken, internalKeyBindingId));
            // Issue a certificate in EJBCA for the public key
            final EndEntityInformation user = new EndEntityInformation(TESTCLASSNAME+"_" + TEST_METHOD_NAME, "CN="+TESTCLASSNAME +"_" + TEST_METHOD_NAME, x509ca.getCAId(), null, null,
                    EndEntityTypes.ENDUSER.toEndEntityType(), 1, CertificateProfileConstants.CERTPROFILE_FIXED_OCSPSIGNER,
                    EndEntityConstants.TOKEN_USERGEN, null);
            user.setPassword("foo123");
            RequestMessage req = new SimpleRequestMessage(publicKey, user.getUsername(), user.getPassword());            
            X509Certificate keyBindingCertificate = (X509Certificate) (((X509ResponseMessage) certificateCreateSession.createCertificate(alwaysAllowToken, user, req,
                    X509ResponseMessage.class, signSession.fetchCertGenParams())).getCertificate());
            certFpToDelete = CertTools.getFingerprintAsString(keyBindingCertificate);
            // Ask the key binding to search the database for a new certificate matching its public key
            final String boundCertificateFingerprint = internalKeyBindingMgmtSession.updateCertificateForInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            // Verify that it was the right certificate it found
            assertEquals("Wrong certificate was found for InternalKeyBinding", CertTools.getFingerprintAsString(keyBindingCertificate), boundCertificateFingerprint);
            // ...so now we have a mapping between a certificate in the database and a key pair in a CryptoToken
            
            // Try to make a new key binding giving the certificate fingerprint directly
            internalKeyBindingId1 = internalKeyBindingMgmtSession.createInternalKeyBinding(alwaysAllowToken, KEYBINDING_TYPE_ALIAS,
                    KEY_BINDING_NAME1, InternalKeyBindingStatus.ACTIVE, CertTools.getFingerprintAsString(keyBindingCertificate), cryptoTokenId, KEY_PAIR_ALIAS, AlgorithmConstants.SIGALG_SHA1_WITH_RSA, dataMap, null);
            InternalKeyBindingInfo info = internalKeyBindingMgmtSession.getInternalKeyBindingInfo(alwaysAllowToken, internalKeyBindingId1);
            assertEquals("Wrong certificate was found for InternalKeyBinding", CertTools.getFingerprintAsString(keyBindingCertificate), info.getCertificateId());

            // Try to make a new key binding giving the certificate fingerprint directly, but in upper case instead of the default lower case
            internalKeyBindingId2 = internalKeyBindingMgmtSession.createInternalKeyBinding(alwaysAllowToken, KEYBINDING_TYPE_ALIAS,
                    KEY_BINDING_NAME2, InternalKeyBindingStatus.ACTIVE, CertTools.getFingerprintAsString(keyBindingCertificate).toUpperCase(Locale.ENGLISH), cryptoTokenId, KEY_PAIR_ALIAS, AlgorithmConstants.SIGALG_SHA1_WITH_RSA, dataMap, null);
            info = internalKeyBindingMgmtSession.getInternalKeyBindingInfo(alwaysAllowToken, internalKeyBindingId2);
            assertEquals("Wrong certificate was found for InternalKeyBinding", CertTools.getFingerprintAsString(keyBindingCertificate), info.getCertificateId());
        } finally { 
            internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, internalKeyBindingId1);
            internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, internalKeyBindingId2);
            internalCertStoreSession.removeCertificate(certFpToDelete);
        }
    }

    @Test
    public void workflowIssueCertFromCsrUpdateAndRenew() throws Exception {
        final String TEST_METHOD_NAME = Thread.currentThread().getStackTrace()[1].getMethodName();
        final String KEY_BINDING_NAME = TEST_METHOD_NAME;
        final String KEY_PAIR_ALIAS = TEST_METHOD_NAME;
        final String endEntityId = TESTCLASSNAME+"_" + TEST_METHOD_NAME;
        // Clean up old key binding
        removeInternalKeyBindingByName(alwaysAllowToken, TEST_METHOD_NAME);
        int internalKeyBindingId = 0;
        String certFpToDelete = null;
        try {
            // First create a new CryptoToken
            cryptoTokenManagementSession.createKeyPair(alwaysAllowToken, cryptoTokenId, KEY_PAIR_ALIAS, KeyGenParams.builder("RSA1024").build());
            // Create a new InternalKeyBinding with a implementation specific property and bind it to the previously generated key
            final Map<String, Serializable> dataMap = new LinkedHashMap<String, Serializable>();
            dataMap.put(PROPERTY_ALIAS, Boolean.FALSE);
            internalKeyBindingId = internalKeyBindingMgmtSession.createInternalKeyBinding(alwaysAllowToken, KEYBINDING_TYPE_ALIAS,
                    KEY_BINDING_NAME, InternalKeyBindingStatus.ACTIVE, null, cryptoTokenId, KEY_PAIR_ALIAS, AlgorithmConstants.SIGALG_SHA1_WITH_RSA, dataMap, null);
            // Add a user to EJBCA for the renewal later on
            final EndEntityInformation endEntityInformation = new EndEntityInformation(endEntityId, "CN="+TESTCLASSNAME +"_" + TEST_METHOD_NAME, x509ca.getCAId(), null, null,
                    EndEntityTypes.ENDUSER.toEndEntityType(), 1, CertificateProfileConstants.CERTPROFILE_FIXED_OCSPSIGNER,
                    EndEntityConstants.TOKEN_USERGEN, null);
            endEntityInformation.setPassword("foo123");
            // Request a CSR for the key pair
            // First make a couple of requests with different DN to see that that part works
            final X500Name x500name = DnComponents.stringToBcX500Name("CN=name,O=org,C=SE", false);
            final byte[] csr = internalKeyBindingMgmtSession.generateCsrForNextKey(alwaysAllowToken, internalKeyBindingId, x500name.getEncoded());
            final JcaPKCS10CertificationRequest jcareq = new JcaPKCS10CertificationRequest(csr);
            assertEquals("Wrong order of DN, should be X500 with C first", "C=SE,O=org,CN=name", jcareq.getSubject().toString());
            final X500Name x500name2 = DnComponents.stringToBcX500Name("CN=name,O=org,C=SE", true);
            final byte[] csr2 = internalKeyBindingMgmtSession.generateCsrForNextKey(alwaysAllowToken, internalKeyBindingId, x500name2.getEncoded());
            final JcaPKCS10CertificationRequest jcareq2 = new JcaPKCS10CertificationRequest(csr2);
            assertEquals("Wrong order of DN, should be LDAP with CN first", "CN=name,O=org,C=SE", jcareq2.getSubject().toString());
            // Now make the request that we will actually use
            final byte[] csr3 = internalKeyBindingMgmtSession.generateCsrForNextKey(alwaysAllowToken, internalKeyBindingId, null);
            final RequestMessage req = new PKCS10RequestMessage(csr3);
            assertEquals("CN="+KEY_BINDING_NAME, req.getRequestDN());
            X509Certificate keyBindingCertificate = (X509Certificate) (((X509ResponseMessage) certificateCreateSession.createCertificate(alwaysAllowToken, endEntityInformation, req,
                    X509ResponseMessage.class, signSession.fetchCertGenParams())).getCertificate());
            certFpToDelete = CertTools.getFingerprintAsString(keyBindingCertificate);
            // Ask the key binding to search the database for a new certificate matching its public key
            final String boundCertificateFingerprint = internalKeyBindingMgmtSession.updateCertificateForInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            // Verify that it was the right certificate it found
            assertEquals("Wrong certificate was found for InternalKeyBinding", CertTools.getFingerprintAsString(keyBindingCertificate), boundCertificateFingerprint);
            // ...so now we have a mapping between a certificate in the database and a key pair in a CryptoToken
            // Since we no have a certificate issued by an internal CA, we should be able to renew it
            final String renewedCertificateFingerprint = internalKeyBindingMgmtSession.renewInternallyIssuedCertificate(alwaysAllowToken, internalKeyBindingId, endEntityInformation);
            assertNotNull("Renewal returned null which is an undefined state.", renewedCertificateFingerprint);
            assertFalse("After certificate renewal the same certificate was returned",
                    boundCertificateFingerprint.equals(renewedCertificateFingerprint));
            final String actualCertificateFingerprint = internalKeyBindingMgmtSession.getInternalKeyBindingInfo(alwaysAllowToken, internalKeyBindingId).getCertificateId();
            assertFalse("After certificate renewal the same certificate still in use.",
                    boundCertificateFingerprint.equals(actualCertificateFingerprint));
            // Check DN in generated CSR when we have a bound certificate, should be the DN of the old certificate
            final byte[] csr4 = internalKeyBindingMgmtSession.generateCsrForNextKey(alwaysAllowToken, internalKeyBindingId, null);
            final JcaPKCS10CertificationRequest jcareq4 = new JcaPKCS10CertificationRequest(csr4);
            assertEquals("Wrong DN, should be from the bound certificate", "CN="+TESTCLASSNAME +"_" + TEST_METHOD_NAME, jcareq4.getSubject().toString());
        } finally {
            internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            internalCertStoreSession.removeCertificate(certFpToDelete);
        }
    }

    @Test
    public void workflowIssueCertFromCsrAndImport() throws Exception {
        final String TEST_METHOD_NAME = Thread.currentThread().getStackTrace()[1].getMethodName();
        final String KEY_BINDING_NAME = TEST_METHOD_NAME;
        final String KEY_PAIR_ALIAS = TEST_METHOD_NAME;
        // Clean up old key binding
        removeInternalKeyBindingByName(alwaysAllowToken, TEST_METHOD_NAME);
        int internalKeyBindingId = 0;
        String certFpToDelete = null;
        try {
            // First create a new CryptoToken
            cryptoTokenManagementSession.createKeyPair(alwaysAllowToken, cryptoTokenId, KEY_PAIR_ALIAS, KeyGenParams.builder("RSA1024").build());
            internalKeyBindingId = internalKeyBindingMgmtSession.createInternalKeyBinding(alwaysAllowToken, KEYBINDING_TYPE_ALIAS,
                    KEY_BINDING_NAME, InternalKeyBindingStatus.ACTIVE, null, cryptoTokenId, KEY_PAIR_ALIAS, AlgorithmConstants.SIGALG_SHA1_WITH_RSA, null, null);
            log.debug("Created InternalKeyBinding with id " + internalKeyBindingId);
            // Request a CSR for the key pair
            final byte[] csr = internalKeyBindingMgmtSession.generateCsrForNextKey(alwaysAllowToken, internalKeyBindingId, DnComponents.stringToBcX500Name("CN="+KEY_BINDING_NAME+",O=workflow", true).getEncoded());
            // Issue a certificate in EJBCA for the public key
            final EndEntityInformation user = new EndEntityInformation(TESTCLASSNAME+"_" + TEST_METHOD_NAME, "CN="+TESTCLASSNAME +"_" + TEST_METHOD_NAME, x509ca.getCAId(), null, null,
                    EndEntityTypes.ENDUSER.toEndEntityType(), 1, CertificateProfileConstants.CERTPROFILE_FIXED_OCSPSIGNER,
                    EndEntityConstants.TOKEN_USERGEN, null);
            user.setPassword("foo123");
            RequestMessage req = new PKCS10RequestMessage(csr);
            assertEquals("CN="+KEY_BINDING_NAME+",O=workflow", req.getRequestDN());
            X509Certificate keyBindingCertificate = (X509Certificate) (((X509ResponseMessage) certificateCreateSession.createCertificate(alwaysAllowToken, user, req,
                    X509ResponseMessage.class, signSession.fetchCertGenParams())).getCertificate());
            certFpToDelete = CertTools.getFingerprintAsString(keyBindingCertificate);
            // Import the issued certificate (since it is already in the database, only the pointer will be updated)
            internalKeyBindingMgmtSession.importCertificateForInternalKeyBinding(alwaysAllowToken, internalKeyBindingId, keyBindingCertificate.getEncoded());
            // Fetch the InternalKeyBinding's current certificate mapping
            String boundCertificateFingerprint = internalKeyBindingMgmtSession.getInternalKeyBindingInfo(alwaysAllowToken, internalKeyBindingId).getCertificateId();
            // Verify that it was the right certificate it found
            assertEquals("Wrong certificate was found for InternalKeyBinding", CertTools.getFingerprintAsString(keyBindingCertificate), boundCertificateFingerprint);
            // ...so now we have a mapping between a certificate in the database and a key pair in a CryptoToken
            // A final check that the CSR's subject is normally based on the existing certs for renewals
            final byte[] csr2 = internalKeyBindingMgmtSession.generateCsrForNextKey(alwaysAllowToken, internalKeyBindingId, null);
            assertEquals("CN="+TESTCLASSNAME +"_" + TEST_METHOD_NAME, new PKCS10RequestMessage(csr2).getRequestDN());
        } finally {
            internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            internalCertStoreSession.removeCertificate(certFpToDelete);
        }
    }

    private void removeInternalKeyBindingByName(AuthenticationToken authenticationToken, String name) throws AuthorizationDeniedException {
        // Clean up old key binding
        final Integer oldInternalKeyBindingId = internalKeyBindingMgmtSession.getIdFromName(name);
        if (oldInternalKeyBindingId != null && internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, oldInternalKeyBindingId)) {
            log.info("Removed keybinding with name " + name + ".");
        }
    }
    
    private void createRemoteInternalKeybindingAndActivateTest(
            String certProfileName, String keyAliasName, String keySpec, String keyBindingType) throws Exception {
        final String TEST_METHOD_NAME = Thread.currentThread().getStackTrace()[1].getMethodName() + new Random().nextLong();
        final String KEY_BINDING_NAME = TEST_METHOD_NAME;
        String KEY_PAIR_ALIAS = TEST_METHOD_NAME;
        if (keyAliasName!=null) {
            KEY_PAIR_ALIAS = keyAliasName;
        }
        // Clean up old key binding
        removeInternalKeyBindingByName(alwaysAllowToken, TEST_METHOD_NAME);
        int internalKeyBindingId = 0;
        String certFpToDelete = null;
        String subjectDn = "CN=check" + KEY_BINDING_NAME;
        try {
            internalKeyBindingId = testInternalKeyBindingMgmtSession.createInternalKeyBindingWithOptionalEnrollmentInfo(
                    alwaysAllowToken, keyBindingType,
                    internalKeyBindingId, KEY_BINDING_NAME, InternalKeyBindingStatus.DISABLED, null, cryptoTokenId, KEY_PAIR_ALIAS, 
                    false, AlgorithmConstants.SIGALG_SHA256_WITH_RSA, null, null, 
                    subjectDn, x509ca.getSubjectDN(), 
                    certProfileName, END_ENTITY_PROFILE, keySpec);
            log.error("Created InternalKeyBinding with id " + internalKeyBindingId);
                        
            EndEntityInformation endEntity = new EndEntityInformation(
                    KEY_BINDING_NAME, subjectDn, x509ca.getCAId(), 
                    null, null, new EndEntityType(EndEntityTypes.ENDUSER), 
                    endEntityProfileId, 
                    certProfileName.equals(CERT_PROFILE_ENDUSER) ? endUserCertProfileId : ocspCertProfileId, 
                    EndEntityConstants.TOKEN_USERGEN, null);
            endEntity.setPassword("dummy");
            endEntity.setStatus(EndEntityConstants.STATUS_NEW);                
            endEntity = endEntityManagementSession.addUser(alwaysAllowToken, endEntity, false);
            
            testInternalKeyBindingMgmtSession.issueCertificateForInternalKeyBinding(alwaysAllowToken, internalKeyBindingId,
                    endEntity, keySpec);
            
            InternalKeyBinding fetchedKeyBinding = 
                    internalKeyBindingMgmtSession.getInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            
            assertNotNull(fetchedKeyBinding.getCertificateId());
            certFpToDelete = fetchedKeyBinding.getCertificateId();
            assertEquals(fetchedKeyBinding.getStatus(), InternalKeyBindingStatus.ACTIVE);
            
        } finally {
            internalKeyBindingMgmtSession.deleteInternalKeyBinding(alwaysAllowToken, internalKeyBindingId);
            internalCertStoreSession.removeCertificate(certFpToDelete);
            endEntityManagementSession.deleteUser(alwaysAllowToken, KEY_BINDING_NAME);
            cryptoTokenManagementSession.removeKeyPair(alwaysAllowToken, cryptoTokenId, KEY_PAIR_ALIAS);
        }
    }
    
    
    @Test
    public void createRemoteInternalKeybindingAndActivate() throws Exception {
        createRemoteInternalKeybindingAndActivateTest(CERT_PROFILE_ENDUSER, null, "RSA3072", "AuthenticationKeyBinding");
    }
    
    @Test
    public void createRemoteInternalKeybindingAndActivateWithExistingKeypair() throws Exception {
        String keyPairAlias = Thread.currentThread().getStackTrace()[1].getMethodName() + new Random().nextLong();
        cryptoTokenManagementSession.createKeyPair(alwaysAllowToken, cryptoTokenId, 
                                                keyPairAlias, KeyGenParams.builder("RSA2048").build());
        createRemoteInternalKeybindingAndActivateTest(CERT_PROFILE_ENDUSER, keyPairAlias, "RSA2048", "AuthenticationKeyBinding");
    }
    
    @Test
    public void createOcspInternalKeybindingAndActivate() throws Exception {
        createRemoteInternalKeybindingAndActivateTest(CERT_PROFILE_OCSP, null, "secp256r1", KEYBINDING_TYPE_ALIAS);
    }

}
