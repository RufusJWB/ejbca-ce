/*************************************************************************
 *                                                                       *
 *  EJBCA - Proprietary Modules: Enterprise Certificate Authority        *
 *                                                                       *
 *  Copyright (c), PrimeKey Solutions AB. All rights reserved.           *
 *  The use of the Proprietary Modules are subject to specific           *
 *  commercial license terms.                                            *
 *                                                                       *
 *************************************************************************/
package org.ejbca.ui.web.rest.api.io.request;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.core.Response;

import org.cesecore.certificates.certificate.CertificateConstants;
import org.ejbca.core.model.era.RaCertificateSearchRequest;
import org.ejbca.ui.web.rest.api.exception.RestException;
import org.ejbca.ui.web.rest.api.validator.ValidSearchCertificateCriteriaRestRequestList;
import org.ejbca.ui.web.rest.api.validator.ValidSearchCertificatePagination;

import io.swagger.annotations.ApiModelProperty;

import static org.ejbca.ui.web.rest.api.io.request.SearchCertificatesRestRequestUtil.parseDateFromStringValue;

/**
 * JSON input for a certificate search V2 containing multiple search criteria and pagination.
 * 
 * @see org.ejbca.ui.web.rest.api.io.request.Pagination
 *
 * The properties of this class has to be valid.
 *
 * @see org.ejbca.ui.web.rest.api.validator.ValidSearchCertificateCriteriaRestRequestList
 * @see org.ejbca.ui.web.rest.api.validator.ValidSearchCertificateCriteriaRestRequest
 * @see org.ejbca.ui.web.rest.api.validator.ValidSearchCertificatePagination
 */
public class SearchCertificatesRestRequestV2 implements SearchCertificateCriteriaRequest {

    @ApiModelProperty(value = "Pagination." )
    @ValidSearchCertificatePagination
    private Pagination pagination;
    
