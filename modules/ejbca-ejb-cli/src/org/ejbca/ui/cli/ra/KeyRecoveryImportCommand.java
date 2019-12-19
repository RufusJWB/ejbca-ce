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

package org.ejbca.ui.cli.ra;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Enumeration;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.ca.IllegalNameException;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.certificate.CertificateStoreSessionRemote;
import org.cesecore.certificates.certificate.exception.CertificateSerialNumberException;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.configuration.GlobalConfigurationSessionRemote;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.util.CertTools;
import org.cesecore.util.EJBTools;
import org.cesecore.util.EjbRemoteHelper;
import org.cesecore.util.FileTools;
import org.ejbca.config.GlobalConfiguration;
import org.ejbca.core.ejb.keyrecovery.KeyRecoverySessionRemote;
import org.ejbca.core.ejb.ra.EndEntityExistsException;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.ra.CustomFieldException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileNotFoundException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileValidationException;
import org.ejbca.ui.cli.infrastructure.command.CommandResult;
import org.ejbca.ui.cli.infrastructure.parameter.Parameter;
import org.ejbca.ui.cli.infrastructure.parameter.ParameterContainer;
import org.ejbca.ui.cli.infrastructure.parameter.enums.MandatoryMode;
import org.ejbca.ui.cli.infrastructure.parameter.enums.ParameterMode;
import org.ejbca.ui.cli.infrastructure.parameter.enums.StandaloneMode;


/**
 * Import key recovery data (private key and certificate) for an end entity, either an exiting end entity or adding a new end entity.
 *
 * @version $Id: KeyRecoveryImport.java
 */
public class KeyRecoveryImportCommand extends BaseRaCommand {

    private static final Logger log = Logger.getLogger(KeyRecoveryImportCommand.class);

    private static final String FILE_KEY = "-f";
    private static final String EE_PROFILE_KEY = "--eeprofile";
    private static final String CERT_PROFILE_KEY = "--certprofile";
    private static final String CA_NAME_KEY = "--caname";
    private static final String USERNAME_KEY = "--username";
    private static final String PASSWORD_KEY = "--password";

    {
        registerParameter(new Parameter(FILE_KEY, "Keystore file", MandatoryMode.MANDATORY, StandaloneMode.FORBID, ParameterMode.ARGUMENT,
                "Keystore file with the private key and certificate to import. Must be a PKCS#12 (p12) file."));
        registerParameter(new Parameter(PASSWORD_KEY, "Password", MandatoryMode.MANDATORY, StandaloneMode.FORBID, ParameterMode.ARGUMENT,
                "Password for the keystore file."));
        registerParameter(new Parameter(CA_NAME_KEY, "CA Name", MandatoryMode.MANDATORY, StandaloneMode.FORBID, ParameterMode.ARGUMENT,
                "Name of the CA that issued the certificate to be imported. THe CA must be an operational CA in this EJBCA instance and have a keyEncryptKey configured, used to encrypt the imported private key."));
        registerParameter(new Parameter(EE_PROFILE_KEY, "Profile Name", MandatoryMode.OPTIONAL, StandaloneMode.FORBID, ParameterMode.ARGUMENT,
                "End Entity Profile to create end entity with. If no profile is specified then the EMPTY profile will be used."));
        registerParameter(new Parameter(CERT_PROFILE_KEY, "Profile Name", MandatoryMode.OPTIONAL, StandaloneMode.FORBID, ParameterMode.ARGUMENT,
                "Certificate Profile to create end entity with. If no profile specified then the ENDUSER profile will be used."));
        registerParameter(new Parameter(USERNAME_KEY, "Username", MandatoryMode.OPTIONAL, StandaloneMode.FORBID, ParameterMode.ARGUMENT,
                "Username for the new end entity to create, or an existing end entity to add key recovery data to. Optional, if left out the username is randomized (creating a new end entity) in the form of 'CN'+_+(20 random alphanumeric characters). If CN does not exist UID or SERIALNUMBER or just 'user' is used, in order of existence."));
    }

    @Override
    public String getMainCommand() {
        return "keyrecoveryimport";
    }

    @Override
    public CommandResult execute(ParameterContainer parameters) { 
        final boolean usekeyrecovery = ((GlobalConfiguration) EjbRemoteHelper.INSTANCE.getRemoteSession(GlobalConfigurationSessionRemote.class)
                .getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID)).getEnableKeyRecovery();
        if (!usekeyrecovery) {
            getLogger().error("Keyrecovery have to be enabled in the system configuration in order to use this command, see System Configuration.");
            return CommandResult.FUNCTIONAL_FAILURE;
        }

