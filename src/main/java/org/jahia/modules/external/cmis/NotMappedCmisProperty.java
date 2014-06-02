package org.jahia.modules.external.cmis;

import javax.jcr.RepositoryException;

/**
 * NotMappedCmisProperty exception used in QueryResolver to indicate that request contains not mapped property.
 * QueryResolver have special workaround for not mapped properties. It is important because Jahia can add jnt:translation properties in query to make it language specific.
 * Not mapped property acts as null in where clauses and ignored in order by
 * Created by: Boris
 * Date: 2/6/14
 * Time: 4:58 PM
 */
public class NotMappedCmisProperty extends RepositoryException {
    public NotMappedCmisProperty() {
    }

    public NotMappedCmisProperty(String name) {
        this(name,null);
    }

    public NotMappedCmisProperty(String name, Throwable rootCause) {
        super("Property "+name+" not mapped", rootCause);
    }

    public NotMappedCmisProperty(Throwable rootCause) {
        super(rootCause);
    }
}
