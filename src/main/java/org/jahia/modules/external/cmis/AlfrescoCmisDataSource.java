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

import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public AlfrescoCmisDataSource() {
    }

    @Override
    public void start() {
        super.start();
        HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
        client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature
                        .basicBuilder()
                        .nonPreemptive()
                        .credentials(repositoryPropertiesMap.get(SessionParameter.USER),
                                repositoryPropertiesMap.get(SessionParameter.PASSWORD))
                        .build())
                .build();

    }

    @Override
    public void stop() {
        super.stop();
        client.close();
    }

    public Session getCmisSession() throws CantConnectCmis {
        try {
            JahiaUser aliasedUser = JCRSessionFactory.getInstance().getCurrentAliasedUser();
            final String user = aliasedUser != null ? aliasedUser.getName() : ExternalContentStoreProvider.getCurrentSession().getUserID();
            return activeConnections.get(user, new Callable<Session>() {
                @Override
                public Session call() throws Exception {
                    HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
                    SessionFactoryImpl factory = SessionFactoryImpl.newInstance();
                    // create session

                    HashMap<String, String> propertiesMap = new HashMap<>(repositoryPropertiesMap);
                    if (!user.startsWith(" system ") &&
                            !user.equals("root")) {

                        WebTarget target = client.target(repositoryPropertiesMap.get("alfresco.url")).
                                path("service/impersonateLogin").
                                queryParam("format", "json").
                                queryParam("username", user);

                        String response = target.request().accept(MediaType.APPLICATION_JSON).get(String.class);
                        JSONObject obj = new JSONObject(response);
                        String ticket = obj.getJSONObject("data").getString("ticket");

                        propertiesMap.put(SessionParameter.USER, "ROLE_TICKET");
                        propertiesMap.put(SessionParameter.PASSWORD, ticket);
                    }

                    return factory.createSession(propertiesMap);
                }
            });
        } catch (ExecutionException e) {
            throw new CantConnectCmis(e);
        }
    }
}