        final String p12File = parameters.get(FILE_KEY);
        final String caname = parameters.get(CA_NAME_KEY);
        final String eeprofile = parameters.get(EE_PROFILE_KEY);
        final String certificateprofile = parameters.get(CERT_PROFILE_KEY);
        final String password = parameters.get(PASSWORD_KEY);
        String username = parameters.get(USERNAME_KEY);

        try {
            final byte[] p12Bytes = loadcertbytes(p12File);
            // load keystore
            final KeyStore keystore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
            keystore.load(new java.io.ByteArrayInputStream(p12Bytes), password.toCharArray());
            final Enumeration<String> en = keystore.aliases();
            String privateKeyAlias = null;
            while (en.hasMoreElements()) {
                final String alias = en.nextElement();
                if (privateKeyAlias != null && keystore.isKeyEntry(alias)) {
                    getLogger().error("Keystore contains more than one private key.");
                    return CommandResult.CLI_FAILURE;
                }
                if (keystore.isKeyEntry(alias)) {
                    privateKeyAlias = alias;
                    getLogger().debug("Found a private key alias in keystore: " + privateKeyAlias);                    
                }
            }
            if (privateKeyAlias == null) {
                getLogger().error("Keystore does not contain any private key aliases.");
                return CommandResult.CLI_FAILURE;                
            }
            final Certificate[] certChain = KeyTools.getCertChain(keystore, privateKeyAlias);
            if (certChain == null) {
                getLogger().error("Cannot load any certificate chain with alias: " + privateKeyAlias);
                return CommandResult.CLI_FAILURE;
            }

            final PrivateKey p12PrivateKey = (PrivateKey) keystore.getKey(privateKeyAlias, password.toCharArray());
            getLogger().info("Found private key with algorithm: " + p12PrivateKey.getAlgorithm());

            // Validate parameters for end entity profile, certificate profile and CA
            final StringBuilder errorString = new StringBuilder();
            int endentityprofileid = EndEntityConstants.EMPTY_END_ENTITY_PROFILE;
            if (eeprofile != null) {
                getLogger().debug("Searching for End Entity Profile: " + eeprofile);
                try {
                    endentityprofileid = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityProfileSessionRemote.class).getEndEntityProfileId(eeprofile);
                } catch (EndEntityProfileNotFoundException e) {
                    errorString.append("End Entity Profile '" + eeprofile + "' does not exist.\n");
                }
            }

