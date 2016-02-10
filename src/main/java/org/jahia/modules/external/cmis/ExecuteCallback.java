package org.jahia.modules.external.cmis;

import org.apache.chemistry.opencmis.client.api.Session;

import javax.jcr.RepositoryException;

/**
 * Callback to perform execution within a CMIS Session
 */
public interface ExecuteCallback<T> {

    /**
     * Content that will be executed with a CMIS session
     *
     * @return
     */
    T execute(Session session) throws RepositoryException;

}
