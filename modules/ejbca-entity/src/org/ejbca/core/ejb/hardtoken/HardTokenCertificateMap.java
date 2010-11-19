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

package org.ejbca.core.ejb.hardtoken;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Version;

import org.apache.log4j.Logger;

/**
 * Representation of certificates placed on a token.
 * 
 * @version $Id$
 */
@Entity
@Table(name="HardTokenCertificateMap")
public class HardTokenCertificateMap implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(HardTokenCertificateMap.class);

	private String certificateFingerprint;
	private String tokenSN;
	private int rowVersion = 0;
	private String rowProtection;

	/**
	 * Entity holding data of a certificate to hard token relation.
	 */
	public HardTokenCertificateMap(String certificateFingerprint, String tokenSN) {
		setCertificateFingerprint(certificateFingerprint);
		setTokenSN(tokenSN);
		log.debug("Created HardTokenCertificateMap for token SN: "+ tokenSN );
	}
	
	public HardTokenCertificateMap() { }

	@Id
	@Column(name="certificateFingerprint")
	public String getCertificateFingerprint() { return certificateFingerprint; }
	public void setCertificateFingerprint(String certificateFingerprint) { this.certificateFingerprint = certificateFingerprint; }

	@Column(name="tokenSN")
	public String getTokenSN() { return tokenSN; }
	public void setTokenSN(String tokenSN) { this.tokenSN = tokenSN; }

	@Version
	@Column(name = "rowVersion", nullable = false, length = 5)
	public int getRowVersion() { return rowVersion; }
	public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

	@Column(name = "rowProtection", length = 10*1024)
	@Lob
	public String getRowProtection() { return rowProtection; }
	public void setRowProtection(String rowProtection) { this.rowProtection = rowProtection; }

	//
	// Search functions. 
	//

	/** @return the found entity instance or null if the entity does not exist */
	public static HardTokenCertificateMap findByCertificateFingerprint(EntityManager entityManager, String certificateFingerprint) {
		return entityManager.find(HardTokenCertificateMap.class, certificateFingerprint);
	}

	/** @return return the query results as a List. */
	public static List<HardTokenCertificateMap> findByTokenSN(EntityManager entityManager, String tokenSN) {
		Query query = entityManager.createQuery("SELECT a FROM HardTokenCertificateMap a WHERE a.tokenSN=:tokenSN");
		query.setParameter("tokenSN", tokenSN);
		return query.getResultList();
	}
}
