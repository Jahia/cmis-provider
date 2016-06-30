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
package org.jahia.modules.external.cmis.services;

import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRFileNode;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;

/**
 * Created by rizak on 13/06/16.
 * This decorator is handle the display of Nuxeo CMIS documents.
 * In pickers the system name is used for the files and we don't
 * want the system name but the cmis document name to be displayed there.
 * This decorator redefine the getDisplayableName() function in order to behave
 * as for nodes instead of files and then uses this function to return the system name
 * This way cmis document titles will be displayed on the site instead of nuxeo specific name
 */
public class NuxeoFileNode extends JCRFileNode {
    protected static final Logger logger = org.slf4j.LoggerFactory.getLogger(NuxeoFileNode.class);

    public NuxeoFileNode(JCRNodeWrapper node) throws RepositoryException {
        super(node);
    }

    @Override
    public String getName() {
        return getDisplayableName();
    }

    @Override
    public String getDisplayableName() {
        try {
            if(isNodeType(Constants.NT_FOLDER)) {
                return super.getDisplayableName();
            }
        } catch (RepositoryException e) {
            logger.error(e.getMessage(), e);
        }
        try {
            return getProperty(Constants.JCR_TITLE).getValue().getString();
        } catch (RepositoryException e) {
            logger.debug("could not retrieve jcr:title of " + this.getPath());
        }
        return super.getName();
    }
}
