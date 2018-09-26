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
 *     Copyright (C) 2002-2018 Jahia Solutions Group. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.jahia.modules.external.ExternalDataSource;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.UncheckedExecutionException;


/**
 * ExternalDataSource implementation for Alfresco 4.2 or 5.0 CMIS to handle user rights on documents, based on CmisProviderFactory
 *
 * @author toto
 */
public class AlfrescoCmisTokenDataSource extends CmisDataSource implements ExternalDataSource.Initializable //, ExternalDataSource.LazyProperty 
{
    private static final Logger log = LoggerFactory.getLogger(AlfrescoCmisTokenDataSource.class);

    private Client client;

    public AlfrescoCmisTokenDataSource() {
        super();
    }

    @Override
    public void start() {
    	log.debug("Starting ALF Token datasource");
        super.start();
        Map<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
        client = ClientBuilder.newBuilder()
                .register(HttpAuthenticationFeature.basic(repositoryPropertiesMap.get(SessionParameter.USER),
                        repositoryPropertiesMap.get(SessionParameter.PASSWORD)))
                .build();
    }

    @Override
    public void stop() {
    	log.debug("Stopping ALF Token datasource");
        super.stop();
        client.close();
    }

    @Override
    public synchronized Session getCmisSession(final String user) throws CantConnectCmis {
        try {
        	log.debug("Getting session for user "+user);
            return activeConnections.get(user, new Callable<Session>() {
                @Override
                public Session call() throws Exception {
                    Map<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
                    SessionFactoryImpl factory = SessionFactoryImpl.newInstance();
                    // create session
                    Map<String, String> propertiesMap = new HashMap<>(repositoryPropertiesMap);
                    setConnectionProperties(propertiesMap, user);
                    Session session = factory.createSession(propertiesMap);
                                       
                    OperationContext defaultContext = session.getDefaultContext();

                    String filter = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_FILTER);
                    String cacheStr = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_CACHE_ENABLE);
                    String includeActionsStr = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_INCLUDE_ALLOWABLE_ACTIONS);
                    String includeAclsStr = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_INCLUDE_ACLS);
                    String includePoliciesStr = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_INCLUDE_POLICIES);

                    String includeRelationships = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_INCLUDE_RELATIONSHIPS);
                    String loadSecondaryTypeProperties = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_LOAD_SECONDARY_TYPE_PROPERTIES);
                    String renditionFilter = repositoryPropertiesMap.get(CmisDataSource.CONF_CONTEXT_RENDITION_FILTER);

                    defaultContext.setFilterString(filter);

                    defaultContext.setCacheEnabled(Boolean.parseBoolean(cacheStr));
                    defaultContext.setIncludeAllowableActions(Boolean.parseBoolean(includeActionsStr));
                    defaultContext.setIncludeAcls(Boolean.parseBoolean(includeAclsStr));
                    defaultContext.setIncludePolicies(Boolean.parseBoolean(includePoliciesStr));

                    defaultContext.setIncludeRelationships(IncludeRelationships.fromValue(includeRelationships));
                    defaultContext.setLoadSecondaryTypeProperties(Boolean.parseBoolean(loadSecondaryTypeProperties));
                    defaultContext.setRenditionFilterString(renditionFilter);
                    return session;
                }

                private void setConnectionProperties(Map<String, String> propertiesMap, String user) throws JSONException {
                    HashMap<String, String> globalConf = getConf().getRepositoryPropertiesMap();
					WebTarget target = client.target(globalConf.get(CmisProviderFactory.ALFRESCO_URL)).
                            path("s/api/login").
                            queryParam("format", "json").
                            queryParam("u", globalConf.get(SessionParameter.USER)).
                            queryParam("pw", globalConf.get(SessionParameter.PASSWORD))
                            ;
                    String response = target.request().accept(MediaType.APPLICATION_JSON).get(String.class);
                    JSONObject obj = new JSONObject(response);
                    String ticket = obj.getJSONObject("data").getString("ticket");
                    log.debug("Retrieved Ticket "+ticket);
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
    
//TODO : Implement lazy property
/*
	@Override
	public String[] getPropertyValues(String path, String propertyName) throws PathNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getI18nPropertyValues(String path, String lang, String propertyName) throws PathNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Binary[] getBinaryPropertyValues(String path, String propertyName) throws PathNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}
*/
}