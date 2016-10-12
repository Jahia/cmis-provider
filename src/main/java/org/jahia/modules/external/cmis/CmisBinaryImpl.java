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

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUnauthorizedException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;

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
    CmisDataSource dataSource;
    String path;
    String user;

    private static final Logger log = LoggerFactory.getLogger(CmisBinaryImpl.class);


    public CmisBinaryImpl(Document doc) {
        this.doc = doc;
//        this.contentStream = doc.getContentStream();
    }

    public CmisBinaryImpl(Document doc, String path, CmisDataSource dataSource, String user) {
        this.doc = doc;
        this.dataSource = dataSource;
        this.path = path;
        this.user = user;
//        this.contentStream = doc.getContentStream();
    }

    @Override
    public InputStream getStream() throws RepositoryException {
        if (listOfStreamsForClose == null) {
            listOfStreamsForClose = new ArrayList<InputStream>();
        }
        BufferedInputStreamWrapper stream = null;
        if (doc == null) {
            throw new IllegalStateException();
        }
        try {
            stream = new BufferedInputStreamWrapper(doc.getContentStream().getStream());
            log.debug("init stream with {} bytes", stream.available());
            stream.setRemainingBytes(doc.getContentStreamLength());
            return stream;
        } catch (CmisUnauthorizedException e1) {
            // restore session on cmis object if session times out
            if (dataSource != null) {
                // clean up the session
                dataSource.getActiveConnections().invalidate(user);
                doc = (Document) dataSource.getObjectById(user, doc.getId());
                try {
                    stream = new BufferedInputStreamWrapper(doc.getContentStream().getStream());
                    stream.setRemainingBytes(doc.getContentStreamLength());
                    return stream;
                } catch (Exception e) {
                    log.error("Error while retreiving binary content of {} with user {}", path, user);
                    throw new RepositoryException(e);
                }
            }
            throw new RepositoryException(String.format("unable to get item %s with user %s", path, user), e1);
        } catch (Exception e) {
            log.error("Error while retreiving binary content of {} with user {}", path, user);
            throw new RepositoryException(e);
        } finally {
            if (stream != null) {
                listOfStreamsForClose.add(stream);
            }
        }
    }

    @Override
    public int read(byte[] b, long position) throws IOException, RepositoryException {
        if (doc == null)
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
        if (doc == null)
            throw new IllegalStateException();
        long size = this.doc.getContentStreamLength();
        return size;
//        return contentStream.getLength();
    }

    @Override
    public void dispose() {
        if (listOfStreamsForClose != null) {
            for (InputStream is : listOfStreamsForClose) {
                IOUtils.closeQuietly(is);
            }
        }
        listOfStreamsForClose = null;
        doc = null;
    }

    private class BufferedInputStreamWrapper extends BufferedInputStream {

        private long remainingBytes;

        public BufferedInputStreamWrapper(InputStream in) {
            super(in);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            mark(len);
            int bytesRead;
            int previousBytesRead = 0;
            for (; ; ) {
                bytesRead = super.read(b, off, len);
                remainingBytes -= bytesRead;
                // if we did not manage to get the full buffer, we try again unless we reach the end of the file
                // if we read twice the same amount of bytes, we can guess that we reach the end of stream
                if (bytesRead < len && remainingBytes > 0 && previousBytesRead != bytesRead) {
                    remainingBytes += bytesRead;
                    previousBytesRead = bytesRead;
                    reset();
                } else {
                    break;
                }
            }
            return bytesRead;
        }

        @Override
        public synchronized int available() throws IOException {
            return Long.valueOf(remainingBytes).intValue();
        }

        @Override
        public synchronized long skip(long skipped) throws IOException {
            int bufferSize = 1024;
            if (skipped > remainingBytes) {
                return 0;
            }
            remainingBytes -= skipped;
            int remainToSkip = Long.valueOf(skipped).intValue();
            // toDo: improve the skip method to not read the whole file each time
            byte[] b = new byte[bufferSize];
            while (remainToSkip > 0) {
                int len = skipped > bufferSize ? bufferSize : remainToSkip;
                int bytesRead = super.read(b, 0, len);
                remainToSkip -= bytesRead;
            }
            return skipped;
        }

        @Override
        public synchronized int read() throws IOException {
            int bytesRead = super.read();
            remainingBytes -= bytesRead;
            return bytesRead;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int bytesRead = super.read(b);
            remainingBytes -= bytesRead;
            return bytesRead;
        }

        @Override
        public synchronized void reset() throws IOException {
            if (pos > markpos) {
                remainingBytes += pos - markpos;
            }
            super.reset();
        }

        public void setRemainingBytes(long remainingBytes) {
            this.remainingBytes = remainingBytes;
        }
    }

}
