/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.ui.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.Vector;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.jce.X509Principal;
import org.ejbca.core.model.ca.catoken.CATokenConstants;
import org.ejbca.util.CertTools;
import org.ejbca.util.PerformanceTest;
import org.ejbca.util.PerformanceTest.Command;
import org.ejbca.util.PerformanceTest.CommandFactory;
import org.ejbca.util.keystore.KeyTools;

import com.novosec.pkix.asn1.cmp.CMPObjectIdentifiers;
import com.novosec.pkix.asn1.cmp.CertConfirmContent;
import com.novosec.pkix.asn1.cmp.CertOrEncCert;
import com.novosec.pkix.asn1.cmp.CertRepMessage;
import com.novosec.pkix.asn1.cmp.CertResponse;
import com.novosec.pkix.asn1.cmp.CertifiedKeyPair;
import com.novosec.pkix.asn1.cmp.PKIBody;
import com.novosec.pkix.asn1.cmp.PKIHeader;
import com.novosec.pkix.asn1.cmp.PKIMessage;
import com.novosec.pkix.asn1.cmp.PKIStatusInfo;
import com.novosec.pkix.asn1.crmf.AttributeTypeAndValue;
import com.novosec.pkix.asn1.crmf.CRMFObjectIdentifiers;
import com.novosec.pkix.asn1.crmf.CertReqMessages;
import com.novosec.pkix.asn1.crmf.CertReqMsg;
import com.novosec.pkix.asn1.crmf.CertRequest;
import com.novosec.pkix.asn1.crmf.CertTemplate;
import com.novosec.pkix.asn1.crmf.OptionalValidity;
import com.novosec.pkix.asn1.crmf.PBMParameter;
import com.novosec.pkix.asn1.crmf.POPOSigningKey;
import com.novosec.pkix.asn1.crmf.ProofOfPossession;

/**
 * Used to test the EJBCA health check servet.
 * @author primelars
 * @version $Id: HealthCheckTest.java 6106 2008-08-21 08:43:43Z primelars $
 *
 */
class CMPTest extends ClientToolBox {
    static private class StressTest {
        final PerformanceTest performanceTest;

        private static final String PBEPASSWORD = "password";
        private static final String httpReqPath = "http://127.0.0.1:8080/ejbca";
        private static final String resourceCmp = "publicweb/cmp";
        final private KeyPair keyPair = KeyTools.genKeys("512", CATokenConstants.KEYALGORITHM_RSA);
        final private X509Certificate cacert;
        final Random random = new Random();

