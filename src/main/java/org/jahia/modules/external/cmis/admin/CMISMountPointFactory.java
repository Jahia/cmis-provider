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
package org.jahia.modules.external.cmis.admin;

import org.hibernate.validator.constraints.NotEmpty;
import org.jahia.modules.external.admin.mount.AbstractMountPointFactory;
import org.jahia.modules.external.admin.mount.validator.LocalJCRFolder;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRMountPointNode;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

/**
 * @author kevan
 */
public class CMISMountPointFactory extends AbstractMountPointFactory{
    private static final long serialVersionUID = 2927976149191746013L;
    protected static final String REPOSITORY_ID = "repositoryId";
    protected static final String USER = "user";
    protected static final String PASSWORD = "password";
    protected static final String _URL = "url";
    protected static final String _URL_ALFRESCO = "urlAlfresco";
    protected static final String TYPE = "type";
    protected static final String TYPE_ALFRESCO = "alfresco";
    protected static final String TYPE_CMIS = "cmis";
    protected static final String URL_ALFRESCO_CMIS_ENDPOINT_PREFIX = "/api/-default-/public/cmis/versions/1.1/atom";
    protected static final String REPOSITORY_ID_ALFRESCO = "-default-";

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

    @Override
    public String getLocalPath() {
        return localPath;
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
        if (TYPE_ALFRESCO.equals(type)) {
            mountNode.setProperty(REPOSITORY_ID, REPOSITORY_ID_ALFRESCO);
            mountNode.setProperty(TYPE, TYPE_ALFRESCO);
            mountNode.setProperty(_URL, url + URL_ALFRESCO_CMIS_ENDPOINT_PREFIX);
            mountNode.setProperty(_URL_ALFRESCO, url);
            mountNode.setProtectedPropertyNames(new String[]{PASSWORD, REPOSITORY_ID, _URL});
        } else {
            mountNode.setProperty(REPOSITORY_ID, repositoryId);
            mountNode.setProperty(TYPE, TYPE_CMIS);
            mountNode.setProperty(_URL, url);
            mountNode.setProtectedPropertyNames(new String[]{PASSWORD, _URL_ALFRESCO});
            if (mountNode.hasProperty(_URL_ALFRESCO)) {
                mountNode.getProperty(_URL_ALFRESCO).remove();
            }
        }
    }

    @Override
    public void populate(JCRNodeWrapper nodeWrapper) throws RepositoryException {
        super.populate(nodeWrapper);
        this.name = getName(nodeWrapper.getName());
        try {
            this.localPath = nodeWrapper.getProperty("mountPoint").getNode().getPath();
        }catch (PathNotFoundException e) {
            // no local path defined for this mount point
        }
        this.repositoryId = nodeWrapper.getPropertyAsString(REPOSITORY_ID);
        this.user = nodeWrapper.getPropertyAsString(USER);
        this.password = nodeWrapper.getPropertyAsString(PASSWORD);
        this.type = nodeWrapper.hasProperty(TYPE) && TYPE_ALFRESCO.equals(nodeWrapper.getPropertyAsString(TYPE)) ? TYPE_ALFRESCO : TYPE_CMIS;
        this.url = TYPE_ALFRESCO.equals(this.type) ? nodeWrapper.getPropertyAsString(_URL_ALFRESCO) : nodeWrapper.getPropertyAsString(_URL);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
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
