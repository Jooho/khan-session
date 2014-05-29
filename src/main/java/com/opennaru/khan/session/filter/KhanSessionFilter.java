/*
 * Opennaru, Inc. http://www.opennaru.com/
 *
 *  Copyright (C) 2014 Opennaru, Inc. and/or its affiliates.
 *  All rights reserved by Opennaru, Inc.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.opennaru.khan.session.filter;

import com.opennaru.khan.session.KhanHttpSession;
import com.opennaru.khan.session.KhanSessionConfig;
import com.opennaru.khan.session.KhanSessionHttpRequest;
import com.opennaru.khan.session.KhanSessionKeyGenerator;
import com.opennaru.khan.session.listener.SessionLoginManager;
import com.opennaru.khan.session.manager.KhanSessionManager;
import com.opennaru.khan.session.store.SessionStore;
import com.opennaru.khan.session.util.CookieUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * KhanSessionFilter
 *
 * @author Junshik Jeon(service@opennaru.com, nameislocus@gmail.com)
 */
public abstract class KhanSessionFilter implements Filter {
    public static KhanSessionConfig khanSessionConfig = null;
    protected static SessionStore sessionStore;
    private Logger log = LoggerFactory.getLogger(this.getClass());
    private KhanSessionManager sessionManager = null;

    public static KhanSessionConfig getKhanSessionConfig() {
        return khanSessionConfig;
    }

    protected static String getConfigValue(FilterConfig config, String keyName) {
        String fromInitParam = config.getInitParameter(keyName);
        return fromInitParam;
    }

    protected static void setSessionStatus(HttpServletRequest req,
                                           SessionStatus status) {
        req.setAttribute(Constants.SESSION_STATUS, status);
    }

    protected static SessionStatus getSessionStatus(HttpServletRequest req) {
        Object status = req.getAttribute(Constants.SESSION_STATUS);
        if (status == null) {
            return SessionStatus.UNKNOWN;
        } else {
            return (SessionStatus) status;
        }
    }

    protected static boolean isValidSession(KhanSessionHttpRequest req) {
        if (getSessionStatus(req) == SessionStatus.FIXED) {
            return true;
        }
        return req.getSession().isValid();
    }

    protected static boolean isKhanSessionHttpRequest(HttpServletRequest req) {
        return req.getSession() instanceof KhanHttpSession;
    }

    public static SessionStore getSessionStore() {
        return sessionStore;
    }

    protected void getSessionFilterConfig(FilterConfig config) {

        khanSessionConfig = new KhanSessionConfig();

        // use library mode
        khanSessionConfig.setUseLibraryMode(getConfigValue(config, Constants.USE_LIBRARY_MODE) != null
                && getConfigValue(config, Constants.USE_LIBRARY_MODE).equals("true"));

        // namespace
        khanSessionConfig.setNamespace(getConfigValue(config, Constants.NAMESPACE));
        if (khanSessionConfig.getNamespace() == null) {
            khanSessionConfig.setNamespace(Constants.GLOBAL_NAMESPACE);
        }

        // exclude regexp
        khanSessionConfig.setExcludeRegExp(getConfigValue(config, Constants.EXCLUDE_REG_EXP));

        // session id
        khanSessionConfig.setSessionIdKey(getConfigValue(config, Constants.SESSION_ID));
        if (khanSessionConfig.getSessionIdKey() == null) {
            khanSessionConfig.setSessionIdKey(Constants.DEFAULT_SESSION_ID_NAME);
        }

        // domain name
        khanSessionConfig.setDomain(getConfigValue(config, Constants.DOMAIN));

        // path
        khanSessionConfig.setPath(getConfigValue(config, Constants.PATH));
        if (khanSessionConfig.getPath() == null) {
            khanSessionConfig.setPath("/");
        }

        // is secure
        khanSessionConfig.setSecure(getConfigValue(config, Constants.SECURE) != null
                && getConfigValue(config, Constants.SECURE).equals("true"));

        // http only
        khanSessionConfig.setHttpOnly(getConfigValue(config, Constants.HTTP_ONLY) != null
                && getConfigValue(config, Constants.HTTP_ONLY).equals("true"));

        // session time out
        String sessionTimeout = getConfigValue(config, Constants.SESSION_TIMEOUT);

        if (sessionTimeout == null) {
            khanSessionConfig.setSessionTimeoutMin(10);
        } else {
            khanSessionConfig.setSessionTimeoutMin(Integer.valueOf(sessionTimeout));
        }

        // allow duplicated login
        khanSessionConfig.setAllowDuplicateLogin(getConfigValue(config, Constants.ALLOW_DUPLICATE_LOGIN) != null
                && getConfigValue(config, Constants.ALLOW_DUPLICATE_LOGIN).equals("true"));

        // force logout url
        khanSessionConfig.setLogoutUrl(getConfigValue(config, Constants.LOGOUT_URL));
        if (khanSessionConfig.getLogoutUrl() == null) {
            khanSessionConfig.setLogoutUrl("");
        }
    }

