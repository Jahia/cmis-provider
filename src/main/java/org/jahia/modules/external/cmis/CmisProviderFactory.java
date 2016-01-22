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
package org.jahia.modules.external.cmis;

import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.security.license.LicenseCheckException;
import org.jahia.security.license.LicenseCheckerService;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRStoreProvider;
import org.jahia.services.content.ProviderFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.jcr.RepositoryException;
import java.util.Arrays;

public class CmisProviderFactory implements ProviderFactory, ApplicationContextAware, InitializingBean {

    private static final String ALFRESCO_ENDPOINT_BROWSER = "/api/-default-/public/cmis/versions/1.1/browser";
    private static final String ALFRESCO_ENDPOINT_ATOM = "/api/-default-/public/cmis/versions/1.1/atom";

    private ApplicationContext applicationContext;

    @Override
    public String getNodeTypeName() {
        return "cmis:cmisMountPoint";
    }

    @Override
    public JCRStoreProvider mountProvider(JCRNodeWrapper mountPoint) throws RepositoryException {
        ExternalContentStoreProvider provider = (ExternalContentStoreProvider) SpringContextSingleton.getBean("ExternalStoreProviderPrototype");
        provider.setKey(mountPoint.getIdentifier());
        provider.setMountPoint(mountPoint.getPath());

        CmisConfiguration conf = (CmisConfiguration) applicationContext.getBean("CmisConfiguration");
        CmisDataSource dataSource;
        if("alfresco".equals(mountPoint.getProperty("type").getString())) {
            dataSource = new AlfrescoCmisDataSource();
            conf.getRepositoryPropertiesMap().put("alfresco.url", mountPoint.getProperty("url").getString());
            if(BindingType.BROWSER.value().equals(conf.getRepositoryPropertiesMap().get(SessionParameter.BINDING_TYPE))) {
                conf.getRepositoryPropertiesMap().put(SessionParameter.BROWSER_URL, mountPoint.getProperty("url").getString() + ALFRESCO_ENDPOINT_BROWSER);
            } else {
                conf.getRepositoryPropertiesMap().put(SessionParameter.ATOMPUB_URL, mountPoint.getProperty("url").getString() + ALFRESCO_ENDPOINT_ATOM);
            }
        } else {
            dataSource = new CmisDataSource();
            if(BindingType.BROWSER.value().equals(conf.getRepositoryPropertiesMap().get(SessionParameter.BINDING_TYPE))) {
                conf.getRepositoryPropertiesMap().put(SessionParameter.BROWSER_URL, mountPoint.getProperty("url").getString());
            } else {
                conf.getRepositoryPropertiesMap().put(SessionParameter.ATOMPUB_URL, mountPoint.getProperty("url").getString());
            }
        }
        conf.getRepositoryPropertiesMap().put(SessionParameter.REPOSITORY_ID, mountPoint.getProperty("repositoryId").getString());
        conf.getRepositoryPropertiesMap().put(SessionParameter.USER, mountPoint.getProperty("user").getString());
        conf.getRepositoryPropertiesMap().put(SessionParameter.PASSWORD, mountPoint.getProperty("password").getString());
        dataSource.setConf(conf);
        dataSource.start();

        provider.setDataSource(dataSource);
        provider.setOverridableItems(Arrays.asList("jmix:description.*", "jmix:i18n.*"));
        provider.setNonExtendableMixins(Arrays.asList("cmismix:base", "cmismix:folder", "cmismix:document", "jmix:image"));
        provider.setDynamicallyMounted(true);
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
}
