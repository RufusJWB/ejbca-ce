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
package org.ejbca.ui.web.admin;

import java.io.IOException;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.authentication.AuthenticationNotProvidedException;
import org.cesecore.authentication.oauth.OAuthGrantResponseInfo;
import org.cesecore.authentication.oauth.OAuthKeyInfo;
import org.cesecore.authentication.oauth.OauthRequestHelper;
import org.cesecore.certificates.certificate.CertificateStoreSessionLocal;
import org.cesecore.keybind.InternalKeyBindingMgmtSessionLocal;
import org.cesecore.keybind.KeyBindingFinder;
import org.cesecore.keybind.KeyBindingNotFoundException;
import org.cesecore.keys.token.CryptoTokenManagementSessionLocal;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.ejbca.config.WebConfiguration;
import org.ejbca.ui.web.jsf.configuration.EjbcaWebBean;
import org.ejbca.util.HttpTools;

/**
 * Bean used to display a login page.
 */
@Named
@SessionScoped
public class AdminLoginMBean extends BaseManagedBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(AdminLoginMBean.class);

    private EjbcaWebBean ejbcaWebBean;

    private List<Throwable> throwables = null;
    private Collection<OAuthKeyInfoGui> oauthKeys = null;
    /** A random identifier used to link requests, to avoid CSRF attacks. */
    private String stateInSession = null;
    private String oauthClicked = null;

    @EJB
    private CryptoTokenManagementSessionLocal cryptoToken;
    @EJB
    private CertificateStoreSessionLocal certificateStoreLocal;
    @EJB
    private InternalKeyBindingMgmtSessionLocal internalKeyBindings;

    public class OAuthKeyInfoGui{
        String label;

        public OAuthKeyInfoGui(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    private String firstHeader;
    private String secondHeader;
    private String text;
    private OauthRequestHelper oauthRequestHelper;

    /**
     * Set the helper object that interacts with OAuth servers.
     */
    @PostConstruct
    public void setRequestHelper() {
        oauthRequestHelper = new OauthRequestHelper(new KeyBindingFinder(internalKeyBindings, certificateStoreLocal, cryptoToken));
    }
    
    /**
     * @return the general error which occurred, or welcome header
     */
    public String getFirstHeader() {
        return firstHeader;
    }

    /**
     * @return error message generated by application exceptions, or welcome text
     */
    public String getSecondHeader() {
        return secondHeader;
    }

    /**
     * @return help text to show below message
     */
    public String getText() {
        return text;
    }

    /**
     * without access to template, we have to fetch the CSS manually
     *
     * @return path to admin web CSS file
     **/
    public String getCssFile() {
        try {
            return ejbcaWebBean.getBaseUrl() + "/" + ejbcaWebBean.getCssFile();
        } catch (Exception e) {
            // This happens when EjbcaWebBeanImpl fails to initialize.
            // That is already logged in EjbcaWebBeanImpl.getText, so log at debug level here.
            final String msg = "Caught exception when trying to get stylesheet URL, most likely EjbcaWebBean failed to initialized";
            if (log.isTraceEnabled()) {
                log.debug(msg, e);
            } else {
                log.debug(msg);
            }
            return "exception_in_getCssFile";
        }
    }

    /**
     * Invoked when login.xhtml is rendered. Show errors and possible login links.
     */
    @SuppressWarnings("unchecked")
    public void onLoginPageLoad() throws Exception {
        ejbcaWebBean = getEjbcaErrorWebBean();
        HttpServletRequest servletRequest = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        ejbcaWebBean.initialize_errorpage(servletRequest);
        firstHeader = ejbcaWebBean.getText("AUTHORIZATIONDENIED");
        secondHeader = "";
        text = "";
        boolean showWelcomePage = false;
        final Map<String, Object> requestMap = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
        final Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        final String authCode = params.get("code");
        final String state = params.get("state");
        final String error = params.get("error");
        // Render error caught by CaExceptionHandlerFactory
        if (requestMap.containsKey(CaExceptionHandlerFactory.REQUESTMAP_KEY)) {
            throwables = (List<Throwable>) requestMap.remove(CaExceptionHandlerFactory.REQUESTMAP_KEY);
        }
        if (CollectionUtils.isNotEmpty(throwables)) {
            for (final Throwable throwable : throwables) {
                if (log.isDebugEnabled()) {
                    log.debug("Error occurred.", throwable);
                }
                if (throwable instanceof AuthenticationNotProvidedException) {
                    // Authentication was not attempted, but is required
                    showWelcomePage = true;
                } else {
                    // Authentication failed
                    secondHeader = ejbcaWebBean.getText("CAUSE") + ": " + throwable.getMessage();
                    if (CollectionUtils.isNotEmpty(oauthKeys)) { // List is still shown. Can happen if an OAuth token can't be refreshed.
                        text = ejbcaWebBean.getText("LOGINWELCOME3_OAUTH");
                    }
                }
            }
        }

        if (error != null) {
            log.info("Server reported user authentication failure: " + error.replaceAll("[^a-zA-Z0-9_]", "")); // sanitize untrusted parameter
            if (verifyStateParameter(state)) {
                secondHeader = params.getOrDefault("error_description", "");
                text = ejbcaWebBean.getText("ERRORFROMAUTHPROVIDER");
            } else {
                internalError("Received 'error' parameter without valid 'state' parameter.");
            }
        }
        else if (StringUtils.isNotEmpty(authCode)) {
            requestTokenUsingCode(servletRequest, params);
        } else {
            log.debug("Generating randomized 'state' string.");
            final byte[] stateBytes = new byte[32];
            new SecureRandom().nextBytes(stateBytes);
            stateInSession = Base64.encodeBase64URLSafeString(stateBytes);
            initOauthProviders();
            if (showWelcomePage) {
                showWelcomeText();
            }
            log.debug("Showing login links.");
        }
    }

    private void showWelcomeText() {
        if (CollectionUtils.isNotEmpty(oauthKeys)) {
            firstHeader = ejbcaWebBean.getText("LOGINWELCOME1_OAUTH");
            secondHeader = ejbcaWebBean.getText("LOGINWELCOME2_OAUTH");
            text = ejbcaWebBean.getText("LOGINWELCOME3_OAUTH");
        } else {
            // No OAuth providers. Assume it is a missing client certificate
            secondHeader = ejbcaWebBean.getText("LOGINWELCOME2_CERT");
            text = ejbcaWebBean.getText("LOGINWELCOME3_CERT");
        }
    }

    private void requestTokenUsingCode(HttpServletRequest servletRequest, Map<String, String> params) throws IOException {
        log.debug("Received authorization code. Requesting token from authorization server.");
        final String authCode = params.get("code");
        final String state = params.get("state");
        if (verifyStateParameter(state)) {
            OAuthKeyInfo oAuthKeyInfo = ejbcaWebBean.getOAuthConfiguration().getOauthKeyByLabel(oauthClicked);
            if (oAuthKeyInfo != null) {
                try {
                    
                    OAuthGrantResponseInfo token = oauthRequestHelper.sendTokenRequest(oAuthKeyInfo, authCode, getRedirectUri());
                    if (token.compareTokenType(HttpTools.AUTHORIZATION_SCHEME_BEARER)) {
                        if (token.getAccessToken() != null) {
                            log.debug("Successfully obtained oauth token, redirecting to main page.");
                            servletRequest.getSession(true).setAttribute("ejbca.bearer.token", token.getAccessToken());
                            servletRequest.getSession(true).setAttribute("ejbca.refresh.token", token.getRefreshToken());
                            FacesContext.getCurrentInstance().getExternalContext().redirect("index.xhtml");
                        } else {
                            internalError("Did not receive any access token from OAuth provider.");
                        }
                    } else {
                        internalError("Received OAuth token of unsupported type '" + token.getTokenType() + "'");
                    }
                } catch (CryptoTokenOfflineException | KeyBindingNotFoundException e) {
                    log.info(e);
                    internalError("Unable to sign request for oauth token with configuration " + oAuthKeyInfo.getLabel() + ". " + e.getMessage());
                }
            } else {
                internalError("Received provider identifier does not correspond to existing oauth providers. Key indentifier = " + oauthClicked);
            }
        } else {
            internalError("Received 'code' parameter without valid 'state' parameter.");
        }
    }

    private void internalError(final String logMessage) {
        log.info(logMessage);
        firstHeader = ejbcaWebBean.getText("ERROR");
        secondHeader = ejbcaWebBean.getText("INTERNALERROR");
    }

    private String getRedirectUri() {
        return ejbcaWebBean.getGlobalConfiguration().getBaseUrl(
                "https",
                WebConfiguration.getHostName(),
                WebConfiguration.getPublicHttpsPort()
        ) + ejbcaWebBean.getGlobalConfiguration().getAdminWebPath();
    }

    private boolean verifyStateParameter(final String state) {
        return stateInSession != null && stateInSession.equals(state);
    }

    /**
     * Returns a list of OAuth Keys containing url.
     *
     * @return a list of OAuth Keys containing url
     */
    public Collection<OAuthKeyInfoGui> getAllOauthKeys() {
        if (oauthKeys == null) {
            initOauthProviders();
        }
        return oauthKeys;
    }

    private void initOauthProviders() {
        StringBuilder providerUrls = new StringBuilder();
        oauthKeys = new ArrayList<>();
        ejbcaWebBean.reloadGlobalConfiguration();
        Collection<OAuthKeyInfo> oAuthKeyInfos = ejbcaWebBean.getOAuthConfiguration().getOauthKeys().values();
        if (!oAuthKeyInfos.isEmpty()) {
            for (OAuthKeyInfo oauthKeyInfo : oAuthKeyInfos) {
                if (StringUtils.isNotEmpty(oauthKeyInfo.getUrl())) {
                    oauthKeys.add(new OAuthKeyInfoGui(oauthKeyInfo.getLabel()));
                    providerUrls.append(oauthKeyInfo.getUrl()).append(" ");
                }
            }
            replaceHttpHeaders(providerUrls.toString());
        }
    }

    private String getOauthLoginUrl(OAuthKeyInfo oauthKeyInfo) {
        String url = oauthKeyInfo.getOauthLoginUrl();
        return addParametersToUrl(oauthKeyInfo, url);
    }

    private String addParametersToUrl(OAuthKeyInfo oauthKeyInfo, String url) {
        UriBuilder uriBuilder = UriBuilder.fromUri(url);
        String scope = "openid";
        if (oauthKeyInfo.getType().equals(OAuthKeyInfo.OAuthProviderType.TYPE_AZURE)) {
            scope += " offline_access " + oauthKeyInfo.getScope();
        }
        if (oauthKeyInfo.getType().equals(OAuthKeyInfo.OAuthProviderType.TYPE_KEYCLOAK) && !oauthKeyInfo.isAudienceCheckDisabled()) {
            scope += " " + oauthKeyInfo.getAudience();
        }
        uriBuilder
                .queryParam("scope", scope)
                .queryParam("client_id", oauthKeyInfo.getClient())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", getRedirectUri())
                .queryParam("state", stateInSession);
        return uriBuilder.build().toString();
    }

    public void clickLoginLink(String keyLabel) throws IOException {
        final OAuthKeyInfo oAuthKeyInfo = ejbcaWebBean.getOAuthConfiguration().getOauthKeyByLabel(keyLabel);
        if (oAuthKeyInfo != null) {
            oauthClicked = keyLabel;
            String url = getOauthLoginUrl(oAuthKeyInfo);
            FacesContext.getCurrentInstance().getExternalContext().redirect(url);
        } else {
            log.info("Trusted provider info not found for label =" + keyLabel);
        }
    }

    private void replaceHttpHeaders(String urls) {
        HttpServletResponse httpResponse = (HttpServletResponse)FacesContext.getCurrentInstance().getExternalContext().getResponse();
        String header = httpResponse.getHeader("Content-Security-Policy");
        header = header.replace("form-action 'self'", "form-action " + urls + "'self'");
        httpResponse.setHeader("Content-Security-Policy", header);
        httpResponse.setHeader("X-Content-Security-Policy", header);
    }
}
