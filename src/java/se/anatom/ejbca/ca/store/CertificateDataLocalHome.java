package se.anatom.ejbca.ca.store;

import java.security.cert.Certificate;

import java.util.Collection;

import javax.ejb.CreateException;
import javax.ejb.FinderException;


/**
 * For docs, see CertificateDataBean
 */
public interface CertificateDataLocalHome extends javax.ejb.EJBLocalHome {
    /**
     * creates a certificate object in the database
     *
     * @param incert certificate
     *
     * @return certificate data object
     *
     * @throws CreateException if the object can not be created in db
     */
    public CertificateDataLocal create(Certificate incert)
        throws CreateException;

    /**
     * finds a certificate in the db
     *
     * @param pk primary key
     *
     * @return certificate data object
     *
     * @throws FinderException if the certificate can not be found in db
     */
    public CertificateDataLocal findByPrimaryKey(CertificateDataPK pk)
        throws FinderException;

    /**
     * Finds certificates which expire within a specified time.
     *
     * @param expireTime (Date.getTime()-format), all certificates that expires before this date
     *        will be listed.
     *
     * @return Collection of CertificateData in no specified order.
     *
     * @throws FinderException if the certificate can not be found in db
     */
    public Collection findByExpireDate(long expireDate)
        throws FinderException;

    /** Finds certificates which a specified subjectDN.
     * @param subjectDN, the subject whose certificates will be listed
     * @param issuerDN, the issuer of certificate
     * @return Collection of CertificateData in no specified order.
     *
     * @throws FinderException if the certificate can not be found in db
     */
    public Collection findBySubjectDNAndIssuerDN(String subjectDN, String issuerdn)
        throws FinderException;

    /**
     * Finds the certificate which a specified issuerDN and SerialNumber.
     *
     * @param issuerDN , the issuer of the certificates that is wanted.
     * @param serialNumber , the serial number (BigInteger.toString()-format) of the certificates
     *        that is wanted.
     *
     * @return Collection of CertificateData in no specified order (should only contain one!).
     *
     * @throws FinderException if the certificate can not be found in db
     */
    public Collection findByIssuerDNSerialNumber(String issuerDN, String serialNumber)
        throws FinderException;

    /**
     * Finds the certificate which a specified SerialNumber.
     *
     * @param serialNumber , the serial number (BigInteger.toString()-format) of the certificates
     *        that is wanted.
     *
     * @return Collection of CertificateData in no specified order (should only contain one!).
     *
     * @throws FinderException if the certificate can not be found in db
     */
    public Collection findBySerialNumber(String serialNumber)
        throws FinderException;

    /**
     * Finds the certificate which a specified Username.
     *
     * @param username of the certificates that is wanted.
     *
     * @return Collection of CertificateData in no specified order (should only contain one!).
     *
     * @throws FinderException if the certificate can not be found in db
     */
    public Collection findByUsername(String username) throws FinderException;
}
