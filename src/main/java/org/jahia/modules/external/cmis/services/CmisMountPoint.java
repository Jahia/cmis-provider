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
 *     Copyright (C) 2002-2023 Jahia Solutions Group. All rights reserved.
 *
 *     This file is part of a Jahia's Enterprise Distribution.
 *
 *     Jahia's Enterprise Distributions must be used in accordance with the terms
 *     contained in the Jahia Solutions Group Terms &amp; Conditions as well as
 *     the Jahia Sustainable Enterprise License (JSEL).
 *
 *     For questions regarding licensing, support, production usage...
 *     please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
 */
package org.jahia.modules.external.cmis.services;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.jahia.modules.external.cmis.CmisProviderFactory;
import org.jahia.modules.external.service.MountPoint;
import org.jahia.services.content.decorator.JCRMountPointNode;
import org.jahia.utils.xml.JahiaDocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import java.io.InputStream;

import static org.jahia.modules.external.cmis.admin.CMISMountPointFactory.*;

public class CmisMountPoint extends MountPoint {

    private static final Logger logger = LoggerFactory.getLogger(CmisMountPoint.class);

    private String cmisType;
    private String repositoryId;
    private String user = "";
    private String password = "";
    private String url;
    private Boolean slowConnection = false;
    private String publicUser = "";
    private String remotePath;
    private Integer ttliveSeconds = 15 * 60;
    private Integer ttidleSeconds = 5 * 60;
    private Integer maxItemsPerBatch = 1000;
    private Integer maxChildNodes = 0;

    private static final String CMIS_MOUNTPOINT = "cmis:cmisMountPoint";

    public CmisMountPoint(String name, String mountPointRefPath) {
        super(name, mountPointRefPath);
    }

    public String getMountNodeType() {
        return CMIS_MOUNTPOINT;
    }

    public void setProperties(JCRMountPointNode mountNode) throws RepositoryException {
        setJcrProperty(mountNode, REMOTE_PATH, remotePath);
        setJcrProperty(mountNode, TYPE, cmisType);
        setJcrProperty(mountNode, URL, url);
        // user/pwd props needs to be created even if blank
        mountNode.setProperty(USER, user != null ? user : "");
        mountNode.setProperty(PASSWORD, password != null ? password : "");

        setJcrProperty(mountNode, SLOW_CONNECTION, slowConnection);
        setJcrProperty(mountNode, TTLIVE_SECONDS, ttliveSeconds);
        setJcrProperty(mountNode, TTIDLE_SECONDS, ttidleSeconds);
        setJcrProperty(mountNode, MAX_CHILD_NODES, maxChildNodes);
        setJcrProperty(mountNode, MAX_ITEMS_PER_BATCH, maxItemsPerBatch);

        if (CmisProviderFactory.TYPE_ALFRESCO.equals(cmisType)) {
            String alfrescoRepositoryId = getAlfrescoRepoId(user, password);
            setJcrProperty(mountNode, REPOSITORY_ID, alfrescoRepositoryId);
            setJcrProperty(mountNode, PUBLIC_USER, publicUser);
            mountNode.setProtectedPropertyNames(new String[] {PASSWORD, REPOSITORY_ID});
        } else {
            setJcrProperty(mountNode, REPOSITORY_ID, repositoryId);
            mountNode.setProtectedPropertyNames(new String[] {PASSWORD});
        }
    }

    /** Set JCR node property. Remove if value is set to blank string, or ignore if null */
    protected void setJcrProperty(JCRMountPointNode node, String propName, String propValue) throws RepositoryException {
        if (StringUtils.isNotBlank(propValue)) {
            node.setProperty(propName, propValue);
            // remove if property is set to blank string
        } else if (propValue != null && StringUtils.isBlank(propValue) && node.hasProperty(propName)) {
            node.getProperty(propName).remove();
        }
    }

    /** Set JCR node property, or ignore if null */
    protected void setJcrProperty(JCRMountPointNode node, String propName, Integer propValue) throws RepositoryException {
        if (propValue != null) {
            node.setProperty(propName, propValue);
        }
    }

    /** Set JCR node property, or ignore if null */
    protected void setJcrProperty(JCRMountPointNode node, String propName, Boolean propValue) throws RepositoryException {
        if (propValue != null) {
            node.setProperty(propName, propValue);
        }
    }

    private String getAlfrescoRepoId(String user, String password) throws RepositoryException {
        String alfrescoRepositoryId;
        HttpAuthenticationFeature auth = HttpAuthenticationFeature.basic(user, password);
        Client client = ClientBuilder.newBuilder().register(auth).build();
        try {
            WebTarget target = client.target(url).path(CMIS_SERVICE_ENDPOINT);
            DocumentBuilder domBuilder = JahiaDocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document domDoc = domBuilder.parse(target.request().accept(MediaType.TEXT_XML).get(InputStream.class));
            alfrescoRepositoryId = domDoc.getElementsByTagName("cmis:repositoryId").item(0).getTextContent();
        } catch (Exception e) {
            String msg = String.format("Unable to get repository id from %s due to the following error '%s'",
                    url + CMIS_SERVICE_ENDPOINT, e.getMessage());
            throw new RepositoryException(msg, e);
        } finally {
            client.close();
        }
        return alfrescoRepositoryId;
    }

    public void setCmisType(String cmisType) {
        this.cmisType = cmisType;
    }

    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSlowConnection(Boolean slowConnection) {
        if (slowConnection != null) {
            this.slowConnection = slowConnection;
        }
    }

    public void modifySlowConnection(Boolean slowConnection) {
        this.slowConnection = slowConnection;
    }

    public void setPublicUser(String publicUser) {
        this.publicUser = publicUser;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    /** Use default value if parameter is null */
    public void setTtlive(Integer ttliveSeconds) {
        if (ttliveSeconds != null) {
            this.ttliveSeconds = ttliveSeconds;
        }
    }

    /** Allow null values which will be ignored when setting properties */
    public void modifyTtlive(Integer ttliveSeconds) {
        this.ttliveSeconds = ttliveSeconds;
    }

    /** Use default value if parameter is null */
    public void setTtidle(Integer ttidleSeconds) {
        if (ttidleSeconds != null) {
            this.ttidleSeconds = ttidleSeconds;
        }
    }

    /** Allow null values which will be ignored when setting properties */
    public void modifyTtidle(Integer ttidleSeconds) {
        this.ttidleSeconds = ttidleSeconds;
    }

    /** Use default value if parameter is null */
    public void setMaxItemsPerBatch(Integer maxItemsPerBatch) {
        if (maxItemsPerBatch != null) {
            this.maxItemsPerBatch = maxItemsPerBatch;
        }
    }

    /** Allow null values which will be ignored when setting properties */
    public void modifyMaxItemsPerBatch(Integer maxItemsPerBatch) {
        this.maxItemsPerBatch = maxItemsPerBatch;
    }

    /** Use default value if parameter is null */
    public void setMaxChildNodes(Integer maxChildNodes) {
        if (maxChildNodes != null) {
            this.maxChildNodes = maxChildNodes;
        }
    }

    /** Allow null values which will be ignored when setting properties */
    public void modifyMaxChildNodes(Integer maxChildNodes) {
        this.maxChildNodes = maxChildNodes;
    }

}