            int certificateprofileid = CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER;
            if (certificateprofile != null) {
                getLogger().debug("Searching for Certificate Profile " + certificateprofile);
                certificateprofileid = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateProfileSessionRemote.class).getCertificateProfileId(
                        certificateprofile);
                if (certificateprofileid == CertificateProfileConstants.CERTPROFILE_NO_PROFILE) {
                    getLogger().error("Certificate Profile " + certificateprofile + " does not exist.");
                    errorString.append("Certificate Profile '" + certificateprofile + "' does not exist.\n");
                }
            }

            final CAInfo cainfo = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(getAuthenticationToken(), caname);
            if (cainfo == null) {
                getLogger().error("CA with name " + caname + " does not exist.");
                errorString.append("CA with name '" + caname + "' does not exist.\n");
            }

            if (errorString.length() > 0) {
                getLogger().error(errorString.toString());
                // Some parameters were correct, i.e. non existing profile or CA
                return CommandResult.CLI_FAILURE; 
            }

            final Certificate userCertificate = certChain[0];
            boolean randomUser = false;
            if (username == null) {
                getLogger().info("No username parameter supplied, creating a randomized username based on CN UID, SERIALNUMBER, or 'user', in order of existence.");
                final String seq = RandomStringUtils.randomAlphanumeric(20);
                String userPart = CertTools.getPartFromDN(CertTools.getSubjectDN(userCertificate), "CN");
                if (userPart == null) {
                    userPart = CertTools.getPartFromDN(CertTools.getSubjectDN(userCertificate), "UID");                    
                    if (userPart == null) {
                        userPart = CertTools.getPartFromDN(CertTools.getSubjectDN(userCertificate), "SERIALNUMBER");                    
                        if (userPart == null) {
                            userPart = "user"; 
                        }
                    }
                }
                username = userPart + "_" + seq;
                randomUser = true;
            }
            getLogger().info("Trying to add key recovery data to end entity with username: " + username);
            final EndEntityManagementSessionRemote endEntityManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);
            if (endEntityManagementSession.existsUser(username)) {
                if (!randomUser) {
                    // If we specified a username, add key recovery to the existing user if it did not exist 
                    getLogger().info("Username specified already exists, will try to add key recovery data to the existing end entity: " + username);
                } else {
                    getLogger().error("End entity with random username already exists, this is an odd fluke that should not happen: " + username);
                    return CommandResult.FUNCTIONAL_FAILURE;
                }
            } else {
                getLogger().info("Adding new end entity with username: " + username);
                final EndEntityInformation userdata = new EndEntityInformation(username, CertTools.getSubjectDN(userCertificate), cainfo.getCAId(), CertTools.getSubjectAlternativeName(userCertificate), 
                        CertTools.getEMailAddress(userCertificate), EndEntityConstants.STATUS_GENERATED, new EndEntityType(EndEntityTypes.ENDUSER), endentityprofileid, certificateprofileid, null,
                        null, SecConst.TOKEN_SOFT_P12, null);
                final String randompwd = RandomStringUtils.randomAlphanumeric(20);
                userdata.setPassword(randompwd);
                userdata.setKeyRecoverable(true);
                endEntityManagementSession.addUser(getAuthenticationToken(), userdata, false);                
                getLogger().info("End entity '" + username + "' has been added.");
            }

            final int crlPartitionIndex = cainfo.determineCrlPartitionIndex(userCertificate);
            final Certificate cacert = cainfo.getCertificateChain().iterator().next();
            final CertificateStoreSessionRemote certStoreSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateStoreSessionRemote.class);
            final String fingerprint = CertTools.getFingerprintAsString(userCertificate);
            if (certStoreSession.getCertificateInfo(fingerprint) != null) {
                getLogger().info("End entity certificate with fingerprint '" + fingerprint + "' already exist in the database, will not try to add it.");
            } else {
                getLogger().info("Adding end entity certificate with fingerprint '" + fingerprint + "' to the database.");
                certStoreSession.storeCertificateRemote(getAuthenticationToken(), EJBTools.wrap(userCertificate),
                        username, CertTools.getFingerprintAsString(cacert), CertificateConstants.CERT_ACTIVE, CertificateConstants.CERTTYPE_ENDENTITY, 
                        certificateprofileid, endentityprofileid, crlPartitionIndex, null, new Date().getTime());
            }
            final KeyRecoverySessionRemote keyRecoverySession = EjbRemoteHelper.INSTANCE.getRemoteSession(KeyRecoverySessionRemote.class);
            final PublicKey p12PublicKey = userCertificate.getPublicKey();
            final KeyPair keypair1 = new KeyPair(p12PublicKey, p12PrivateKey);
            if(!keyRecoverySession.addKeyRecoveryData(getAuthenticationToken(), EJBTools.wrap(userCertificate), username, EJBTools.wrap(keypair1))) {
                getLogger().error("Key recovery data for user '" + username + "' could not be added to the database because it already exists.");
                return CommandResult.FUNCTIONAL_FAILURE;
            }
            // All went well
            return CommandResult.SUCCESS;
        } catch (IOException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | CertificateException nsae) {
            getLogger().error("Unable to read provided PKCS#12 file: " + nsae.getMessage());
        } catch (AuthorizationDeniedException ade) {
            getLogger().error("CLI user is not authorized to add and entity with the configured values: " + ade.getMessage());
        } catch (IllegalNameException | EndEntityProfileValidationException eepve) {
            getLogger().error("The certificate Subject DN or alternative name does not fulfil requirements of the configured profiles: " + eepve.getMessage());
        } catch (EndEntityExistsException | CustomFieldException | CertificateSerialNumberException eepve) {
            // EndEntityExistsException here should only happen if there is something wrong, since we already check above in the code if it exists before trying to add it
            getLogger().error("Error adding end entity: " + eepve.getMessage());
        } catch (WaitingForApprovalException | ApprovalException eepve) {
            getLogger().error("Approvals are needed to add end entities with the configured profiles: " + eepve.getMessage());
        } catch (CADoesntExistsException eepve) {
            getLogger().error("The configured CA does not exist: " + eepve.getMessage());
        } catch (NoSuchProviderException nspe) {
            getLogger().error("ERROR, BouncyCastle provider does not exist: " + nspe.getMessage());
        } 
        return CommandResult.FUNCTIONAL_FAILURE;
    }

    /**
     * 
     * @param filename path to a file containing a PEM encoded certificate
     * @return the certificate
     * 
     * @throws IOException if the file didn't contain the certificate keys.
     * @throws CertificateException if the read PEM couldn't be decoded to a certificate
     * @throws FileNotFoundException if file wasn't found
     */
    private byte[] loadcertbytes(String filename) throws FileNotFoundException {
        final File certfile = new File(filename);
        if (!certfile.exists()) {
            throw new FileNotFoundException("'" + filename + "' does not exist.");
        }
        if (!certfile.isFile()) {
            throw new FileNotFoundException("'" + filename + "' is not a file.");
        }
        final byte[] bytes = FileTools.readFiletoBuffer(filename);
        return bytes;
    }

    @Override
    public String getCommandDescription() {
        return "Imports key recovery data for an end entity";
    }

    @Override
    public String getFullHelpText() {
        return getCommandDescription();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}