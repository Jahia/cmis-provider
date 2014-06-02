package org.jahia.modules.external.cmis;

import java.util.*;

/**
 * Configuration bean for CMIS.
 * Contains complete configuration settings for CmisDataSource.
 * Created by: Boris
 * Date: 1/31/14
 * Time: 7:48 PM
 */
public class CmisConfiguration {
    private List<CmisTypeMapping> typeMapping;
    private Map<String,CmisTypeMapping> cmisTypes;
    private Set<String> supportedNodeTypes;
    private Map<String,CmisTypeMapping> jcrTypes;
    private String defaultDocumentTypeName="cmis:document";
    private String defaultFolderTypeName="cmis:folder";
//    private Properties repositoryProperties;
    private HashMap<String,String> repositoryPropertiesMap;

    public void onStart() {
        cmisTypes=new HashMap<String, CmisTypeMapping>();
        jcrTypes=new HashMap<String, CmisTypeMapping>();
        if (typeMapping!=null) {
            Queue<CmisTypeMapping> list=new LinkedList<CmisTypeMapping>(typeMapping);
            while (!list.isEmpty()) {
                CmisTypeMapping type=list.remove();
                cmisTypes.put(type.getCmisName(),type);
                jcrTypes.put(type.getJcrName(),type);
                if (type.getChildren()!=null) {
                    list.addAll(type.getChildren());
                }
            }
            for (CmisTypeMapping cmisTypeMapping : typeMapping) {
                cmisTypeMapping.initProperties();
            }
        }
        supportedNodeTypes=Collections.unmodifiableSet(new HashSet(cmisTypes.keySet()));
    }
    public void setTypeMapping(List<CmisTypeMapping> typeMapping) {
        this.typeMapping = typeMapping;

    }
    public List getTypeMapping() {
        return typeMapping;
    }

    public CmisTypeMapping getTypeByJCR(String name) {
        return jcrTypes.get(name);
    }

    public CmisTypeMapping getTypeByCMIS(String name) {
        return cmisTypes.get(name);
    }

    public CmisTypeMapping getDefaultFolderType() {
        return cmisTypes.get(defaultFolderTypeName);
    }

    public CmisTypeMapping getDefaultDocumentType() {
        return cmisTypes.get(defaultDocumentTypeName);
    }


    /**
     * @return Name of CMIS typed for CMIS documents used if real type not mapped
     */
    public String getDefaultDocumentTypeName() {
        return defaultDocumentTypeName;
    }

    public void setDefaultDocumentTypeName(String defaultDocumentTypeName) {
        this.defaultDocumentTypeName = defaultDocumentTypeName;
    }

    /**
     * @return Name of CMIS typed for CMIS folder used if real type not mapped
     */
    public String getDefaultFolderTypeName() {
        return defaultFolderTypeName;
    }

    public void setDefaultFolderTypeName(String defaultFolderTypeName) {
        this.defaultFolderTypeName = defaultFolderTypeName;
    }

    /**
     * @return  set of JSR node types which mapped to CMIS types
     */
    public Set<String> getSupportedNodeTypes() {
        return supportedNodeTypes;
    }

    /**
     * Don't call for configuration purposes only.
     * Use getRepositoryPropertiesMap for getting properties
     * @return throw UnsupportedOperationException
     */
    public Properties getRepositoryProperties() {
        throw new UnsupportedOperationException();
    }

    /**
     * For configuration purposes only. Init RepositoryPropertiesMap, filtering passed properties by names prefixed 'org.apache.chemistry.'
     * Use getRepositoryPropertiesMap for getting properties
     */
    public void setRepositoryProperties(Properties repositoryProperties) {
        repositoryPropertiesMap=new HashMap<String, String>();
        for (String name : repositoryProperties.stringPropertyNames()) {
            if (name.startsWith("org.apache.chemistry.")) {
                repositoryPropertiesMap.put(name, repositoryProperties.getProperty(name));
            }
        }
    }

    /**
     *
     * @return
     */
    public HashMap<String, String> getRepositoryPropertiesMap() {
        return repositoryPropertiesMap;
    }
}
