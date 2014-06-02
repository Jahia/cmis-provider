package org.jahia.modules.external.cmis;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.query.qom.*;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.jackrabbit.commons.query.qom.Operator;
import org.jahia.modules.external.ExternalQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final String FALSE = "(cmis:name='')";  // USED as False in sql
    CmisDataSource dataSource;
    ExternalQuery query;
    CmisConfiguration conf;
    CmisTypeMapping cmisType;

    public QueryResolver(CmisDataSource dataSource,ExternalQuery query) {
        this.dataSource = dataSource;
        this.query=query;
        conf=dataSource.conf;
    }

    public String resolve() throws RepositoryException {
        StringBuffer buff=new StringBuffer("SELECT cmis:objectId as id FROM ");

        Source source = query.getSource();
        if (source instanceof Join) {
            log.debug("Join not supported in CMIS queries");
            return null;
        }
        Selector selector= (Selector)source;
        String nodeTypeName = selector.getNodeTypeName();
        cmisType = conf.getTypeByJCR(nodeTypeName);
        if (cmisType==null) {
            log.debug("Unmapped types not supported in CMIS queries");
            return null;
        }
        buff.append(cmisType.getQueryName());
//        if (selector.getSelectorName()!=null && !selector.getSelectorName().isEmpty())
//            buff.append(" as ").append(selector.getSelectorName());
        if (query.getConstraint()!=null) {
            buff.append(" WHERE ");
            addConstarint(buff, query.getConstraint());
        }
        if (query.getOrderings()!=null) {
            boolean isFirst=true;
            StringBuffer tmpBuf=new StringBuffer();
            for (Ordering ordering : query.getOrderings()) {
                tmpBuf.setLength(0);
                try {
                    addOpperand(tmpBuf,ordering.getOperand());
                    if (isFirst) {
                        buff.append(" ORDER BY ");
                        isFirst=false;
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
                } catch (NotMappedCmisProperty ignore) { //ignore ordering by not mapped properties
                }
            }
        }
        return buff.toString();
    }


    private void addConstarint(StringBuffer buff, Constraint constraint) throws RepositoryException {
        if (constraint instanceof Or) {
            Or c= (Or)constraint;
            buff.append(" (");
            addConstarint(buff,c.getConstraint1());
            buff.append(" OR ");
            addConstarint(buff,c.getConstraint2());
            buff.append(") ");
        } else if (constraint instanceof And) {
            And c = (And)constraint;
            buff.append(" (");
            addConstarint(buff, c.getConstraint1());
            buff.append(" AND ");
            addConstarint(buff, c.getConstraint2());
            buff.append(") ");
        } else if (constraint instanceof Comparison) {
            Comparison c = (Comparison)constraint;
            buff.append(" (");
            try {
                int pos=buff.length();
                addOpperand(buff, c.getOperand1());
                String op1=buff.substring(pos);
                buff.setLength(pos);

                pos=buff.length();
                addOpperand(buff, c.getOperand2());
                String op2=buff.substring(pos);
                buff.setLength(pos);

                Operator operator = Operator.getOperatorByName(c.getOperator());
                buff.append(operator.formatSql(op1,op2));
            } catch (NotMappedCmisProperty e) {
                buff.append(FALSE);  // FALSE anyway
            }
            buff.append(") ");
        } else if (constraint instanceof PropertyExistence) {
            PropertyExistence c = (PropertyExistence)constraint;
            CmisPropertyMapping propertyMapping = cmisType.getPropertyByJCR(c.getPropertyName());
            if (propertyMapping==null)
                buff.append(FALSE);  // FALSE anyway
            else
                buff.append(" (").append(propertyMapping.getQueryName()).append(" IS NOT NULL) ");
        } else if (constraint instanceof SameNode) {
            try {
                SameNode c = (SameNode)constraint;
                String path = c.getPath();
                CmisObject object = dataSource.getCmisSession().getObjectByPath(path);
                buff.append(" (cmis:objectId='").append(object.getId()).append("') ");
            } catch (CmisObjectNotFoundException e) {
                buff.append(FALSE);  // FALSE anyway
            }
        } else if (constraint instanceof Not) {
            Not c = (Not)constraint;
            buff.append(" NOT(");
            addConstarint(buff, c.getConstraint());
            buff.append(") ");
        } else if (constraint instanceof ChildNode) {
            try {
                ChildNode c = (ChildNode)constraint;
                String parentPath = c.getParentPath();
                CmisObject object = dataSource.getCmisSession().getObjectByPath(parentPath);
                buff.append(" IN_FOLDER('").append(object.getId()).append("') ");
            } catch (CmisObjectNotFoundException e) {
                buff.append(FALSE);  // FALSE anyway
            }
        } else if (constraint instanceof DescendantNode) {
            try {
                DescendantNode c = (DescendantNode)constraint;
                String ancestorPath = c.getAncestorPath();
                CmisObject object = dataSource.getCmisSession().getObjectByPath(ancestorPath);
                buff.append(" IN_TREE('").append(object.getId()).append("') ");
            } catch (CmisObjectNotFoundException e) {
                buff.append(FALSE);  // FALSE anyway
            }
        } else if (constraint instanceof FullTextSearch) {
            FullTextSearch c = (FullTextSearch)constraint;
            buff.append(" contains(");
            addOpperand(buff, c.getFullTextSearchExpression());
            buff.append(") ");
        }
    }
    private void addOpperand(StringBuffer buff, DynamicOperand operand) throws RepositoryException {
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
            PropertyValue o= (PropertyValue)operand;
            CmisPropertyMapping propertyByJCR = cmisType.getPropertyByJCR(o.getPropertyName());
            if (propertyByJCR==null)
                throw new NotMappedCmisProperty(o.getPropertyName());
            buff.append(propertyByJCR.getQueryName());
        } else if (operand instanceof FullTextSearchScore) {
            buff.append(" score() ");
        }
    }

    private void addOpperand(StringBuffer buff, StaticOperand operand) throws RepositoryException {
        if (operand instanceof Literal) {
            Value val=((Literal)operand).getLiteralValue();
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
                    throw new UnsupportedRepositoryOperationException("Unsupported operand value type "+val.getType());
            }
        } else {
            throw new UnsupportedRepositoryOperationException("Unsupported operand type "+operand.getClass());
        }
    }

    private String escapeString(String string) {
        return string.replace("\\", "\\\\").replace("'","\\'");

    }
}
