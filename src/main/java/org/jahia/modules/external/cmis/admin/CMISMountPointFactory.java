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

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.hibernate.validator.constraints.NotEmpty;
import org.jahia.modules.external.admin.mount.AbstractMountPointFactory;
import org.jahia.modules.external.admin.mount.validator.LocalJCRFolder;
import org.jahia.modules.external.cmis.CmisProviderFactory;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRMountPointNode;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author kevan
 */
public class CMISMountPointFactory extends AbstractMountPointFactory {
    protected static final String REPOSITORY_ID = "repositoryId";
    protected static final String USER = "user";
    protected static final String PASSWORD = "password";
    protected static final String URL = "url";
    protected static final String TYPE_CMIS = "cmis";
    protected static final String CMIS_SERVICE_ENDPOINT = "service/cmis";
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
            } catch (SAXException | ParserConfigurationException | IOException e) {
                throw new RepositoryException("Unable to get repository id from " + url + CMIS_SERVICE_ENDPOINT, e);
            } finally {
                client.close();
            }

            mountNode.setProperty(REPOSITORY_ID, alfrescoRepositoryId);
            mountNode.setProperty(CmisProviderFactory.TYPE, CmisProviderFactory.TYPE_ALFRESCO);
            mountNode.setProtectedPropertyNames(new String[]{PASSWORD, REPOSITORY_ID});
        } else {
            mountNode.setProperty(REPOSITORY_ID, repositoryId);
            mountNode.setProperty(CmisProviderFactory.TYPE, TYPE_CMIS);
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
        this.type = nodeWrapper.hasProperty(CmisProviderFactory.TYPE) && CmisProviderFactory.TYPE_ALFRESCO.equals(nodeWrapper.getPropertyAsString(CmisProviderFactory.TYPE)) ? CmisProviderFactory.TYPE_ALFRESCO : TYPE_CMIS;
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
