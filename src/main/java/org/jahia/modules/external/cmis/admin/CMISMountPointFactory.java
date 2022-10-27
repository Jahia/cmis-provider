/*
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *                                  http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group. All rights reserved.
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

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.hibernate.validator.constraints.NotEmpty;
import org.jahia.modules.external.admin.mount.AbstractMountPointFactory;
import org.jahia.modules.external.admin.mount.validator.LocalJCRFolder;
import org.jahia.modules.external.cmis.CmisProviderFactory;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRMountPointNode;
import org.w3c.dom.Document;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

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
    public static final String TTLIVE_SECONDS = "ttliveSeconds";
    public static final String TTIDLE_SECONDS = "ttidleSeconds";
    public static final String MAX_CHILD_NODES = "maxChildNodes";
    public static final String MAX_ITEMS_PER_BATCH = "maxItemsPerBatch";

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

    private String publicUser = "";

    private String remotePath;

    private int ttliveSeconds = 15 * 60;
    private int ttidleSeconds = 5 * 60;
    private int maxItemsPerBatch = 1000;
    private int maxChildNodes = 0;

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
        mountNode.setProperty(TTLIVE_SECONDS, ttliveSeconds);
        mountNode.setProperty(TTIDLE_SECONDS, ttidleSeconds);
        mountNode.setProperty(MAX_CHILD_NODES, maxChildNodes);
        mountNode.setProperty(MAX_ITEMS_PER_BATCH, maxItemsPerBatch);
        if (CmisProviderFactory.TYPE_ALFRESCO.equals(type)) {

            // get repository id from the server
            String alfrescoRepositoryId;
            Client client = ClientBuilder.newBuilder().register(HttpAuthenticationFeature.basic(user, password)).build();

            try {
                WebTarget target = client.target(url).path(CMIS_SERVICE_ENDPOINT);

                // get reposiroty id

                DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
                domFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
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
        if(nodeWrapper.hasProperty(TYPE) && CmisProviderFactory.TYPE_NUXEO.equals(nodeWrapper.getPropertyAsString(TYPE))){
            type = CmisProviderFactory.TYPE_NUXEO;
        }
        this.type = type;
        this.publicUser = nodeWrapper.getPropertyAsString(PUBLIC_USER);
        this.remotePath = nodeWrapper.hasProperty(REMOTE_PATH) ? nodeWrapper.getProperty(REMOTE_PATH).getString() : "";
        this.url = nodeWrapper.getPropertyAsString(URL);
        this.slowConnection = nodeWrapper.hasProperty(SLOW_CONNECTION) && nodeWrapper.getProperty(SLOW_CONNECTION).getBoolean();
        this.maxChildNodes = nodeWrapper.hasProperty(MAX_CHILD_NODES) ? (int) nodeWrapper.getProperty(MAX_CHILD_NODES).getValue().getLong() : 0;
        this.maxItemsPerBatch = nodeWrapper.hasProperty(MAX_ITEMS_PER_BATCH) ? (int) nodeWrapper.getProperty(MAX_ITEMS_PER_BATCH).getValue().getLong() : 1000;
        this.ttliveSeconds = nodeWrapper.hasProperty(TTLIVE_SECONDS) ? (int) nodeWrapper.getProperty(TTIDLE_SECONDS).getValue().getLong() : 15 * 60;
        this.ttidleSeconds = nodeWrapper.hasProperty(TTIDLE_SECONDS) ? (int) nodeWrapper.getProperty(TTIDLE_SECONDS).getValue().getLong() : 15 * 60;
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

    public int getTtliveSeconds() {
        return ttliveSeconds;
    }

    public void setTtliveSeconds(int ttliveSeconds) {
        this.ttliveSeconds = ttliveSeconds;
    }

    public int getTtidleSeconds() {
        return ttidleSeconds;
    }

    public void setTtidleSeconds(int ttidleSeconds) {
        this.ttidleSeconds = ttidleSeconds;
    }

    public int getMaxItemsPerBatch() {
        return maxItemsPerBatch;
    }

    public void setMaxItemsPerBatch(int maxItemsPerBatch) {
        this.maxItemsPerBatch = maxItemsPerBatch;
    }

    public int getMaxChildNodes() {
        return maxChildNodes;
    }

    public void setMaxChildNodes(int maxChildNodes) {
        this.maxChildNodes = maxChildNodes;
    }
}
