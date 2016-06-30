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

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.ExternalQuery;
import javax.jcr.RepositoryException;
import javax.jcr.query.qom.*;

/**
 * Created by rizak on 17/06/16.
 * This class is based on the CMIS QueryResolver but implements Nuxeo Query building specificities
 */
public class NuxeoQueryResolver extends QueryResolver{
    public NuxeoQueryResolver(CmisDataSource dataSource, ExternalQuery query) {
        this.dataSource = dataSource;
        this.query = query;
        conf = dataSource.conf;
    }

    public String resolve() throws RepositoryException {
        StringBuffer buff = new StringBuffer("SELECT cmis:objectId as id FROM ");

        Source source = query.getSource();
        if (source instanceof Join) {
            log.debug("Join not supported in CMIS queries");
            return null;
        }
        Selector selector = (Selector) source;
        String nodeTypeName = selector.getNodeTypeName();

        // Supports queries on hierarchyNode as file queries
        if (nodeTypeName.equals("nt:hierarchyNode") || nodeTypeName.equals("jmix:searchable")) {
            nodeTypeName = "cmis:file";
        }
        cmisType = conf.getTypeByJCR(nodeTypeName);
        if (cmisType == null) {
            log.debug("Unmapped types not supported in CMIS queries");
            return null;
        }
        //TODO : Check the repository on mount point for the "capabilityJoin" attribute before implementing the join between files and folders (Adding an option with user choice in mount point creation form ?)
        //TODO : When the JOIN will be implemented the contraints operand will need to be doubled to verify properties in files and in folders
        //Join Files and folders in the search
        //buff.append("(");
        buff.append(cmisType.getQueryName());
        //buff.append(" AS file JOIN ");
        //cmisType = conf.getTypeByJCR("jnt:folder");
        //buff.append(cmisType.getQueryName()+" AS folder ON file.nuxeo:parentId = folder.cmis:objectId )");

//        if (selector.getSelectorName()!=null && !selector.getSelectorName().isEmpty())
//            buff.append(" as ").append(selector.getSelectorName());
        boolean hasConstraint = false;
        if (query.getConstraint() != null) {
            StringBuffer buffer = addConstraint(query.getConstraint());
            if (buffer == FALSE) {
                return null;
            } else if (buffer != TRUE) {
                buff.append(" WHERE ");
                buff.append(buffer);
                hasConstraint = true;
            }
        }
        if (StringUtils.isNotBlank(dataSource.getRemotePath())) {
            if (hasConstraint) {
                buff.append(" AND");
            } else {
                buff.append(" WHERE ");
            }
            buff.append(" IN_TREE('");
            buff.append(dataSource.getObjectByPath("/").getId());
            buff.append("')");
        }

        if (query.getOrderings() != null) {
            boolean isFirst = true;
            StringBuffer tmpBuf = new StringBuffer();
            for (Ordering ordering : query.getOrderings()) {
                tmpBuf.setLength(0);
                try {
                    addOperand(tmpBuf, ordering.getOperand());
                    //In Nuxeo the score can only be used when using fulltext search
                    if (tmpBuf.toString().equals(" myscore ") && (!buff.toString().contains("contains("))) {
                        return buff.toString();
                    }
                    if (isFirst) {
                        buff.append(" ORDER BY ");
                        isFirst = false;
                    } else {
                        buff.append(",");
                    }
                    buff.append(tmpBuf);
                    String order = ordering.getOrder();
                    if (QueryObjectModelConstants.JCR_ORDER_ASCENDING.equals(order)) {
                        buff.append(' ').append("ASC");
                    } else if (QueryObjectModelConstants.JCR_ORDER_DESCENDING.equals(order)) {
                        buff.append(' ').append("DESC");
                    }
                    if (tmpBuf.toString().equals(" myscore ")) {
                        buff.insert(buff.indexOf(" FROM"), ", SCORE() as myscore ");
                    }
                } catch (NotMappedCmisProperty ignore) { //ignore ordering by not mapped properties
                }
            }
        }
        return buff.toString();
    }
}