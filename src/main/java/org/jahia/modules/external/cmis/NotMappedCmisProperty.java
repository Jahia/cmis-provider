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
 *     Copyright (C) 2002-2020 Jahia Solutions Group. All rights reserved.
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
    private static final long serialVersionUID = 404316619871182597L;

    public NotMappedCmisProperty() {
    }

    public NotMappedCmisProperty(String name) {
        this(name, null);
    }

    public NotMappedCmisProperty(String name, Throwable rootCause) {
        super("Property " + name + " not mapped", rootCause);
    }

    public NotMappedCmisProperty(Throwable rootCause) {
        super(rootCause);
    }
}
