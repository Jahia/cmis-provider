package org.jahia.modules.external.cmis;

import java.text.ParseException;
import java.util.logging.Level;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.commons.query.qom.Operator;
import org.jahia.modules.external.ExternalQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

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

    private static final Pattern ISO8601_TIMESTAMP = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+\\d{2}:\\d{2}");

    protected final StringBuffer TRUE = new StringBuffer("true");
    protected final StringBuffer FALSE = new StringBuffer("false");

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
        String nodeTypeName = getNodeTypeName(selector.getNodeTypeName());

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

        // add constraint on mime type to get only images
        if (nodeTypeName.equals("jmix:image")) {
            if (hasConstraint) {
                buff.append(" AND");
            } else {
                buff.append(" WHERE ");
            }
            buff.append( " (cmis:contentStreamMimeType like 'image/%')");
        }

        if (query.getOrderings() != null) {
            boolean isFirst = true;
            StringBuffer tmpBuf = new StringBuffer();
            for (Ordering ordering : query.getOrderings()) {
                tmpBuf.setLength(0);
                try {
                    addOperand(tmpBuf, ordering.getOperand());
                    //In CMIS the score can only be used when using fulltext search
                    if (" myscore ".equals(tmpBuf.toString()) && (!"contains(".contains(buff.toString()))) {
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


    protected StringBuffer addConstraint(Constraint constraint) throws RepositoryException {
        StringBuffer buff = new StringBuffer();
        if (constraint instanceof Or) {
           buff = getOrConstraint((Or) constraint);
        } else if (constraint instanceof And) {
            buff = getAndConstraint((And) constraint);
        } else if (constraint instanceof Comparison) {
            try {
                buff = getComparisonConstraint((Comparison) constraint);
            } catch (NotMappedCmisProperty e) {
                return FALSE;
            }
        } else if (constraint instanceof PropertyExistence) {
            buff = getPropertyExistenceConstraint((PropertyExistence) constraint);
        } else if (constraint instanceof SameNode) {
            try{
                buff = getSameNodeConstraint((SameNode) constraint);
            } catch (CmisObjectNotFoundException e) {
                return FALSE;
            }
        } else if (constraint instanceof Not) {
            buff = getNotConstraint((Not) constraint);
        } else if (constraint instanceof ChildNode) {
            try {
                buff = getChildNodeConstraint((ChildNode) constraint);
            } catch (CmisObjectNotFoundException e) {
                return FALSE;
            }
        } else if (constraint instanceof DescendantNode) {
            try {
                buff = getDescendantNodeConstraint((DescendantNode) constraint);
            } catch (CmisObjectNotFoundException e) {
                return FALSE;
            }
        } else if (constraint instanceof FullTextSearch) {
            FullTextSearch c = (FullTextSearch) constraint;
            buff = getFullTextSearchConstraint(c);
        }
        return buff;
    }

    protected StringBuffer getDescendantNodeConstraint(DescendantNode c) throws CmisObjectNotFoundException, RepositoryException{
        StringBuffer buff = new StringBuffer();
        String ancestorPath = c.getAncestorPath();
        CmisObject object = dataSource.getObjectByPath(ancestorPath);
        buff.append(" IN_TREE('").append(object.getId()).append("') ");
        return buff;
    }

    protected StringBuffer getChildNodeConstraint(ChildNode c) throws CmisObjectNotFoundException, RepositoryException{
        StringBuffer buff = new StringBuffer();
        String parentPath = c.getParentPath();
        CmisObject object = dataSource.getObjectByPath(parentPath);
        buff.append(" IN_FOLDER('").append(object.getId()).append("') ");
        return buff;
    }

    protected StringBuffer getNotConstraint(Not c) throws CmisObjectNotFoundException, RepositoryException{
        StringBuffer buff = new StringBuffer();
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
        return buff;
    }

    protected StringBuffer getSameNodeConstraint(SameNode c) throws CmisObjectNotFoundException, RepositoryException{
        StringBuffer buff = new StringBuffer();
        String path = c.getPath();
        CmisObject object = dataSource.getObjectByPath(path);
        buff.append(" (cmis:objectId='").append(object.getId()).append("') ");
        return buff;
    }

    protected StringBuffer getPropertyExistenceConstraint(PropertyExistence c) throws CmisObjectNotFoundException, RepositoryException{
        StringBuffer buff = new StringBuffer();
        CmisPropertyMapping propertyMapping = cmisType.getPropertyByJCR(c.getPropertyName());
        if (propertyMapping == null)
            return FALSE;
        else
            buff.append(" (").append(propertyMapping.getQueryName()).append(" IS NOT NULL) ");
        return buff;
    }

    protected StringBuffer getComparisonConstraint(Comparison c) throws NotMappedCmisProperty, RepositoryException{
        StringBuffer buff = new StringBuffer();
        buff.append(" (");
        int pos = buff.length();
        addOperand(buff, c.getOperand1());
        String op1 = buff.substring(pos);
        buff.setLength(pos);

        pos = buff.length();
        addOperand(buff, c.getOperand2());
        String op2 = buff.substring(pos);
        buff.setLength(pos);

        Operator operator = Operator.getOperatorByName(c.getOperator());
        buff.append(operator.formatSql(op1, op2));
        buff.append(") ");
        return buff;
    }

    protected StringBuffer getAndConstraint(And c) throws RepositoryException{
        StringBuffer buff = new StringBuffer();
        StringBuffer constraint1 = addConstraint(c.getConstraint1());
        StringBuffer constraint2 = addConstraint(c.getConstraint2());
        if (constraint1 == FALSE || constraint2 == FALSE) {
            return FALSE;
        }
        if (constraint1 == TRUE) {
            return constraint2;
        }
        if (constraint2 == TRUE) {
            return constraint1;
        }
        buff.append(" (");
        buff.append(constraint1);
        buff.append(" AND ");
        buff.append(constraint2);
        buff.append(") ");
        return buff;
    }

    protected StringBuffer getOrConstraint(Or c) throws RepositoryException{
        StringBuffer buff = new StringBuffer();
        StringBuffer constraint1 = addConstraint(c.getConstraint1());
        StringBuffer constraint2 = addConstraint(c.getConstraint2());
        if (constraint1 == TRUE || constraint2 == TRUE) {
            return TRUE;
        }
        if (constraint1 == FALSE) {
            return constraint2;
        }
        if (constraint2 == FALSE) {
            return constraint1;
        }
        buff.append(" (");
        buff.append(constraint1);
        buff.append(" OR ");
        buff.append(constraint2);
        buff.append(") ");
        return buff;
    }

    /**
     * Externalize the fulltext search treatment for overrides
     * @param c
     * @return
     * @throws RepositoryException
     */
    protected StringBuffer getFullTextSearchConstraint(FullTextSearch c) throws RepositoryException {
        StringBuffer buff = new StringBuffer();
        buff.append(" contains(");
        addOperand(buff, c.getFullTextSearchExpression());
        buff.append(") ");
        return buff;
    }

    protected void addOperand(StringBuffer buff, DynamicOperand operand) throws RepositoryException {
        if (operand instanceof LowerCase) {
            // ignore lowerCase keyword as it's not supported by cmis
            addOperand(buff, ((LowerCase) operand).getOperand());
        } else if (operand instanceof UpperCase) {
            // ignore upperCase keyword as it's not supported by cmis
            addOperand(buff, ((UpperCase) operand).getOperand());
        } else if (operand instanceof Length) {
            throw new UnsupportedRepositoryOperationException("Unsupported operand type Length");
        } else if (operand instanceof NodeName || operand instanceof NodeLocalName) {
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

    protected void addOperand(StringBuffer buff, StaticOperand operand) throws RepositoryException {
        if (operand instanceof Literal) {
            try {
                final Value val = ((Literal) operand).getLiteralValue();
                final String valStr = discardEscapeChar(val.getString());
                switch (val.getType()) {
                    case PropertyType.BINARY:
                    case PropertyType.DOUBLE:
                    case PropertyType.DECIMAL:
                    case PropertyType.LONG:
                    case PropertyType.BOOLEAN:
                        buff.append(valStr);
                        break;
                    case PropertyType.STRING:
                        String escapedValue = escapeString(valStr);
                        if (ISO8601_TIMESTAMP.matcher(escapedValue).matches()) {
                            buff.append(" TIMESTAMP ");
                        }
                        buff.append("'").append(escapedValue).append("'");
                        break;
                    case PropertyType.DATE:
                        buff.append(" TIMESTAMP '").append(valStr).append("'");
                        break;
                    case PropertyType.NAME:
                    case PropertyType.PATH:
                    case PropertyType.REFERENCE:
                    case PropertyType.WEAKREFERENCE:
                    case PropertyType.URI:
                        // TODO implement valid support for this operand types
                        buff.append("'").append(valStr).append("'");
                        break;
                    default:
                        throw new UnsupportedRepositoryOperationException("Unsupported operand value type " + val.getType());
                }
            } catch (ParseException ex) {
                throw new IllegalArgumentException(ex);
            }
        } else {
            throw new UnsupportedRepositoryOperationException("Unsupported operand type " + operand.getClass());
        }
    }

    protected String escapeString(String string) {
        return string.replace("\\", "\\\\").replace("'", "\\'");

    }

    protected String getNodeTypeName(String name){
        // Supports queries on nt:hierarchyNode or jmix:searchable or jmix:image as file queries
        return (name.equals("nt:hierarchyNode") || name.equals("jmix:searchable") || name.equals("jmix:image")) ?
                "jnt:file" : name;
    }
    
    
    // Function retrieved from org.apache.lucene.queryParser.QueryParser
    public static String discardEscapeChar(String input) throws ParseException{
        // Create char array to hold unescaped char sequence
        char[] output = new char[input.length()];

        // The length of the output can be less than the input
        // due to discarded escape chars. This variable holds
        // the actual length of the output
        int length = 0;

        // We remember whether the last processed character was 
        // an escape character
        boolean lastCharWasEscapeChar = false;

        // The multiplier the current unicode digit must be multiplied with.
        // E. g. the first digit must be multiplied with 16^3, the second with 16^2...
        int codePointMultiplier = 0;

        // Used to calculate the codepoint of the escaped unicode character
        int codePoint = 0;

        for (int i = 0; i < input.length(); i++) {
            char curChar = input.charAt(i);
            if (codePointMultiplier > 0) {
                codePoint += hexToInt(curChar) * codePointMultiplier;
                codePointMultiplier >>>= 4;
                if (codePointMultiplier == 0) {
                    output[length++] = (char) codePoint;
                    codePoint = 0;
                }
            } else if (lastCharWasEscapeChar) {
                if (curChar == 'u') {
                    // found an escaped unicode character
                    codePointMultiplier = 16 * 16 * 16;
                } else {
                    // this character was escaped
                    output[length] = curChar;
                    length++;
                }
                lastCharWasEscapeChar = false;
            } else {
                if (curChar == '\\') {
                    lastCharWasEscapeChar = true;
                } else {
                    output[length] = curChar;
                    length++;
                }
            }
        }

        if (codePointMultiplier > 0) {
            throw new IllegalStateException("Truncated unicode escape sequence.");
        }

        if (lastCharWasEscapeChar) {
            throw new IllegalStateException("Term can not end with escape character.");
        }

        return new String(output, 0, length);
    }
    
    // Function retrieved from org.apache.lucene.queryParser.QueryParser
    private static int hexToInt(char c) throws ParseException {
        if ('0' <= c && c <= '9') {
            return c - '0';
        } else if ('a' <= c && c <= 'f') {
            return c - 'a' + 10;
        } else if ('A' <= c && c <= 'F') {
            return c - 'A' + 10;
        } else {
            throw new IllegalStateException("None-hex character in unicode escape sequence: " + c);
        }
    }
}
