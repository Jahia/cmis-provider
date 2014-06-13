/**
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect”, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group. All rights reserved.
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
 *
 *
 *
 *
 */
package org.jahia.modules.external.cmis;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.commons.io.IOUtils;

import javax.jcr.Binary;
import javax.jcr.RepositoryException;
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

    public CmisBinaryImpl(Document doc) {
        this.doc = doc;
//        this.contentStream = doc.getContentStream();
    }

    @Override
    public InputStream getStream() throws RepositoryException {
        if (doc == null)
            throw new IllegalStateException();
        try {
            if (listOfStreamsForClose == null) {
                listOfStreamsForClose = new ArrayList<InputStream>();
            }
            InputStream stream = doc.getContentStream().getStream();
            listOfStreamsForClose.add(stream);
            return stream;
        } catch (Exception e) {
            throw new RepositoryException(e);
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

}
