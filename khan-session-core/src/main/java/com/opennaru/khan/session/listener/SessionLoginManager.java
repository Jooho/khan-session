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
package com.opennaru.khan.session.listener;

import com.opennaru.khan.session.KhanHttpSession;
import com.opennaru.khan.session.KhanSessionKeyGenerator;
import com.opennaru.khan.session.KhanSessionMetadata;
import com.opennaru.khan.session.filter.KhanSessionFilter;
import com.opennaru.khan.session.management.SessionMonitorMBean;
import com.opennaru.khan.session.manager.KhanSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.io.Serializable;

/**
 * SessionLoginManager / 중복로그인 관리를 위한 Manager
 *
 * @author Junshik Jeon(service@opennaru.com, nameislocus@gmail.com)
 */
public class SessionLoginManager implements HttpSessionBindingListener, Serializable {
    private static final long serialVersionUID = 1L;
    private static SessionLoginManager loginManager = null;
    private transient Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Get Singleton instance
     * @return
     */
    public static SessionLoginManager getInstance() {
        if (loginManager == null) {
            loginManager = new SessionLoginManager();
        }

        return loginManager;
    }

    /**
     * Session value bound event / do nothing
     * @param event
     */
    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        if( log.isDebugEnabled() ) {
            log.debug("******** valueBound=" + event.getName());
            log.debug("******** valueBound=" + event.getValue().toString());
            log.debug("******** valueBound=" + event.getSession().getAttribute("khan.uid"));

            log.debug("  " + event.getName() + " Session created.");
        }
    }


    /**
     * Session value unbound event / do nothing
     * @param event
     */
    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        if( log.isDebugEnabled() ) {
            log.debug("******** valueUnbound=" + event.getName());
            log.debug("******** valueUnbound=" + event.getValue());

            // Get the session that was invalidated
            HttpSession session = event.getSession();
            String sessionId = session.getId();

            log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> destroy sessionEvent=" + event);
            log.debug("Session invalidated: " + sessionId);
            //String appName = event.getSession().getServletContext().getContextPath();

            log.debug("  " + event.getName() + " Session destroy.");
        }
    }

    // TODO
    public void removeSession(String userId) {

    }

    /**
     * 중복로그인을 위한 login 처리
     * khan.uid에 로그인 ID만 저장함
     *
     * @param request
     * @param uid
     * @throws Exception
     */
    public void login(HttpServletRequest request, String uid) throws Exception {

        if (uid == null || uid.equals(""))
            throw new Exception("uid is null!");

        HttpSession session = request.getSession();
        // 세션에 세팅
        session.setAttribute("khan.uid", uid);

        // 예전 로그인 정보 세션이 있는지..
        String previousSessionId = loggedInSessionId(uid);

        //
        if (previousSessionId != null && !previousSessionId.equals("")
                && !previousSessionId.equals(session.getId())) {

            if( log.isDebugEnabled() ) {
                log.debug("previousSessionId=" + previousSessionId);
                log.debug("sessionId=" + session.getId());
            }

            String appName = request.getContextPath();
            SessionMonitorMBean sessionMonitorMBean = KhanSessionManager.getInstance(appName).getSessionMonitor();
            if( sessionMonitorMBean != null )
                sessionMonitorMBean.duplicatedLogin();

            String sidKey = KhanSessionKeyGenerator.generate("$", "SID", previousSessionId);
            KhanSessionFilter.getSessionStore().loginPut(sidKey, "DUPLICATED", session.getMaxInactiveInterval());

            String previousMetaKey = KhanSessionKeyGenerator.generate(
                    KhanSessionFilter.getKhanSessionConfig().getNamespace(),
                    previousSessionId,
                    KhanHttpSession.METADATA_KEY);
            KhanSessionMetadata previousMetadata = KhanSessionFilter.getSessionStore().get(previousMetaKey);
            if( log.isDebugEnabled() ) {
                log.debug("PreviousSessionId=" + previousSessionId);
                log.debug("PreviousMetaData=" + previousMetadata);
            }

            String currentMetaKey = KhanSessionKeyGenerator.generate(
                    KhanSessionFilter.getKhanSessionConfig().getNamespace(),
                    session.getId(),
                    KhanHttpSession.METADATA_KEY);
            KhanSessionMetadata currentMetadata = KhanSessionFilter.getSessionStore().get(currentMetaKey);
            if( log.isDebugEnabled() ) {
                log.debug("CurrentSessionId=" + session.getId());
                log.debug("CurrentMetaData=" + currentMetadata);

                log.debug("uid=" + uid);
            }
            //System.out.println("####### duplicated login=" + uid);
//    		TODO : Send 중복 로그인 정보 to Server...

        }

        String uidKey = KhanSessionKeyGenerator.generate("$", "UID", uid);
        KhanSessionFilter.getSessionStore().loginPut(uidKey, session.getId(), session.getMaxInactiveInterval());

        String sidKey = KhanSessionKeyGenerator.generate("$", "SID", session.getId());
        KhanSessionFilter.getSessionStore().loginPut(sidKey, uid, session.getMaxInactiveInterval());

    }

    /**
     * 중복로그인을 위한 logout 처리
     * khan.uid를 삭제하고 Session Invalidate
     *
     * @param request
     * @throws Exception
     */
    public void logout(HttpServletRequest request) throws Exception {
        HttpSession session = request.getSession();
        session.removeAttribute("khan.uid");
        String sidKey = KhanSessionKeyGenerator.generate("$", "SID", session.getId());
        KhanSessionFilter.getSessionStore().loginRemove(sidKey);

//        session.invalidate();
    }

    /**
     * 사용자 id로 로그인한 Session ID 정보를 가져오기
     *
     * @param uid
     * @return
     */
    public String loggedInSessionId(String uid) {
        String key = KhanSessionKeyGenerator.generate("$", "UID", uid);
        String sessionId = KhanSessionFilter.getSessionStore().loginGet(key);
        if (sessionId == null) return "";
        else return sessionId;
    }

    /**
     * Request 객체로 로그인한 사용자 ID를 가져오기
     *
     * @param request
     * @return
     */
    public String loggedInUserId(HttpServletRequest request) {
        HttpSession session = request.getSession();
        return (String)session.getAttribute("khan.uid");
//        SessionLoginManager sessionLoginManager = (SessionLoginManager) session.getAttribute("khan.uid");

    }

}