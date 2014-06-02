package org.jahia.modules.external.cmis;

/**
 * Configuration class to map CMIS <-> JCR type attributes.
 * Any attribute may be mapped in list of modes. Depends on mode attribute may support reade, write and be used on create.
 * Created by: Boris
 * Date: 1/29/14
 * Time: 10:54 PM
 */
public class CmisPropertyMapping {
    /**
     * Property will readable
     */
    public static final String MODE_READ="r";
    /**
     * Property will updatable
     */
    public static final String MODE_WRITE="w";
    /**
     * Property will use on creation
     */
    public static final String MODE_CREATE="c";
    private String jcrName;
    private String cmisName;
    private String queryName;
    /**
     * Mode of property may combination of 'r' -Read; 'w' - Write (update); 'c' - Create
     */
    String mode="r";
    public CmisPropertyMapping() {
    }

    public CmisPropertyMapping(String jcrName, String cmisName) {
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
     * Mode of property may combination of 'r' -Read; 'w' - Write (update); 'c' - Create
     */
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean inMode(char _mode) {
        return mode.indexOf(_mode)!=-1;
    }

    /**
     * @return name used in Queries, if not set used cmisName
     */
    public String getQueryName() {
        return queryName==null?cmisName:queryName;
    }

    public void setQueryName(String queryName) {
        this.queryName = queryName;
    }
}