    protected Cookie getCurrentValidSessionIdCookie(HttpServletRequest req) {
        if (req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if( cookie.getName().equals( khanSessionConfig.getSessionIdKey() )
                        && cookie.getValue() != null
                        && cookie.getValue().trim().length() > 0 ) {

                    if( isValidSession(createSessionRequest(req, cookie.getValue())) ) {
                        if (log.isDebugEnabled()) {
                            log.debug("SessionId cookie is found. ("
                                    + khanSessionConfig.getSessionIdKey() + " -> "
                                    + cookie.getValue() + ")");
                        }
                        return cookie;
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("SessionId cookie is found but it's invalid. ("
                                    + khanSessionConfig.getSessionIdKey()
                                    + " -> "
                                    + cookie.getValue() + ")");
                        }
                    }
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("SessionId cookie is not found.");
        }
        return null;
    }

    protected Cookie generateSessionIdCookie(String sessionIdValue) {

        Cookie sessionIdCookie = new Cookie(khanSessionConfig.getSessionIdKey(), sessionIdValue);
        if (khanSessionConfig.getDomain() != null) {
            sessionIdCookie.setDomain(khanSessionConfig.getDomain());
        }
        if (khanSessionConfig.getPath() != null) {
            sessionIdCookie.setPath(khanSessionConfig.getPath());
        } else {
            sessionIdCookie.setPath("/");
        }
        sessionIdCookie.setSecure(khanSessionConfig.isSecure());

        // httpOnly 는 Servlet 2.x에서 지원하지 않음. Error in WLS 11g
//        try {
//            sessionIdCookie.setHttpOnly(khanSessionConfig.isHttpOnly());
//        } catch( NoSuchFieldError e ) {
//        }

        return sessionIdCookie;
    }

