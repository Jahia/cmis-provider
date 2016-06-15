package org.jahia.modules.external.cmis;

import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.jahia.modules.external.ExternalData;
import org.jahia.modules.external.ExternalDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by rizak on 10/06/16.
 */
public class NuxeoCmisDataSource extends CmisDataSource implements ExternalDataSource.Initializable {
    /*
    * The logger instance for this class
     */
    private static final Logger log = LoggerFactory.getLogger(NuxeoCmisDataSource.class);
    @Override
    public List<ExternalData> getChildrenNodes(final String path) throws RepositoryException {
        return executeWithCMISSession(new ExecuteCallback<List<ExternalData>>() {
            @Override
            public List<ExternalData> execute(Session session) throws RepositoryException {
                ArrayList<ExternalData> list = new ArrayList<>();
                try {
                    if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                        // the path is encoded by DX using JCRContentUtils.escapeLocalNodeName() that
                        // do not encode "+" that has to be encoded
                        CmisObject object = session.getObjectByPath(addRemotePath(transformPath(transformPath(path, Operation.DECODE), Operation.URLENCODE)));
                        if (object instanceof Document) {
                            if(hasContent(object)) {
                                return Collections.singletonList(getObjectContent((Document) object, path + JCR_CONTENT_SUFFIX));
                            } else {
                                return new ArrayList<ExternalData>();
                            }
                        } else if (object instanceof Folder) {
                            Folder folder = (Folder) object;
                            ItemIterable<CmisObject> children = folder.getChildren();
                            int i = 0;
                            for (CmisObject child : children) {
                                if (child.getBaseTypeId() == BaseTypeId.CMIS_DOCUMENT || child.getBaseTypeId() == BaseTypeId.CMIS_FOLDER) {
                                    if (maxChildNodes > 0 && ++i > maxChildNodes) {
                                        log.warn(String.format("getChildrenNodes returns too many children - path : %s , number of children %s, max children %s", path, children.getTotalNumItems(), maxChildNodes));
                                        break;
                                    }
                                    //Change object path resolution in order to be able to handle non updated paths when object's title changes
                                    String childPath;
                                    Property pathProperty = child.getProperty("cmis:path");
                                    if(pathProperty != null) {
                                        childPath = transformPath(removeRemotePath(pathProperty.getValueAsString()),Operation.ENCODE);
                                    } else {
                                        //Specific code made to handle Nuxeo path segment specific truncation by getting nuxeo custom property
                                        Property pathSegment = child.getProperty("nuxeo:pathSegment");
                                        if(pathSegment != null) {
                                            childPath = transformPath(removeRemotePath(!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + pathSegment.getValueAsString(), Operation.ENCODE);
                                        } else {
                                            childPath = transformPath(removeRemotePath(!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + child.getName(), Operation.ENCODE);
                                        }
                                    }
                                    list.add(getObject(child, childPath));
                                    if (child.getBaseTypeId() == BaseTypeId.CMIS_DOCUMENT && hasContent(child)) {
                                        list.add(getObjectContent((Document) child, childPath + JCR_CONTENT_SUFFIX));
                                    }
                                }
                            }
                        }
                    }
                } catch (CmisObjectNotFoundException | PathNotFoundException e) {
                    throw new PathNotFoundException("Can't find cmis folder " + path, e);
                }
                return list;
            }
        });
    }

    @Override
    public ExternalData getItemByPath(String path) throws PathNotFoundException {
        try {
            if (path.endsWith(CmisDataSource.JCR_CONTENT_SUFFIX)) {
                CmisObject object = getObjectByPath(removeContentSufix(path));
                //Handle case of CMIS documents without binaries
                if(hasContent(object)) {
                    return getObjectContent((Document) object, path);
                } else {
                    return getObject(object, path);
                }
            } else {
                CmisObject object = getObjectByPath(path);
                return getObject(object, path);
            }
        } catch (Exception e) {
            throw new PathNotFoundException("Can't find object by path " + path, e);
        }
    }

    /**
     * This function check if the cmis object in parameters has a content stream length greater than 0
     * @param object : the cmis object of which content stream size has to be checked
     * @return true if content stream size is greater than 0 and false in the other case
     */
    private boolean hasContent(CmisObject object){
        //Get the content stream size property of the cmis object
        Property contentStreamLength = object.getProperty("cmis:contentStreamLength");
        //Check if the size property exists and if its value is greater than 0
        if(contentStreamLength != null && contentStreamLength.getValues().size() > 0){
            return true;
        } else {
            return false;
        }
    }
}
