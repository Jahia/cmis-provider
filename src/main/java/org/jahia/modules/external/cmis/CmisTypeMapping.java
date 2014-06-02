package org.jahia.modules.external.cmis;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration class to map CMIS <-> JCR types.
 * Support inheritances. May be reused for many DataSources.
 * Created by: Boris
 * Date: 1/29/14
 * Time: 10:51 PM
 */
public class CmisTypeMapping implements Cloneable{
    private String jcrName;
    private String cmisName;
    private String queryName;
    private CmisTypeMapping parent;
    private List<CmisTypeMapping> children;
    private List<CmisPropertyMapping> properties;
    private Map<String,CmisPropertyMapping> propertiesMapJCR;
    private Map<String,CmisPropertyMapping> propertiesMapCMIS;

    public CmisTypeMapping() {

    }
    public CmisTypeMapping(String jcrName, String cmisName) {
        this.jcrName = jcrName;
        this.cmisName = cmisName;
    }

    public String getJcrName() {
        return jcrName;
    }

    public void setJcrName(String jcrName) {
        this.jcrName = jcrName;
    }

    public String getCmisName() {
        return cmisName;
    }

    public void setCmisName(String cmisName) {
        this.cmisName = cmisName;
    }

    /**
     * Parent mapping. Used to configure tree of inheritance.
     * @return
     */
    public CmisTypeMapping getParent() {
        return parent;
    }

    public void setParent(CmisTypeMapping parent) {
        this.parent = parent;
    }

    /**
     * List of configured children.  Used to configure tree of inheritance.
     * Return children added directly by setChildren method only.
     * If children initialize inheritance using parent property this method will not return such children.
     * This field used for configuration purposes only.
     * @return List of configured children.
     */
    public List<CmisTypeMapping> getChildren() {
        return children;
    }

    public void setChildren(List<CmisTypeMapping> children) {
        this.children = children;
        if (children!=null) {
            for (CmisTypeMapping child : children) {
                child.setParent(this);
            }
        }
    }

    public List<CmisPropertyMapping> getProperties() {
        return properties;
    }

    public void setProperties(List<CmisPropertyMapping> properties) {
        this.properties = properties;
    }

    protected Map getPropertiesMapJCR() {
        return propertiesMapJCR;
    }

    protected Map getPropertiesMapCMIS() {
        return propertiesMapCMIS;
    }

    protected void initProperties() {
        HashMap<String,CmisPropertyMapping> mapJCR=new HashMap<String,CmisPropertyMapping>();
        HashMap<String,CmisPropertyMapping> mapCMIS=new HashMap<String,CmisPropertyMapping>();
        if (parent!=null) {
            mapJCR.putAll(parent.getPropertiesMapJCR());
            mapCMIS.putAll(parent.getPropertiesMapCMIS());
        }
        if (properties!=null) {
            for (CmisPropertyMapping property : properties) {
                mapJCR.put(property.getJcrName(), property);
                mapCMIS.put(property.getCmisName(), property);
            }
        }
        propertiesMapCMIS=mapCMIS.size()==0? Collections.<String, CmisPropertyMapping>emptyMap() :Collections.unmodifiableMap(mapCMIS);
        propertiesMapJCR= mapJCR.size()==0? Collections.<String, CmisPropertyMapping>emptyMap() :Collections.unmodifiableMap(mapJCR);
        if (children!=null) {
            for (CmisTypeMapping child : children) {
                child.initProperties();
            }
        }
    }


    @Override
    protected CmisTypeMapping clone() {
        try {
            return (CmisTypeMapping)super.clone();
        } catch (CloneNotSupportedException e) {  // impossible
            throw new IllegalStateException(e);
        }
    }

    /**
     * Lookup property mapping by JCR name
     * @param propertyName
     * @return
     */
    public CmisPropertyMapping getPropertyByJCR(String propertyName) {
        return propertiesMapJCR.get(propertyName);
    }

    /**
     * Lookup property mapping by CMIS local name
     * @param localName
     * @return
     */
    public CmisPropertyMapping getPropertyByCMIS(String localName) {
        return propertiesMapCMIS.get(localName);
    }

    /**
     * Name of type used in CMIS queries.
     * If not set return cmisName
     * @return
     */
    public String getQueryName() {
        return queryName==null?cmisName:queryName;
    }

    public void setQueryName(String queryName) {
        this.queryName = queryName;
    }
}
