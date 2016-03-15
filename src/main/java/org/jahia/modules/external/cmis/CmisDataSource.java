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
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.Action;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisServiceUnavailableException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUnauthorizedException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.value.BinaryImpl;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalContentStoreProvider;
import org.jahia.modules.external.ExternalData;
import org.jahia.modules.external.ExternalDataSource;
import org.jahia.modules.external.ExternalQuery;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRMountPointNode;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.*;
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

    private static final String CONF_MAX_CHILD_NODES = "org.jahia.cmis.max.child.nodes";

    private static final String DEFAULT_MIMETYPE = "binary/octet-stream";
    private static final List<String> JCR_CONTENT_LIST = Collections.singletonList(Constants.JCR_CONTENT);
    private static final String JCR_CONTENT_SUFFIX = "/" + Constants.JCR_CONTENT;

    private boolean firstConnectFailure = true;
    protected Cache<String, Session> activeConnections;
    private boolean recordingConnectionsStats;
    private ExternalContentStoreProvider provider;

    private int maxChildNodes = 0;

    private String remotePath;

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

    /**
     * Resolve the username from the Aliased / External Session / JCR Session
     * @return the username
     */
    protected static String resolveUser() {
        JahiaUser aliasedUser = JCRSessionFactory.getInstance().getCurrentAliasedUser();
        String sesssionUSer = ExternalContentStoreProvider.getCurrentSession().getUserID();
        return aliasedUser != null ? aliasedUser.getName() : sesssionUSer;
    }


    @Override
    public List<String> getChildren(final String path) throws RepositoryException {
        final List<String> list = new ArrayList<>();
        try {
            if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getObjectByPath(path);
                if (object instanceof Document) {
                    return JCR_CONTENT_LIST;
                } else if (object instanceof Folder) {
                    final Folder folder = (Folder) object;
                    executeWithCMISSession(new ExecuteCallback<Object>() {
                        @Override
                        public Object execute(Session session) {
                            ItemIterable<CmisObject> children = folder.getChildren();
                            int i = 0;
                            for (CmisObject child : children) {
                                list.add(child.getName());
                                if (maxChildNodes > 0 && ++i > maxChildNodes) {
                                    log.warn(String.format("getChildren returns too many children - path : %s , number of children %s, max children %s", path, children.getTotalNumItems(), maxChildNodes));
                                    break;
                                }
                            }
                            return null;
                        }
                    });

                }
            }
            return list;
        } catch (CmisObjectNotFoundException e) {
            throw new PathNotFoundException("Can't find cmis folder " + path, e);
        } catch (CantConnectCmis e) {
            return list;
        }
    }

    protected CmisObject getObjectByPath(final String path) throws RepositoryException {
        return executeWithCMISSession(new ExecuteCallback<CmisObject>() {
            @Override
            public CmisObject execute(Session session) throws RepositoryException {
                return session.getObjectByPath(addRemotePath(path));
            }
        });
    }

    protected CmisObject getObjectById(final String user, final String id) throws RepositoryException {
        return executeWithCMISSession(user, new ExecuteCallback<CmisObject>() {
            @Override
            public CmisObject execute(Session session) throws RepositoryException {
                return session.getObject(id);
            }
        });

    }

    @Override
    public List<ExternalData> getChildrenNodes(final String path) throws RepositoryException {
        return executeWithCMISSession(new ExecuteCallback<List<ExternalData>>() {
            @Override
            public List<ExternalData> execute(Session session) throws RepositoryException {
                ArrayList<ExternalData> list = new ArrayList<>();
                try {
                    if (!path.endsWith(JCR_CONTENT_SUFFIX)) {
                        CmisObject object = session.getObjectByPath(addRemotePath(path));
                        if (object instanceof Document) {
                            return Collections.singletonList(getObjectContent((Document) object, path + JCR_CONTENT_SUFFIX));
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
                                    list.add(getObject(child, (!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + child.getName()));
                                    if (child.getBaseTypeId() == BaseTypeId.CMIS_DOCUMENT) {
                                        list.add(getObjectContent((Document) child, (!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + child.getName() + JCR_CONTENT_SUFFIX));
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
    public ExternalData getItemByIdentifier(final String identifier) throws ItemNotFoundException {
        try {
            return executeWithCMISSession(new ExecuteCallback<ExternalData>() {
                @Override
                public ExternalData execute(Session session) throws RepositoryException {
                    if (identifier.endsWith(JCR_CONTENT_SUFFIX)) {
                        CmisObject object = session.getObject(session.createObjectId(removeContentSufix(identifier)));
                        return getObjectContent((Document) object, null);
                    } else {
                        CmisObject object = session.getObject(session.createObjectId(identifier));
                        return getObject(object, null);
                    }
                }
            });

        } catch (Exception e) {
            throw new ItemNotFoundException("Can't find object by id " + identifier, e);
        }
    }

    @Override
    public ExternalData getItemByPath(String path) throws PathNotFoundException {
        try {
            if (path.endsWith(JCR_CONTENT_SUFFIX)) {
                CmisObject object = getObjectByPath(removeContentSufix(path));
                return getObjectContent((Document) object, path);
            } else {
                CmisObject object = getObjectByPath(path);
                return getObject(object, path);
            }
        } catch (Exception e) {
            throw new PathNotFoundException("Can't find object by path " + path, e);
        }
    }

    private ExternalData getObjectContent(Document doc, String jcrContentPath) throws PathNotFoundException {
        if (jcrContentPath == null) {
            if (doc.getPaths().isEmpty() || doc.getContentStreamLength() < 0) {
                throw new PathNotFoundException("No path found for CMIS document: " + doc.getId());
            } else {
                jcrContentPath = doc.getPaths().get(0) + JCR_CONTENT_SUFFIX;
            }
        }
        Map<String, String[]> properties = new HashMap<>(1);
        properties.put(Constants.JCR_MIMETYPE, new String[]{doc.getContentStreamMimeType()});
        ExternalData externalData = new ExternalData(stripVersionFromId(doc.getId()) + JCR_CONTENT_SUFFIX, removeRemotePath(jcrContentPath), Constants.NT_RESOURCE, properties);

        Map<String, Binary[]> binaryProperties = new HashMap<>(1);
        if (doc.getContentStreamLength() > 0) {
            CmisBinaryImpl cmisBinary = new CmisBinaryImpl(doc, jcrContentPath, this, resolveUser());
            binaryProperties.put(Constants.JCR_DATA, new Binary[]{cmisBinary});
        } else {
            BinaryImpl binary = new BinaryImpl("unable to get binary content".getBytes());
            binaryProperties.put(Constants.JCR_DATA, new Binary[]{binary});
        }
        externalData.setBinaryProperties(binaryProperties);
        return externalData;
    }

    private String stripVersionFromId(String id) {
        return id.contains(";") ? StringUtils.substringBeforeLast(id, ";") : id;
    }

    private ExternalData getObject(CmisObject object, String path) throws PathNotFoundException {
        CmisTypeMapping typeMapping = getTypeMapping(object);
        Map<String, String[]> properties = new HashMap<>();
        String additionalMixin = null;
        if (object instanceof Document) {
            Document doc = ((Document) object);
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
        ExternalData externalData = new ExternalData(stripVersionFromId(object.getId()), removeRemotePath(path), typeMapping.getJcrName(), properties);
        Set<String> mixins = new HashSet<>(typeMapping.getJcrMixins());
        if (additionalMixin != null) {
            mixins.add(additionalMixin);
        }
        externalData.setMixin(new ArrayList<String>(mixins));
        return externalData;
    }

    private String removeRemotePath(String path) {
        return StringUtils.startsWith(path, remotePath) ? (StringUtils.equals(path, remotePath) ? "/" : StringUtils.substringAfter(path, remotePath)) : path;
    }

    private String addRemotePath(String path) {
        return remotePath + path;
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
            getObjectByPath(path);
            return true;
        } catch (CmisObjectNotFoundException | RepositoryException e) {
            return false;
        }
    }

    @Override
    public void start() {
        // cache config
        HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();

        int concurrencyLevel = parseInt(repositoryPropertiesMap, CONF_SESSION_CACHE_CONCURRENCY_LEVEL);
        int size = parseInt(repositoryPropertiesMap, CONF_SESSION_CACHE_MAXIMUM_SIZE);
        int duration = parseInt(repositoryPropertiesMap, CONF_SESSION_CACHE_EXPIRE_AFTER_ACCESS);
        maxChildNodes = parseInt(repositoryPropertiesMap, CONF_MAX_CHILD_NODES);
        cacheBuilder = CacheBuilder.newBuilder().removalListener(removalListener)
                .concurrencyLevel(concurrencyLevel)
                .maximumSize(size)
                .expireAfterAccess(duration, TimeUnit.MINUTES);
        buildActiveConnections();
    }

    private int parseInt(HashMap<String, String> repositoryPropertiesMap, String propertyName) {
        int value;
        try {
            value = Integer.parseInt(repositoryPropertiesMap.get(propertyName));
        } catch (NumberFormatException e) {
            throw new NumberFormatException(String.format("Parsing property %s failed. Unable to parse \"%s\" as int", propertyName, repositoryPropertiesMap.get(propertyName)));
        }
        return value;
    }

    @Override
    public void stop() {
        activeConnections.invalidateAll();
    }

    @Override
    public void move(String oldPath, String newPath) throws RepositoryException {
        CmisObject object = getObjectByPath(oldPath);
        cleanUpCache(object, getCmisSession(resolveUser()));
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

                file = file.move(getObjectByPath(oldFolder), getObjectByPath(newFolder));

                if (!sameName) {
                    // perform the renaming now
                    file.rename(newName);
                }
            }

        } catch (CmisUnauthorizedException e) {
            throw new AccessDeniedException(e);
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public void order(String s, List<String> strings) throws RepositoryException {
        // Not supported
    }

    @Override
    public void removeItemByPath(final String path) throws RepositoryException {
        try {
            CmisObject object = getObjectByPath(path);
            if (object instanceof Folder) {
                cleanUpCache(object, getCmisSession(resolveUser()));
                List<String> failedToDelete = ((Folder) object).deleteTree(true, UnfileObject.DELETE, true);
                boolean hasError = false;
                if (failedToDelete.size() > 0) {
                    // Alfresco 4.2 returns the list of the child nodes as failed nodes
                    // even if they are really removed
                    for (String id : failedToDelete) {
                        try {
                            CmisObject doc = getObjectById(resolveUser(), id);
                            // doc still available, send error
                            hasError = true;
                            break;
                        } catch (CmisObjectNotFoundException e) {
                            // this is the excpected behavior
                        } catch (Exception e1) {
                            hasError = true;
                            break;
                        }
                    }
                    if (hasError) {
                        throw new RepositoryException(String.format("unable to delete folder %s", StringUtils.substringAfterLast(path, "/")));
                    }
                }
            } else {
                object.delete(true);
            }
        } catch (CmisUnauthorizedException e) {
            throw new AccessDeniedException(e);
        } catch (CmisObjectNotFoundException e) {
            throw new PathNotFoundException("Path not found " + path);
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    private void cleanUpCache(CmisObject object, Session session) {
        // remove cache key
        session.removeObjectFromCache(object);
        // cleanup all documents from the cache
        if (object instanceof Document) {
            for (CmisObject version : ((Document) object).getAllVersions()) {
                session.removeObjectFromCache(version);
            }
        } else if (object instanceof Folder) {
            for (CmisObject o : ((Folder) object).getChildren()) {
                cleanUpCache(o, session);
            }
        }
    }

    @Override
    public void saveItem(ExternalData data) throws RepositoryException {
        String path = data.getPath();
        String jcrTypeName = data.getType();
        ExtendedNodeType nodeType = NodeTypeRegistry.getInstance().getNodeType(jcrTypeName);
        if (path.endsWith(JCR_CONTENT_SUFFIX)) {
            path = path.substring(0, path.lastIndexOf('/'));
            Document doc = (Document) getObjectByPath(path);
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
                CmisObject folder = getObjectByPath(path);
                if (data.isNew()) {
                    throw new RepositoryException("Сan't create node '" + path + "' already exists.");
                }
                mapProperties(properties, data, cmisType, 'w');
                if (!properties.isEmpty()) {
                    folder.updateProperties(properties, true);
                }
            } catch (CmisUnauthorizedException e) {
                throw new AccessDeniedException(e);
            } catch (CmisObjectNotFoundException e) { // Not found - create
                try {
                    if (!data.isNew()) {
                        throw new PathNotFoundException("Path not found " + path + " Can't update node.");
                    }
                    path = path.substring(0, path.lastIndexOf('/'));
                    if (path.length() == 0) {
                        path = "/";
                    }
                    Folder parentFolder = (Folder) getObjectByPath(path);
                    properties.put(PropertyIds.OBJECT_TYPE_ID, cmisType.getCmisName());
                    properties.put(PropertyIds.NAME, name);
                    mapProperties(properties, data, cmisType, 'c');
                    Folder newFolder = parentFolder.createFolder(properties);
                    // change externalData id since it's generate by a parent call and can be inconsistent with CMIS provider ids, we can override it
                    data.setId(stripVersionFromId(newFolder.getId()));
                } catch (CmisUnauthorizedException e1) {
                    throw new AccessDeniedException(e1);
                }
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
                doc = ((Document) getObjectByPath(path)).getObjectOfLatestVersion(false);
                if (data.isNew()) {
                    throw new RepositoryException("Сan't create node '" + path + "' already exists.");
                }
                mapProperties(properties, data, cmisType, 'w');
                if (!properties.isEmpty()) {
                    doc.updateProperties(properties);
                }
            } catch (CmisUnauthorizedException e) {
                throw new AccessDeniedException(e);
            } catch (CmisObjectNotFoundException e) { // Not found - create
                try {
                    if (!data.isNew()) {
                        throw new PathNotFoundException("Path not found " + path + " Can't update node.");
                    }
                    path = path.substring(0, path.lastIndexOf('/'));
                    if (path.length() == 0) {
                        path = "/";
                    }
                    Folder parentFolder = (Folder) getObjectByPath(path);
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
                } catch (CmisUnauthorizedException e1) {
                    throw new RepositoryException(e1);
                }
            }
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
        String path = addRemotePath(data.getPath());
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
    public List<String> search(final ExternalQuery query) throws RepositoryException {
        return executeWithCMISSession(new ExecuteCallback<List<String>>() {
            @Override
            public List<String> execute(Session session) {
                try {
                    QueryResolver resolver = new QueryResolver(CmisDataSource.this, query);
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
                            path = removeRemotePath(hit.getPropertyValueByQueryName("id").toString());
                        } else {
                            String id = hit.getPropertyValueByQueryName("id").toString();
                            CmisObject object = session.getObject(id);
                            path = removeRemotePath(((FileableCmisObject) object).getPaths().get(0));
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
        });
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

    /**
     * create or get from the connection pool a CMIS Session, a CMIS Session is created by user
     *
     * @return Session
     * @throws CantConnectCmis
     * @param user
     */
    public synchronized Session getCmisSession(String user) throws CantConnectCmis {
        try {
            // get or create session
            final HashMap<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
            Session cmisSession = activeConnections.get(repositoryPropertiesMap.get(SessionParameter.USER), new Callable<Session>() {
                @Override
                public Session call() throws ExecutionException {
                    SessionFactory factory = SessionFactoryImpl.newInstance();
                    return factory.createSession(repositoryPropertiesMap);
                }
            });
            firstConnectFailure = true;
            return cmisSession;
        } catch (CmisBaseException | ExecutionException | UncheckedExecutionException e) {
            if (firstConnectFailure) {
                log.error("Can't establish cmis connection", e);
                firstConnectFailure = false;
            }
            throw new CantConnectCmis(e);
        }
    }

    public <X> X executeWithCMISSession(ExecuteCallback<X> callback) throws RepositoryException {
        return executeWithCMISSession(resolveUser(), callback);
    }

    /**
     * Execute the callback again with a new session if it fails due to authorization issue
     * todo : improve to prevent this call when a user really cannot have access to the resource
     *
     * @param user user to use in the session
     * @param callback contains the code to execute
     * @param <X>      is the return Object type of the callback
     * @return
     * @throws RepositoryException
     */
    public <X> X executeWithCMISSession(String user, ExecuteCallback<X> callback) throws RepositoryException {
        Session cmisSession = getCmisSession(user);
        if (maxChildNodes > 0) {
            cmisSession.getDefaultContext().setMaxItemsPerPage(maxChildNodes);
            cmisSession.getDefaultContext().setOrderBy("cmis:name");
        }
        try {
            return callback.execute(cmisSession);
        } catch (CmisUnauthorizedException e) {
            // flush caches
            invalidateCurrentConnection();
            return callback.execute(cmisSession);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException ||
                    cause instanceof BindException ||
                    cause instanceof NoRouteToHostException ||
                    cause instanceof SocketTimeoutException ||
                    cause instanceof UnknownHostException ||
                    cause instanceof CmisServiceUnavailableException) {
                provider.setMountStatus(JCRMountPointNode.MountStatus.waiting, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Invalidate the current user connection
     */
    protected void invalidateCurrentConnection() throws RepositoryException {
        getActiveConnections().invalidateAll();
    }

    @Override
    public boolean isAvailable() throws RepositoryException {
        return executeWithCMISSession(new ExecuteCallback<Boolean>() {
            @Override
            public Boolean execute(Session session) throws RepositoryException {
                try {
                    OperationContext operationContext = session.createOperationContext();
                    operationContext.setCacheEnabled(false);
                    session.getRootFolder(operationContext);
                } catch (Exception e) {
                    return false;
                }
                return true;
            }
        });
    }

    @Override
    public String[] getPrivilegesNames(String username, String path) {
        Set<String> privileges = new HashSet<>();

        try {
            AllowableActions allowable = getObjectByPath(path).getAllowableActions();
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
                        privileges.add(JCR_WRITE + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_WRITE + "_" + LIVE_WORKSPACE);
                        privileges.add(JCR_ADD_CHILD_NODES + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_ADD_CHILD_NODES + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_DELETE_TREE:
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_DELETE_OBJECT:
                        privileges.add(JCR_REMOVE_NODE + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_REMOVE_NODE + "_" + LIVE_WORKSPACE);
                        break;
                }
            }
        } catch (RepositoryException cantConnectCmis) {
            log.error(cantConnectCmis.getMessage(), cantConnectCmis);
            throw new RuntimeException(cantConnectCmis);
        }
        return privileges.toArray(new String[privileges.size()]);
    }

    public Cache<String, Session> getActiveConnections() {
        return activeConnections;
    }

    /**
     * enable / disable the recording of statistics on the connections pool
     * swithching state reset the pool
     *
     * @param recordingConnectionsStats
     */
    public void setRecordingConnectionsStats(boolean recordingConnectionsStats) {
        if ((recordingConnectionsStats && isRecordingConnectionsStats()) || (!recordingConnectionsStats && !isRecordingConnectionsStats())) {
            return;
        }
        activeConnections.invalidateAll();
        this.recordingConnectionsStats = recordingConnectionsStats;
        buildActiveConnections();
    }

    /**
     * get the statistics recording status
     *
     * @return
     */
    public boolean isRecordingConnectionsStats() {
        return recordingConnectionsStats;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public void setProvider(ExternalContentStoreProvider provider) {
        this.provider = provider;
    }
}
