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
package org.jahia.modules.external.cmis;

import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.modules.external.cmis.admin.CMISMountPointFactory;
import org.jahia.modules.external.cmis.services.NuxeoFileNode;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.jahia.services.content.*;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.jcr.RepositoryException;
import java.util.Arrays;

public class CmisProviderFactory implements ProviderFactory, ApplicationContextAware {

    private static final String ALFRESCO_ENDPOINT_BROWSER = "/api/-default-/public/cmis/versions/1.1/browser";
    private static final String ALFRESCO_ENDPOINT_ATOM = "/api/-default-/public/cmis/versions/1.1/atom";
    private static final String NUXEO_ENDPOINT_ATOM = "/nuxeo/atom/cmis/default/";

    private static final String CONF_MAX_CHILD_NODES = "org.jahia.cmis.max.child.nodes";

    private ApplicationContext applicationContext;

    public static final String TYPE_ALFRESCO = "alfresco";
    public static final String ALFRESCO_URL = "alfresco.url";

    public static final String TYPE_NUXEO = "nuxeo";
    public static final String NUXEO_URL = "nuxeo.url";
    private JCRStoreService jcrStoreService;
    private EhCacheProvider ehCacheprovider;

    @Override
    public String getNodeTypeName() {
        return "cmis:cmisMountPoint";
    }

