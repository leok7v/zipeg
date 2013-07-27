/*
 * Zip32OutputArchive.java
 *
 * Created on 27. Februar 2006, 01:10
 */
/*
 * Copyright 2006 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schlichtherle.io.archive.zip;

import de.schlichtherle.io.OutputArchiveMetaData;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.OutputArchive;
import de.schlichtherle.io.archive.spi.OutputArchiveBusyException;
import de.schlichtherle.util.zip.ZipOutputStream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.zip.Deflater;

/**
 * An implementation of {@link OutputArchive} to write ZIP32 archives.
 *
 * @see Zip32Driver
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class Zip32OutputArchive
        extends ZipOutputStream
        implements OutputArchive {

    private final Zip32InputArchive source;
    
    /**
     * Creates a new instance of <code>Zip32OutputArchive</code> which always
     * uses the best compression level.
     */
    public Zip32OutputArchive(
            final OutputStream out,
            final String encoding,
            final Zip32InputArchive source)
    throws  NullPointerException,
            UnsupportedEncodingException,
            IOException {
        super(out, encoding);
        setLevel(Deflater.BEST_COMPRESSION);

        this.source = source;
        if (source != null) {
            // Retain comment and preamble of input ZIP32 archive.
            setComment(source.getComment());
            if (source.getPreambleLength() > 0) {
                final InputStream in = source.getPreambleInputStream();
                try {
                    de.schlichtherle.io.File.cat(
                            in, source.offsetsConsiderPreamble() ? this : out);
                } finally {
                    in.close();
                }
            }
        }
    }

    public int getNumArchiveEntries() {
        return size();
    }

    public Enumeration getArchiveEntries() {
        return entries();
    }

    public ArchiveEntry getArchiveEntry(final String name) {
        return (Zip32Entry) getEntry(name);
    }

    public OutputStream getOutputStream(
            final ArchiveEntry entry,
            final ArchiveEntry srcEntry)
    throws IOException {
        if (busy())
            throw new OutputArchiveBusyException(entry);

        final Zip32Entry zipEntry = (Zip32Entry) entry;
        if (srcEntry instanceof Zip32Entry) {
            // Steal some entry attributes for Direct Data Copying (DDC).
            final Zip32Entry srcZipEntry = (Zip32Entry) srcEntry;
            zipEntry.setCrc(srcZipEntry.getCrc());
            zipEntry.setCompressedSize(srcZipEntry.getCompressedSize());
            zipEntry.setSize(srcZipEntry.getSize());
        }

        return createEntryOutputStream(zipEntry, srcEntry);
    }

    protected OutputStream createEntryOutputStream(
            final Zip32Entry entry,
            final ArchiveEntry srcEntry)
    throws IOException {
        return new EntryOutputStream(entry, !(srcEntry instanceof Zip32Entry));
    }

    protected class EntryOutputStream extends FilterOutputStream {

        public EntryOutputStream(Zip32Entry entry, boolean deflate)
        throws IOException {
            super(Zip32OutputArchive.this);
            putNextEntry(entry, deflate);
        }

        public void write(int b) throws IOException {
            out.write(b);
        }

        public void write(byte[] b) throws IOException {
            out.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        public void close() throws IOException {
            assert out == Zip32OutputArchive.this;
            Zip32OutputArchive.this.closeEntry();
        }
    }

    public void storeDirectory(ArchiveEntry entry)
    throws IOException {
        final Zip32Entry ze = (Zip32Entry) entry;
        ze.setMethod(Zip32Entry.STORED);
        ze.setCrc(0);
        ze.setSize(0);
        putNextEntry(ze);
        closeEntry();
    }

    /**
     * Retain the postamble of the source ZIP archive, if any.
     */
    public void finish() throws IOException {
        super.finish();

        if (source == null)
            return;

        final long ipl = source.getPostambleLength();
        if (ipl <= 0)
            return;

        final long il = source.length();
        final long ol = length();

        final InputStream in = source.getPostambleInputStream();
        try {
            // Second, if the output ZIP compatible file differs in length from
            // the input ZIP compatible file pad the output to the next four byte
            // boundary before appending the postamble.
            // This might be required for self extracting files on some platforms
            // (e.g. Wintel).
            if (ol + ipl != il)
                write(new byte[(int) (ol % 4)]);

            // Finally, write the postamble.
            de.schlichtherle.io.File.cat(in, this);
        } finally {
            in.close();
        }
    }

    //
    // Metadata implementation.
    //

    private OutputArchiveMetaData metaData;

    public OutputArchiveMetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(OutputArchiveMetaData metaData) {
        this.metaData = metaData;
    }
}