        StressTest( final String caCertFileName,
                    final int numberOfThreads,
                    final int waitTime) throws Exception {
            CertificateFactory cf = CertTools.getCertificateFactory("BC");
            this.cacert = (X509Certificate)cf.generateCertificate(new FileInputStream(caCertFileName));
            this.performanceTest = new PerformanceTest();
            this.performanceTest.execute(new MyCommandFactory(), numberOfThreads, waitTime, System.out);
        }
        private PKIMessage genCertReq(SessionData sessionData, boolean raVerifiedPopo, X509Extensions extensions) throws NoSuchAlgorithmException, NoSuchProviderException, IOException, InvalidKeyException, SignatureException {
            final OptionalValidity myOptionalValidity = new OptionalValidity();
            myOptionalValidity.setNotBefore( new org.bouncycastle.asn1.x509.Time( new DERGeneralizedTime("20030211002120Z") ) );
            myOptionalValidity.setNotAfter( new org.bouncycastle.asn1.x509.Time(new Date()) );

            final CertTemplate myCertTemplate = new CertTemplate();
            myCertTemplate.setValidity( myOptionalValidity );
            myCertTemplate.setIssuer(new X509Name(this.cacert.getSubjectDN().getName()));
            myCertTemplate.setSubject(new X509Name(sessionData.getUserDN()));
            final byte[]                  bytes = this.keyPair.getPublic().getEncoded();
            final ByteArrayInputStream    bIn = new ByteArrayInputStream(bytes);
            final ASN1InputStream         dIn = new ASN1InputStream(bIn);
            final SubjectPublicKeyInfo keyInfo = new SubjectPublicKeyInfo((ASN1Sequence)dIn.readObject());
            myCertTemplate.setPublicKey(keyInfo);
            // If we did not pass any extensions as parameter, we will create some of our own, standard ones
            if (extensions == null) {
                // SubjectAltName
                // Some altNames
                final Vector<X509Extension> values = new Vector<X509Extension>();
                final Vector<DERObjectIdentifier> oids = new Vector<DERObjectIdentifier>();
                {
                    final GeneralNames san = CertTools.getGeneralNamesFromAltName("UPN=fooupn@bar.com,rfc822Name=fooemail@bar.com");
                    final ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
                    final DEROutputStream         dOut = new DEROutputStream(bOut);
                    dOut.writeObject(san);
                    final byte value[] = bOut.toByteArray();
                    values.add(new X509Extension(false, new DEROctetString(value)));
                    oids.add(X509Extensions.SubjectAlternativeName);
                }{
                    // KeyUsage
                    final int bcku = X509KeyUsage.digitalSignature | X509KeyUsage.keyEncipherment | X509KeyUsage.nonRepudiation;
                    final X509KeyUsage ku = new X509KeyUsage(bcku);
                    final ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                    final DEROutputStream dOut = new DEROutputStream(bOut);
                    dOut.writeObject(ku);
                    final byte value[] = bOut.toByteArray();
                    final X509Extension kuext = new X509Extension(false, new DEROctetString(value));
                    values.add(kuext);
                    oids.add(X509Extensions.KeyUsage);     
                }
                // Make the complete extension package
                myCertTemplate.setExtensions(new X509Extensions(oids, values));
            } else
                myCertTemplate.setExtensions(extensions);

            final CertRequest myCertRequest = new CertRequest(new DERInteger(4), myCertTemplate);

            final CertReqMsg myCertReqMsg = new CertReqMsg(myCertRequest);

            ProofOfPossession myProofOfPossession;
            if (raVerifiedPopo) {
                // raVerified POPO (meaning there is no POPO)
                myProofOfPossession = new ProofOfPossession(new DERNull(), 0);
            } else {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final DEROutputStream mout = new DEROutputStream( baos );
                mout.writeObject( myCertRequest );
                mout.close();
                final byte[] popoProtectionBytes = baos.toByteArray();
                final Signature sig = Signature.getInstance( PKCSObjectIdentifiers.sha1WithRSAEncryption.getId(), "BC");
                sig.initSign(this.keyPair.getPrivate());
                sig.update( popoProtectionBytes );

                final DERBitString bs = new DERBitString(sig.sign());

                final POPOSigningKey myPOPOSigningKey =
                    new POPOSigningKey(
                            new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption),
                            bs);
                //myPOPOSigningKey.setPoposkInput( myPOPOSigningKeyInput );
                myProofOfPossession = new ProofOfPossession(myPOPOSigningKey, 1);           
            }

            myCertReqMsg.setPop(myProofOfPossession);

            final AttributeTypeAndValue av = new AttributeTypeAndValue(CRMFObjectIdentifiers.regCtrl_regToken, new DERUTF8String("foo123")); 
            myCertReqMsg.addRegInfo(av);

            final CertReqMessages myCertReqMessages = new CertReqMessages(myCertReqMsg);

            final PKIHeader myPKIHeader =
                new PKIHeader( new DERInteger(2),
                               new GeneralName(new X509Name(sessionData.getUserDN())),
                               new GeneralName(new X509Name(this.cacert.getSubjectDN().getName())) );
            myPKIHeader.setMessageTime(new DERGeneralizedTime(new Date()));
            myPKIHeader.setSenderNonce(new DEROctetString(sessionData.getNonce()));
            myPKIHeader.setTransactionID(new DEROctetString(sessionData.getTransId()));

