package org.jahia.modules.external.cmis;

import javax.jcr.Binary;
import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;

import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.jackrabbit.util.ISO8601;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalData;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.modules.external.ExternalQuery;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExternalDataSource implementation for CMIS
 * full support for write and search
 * Created by: Boris
 * Date: 1/20/14
 * Time: 7:35 PM
 */
public class CmisDataSource  implements ExternalDataSource,ExternalDataSource.Initializable,ExternalDataSource.Writable,ExternalDataSource.Searchable {
    private static final String DEFAULT_MIMETYPE = "binary/octet-stream";;
    private static final List<String> JCR_CONTENT_LIST = Arrays.asList(Constants.JCR_CONTENT);
    private static final String JCR_CONTENT_SUFFIX="/"+Constants.JCR_CONTENT;
    private boolean firstConnectFailure=true;

    /*
    * The logger instance for this class
    */
    private static final Logger log = LoggerFactory.getLogger(CmisDataSource.class);

    /**
     * Shared CMIS session.
     */
    private Session cmisSession;

    /**
     * Configuration
     */
    CmisConfiguration conf;


    @Override
    public List<String> getChildren(String path) throws RepositoryException {
        List<String> list = new ArrayList<String>();
        try {
            if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getCmisSession().getObjectByPath(path);
                if (object instanceof Document) {
                    return JCR_CONTENT_LIST;
                } else if (object instanceof Folder) {
                    Folder folder = (Folder)object;
                    OperationContext operationContext = getCmisSession().createOperationContext();
                    operationContext.setMaxItemsPerPage(Integer.MAX_VALUE);

                    ItemIterable<CmisObject> children = folder.getChildren(operationContext);
                    for (CmisObject child : children) {
                        list.add(child.getName());
                    }
                }
            }
            return list;
        } catch (CmisObjectNotFoundException e) {
            throw new PathNotFoundException("Can't find cmis folder "+path,e);
        } catch (CantConnectCmis e) {
            return list;
        }
    }

    @Override
    public ExternalData getItemByIdentifier(String identifier) throws ItemNotFoundException {
        try {
            if (identifier.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getCmisSession().getObject(getCmisSession().createObjectId(removeContentSufix(identifier)));
                return getObjectContent((Document)object, null);
            } else {
                CmisObject object = getCmisSession().getObject(getCmisSession().createObjectId(identifier));
                return getObject(object,null);
            }
        } catch (Exception e) {
            throw new ItemNotFoundException("Can't find object by id "+identifier,e);
        }
    }

    @Override
    public ExternalData getItemByPath(String path) throws PathNotFoundException {
        try {
            if(path.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getCmisSession().getObjectByPath(removeContentSufix(path));
                return getObjectContent((Document)object, path);
            } else {
                CmisObject object = getCmisSession().getObjectByPath(path);
                return getObject(object,path);
            }
        } catch (PathNotFoundException e) {
            throw e;
        } catch (CantConnectCmis e) {  // Workaround if Repository is not accessible
            if ("/".equals(path))
                return createDummyMointPointData();
            throw new PathNotFoundException("Can't find object by path "+path,e);
        } catch (Exception e) {
            throw new PathNotFoundException("Can't find object by path "+path,e);
        }
    }

    private ExternalData getObjectContent(Document doc,String jcrContentPath) throws PathNotFoundException {
        if (jcrContentPath==null) {
            jcrContentPath = doc.getPaths().get(0)+JCR_CONTENT_SUFFIX;
        }
        Map<String,String[]> properties = new HashMap<String, String[]>(1);
        properties.put(Constants.JCR_MIMETYPE, new String[]{doc.getContentStreamMimeType()});
        ExternalData externalData = new ExternalData(doc.getId()+JCR_CONTENT_SUFFIX, jcrContentPath, Constants.NT_RESOURCE, properties);

        prepareJCR_DATA(doc, externalData);

        return externalData;
    }

    private void prepareJCR_DATA(Document doc, ExternalData externalData) {
        Map<String, Binary[]> binaryProperties = new HashMap<String, Binary[]>(1);
        binaryProperties.put(Constants.JCR_DATA, new Binary[]{new CmisBinaryImpl(doc)});
        externalData.setBinaryProperties(binaryProperties);
    }

    private ExternalData createDummyMointPointData() {
        CmisTypeMapping typeMapping = getConf().getDefaultFolderType();
        Map<String,String[]> properties = new HashMap<String, String[]>();
        String[] now = formatDate(new GregorianCalendar());
        properties.put(Constants.JCR_CREATED, now);
        properties.put(Constants.JCR_LASTMODIFIED, now);
        ExternalData externalData = new ExternalData("-1", "/", typeMapping.getJcrName(), properties);
        return externalData;
    }
    private ExternalData getObject(CmisObject object,String path) {
        CmisTypeMapping typeMapping = getTypeMapping(object);
        Map<String,String[]> properties = new HashMap<String, String[]>();
        if (object instanceof Document) {
            Document doc = (Document)object;
            if (path==null)
                path=doc.getPaths().get(0);
        } else if (object instanceof Folder) {
            Folder folder= (Folder)object;
            if (path==null)
                path = folder.getPath();
        }
        properties.put(Constants.JCR_CREATED, formatDate(object.getCreationDate()));
        properties.put(Constants.JCR_LASTMODIFIED, formatDate(object.getLastModificationDate()));
        mapProperties(properties, object, typeMapping, 'r');
        ExternalData externalData = new ExternalData(object.getId(), path, typeMapping.getJcrName(), properties);
        if (object instanceof Document) {
            Document doc = (Document)object;
            prepareJCR_DATA(doc, externalData);
        }
        return externalData;
    }


    private String[] formatDate(GregorianCalendar date) {
        return new String[]{ISO8601.format(date)};
    }

    /**
     * Look for correspondent mapping for CmisObject
     * @param object
     * @return mapping
     */
    private CmisTypeMapping getTypeMapping(CmisObject object) {
        ObjectType type = object.getType();
        BaseTypeId baseTypeId = type.getBaseTypeId();
        if (BaseTypeId.CMIS_DOCUMENT!=baseTypeId && BaseTypeId.CMIS_FOLDER!=baseTypeId) {
            throw new UnsupportedOperationException("Unsupported object type "+type.getLocalName());
        }
        while (type!=null) {
            CmisTypeMapping typeMapping = conf.getTypeByCMIS(type.getQueryName());
            if (typeMapping!=null)
                return typeMapping;
            else
                type=type.getParentType();
        }
        if (BaseTypeId.CMIS_DOCUMENT==baseTypeId) {
            return conf.getDefaultDocumentType();
        } else if (BaseTypeId.CMIS_FOLDER==baseTypeId) {
            return conf.getDefaultFolderType();
        } else {
            throw new UnsupportedOperationException("Unsoported object type "+type.getBaseType());
        }
    }
    private String getJahiaType(CmisObject object) {
        return getTypeMapping(object).getJcrName();
    }

    @Override
    public Set<String> getSupportedNodeTypes() {
        return conf.getSupportedNodeTypes();
    }

    @Override
    public boolean isSupportsHierarchicalIdentifiers() {
        return false;
    }

    @Override
    public boolean isSupportsUuid() {
        return false;
    }

    @Override
    public boolean itemExists(String path) {
        CmisObject objectByPath = null;
        try {
            objectByPath = getCmisSession().getObjectByPath(path);
            return true;
        } catch (CmisObjectNotFoundException e) {
            return false;
        } catch (CantConnectCmis cantConnectCmis) {
            return false;
        }
    }

    @Override
    public void start() {
//        try {
//            createSession();
//        } catch (CantConnectCmis e) {
//            log.error("Cant connect to CMIS repository",e);
//        }
    }

    private Session createSession() throws CantConnectCmis {
        try {
            SessionFactory factory = SessionFactoryImpl.newInstance();
            // create session
            return factory.createSession(getConf().getRepositoryPropertiesMap());
        } catch (CmisBaseException e) {
            CantConnectCmis cantConnectCmis = new CantConnectCmis(e);
            throw cantConnectCmis;
        }
    }

    @Override
    public void stop() {
        try {
            getCmisSession().clear();
        } catch (CantConnectCmis cantConnectCmis) {
        }
    }

    @Override
    public void move(String oldPath, String newPath) throws RepositoryException {
        CmisObject object = getCmisSession().getObjectByPath(oldPath);
        if ( !(object instanceof FileableCmisObject)) {
            throw new RepositoryException("Can't move " +oldPath + "to " +newPath);
        }
        try {
            FileableCmisObject file= (FileableCmisObject)object;
            String oldName=oldPath.substring(oldPath.lastIndexOf('/')+1);
            String oldFolder=oldPath.substring(0,oldPath.lastIndexOf('/'));
            if (oldFolder.length()==0)
                oldFolder="/";
            String newName=newPath.substring(newPath.lastIndexOf('/')+1);
            String newFolder=newPath.substring(0,newPath.lastIndexOf('/'));
            if (newFolder.length()==0)
                newFolder="/";
            if (!oldFolder.equals(newFolder)){
                file=file.move(getCmisSession().getObjectByPath(oldFolder),getCmisSession().getObjectByPath(newFolder));
            }
            if (!oldName.equals(newName)) {
                Map<String, String> properties = new HashMap<String, String>();
                properties.put(PropertyIds.NAME, newName);
                file.updateProperties(properties, true);
            }
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void order(String s, List<String> strings) throws RepositoryException {
        // Not supported
    }

    @Override
    public void removeItemByPath(String path) throws RepositoryException {
        try {
            CmisObject object = getCmisSession().getObjectByPath(path);
            object.delete();
        } catch (CmisObjectNotFoundException e) {
            throw new PathNotFoundException("Path not found "+path);
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void saveItem(ExternalData data) throws RepositoryException {
        String path = data.getPath();
        String jcrTypeName = data.getType();
        ExtendedNodeType nodeType = NodeTypeRegistry.getInstance().getNodeType(jcrTypeName);
        if (path.endsWith(JCR_CONTENT_SUFFIX)) {
            path=path.substring(0, path.lastIndexOf('/'));
            Document doc = (Document)getCmisSession().getObjectByPath(path);
            ContentStreamBinaryImpl contentStream = getContentStream(data,doc.getContentStreamMimeType());
            if (contentStream!=null) {
                doc.setContentStream(contentStream,true,true);
                contentStream.disposeBinary();
            }
        } else if (nodeType.isNodeType("jnt:folder")) {
            CmisTypeMapping cmisType = conf.getTypeByJCR(jcrTypeName);
            if (cmisType==null) {
                cmisType=conf.getDefaultFolderType();
            }
            String name = path.substring(path.lastIndexOf('/')+1);
            Map<String, Object> properties = new HashMap<String, Object>();
            try {
                CmisObject folder = getCmisSession().getObjectByPath(path);
                if (data.isNew())
                    throw new RepositoryException("Сan't create node '"+path+"' already exists.");
                mapProperties(properties, data, cmisType, 'w');
                if (!properties.isEmpty())
                    folder.updateProperties(properties,true);
            } catch (CmisObjectNotFoundException e) { // Not found - create
                if (!data.isNew())
                    throw new PathNotFoundException("Path not found "+path+" Can't update node.");
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.length()==0) {
                    path = "/";
                }
                Folder parentFolder = (Folder)getCmisSession().getObjectByPath(path);
                properties.put(PropertyIds.OBJECT_TYPE_ID, cmisType.getCmisName());
                properties.put(PropertyIds.NAME, name);
                mapProperties(properties, data, cmisType, 'c');
                Folder newFolder = parentFolder.createFolder(properties);
                data.setId(newFolder.getId());
            }
        } else if (nodeType.isNodeType("jnt:file")) {
            CmisTypeMapping cmisType = conf.getTypeByJCR(jcrTypeName);
            if (cmisType==null) {
                cmisType=conf.getDefaultDocumentType();
            }
            String name = path.substring(path.lastIndexOf('/')+1);
            Map<String, Object> properties = new HashMap<String, Object>();
            Document doc;
            try {
                doc = (Document)getCmisSession().getObjectByPath(path);
                if (data.isNew())
                    throw new RepositoryException("Сan't create node '"+path+"' already exists.");
                mapProperties(properties, data, cmisType, 'w');
                if (!properties.isEmpty())
                    doc.updateProperties(properties, true);
            } catch (CmisObjectNotFoundException e) { // Not found - create
                if (!data.isNew())
                    throw new PathNotFoundException("Path not found "+path+" Can't update node.");
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.length()==0) {
                    path = "/";
                }
                Folder parentFolder = (Folder)getCmisSession().getObjectByPath(path);
                properties.put(PropertyIds.OBJECT_TYPE_ID, cmisType.getCmisName());
                properties.put(PropertyIds.NAME, name);
                mapProperties(properties, data, cmisType, 'c');
                String mimeType=JCRContentUtils.getMimeType(name,DEFAULT_MIMETYPE);
                if (properties.containsKey(PropertyIds.CONTENT_STREAM_MIME_TYPE)) {
                    mimeType=properties.get(PropertyIds.CONTENT_STREAM_MIME_TYPE).toString();
                }
                InputStream stream = new ByteArrayInputStream(new byte[0]);
//                ContentStream contentStream = getContentStream(data,mimeType);
//                if (contentStream==null) {
                ContentStream contentStream = new ContentStreamImpl(name, BigInteger.valueOf(0),
                                                                    mimeType, stream);
//                }
                Document newDocument = parentFolder.createDocument(properties, contentStream, null);
                data.setId(newDocument.getId());
            }

        } else {
            throw new RepositoryException("CMIS provider does not support "+jcrTypeName);
        }
    }

    private void mapProperties(Map<String, Object> dest, ExternalData src,CmisTypeMapping type, char mode) {
        Map<String, String[]> properties = src.getProperties();
        for (Map.Entry<String, String[]> property : properties.entrySet()) {
            CmisPropertyMapping propertyMapping = type.getPropertyByJCR(property.getKey());
            if (propertyMapping.inMode(mode)) {
                dest.put(propertyMapping.getCmisName(),property.getValue()[0]);
            }
        }
    }
    private void mapProperties(Map<String, String[]> dest, CmisObject src, CmisTypeMapping type, char mode) {
        List<Property<?>> properties = src.getProperties();
        properties:for (Property<?> property : properties) {
            CmisPropertyMapping propertyMapping = type.getPropertyByCMIS(property.getLocalName());
            if (propertyMapping!=null && propertyMapping.inMode(mode)) {
                String[] val;
                List<?> values = property.getValues();
                if (values.isEmpty())
                    continue ;
                val = new String[values.size()];
                for (int i = 0; i<val.length; i++) {
                    Object srcValue = values.get(i);
                    if (srcValue==null) {
                        continue properties;
                    }
                    if (srcValue instanceof Calendar) {
                        val[i] = ISO8601.format((Calendar)srcValue);
                    } else {
                        val[i] = srcValue.toString();
                    }
                }
                dest.put(propertyMapping.getJcrName(),val);
            }
        }
    }

    private ContentStreamBinaryImpl getContentStream(ExternalData data,String mimeType) throws RepositoryException {
        if (data.getBinaryProperties()==null)
            return null;
        String path=data.getPath();
        if (path.endsWith(JCR_CONTENT_SUFFIX))
            path=path.substring(0, path.lastIndexOf('/'));
        String name=path.substring(path.lastIndexOf('/')+1);
        final Binary[] binaries = data.getBinaryProperties().get(Constants.JCR_DATA);
        if (binaries!=null && binaries.length > 0) {
            Binary binary = binaries[0];
            if (mimeType==null)
                mimeType=JCRContentUtils.getMimeType(name,DEFAULT_MIMETYPE);
            return new ContentStreamBinaryImpl(binary, name, mimeType);
        }
        return null;
    }

    @Override
    public List<String> search(ExternalQuery query) throws RepositoryException {
        try {
            Session session = getCmisSession();
            QueryResolver resolver = new QueryResolver(this, query);
            String sql =  resolver.resolve();
            boolean isFolder=false;
            if (BaseTypeId.CMIS_FOLDER.equals(session.getTypeDefinition(resolver.cmisType.getCmisName()).getBaseTypeId())) {
                isFolder=true;
                sql=sql.replace("cmis:objectId","cmis:path");
            }
            // Not mapped or unsupported queries treated as empty.
            if (sql==null) {
                return Collections.emptyList();
            }
            if (log.isDebugEnabled()) {
                log.debug("CMIS query "+sql);
            }
            OperationContext operationContext = session.createOperationContext();
            operationContext.setIncludePathSegments(true);
            ItemIterable<QueryResult> results = session.query(sql, false, operationContext);
            if (query.getLimit()>0 && query.getLimit()<Integer.MAX_VALUE)
                results=results.getPage((int)query.getLimit());
            if (query.getOffset()!=0)
                results=results.skipTo(query.getOffset());
            ArrayList<String> res = new ArrayList<String>();
            for (QueryResult hit : results) {
                String path;
                if (isFolder) {
                    path = hit.getPropertyValueByQueryName("id").toString();
                } else {
                    String id = hit.getPropertyValueByQueryName("id").toString();
                    CmisObject object = session.getObject(id);
                    path = ((FileableCmisObject)object).getPaths().get(0);
                }
                res.add(path);
            }
            return res;
        } catch (RepositoryException e) {
            log.warn("Can't execute query to cmis ", e);
            return Collections.emptyList();
        }
    }



    private String removeContentSufix(String identifier) {
        return identifier.substring(0,identifier.length()-JCR_CONTENT_SUFFIX.length());
    }


    public CmisConfiguration getConf() {
        return conf;
    }

    public void setConf(CmisConfiguration conf) {
        this.conf = conf;
    }

    public synchronized Session getCmisSession() throws CantConnectCmis {
        if (cmisSession==null)
        try {
            cmisSession = createSession();
            firstConnectFailure=true;
        } catch (CantConnectCmis ex) {
            if (firstConnectFailure) {
                log.error("Can't establish cmis connection", ex);
                firstConnectFailure = false;
            }
            throw ex;
        }
        return cmisSession;
    }
}
