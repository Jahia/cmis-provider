/*
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *                                  http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2023 Jahia Solutions Group. All rights reserved.
 *
 *     This file is part of a Jahia's Enterprise Distribution.
 *
 *     Jahia's Enterprise Distributions must be used in accordance with the terms
 *     contained in the Jahia Solutions Group Terms &amp; Conditions as well as
 *     the Jahia Sustainable Enterprise License (JSEL).
 *
 *     For questions regarding licensing, support, production usage...
 *     please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
 */
package org.jahia.modules.external.cmis.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import org.jahia.modules.external.cmis.CmisProviderFactory;
import org.jahia.modules.external.cmis.admin.CMISMountPointFactory;

@GraphQLDescription("CMIS mount point type to use on creation")
public enum GqlCmisType {

    CMIS(CMISMountPointFactory.TYPE_CMIS),
    ALFRESCO(CmisProviderFactory.TYPE_ALFRESCO),
    NUXEO(CmisProviderFactory.TYPE_NUXEO);

    private final String type;

    private GqlCmisType(String type) {
        this.type = type;
    }

    public String getValue() {
        return type;
    }
}
