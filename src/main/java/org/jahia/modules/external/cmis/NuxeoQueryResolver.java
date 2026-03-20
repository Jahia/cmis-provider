package org.jahia.modules.external.cmis;


import java.text.ParseException;
import org.jahia.api.Constants;
import org.jahia.modules.external.ExternalQuery;
import javax.jcr.RepositoryException;
import javax.jcr.query.qom.*;
import org.apache.chemistry.opencmis.client.api.QueryStatement;
import org.apache.chemistry.opencmis.client.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by rizak on 17/06/16.
 * This class is based on the CMIS QueryResolver but implements Nuxeo Query building specificities
 */
public class NuxeoQueryResolver extends QueryResolver{
    private static final Logger LOGGER = LoggerFactory.getLogger(NuxeoQueryResolver.class);
    private final Session session;

    public NuxeoQueryResolver(CmisDataSource dataSource, ExternalQuery query, Session session) {
        super(dataSource, query);
        this.session = session;
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
    protected StringBuffer getFullTextSearchConstraint(FullTextSearch c) throws RepositoryException {
        final StringBuffer buff = new StringBuffer();
        String searchTerm;

        try {
            searchTerm = discardEscapeChar(c.getFullTextSearchExpression().toString());
        } catch (ParseException ex) {
            LOGGER.warn("Impossible to escape the full text search expression", ex);
            searchTerm = escapeString(c.getFullTextSearchExpression().toString());
        }
        searchTerm = searchTerm.substring(1, searchTerm.length() - 1);

        //If fulltext search is done on jcr:content then executing fulltext search
        if (c.getPropertyName().equals(Constants.JCR_CONTENT)) {
            buff.append(" contains(?) ");
        } //If fulltext search is done on another property then checking the property mapping
        //TODO : Avoid to use the like operator as often as possible and prefer one contains on multiple expressions
        else {
            CmisPropertyMapping propertyByJCR = cmisType.getPropertyByJCR(c.getPropertyName());
            //If the property is mapped then use like operator to avoid "contains" repetition
            if (propertyByJCR != null) {
                buff.append(propertyByJCR.getCmisName()).append("  like ? ");
                searchTerm = '%' + searchTerm + '%';
            } else {
                //If the property is not mapped we don't do anything
                return FALSE;
            }
        }

        final QueryStatement qs = session.createQueryStatement(buff.toString());
        qs.setString(1, searchTerm);
        return new StringBuffer(qs.toQueryString());
    }
}