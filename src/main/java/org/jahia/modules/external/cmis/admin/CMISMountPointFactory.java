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
package org.jahia.modules.external.cmis.admin;

import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.hibernate.validator.constraints.NotEmpty;
import org.jahia.modules.external.admin.mount.AbstractMountPointFactory;
import org.jahia.modules.external.admin.mount.validator.LocalJCRFolder;
import org.jahia.modules.external.cmis.CmisProviderFactory;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRMountPointNode;
import org.w3c.dom.Document;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Properties;

import static org.jahia.modules.external.cmis.CmisDataSource.*;

/**
 * @author kevan
 */
public class CMISMountPointFactory extends AbstractMountPointFactory {
    public static final String REPOSITORY_ID = "repositoryId";
    public static final String USER = "user";
    public static final String PASSWORD = "password";
    public static final String URL = "url";
    public static final String SLOW_CONNECTION="slowConnection";
    public static final String TYPE_CMIS = "cmis";
    public static final String REMOTE_PATH = "remotePath";
    public static final String TYPE = "type";
    public static final String CMIS_SERVICE_ENDPOINT = "/api/-default-/public/cmis/versions/1.1/atom";
    public static final String PUBLIC_USER = "publicUser";

    public static final String CACHE_CONCURRENCY_LEVEL = "cacheConcurrencyLevel";
    public static final String CACHE_MAXIMUM_SIZE = "cacheMaximumSize";
    public static final String CACHE_EXPIRE_AFTER_ACCESS = "cacheExpireAfterAccess";
    public static final String CONNECT_TIMEOUT = "connectionTimeout";
    public static final String READ_TIMEOUT = "readTimeout";
    public static final String COMPRESSION = "compressionEnabled";
    public static final String CLIENT_COMPRESSION = "clientCompressionEnabled";
    public static final String COOKIES = "cookieEnabled";
    public static final String FORCED_CMIS_VERSION = "forceCmisVersion";
    public static final String CACHE_ENABLED = "cacheEnabled";
    public static final String RESPONSE_FILTER = "responseFilter";
    public static final String INCLUDE_ACTIONS = "includeActions";
    public static final String INCLUDE_ACL = "includeAcl";
    public static final String INCLUDE_POLICIES = "includePolicies";

    public static final String INCLUDE_RELATIONSHIPS = "includeRelationships";
    public static final String LOAD_SECONDARY_TYPE_PROPERTIES = "loadSecondaryTypeProperties";
    public static final String RENDITION_FILTER = "renditionFilter";

    private static final long serialVersionUID = 2927976149191746013L;
    @NotEmpty
    private String type;
    @NotEmpty
    private String name;
    @LocalJCRFolder
    private String localPath;
    @NotEmpty
    private String repositoryId;
    private String user;
    private String password;
    @NotEmpty
    private String url;
    private boolean slowConnection = false;

    private Long cacheConcurrencyLevel;
    private Long cacheMaximumSize;
    private Long cacheExpireAfterAccess;
    private Long connectionTimeout;
    private Long readTimeout;