            final PKIBody myPKIBody = new PKIBody(myCertReqMessages, 0); // initialization request
            return new PKIMessage(myPKIHeader, myPKIBody);   
        }
        protected PKIMessage protectPKIMessage(PKIMessage msg, boolean badObjectId, String password) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
            return protectPKIMessage(msg, badObjectId, password, "primekey");
        }
        
        protected PKIMessage protectPKIMessage(PKIMessage msg, boolean badObjectId, String password, String keyId) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
            // SHA1
            final AlgorithmIdentifier owfAlg = new AlgorithmIdentifier("1.3.14.3.2.26");
            // 567 iterations
            final int iterationCount = 567;
            // HMAC/SHA1
            final AlgorithmIdentifier macAlg = new AlgorithmIdentifier("1.2.840.113549.2.7");
            final byte[] salt = "foo123".getBytes();
            final DEROctetString derSalt = new DEROctetString(salt);
            final PKIMessage ret; {
                // Create the PasswordBased protection of the message
                final PKIHeader head = msg.getHeader();
                head.setSenderKID(new DEROctetString(keyId.getBytes()));
                final DERInteger iteration = new DERInteger(iterationCount);

                // Create the new protected return message
                String objectId = "1.2.840.113533.7.66.13";
                if (badObjectId) {
                    objectId += ".7";
                }
                final PBMParameter pp = new PBMParameter(derSalt, owfAlg, iteration, macAlg);
                final AlgorithmIdentifier pAlg = new AlgorithmIdentifier(new DERObjectIdentifier(objectId), pp);
                head.setProtectionAlg(pAlg);

                final PKIBody body = msg.getBody();
                ret = new PKIMessage(head, body);
            }
            {
                // Calculate the protection bits
                final byte[] raSecret = password.getBytes();
                byte basekey[] = new byte[raSecret.length + salt.length];
                for (int i = 0; i < raSecret.length; i++) {
                    basekey[i] = raSecret[i];
                }
                for (int i = 0; i < salt.length; i++) {
                    basekey[raSecret.length+i] = salt[i];
                }
                // Construct the base key according to rfc4210, section 5.1.3.1
                final MessageDigest dig = MessageDigest.getInstance(owfAlg.getObjectId().getId(), "BC");
                for (int i = 0; i < iterationCount; i++) {
                    basekey = dig.digest(basekey);
                    dig.reset();
                }
                // For HMAC/SHA1 there is another oid, that is not known in BC, but the result is the same so...
                final String macOid = macAlg.getObjectId().getId();
                final byte[] protectedBytes = ret.getProtectedBytes();
                final Mac mac = Mac.getInstance(macOid, "BC");
                final SecretKey key = new SecretKeySpec(basekey, macOid);
                mac.init(key);
                mac.reset();
                mac.update(protectedBytes, 0, protectedBytes.length);
                final byte[] out = mac.doFinal();
                final DERBitString bs = new DERBitString(out);

                // Finally store the protection bytes in the msg
                ret.setProtection(bs);
            }
            return ret;
        }
        protected byte[] sendCmpHttp(byte[] message) throws IOException {
            // POST the CMP request
            // we are going to do a POST
            final String resource = resourceCmp;
            final String urlString = httpReqPath + '/' + resource;
            final URL url = new URL(urlString);
            final HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-type", "application/pkixcmp");
            con.connect();
            // POST it
            final OutputStream os = con.getOutputStream();
            os.write(message);
            os.close();

            if ( con.getResponseCode()!= 200 ) {
                StressTest.this.performanceTest.getLog().error("Wrong http resonse code:"+con.getResponseCode());
                return null;
            }
            // Some appserver (Weblogic) responds with "application/pkixcmp; charset=UTF-8"
            if ( !con.getContentType().startsWith("application/pkixcmp") )
                StressTest.this.performanceTest.getLog().info("wrong content type: "+con.getContentType());
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // This works for small requests, and CMP requests are small enough
            final InputStream in = con.getInputStream();
            int b = in.read();
            while (b != -1) {
                baos.write(b);
                b = in.read();
            }
            baos.flush();
            in.close();
            final byte[] respBytes = baos.toByteArray();
            if (respBytes.length <= 0) {
                StressTest.this.performanceTest.getLog().error("No response.");                
            }
            return respBytes;
        }
        private boolean checkCmpResponseGeneral(byte[] retMsg, SessionData sessionData, boolean signed, boolean pbe) throws Exception {
            //
            // Parse response message
            //
            final PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(retMsg)).readObject());
            if ( respObject==null ) {
                StressTest.this.performanceTest.getLog().error("No command response message.");
                return false;
            }
            
            // The signer, i.e. the CA, check it's the right CA
            final PKIHeader header = respObject.getHeader();
            if ( header==null ) {
                StressTest.this.performanceTest.getLog().error("No header in response message.");
                return false;
            }

            // Check that the message is signed with the correct digest alg
            if (signed) {
                final AlgorithmIdentifier algId = header.getProtectionAlg();
                if ( algId==null || !algId.getObjectId().getId().equals(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId()) )
                    StressTest.this.performanceTest.getLog().error("Wrong signing algorithm used.");
            }
            if (pbe) {
                final AlgorithmIdentifier algId = header.getProtectionAlg();
                if ( algId==null || !algId.getObjectId().getId().equals(CMPObjectIdentifiers.passwordBasedMac.getId()) )
                    StressTest.this.performanceTest.getLog().error("Wrong password.");
            }

            // Check that the signer is the expected CA
            X509Name name = X509Name.getInstance(header.getSender().getName()); 
            if ( header.getSender().getTagNo()!=4 || name==null || !name.equals(this.cacert.getSubjectDN()) )
                StressTest.this.performanceTest.getLog().error("Not signed by right issuer.");

            if (signed) {
                // Verify the signature
                byte[] protBytes = respObject.getProtectedBytes();
                final DERBitString bs = respObject.getProtection();
                final Signature sig;
                try {
                    sig = Signature.getInstance(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId(), "BC");
                    sig.initVerify(this.cacert);
                    sig.update(protBytes);
                    if ( !sig.verify(bs.getBytes()) )
                        StressTest.this.performanceTest.getLog().error("CA signature not verifying");
                } catch ( Exception e) {
                    StressTest.this.performanceTest.getLog().error("Not possible to verify signature.", e);
                }           
            }
            if (pbe) {
                DEROctetString os = header.getSenderKID();
                if ( os!=null )
                    StressTest.this.performanceTest.getLog().info("Found a sender keyId: "+new String(os.getOctets()));
                // Verify the PasswordBased protection of the message
                final PBMParameter pp; {
                    AlgorithmIdentifier pAlg = header.getProtectionAlg();
                    StressTest.this.performanceTest.getLog().info("Protection type is: "+pAlg.getObjectId().getId());
                    pp = PBMParameter.getInstance(pAlg.getParameters());
                }
                final int iterationCount = pp.getIterationCount().getPositiveValue().intValue();
                StressTest.this.performanceTest.getLog().info("Iteration count is: "+iterationCount);
                final AlgorithmIdentifier owfAlg = pp.getOwf();
                // Normal OWF alg is 1.3.14.3.2.26 - SHA1
                StressTest.this.performanceTest.getLog().info("Owf type is: "+owfAlg.getObjectId().getId());
                final AlgorithmIdentifier macAlg = pp.getMac();
                // Normal mac alg is 1.3.6.1.5.5.8.1.2 - HMAC/SHA1
                StressTest.this.performanceTest.getLog().info("Mac type is: "+macAlg.getObjectId().getId());
                final byte[] salt = pp.getSalt().getOctets();
                //log.info("Salt is: "+new String(salt));
                String raAuthenticationSecret = "password";
                final byte[] raSecret = raAuthenticationSecret.getBytes();
                // HMAC/SHA1 os normal 1.3.6.1.5.5.8.1.2 or 1.2.840.113549.2.7 
                final String macOid = macAlg.getObjectId().getId();
                final SecretKey key; {
                    byte[] basekey = new byte[raSecret.length + salt.length];
                    for (int i = 0; i < raSecret.length; i++) {
                        basekey[i] = raSecret[i];
                    }
                    for (int i = 0; i < salt.length; i++) {
                        basekey[raSecret.length+i] = salt[i];
                    }
                    // Construct the base key according to rfc4210, section 5.1.3.1
                    final MessageDigest dig = MessageDigest.getInstance(owfAlg.getObjectId().getId(), "BC");
                    for (int i = 0; i < iterationCount; i++) {
                        basekey = dig.digest(basekey);
                        dig.reset();
                    }
                    key = new SecretKeySpec(basekey, macOid);
                }
                final Mac mac = Mac.getInstance(macOid, "BC");
                mac.init(key);
                mac.reset();
                final byte[] protectedBytes = respObject.getProtectedBytes();
                final DERBitString protection = respObject.getProtection();
                mac.update(protectedBytes, 0, protectedBytes.length);
                byte[] out = mac.doFinal();
                // My out should now be the same as the protection bits
                byte[] pb = protection.getBytes();
                if ( !Arrays.equals(out, pb) )
                    StressTest.this.performanceTest.getLog().error("Wrong PBE hash");
                return true;
            }

            // --Recipient Nonce
            // recipient nonce should be the same as we sent away as sender nonce
            if ( !Arrays.equals(header.getSenderNonce().getOctets(), sessionData.getNonce()) )
                StressTest.this.performanceTest.getLog().error("recipient nonce not the same as we sent away as the sender nonce");

            // --Transaction ID
            // transid should be the same as the one we sent
            if ( !Arrays.equals(header.getTransactionID().getOctets(), sessionData.getTransId()) )
                StressTest.this.performanceTest.getLog().error("transid is not the same as the one we sent");
            return true;
        }
        private X509Certificate checkCmpCertRepMessage(SessionData sessionData, byte[] retMsg, int requestId) throws IOException, CertificateException {
            //
            // Parse response message
            //
            final PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(retMsg)).readObject());
            if ( respObject==null ) {
                StressTest.this.performanceTest.getLog().error("No PKIMessage for certificate received.");
                return null;
            }
            final PKIBody body = respObject.getBody();
            if ( body==null ) {
                StressTest.this.performanceTest.getLog().error("No PKIBody for certificate received.");
                return null;
            }
            if ( body.getTagNo()!=1 )
                StressTest.this.performanceTest.getLog().error("Cert body tag not 1.");
            final CertRepMessage c = body.getIp();
            if ( c==null ) {
                StressTest.this.performanceTest.getLog().error("No CertRepMessage for certificate received.");
                return null;
            }
            final CertResponse resp = c.getResponse(0);
            if ( resp==null ) {
                StressTest.this.performanceTest.getLog().error("No CertResponse for certificate received.");
                return null;
            }
            if ( resp.getCertReqId().getValue().intValue()!=requestId )
                StressTest.this.performanceTest.getLog().error("Received CertReqId is "+resp.getCertReqId().getValue().intValue()+" but should be "+requestId);                
            final PKIStatusInfo info = resp.getStatus();
            if ( info==null ) {
                StressTest.this.performanceTest.getLog().error("No PKIStatusInfo for certificate received.");
                return null;
            }
            if ( info.getStatus().getValue().intValue()!=0 )
                StressTest.this.performanceTest.getLog().error("Received Status is "+info.getStatus().getValue().intValue()+" but should be 0");                
            final CertifiedKeyPair kp = resp.getCertifiedKeyPair();
            if ( kp==null ) {
                StressTest.this.performanceTest.getLog().error("No CertifiedKeyPair for certificate received.");
                return null;
            }
            final CertOrEncCert cc = kp.getCertOrEncCert();
            if ( cc==null ) {
                StressTest.this.performanceTest.getLog().error("No CertOrEncCert for certificate received.");
                return null;
            }
            final X509CertificateStructure struct = cc.getCertificate();
            if ( struct==null ) {
                StressTest.this.performanceTest.getLog().error("No X509CertificateStructure for certificate received.");
                return null;
            }
            final byte encoded[] = struct.getEncoded();
            if ( encoded==null || encoded.length<=0 ) {
                StressTest.this.performanceTest.getLog().error("No encoded certificate received.");
                return null;
            }
            X509Certificate cert;
            try {
                cert = (X509Certificate)CertificateFactory.getInstance("X.509", "BC").generateCertificate(new ByteArrayInputStream(encoded));
            } catch (NoSuchProviderException e) {
                StressTest.this.performanceTest.getLog().error("Not possbile to create certificate.",e);
                return null;
            }
            if ( cert==null ) {
                StressTest.this.performanceTest.getLog().error("Not possbile to create certificate.");
                return null;
            }
            if ( !cert.getSubjectDN().equals(new X509Principal(sessionData.getUserDN())) )
                StressTest.this.performanceTest.getLog().error("Subject is '"+cert.getSubjectDN()+"' but should be '"+sessionData.getUserDN()+'\'');
            if ( !cert.getIssuerDN().equals(this.cacert.getSubjectDN()) )
                StressTest.this.performanceTest.getLog().error("Issuer is '"+cert.getIssuerDN()+"' but should be '"+this.cacert.getSubjectDN()+'\'');
            try {
                cert.verify(this.cacert.getPublicKey());
            } catch (Exception e) {
                StressTest.this.performanceTest.getLog().error("Certificate not verifying. See exception", e);
            }
            return cert;
        }
        private boolean checkCmpPKIConfirmMessage(SessionData sessionData, byte[] retMsg) throws IOException {
            //
            // Parse response message
            //
            final PKIMessage respObject = PKIMessage.getInstance(new ASN1InputStream(new ByteArrayInputStream(retMsg)).readObject());
            if ( respObject==null ) {
                StressTest.this.performanceTest.getLog().error("Not possbile to get response message.");
                return false;
            }
            final PKIHeader header = respObject.getHeader();
            if ( header.getSender().getTagNo()!=4 )
                StressTest.this.performanceTest.getLog().error("Wrong tag in respnse message header. Is "+header.getSender().getTagNo()+" should be 4.");
            {
                final X509Name name = X509Name.getInstance(header.getSender().getName()); 
                if ( name.equals(this.cacert.getSubjectDN()) )
                    StressTest.this.performanceTest.getLog().error("Wrong CA DN. Is "+name+" should be "+this.cacert.getSubjectDN());
            }{
                final X509Name name = X509Name.getInstance(header.getRecipient().getName());
                if ( name.equals(new X509Name(sessionData.userDN)))
                    StressTest.this.performanceTest.getLog().error("Wrong recipient DN. Is "+name+" should be "+sessionData.userDN);
            }
            final PKIBody body = respObject.getBody();
            if ( body==null ) {
                StressTest.this.performanceTest.getLog().error("No PKIBody for response received.");
                return false;
            }
            if ( body.getTagNo()!=19 )
                StressTest.this.performanceTest.getLog().error("Cert body tag not 19.");
            final DERNull n = body.getConf();
            if ( n==null )
                StressTest.this.performanceTest.getLog().error("Confirmation is null.");
            return true;
        }
        private PKIMessage genCertConfirm(SessionData sessionData, String hash) {
            
            PKIHeader myPKIHeader =
                new PKIHeader(
                        new DERInteger(2),
                        new GeneralName(new X509Name(sessionData.getUserDN())),
                        new GeneralName(new X509Name(this.cacert.getSubjectDN().getName())));
            myPKIHeader.setMessageTime(new DERGeneralizedTime(new Date()));
            // senderNonce
            myPKIHeader.setSenderNonce(new DEROctetString(sessionData.getNonce()));
            // TransactionId
            myPKIHeader.setTransactionID(new DEROctetString(sessionData.getTransId()));
            
            CertConfirmContent cc = new CertConfirmContent(new DEROctetString(hash.getBytes()), new DERInteger(sessionData.getReqId()));
            PKIBody myPKIBody = new PKIBody(cc, 24); // Cert Confirm
            PKIMessage myPKIMessage = new PKIMessage(myPKIHeader, myPKIBody);   
            return myPKIMessage;
        }
        private class GetCertificate implements Command {
            final private SessionData sessionData;
            GetCertificate(SessionData sd) {
                this.sessionData = sd;
            }
            public boolean doIt() throws Exception {
                this.sessionData.newSession();
                final PKIMessage one = genCertReq(this.sessionData, true, null);
                if ( one==null ) {
                    StressTest.this.performanceTest.getLog().error("No certificate request.");
                    return false;
                }
                final PKIMessage req = protectPKIMessage(one, false, PBEPASSWORD);
                if ( req==null ) {
                    StressTest.this.performanceTest.getLog().error("No protected message.");
                    return false;
                }
                this.sessionData.setReqId(req.getBody().getIr().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue());
                final ByteArrayOutputStream bao = new ByteArrayOutputStream();
                final DEROutputStream out = new DEROutputStream(bao);
                out.writeObject(req);
                final byte[] ba = bao.toByteArray();
                // Send request and receive response
                final byte[] resp = sendCmpHttp(ba);
                if ( resp==null || resp.length <= 0 ) {
                    StressTest.this.performanceTest.getLog().error("No response message.");
                    return false;
                }
                checkCmpResponseGeneral(resp, this.sessionData, true, false);
                checkCmpCertRepMessage(this.sessionData, resp, this.sessionData.getReqId());
                return true;
            }
            public String getJobTimeDescription() {
                return "Get certificate";
            }
        }
        private class SendConfirmMessageToCA implements Command {
            final private SessionData sessionData;
            SendConfirmMessageToCA(SessionData sd) {
                this.sessionData = sd;
            }
            public boolean doIt() throws Exception {
                String hash = "foo123";
                PKIMessage con = genCertConfirm(this.sessionData, hash);
                if ( con==null ) {
                    StressTest.this.performanceTest.getLog().error("Not possible to generate PKIMessage.");
                    return false;
                }
                PKIMessage confirm = protectPKIMessage(con, false, PBEPASSWORD);
                final ByteArrayOutputStream bao = new ByteArrayOutputStream();
                final DEROutputStream out = new DEROutputStream(bao);
                out.writeObject(confirm);
                final byte ba[] = bao.toByteArray();
                // Send request and receive response
                final byte[] resp = sendCmpHttp(ba);
                if ( resp==null || resp.length <= 0 ) {
                    StressTest.this.performanceTest.getLog().error("No response message.");
                    return false;
                }
                checkCmpResponseGeneral(resp, this.sessionData, false, false);
                checkCmpPKIConfirmMessage(this.sessionData, resp);
                return true;
            }
            public String getJobTimeDescription() {
                return "Send confirmation to CA";
            }
        }
        /* to be implemented later
        private class Revoke implements Command {
            public boolean doIt() throws Exception {
                PKIMessage rev = genRevReq(issuerDN, userDN, cert.getSerialNumber(), cacert, StressTest.this.nonce, StressTest.this.transid);
                assertNotNull(rev);
                bao = new ByteArrayOutputStream();
                out = new DEROutputStream(bao);
                out.writeObject(rev);
                ba = bao.toByteArray();
                // Send request and receive response
                resp = sendCmpHttp(ba);
                assertNotNull(resp);
                assertTrue(resp.length > 0);
                checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, StressTest.this.nonce, StressTest.this.transid, false, false);
                checkCmpFailMessage(resp, "No PKI protection to verify.", 23, reqId, 1);
            }
            public String getJobTimeDescription() {
                return "Revoke user";
            }
        }*/
        class SessionData {
            private String userDN;
            private byte[] nonce;
            private byte[] transid;
            private int reqId;
            void newSession() {
                this.userDN = "CN=CMP Test User Nr "+StressTest.this.random.nextInt()+",O=CMP Test";
                StressTest.this.random.nextBytes(this.nonce);
                StressTest.this.random.nextBytes(this.transid);
            }
            int getReqId() {
                return this.reqId;
            }
            void setReqId(int i) {
                this.reqId = i;
            }
            String getUserDN() {
                return this.userDN;
            }
            byte[] getTransId() {
                return this.transid;
            }
            byte[] getNonce() {
                return this.nonce;
            }
        }
        private class MyCommandFactory implements CommandFactory {
            public Command[] getCommands() throws Exception {
                final SessionData sessionData = new SessionData();
                return new Command[]{new GetCertificate(sessionData), new SendConfirmMessageToCA(sessionData)};//, new Revoke(sessionData)};
            }
        }
    }

    /* (non-Javadoc)
     * @see org.ejbca.ui.cli.ClientToolBox#execute(java.lang.String[])
     */
    @Override
    void execute(String[] args) {
        final String httpPath;
        final int numberOfThreads;
        final int waitTime;
        if ( args.length < 2 ) {
            System.out.println(args[0]+" <http URL> [<number of threads>] [<wait time between eash thread is started>]");
            System.out.println("Example: ");
            return;
        }
        httpPath = args[1];
        numberOfThreads = args.length>2 ? Integer.parseInt(args[2].trim()):1;
        waitTime = args.length>3 ? Integer.parseInt(args[3].trim()):0;
        try {
            new StressTest(httpPath, numberOfThreads, waitTime);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* (non-Javadoc)
     * @see org.ejbca.ui.cli.ClientToolBox#getName()
     */
    @Override
    String getName() {
        return "CMPTest";
    }

}
