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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.client.util.FileUtils;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.Action;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.commons.lang.StringUtils;
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

import javax.jcr.Binary;
import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static javax.jcr.security.Privilege.*;
import static org.jahia.api.Constants.EDIT_WORKSPACE;
import static org.jahia.api.Constants.LIVE_WORKSPACE;

/**
 * ExternalDataSource implementation for CMIS full support for write and search Created by: Boris Date: 1/20/14 Time: 7:35 PM
 */
public class CmisDataSource implements ExternalDataSource, ExternalDataSource.Initializable, ExternalDataSource.Writable,
        ExternalDataSource.Searchable, ExternalDataSource.CanLoadChildrenInBatch, ExternalDataSource.CanCheckAvailability,
        ExternalDataSource.AccessControllable {

    private static final String CONF_SESSION_CACHE_CONCURRENCY_LEVEL = "org.jahia.cmis.session.cache.concurrencyLevel";
    private static final String CONF_SESSION_CACHE_MAXIMUM_SIZE = "org.jahia.cmis.session.cache.maximumSize";
    private static final String CONF_SESSION_CACHE_EXPIRE_AFTER_ACCESS = "org.jahia.cmis.session.cache.expireAfterAccess";

    private static final String DEFAULT_MIMETYPE = "binary/octet-stream";
    private static final List<String> JCR_CONTENT_LIST = Collections.singletonList(Constants.JCR_CONTENT);
    private static final String JCR_CONTENT_SUFFIX = "/" + Constants.JCR_CONTENT;

    private boolean firstConnectFailure = true;
    protected Cache<String, Session> activeConnections;
    private boolean recordingConnectionsStats;

    private final RemovalListener<String, Session> removalListener = new RemovalListener<String, Session>() {
        @Override
        public void onRemoval(RemovalNotification<String, Session> removal) {
            if (removal.getValue() != null) {
                removal.getValue().clear();
            }
        }
    };

    private CacheBuilder<String, Session> cacheBuilder;

    /*
    * The logger instance for this class
     */
    private static final Logger log = LoggerFactory.getLogger(CmisDataSource.class);

    /**
     * Configuration
     */
    CmisConfiguration conf;

    @Override
    public List<String> getChildren(String path) throws RepositoryException {
        List<String> list = new ArrayList<>();
        try {
            if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getCmisSession().getObjectByPath(path);
                if (object instanceof Document) {
                    return JCR_CONTENT_LIST;
                } else if (object instanceof Folder) {
                    Folder folder = (Folder) object;
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
            throw new PathNotFoundException("Can't find cmis folder " + path, e);
        } catch (CantConnectCmis e) {
            return list;
        }
    }

    @Override
    public List<ExternalData> getChildrenNodes(String path) throws RepositoryException {
        List<ExternalData> list = Collections.emptyList();
        try {
            if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getCmisSession().getObjectByPath(path);
                if (object instanceof Document) {
                    return Collections.singletonList(getObjectContent((Document) object, path + JCR_CONTENT_SUFFIX));
                } else if (object instanceof Folder) {
                    Folder folder = (Folder) object;
                    OperationContext operationContext = getCmisSession().createOperationContext();
                    operationContext.setMaxItemsPerPage(Integer.MAX_VALUE);

                    ItemIterable<CmisObject> children = folder.getChildren(operationContext);
                    list = new ArrayList<>((int) children.getTotalNumItems());
                    for (CmisObject child : children) {
                        list.add(getObject(child, (!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + child.getName()));
                        if (child instanceof Document) {
                            list.add(getObjectContent((Document) child, (!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + child.getName() + JCR_CONTENT_SUFFIX));
                        }
                    }
                }
            }
            return list;
        } catch (CmisObjectNotFoundException e) {
            throw new PathNotFoundException("Can't find cmis folder " + path, e);
        } catch (CantConnectCmis e) {
            return list;
        }
    }

    @Override
    public ExternalData getItemByIdentifier(String identifier) throws ItemNotFoundException {
        try {
            if (identifier.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getCmisSession().getObject(getCmisSession().createObjectId(removeContentSufix(identifier)));
                return getObjectContent((Document) object, null);
            } else {
                CmisObject object = getCmisSession().getObject(getCmisSession().createObjectId(identifier));
                return getObject(object, null);
            }
        } catch (Exception e) {
            throw new ItemNotFoundException("Can't find object by id " + identifier, e);
        }
    }

    @Override
    public ExternalData getItemByPath(String path) throws PathNotFoundException {
        try {
            if (path.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getCmisSession().getObjectByPath(removeContentSufix(path));
                return getObjectContent((Document) object, path);
            } else {
                CmisObject object = getCmisSession().getObjectByPath(path);
                return getObject(object, path);
            }
        } catch (PathNotFoundException e) {
            throw e;
        } catch (CantConnectCmis e) {  // Workaround if Repository is not accessible
            if ("/".equals(path)) {
                return createDummyMointPointData();
            }
            throw new PathNotFoundException("Can't find object by path " + path, e);
        } catch (Exception e) {
            throw new PathNotFoundException("Can't find object by path " + path, e);
        }
    }

    private ExternalData getObjectContent(Document doc, String jcrContentPath) throws PathNotFoundException {
        doc = doc.getObjectOfLatestVersion(false);
        if (jcrContentPath == null) {
            if (doc.getPaths().isEmpty()) {
                throw new PathNotFoundException("No path found for CMIS document: " + doc.getId());
            } else {
                jcrContentPath = doc.getPaths().get(0) + JCR_CONTENT_SUFFIX;
            }
        }
        Map<String, String[]> properties = new HashMap<>(1);
        properties.put(Constants.JCR_MIMETYPE, new String[]{doc.getContentStreamMimeType()});
        ExternalData externalData = new ExternalData(stripVersionFromId(doc.getId()) + JCR_CONTENT_SUFFIX, jcrContentPath, Constants.NT_RESOURCE, properties);

        Map<String, Binary[]> binaryProperties = new HashMap<>(1);
        binaryProperties.put(Constants.JCR_DATA, new Binary[]{new CmisBinaryImpl(doc)});
        externalData.setBinaryProperties(binaryProperties);

        return externalData;
    }

    private String stripVersionFromId(String id) {
        return id.contains(";") ? StringUtils.substringBeforeLast(id, ";") : id;
    }

    private ExternalData createDummyMointPointData() {
        CmisTypeMapping typeMapping = getConf().getDefaultFolderType();
        Map<String, String[]> properties = new HashMap<>();
        String[] now = formatDate(new GregorianCalendar());
        properties.put(Constants.JCR_CREATED, now);
        properties.put(Constants.JCR_LASTMODIFIED, now);
        return new ExternalData("-1", "/", typeMapping.getJcrName(), properties);
    }

    private ExternalData getObject(CmisObject object, String path) throws PathNotFoundException {
        CmisTypeMapping typeMapping = getTypeMapping(object);
        Map<String, String[]> properties = new HashMap<>();
        String additionalMixin = null;
        if (object instanceof Document) {
            Document doc = ((Document) object).getObjectOfLatestVersion(false);
            object = doc;

            // set image mixin if mymetype match
            if (doc.getContentStreamMimeType() != null && doc.getContentStreamMimeType().matches("image/(.*)")) {
                additionalMixin = Constants.JAHIAMIX_IMAGE;
            }

            if (path == null) {
                if (doc.getPaths().isEmpty()) {
                    throw new PathNotFoundException("No path found for CMIS document: " + doc.getId());
                } else {
                    path = doc.getPaths().get(0);
                }
            }
        } else if (object instanceof Folder) {
            Folder folder = (Folder) object;
            if (path == null) {
                path = folder.getPath();
            }
        }
        properties.put(Constants.JCR_CREATED, formatDate(object.getCreationDate()));
        properties.put(Constants.JCR_LASTMODIFIED, formatDate(object.getLastModificationDate()));
        mapProperties(properties, object, typeMapping, 'r');
        ExternalData externalData = new ExternalData(stripVersionFromId(object.getId()), path, typeMapping.getJcrName(), properties);
        Set<String> mixins = new HashSet<>(typeMapping.getJcrMixins());
        if (additionalMixin != null) {
            mixins.add(additionalMixin);
        }
        externalData.setMixin(new ArrayList<String>(mixins));
        return externalData;
    }

    private String[] formatDate(GregorianCalendar date) {
        return new String[]{ISO8601.format(date)};
    }

    /**
     * Look for correspondent mapping for CmisObject
     *
     * @param object the CmisObject we want to retrieve a mapping for
     * @return mapping
     */
    private CmisTypeMapping getTypeMapping(CmisObject object) {
        ObjectType type = object.getType();
        BaseTypeId baseTypeId = type.getBaseTypeId();
        if (BaseTypeId.CMIS_DOCUMENT != baseTypeId && BaseTypeId.CMIS_FOLDER != baseTypeId) {
            throw new UnsupportedOperationException("Unsupported object type " + type.getLocalName());
        }
        while (type != null) {
            CmisTypeMapping typeMapping = conf.getTypeByCMIS(type.getQueryName());
            if (typeMapping != null) {
                return typeMapping;
            } else {
                type = type.getParentType();
            }
        }
        if (BaseTypeId.CMIS_DOCUMENT == baseTypeId) {
            return conf.getDefaultDocumentType();
        } else if (BaseTypeId.CMIS_FOLDER == baseTypeId) {
            return conf.getDefaultFolderType();
        } else {
            throw new UnsupportedOperationException("Unsupported object type " + type.getBaseType());
        }
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
        try {
            getCmisSession().getObjectByPath(path);
            return true;
        } catch (CmisObjectNotFoundException e) {
            return false;
        } catch (CantConnectCmis cantConnectCmis) {
            return false;
        }
    }

    @Override
    public void start() {
        // cache config
        HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
        cacheBuilder = CacheBuilder.newBuilder().removalListener(removalListener)
                .concurrencyLevel(Integer.parseInt(repositoryPropertiesMap.get(CONF_SESSION_CACHE_CONCURRENCY_LEVEL)))
                .maximumSize(Integer.parseInt(repositoryPropertiesMap.get(CONF_SESSION_CACHE_MAXIMUM_SIZE)))
                .expireAfterAccess(Integer.parseInt(repositoryPropertiesMap.get(CONF_SESSION_CACHE_EXPIRE_AFTER_ACCESS)), TimeUnit.MINUTES);
        buildActiveConnections();
    }

    @Override
    public void stop() {
        activeConnections.invalidateAll();
    }

    @Override
    public void move(String oldPath, String newPath) throws RepositoryException {
        CmisObject object = getCmisSession().getObjectByPath(oldPath);
        if (!(object instanceof FileableCmisObject)) {
            throw new RepositoryException("Can't move " + oldPath + "to " + newPath);
        }
        try {
            FileableCmisObject file;
            if (object instanceof Document) {
                // here we get the latest version of the object to avoid version mismatch
                file = ((Document) object).getObjectOfLatestVersion(false);
            } else {
                file = (FileableCmisObject) object;
            }

            String oldName = oldPath.substring(oldPath.lastIndexOf('/') + 1);
            String oldFolder = oldPath.substring(0, oldPath.lastIndexOf('/'));
            if (oldFolder.length() == 0) {
                oldFolder = "/";
            }
            String newName = newPath.substring(newPath.lastIndexOf('/') + 1);
            String newFolder = newPath.substring(0, newPath.lastIndexOf('/'));
            if (newFolder.length() == 0) {
                newFolder = "/";
            }

            boolean sameName = oldName.equals(newName);
            // in case of a node renaming, the folder is the same, no need to move the node then, just rename it
            if (oldFolder.equals(newFolder)) {
                // should be always the case, same name move is not possible when cut/paste in same folder, but test anyway to be sure
                if (!sameName) {
                    file.rename(newName);
                }
            } else {
                if (!sameName) {
                    // generate temporary name to avoid name conflict with other sibling files.
                    file = (FileableCmisObject) file.rename(UUID.randomUUID().toString());
                }

                file = file.move(getCmisSession().getObjectByPath(oldFolder), getCmisSession().getObjectByPath(newFolder));

                if (!sameName) {
                    // perform the renaming now
                    file.rename(newName);
                }
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
            FileUtils.delete(path, getCmisSession());
        } catch (CmisObjectNotFoundException e) {
            throw new PathNotFoundException("Path not found " + path);
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
            path = path.substring(0, path.lastIndexOf('/'));
            Document doc = (Document) getCmisSession().getObjectByPath(path);
            ContentStreamBinaryImpl contentStream = getContentStream(data, doc.getContentStreamMimeType());
            if (contentStream != null) {
                doc.setContentStream(contentStream, true, true);
                contentStream.disposeBinary();
            }
        } else if (nodeType.isNodeType("jnt:folder")) {
            CmisTypeMapping cmisType = conf.getTypeByJCR(jcrTypeName);
            if (cmisType == null) {
                cmisType = conf.getDefaultFolderType();
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            Map<String, Object> properties = new HashMap<>();
            try {
                CmisObject folder = getCmisSession().getObjectByPath(path);
                if (data.isNew()) {
                    throw new RepositoryException("Сan't create node '" + path + "' already exists.");
                }
                mapProperties(properties, data, cmisType, 'w');
                if (!properties.isEmpty()) {
                    folder.updateProperties(properties, true);
                }
            } catch (CmisObjectNotFoundException e) { // Not found - create
                if (!data.isNew()) {
                    throw new PathNotFoundException("Path not found " + path + " Can't update node.");
                }
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.length() == 0) {
                    path = "/";
                }
                Folder parentFolder = (Folder) getCmisSession().getObjectByPath(path);
                properties.put(PropertyIds.OBJECT_TYPE_ID, cmisType.getCmisName());
                properties.put(PropertyIds.NAME, name);
                mapProperties(properties, data, cmisType, 'c');
                Folder newFolder = parentFolder.createFolder(properties);
                // change externalData id since it's generate by a parent call and can be inconsistent with CMIS provider ids, we can override it
                data.setId(stripVersionFromId(newFolder.getId()));
            }
        } else if (nodeType.isNodeType("jnt:file")) {
            CmisTypeMapping cmisType = conf.getTypeByJCR(jcrTypeName);
            if (cmisType == null) {
                cmisType = conf.getDefaultDocumentType();
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            Map<String, Object> properties = new HashMap<>();
            Document doc;
            try {
                doc = ((Document) getCmisSession().getObjectByPath(path)).getObjectOfLatestVersion(false);
                if (data.isNew()) {
                    throw new RepositoryException("Сan't create node '" + path + "' already exists.");
                }
                mapProperties(properties, data, cmisType, 'w');
                if (!properties.isEmpty()) {
                    doc.updateProperties(properties);
                }
            } catch (CmisObjectNotFoundException e) { // Not found - create
                if (!data.isNew()) {
                    throw new PathNotFoundException("Path not found " + path + " Can't update node.");
                }
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.length() == 0) {
                    path = "/";
                }
                Folder parentFolder = (Folder) getCmisSession().getObjectByPath(path);
                properties.put(PropertyIds.OBJECT_TYPE_ID, cmisType.getCmisName());
                properties.put(PropertyIds.NAME, name);
                mapProperties(properties, data, cmisType, 'c');
                String mimeType = JCRContentUtils.getMimeType(name, DEFAULT_MIMETYPE);
                if (properties.containsKey(PropertyIds.CONTENT_STREAM_MIME_TYPE)) {
                    mimeType = properties.get(PropertyIds.CONTENT_STREAM_MIME_TYPE).toString();
                }
                InputStream stream = new ByteArrayInputStream(new byte[0]);
                ContentStream contentStream = new ContentStreamImpl(name, BigInteger.valueOf(0),
                        mimeType, stream);
                Document newDocument = parentFolder.createDocument(properties, contentStream, null);
                // change externalData id since it's generate by a parent call and can be inconsistent with CMIS provider ids, we can override it
                data.setId(stripVersionFromId(newDocument.getId()));
            }
        } else if (nodeType.isNodeType("nt:resource")) {
            //ignore
        }
    }

    private void mapProperties(Map<String, Object> dest, ExternalData src, CmisTypeMapping type, char mode) {
        Map<String, String[]> properties = src.getProperties();
        for (Map.Entry<String, String[]> property : properties.entrySet()) {
            CmisPropertyMapping propertyMapping = type.getPropertyByJCR(property.getKey());
            if (propertyMapping != null && propertyMapping.inMode(mode)) {
                dest.put(propertyMapping.getCmisName(), property.getValue()[0]);
            }
        }
    }

    private void mapProperties(Map<String, String[]> dest, CmisObject src, CmisTypeMapping type, char mode) {
        List<Property<?>> properties = src.getProperties();
        properties:
        for (Property<?> property : properties) {
            CmisPropertyMapping propertyMapping = type.getPropertyByCMIS(property.getQueryName());
            if (propertyMapping != null && propertyMapping.inMode(mode)) {
                String[] val;
                List<?> values = property.getValues();
                if (values.isEmpty()) {
                    continue;
                }
                val = new String[values.size()];
                for (int i = 0; i < val.length; i++) {
                    Object srcValue = values.get(i);
                    if (srcValue == null) {
                        continue properties;
                    }
                    if (srcValue instanceof Calendar) {
                        val[i] = ISO8601.format((Calendar) srcValue);
                    } else {
                        val[i] = srcValue.toString();
                    }
                }
                dest.put(propertyMapping.getJcrName(), val);
            }
        }
    }

    private ContentStreamBinaryImpl getContentStream(ExternalData data, String mimeType) throws RepositoryException {
        if (data.getBinaryProperties() == null) {
            return null;
        }
        String path = data.getPath();
        if (path.endsWith(JCR_CONTENT_SUFFIX)) {
            path = path.substring(0, path.lastIndexOf('/'));
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        final Binary[] binaries = data.getBinaryProperties().get(Constants.JCR_DATA);
        if (binaries != null && binaries.length > 0) {
            Binary binary = binaries[0];
            if (mimeType == null) {
                mimeType = JCRContentUtils.getMimeType(name, DEFAULT_MIMETYPE);
            }
            return new ContentStreamBinaryImpl(binary, name, mimeType);
        }
        return null;
    }

    private void buildActiveConnections() {
        activeConnections = recordingConnectionsStats ? cacheBuilder.recordStats().build() : cacheBuilder.build();
    }


    @Override
    public List<String> search(ExternalQuery query) throws RepositoryException {
        try {
            Session session = getCmisSession();
            QueryResolver resolver = new QueryResolver(this, query);
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
            ItemIterable<QueryResult> results = session.query(sql, false, operationContext);
            if (query.getLimit() > 0 && query.getLimit() < Integer.MAX_VALUE) {
                results = results.getPage((int) query.getLimit());
            }
            if (query.getOffset() != 0) {
                results = results.skipTo(query.getOffset());
            }
            ArrayList<String> res = new ArrayList<>();
            for (QueryResult hit : results) {
                String path;
                if (isFolder) {
                    path = hit.getPropertyValueByQueryName("id").toString();
                } else {
                    String id = hit.getPropertyValueByQueryName("id").toString();
                    CmisObject object = session.getObject(id);
                    path = ((FileableCmisObject) object).getPaths().get(0);
                }
                res.add(path);
            }
            return res;
        } catch (RepositoryException | CmisObjectNotFoundException e) {
            // CmisObjectNotFoundException in case of the cmis server doesn't support query
            log.warn("Can't execute query to cmis ", e);
            return Collections.emptyList();
        }
    }

    private String removeContentSufix(String identifier) {
        return identifier.substring(0, identifier.length() - JCR_CONTENT_SUFFIX.length());
    }

    public CmisConfiguration getConf() {
        return conf;
    }

    public void setConf(CmisConfiguration conf) {
        this.conf = conf;
    }

    public synchronized Session getCmisSession() throws CantConnectCmis {
        try {
            // get or create session
            final HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
            Session cmisSession = activeConnections.get(repositoryPropertiesMap.get(SessionParameter.USER), new Callable<Session>() {
                @Override
                public Session call() throws Exception {
                    SessionFactory factory = SessionFactoryImpl.newInstance();
                    return factory.createSession(repositoryPropertiesMap);
                }
            });
            firstConnectFailure = true;
            return cmisSession;
        } catch (CmisBaseException | ExecutionException e) {
            if (firstConnectFailure) {
                log.error("Can't establish cmis connection", e);
                firstConnectFailure = false;
            }
            throw new CantConnectCmis(e);
        }
    }

    @Override
    public boolean isAvailable() throws RepositoryException {
        try {
            getCmisSession();
        } catch (CantConnectCmis e) {
            return false;
        }
        return true;
    }

    @Override
    public String[] getPrivilegesNames(String username, String path) {
        Set<String> privileges = new HashSet<>();

        try {
            AllowableActions allowable = getCmisSession().getObjectByPath(path).getAllowableActions();
            for (Action action : allowable.getAllowableActions()) {
                switch (action) {
                    case CAN_GET_FOLDER_TREE:
                    case CAN_GET_PROPERTIES:
                        privileges.add(JCR_READ + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_READ + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_UPDATE_PROPERTIES:
                    case CAN_SET_CONTENT_STREAM:
                    case CAN_CREATE_FOLDER:
                    case CAN_CREATE_DOCUMENT:
                    case CAN_ADD_OBJECT_TO_FOLDER:
                        privileges.add(JCR_WRITE + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_WRITE + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_DELETE_TREE:
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + LIVE_WORKSPACE);
                        break;
                }
            }
        } catch (CantConnectCmis cantConnectCmis) {
            log.error(cantConnectCmis.getMessage(), cantConnectCmis);
            throw new RuntimeException(cantConnectCmis);
        }
        return privileges.toArray(new String[privileges.size()]);
    }

    public Cache<String, Session> getActiveConnections() {
        return activeConnections;
    }

    public void setRecordingConnectionsStats(boolean recordingConnectionsStats) {
        if ((recordingConnectionsStats && isRecordingConnectionsStats()) || (!recordingConnectionsStats && !isRecordingConnectionsStats())) {
            return;
        }
        activeConnections.cleanUp();
        this.recordingConnectionsStats = recordingConnectionsStats;
        buildActiveConnections();
    }

    public boolean isRecordingConnectionsStats() {
        return recordingConnectionsStats;
    }
}
