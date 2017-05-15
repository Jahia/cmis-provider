package org.jahia.modules.external.cmis;

/**
 * Created by rahmed on 12/05/17.
 */
public class ExifPropertyMapping extends CmisPropertyMapping {
    //Class for exif properties to be check on instance

    public ExifPropertyMapping() {
        super();
    }

    public ExifPropertyMapping(String jcrName, String cmisName) {
        super(jcrName,cmisName);
    }

}
