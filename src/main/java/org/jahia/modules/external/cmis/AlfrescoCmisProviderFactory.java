package org.jahia.modules.external.cmis;

import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.registries.ServicesRegistry;
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

/**
 * TODO Comment me
 *
 * @author toto
 */
public class AlfrescoCmisProviderFactory implements ProviderFactory, ApplicationContextAware {
    private ApplicationContext applicationContext;

    public String getNodeTypeName() {
        return "jnt:alfrescoCmisMountPoint";
    }

    @Override
    public JCRStoreProvider mountProvider(JCRNodeWrapper mountPoint) throws RepositoryException {
        ExternalContentStoreProvider provider = (ExternalContentStoreProvider) SpringContextSingleton.getBean("ExternalStoreProviderPrototype");
        provider.setKey(mountPoint.getIdentifier());
        provider.setMountPoint(mountPoint.getPath());
        AlfrescoCmisDataSource dataSource = new AlfrescoCmisDataSource();

        CmisConfiguration conf = (CmisConfiguration) applicationContext.getBean("CmisConfiguration");
        conf.getRepositoryPropertiesMap().put("alfresco.url", mountPoint.getProperty("j:url").getString());
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.binding.atompub.url", mountPoint.getProperty("j:url").getString() + "/cmisatom");
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.session.repository.id", mountPoint.getProperty("j:repositoryId").getString());
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.user", mountPoint.getProperty("j:rootUsername").getString());
        conf.getRepositoryPropertiesMap().put("org.apache.chemistry.opencmis.password", mountPoint.getProperty("j:rootPassword").getString());
        dataSource.setConf(conf);
        dataSource.start();
        provider.setDataSource(dataSource);
        provider.setOverridableItems(Arrays.asList(new String[]{"jmix:description.*", "jmix:i18n.*"}));
        provider.setNonExtendableMixins(Arrays.asList(new String[]{"cmismix:base", "cmismix:folder", "cmismix:document", "jmix:image"}));
        provider.setDynamicallyMounted(true);
        provider.setSessionFactory(JCRSessionFactory.getInstance());

        try {
            provider.start();
            return provider;
        } catch (JahiaInitializationException var6) {
            throw new RepositoryException(var6);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
