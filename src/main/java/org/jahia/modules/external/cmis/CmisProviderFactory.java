package org.jahia.modules.external.cmis;

import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRStoreProvider;
import org.jahia.services.content.ProviderFactory;
import org.jahia.services.templates.JahiaModuleAware;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.jcr.RepositoryException;

public class CmisProviderFactory implements ProviderFactory, ApplicationContextAware {
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
        provider.setLockSupport(true);

        CmisDataSource dataSource = new CmisDataSource();
        CmisConfiguration conf = (CmisConfiguration) applicationContext.getBean("CmisConfiguration");
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.binding.atompub.url", mountPoint.getProperty("url").getString());
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.session.repository.id", mountPoint.getProperty("repositoryId").getString());
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.user", mountPoint.getProperty("user").getString());
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.password", mountPoint.getProperty("password").getString());
        dataSource.setConf(conf);

        dataSource.start();

        provider.setDataSource(dataSource);
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
}
