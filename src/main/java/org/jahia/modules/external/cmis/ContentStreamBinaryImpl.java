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
 *     Copyright (C) 2002-2017 Jahia Solutions Group. All rights reserved.
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

import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import java.math.BigInteger;

/**
 * Created by: Boris
 * Date: 2/4/14
 * Time: 9:02 PM
 */
public class ContentStreamBinaryImpl extends ContentStreamImpl {
    private static final long serialVersionUID = 5183491474411250965L;
    
    transient Binary binary;

    public ContentStreamBinaryImpl(Binary binary, String fileName, String mimeType) throws RepositoryException {
        setLength(BigInteger.valueOf(binary.getSize()));
        setMimeType(mimeType);
        setFileName(fileName);
        setStream(binary.getStream());
        this.binary = binary;
    }

    public void disposeBinary() {
        binary.dispose();
    }
}
