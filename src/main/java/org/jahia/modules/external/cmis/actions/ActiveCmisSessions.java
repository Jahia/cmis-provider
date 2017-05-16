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
 *     Copyright (C) 2002-2017 Jahia Solutions Group. All rights reserved.
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
package org.jahia.modules.external.cmis.actions;

import com.google.common.cache.Cache;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.impl.json.JSONArray;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.modules.external.cmis.CmisDataSource;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRStoreProvider;
import org.jahia.services.content.JCRStoreService;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * JahiaAction that returns all active session
 * if the parameter flushAll is true, then close all active session and flush the map that contains them
 */
public class ActiveCmisSessions extends Action {

    private JCRStoreService jcrStoreService;

    @Override
    public ActionResult doExecute(HttpServletRequest httpServletRequest, RenderContext renderContext, Resource resource, JCRSessionWrapper jcrSessionWrapper, Map<String, List<String>> map, URLResolver urlResolver) throws Exception {
        JSONObject result = new JSONObject();
        if (!jcrSessionWrapper.getUser().isRoot()) {
            return ActionResult.BAD_REQUEST;
        }
        boolean doFlush = map.containsKey("flushAll") && Boolean.valueOf(map.get("flushAll").get(0));
        Boolean recordStats = null;
        if (map.containsKey("recordStats")) {
            recordStats = Boolean.valueOf(map.get("recordStats").get(0));
        }

        for (JCRStoreProvider provider : jcrStoreService.getSessionFactory().getProviderList()) {
            if (provider instanceof ExternalContentStoreProvider
                    && ((ExternalContentStoreProvider) provider).getDataSource() instanceof CmisDataSource) {
                JSONObject providerEntry = new JSONObject();
                CmisDataSource cmisDataSource = (CmisDataSource) ((ExternalContentStoreProvider) provider).getDataSource();
                if (recordStats != null) {
                    cmisDataSource.setRecordingConnectionsStats(recordStats);
                }
                Cache<String, Session> sessions = cmisDataSource.getActiveConnections();
                if (doFlush) {
                    sessions.invalidateAll();
                }
                JSONArray connectedUsers = new JSONArray();
                for (Map.Entry<String, Session> userName : sessions.asMap().entrySet()) {
                    connectedUsers.add(userName.getKey());
                }
                providerEntry.append("connectedUsers", connectedUsers);
                if (cmisDataSource.isRecordingConnectionsStats()) {
                    providerEntry.append("stats", sessions.stats().toString());
                }
                result.append(provider.getMountPoint(), providerEntry);
            }
        }
        return new ActionResult(HttpServletResponse.SC_OK, resource.getNode().getPath(), result);
    }

    public void setJcrStoreService(JCRStoreService jcrStoreService) {
        this.jcrStoreService = jcrStoreService;
    }
}
