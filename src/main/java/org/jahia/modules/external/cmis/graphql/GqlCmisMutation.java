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
package org.jahia.modules.external.cmis.graphql;

import graphql.annotations.annotationTypes.*;
import org.apache.commons.lang3.StringUtils;
import org.jahia.modules.external.cmis.CmisDataSource;
import org.jahia.modules.external.cmis.services.CmisMountPoint;
import org.jahia.modules.external.graphql.GqlMountPointMutation;
import org.jahia.modules.external.service.MountPointService;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.ehcache.EhCacheProvider;

import javax.jcr.RepositoryException;

@GraphQLTypeExtension(GqlMountPointMutation.class)
public class GqlCmisMutation {

    private final MountPointService mountPointService;

    public GqlCmisMutation(GqlMountPointMutation m) {
        // Unable to inject OSGI service; get through utils
        this.mountPointService = BundleUtils.getOsgiService(MountPointService.class, null);
    }

    @GraphQLField
    @GraphQLDescription("Create a standard CMIS connector mount point.\nMount point node will be set to waiting status if CMIS connection "
            + "cannot be established.")
    public String addCmis(
            @GraphQLName("name") @GraphQLDescription("Name for the mount point") @GraphQLNonNull String name,
            @GraphQLName("mountPointRefPath") @GraphQLDescription("Target local mount point") String mountPointRefPath,
            @GraphQLName("rootPath") @GraphQLDescription("CMIS remote root path") String rootPath,
            @GraphQLName("cmisType") @GraphQLDescription("CMIS mount point type") @GraphQLNonNull GqlCmisType cmisType,
            @GraphQLName("repositoryId") @GraphQLDescription("Repository ID (only for CMIS and Nuxeo connectors)") String repositoryId,
            @GraphQLName("user") @GraphQLDescription("CMIS repository user") String user,
            @GraphQLName("password") @GraphQLDescription("CMIS repository password") String password,
            @GraphQLName("url") @GraphQLDescription("CMIS endpoint URL") @GraphQLNonNull String url,
            @GraphQLName("publicUser") @GraphQLDescription("Alfresco user used to access public content (it must not be guest)") String publicUser,
            @GraphQLName("ttLive") @GraphQLDescription("Amount of seconds documents are cached (default: 900)") Integer ttLive,
            @GraphQLName("ttIdle") @GraphQLDescription("Amount of seconds documents will stay in cache if not accessed (default: 300)") Integer ttIdle,
            @GraphQLName("maxNumDocs") @GraphQLDescription("Max number of documents/folder to read from any given path "
                    + "(default: 0 means read everything)") Integer maxNumDocs,
            @GraphQLName("maxItemsPerBatch") @GraphQLDescription("Max number of items to bring from the backend server per request "
                    + "(default: 1000)") Integer maxItemsPerBatch,
            @GraphQLName("useSlowConn") @GraphQLDescription("Use slow connection (default: false)") Boolean useSlowConn
    ) {
        // required fields; cannot be set to blank
        checkBlank(name, "name");
        checkBlank(url, "CMIS endpoint URL");
        if (cmisType != GqlCmisType.ALFRESCO) {
            if (repositoryId == null) {
                throw new DataFetchingException("Repository ID cannot be null");
            }
            checkBlank(repositoryId, "Repository ID");
        }

        // check number values
        checkPositive(ttLive, "ttLive");
        checkPositive(ttIdle, "ttIdle");
        checkPositive(maxNumDocs, "maxNumDocs");
        checkPositive(maxItemsPerBatch, "maxItemsPerBatch");

        CmisMountPoint mountPoint = new CmisMountPoint(name, mountPointRefPath);
        mountPoint.setCmisType(cmisType.getValue());
        mountPoint.setRepositoryId(repositoryId);
        mountPoint.setUser(user);
        mountPoint.setPassword(password);
        mountPoint.setUrl(url);
        mountPoint.setPublicUser(publicUser);
        mountPoint.setTtlive(ttLive);
        mountPoint.setTtidle(ttIdle);
        mountPoint.setMaxChildNodes(maxNumDocs);
        mountPoint.setMaxItemsPerBatch(maxItemsPerBatch);
        mountPoint.setSlowConnection(useSlowConn);

        try {
            return mountPointService.create(mountPoint);
        } catch (RepositoryException e) {
            throw new DataFetchingException(e.getMessage());
        }
    }

