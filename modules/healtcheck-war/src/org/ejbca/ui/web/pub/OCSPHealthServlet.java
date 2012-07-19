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
package org.ejbca.ui.web.pub;

import javax.ejb.EJB;

import org.ejbca.core.ejb.config.HealthCheckSessionLocal;
import org.ejbca.ui.web.pub.cluster.IHealthCheck;
import org.ejbca.ui.web.pub.cluster.IHealthResponse;
import org.ejbca.ui.web.pub.cluster.TextResponse;
import org.ejbca.ui.web.pub.cluster.ValidationAuthorityHealthCheck;

/**
 * OCSP/Validation Authority health check entry point.
 * @version $Id$
 */
public class OCSPHealthServlet extends AbstractHealthServlet {

    private static final long serialVersionUID = -5943655956890993863L;

    @EJB
    private HealthCheckSessionLocal healthCheckSession;

    private IHealthCheck validationAuthorityHealthCheck;
    private TextResponse textResponse;
    
    @Override
    public IHealthCheck getHealthCheck() {
        return validationAuthorityHealthCheck;
    }

    @Override
    public IHealthResponse getHealthResponse() {
        return textResponse;
    }

    @Override
    public void initializeServlet() {
        validationAuthorityHealthCheck = new ValidationAuthorityHealthCheck(healthCheckSession);  
        textResponse = new TextResponse();
    }
}