    @ApiModelProperty(value = "A List of search criteria." )
    @ValidSearchCertificateCriteriaRestRequestList
    @Valid
    private List<SearchCertificateCriteriaRestRequest> criteria = new ArrayList<>();

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    @Override
    public List<SearchCertificateCriteriaRestRequest> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<SearchCertificateCriteriaRestRequest> criteria) {
        this.criteria = criteria;
    }

    /**
     * Return a builder instance for this class.
     *
     * @return builder instance for this class.
     */
    public static SearchCertificatesRestRequestBuilderV2 builder() {
        return new SearchCertificatesRestRequestBuilderV2();
    }

    public static class SearchCertificatesRestRequestBuilderV2 {
        private Pagination pagination;
        private List<SearchCertificateCriteriaRestRequest> criteria;

        private SearchCertificatesRestRequestBuilderV2() {
        }

        public SearchCertificatesRestRequestBuilderV2 pagination(final Pagination pagination) {
            this.pagination = pagination;
            return this;
        }

        public SearchCertificatesRestRequestBuilderV2 criteria(final List<SearchCertificateCriteriaRestRequest> criteria) {
            this.criteria = criteria;
            return this;
        }

        public SearchCertificateCriteriaRequest build() {
            final SearchCertificatesRestRequestV2 result = new SearchCertificatesRestRequestV2();
            result.setPagination(pagination);
            result.setCriteria(criteria);
            return result;
        }
    }

    /**
     * Returns a converter instance for this class.
     *
     * @return instance of converter for this class.
     */
    public static SearchCertificatesRestRequestConverterV2 converter() {
        return new SearchCertificatesRestRequestConverterV2();
    }

    public static class SearchCertificatesRestRequestConverterV2 {

        public RaCertificateSearchRequest toEntity(final SearchCertificatesRestRequestV2 restRequest) throws RestException {
            if(restRequest.getCriteria() == null || restRequest.getCriteria().isEmpty()) {
                throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), "Malformed request.");
            }
            final RaCertificateSearchRequest raRequest = new RaCertificateSearchRequest();
            if(restRequest.getPagination() == null) {
                // Enables count rows only.
                raRequest.setPageNumber(-1);
            }
            final Pagination pagination = restRequest.getPagination();
            if (pagination != null) {
                raRequest.setMaxResults(pagination.getPageSize());
                raRequest.setPageNumber(pagination.getCurrentPage());
            }
            raRequest.setEepIds(new ArrayList<Integer>());
            raRequest.setCpIds(new ArrayList<Integer>());
            raRequest.setCaIds(new ArrayList<Integer>());
            raRequest.setStatuses(new ArrayList<Integer>());
            raRequest.setRevocationReasons(new ArrayList<Integer>());
            for(final SearchCertificateCriteriaRestRequest searchCertificateCriteriaRestRequest : restRequest.getCriteria()) {
                final SearchCertificateCriteriaRestRequest.CriteriaProperty criteriaProperty = SearchCertificateCriteriaRestRequest.CriteriaProperty.resolveCriteriaProperty(searchCertificateCriteriaRestRequest.getProperty());
                if(criteriaProperty == null) {
                    throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), "Malformed request.");
                }
                final String criteriaValue = searchCertificateCriteriaRestRequest.getValue();
                final SearchCertificateCriteriaRestRequest.CriteriaOperation criteriaOperation = SearchCertificateCriteriaRestRequest.CriteriaOperation.resolveCriteriaOperation(searchCertificateCriteriaRestRequest.getOperation());
                switch (criteriaProperty) {
                    case QUERY: {
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.EQUAL) {
                            raRequest.setSubjectDnSearchExact(true);
                            raRequest.setSubjectAnSearchExact(true);
                            raRequest.setUsernameSearchExact(true);
                            raRequest.setExternalAccountIdSearchExact(true);
                        }
                        raRequest.setSubjectDnSearchString(criteriaValue);
                        raRequest.setSubjectAnSearchString(criteriaValue);
                        raRequest.setUsernameSearchString(criteriaValue);
                        raRequest.setSerialNumberSearchStringFromDec(criteriaValue);
                        raRequest.setSerialNumberSearchStringFromHex(criteriaValue);
                        raRequest.setExternalAccountIdSearchString(criteriaValue);
                        break;
                    }
                    case END_ENTITY_PROFILE: {
                        raRequest.getEepIds().add(searchCertificateCriteriaRestRequest.getIdentifier());
                        break;
                    }
                    case EXTERNAL_ACCOUNT_BINDING_ID: {
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.EQUAL) {
                            raRequest.setExternalAccountIdSearchExact(true);
                        }
                        raRequest.setExternalAccountIdSearchString(criteriaValue);
                        break;
                    }
                    case CERTIFICATE_PROFILE: {
                        raRequest.getCpIds().add(searchCertificateCriteriaRestRequest.getIdentifier());
                        break;
                    }
                    case CA: {
                        raRequest.getCaIds().add(searchCertificateCriteriaRestRequest.getIdentifier());
                        break;
                    }
                    case STATUS: {
                        final SearchCertificateCriteriaRestRequest.CertificateStatus certificateStatus = SearchCertificateCriteriaRestRequest.CertificateStatus.resolveCertificateStatusByName(criteriaValue);
                        if(certificateStatus == null) {
                            throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), "Malformed request.");
                        }
                        if (certificateStatus == SearchCertificateCriteriaRestRequest.CertificateStatus.CERT_ACTIVE) {
                            raRequest.getStatuses().add(certificateStatus.getStatusValue());
                            // ECA-8578: when searching for active certificates we need to include certificates that are notified about expiration.
                            // Add this automatically to the search conditions.
                            raRequest.getStatuses().add(CertificateConstants.CERT_NOTIFIEDABOUTEXPIRATION);
                        }
                        if (certificateStatus == SearchCertificateCriteriaRestRequest.CertificateStatus.CERT_REVOKED) {
                            raRequest.getStatuses().add(certificateStatus.getStatusValue());
                        }
                        if (SearchCertificateCriteriaRestRequest.CertificateStatus.REVOCATION_REASONS().contains(certificateStatus)) {
                            raRequest.getRevocationReasons().add(certificateStatus.getStatusValue());
                        }
                        break;
                    }
                    case ISSUED_DATE: {
                        final long issuedDateLong = parseDateFromStringValue(criteriaValue).getTime();
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.AFTER) {
                            raRequest.setIssuedAfter(issuedDateLong);
                        }
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.BEFORE) {
                            raRequest.setIssuedBefore(issuedDateLong);
                        }
                        break;
                    }
                    case EXPIRE_DATE: {
                        final long expireDateLong = parseDateFromStringValue(criteriaValue).getTime();
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.AFTER) {
                            raRequest.setExpiresAfter(expireDateLong);
                        }
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.BEFORE) {
                            raRequest.setExpiresBefore(expireDateLong);
                        }
                        break;
                    }
                    case REVOCATION_DATE: {
                        final long revocationDateLong = parseDateFromStringValue(criteriaValue).getTime();
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.AFTER) {
                            raRequest.setRevokedAfter(revocationDateLong);
                        }
                        if (criteriaOperation == SearchCertificateCriteriaRestRequest.CriteriaOperation.BEFORE) {
                            raRequest.setRevokedBefore(revocationDateLong);
                        }
                        break;
                    }
                }
            }
            return raRequest;
        }
    }

}
