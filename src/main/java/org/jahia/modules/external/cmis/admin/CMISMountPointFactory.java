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
package org.jahia.modules.external.cmis.admin;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.jahia.modules.external.admin.mount.AbstractMountPointFactory;
import org.jahia.modules.external.admin.mount.validator.LocalJCRFolder;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRMountPointNode;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.io.IOException;

/**
 * @author kevan
 */
public class CMISMountPointFactory extends AbstractMountPointFactory {
    protected static final String REPOSITORY_ID = "repositoryId";
    protected static final String USER = "user";
    protected static final String PASSWORD = "password";
    protected static final String URL = "url";
    protected static final String TYPE = "type";
    protected static final String TYPE_ALFRESCO = "alfresco";
    protected static final String TYPE_CMIS = "cmis";
    protected static final String CMIS_SERVICE_ENDPOINT = "/service/cmis";
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
        if (TYPE_ALFRESCO.equals(type)) {

            // get repository id from the server
            HttpClient httpclient = new HttpClient();
            Credentials credentials = new UsernamePasswordCredentials(user, password);
            httpclient.getState().setCredentials(AuthScope.ANY, credentials);
            GetMethod get = new GetMethod(url + CMIS_SERVICE_ENDPOINT);
            get.setDoAuthentication(true);
            String alfrescoRepositoryId = null;
            try {
                httpclient.executeMethod(get);
                alfrescoRepositoryId = StringUtils.substringBetween(get.getResponseBodyAsString(), "<cmis:repositoryId>", "</cmis:repositoryId>");
            } catch (IOException e) {
                throw new RepositoryException("Unable to get repository id from " + url + CMIS_SERVICE_ENDPOINT, e);
            } finally {
                get.releaseConnection();
            }

            mountNode.setProperty(REPOSITORY_ID, alfrescoRepositoryId);
            mountNode.setProperty(TYPE, TYPE_ALFRESCO);
            mountNode.setProtectedPropertyNames(new String[]{PASSWORD, REPOSITORY_ID});
        } else {
            mountNode.setProperty(REPOSITORY_ID, repositoryId);
            mountNode.setProperty(TYPE, TYPE_CMIS);
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
        this.type = nodeWrapper.hasProperty(TYPE) && TYPE_ALFRESCO.equals(nodeWrapper.getPropertyAsString(TYPE)) ? TYPE_ALFRESCO : TYPE_CMIS;
        this.url = nodeWrapper.getPropertyAsString(URL);
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

}
