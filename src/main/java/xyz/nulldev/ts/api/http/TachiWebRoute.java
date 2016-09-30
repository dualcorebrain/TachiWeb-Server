/*
 * Copyright 2016 Andy Bao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.nulldev.ts.api.http;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;
import uy.kohesive.injekt.InjektKt;
import xyz.nulldev.ts.api.http.auth.SessionManager;
import xyz.nulldev.ts.library.Library;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Project: TachiServer
 * Author: nulldev
 * Creation Date: 17/07/16
 */
public abstract class TachiWebRoute implements Route {

    private boolean requiresAuth = true;

    private static Logger logger = LoggerFactory.getLogger(TachiWebRoute.class);

    private static SessionManager sessionManager = new SessionManager();

    public TachiWebRoute() {
    }

    public TachiWebRoute(boolean requiresAuth) {
        this.requiresAuth = requiresAuth;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        try {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Credentials", "true");
            String session = request.cookie("session");
            if(session == null || session.trim().isEmpty()) {
                session = sessionManager.newSession();
                response.removeCookie("session");
                response.cookie("session", session);
            }
            request.attribute("session", session);
            //Auth all sessions if auth not enabled
            if(!SessionManager.Companion.authEnabled()) {
                sessionManager.authenticateSession(session);
            }
            if(!sessionManager.isAuthenticated(session) && requiresAuth) {
                //Not authenticated!
                return error("Not authenticated!");
            } else {
                ReentrantLock masterLock = getLibrary().getMasterLock().get();
                if (masterLock != null) {
                    masterLock.lock();
                    try {
                        Object toReturn = handleReq(request, response);
                        masterLock.unlock();
                        return toReturn;
                    } catch (Throwable e) {
                        masterLock.unlock();
                        throw e;
                    }
                } else {
                    return handleReq(request, response);
                }
            }
        } catch (Exception e) {
            logger.error("Exception handling route!", e);
            throw e;
        }
    }

    public abstract Object handleReq(Request request, Response response) throws Exception;

    public Library getLibrary() {
        return InjektKt.getInjekt().getInstance(Library.class);
    }

    public static String error(String message) {
        JSONObject object = success(false);
        object.put("error", message);
        return object.toString();
    }

    public static JSONObject success() {
        return success(true);
    }

    public static JSONObject success(boolean success) {
        JSONObject object = new JSONObject();
        object.put("success", success);
        return object;
    }

    public static SessionManager getSessionManager() {
        return sessionManager;
    }
}
