/**
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 * http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 * Copyright (C) 2002-2016 Jahia Solutions Group. All rights reserved.
 *
 * This file is part of a Jahia's Enterprise Distribution.
 *
 * Jahia's Enterprise Distributions must be used in accordance with the terms
 * contained in the Jahia Solutions Group Terms & Conditions as well as
 * the Jahia Sustainable Enterprise License (JSEL).
 *
 * For questions regarding licensing, support, production usage...
 * please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
 */
package org.jahia.modules.external.cmis;

import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUnauthorizedException;
import org.apache.commons.lang.StringUtils;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalDataSource;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;


/**
 * ExternalDataSource implementation for Alfresco 4.2 or 5.0 CMIS to handle user rights on documents, based on CmisProviderFactory
 *
 * @author toto
 */
public class AlfrescoCmisDataSource extends CmisDataSource implements ExternalDataSource.Initializable {
    private static final Logger log = LoggerFactory.getLogger(AlfrescoCmisDataSource.class);

    private Client client;
    private String publicUser;

    public AlfrescoCmisDataSource() {
    }

    @Override
    public void start() {
        super.start();
        HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
        client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basic(repositoryPropertiesMap.get(SessionParameter.USER),
                        repositoryPropertiesMap.get(SessionParameter.PASSWORD)))
                .build();
    }

    @Override
    public void stop() {
        super.stop();
        client.close();
    }

    @Override
    public synchronized Session getCmisSession(final String user) throws CantConnectCmis {
        try {
           ;
            return activeConnections.get(user, new Callable<Session>() {
                @Override
                public Session call() throws Exception {
                    HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
                    SessionFactoryImpl factory = SessionFactoryImpl.newInstance();
                    // create session

                    HashMap<String, String> propertiesMap = new HashMap<>(repositoryPropertiesMap);
                    try {
                        if (!user.startsWith(" system ") &&
                                !user.equals("root")) {
                            if (user.trim().equals(Constants.GUEST_USERNAME)) {
                                // guest user is not authorize to browse cmis repo, public user should be use if set
                                throw new CmisUnauthorizedException();
                            }
                            setConnectionProperties(propertiesMap, user);
                        }
                        return factory.createSession(propertiesMap);
                    } catch (CmisUnauthorizedException e) {
                        // log failed try to connect with public user
                        if (StringUtils.isEmpty(publicUser)) {
                            throw new CmisUnauthorizedException("You cannot access Alfresco as guest user, please set a public user in your configuration");
                        } else {
                            setConnectionProperties(propertiesMap, publicUser);
                            return factory.createSession(propertiesMap);
                        }
                    }
                }

                private void setConnectionProperties(HashMap<String, String> propertiesMap, String user) throws JSONException {
                    WebTarget target = client.target(getConf().getRepositoryPropertiesMap().get(CmisProviderFactory.ALFRESCO_URL)).
                            path("service/impersonateLogin").
                            queryParam("format", "json").
                            queryParam("username", user);
                    String response = target.request().accept(MediaType.APPLICATION_JSON).get(String.class);
                    JSONObject obj = new JSONObject(response);
                    String ticket = obj.getJSONObject("data").getString("ticket");

                    propertiesMap.put(SessionParameter.USER, "ROLE_TICKET");
                    propertiesMap.put(SessionParameter.PASSWORD, ticket);
                }
            });
        } catch (CmisBaseException | ExecutionException | UncheckedExecutionException e) {
            log.warn(e.getMessage());
            throw new CantConnectCmis(e);
        }
    }

    @Override
    protected void invalidateCurrentConnection() throws RepositoryException {
        getActiveConnections().invalidate(resolveUser());
    }

    public void setPublicUser(String publicUser) {
        this.publicUser = publicUser;
    }
}