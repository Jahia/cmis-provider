/**
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *                                  http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2016 Jahia Solutions Group. All rights reserved.
 *
 *     This file is part of a Jahia's Enterprise Distribution.
 *
 *     Jahia's Enterprise Distributions must be used in accordance with the terms
 *     contained in the Jahia Solutions Group Terms & Conditions as well as
 *     the Jahia Sustainable Enterprise License (JSEL).
 *
 *     For questions regarding licensing, support, production usage...
 *     please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
 */
package org.jahia.modules.external.cmis;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.services.content.JCRSessionFactory;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


/**
 * ExternalDataSource implementation for Alfresco 4.2 or 5.0 CMIS to handle user rights on documents, based on CmisProviderFactory
 *
 * @author toto
 */
public class AlfrescoCmisDataSource extends CmisDataSource implements ExternalDataSource.Initializable {
    private static final Logger log = LoggerFactory.getLogger(AlfrescoCmisDataSource.class);

    private Client client;
    private Cache<String, Session> sessionCache;

    public AlfrescoCmisDataSource() {
    }

    @Override
    public void start() {
        super.start();
        client = ClientBuilder.newBuilder().build();

        // cache config
        sessionCache = CacheBuilder.newBuilder()
                .concurrencyLevel(Integer.parseInt(getConf().getRepositoryPropertiesMap().get("org.jahia.cmis.alfresco.session.cache.concurrencyLevel")))
                .maximumSize(Integer.parseInt(getConf().getRepositoryPropertiesMap().get("org.jahia.cmis.alfresco.session.cache.maximumSize")))
                .expireAfterAccess(Integer.parseInt(getConf().getRepositoryPropertiesMap().get("org.jahia.cmis.alfresco.session.cache.expireAfterAccess")), TimeUnit.MINUTES)
                .build();
    }

    @Override
    public void stop() {
        // do not call super.stop() because CmisDataSource will try to close the unique session he use
        client.close();

        // clear all sessions
        for (Session cmisSession : sessionCache.asMap().values()) {
            cmisSession.clear();
        }
        sessionCache.invalidateAll();
    }

    public Session getCmisSession() throws CantConnectCmis {
        try {
            return sessionCache.get(ExternalContentStoreProvider.getCurrentSession().getUserID(), new Callable<Session>() {
                @Override
                public Session call() throws Exception {
                    HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
                    String username = repositoryPropertiesMap.get("org.apache.chemistry.opencmis.user");
                    String password = repositoryPropertiesMap.get("org.apache.chemistry.opencmis.password");
                    String url = repositoryPropertiesMap.get("alfresco.url");
                    SessionFactoryImpl factory = SessionFactoryImpl.newInstance();
                    // create session
                    HashMap<String, String> propertiesMap = new HashMap<>(repositoryPropertiesMap);

                    if (!ExternalContentStoreProvider.getCurrentSession().getUserID().startsWith(" system ") &&
                            !ExternalContentStoreProvider.getCurrentSession().getUserID().equals("root")) {
                        WebTarget target = client.target(url).
                                path("service/impersonateLogin").
                                queryParam("u", username).
                                queryParam("pw", password).
                                queryParam("format", "json").
                                queryParam("username", JCRSessionFactory.getInstance().getCurrentUser().getName());

                        String response = target.request().accept(MediaType.APPLICATION_JSON).get(String.class);
                        JSONObject obj = new JSONObject(response);
                        String ticket = obj.getJSONObject("data").getString("ticket");
                        propertiesMap.put("org.apache.chemistry.opencmis.user", "ROLE_TICKET");
                        propertiesMap.put("org.apache.chemistry.opencmis.password", ticket);
                    }

                    return factory.createSession(propertiesMap);
                }
            });
        } catch (ExecutionException e) {
            throw new CantConnectCmis(e);
        }
    }
}