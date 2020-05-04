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

import java.util.*;

/**
 * Configuration class to map CMIS <-> JCR types.
 * Support inheritances. May be reused for many DataSources.
 * Created by: Boris
 * Date: 1/29/14
 * Time: 10:51 PM
 */
public class CmisTypeMapping {
    private String jcrName;
    private Set<String> additionalSupportedMixins;
    private List<String> jcrMixins;
    private String cmisName;
    private String queryName;
    private CmisTypeMapping parent;
    private List<CmisTypeMapping> children;
    private List<CmisPropertyMapping> properties;
    private Map<String, CmisPropertyMapping> propertiesMapJCR;
    private Map<String, CmisPropertyMapping> propertiesMapCMIS;

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

    public List<String> getJcrMixins() {
        return jcrMixins;
    }

    public void setJcrMixins(String jcrName) {
        this.jcrMixins = Arrays.asList(jcrName.split(" "));
    }

    public Set<String> getAdditionalSupportedMixins() {
        return additionalSupportedMixins;
    }

    public void setAdditionalSupportedMixins(String additionalSupportedMixins) {
        this.additionalSupportedMixins = new HashSet<>(Arrays.asList(additionalSupportedMixins.split(" ")));
    }

    public String getCmisName() {
        return cmisName;
    }

    public void setCmisName(String cmisName) {
        this.cmisName = cmisName;
    }

    /**
     * Parent mapping. Used to configure tree of inheritance.
     *
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
     *
     * @return List of configured children.
     */
    public List<CmisTypeMapping> getChildren() {
        return children;
    }

    public void setChildren(List<CmisTypeMapping> children) {
        this.children = children;
        if (children != null) {
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

    protected Map<String, CmisPropertyMapping> getPropertiesMapJCR() {
        return propertiesMapJCR;
    }

    protected Map<String, CmisPropertyMapping> getPropertiesMapCMIS() {
        return propertiesMapCMIS;
    }

    protected void initProperties() {
        HashMap<String, CmisPropertyMapping> mapJCR = new HashMap<>();
        HashMap<String, CmisPropertyMapping> mapCMIS = new HashMap<>();
        if (parent != null) {
            mapJCR.putAll(parent.getPropertiesMapJCR());
            mapCMIS.putAll(parent.getPropertiesMapCMIS());
        }
        if (properties != null) {
            for (CmisPropertyMapping property : properties) {
                mapJCR.put(property.getJcrName(), property);
                mapCMIS.put(property.getCmisName(), property);
            }
        }
        propertiesMapCMIS = mapCMIS.size() == 0 ? Collections.<String, CmisPropertyMapping>emptyMap() : Collections.unmodifiableMap(mapCMIS);
        propertiesMapJCR = mapJCR.size() == 0 ? Collections.<String, CmisPropertyMapping>emptyMap() : Collections.unmodifiableMap(mapJCR);
        if (children != null) {
            for (CmisTypeMapping child : children) {
                child.initProperties();
            }
        }
    }

    /**
     * Lookup property mapping by JCR name
     *
     * @param propertyName
     * @return
     */
    public CmisPropertyMapping getPropertyByJCR(String propertyName) {
        return propertiesMapJCR.get(propertyName);
    }

    /**
     * Lookup property mapping by CMIS local name
     *
     * @param localName
     * @return
     */
    public CmisPropertyMapping getPropertyByCMIS(String localName) {
        return propertiesMapCMIS.get(localName);
    }

    /**
     * Name of type used in CMIS queries.
     * If not set return cmisName
     *
     * @return
     */
    public String getQueryName() {
        return queryName == null ? cmisName : queryName;
    }

    public void setQueryName(String queryName) {
        this.queryName = queryName;
    }
}
