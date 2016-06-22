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
 *     Copyright (C) 2002-2016 Jahia Solutions Group. All rights reserved.
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

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.query.qom.Operator;
import org.jahia.modules.external.ExternalQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.query.qom.*;

/**
 * Helper class used for convert JCR ExternalQuery to CMIS Query
 * Created by: Boris
 * Date: 2/4/14
 * Time: 6:49 PM
 */
public class QueryResolver {
    /*
    * The logger instance for this class
    */
    private static final Logger log = LoggerFactory.getLogger(CmisDataSource.class);

    private final StringBuffer TRUE = new StringBuffer("true");
    private final StringBuffer FALSE = new StringBuffer("false");
    private final StringBuffer EMPTY = new StringBuffer("");

    CmisDataSource dataSource;
    ExternalQuery query;
    CmisConfiguration conf;
    CmisTypeMapping cmisType;

    public QueryResolver(CmisDataSource dataSource, ExternalQuery query) {
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
        if (nodeTypeName.equals("nt:hierarchyNode")) {
            nodeTypeName = "jnt:file";
        }
        cmisType = conf.getTypeByJCR(nodeTypeName);
        if (cmisType == null) {
            log.debug("Unmapped types not supported in CMIS queries");
            return null;
        }
        buff.append(cmisType.getQueryName());
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
            buff.append( " IN_TREE('");
            buff.append( dataSource.getObjectByPath("/").getId() );
            buff.append("')");
        }

