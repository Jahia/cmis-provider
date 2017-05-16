/**
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 * http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 * Copyright (C) 2002-2016 Jahia Solutions Group. All rights reserved.
 *
 * This file is part of a Jahia's Enterprise Distribution.
 *
 * Jahia's Enterprise Distributions must be used in accordance with the terms
 * contained in the Jahia Solutions Group Terms & Conditions as well as
 * the Jahia Sustainable Enterprise License (JSEL).
 *
 * For questions regarding licensing, support, production usage...
 * please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
 */
package org.jahia.modules.external.cmis;

import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.codehaus.plexus.util.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalData;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.modules.external.ExternalQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.*;

/**
 * Created by rizak on 10/06/16.
 */
public class NuxeoCmisDataSource extends CmisDataSource implements ExternalDataSource.Initializable {
    /*
    * The logger instance for this class
     */
    private static final Logger log = LoggerFactory.getLogger(NuxeoCmisDataSource.class);


    protected void setSessionProperties(Session cmisSession){
        super.setSessionProperties(cmisSession);
        //Get CMIS folder maps properties into string
        CmisTypeMapping folderTypeMapping = conf.getTypeByJCR("jnt:folder");
        String folderPropertiesList = folderTypeMapping.getPropertiesMapCMIS().keySet().toString().replaceAll("\\[","").replaceAll("]","");

        //Get CMIS files mapped properties into string
        CmisTypeMapping fileTypeMapping = conf.getTypeByJCR("nuxeo:file");
        String filePropertiesList = fileTypeMapping.getPropertiesMapCMIS().keySet().toString().replaceAll("\\[","").replaceAll("]","");
        filePropertiesList += ", nuxeo:pathSegment";

        //Set filter on the mapped properties properties
        cmisSession.getDefaultContext().setFilterString(folderPropertiesList+", "+filePropertiesList);
        //Set other request parameters
        cmisSession.getDefaultContext().setCacheEnabled(true);
        cmisSession.getDefaultContext().setIncludeAllowableActions(true);
        cmisSession.getDefaultContext().setIncludeAcls(false);
        cmisSession.getDefaultContext().setIncludePolicies(false);
        cmisSession.getDefaultContext().setIncludeRelationships(IncludeRelationships.NONE);
        cmisSession.getDefaultContext().setLoadSecondaryTypeProperties(false);
        cmisSession.getDefaultContext().setRenditionFilterString("cmis:none");
    }

    /**
     * Overriding the default function in order to add jmix:exif if at least one exif property value is set on the document
     * @param doc
     * @return The list of mixins to add to the document node
     */
    protected List<String> getMixinsToAdd(Document doc){
        List<String> mixins = super.getMixinsToAdd(doc);
        //If the document is an image we look for Exif properties
        if(mixins.contains(Constants.JAHIAMIX_IMAGE)){
            if(isExif(doc)){
                mixins.add("jmix:exif");
            }
        }
        return mixins;
    }

    /**
     * If at least one exif property is not empty on the document then we considerate it is an Exif document
     * To know if a document property is Exif we test the corresponding propertyMapping class looking for ExifPropertyMapping class
     * @param doc
     * @return
     */
    private boolean isExif(Document doc){
        CmisTypeMapping docTypeMapping = conf.getTypeByCMIS(doc.getBaseType().getId());
        if(docTypeMapping != null) {
            List<Property<?>> properties = doc.getProperties();
            //We get mapped exif properties values
            for (Property property : properties) {
                //Get the property mapping corresponding to the property
                CmisPropertyMapping mappedProperty = docTypeMapping.getPropertyByCMIS(property.getId());
                //Set exif mixin if at least one exif property is not empty
                if(mappedProperty != null){
                    if (mappedProperty instanceof ExifPropertyMapping && StringUtils.isNotEmpty(property.getValueAsString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

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
                            if (hasContent(object)) {
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
                                    String remotePath;
                                    Property<?> pathProperty = child.getProperty("cmis:path");
                                    if (pathProperty != null) {
                                        remotePath = removeRemotePath(pathProperty.getValueAsString());
                                    } else {
                                        //Specific code made to handle Nuxeo path segment specific truncation by getting nuxeo custom property
                                        Property<?> pathSegment = child.getProperty("nuxeo:pathSegment");
                                        remotePath = removeRemotePath(!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + (pathSegment != null ? pathSegment.getValueAsString() : child.getName());
                                    }
                                    String childPath = transformPath(remotePath, Operation.ENCODE);
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
                if (hasContent(object)) {
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
     * This function check if the cmis object in parameters has a content stream length in order to know
     * if a binary is associated to the document
     *
     * @param object : the cmis object of which content stream size has to be checked
     * @return true if content stream size is greater than 0 and false in the other case
     */
    private boolean hasContent(CmisObject object) {
        //Get the content stream size property of the cmis object
        Property contentStreamLength = object.getProperty("cmis:contentStreamLength");
        //Check if the size property exists and if its value is greater than 0
        if (contentStreamLength != null && contentStreamLength.getValues().size() > 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public List<String> search(final ExternalQuery query) throws RepositoryException {
        return executeWithCMISSession(new ExecuteCallback<List<String>>() {
            @Override
            public List<String> execute(Session session) {
                try {
                    NuxeoQueryResolver resolver = new NuxeoQueryResolver(NuxeoCmisDataSource.this, query);
                    String sql = resolver.resolve();

                    // Not mapped or unsupported queries treated as empty.
                    if (sql == null) {
                        return Collections.emptyList();
                    }

                    boolean isFolder = false;
                    if (BaseTypeId.CMIS_FOLDER.equals(session.getTypeDefinition(resolver.cmisType.getCmisName()).getBaseTypeId())) {
                        isFolder = true;
                        sql = sql.replace("cmis:objectId", "cmis:path");
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("CMIS query " + sql);
                    }
                    OperationContext operationContext = session.createOperationContext();
                    operationContext.setIncludePathSegments(true);
                    //In nuxeo the search query MUST be done with searchAllVersions attribute to true
                    ItemIterable<QueryResult> results = session.query(sql, true, operationContext);
                    if (query.getLimit() > 0 && query.getLimit() < Integer.MAX_VALUE) {
                        results = results.getPage((int) query.getLimit());
                    }
                    if (query.getOffset() != 0) {
                        results = results.skipTo(query.getOffset());
                    }
                    ArrayList<String> res = new ArrayList<>();
                    for (QueryResult hit : results) {
                        String remotePath = hit.getPropertyValueByQueryName("cmis:objectId").toString();
                        if (!isFolder) {
                            CmisObject object = session.getObject(remotePath);
                            remotePath = ((FileableCmisObject) object).getPaths().get(0);
                        }
                        res.add(removeRemotePath(remotePath));
                    }
                    return res;
                } catch (RepositoryException | CmisObjectNotFoundException e) {
                    // CmisObjectNotFoundException in case of the cmis server doesn't support query
                    log.warn("Can't execute query to cmis ", e);
                    return Collections.emptyList();
                }
            }
        });
    }

}
