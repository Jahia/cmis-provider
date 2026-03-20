package org.jahia.modules.external.cmis;

import javax.jcr.RepositoryException;

/**
 * CantConnectCmis exception used when datasource can't get access to repository.
 * Created by: Boris
 * Date: 4/23/14
 * Time: 10:20 PM
 */
public class CantConnectCmis extends RepositoryException {
    private static final long serialVersionUID = -5743988716006574710L;

    public CantConnectCmis(Throwable rootCause) {
        super(rootCause);
    }
}
