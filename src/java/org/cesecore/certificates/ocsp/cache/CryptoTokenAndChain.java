/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.certificates.ocsp.cache;

import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.cesecore.certificates.util.AlgorithmTools;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.util.CertTools;

/**
 * This is a tuple object to wrap a crypto token and a X509Certificate chain. 
 * 
 * If rekeying has been activated on this crypto token, cached versions of the chain and crypto token will be returned.
 * 
 * @version $Id$
 *
 */
@Deprecated // TODO: Remove
public class CryptoTokenAndChain implements Serializable {

    private static final long serialVersionUID = -4091605336832090454L;
    private CryptoToken cryptoToken;
    private X509Certificate[] chain;
    private String privateKeyAlias;

    private final int caCertPosition;

    public CryptoTokenAndChain(CryptoToken cryptoToken, X509Certificate[] chain, String privateKeyAlias) {
        this.cryptoToken = cryptoToken;
        this.chain = chain;
        this.privateKeyAlias = privateKeyAlias;

        if (CertTools.isCA(chain[0])) {
            //A CA or SUBCA
            caCertPosition = 0;
        } else {
            //OCSP certificate in position 0
            caCertPosition = 1;
        }
     }

    /**
     * Generates a new keypair in the crypto token wrapped by this object
     * 
     * @throws CryptoTokenOfflineException crypto token wrapped by this object was offline
     * @throws InvalidKeyException if the public key can not be used to verify a string signed by the private key, because the key is wrong or the 
     * signature operation fails for other reasons such as a NoSuchAlgorithmException or SignatureException.
     */
    public void generateKeyPair(String newAlias) throws CryptoTokenOfflineException, InvalidKeyException {
        String keySpecification;
        try {
            keySpecification = AlgorithmTools.getKeySpecification(cryptoToken.getPublicKey(privateKeyAlias));
            cryptoToken.generateKeyPair(keySpecification, newAlias);
            cryptoToken.testKeyPair(newAlias);
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Existing keypair was based on an invalid algorithm parameter.", e);
        }

    }

    /**
     * 
     * @return the public key from this crypto token
     * @throws CryptoTokenOfflineException if Crypto Token is not available or connected, or key with alias does not exist.
     */
    public PublicKey getPublicKey() throws CryptoTokenOfflineException {
        return cryptoToken.getPublicKey(privateKeyAlias);
    }

    /**
     * 
     * @return the private key from this crypto token.
     * @throws CryptoTokenOfflineException if Crypto Token is not available or connected, or key with alias does not exist.
     */
    public PrivateKey getPrivateKey() throws CryptoTokenOfflineException {
        return cryptoToken.getPrivateKey(privateKeyAlias);
    }

    /**
     * 
     * @return the signer provider name
     */
    public String getSignProviderName() {
        return cryptoToken.getSignProviderName();
    }

    /**
     * @return the chain. If rekeying is in progress, will return a cached chain instead. 
     */
    public X509Certificate[] getChain() {
        return chain;
    }

    public synchronized void renewAliasAndChain(String newAlias,X509Certificate[] chain) {
        this.privateKeyAlias = newAlias;
        this.chain = chain;
    }

    public X509Certificate getCaCertificate() {
        return chain[caCertPosition];
    }

    public String getAlias() {
        return privateKeyAlias;
    }

    public int getCryptoTokenId() {
        return cryptoToken.getId();
    }

    /**
     * 
     * @param signatureAlgorithm
     * @return
     * @throws OperatorCreationException
     * @throws IOException 
     * @throws CryptoTokenOfflineException if Crypto Token is not available or connected
     */
    public PKCS10CertificationRequest getPKCS10CertificationRequest(String signatureAlgorithm) throws OperatorCreationException, IOException,
            CryptoTokenOfflineException {
        return CertTools.genPKCS10CertificationRequest(signatureAlgorithm, CertTools.stringToBcX500Name("CN=NOUSED"),
                cryptoToken.getPublicKey(privateKeyAlias), new DERSet(), cryptoToken.getPrivateKey(privateKeyAlias),
                cryptoToken.getSignProviderName());
    }

}
