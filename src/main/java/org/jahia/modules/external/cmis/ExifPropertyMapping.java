package org.jahia.modules.external.cmis;

/**
 * Configuration class to map EXIF metadata based CMIS <-> JCR type attributes.
 */
public class ExifPropertyMapping extends CmisPropertyMapping {

    public ExifPropertyMapping() {
        super();
    }

    public ExifPropertyMapping(String jcrName, String cmisName) {
        super(jcrName,cmisName);
    }

}
