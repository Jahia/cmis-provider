/**
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *                                  http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2018 Jahia Solutions Group. All rights reserved.
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
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisServiceUnavailableException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUnauthorizedException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.chemistry.opencmis.commons.impl.json.JSONObject;
import org.apache.chemistry.opencmis.commons.impl.json.parser.JSONParseException;
import org.apache.chemistry.opencmis.commons.impl.json.parser.JSONParser;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.util.Text;
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
        ExternalDataSource.SupportPrivileges {
   /*
    * The logger instance for this class
    */
    private static final Logger log = LoggerFactory.getLogger(CmisDataSource.class);
    
    private static final String CONF_SESSION_CACHE_CONCURRENCY_LEVEL = "org.jahia.cmis.session.cache.concurrencyLevel";
    private static final String CONF_SESSION_CACHE_MAXIMUM_SIZE = "org.jahia.cmis.session.cache.maximumSize";
    private static final String CONF_SESSION_CACHE_EXPIRE_AFTER_ACCESS = "org.jahia.cmis.session.cache.expireAfterAccess";

    private static final String CONF_MAX_CHILD_NODES = "org.jahia.cmis.max.child.nodes";

    private static final String DEFAULT_MIMETYPE = "binary/octet-stream";
    private static final List<String> JCR_CONTENT_LIST = Collections.singletonList(Constants.JCR_CONTENT);
    protected static final String JCR_CONTENT_SUFFIX = "/" + Constants.JCR_CONTENT;

    private boolean firstConnectFailure = true;
    protected Cache<String, Session> activeConnections;
    private boolean recordingConnectionsStats;
    private ExternalContentStoreProvider provider;

    protected enum Operation { ENCODE, DECODE, URLENCODE }

    protected int maxChildNodes = 0;

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
                // the path is encoded by DX using JCRContentUtils.escapeLocalNodeName() that
                // do not encode "+" that has to be encoded
                return session.getObjectByPath(addRemotePath(transformPath(transformPath(path, Operation.DECODE), Operation.URLENCODE)));
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
                        // the path is encoded by DX using JCRContentUtils.escapeLocalNodeName() that
                        // do not encode "+" that has to be encoded
                        CmisObject object = session.getObjectByPath(addRemotePath(transformPath(transformPath(path, Operation.DECODE), Operation.URLENCODE)));
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
                                    String childPath = transformPath(removeRemotePath(!folder.getPath().equals("/") ? folder.getPath() + "/" : "/") + child.getName(), Operation.ENCODE);
                                    list.add(getObject(child, childPath));
                                    if (child.getBaseTypeId() == BaseTypeId.CMIS_DOCUMENT) {
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

    protected ExternalData getObjectContent(Document doc, String jcrContentPath) throws PathNotFoundException {
        if (jcrContentPath == null) {
            if (doc.getPaths().isEmpty() || doc.getContentStreamLength() < 0) {
                throw new PathNotFoundException("No path found for CMIS document: " + doc.getId());
            } else {
                jcrContentPath = transformPath(removeRemotePath(doc.getPaths().get(0) + JCR_CONTENT_SUFFIX), Operation.ENCODE);
            }
        }
        Map<String, String[]> properties = new HashMap<>(1);
        properties.put(Constants.JCR_MIMETYPE, new String[]{doc.getContentStreamMimeType()});
        ExternalData externalData = new ExternalData(stripVersionFromId(doc.getId()) + JCR_CONTENT_SUFFIX, jcrContentPath, Constants.NT_RESOURCE, properties);

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

    protected ExternalData getObject(CmisObject object, String path) throws PathNotFoundException {
        CmisTypeMapping typeMapping = getTypeMapping(object);
        Map<String, String[]> properties = new HashMap<>();
        List <String> additionalMixins = new ArrayList<>();
        if (object instanceof Document) {
            Document doc = ((Document) object);
            object = doc;
            additionalMixins = getMixinsToAdd(doc);

            if (path == null) {
                if (doc.getPaths().isEmpty()) {
                    throw new PathNotFoundException("No path found for CMIS document: " + doc.getId());
                } else {
                    path = transformPath(removeRemotePath(doc.getPaths().get(0)), Operation.ENCODE);
                }
            }
        } else if (object instanceof Folder) {
            Folder folder = (Folder) object;
            if (path == null) {
                path = transformPath(removeRemotePath(folder.getPath()), Operation.ENCODE);
            }
        }
        final GregorianCalendar createdDate = object.getCreationDate();
        properties.put(Constants.JCR_CREATED, formatDate(createdDate == null ? new GregorianCalendar() : createdDate));
        final GregorianCalendar lastModificationDate = object.getLastModificationDate();
        properties.put(Constants.JCR_LASTMODIFIED, formatDate(lastModificationDate == null ? new GregorianCalendar() : lastModificationDate));
        properties.put(Constants.LASTPUBLISHED, formatDate(lastModificationDate == null ? new GregorianCalendar() : lastModificationDate));
        mapProperties(properties, object, typeMapping, 'r');
        ExternalData externalData = new ExternalData(stripVersionFromId(object.getId()), path, typeMapping.getJcrName(), properties);
        Set<String> mixins = new HashSet<>(typeMapping.getJcrMixins());
        if (CollectionUtils.isNotEmpty(additionalMixins)) {
            mixins.addAll(additionalMixins);
        }
        externalData.setMixin(new ArrayList<String>(mixins));
        return externalData;
    }

    protected List<String> getMixinsToAdd(Document doc){
        List<String> mixins = new ArrayList<>();
        // set image mixin if mymetype match
        if (doc.getContentStreamMimeType() != null && doc.getContentStreamMimeType().matches("image/(.*)")) {
            mixins.add(Constants.JAHIAMIX_IMAGE);
        }
        return mixins;
    }

    protected String removeRemotePath(String path) {
        return StringUtils.startsWith(path, remotePath) ? (StringUtils.equals(path, remotePath) ? "/" : StringUtils.substringAfter(path, remotePath)) : path;
    }

    protected String addRemotePath(String path) {
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
            throw new UnsupportedOperationException("Unsupported object type " + object.getType().getBaseType());
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
        Map<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();

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

    private int parseInt(Map<String, String> repositoryPropertiesMap, String propertyName) {
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
        if (!(object instanceof FileableCmisObject)) {
            throw new RepositoryException("Can't move " + oldPath + "to " + newPath);
        }
        if (StringUtils.equals(newPath, oldPath)) {
            return;
        }
        try {
            FileableCmisObject file;
            if (object instanceof Document) {
                // here we get the latest version of the object to avoid version mismatch
                file = ((Document) object).getObjectOfLatestVersion(false);
            } else {
                file = (FileableCmisObject) object;
            }

            String oldFolder = StringUtils.substringBeforeLast(oldPath, "/");
            if (oldFolder.length() == 0) {
                oldFolder = "/";
            }
            String newFolder = StringUtils.substringBeforeLast(newPath, "/");
            if (newFolder.length() == 0) {
                newFolder = "/";
            }
            if (newFolder.equals(oldFolder)) {
                // disable refresh here because using open CMIS server lead to CmisObjectNotFoundException
                // because open cmis ids are based on the path of the file on the file system.
                // changing the name, also change the id of the object
                // disabling this refresh does'nt cause any issue for us, because we refresh the list of item after an edition, move, or rename
                // so the file will be rename and correctly display after the action
                file.rename(transformPath(StringUtils.substringAfterLast(newPath, "/"), Operation.DECODE), false);
            } else {
                file.move(getObjectByPath(oldFolder), getObjectByPath(newFolder));
            }
            // clean caches
            cleanUpCache(object, getCmisSession(resolveUser()));
            // clean path
            // the cache maintains 2 maps, one by id, the other by path
            // we only have access to the cached map by id that is handled by cleanUpCache()
            // to remove the item from the cached map by path, we have to try to get the item
            // this is an issue in move because the reference still exists and both path point on the same object
            try {
                getItemByPath(oldPath);
            } catch (Exception e) {
                // do nothing
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
                            getObjectById(resolveUser(), id);
                            // doc still available, send error
                            log.warn("Document is still available, although it should have been deleted: {}", id);
                            hasError = true;
                            break;
                        } catch (CmisObjectNotFoundException e) {
                            // this is the excpected behavior
                        } catch (Exception e1) {
                            log.warn("Unexpected error while checking failed deletion on document: " + id, e1);
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
        try {
            if (object instanceof Document) {
                for (CmisObject version : ((Document) object).getAllVersions()) {
                    session.removeObjectFromCache(version);
                }
            } else if (object instanceof Folder) {
                for (CmisObject o : ((Folder) object).getChildren()) {
                    cleanUpCache(o, session);
                }
            }
        } catch (CmisObjectNotFoundException e) {
            // object don't exist anymore
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
            // cleanup cache
            cleanUpCache(doc, getCmisSession(resolveUser()));
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
            String name = transformPath(path.substring(path.lastIndexOf('/') + 1), Operation.DECODE);
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
            String name = transformPath(path.substring(path.lastIndexOf('/') + 1), Operation.DECODE);
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
                        String remotePath = hit.getPropertyValueByQueryName("id").toString();
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

    protected String removeContentSufix(String identifier) {
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
            final Map<String, String> repositoryPropertiesMap = getConf().getRepositoryPropertiesMap();
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
        try {
            Session cmisSession = getCmisSession(user);
            setSessionProperties(cmisSession);
            return callback.execute(cmisSession);
        } catch (CmisUnauthorizedException e) {
            // flush caches
            invalidateCurrentConnection();
            return callback.execute(getCmisSession(user));
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (e instanceof CantConnectCmis ||
                    cause instanceof ConnectException ||
                    cause instanceof BindException ||
                    cause instanceof NoRouteToHostException ||
                    cause instanceof SocketTimeoutException ||
                    cause instanceof UnknownHostException ||
                    cause instanceof CmisServiceUnavailableException) {
                provider.setMountStatus(JCRMountPointNode.MountStatus.waiting, e.getMessage());
            }

            if (e instanceof CmisBaseException) {
                JSONParser jp = new JSONParser();
                String errorContent = ((CmisBaseException) e).getErrorContent();
                try {
                    JSONObject json = (JSONObject) jp.parse(errorContent);
                    errorContent = json.get("stacktrace").toString();
                } catch (JSONParseException e1) {
                    // parsing fail .. return all object
                }
                if (e instanceof CmisObjectNotFoundException) {
                    log.debug("an error occurs on remote server:\n {}", errorContent);
                } else {
                    log.error("an error occurs on remote server:\n {}", errorContent);
                }
            }

            throw e;
        }
    }

    protected void setSessionProperties(Session cmisSession){
        if (maxChildNodes > 0) {
            cmisSession.getDefaultContext().setMaxItemsPerPage(maxChildNodes);
            cmisSession.getDefaultContext().setOrderBy("cmis:name");
        }
    }

    /**
     * Invalidate the current user connection
     */
    protected void invalidateCurrentConnection() throws RepositoryException {
        getActiveConnections().invalidate(resolveUser());
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
            AllowableActions allowable = getObjectByPath(path.endsWith(JCR_CONTENT_SUFFIX) ? removeContentSufix(path) : path)
                    .getAllowableActions();
            for (Action action : allowable.getAllowableActions()) {
                switch (action) {
                    case CAN_GET_FOLDER_TREE:
                    case CAN_GET_PROPERTIES:
                    case CAN_GET_DESCENDANTS:
                    case CAN_GET_CHILDREN:
                        privileges.add(JCR_READ + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_READ + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_UPDATE_PROPERTIES:
                        privileges.add(JCR_MODIFY_PROPERTIES + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_MODIFY_PROPERTIES + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_SET_CONTENT_STREAM:
                    case CAN_CREATE_FOLDER:
                    case CAN_CREATE_DOCUMENT:
                    case CAN_ADD_OBJECT_TO_FOLDER:
                    case CAN_CREATE_ITEM:
                        privileges.add(JCR_ADD_CHILD_NODES + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_ADD_CHILD_NODES + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_MOVE_OBJECT:
                        privileges.add(JCR_WRITE + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_WRITE + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_DELETE_TREE:
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_DELETE_OBJECT:
                        privileges.add(JCR_REMOVE_NODE + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_REMOVE_NODE + "_" + LIVE_WORKSPACE);
                        break;
                    case CAN_REMOVE_OBJECT_FROM_FOLDER:
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + EDIT_WORKSPACE);
                        privileges.add(JCR_REMOVE_CHILD_NODES + "_" + LIVE_WORKSPACE);
                        break;
                }
            }
        } catch (RepositoryException cantConnectCmis) {
            log.error(cantConnectCmis.getMessage(), cantConnectCmis);
            throw new RuntimeException(cantConnectCmis);
        } catch (CmisObjectNotFoundException cmisObjectNotFound) {
            log.warn("Cannot get privileges for "+path, cmisObjectNotFound);
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

    protected String transformPath(String path, Operation operation) throws PathNotFoundException {
        if (StringUtils.equals(path, "/")) {
            return path;
        }
        String sep = StringUtils.contains(path, "/") ? "/" : "";
        StringBuilder sb = new StringBuilder();
        if (operation != Operation.URLENCODE) {
            for (String p : StringUtils.split(path, "/")) {
                sb.append(sep);
                if (operation == Operation.DECODE) {
                    // as + is decoded to space, we have first to escape it
                    sb.append(p != null && p.indexOf('%') != -1 ? Text.unescapeIllegalJcrChars(p) : p);
                } else {
                    // replace encoded space to "+" by %20
                    for (int i = 0; i < p.length(); i++) {
                        char ch = p.charAt(i);
                        if (ch == '[' || ch == ']' || ch == '*' || ch == '|' || ch == '%') {
                            sb.append('%');
                            sb.append(Character.toUpperCase(Character.forDigit(ch / 16, 16)));
                            sb.append(Character.toUpperCase(Character.forDigit(ch % 16, 16)));
                        } else {
                            sb.append(ch);
                        }
                    }
                }
            }
        } else {
            // Browser binding needs to encode the path as it is part of the url path
            // in the other case (atompub), the path is part of the queryString and is encoded by the CMIS implementation
            if (BindingType.BROWSER.value().equals(getConf().getRepositoryPropertiesMap().get(SessionParameter.BINDING_TYPE))) {
                try {
                    return (new URI(null,null,path,null)).toString().replace("+","%2B");
                } catch (URISyntaxException e) {
                    throw new PathNotFoundException(e);
                }
            } else {
                return path;
            }
        }
        return sb.toString();

    }

}