        if (query.getOrderings() != null) {
            boolean isFirst = true;
            StringBuffer tmpBuf = new StringBuffer();
            for (Ordering ordering : query.getOrderings()) {
                tmpBuf.setLength(0);
                try {
                    addOperand(tmpBuf, ordering.getOperand());
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


    private StringBuffer addConstraint(Constraint constraint) throws RepositoryException {
        StringBuffer buff = new StringBuffer();
        if (constraint instanceof Or) {
            Or c = (Or) constraint;
            StringBuffer constraint1 = addConstraint(c.getConstraint1());
            StringBuffer constraint2 = addConstraint(c.getConstraint2());
            if (constraint1 == TRUE || constraint2 == TRUE) {
                return TRUE;
            }
            if (constraint1 == FALSE && constraint2 == FALSE) {
                return EMPTY;
            }
            if (constraint1 == EMPTY && constraint2 == EMPTY) {
                return EMPTY;
            }
            if (constraint1 == FALSE || constraint1 == EMPTY) {
                return constraint2;
            }
            if (constraint2 == FALSE || constraint2 == EMPTY) {
                return constraint1;
            }
            buff.append(" (");
            buff.append(constraint1);
            buff.append(" OR ");
            buff.append(constraint2);
            buff.append(") ");
        } else if (constraint instanceof And) {
            And c = (And) constraint;
            StringBuffer constraint1 = addConstraint(c.getConstraint1());
            StringBuffer constraint2 = addConstraint(c.getConstraint2());
            if (constraint1 == FALSE || constraint2 == FALSE) {
                return FALSE;
            }
            if (constraint1 == EMPTY && constraint2 == EMPTY) {
                return EMPTY;
            }
            if (constraint1 == TRUE || constraint1 == EMPTY) {
                return constraint2;
            }
            if (constraint2 == TRUE || constraint2 == EMPTY) {
                return constraint1;
            }
            buff.append(" (");
            buff.append(constraint1);
            buff.append(" AND ");
            buff.append(constraint2);
            buff.append(") ");
        } else if (constraint instanceof Comparison) {
            Comparison c = (Comparison) constraint;
            boolean isDate = false;
            buff.append(" (");
            try {
                int pos = buff.length();
                addOperand(buff, c.getOperand1());
                String op1 = buff.substring(pos);
                buff.setLength(pos);

                pos = buff.length();
                addOperand(buff, c.getOperand2());
                String op2 = buff.substring(pos);
                buff.setLength(pos);
                //Handling Dates comparison case :
                //Need to prefix the String with DATE or TIMESTAMP in order for the server to process it like a date
                if (op1.equals("cmis:creationDate") || op1.equals("cmis:lastModificationDate")) {
                    isDate = true;
                }
                Operator operator = Operator.getOperatorByName(c.getOperator());
                buff.append(operator.formatSql(op1, isDate ? "TIMESTAMP " + op2 : op2));
            } catch (NotMappedCmisProperty e) {
                return FALSE;
            }
            buff.append(") ");
        } else if (constraint instanceof PropertyExistence) {
            PropertyExistence c = (PropertyExistence) constraint;
            CmisPropertyMapping propertyMapping = cmisType.getPropertyByJCR(c.getPropertyName());
            if (propertyMapping == null)
                return FALSE;
            else
                buff.append(" (").append(propertyMapping.getQueryName()).append(" IS NOT NULL) ");
        } else if (constraint instanceof SameNode) {
            try {
                SameNode c = (SameNode) constraint;
                String path = c.getPath();
                CmisObject object = dataSource.getObjectByPath(path);
                buff.append(" (cmis:objectId='").append(object.getId()).append("') ");
            } catch (CmisObjectNotFoundException e) {
                return FALSE;
            }
        } else if (constraint instanceof Not) {
            Not c = (Not) constraint;
            StringBuffer constraint1 = addConstraint(c.getConstraint());
            if (constraint1 == FALSE) {
                return TRUE;
            }
            if (constraint1 == TRUE) {
                return FALSE;
            }
            buff.append(" NOT(");
            buff.append(constraint1);
            buff.append(") ");
        } else if (constraint instanceof ChildNode) {
            try {
                ChildNode c = (ChildNode) constraint;
                String parentPath = c.getParentPath();
                CmisObject object = dataSource.getObjectByPath(parentPath);
                buff.append(" IN_FOLDER('").append(object.getId()).append("') ");
            } catch (CmisObjectNotFoundException e) {
                return FALSE;
            }
        } else if (constraint instanceof DescendantNode) {
            try {
                DescendantNode c = (DescendantNode) constraint;
                String ancestorPath = c.getAncestorPath();
                CmisObject object = dataSource.getObjectByPath(ancestorPath);
                buff.append(" IN_TREE('").append(object.getId()).append("') ");
            } catch (CmisObjectNotFoundException e) {
                return FALSE;
            }
        } else if (constraint instanceof FullTextSearch) {
            FullTextSearch c = (FullTextSearch) constraint;
            //If fulltext search is done on jcr:content then executing fulltext search
            if (c.getPropertyName().equals("jcr:content")) {
                buff.append(" contains(");
                addOperand(buff, c.getFullTextSearchExpression());
                buff.append(") ");
            }
            //If fulltext search is done on another property then checking the property mapping
            else {
                CmisPropertyMapping propertyByJCR = cmisType.getPropertyByJCR(c.getPropertyName());
                //If the property is mapped then use like operator to avoid "contains" repetition
                if (propertyByJCR != null) {
                    String searchTerm = c.getFullTextSearchExpression().toString();
                    searchTerm = searchTerm.substring(1, searchTerm.length() - 1);
                    buff.append(propertyByJCR.getCmisName() + "  like '%" + searchTerm + "%' ");
                } else {
                    //If the property is not mapped we do anything
                    return EMPTY;
                }
            }
        }
        return buff;
    }

    private void addOperand(StringBuffer buff, DynamicOperand operand) throws RepositoryException {
        if (operand instanceof LowerCase) {
            throw new UnsupportedRepositoryOperationException("Unsupported operand type LowerCase");
        } else if (operand instanceof UpperCase) {
            throw new UnsupportedRepositoryOperationException("Unsupported operand type UpperCase");
        } else if (operand instanceof Length) {
            throw new UnsupportedRepositoryOperationException("Unsupported operand type Length");
        } else if (operand instanceof NodeName) {
            buff.append("cmis:name");
        } else if (operand instanceof NodeLocalName) {
            buff.append("cmis:name");
        } else if (operand instanceof PropertyValue) {
            PropertyValue o = (PropertyValue) operand;
            CmisPropertyMapping propertyByJCR = cmisType.getPropertyByJCR(o.getPropertyName());
            if (propertyByJCR == null)
                throw new NotMappedCmisProperty(o.getPropertyName());
            buff.append(propertyByJCR.getQueryName());
        } else if (operand instanceof FullTextSearchScore) {
            buff.append(" myscore ");
        }
    }

    private void addOperand(StringBuffer buff, StaticOperand operand) throws RepositoryException {
        if (operand instanceof Literal) {
            Value val = ((Literal) operand).getLiteralValue();
            switch (val.getType()) {
                case PropertyType.BINARY:
                case PropertyType.DOUBLE:
                case PropertyType.DECIMAL:
                case PropertyType.LONG:
                case PropertyType.BOOLEAN:
                    buff.append(val.getString());
                    break;
                case PropertyType.STRING:
                    buff.append("'").append(escapeString(val.getString())).append("'");
                    break;
                case PropertyType.DATE:
                    buff.append(" TIMESTAMP '").append(val.getString()).append("'");
                    break;
                case PropertyType.NAME:
                case PropertyType.PATH:
                case PropertyType.REFERENCE:
                case PropertyType.WEAKREFERENCE:
                case PropertyType.URI:
                    // TODO implement valid suppoert for this operand types
                    buff.append("'").append(val.getString()).append("'");
                    break;
                default:
                    throw new UnsupportedRepositoryOperationException("Unsupported operand value type " + val.getType());
            }
        } else {
            throw new UnsupportedRepositoryOperationException("Unsupported operand type " + operand.getClass());
        }
    }

    private String escapeString(String string) {
        return string.replace("\\", "\\\\").replace("'", "\\'");

    }
}