    public CMISMountPointFactory() {
        Properties defaultValues = (Properties) SpringContextSingleton.getBean("CmisRepositoryProperties");

        this.cacheConcurrencyLevel = Long.parseLong(defaultValues.getProperty(CONF_SESSION_CACHE_CONCURRENCY_LEVEL));
        this.cacheMaximumSize = Long.parseLong(defaultValues.getProperty(CONF_SESSION_CACHE_MAXIMUM_SIZE));
        this.cacheExpireAfterAccess = Long.parseLong(defaultValues.getProperty(CONF_SESSION_CACHE_EXPIRE_AFTER_ACCESS));
        this.readTimeout = Long.parseLong(defaultValues.getProperty(SessionParameter.READ_TIMEOUT));
        this.compressionEnabled = Boolean.parseBoolean(defaultValues.getProperty(SessionParameter.COMPRESSION));
        this.clientCompressionEnabled = Boolean.parseBoolean(defaultValues.getProperty(SessionParameter.CLIENT_COMPRESSION));
        this.cookieEnabled = Boolean.parseBoolean(defaultValues.getProperty(SessionParameter.COOKIES));


        this.forceCmisVersion = defaultValues.getProperty(SessionParameter.FORCE_CMIS_VERSION);
        this.cacheEnabled = Boolean.parseBoolean(defaultValues.getProperty(CONF_CONTEXT_CACHE_ENABLE));

        this.responseFilter = defaultValues.getProperty(CONF_CONTEXT_FILTER);

        this.includeActions = Boolean.parseBoolean(defaultValues.getProperty(CONF_CONTEXT_INCLUDE_ALLOWABLE_ACTIONS));
        this.includeAcl = Boolean.parseBoolean(defaultValues.getProperty(CONF_CONTEXT_INCLUDE_ACLS));
        this.includePolicies = Boolean.parseBoolean(defaultValues.getProperty(CONF_CONTEXT_INCLUDE_POLICIES));

        this.includeRelationships= defaultValues.getProperty(CONF_CONTEXT_INCLUDE_RELATIONSHIPS);
        this.renditionFilter = defaultValues.getProperty(CONF_CONTEXT_RENDITION_FILTER);
        this.loadSecondaryTypeProperties = Boolean.parseBoolean(defaultValues.getProperty(CONF_CONTEXT_LOAD_SECONDARY_TYPE_PROPERTIES));

    }

    public Long getCacheConcurrencyLevel() { return cacheConcurrencyLevel; }

    public void setCacheConcurrencyLevel(Long cacheConcurrencyLevel) { this.cacheConcurrencyLevel = cacheConcurrencyLevel; }

    public Long getCacheMaximumSize() { return cacheMaximumSize; }

    public void setCacheMaximumSize(Long cacheMaximumSize) { this.cacheMaximumSize = cacheMaximumSize; }

    public Long getCacheExpireAfterAccess() { return cacheExpireAfterAccess; }

    public void setCacheExpireAfterAccess(Long cacheExpireAfterAccess) { this.cacheExpireAfterAccess = cacheExpireAfterAccess; }

    public Long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    public boolean isClientCompressionEnabled() {
        return clientCompressionEnabled;
    }

    public void setClientCompressionEnabled(boolean clientCompressionEnabled) {
        this.clientCompressionEnabled = clientCompressionEnabled;
    }

    public boolean isCookieEnabled() {
        return cookieEnabled;
    }

    public void setCookieEnabled(boolean cookieEnabled) {
        this.cookieEnabled = cookieEnabled;
    }

    public String getForceCmisVersion() {
        return forceCmisVersion;
    }

