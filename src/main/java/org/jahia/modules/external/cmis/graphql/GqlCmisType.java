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
