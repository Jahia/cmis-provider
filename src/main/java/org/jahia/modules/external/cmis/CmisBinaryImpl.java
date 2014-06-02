package org.jahia.modules.external.cmis;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.commons.io.IOUtils;

/**
 * Implementation binaray properties to access CMIS document's content
 * Created by: Boris
 * Date: 1/27/14
 * Time: 1:09 PM
 */
public class CmisBinaryImpl implements Binary {
//    ContentStream contentStream;
    Document doc;
    ArrayList<InputStream> listOfStreamsForClose;

    public CmisBinaryImpl(Document doc) {
        this.doc=doc;
//        this.contentStream = doc.getContentStream();
    }

    @Override
    public InputStream getStream() throws RepositoryException {
        if (doc==null)
            throw new IllegalStateException();
        try {
            if (listOfStreamsForClose==null) {
                listOfStreamsForClose=new ArrayList<InputStream>();
            }
            InputStream stream=doc.getContentStream().getStream();
            listOfStreamsForClose.add(stream);
            return stream;
        } catch (Exception e) {
            throw new RepositoryException(e);
        }
    }

    @Override
    public int read(byte[] b, long position) throws IOException, RepositoryException {
        if (doc==null)
            throw new IllegalStateException();
        InputStream is = null;
        int read = 0;
        try {
            ContentStream cs = doc.getContentStream(BigInteger.valueOf(position), BigInteger.valueOf(b.length));
            is = cs.getStream();
            is.skip(position);
            read = is.read(b);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RepositoryException(ex);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return read;
    }

    @Override
    public long getSize() throws RepositoryException {
        if (doc==null)
            throw new IllegalStateException();
        long size = this.doc.getContentStreamLength();
        return size;
//        return contentStream.getLength();
    }

    @Override
    public void dispose() {
        if (listOfStreamsForClose!=null) {
            for (InputStream is : listOfStreamsForClose) {
                IOUtils.closeQuietly(is);
            }
        }
        listOfStreamsForClose=null;
        doc=null;
    }

}