    protected KhanSessionHttpRequest createSessionRequest(
            HttpServletRequest request, String sessionIdValue) {

        if (log.isDebugEnabled()) {
            log.debug("***** createSessionRequest");
        }

        return new KhanSessionHttpRequest(request, sessionIdValue,
                khanSessionConfig.getNamespace(), khanSessionConfig.getSessionTimeoutMin(),
                sessionStore, sessionManager);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        getSessionFilterConfig(config);

        if (sessionManager == null) {
            sessionManager = new KhanSessionManager(config.getServletContext().getContextPath(), sessionStore);
            if (log.isDebugEnabled()) {
                log.debug("***** init filter");
                log.debug("***** sessionManager=" + sessionManager);
                log.debug("***** sessionMonitor=" + sessionManager.getSessionMonitor());
            }

        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest _request = (HttpServletRequest) request;
        HttpServletResponse _response = (HttpServletResponse) response;

        if (isKhanSessionHttpRequest(_request)) {

            if (log.isDebugEnabled()) {
                log.debug("KhanSessionHttpRequest is already applied.");
            }
            chain.doFilter(_request, _response);

        } else if (khanSessionConfig.getExcludeRegExp() != null
                && _request.getRequestURI().matches(khanSessionConfig.getExcludeRegExp())) {

            if (log.isDebugEnabled()) {
                log.debug("This URI is excluded. (URI: " + _request.getRequestURI() + ")");
            }
            chain.doFilter(_request, _response);

        } else {

            Cookie currentValidSessionIdCookie = getCurrentValidSessionIdCookie(_request);

            String sessionIdValue = null;
            if (currentValidSessionIdCookie == null) {
//                if( _request.getSession().isNew() ) {
//                    sessionIdValue = UUID.randomUUID().toString();
//                } else {
                    // copy JSESSIONID value to original session
                    sessionIdValue = _request.getSession().getId();
//                }
            } else {
                //System.out.println(">> current session cookie=" + currentValidSessionIdCookie.getValue() );
                //System.out.println(">> session id=" + _request.getSession().getId());
                //if( currentValidSessionIdCookie.getValue().equals())
                // current session is valid
                sessionIdValue = currentValidSessionIdCookie.getValue();
            }

            //if( currentValidSessionIdCookie != null )
            //    System.out.println("currentValidSessionIdCookie.getValue()=" + currentValidSessionIdCookie.getValue());
            //System.out.println("sessionIdValue=" + sessionIdValue);

            if (currentValidSessionIdCookie == null) {
                Cookie newSessionIdCookie = generateSessionIdCookie(sessionIdValue);
                // httpOnly 는 Servlet 2.x에서 지원하지 않음.
                // addCookie대신 addHeader를 사용
                String setCookie = CookieUtil.createCookieHeader(newSessionIdCookie, khanSessionConfig.isHttpOnly());
                _response.addHeader("Set-Cookie", setCookie);
                setSessionStatus(_request, SessionStatus.FIXED);

                if (log.isDebugEnabled()) {
                    log.debug("SessionId cookie is updated. (" + sessionIdValue + ")");
                }
            }

            // doFilter with the request wrapper
            KhanSessionHttpRequest _wrappedRequest = createSessionRequest(_request, sessionIdValue);

            boolean redirectLogoutUrl = false;
            String khan_uid = "";

            if (khanSessionConfig.isAllowDuplicateLogin() == false) {

//				if( khanSessionConfig.isUseAuthenticator() ) {
//					try {
//						khan_uid = _wrappedRequest.getUserPrincipal().getName();
//					} catch(NullPointerException e) {
//					}
//				} else {
//				}
                khan_uid = (String) _wrappedRequest.getSession().getAttribute("khan.uid");
                if (log.isDebugEnabled()) {
                    log.debug("$$$$$ khan_uid=" + khan_uid);
                }
                String key = KhanSessionKeyGenerator.generate("$", "SID", _wrappedRequest.getSession().getId());
                String loginStatus = KhanSessionFilter.getSessionStore().loginGet(key);
                if (log.isDebugEnabled()) {
                    log.debug("$$$$$ loginStatus=" + loginStatus);
                }
                if (loginStatus != null && loginStatus.equals("DUPLICATED")) {
                    redirectLogoutUrl = true;
                } else {
                    // update login info
                    if (khan_uid != null && !khan_uid.equals("")) {
                        try {
                            SessionLoginManager.getInstance().login(_wrappedRequest, khan_uid);
                        } catch (Exception e) {
                            log.error("login ", e);
                        }
//						if( khanSessionConfig.isUseAuthenticator() ) {
//							// Tomcat : Authenticator.login(request, response, username, password, rememberMe)
//							if( _wrappedRequest.getUserPrincipal() == null )
//								_wrappedRequest.login(khan_uid, khan_uid);
//						}
                        if (log.isDebugEnabled()) {
                            log.debug("$$$$$ login");
                        }
                    }
                }
            }

            // do KHAN Session filter
            chain.doFilter(_wrappedRequest, _response);
            // after KHAN Session filter

            // update attributes, expiration
            KhanHttpSession session = _wrappedRequest.getSession();

            _request.getSession().setAttribute("khan.session.id", session.getId());

            session.reloadAttributes(); // need reloading from the store to work

            session.save();

            if (redirectLogoutUrl) {
                try {
                    request.getRequestDispatcher(khanSessionConfig.getLogoutUrl()).forward(request, response);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (log.isDebugEnabled())
                log.debug("*************************************************************************************");
        }
    }

    @Override
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("KhanSessionFilter destroy..");
        }
        sessionManager.destroy();
    }
}