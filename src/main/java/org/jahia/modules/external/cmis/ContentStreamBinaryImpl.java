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
