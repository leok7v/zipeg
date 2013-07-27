/*
 * TarInputArchive.java
 *
 * Created on 28. Februar 2006, 11:53
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

package de.schlichtherle.io.archive.tar;

import de.schlichtherle.io.InputArchiveMetaData;
import de.schlichtherle.io.archive.Archive;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.io.util.Path;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.tools.tar.TarBuffer;
import org.apache.tools.tar.TarConstants;

import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarUtils;

/**
 * Presents a {@link TarInputStream} as a random accessible archive.
 * <b>Warning:</b> 
 * The constructors of this class extract the entire archive to a
 * temporary directory before allowing any access to its contents.
 * This may be very time and space consuming for large archives!
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class TarInputArchive implements InputArchive, TarConstants {

    /**
     * Loads the entire TAR input stream into a temporary directory in order
     * to allow random access to its contents.
     * @param in The input stream from which this input archive file should be
     *        initialized. This stream is not used by any of the methods in
     *        this class after the constructor has terminated and is
     *        <em>never</em> closed!
     *        So it is safe and recommended to close it upon termination
     *        of this constructor.
     */
    TarInputArchive(final Archive archive, final InputStream in) throws IOException {
        final TarInputStream tin = createValidatedTarInputStream(in);
        try {
            org.apache.tools.tar.TarEntry tinEntry;
            while ((tinEntry = tin.getNextEntry()) != null) {
                final File temp = File.createTempFile("tar", null);
                boolean ok = temp.delete();
                assert ok;
                if (tinEntry.isDirectory()) {
                    ok = temp.mkdirs();
                } else {
                    final FileOutputStream out = new FileOutputStream(temp);
                    try {
                        de.schlichtherle.io.File.cat(tin, out); // use high performance pump (async I/O)
                    } finally {
                        out.close();
                    }
                }
                if (!temp.setLastModified(tinEntry.getModTime().getTime()))
                    throw new FileNotFoundException(archive.getPath() + "/" + tinEntry.getName());
                final TarEntry entry = new TarEntry(temp);
                entry.setName(Path.normalize(tinEntry.getName(), '/'));
                entries.put(entry.getName(), entry);
            }
        } catch (IOException failure) {
            close();
            throw failure;
        }
    }

    private static final byte[] empty = new byte[TarBuffer.DEFAULT_RCDSIZE];

    private static final int CHECKSUM_OFFSET
            = NAMELEN + MODELEN + UIDLEN + GIDLEN + SIZELEN + MODTIMELEN;

    /**
     * Returns a newly created and validated {@link TarInputStream}.
     * This method performs a simple validation by computing the checksum
     * for the first record only.
     * This method is required because the <code>TarInputStream</code>
     * unfortunately does not do any validation!
     */
    private static TarInputStream createValidatedTarInputStream(
            final InputStream in)
    throws IOException {
        final ReadAheadInputStream pin
                = new ReadAheadInputStream(
                    in, TarBuffer.DEFAULT_RCDSIZE);
        final byte[] buf = new byte[TarBuffer.DEFAULT_RCDSIZE];
        pin.readFully(buf);
        pin.unread(buf);
        if (!Arrays.equals(buf, empty)) {
            final long expected = TarUtils.parseOctal(buf, CHECKSUM_OFFSET, 8);
            for (int i = 0; i < 8; i++)
                buf[CHECKSUM_OFFSET + i] = ' ';
            final long is = TarUtils.computeCheckSum(buf);
            if (expected != is)
                throw new IOException(
                        "Illegal initial record in TAR file: Expected checksum " + expected + ", is " + is + "!");
        }
        
        return new TarInputStream(
                pin, TarBuffer.DEFAULT_BLKSIZE, TarBuffer.DEFAULT_RCDSIZE);
    }

    /**
     * Maps entry names to tar entries [String -> TarEntry].
     */
    private final Map entries = new HashMap();

    public int getNumArchiveEntries() {
        return entries.size();
    }

    public Enumeration getArchiveEntries() {
        return Collections.enumeration(entries.values());
    }

    public ArchiveEntry getArchiveEntry(String name) {
        return (TarEntry) entries.get(name);
    }

    public InputStream getInputStream(
            final ArchiveEntry entry,
            final ArchiveEntry dstEntry)
    throws IOException {
        return new FileInputStream(((TarEntry) entry).getFile());
    }

    public void close() throws IOException {
        final Enumeration e = getArchiveEntries();
        while (e.hasMoreElements()) {
            final TarEntry entry = (TarEntry) e.nextElement();
            final File file = entry.getFile();
            if (file.exists() && !file.delete()) {
                // Windoze: This entry file is still open for reading.
                file.deleteOnExit();
            }
        }
    }

    //
    // Metadata stuff.
    //

    private InputArchiveMetaData metaData;

    public InputArchiveMetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(InputArchiveMetaData metaData) {
        this.metaData = metaData;
    }
}