    @GraphQLField
    @GraphQLDescription("Modify an existing CMIS mount point node. Use empty string to remove property, unless otherwise specified")
    public boolean modifyCmis(
            @GraphQLName("pathOrId") @GraphQLDescription("Mount point path or ID to modify") @GraphQLNonNull String pathOrId,
            @GraphQLName("name") @GraphQLDescription("Name for the mount point") String name,
            @GraphQLName("mountPointRefPath") @GraphQLDescription("Target local mount point") String mountPointRefPath,
            @GraphQLName("rootPath") @GraphQLDescription("CMIS remote root path") String rootPath,
            @GraphQLName("cmisType") @GraphQLDescription("CMIS mount point type") GqlCmisType cmisType,
            @GraphQLName("repositoryId") @GraphQLDescription("Repository ID (for standard and Nuxeo CMIS connector)") String repositoryId,
            @GraphQLName("user") @GraphQLDescription("Repository user; cannot be deleted, only changed") String user,
            @GraphQLName("password") @GraphQLDescription("Repository password; cannot be deleted, only changed") String password,
            @GraphQLName("url") @GraphQLDescription("CMIS endpoint URL") String url,
            @GraphQLName("publicUser") @GraphQLDescription("Username used to access public content (it must not be guest)") String publicUser,
            @GraphQLName("ttLive") @GraphQLDescription("Amount of seconds documents are cached (default: 900)") Integer ttLive,
            @GraphQLName("ttIdle") @GraphQLDescription("Amount of seconds documents will stay in cache if not accessed "
                    + "(default: 300)") Integer ttIdle,
            @GraphQLName("maxNumDocs") @GraphQLDescription("Max number of documents/folder to read from any given path "
                    + "(default: 0 means read everything)") Integer maxNumDocs,
            @GraphQLName("maxItemsPerBatch") @GraphQLDescription("Max number of items to bring from the backend server per request "
                    + "(default: 1000)") Integer maxItemsPerBatch,
            @GraphQLName("useSlowConn") @GraphQLDescription("Use slow connection (default: false)") Boolean useSlowConn
    ) {
        // required fields; cannot be set to blank
        checkBlank(name, "name");
        checkBlank(url, "CMIS endpoint URL");
        if (cmisType != null && cmisType != GqlCmisType.ALFRESCO) {
            checkBlank(repositoryId, "Repository ID");
        }

        // check number values
        checkPositive(ttLive, "ttLive");
        checkPositive(ttIdle, "ttIdle");
        checkPositive(maxNumDocs, "maxNumDocs");
        checkPositive(maxItemsPerBatch, "maxItemsPerBatch");

        CmisMountPoint mountPoint = new CmisMountPoint(name, mountPointRefPath);
        mountPoint.setPathOrId(pathOrId);
        mountPoint.setCmisType(cmisType != null ? cmisType.getValue() : null);
        mountPoint.setRepositoryId(repositoryId);
        mountPoint.setUser(user);
        mountPoint.setPassword(password);
        mountPoint.setUrl(url);
        mountPoint.setPublicUser(publicUser);
        mountPoint.modifyTtlive(ttLive);
        mountPoint.modifyTtidle(ttIdle);
        mountPoint.modifyMaxChildNodes(maxNumDocs);
        mountPoint.modifyMaxItemsPerBatch(maxItemsPerBatch);
        mountPoint.modifySlowConnection(useSlowConn);

        try {
            return mountPointService.modify(mountPoint);
        } catch (RepositoryException e) {
            throw new DataFetchingException(e.getMessage());
        }
    }

    protected final void checkBlank(String value, String msgName) throws DataFetchingException {
        if (value != null && StringUtils.isBlank(value)) {
            throw new DataFetchingException("Specified " + msgName + " must not be blank");
        }
    }

    protected final void checkPositive(Integer value, String msgName) throws DataFetchingException {
        if (value != null && value < 0) {
            throw new DataFetchingException(msgName + " must be a positive number");
        }
    }

    @GraphQLField
    @GraphQLDescription("Flush CMIS cache")
    public boolean flushCmisCache() {
        EhCacheProvider ehCacheProvider = (EhCacheProvider) SpringContextSingleton.getBean("ehCacheProvider");
        ehCacheProvider.getCacheManager().getCache(CmisDataSource.CMIS_CACHE).removeAll();
        return true;
    }

}