    @Override
    public JCRStoreProvider mountProvider(JCRNodeWrapper mountPoint) throws RepositoryException {
        ExternalContentStoreProvider provider = (ExternalContentStoreProvider) SpringContextSingleton.getBean("ExternalStoreProviderPrototype");
        provider.setKey(mountPoint.getIdentifier());
        provider.setMountPoint(mountPoint.getPath());
        CmisDataSource dataSource;
        CmisConfiguration conf = null;
        String cmisUrl = mountPoint.getProperty("url").getString();
        cmisUrl = StringUtils.endsWith(cmisUrl, "/") ? StringUtils.substring(cmisUrl, 0, cmisUrl.length() - 1) : cmisUrl;
        String type = "";
        if (mountPoint.hasProperty(CMISMountPointFactory.TYPE)) {
            type = mountPoint.getProperty(CMISMountPointFactory.TYPE).getString();
        }
        if (TYPE_ALFRESCO.equals(type)) {
            conf = (CmisConfiguration) applicationContext.getBean("CmisConfiguration");
            dataSource = new AlfrescoCmisDataSource();
            conf.getRepositoryPropertiesMap().put(ALFRESCO_URL, cmisUrl);
            if (BindingType.BROWSER.value().equals(conf.getRepositoryPropertiesMap().get(SessionParameter.BINDING_TYPE))) {
                conf.getRepositoryPropertiesMap().put(SessionParameter.BROWSER_URL, cmisUrl + ALFRESCO_ENDPOINT_BROWSER);
            } else {
                conf.getRepositoryPropertiesMap().put(SessionParameter.ATOMPUB_URL, cmisUrl + ALFRESCO_ENDPOINT_ATOM);
            }
            if (mountPoint.hasProperty(CMISMountPointFactory.PUBLIC_USER)) {
                ((AlfrescoCmisDataSource) dataSource).setPublicUser(mountPoint.getProperty(CMISMountPointFactory.PUBLIC_USER).getString());
            }
        } else if (TYPE_NUXEO.equals(type)) {
            //Get Nuxeo conf for type Mapping
            conf = (CmisConfiguration) applicationContext.getBean("NuxeoConfiguration");

            //Change file decorator to use the nuxeo one (Display only title instead of fileName and title in gwt
            if (!jcrStoreService.getDecorators().containsKey("nuxeo:file")) {
                jcrStoreService.addDecorator("nuxeo:file", NuxeoFileNode.class);
            }

            if (!jcrStoreService.getDecorators().containsKey("nuxeo:folder")) {
                jcrStoreService.addDecorator("nuxeo:folder", NuxeoFileNode.class);
            }

            //Setup Nuxeo datasource
            dataSource = new NuxeoCmisDataSource();
            conf.getRepositoryPropertiesMap().put(NUXEO_URL, cmisUrl);
            conf.getRepositoryPropertiesMap().put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
            conf.getRepositoryPropertiesMap().put(SessionParameter.ATOMPUB_URL, cmisUrl + NUXEO_ENDPOINT_ATOM);
        } else {
            conf = (CmisConfiguration) applicationContext.getBean("CmisConfiguration");
            // legacy support
            dataSource = new CmisDataSource();
            conf.getRepositoryPropertiesMap().put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
            conf.getRepositoryPropertiesMap().put(SessionParameter.ATOMPUB_URL, cmisUrl);
        }
        conf.getRepositoryPropertiesMap().put(SessionParameter.REPOSITORY_ID, mountPoint.getProperty(CMISMountPointFactory.REPOSITORY_ID).getString());
        conf.getRepositoryPropertiesMap().put(SessionParameter.USER, mountPoint.getProperty(CMISMountPointFactory.USER).getString());
        conf.getRepositoryPropertiesMap().put(SessionParameter.PASSWORD, mountPoint.getProperty(CMISMountPointFactory.PASSWORD).getString());
        dataSource.setConf(conf);
        String remotePath = "";
        if (mountPoint.hasProperty(CMISMountPointFactory.REMOTE_PATH)) {
            remotePath = mountPoint.getProperty(CMISMountPointFactory.REMOTE_PATH).getString();
            remotePath = StringUtils.equals(remotePath, "/") ? "" : remotePath;
        }
        dataSource.setProvider(provider);
        dataSource.setRemotePath(remotePath);
        dataSource.setCacheProvider(ehCacheprovider);
        int maxChildNodesConfiguration = Integer.parseInt(conf.getRepositoryPropertiesMap().get(CONF_MAX_CHILD_NODES));
        int maxChildNodesMountPoint = (int) mountPoint.getProperty(CMISMountPointFactory.MAX_CHILD_NODES).getValue().getLong();
        dataSource.setMaxChildNodes(maxChildNodesMountPoint == 0 ? maxChildNodesConfiguration : maxChildNodesMountPoint);
        dataSource.setMaxItemsPerPage((int) mountPoint.getProperty(CMISMountPointFactory.MAX_ITEMS_PER_PAGE).getValue().getLong());
        dataSource.setTtidleSeconds((int) mountPoint.getProperty(CMISMountPointFactory.TTIDLE_SECONDS).getValue().getLong());
        dataSource.setTtliveSeconds((int) mountPoint.getProperty(CMISMountPointFactory.TTLIVE_SECONDS).getValue().getLong());
        dataSource.start();

        boolean slowConnection = false;
        if (mountPoint.hasProperty(CMISMountPointFactory.SLOW_CONNECTION)) {
            slowConnection = mountPoint.getProperty(CMISMountPointFactory.SLOW_CONNECTION).getBoolean();
        }
        provider.setSlowConnection(slowConnection);
        provider.setDataSource(dataSource);
        provider.setOverridableItems(Arrays.asList("jmix:description.*", "jmix:i18n.*"));
        provider.setNonExtendableMixins(Arrays.asList("cmismix:base", "cmismix:folder", "cmismix:document", "jmix:image"));
        provider.setDynamicallyMounted(true);
        provider.setCacheKeyOnReferenceSupport(TYPE_ALFRESCO.equals(type));
        provider.setSessionFactory(JCRSessionFactory.getInstance());
        try {
            provider.start();
        } catch (JahiaInitializationException e) {
            throw new RepositoryException(e);
        }
        return provider;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setJcrStoreService(JCRStoreService jcrStoreService) {
        this.jcrStoreService = jcrStoreService;
    }

    public void setEhCacheprovider(EhCacheProvider ehCacheprovider) {
        this.ehCacheprovider = ehCacheprovider;
    }

}
