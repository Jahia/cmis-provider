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

import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.modules.external.cmis.admin.CMISMountPointFactory;
import org.jahia.modules.external.cmis.services.NuxeoFileNode;
import org.jahia.security.license.LicenseCheckException;
import org.jahia.security.license.LicenseCheckerService;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.HashMap;

public class CmisProviderFactory implements ProviderFactory, ApplicationContextAware, InitializingBean {

    private static final String ALFRESCO_ENDPOINT_BROWSER = "/api/-default-/public/cmis/versions/1.1/browser";
    private static final String ALFRESCO_ENDPOINT_ATOM = "/api/-default-/public/cmis/versions/1.1/atom";
    private static final String NUXEO_ENDPOINT_ATOM = "/nuxeo/atom/cmis/default/";
    private static final String CONNECT_TIMEOUT = "org.apache.chemistry.opencmis.binding.connecttimeout";
    private ApplicationContext applicationContext;

    public static final String TYPE_ALFRESCO = "alfresco";
    public static final String ALFRESCO_URL = "alfresco.url";

    public static final String TYPE_NUXEO = "nuxeo";
    public static final String NUXEO_URL = "nuxeo.url";
    private JCRStoreService jcrStoreService;

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
        conf.getRepositoryPropertiesMap().put(SessionParameter.CONNECT_TIMEOUT, conf.getRepositoryPropertiesMap().get(CONNECT_TIMEOUT));

        //DTIC : Ajout du timeout à la lecture également
        HashMap<String, String> repositoryPropertiesMap = conf.getRepositoryPropertiesMap();

        String connectionTimeout = mountPoint.getPropertyAsString(CMISMountPointFactory.CONNECT_TIMEOUT);
        String readTimeout = mountPoint.getPropertyAsString(CMISMountPointFactory.READ_TIMEOUT);

        String compression = mountPoint.getPropertyAsString(CMISMountPointFactory.COMPRESSION);
        String clientCompression = mountPoint.getPropertyAsString(CMISMountPointFactory.CLIENT_COMPRESSION);
        String cookies = mountPoint.getPropertyAsString(CMISMountPointFactory.COOKIES);
        String forcedCMISVersion = mountPoint.getPropertyAsString(CMISMountPointFactory.FORCED_CMIS_VERSION);

        String cacheEnable=mountPoint.getPropertyAsString(CMISMountPointFactory.CACHE_ENABLED);
        String filter=mountPoint.getPropertyAsString(CMISMountPointFactory.RESPONSE_FILTER);
        String includeAllowableAction=mountPoint.getPropertyAsString(CMISMountPointFactory.INCLUDE_ACTIONS);
        String includeAcls=mountPoint.getPropertyAsString(CMISMountPointFactory.INCLUDE_ACL);
        String includePolicies=mountPoint.getPropertyAsString(CMISMountPointFactory.INCLUDE_POLICIES);

        String includeRelationships=mountPoint.getPropertyAsString(CMISMountPointFactory.INCLUDE_RELATIONSHIPS);
        String loadSecondaryTypeProperties=mountPoint.getPropertyAsString(CMISMountPointFactory.LOAD_SECONDARY_TYPE_PROPERTIES);
        String renditionFilter=mountPoint.getPropertyAsString(CMISMountPointFactory.RENDITION_FILTER);


        repositoryPropertiesMap.put(SessionParameter.CONNECT_TIMEOUT, connectionTimeout);
        repositoryPropertiesMap.put(SessionParameter.READ_TIMEOUT, readTimeout);

        // source: CMIS and Apache Chemistry in Action / Chapter 13. Performance :
        /* CMIS sends XML and JSON requests and responses over the wire. Both compress very well (with gzip).
         * The size of an AtomPub feed shrinks between 5% and 95% when it s compressed.
         * Compression can burst application performance, especially on slow networks and over the internet.
         *
         * Clients can request RESPONSE compression by setting the HTTP header Accept-Encoding.
         * OpenCMIS does that if you turn on compression when you set up the session, exemple:
         * parameter.put(SessionParameter.COMPRESSION, "true");
         * REM: That doesn t necessarily mean the repository returns a compressed response.
         * Some repositories support it out of the box, and others don t (and they ignore it then)
         * Check with the repository vendor as well as the application server vendor.
         * For Alfresco, see: https://wiki.alfresco.com/wiki/CMIS#Compression
         */
        conf.getRepositoryPropertiesMap().put(SessionParameter.COMPRESSION, compression);
        /* OpenCMIS can also compress REQUESTs.
         * Turn on client compression when you set up the session, like this:
         * (but it's not recommanded by default for normal use because it's not a good tradeoff : compression time VS normal packet transfert time)
         */
        conf.getRepositoryPropertiesMap().put(SessionParameter.CLIENT_COMPRESSION, clientCompression);
        conf.getRepositoryPropertiesMap().put(SessionParameter.COOKIES, cookies);
        conf.getRepositoryPropertiesMap().put(SessionParameter.FORCE_CMIS_VERSION, forcedCMISVersion); //Valeur a valider


        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_CACHE_ENABLE,cacheEnable);
        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_FILTER,filter);
        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_INCLUDE_ALLOWABLE_ACTIONS,includeAllowableAction);
        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_INCLUDE_ACLS, includeAcls);
        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_INCLUDE_POLICIES, includePolicies);

        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_INCLUDE_RELATIONSHIPS, includeRelationships);
        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_LOAD_SECONDARY_TYPE_PROPERTIES, loadSecondaryTypeProperties);
        conf.getRepositoryPropertiesMap().put(CmisDataSource.CONF_CONTEXT_RENDITION_FILTER, renditionFilter);

        dataSource.setConf(conf);
        String remotePath = "";
        if (mountPoint.hasProperty(CMISMountPointFactory.REMOTE_PATH)) {
            remotePath = mountPoint.getProperty(CMISMountPointFactory.REMOTE_PATH).getString();
            remotePath = StringUtils.equals(remotePath, "/") ? "" : remotePath;
        }
        dataSource.setProvider(provider);
        dataSource.setRemotePath(remotePath);
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

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!LicenseCheckerService.Stub.isAllowed("org.jahia.cmis")) {
            throw new LicenseCheckException("No license found for CMIS connector");
        }
    }

    public void setJcrStoreService(JCRStoreService jcrStoreService) {
        this.jcrStoreService = jcrStoreService;
    }
}
