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


import java.text.ParseException;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalQuery;
import javax.jcr.RepositoryException;
import javax.jcr.query.qom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by rizak on 17/06/16.
 * This class is based on the CMIS QueryResolver but implements Nuxeo Query building specificities
 */
public class NuxeoQueryResolver extends QueryResolver{
    private static final Logger LOGGER = LoggerFactory.getLogger(NuxeoQueryResolver.class);

    public NuxeoQueryResolver(CmisDataSource dataSource, ExternalQuery query) {
        super(dataSource, query);
    }

    @Override
    protected String getNodeTypeName(String name){
        // Supports queries on hierarchyNode as file queries
        if ("nt:hierarchyNode".equals(name) || "jmix:searchable".equals(name) || "jnt:file".equals(name) || "nt:file".equals(name)
                || "jmix:image".equals(name)) {
            return "nuxeo:file";
        }
        return name;
    }


    /**
     * Use contains only for jcr:content fulltextSearch, if the property is different we use like operator
     * @param c
     * @return
     * @throws RepositoryException
     */
    @Override
    protected StringBuffer getFullTextSearchConstraint(FullTextSearch c) throws RepositoryException{
        StringBuffer buff = new StringBuffer();
        //If fulltext search is done on jcr:content then executing fulltext search
        if (c.getPropertyName().equals(Constants.JCR_CONTENT)) {
            buff.append(" contains(");
            addOperand(buff, c.getFullTextSearchExpression());
            buff.append(") ");
        }
        //If fulltext search is done on another property then checking the property mapping
        //TODO : Avoid to use the like operator as often as possible and prefer one contains on multiple expressions
        else {
            CmisPropertyMapping propertyByJCR = cmisType.getPropertyByJCR(c.getPropertyName());
            //If the property is mapped then use like operator to avoid "contains" repetition
            if (propertyByJCR != null) {
                String searchTerm; 
                try {
                    searchTerm = discardEscapeChar(c.getFullTextSearchExpression().toString());
                } catch (ParseException ex) {
                    LOGGER.warn("Impossible to escape the full text search expression", ex);
                    searchTerm = escapeString(c.getFullTextSearchExpression().toString());
                }
                searchTerm = searchTerm.substring(1, searchTerm.length() - 1);
                buff.append(propertyByJCR.getCmisName()).append("  like '%").append(searchTerm).append("%' ");
            } else {
                //If the property is not mapped we don't do anything
                return FALSE;
            }
        }
        return buff;
    }
}