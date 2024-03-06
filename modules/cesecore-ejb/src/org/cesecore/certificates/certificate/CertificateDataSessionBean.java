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
package org.cesecore.certificates.certificate;

import static java.util.stream.Collectors.toList;
import static org.cesecore.authorization.control.StandardRules.SYSTEMCONFIGURATION_VIEW;

import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.AuthorizationSessionLocal;
import org.cesecore.certificates.crl.RevokedCertInfo;
import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.util.LogRedactionUtils;
import org.cesecore.util.QueryResultWrapper;
import org.cesecore.util.ValidityDate;
import org.cesecore.util.ValueExtractor;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.math.BigInteger;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Low level CRUD functions to access CertificateData
 */
@Stateless //(mappedName = JndiConstants.APP_JNDI_PREFIX + "CertificateDataSessionRemote")
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class CertificateDataSessionBean extends BaseCertificateDataSessionBean implements CertificateDataSessionLocal, CertificateDataSessionRemote {

    private static final Logger log = Logger.getLogger(CertificateDataSessionBean.class);

    /** A SELECT query that fetches certificate metadata, but not the actual certificates themselves */
    private static final String SELECT_QUERY_FOR_CERTIFICATEINFO_SUBSET = "SELECT a.fingerprint as fingerprint, a.issuerDN as issuerDN, a.subjectDN as subjectDN, a.cAFingerprint as cAFingerprint, a.status as status, a.type as type, a.serialNumber as serialNumber, "
            + "a.notBefore as notBefore, a.expireDate as expireDate, a.revocationDate as revocationDate, a.revocationReason as revocationReason, "
            + "a.username as username, a.tag as tag, a.certificateProfileId as certificateProfileId, a.endEntityProfileId as endEntityProfileId, a.updateTime as updateTime, "
            + "a.subjectKeyId as subjectKeyId, a.subjectAltName as subjectAltName, a.accountBindingId as accountBindingId FROM CertificateData a ";

    @PersistenceContext(unitName = CesecoreConfiguration.PERSISTENCE_UNIT)
    private EntityManager entityManager;
    
    @EJB
    private AuthorizationSessionLocal authorizationSession;

    @Override
    protected EntityManager getEntityManager() {
        return entityManager;
    }

    //
    // Search functions.
    //

    /** @return the found entity instance or null if the entity does not exist */
    @Override
    public CertificateData findByFingerprint(String fingerprint) {
        return entityManager.find(CertificateData.class, fingerprint);
    }

    /** @return return the query results as a Set. */
    @Override
    public Set<String> findUsernamesBySubjectDNAndIssuerDN(final String subjectDN, final String issuerDN) {
            final TypedQuery<String> query = entityManager.createQuery("SELECT a.username FROM CertificateData a WHERE a.subjectDN=:subjectDN AND a.issuerDN=:issuerDN", String.class);
            query.setParameter("subjectDN", subjectDN);
            query.setParameter("issuerDN", issuerDN);
            return new HashSet<>(query.getResultList());
    }

    /** @return return the query results as a List. */
    @Override
    public List<CertificateData> findBySubjectDN(final String subjectDN) {
        final TypedQuery<CertificateData> query = entityManager.createQuery("SELECT a FROM CertificateData a WHERE a.subjectDN=:subjectDN", CertificateData.class);
        query.setParameter("subjectDN", subjectDN);
        return query.getResultList();
    }

    /**
     * @return active certificates that match the specified types and subjectDN
     */
    @Override
    public List<Certificate> findActiveBySubjectDnAndType(final String subjectDN, final Collection<Integer> certificateTypes) {
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE (a.status=:status1 or a.status=:status2) AND a.type IN (:ctypes) AND a.subjectDN=:subjectDN", CertificateData.class);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setParameter("ctypes", certificateTypes);
        query.setParameter("subjectDN", subjectDN);
        return getCertificateList(query.getResultList());
    }

    /** @return return the query results as a List. */
    @Override
    public List<CertificateData> findBySerialNumber(final String serialNumber) {
        final TypedQuery<CertificateData> query = entityManager.createQuery("SELECT a FROM CertificateData a WHERE a.serialNumber=:serialNumber", CertificateData.class);
        query.setParameter("serialNumber", serialNumber);
        return query.getResultList();
    }

    /** @return return the query results as a List. */
    @Override
    public List<CertificateData> findByIssuerDNSerialNumber(final String issuerDN, final String serialNumber) {
        final TypedQuery<CertificateData> query = entityManager.createQuery("SELECT a FROM CertificateData a WHERE a.issuerDN=:issuerDN AND a.serialNumber=:serialNumber", CertificateData.class);
        query.setParameter("issuerDN", issuerDN);
        query.setParameter("serialNumber", serialNumber);
        return query.getResultList();
    }

    @Override
    public Long findQuantityOfAllCertificates() {
        Query query = entityManager.createQuery("SELECT count(cd) FROM CertificateData cd");
        return (Long) query.getResultList().get(0);
    }

    @Override
    public Long findQuantityOfTheActiveCertificates() {
        Query query = entityManager.createQuery("SELECT count(cd) FROM CertificateData cd WHERE cd.expireDate >= :now "
                + "AND (cd.status = :statusActive OR cd.status = :statusNotifiedAboutExpiration)");
        query.setParameter("now", System.currentTimeMillis());
        query.setParameter("statusActive", CertificateConstants.CERT_ACTIVE);
        query.setParameter("statusNotifiedAboutExpiration", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        return (Long) query.getResultList().get(0);
    }

    @Override
    public CertificateInfo findFirstCertificateInfo(final String issuerDN, final String serialNumber) {
        CertificateInfo ret = null;
        final Query query = entityManager
                .createNativeQuery(
                        "SELECT a.fingerprint, a.subjectDN, a.cAFingerprint, a.status, a.type, a.serialNumber, a.notBefore, a.expireDate, a.revocationDate, a.revocationReason, "
                                + "a.username, a.tag, a.certificateProfileId, a.endEntityProfileId, a.updateTime, a.subjectKeyId, a.subjectAltName, a.accountBindingId FROM CertificateData a WHERE a.issuerDN=:issuerDN AND a.serialNumber=:serialNumber",
                        "CertificateInfoSubset2");
        query.setParameter("issuerDN", issuerDN);
        query.setParameter("serialNumber", serialNumber);
        query.setMaxResults(1);
        @SuppressWarnings("unchecked")
        final List<Object[]> resultList = query.getResultList();
        if (!resultList.isEmpty()) {
            final Object[] fields = resultList.get(0);
            // The order of the results are defined by the SqlResultSetMapping annotation
            final String fingerprint = (String) fields[0];
            final String subjectDN = (String) fields[1];
            final String cafp = (String) fields[2];
            final int status = ValueExtractor.extractIntValue(fields[3]);
            final int type = ValueExtractor.extractIntValue(fields[4]);
            final Long notBefore;
            if (fields[5] == null) {
                notBefore = null;
            } else {
                notBefore = ValueExtractor.extractLongValue(fields[5]);
            }
            final long expireDate = ValueExtractor.extractLongValue(fields[6]);
            final long revocationDate = ValueExtractor.extractLongValue(fields[7]);
            final int revocationReason = ValueExtractor.extractIntValue(fields[8]);
            final String username = (String) fields[9];
            final String tag = (String) fields[10];
            final int certificateProfileId = ValueExtractor.extractIntValue(fields[11]);
            final Integer endEntityProfileId;
            if (fields[12] == null) {
                endEntityProfileId = null;
            } else {
                endEntityProfileId = ValueExtractor.extractIntValue(fields[12]);
            }
            final long updateTime;
            if (fields[13] == null) {
                updateTime = 0; // Might be null in an upgraded installation
            } else {
                updateTime = ValueExtractor.extractLongValue(fields[13]);
            }
            final String subjectKeyId = (String)fields[14];
            final String subjectAltName = (String)fields[15];
            final String accountBindingId = (String)fields[16];
            ret = new CertificateInfo(fingerprint, cafp, serialNumber, issuerDN, subjectDN, status, type, notBefore, expireDate, revocationDate,
                    revocationReason, username, tag, certificateProfileId, endEntityProfileId, updateTime, subjectKeyId, subjectAltName, accountBindingId);
        }
        return ret;
    }

    @Override
    public String findLastUsernameByIssuerDNSerialNumber(String issuerDN, String serialNumber) {
        final TypedQuery<String> query = entityManager
                .createQuery("SELECT a.username FROM CertificateData a WHERE a.issuerDN=:issuerDN AND a.serialNumber=:serialNumber", String.class);
        query.setParameter("issuerDN", issuerDN);
        query.setParameter("serialNumber", serialNumber);
        // Since no ordering is done this seems a bit strange, but this is what it was like in previous versions..
        return QueryResultWrapper.getLastResult(query);
    }

    @Override
    public List<CertificateData> findByUsernameOrdered(String username) {
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE a.username=:username ORDER BY a.expireDate DESC, a.serialNumber DESC", CertificateData.class);
        query.setParameter("username", username);
        return query.getResultList();
    }

    /** @return return the query results as a List. */
    @Override
    public List<CertificateData> findByUsernameAndStatus(final String username, final int status) {
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE a.username=:username AND a.status=:status ORDER BY a.expireDate DESC, a.serialNumber DESC", CertificateData.class);
        query.setParameter("username", username);
        query.setParameter("status", status);
        return query.getResultList();
    }

    /** @return return the query results as a List. */
    @Override
    public List<CertificateData> findByUsernameAndStatusAfterExpireDate(final String username, final int status, final long afterExpireDate) {
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE a.username=:username AND a.status=:status AND a.expireDate>=:afterExpireDate ORDER BY a.expireDate DESC, a.serialNumber DESC",
                        CertificateData.class);
        query.setParameter("username", username);
        query.setParameter("status", status);
        query.setParameter("afterExpireDate", afterExpireDate);
        return query.getResultList();
    }

    // TODO: When only JPA is used, check if we can refactor this method to SELECT DISTINCT a.username FROM ...
    @Override
    public Set<String> findUsernamesByIssuerDNAndSubjectKeyId(final String issuerDN, final String subjectKeyId) {
        final TypedQuery<String> query = entityManager.createQuery("SELECT a.username FROM CertificateData a WHERE a.issuerDN=:issuerDN AND a.subjectKeyId=:subjectKeyId", String.class);
        query.setParameter("issuerDN", issuerDN);
        query.setParameter("subjectKeyId", subjectKeyId);
        return new HashSet<>(query.getResultList());
    }

    @Override
    public String findUsernameByIssuerDnAndSerialNumber(final String issuerDn, final String serialNumber) {
        final TypedQuery<String> query = entityManager.createQuery("SELECT a.username FROM CertificateData a WHERE a.issuerDN=:issuerDN AND a.serialNumber=:serialNumber", String.class);
        query.setParameter("issuerDN", issuerDn);
        query.setParameter("serialNumber", serialNumber);
        return query.getSingleResult();
    }

    @Override
    public Set<String> findUsernamesBySubjectKeyIdOrDnAndIssuer(final String issuerDN, final String subjectKeyId, final String subjectDN) {
        final TypedQuery<String> query = entityManager.createQuery("SELECT a.username FROM CertificateData a WHERE (a.subjectKeyId=:subjectKeyId OR a.subjectDN=:subjectDN) AND a.issuerDN=:issuerDN",
                String.class);
        query.setParameter("issuerDN", issuerDN);
        query.setParameter("subjectKeyId", subjectKeyId);
        query.setParameter("subjectDN", subjectDN);
        return new HashSet<>(query.getResultList());
    }

    @Override
    public List<String> findFingerprintsByIssuerDN(final String issuerDN) {
        final TypedQuery<String> query = entityManager.createQuery("SELECT a.fingerprint FROM CertificateData a WHERE a.issuerDN=:issuerDN", String.class);
        query.setParameter("issuerDN", issuerDN);
        return query.getResultList();
    }

    @Override
    public Collection<RevokedCertInfo> getRevokedCertInfos(final String issuerDN, final boolean deltaCrl, final int crlPartitionIndex, final long lastBaseCrlDate, 
            final boolean allowInvalidityDate) {
        if (log.isDebugEnabled()) {
            log.debug("Querying for revoked certificates. IssuerDN: '" + issuerDN + "'" +
                    ", Delta CRL: " + deltaCrl +
                    ", Last Base CRL Date: " + FastDateFormat.getInstance(ValidityDate.ISO8601_DATE_FORMAT, TimeZone.getTimeZone("GMT")).format(lastBaseCrlDate) +
                    ", Allow Invalidity Date: " + allowInvalidityDate);
        }
        final String crlPartitionExpression;
        final String ordering;
        final Query query;
        if (crlPartitionIndex != 0) {
            crlPartitionExpression = " AND crlPartitionIndex = :crlPartitionIndex";
        } else {
            crlPartitionExpression = " AND (crlPartitionIndex = :crlPartitionIndex OR crlPartitionIndex IS NULL)";
        }
        if (CesecoreConfiguration.getDatabaseRevokedCertInfoFetchOrdered()) {
            ordering = " ORDER BY revocationDate, fingerprint ASC";
        } else {
            ordering = "";
        }
        if (allowInvalidityDate && deltaCrl) {
            // For delta CRL generation with invalidityDate. Results will be filtered later. This is needed since we will need to compare the results with the revoked cert entries
            // in the last base CRL in order to figure out which certificates had their invalidity date changed since the last base CRL. We can't determine that in the query here.
            query = getEntityManager().createNativeQuery(
                    "SELECT a.fingerprint as fingerprint, a.serialNumber as serialNumber, a.expireDate as expireDate, a.revocationDate as revocationDate, a.revocationReason as revocationReason, a.invalidityDate as invalidityDate  FROM CertificateData a WHERE "
                            + "a.issuerDN=:issuerDN AND a.revocationDate>:revocationDate AND a.updateTime>:lastBaseCrlDate AND (a.status=:status1 OR a.status=:status2 OR a.status=:status3)"
                            + crlPartitionExpression + ordering,
                    "RevokedCertInfoSubset");
            query.setParameter("lastBaseCrlDate", lastBaseCrlDate);
            query.setParameter("revocationDate", -1L);
            query.setParameter("status1", CertificateConstants.CERT_REVOKED);
            query.setParameter("status2", CertificateConstants.CERT_ACTIVE); // in case the certificate has been changed from on hold, we need to include it as "removeFromCRL" in the Delta CRL
            query.setParameter("status3", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION); // could happen if a cert is re-activated just before expiration            
        }
        else if (deltaCrl) {
            // Delta CRL
            query = getEntityManager().createNativeQuery(
                    "SELECT a.fingerprint as fingerprint, a.serialNumber as serialNumber, a.expireDate as expireDate, a.revocationDate as revocationDate, a.revocationReason as revocationReason, a.invalidityDate as invalidityDate  FROM CertificateData a WHERE "
                            + "a.issuerDN=:issuerDN AND a.revocationDate>:revocationDate AND (a.status=:status1 OR a.status=:status2 OR a.status=:status3)"
                            + crlPartitionExpression + ordering,
                    "RevokedCertInfoSubset");
            query.setParameter("revocationDate", lastBaseCrlDate);
            query.setParameter("status1", CertificateConstants.CERT_REVOKED);
            query.setParameter("status2", CertificateConstants.CERT_ACTIVE); // in case the certificate has been changed from on hold, we need to include it as "removeFromCRL" in the Delta CRL
            query.setParameter("status3", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION); // could happen if a cert is re-activated just before expiration
        } else {
            // Base CRL
            query = getEntityManager().createNativeQuery(
                    "SELECT a.fingerprint as fingerprint, a.serialNumber as serialNumber, a.expireDate as expireDate, a.revocationDate as revocationDate, a.revocationReason as revocationReason, a.invalidityDate as invalidityDate FROM CertificateData a WHERE "
                            + "a.issuerDN=:issuerDN AND a.status=:status"
                            + crlPartitionExpression + ordering,
                    "RevokedCertInfoSubset");
            query.setParameter("status", CertificateConstants.CERT_REVOKED);
        }
        query.setParameter("issuerDN", issuerDN);
        query.setParameter("crlPartitionIndex", crlPartitionIndex);
        return getRevokedCertInfosInternal(query, allowInvalidityDate);
    }

    @Override
    public List<CertificateData> findByExpireDateWithLimit(final long expireDate, final int maxNumberOfResults) {
        final long now = System.currentTimeMillis();
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE a.expireDate<:expireDate AND a.expireDate>=:now AND (a.status=:status1 OR a.status=:status2)", CertificateData.class);
        query.setParameter("expireDate", expireDate);
        query.setParameter("now", now);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setMaxResults(maxNumberOfResults);
        return query.getResultList();
    }

    @Override
    public List<String> findSerialNrByIssuerWithLimitAndOffset(String issuerDN, int limit, int offset) {
        final TypedQuery<String> query = entityManager
                .createQuery("SELECT a.serialNumber FROM CertificateData a WHERE a.issuerDN=:issuerDN", String.class);
        query.setParameter("issuerDN", issuerDN);
        query.setMaxResults(limit);
        query.setFirstResult(offset);
        return query.getResultList();
    }
    
    @Override
    public List<String> findSerialNrByIssuerAndExpireDateWithLimitAndOffset(String issuerDN, long expireDate, int limit, int offset) {
        // we ignore the certificate status
        final TypedQuery<String> query = entityManager
                .createQuery("SELECT a.serialNumber FROM CertificateData a WHERE a.issuerDN=:issuerDN and a.expireDate>:expireDate", String.class);
        query.setParameter("issuerDN", issuerDN);
        query.setParameter("expireDate", expireDate);
        query.setMaxResults(limit);
        query.setFirstResult(offset);
        return query.getResultList();
    }
    
    @Override
    public List<CertificateData> findByExpireDateWithLimitAndOffset(long expireDate, int maxNumberOfResults, int offset) {
        final long now = System.currentTimeMillis();
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE a.expireDate<:expireDate AND a.expireDate>=:now AND (a.status=:status1 OR a.status=:status2) order by a.expireDate asc", CertificateData.class);
        query.setParameter("expireDate", expireDate);
        query.setParameter("now", now);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setMaxResults(maxNumberOfResults);
        query.setFirstResult(offset);
        return query.getResultList();
    }


    @Override
    public int countByExpireDate(long expireDate) {
        final long now = System.currentTimeMillis();
        Query query = entityManager.createQuery("SELECT count(a) FROM CertificateData a WHERE a.expireDate<:expireDate AND a.expireDate>=:now AND (a.status=:status1 OR a.status=:status2) ");
        query.setParameter("expireDate", expireDate);
        query.setParameter("now", now);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        return ((Long) query.getSingleResult()).intValue();
    }
    
    @Override
    public Long getCertificateCount(AuthenticationToken adminToken, Boolean isActive) throws AuthorizationDeniedException {
        final String errorMessage = "Unauthorized access to the resource. Token: %s. "
                + "Only the user with the \"/system_functionality/view_systemconfiguration/\" privilege "
                + "is allowed to perform this operation.";
        
        if (!authorizationSession.isAuthorized(adminToken, SYSTEMCONFIGURATION_VIEW.resource())) {
            throw new AuthorizationDeniedException(String.format(errorMessage, adminToken.toString()));
        }
        if (isActive != null && isActive) {
            return findQuantityOfTheActiveCertificates();
        }
        return findQuantityOfAllCertificates();
    }

    @Override
    public List<CertificateData> findByExpireDateAndIssuerWithLimit(final long expireDate, final String issuerDN, final int maxNumberOfResults) {
        final long now = System.currentTimeMillis();
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE a.expireDate<:expireDate AND a.expireDate>=:now AND (a.status=:status1 OR a.status=:status2) AND a.issuerDN=:issuerDN", CertificateData.class);
        query.setParameter("expireDate", expireDate);
        query.setParameter("now", now);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setParameter("issuerDN", issuerDN);
        query.setMaxResults(maxNumberOfResults);
        return query.getResultList();
    }

    @Override
    public List<CertificateData> findByExpireDateAndTypeWithLimit(final long expireDate, final int certificateType, final int maxNumberOfResults) {
        final long now = System.currentTimeMillis();
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE a.expireDate<:expireDate AND a.expireDate>=:now AND (a.status=:status1 OR a.status=:status2) AND a.type=:ctype", CertificateData.class);
        query.setParameter("expireDate", expireDate);
        query.setParameter("now", now);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setParameter("ctype", certificateType);
        query.setMaxResults(maxNumberOfResults);
        return query.getResultList();
    }

    @Override
    public List<String> findUsernamesByExpireTimeWithLimit(final long minExpireTime, final long maxExpireTime, final int maxResults) {
        // TODO: Would it be more effective to drop the NOT NULL of this query and remove it from the result?
        final TypedQuery<String> query = entityManager
                .createQuery("SELECT DISTINCT a.username FROM CertificateData a WHERE a.expireDate>=:minExpireTime AND a.expireDate<:maxExpireTime AND " +
                        "(a.status=:status1 OR a.status=:status2) AND a.username IS NOT NULL", String.class);
        query.setParameter("minExpireTime", minExpireTime);
        query.setParameter("maxExpireTime", maxExpireTime);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setMaxResults(maxResults);
        return query.getResultList();
    }

    @Override
    public List<Certificate> getCertificateList(final List<CertificateData> cdl) {
        final List<Certificate> cl = new LinkedList<>();
        for (final CertificateData cd : cdl) {
            final Certificate cert = cd.getCertificate(entityManager);
            if ( cert==null ) {
                continue;
            }
            cl.add(cert);
        }
        return cl;
    }

    @Override
    public List<Certificate> findCertificatesByIssuerDnAndSerialNumbers(final String issuerDN, final Collection<BigInteger> serialNumbers) {
        final StringBuilder sb = new StringBuilder();
        for(final BigInteger serno : serialNumbers) {
            sb.append(", '");
            sb.append(serno.toString());
            sb.append("'");
        }
        // to save the repeating if-statement in the above closure not to add ', ' as the first characters in the StringBuilder we remove the two chars
        // here :)
        sb.delete(0, ", ".length());
        // Derby: Columns of type 'LONG VARCHAR' may not be used in CREATE INDEX, ORDER BY, GROUP BY, UNION, INTERSECT, EXCEPT or DISTINCT statements
        // because comparisons are not supported for that type.
        // Since two certificates in the database should never be the same, "SELECT DISTINCT ..." was changed to "SELECT ..." here.
        final TypedQuery<CertificateData> query = entityManager.createQuery("SELECT a FROM CertificateData a WHERE a.issuerDN=:issuerDN AND a.serialNumber IN ("
                + sb.toString() + ")", CertificateData.class);
        query.setParameter("issuerDN", issuerDN);
        return getCertificateList(query.getResultList());
    }

    @Override
    public CertificateInfo getCertificateInfo(final String fingerprint) {
        CertificateInfo ret = null;
        final Query query = entityManager.createNativeQuery(
                SELECT_QUERY_FOR_CERTIFICATEINFO_SUBSET + "WHERE a.fingerprint=:fingerprint",
                "CertificateInfoSubset");
        query.setParameter("fingerprint", fingerprint);
        @SuppressWarnings("unchecked")
        final List<Object[]> resultList = query.getResultList();
        if (!resultList.isEmpty()) {
            final Object[] fields = resultList.get(0);
            ret = resultToCertificateData(fields);
        }
        return ret;
    }

    /**
     * Converts a result to a CertificateData. The fields must be ordered exactly as in CertificateInfoSubset
     */
    private CertificateInfo resultToCertificateData(final Object[] fields) {
        // The order of the results are defined by the SqlResultSetMapping annotation
        final String fingerprint = (String) fields[0];
        final String issuerDN = (String) fields[1];
        final String subjectDN = (String) fields[2];
        final String cafp = (String) fields[3];
        final int status = ValueExtractor.extractIntValue(fields[4]);
        final int type = ValueExtractor.extractIntValue(fields[5]);
        final String serno = (String) fields[6];
        final Long notBefore;
        if (fields[7] == null) {
            notBefore = null;
        } else {
            notBefore = ValueExtractor.extractLongValue(fields[7]);
        }
        final long expireDate = ValueExtractor.extractLongValue(fields[8]);
        final long revocationDate = ValueExtractor.extractLongValue(fields[9]);
        final int revocationReason = ValueExtractor.extractIntValue(fields[10]);
        final String username = (String) fields[11];
        final String tag = (String) fields[12];
        final int certificateProfileId = ValueExtractor.extractIntValue(fields[13]);
        final Integer endEntityProfileId;
        if (fields[14] == null) {
            endEntityProfileId = null;
        } else {
            endEntityProfileId = ValueExtractor.extractIntValue(fields[14]);
        }
        final long updateTime;
        if (fields[15] == null) {
            updateTime = 0; // Might be null in an upgraded installation
        } else {
            updateTime = ValueExtractor.extractLongValue(fields[15]);
        }
        final String subjectKeyId = (String)fields[16];
        final String subjectAltName = (String)fields[17];
        final String accountBindingId = (String)fields[18];
        return new CertificateInfo(fingerprint, cafp, serno, issuerDN, subjectDN, status, type, notBefore, expireDate, revocationDate, revocationReason,
                username, tag, certificateProfileId, endEntityProfileId, updateTime, subjectKeyId, subjectAltName, accountBindingId);
    }

    @Override
    public List<Certificate> findActiveCertificatesByType(final Collection<Integer> certificateTypes) {
        // Derby: Columns of type 'LONG VARCHAR' may not be used in CREATE INDEX, ORDER BY, GROUP BY, UNION, INTERSECT, EXCEPT or DISTINCT statements
        // because comparisons are not supported for that type.
        // Since two certificates in the database should never be the same, "SELECT DISTINCT ..." was changed to "SELECT ..." here.
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE (a.status=:status1 or a.status=:status2) AND a.type IN (:ctypes)", CertificateData.class);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setParameter("ctypes", certificateTypes);
        return getCertificateList( query.getResultList());
    }
    
    @Override
    public List<Certificate> findActiveCaCertificatesByType(final Collection<Integer> certificateTypes) {
        // only loads active CA certificates compared to findActiveCertificatesByType
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE (a.status=:status1 or a.status=:status2) AND a.type IN (:ctypes) AND a.username=:uname", CertificateData.class);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setParameter("ctypes", certificateTypes);
        query.setParameter("uname", CertificateConstants.CERT_USERNAME_SYSTEMCA);
        return getCertificateList( query.getResultList());
    }

    @Override
    public List<Certificate> findActiveCertificatesByTypeAndIssuer(final Collection<Integer> certificateTypes, final String issuerDN) {
        // Derby: Columns of type 'LONG VARCHAR' may not be used in CREATE INDEX, ORDER BY, GROUP BY, UNION, INTERSECT, EXCEPT or DISTINCT statements
        // because comparisons are not supported for that type.
        // Since two certificates in the database should never be the same, "SELECT DISTINCT ..." was changed to "SELECT ..." here.
        final TypedQuery<CertificateData> query = entityManager
                .createQuery("SELECT a FROM CertificateData a WHERE (a.status=:status1 or a.status=:status2) AND a.type IN (:ctypes) AND a.issuerDN=:issuerDN", CertificateData.class);
        query.setParameter("ctypes", certificateTypes);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setParameter("issuerDN", issuerDN);
        return getCertificateList(query.getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findExpirationInfo(final Collection<String> cas, final Collection<Integer> certificateProfiles,
            final long activeNotifiedExpireDateMin, final long activeNotifiedExpireDateMax, final long activeExpireDateMin) {
        // We don't select the base64 certificate data here, because it may be a LONG data type which we can't simply select, or we don't want to read all the data.
        final Query query = entityManager.createNativeQuery("SELECT DISTINCT fingerprint as fingerprint, username as username"
                + " FROM CertificateData WHERE "
                + "issuerDN IN (:cas) AND "
                // If the list of certificate profiles is empty, ignore it as a parameter
                + (!certificateProfiles.isEmpty() ? "certificateProfileId IN (:certificateProfiles) AND" : "")
                + "(expireDate>:activeNotifiedExpireDateMin) AND " + "(expireDate<:activeNotifiedExpireDateMax) AND (status=:status1"
                + " OR status=:status2) AND (expireDate>=:activeExpireDateMin OR " + "status=:status3)", "FingerprintUsernameSubset");
        query.setParameter("cas", cas);
        if(!certificateProfiles.isEmpty()) {
            query.setParameter("certificateProfiles", certificateProfiles);
        }
        query.setParameter("activeNotifiedExpireDateMin", activeNotifiedExpireDateMin);
        query.setParameter("activeNotifiedExpireDateMax", activeNotifiedExpireDateMax);
        query.setParameter("status1", CertificateConstants.CERT_ACTIVE);
        query.setParameter("status2", CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
        query.setParameter("activeExpireDateMin", activeExpireDateMin);
        query.setParameter("status3", CertificateConstants.CERT_ACTIVE);
        // How to debug log the SQL query:
        // log.debug("findExpirationInfo: "+query.unwrap(org.hibernate.Query.class).getQueryString());
        return query.getResultList();
    }

    @Override
    public List<CertificateInfo> findOldCertificates(final Collection<String> issuerDns, final Date expiredBefore, final int maxNumberOfResults) {
        final StringBuilder sb = new StringBuilder(SELECT_QUERY_FOR_CERTIFICATEINFO_SUBSET);
        sb.append("WHERE a.expireDate <= :expiredBefore ");
        if (issuerDns != null) {
            sb.append(" AND a.issuerDN IN (:issuerDns)");
        }
        // Use ABS to prevent the optimizer from using an index
        sb.append(" AND ABS(a.type+1)-1 IN (:types)");

        final String queryString = sb.toString();
        if (log.isTraceEnabled()) {
            log.trace("findOldCertificates query: " + LogRedactionUtils.getRedactedMessage(queryString));
        }
        final Query query = entityManager.createNativeQuery(queryString, "CertificateInfoSubset");
        query.setParameter("expiredBefore", expiredBefore.getTime());
        query.setParameter("types", Arrays.asList(CertificateConstants.CERTTYPE_ENDENTITY, CertificateConstants.CERTTYPE_SSH));
        if (issuerDns != null) {
            query.setParameter("issuerDns", issuerDns);
        }
        query.setMaxResults(maxNumberOfResults);
        final List<?> dbResults = query.getResultList();
        return dbResults.stream()
                .map(dbResult -> resultToCertificateData((Object[]) dbResult))
                .collect(toList());
    }

}