    public void setForceCmisVersion(String forceCmisVersion) {
        this.forceCmisVersion = forceCmisVersion;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public String getResponseFilter() {
        return responseFilter;
    }

    public void setResponseFilter(String responseFilter) {
        this.responseFilter = responseFilter;
    }

    public boolean isIncludeActions() {
        return includeActions;
    }

    public void setIncludeActions(boolean includeActions) {
        this.includeActions = includeActions;
    }

    public boolean isIncludeAcl() {
        return includeAcl;
    }

    public void setIncludeAcl(boolean includeAcl) {
        this.includeAcl = includeAcl;
    }

    public boolean isIncludePolicies() {
        return includePolicies;
    }

    public void setIncludePolicies(boolean includePolicies) {
        this.includePolicies = includePolicies;
    }

    private boolean compressionEnabled;
    private boolean clientCompressionEnabled;
    private boolean cookieEnabled;
    private String forceCmisVersion;
    private boolean cacheEnabled;
    private String responseFilter;
    private boolean includeActions;
    private boolean includeAcl;
    private boolean includePolicies;

    public String getRenditionFilter() {
        return renditionFilter;
    }

    public void setRenditionFilter(String renditionFilter) {
        this.renditionFilter = renditionFilter;
    }

    public String getIncludeRelationships() {
        return includeRelationships;
    }

    public void setIncludeRelationships(String includeRelationships) {
        this.includeRelationships = includeRelationships;
    }

    public boolean isLoadSecondaryTypeProperties() {
        return loadSecondaryTypeProperties;
    }

    public void setLoadSecondaryTypeProperties(boolean loadSecondaryTypeProperties) {
        this.loadSecondaryTypeProperties = loadSecondaryTypeProperties;
    }

    private String renditionFilter;
    private String includeRelationships;
    private boolean loadSecondaryTypeProperties;

    private String publicUser = "";

    private String remotePath;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    @Override
    public String getMountNodeType() {
        return "cmis:cmisMountPoint";
    }

    @Override
    public void setProperties(JCRNodeWrapper node) throws RepositoryException {
        JCRMountPointNode mountNode = (JCRMountPointNode) node;
        mountNode.setProperty(USER, user);
        mountNode.setProperty(PASSWORD, password);
        mountNode.setProperty(URL, url);
        mountNode.setProperty(SLOW_CONNECTION, slowConnection);
        mountNode.setProperty(REMOTE_PATH, remotePath);

        mountNode.setProperty(CACHE_CONCURRENCY_LEVEL,cacheConcurrencyLevel);
        mountNode.setProperty(CACHE_MAXIMUM_SIZE,cacheMaximumSize);
        mountNode.setProperty(CACHE_EXPIRE_AFTER_ACCESS,cacheExpireAfterAccess);
        mountNode.setProperty(CONNECT_TIMEOUT,connectionTimeout);
        mountNode.setProperty(READ_TIMEOUT,readTimeout);
        mountNode.setProperty(COMPRESSION, compressionEnabled);
        mountNode.setProperty(CLIENT_COMPRESSION, clientCompressionEnabled);
        mountNode.setProperty(COOKIES, cookieEnabled);
        mountNode.setProperty(FORCED_CMIS_VERSION, forceCmisVersion);
        mountNode.setProperty(CACHE_ENABLED,cacheEnabled);
        mountNode.setProperty(RESPONSE_FILTER, responseFilter);
        mountNode.setProperty(INCLUDE_ACTIONS,includeActions);
        mountNode.setProperty(INCLUDE_ACL,includeAcl);
        mountNode.setProperty(INCLUDE_POLICIES,includePolicies);

        mountNode.setProperty(INCLUDE_RELATIONSHIPS,includeRelationships);
        mountNode.setProperty(LOAD_SECONDARY_TYPE_PROPERTIES,loadSecondaryTypeProperties);
        mountNode.setProperty(RENDITION_FILTER,renditionFilter);

        if (CmisProviderFactory.TYPE_ALFRESCO.equals(type)) {

            // get repository id from the server
            String alfrescoRepositoryId;
            Client client = ClientBuilder.newBuilder().register(HttpAuthenticationFeature.basic(user, password)).build();

            try {
                WebTarget target = client.target(url).path(CMIS_SERVICE_ENDPOINT);

                // get reposiroty id

                DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
                Document domDoc = domBuilder.parse(target.request().accept(MediaType.TEXT_XML).get(InputStream.class));

                alfrescoRepositoryId = domDoc.getElementsByTagName("cmis:repositoryId").item(0).getTextContent();
            } catch (Exception e) {
                throw new RepositoryException(String.format("Unable to get repository id from %s due to the following error '%s'", url + CMIS_SERVICE_ENDPOINT, e.getMessage()), e);
            } finally {
                client.close();
            }

            mountNode.setProperty(REPOSITORY_ID, alfrescoRepositoryId);
            mountNode.setProperty(TYPE, CmisProviderFactory.TYPE_ALFRESCO);
            mountNode.setProtectedPropertyNames(new String[]{PASSWORD, REPOSITORY_ID});
            mountNode.setProperty(PUBLIC_USER, publicUser);
        } else if (CmisProviderFactory.TYPE_ALFRESCO_TOKEN.equals(type)) {

                // get repository id from the server
                String alfrescoRepositoryId;
                Client client = ClientBuilder.newBuilder().register(HttpAuthenticationFeature.basic(user, password)).build();

                try {
                    WebTarget target = client.target(url).path(CMIS_SERVICE_ENDPOINT);

                    // get reposiroty id

                    DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
                    Document domDoc = domBuilder.parse(target.request().accept(MediaType.TEXT_XML).get(InputStream.class));

                    alfrescoRepositoryId = domDoc.getElementsByTagName("cmis:repositoryId").item(0).getTextContent();
                } catch (Exception e) {
                    throw new RepositoryException(String.format("Unable to get repository id from %s due to the following error '%s'", url + CMIS_SERVICE_ENDPOINT, e.getMessage()), e);
                } finally {
                    client.close();
                }

                mountNode.setProperty(REPOSITORY_ID, alfrescoRepositoryId);
                mountNode.setProperty(TYPE, CmisProviderFactory.TYPE_ALFRESCO_TOKEN);
                mountNode.setProtectedPropertyNames(new String[]{PASSWORD, REPOSITORY_ID});
        } else {
            mountNode.setProperty(REPOSITORY_ID, repositoryId);
            if(CmisProviderFactory.TYPE_NUXEO.equals(type)){
                mountNode.setProperty(TYPE, CmisProviderFactory.TYPE_NUXEO);
            }
            else{
                mountNode.setProperty(TYPE, TYPE_CMIS);
            }
            mountNode.setProtectedPropertyNames(new String[]{PASSWORD});
        }
    }

    @Override
    public void populate(JCRNodeWrapper nodeWrapper) throws RepositoryException {
        super.populate(nodeWrapper);
        this.name = getName(nodeWrapper.getName());
        try {
            this.localPath = nodeWrapper.getProperty("mountPoint").getNode().getPath();
        } catch (PathNotFoundException e) {
            // no local path defined for this mount point
        }
        this.repositoryId = nodeWrapper.getPropertyAsString(REPOSITORY_ID);
        this.user = nodeWrapper.getPropertyAsString(USER);
        this.password = nodeWrapper.getPropertyAsString(PASSWORD);
        String type = TYPE_CMIS;
        if(nodeWrapper.hasProperty(TYPE) && CmisProviderFactory.TYPE_ALFRESCO.equals(nodeWrapper.getPropertyAsString(TYPE))){
            type = CmisProviderFactory.TYPE_ALFRESCO;
        }
        if(nodeWrapper.hasProperty(TYPE) && CmisProviderFactory.TYPE_ALFRESCO_TOKEN.equals(nodeWrapper.getPropertyAsString(TYPE))){
            type = CmisProviderFactory.TYPE_ALFRESCO_TOKEN;
        }
        if(nodeWrapper.hasProperty(TYPE) && CmisProviderFactory.TYPE_NUXEO.equals(nodeWrapper.getPropertyAsString(TYPE))){
            type = CmisProviderFactory.TYPE_NUXEO;
        }
        this.type = type;
        this.publicUser = nodeWrapper.getPropertyAsString(PUBLIC_USER);
        this.remotePath = nodeWrapper.hasProperty(REMOTE_PATH) ? nodeWrapper.getProperty(REMOTE_PATH).getString() : "";
        this.url = nodeWrapper.getPropertyAsString(URL);
        this.slowConnection = nodeWrapper.hasProperty(SLOW_CONNECTION) && nodeWrapper.getProperty(SLOW_CONNECTION).getBoolean();

        Properties defaultValues = (Properties) SpringContextSingleton.getBean("CmisRepositoryProperties");

        this.cacheConcurrencyLevel = (nodeWrapper.hasProperty(CACHE_CONCURRENCY_LEVEL) ? nodeWrapper.getProperty(CACHE_CONCURRENCY_LEVEL).getLong():Integer.valueOf(defaultValues.getProperty(CONF_SESSION_CACHE_CONCURRENCY_LEVEL)));
        this.cacheMaximumSize = (nodeWrapper.hasProperty(CACHE_MAXIMUM_SIZE) ? nodeWrapper.getProperty(CACHE_MAXIMUM_SIZE).getLong(): Integer.valueOf(defaultValues.getProperty(CONF_SESSION_CACHE_MAXIMUM_SIZE)));
        this.cacheExpireAfterAccess = (nodeWrapper.hasProperty(CACHE_EXPIRE_AFTER_ACCESS) ? nodeWrapper.getProperty(CACHE_EXPIRE_AFTER_ACCESS).getLong(): Integer.valueOf(defaultValues.getProperty(CONF_SESSION_CACHE_EXPIRE_AFTER_ACCESS)));
        this.connectionTimeout = (nodeWrapper.hasProperty(CONNECT_TIMEOUT) ? nodeWrapper.getProperty(CONNECT_TIMEOUT).getLong(): Integer.valueOf(defaultValues.getProperty(CONF_SESSION_CMIS_CONNECT_TIMEOUT)));
        this.readTimeout = (nodeWrapper.hasProperty(READ_TIMEOUT) ? nodeWrapper.getProperty(READ_TIMEOUT).getLong(): Integer.valueOf(defaultValues.getProperty(CONF_SESSION_CMIS_READ_TIMEOUT)));
        this.compressionEnabled = nodeWrapper.hasProperty(COMPRESSION) && nodeWrapper.getProperty(COMPRESSION).getBoolean();
        this.clientCompressionEnabled = nodeWrapper.hasProperty(CLIENT_COMPRESSION) && nodeWrapper.getProperty(CLIENT_COMPRESSION).getBoolean();
        this.cookieEnabled = nodeWrapper.hasProperty(COOKIES) && nodeWrapper.getProperty(COOKIES).getBoolean();

        this.forceCmisVersion = (nodeWrapper.hasProperty(FORCED_CMIS_VERSION)? nodeWrapper.getProperty(FORCED_CMIS_VERSION).getString():defaultValues.getProperty(CONF_SESSION_CMIS_CMISVERSION));
        this.cacheEnabled = nodeWrapper.hasProperty(CACHE_ENABLED) && nodeWrapper.getProperty(CACHE_ENABLED).getBoolean();

        this.responseFilter = (nodeWrapper.hasProperty(RESPONSE_FILTER) ? nodeWrapper.getProperty(RESPONSE_FILTER).getString():defaultValues.getProperty(CONF_CONTEXT_FILTER));

        this.includeActions = nodeWrapper.hasProperty(INCLUDE_ACTIONS) && nodeWrapper.getProperty(INCLUDE_ACTIONS).getBoolean();
        this.includeAcl = nodeWrapper.hasProperty(INCLUDE_ACL) && nodeWrapper.getProperty(INCLUDE_ACL).getBoolean();
        this.includePolicies = nodeWrapper.hasProperty(INCLUDE_POLICIES) && nodeWrapper.getProperty(INCLUDE_POLICIES).getBoolean();

        this.includeRelationships= (nodeWrapper.hasProperty(INCLUDE_RELATIONSHIPS) ? nodeWrapper.getProperty(INCLUDE_RELATIONSHIPS).getString():defaultValues.getProperty(CONF_CONTEXT_INCLUDE_RELATIONSHIPS));
        this.renditionFilter = (nodeWrapper.hasProperty(RENDITION_FILTER) ? nodeWrapper.getProperty(RENDITION_FILTER).getString():defaultValues.getProperty(CONF_CONTEXT_RENDITION_FILTER));
        this.loadSecondaryTypeProperties = nodeWrapper.hasProperty(LOAD_SECONDARY_TYPE_PROPERTIES) && nodeWrapper.getProperty(LOAD_SECONDARY_TYPE_PROPERTIES).getBoolean();

    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isSlowConnection() {
        return slowConnection;
    }

    public void setSlowConnection(boolean slowConnection) {
        this.slowConnection = slowConnection;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public String getPublicUser() {
        return publicUser;
    }

    public void setPublicUser(String publicUser) {
        this.publicUser = publicUser;
    }
}
