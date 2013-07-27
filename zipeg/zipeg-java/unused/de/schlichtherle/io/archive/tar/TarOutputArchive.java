/*
 * TarOutputArchive.java
 *
 * Created on 28. Februar 2006, 20:17
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

import de.schlichtherle.io.ChainableIOException;
import de.schlichtherle.io.InputIOException;
import de.schlichtherle.io.OutputArchiveMetaData;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.OutputArchive;
import de.schlichtherle.io.archive.spi.OutputArchiveBusyException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.tools.tar.TarOutputStream;

/**
 * An implementation of {@link OutputArchive} to write TAR archives.
 * Because the TAR file format is so dumb that we need to know each entry's
 * length in advance, we write the entries to a temp file before actually
 * copying them to the underlying TarOutputStream.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class TarOutputArchive
        extends TarOutputStream
        implements OutputArchive {

    public TarOutputArchive(OutputStream out) {
        super(out);
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

    public OutputStream getOutputStream(
            final ArchiveEntry entry,
            final ArchiveEntry srcEntry)
    throws IOException {
        final TarEntry tarEntry = (TarEntry) entry;
        if (srcEntry instanceof TarEntry) {
            tarEntry.setSize(((TarEntry) srcEntry).getSize());
        }

        synchronized (this) {
            return createEntryOutputStream(tarEntry, srcEntry);
        }
    }

    private boolean busy;

    protected boolean busy() {
        return busy;
    }

    protected OutputStream createEntryOutputStream(
            final TarEntry entry,
            final ArchiveEntry srcEntry)
    throws IOException {
        if (srcEntry instanceof TarEntry && !busy()) {
            return new EntryOutputStream(entry);
        } else {
            final File temp = File.createTempFile("tar", null);
            return new EntryTempOutputStream(entry, temp);
        }
    }

    /**
     * This entry output stream writes directly to our subclass.
     * It may be used only if <code>entry</code> holds enough information to
     * write the TAR header and this TarOutputStream is not currently busy
     * writing another entry.
     * This is detected by {@link #getOutputStream} in advance.
     */
    protected class EntryOutputStream extends FilterOutputStream {

        public EntryOutputStream(TarEntry entry)
        throws IOException {
            super(TarOutputArchive.this);
            assert !busy();
            putNextEntry(entry);
            busy = true;
            entries.put(entry.getName(), entry);
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
            assert out == TarOutputArchive.this;
            synchronized (TarOutputArchive.this) {
                busy = false;
                closeEntry();
                storeAllRemainingTempEntries();
            }
        }
    }
    
    /**
     * This entry output stream writes the entry to a temporary file.
     * Upon a call to <code>close()</code>, the temporary file provided to
     * its constructor is then copied to this <code>TarOutputStream</code>
     * and finally deleted.
     */
    protected class EntryTempOutputStream extends FileOutputStream {

        private final TarEntry entry;
        private final File temp;

        public EntryTempOutputStream(
                final TarEntry entry,
                final File temp)
        throws IOException {
            super(temp);
            this.entry = entry;
            this.temp = temp;
            entries.put(entry.getName(), entry);
        }

        public void close() throws IOException {
            super.close();
            synchronized (TarOutputArchive.this) {
                temps.put(entry, temp);
                if (!busy())
                    storeAllRemainingTempEntries();
            }
        }
    }

    private final Map temps = new HashMap();

    private synchronized void storeAllRemainingTempEntries()
    throws IOException {
        ChainableIOException exception = null;

        for (Iterator it = temps.entrySet().iterator(); it.hasNext();) {
            final Map.Entry elem = (Map.Entry) it.next();
            final TarEntry entry = (TarEntry) elem.getKey();
            final File temp = (File) elem.getValue();
            try {
                storeTempEntry(entry, temp);
            } catch (FileNotFoundException failure) {
                // Input exception - let's continue!
                exception = new ChainableIOException(exception, failure);
            } catch (InputIOException failure) {
                // Input exception - let's continue!
                exception = new ChainableIOException(exception, failure);
            } catch (IOException failure) {
                // Something's wrong writing this TarOutputStream!
                throw new ChainableIOException(exception, failure);
            }
            // Remove entry anyway - if something is wrong opening or reading
            // the temp file, there is probably an error in this code and
            // we can't really recover from this situation anyway.
            // Removing this temp ensures that we will not see an exception
            // for the same temp in future again.
            // In addition, storeTempEntry() has removed the temp file anyway.
            it.remove();
        }

        if (exception != null)
            throw exception.sortPriority();
        
        assert temps.isEmpty();
    }

    private synchronized void storeTempEntry(final TarEntry entry, final File temp)
    throws IOException {
        assert !busy();

        try {
            final InputStream in = new FileInputStream(temp);
            try {
                entry.setSize(temp.length());
                putNextEntry(entry);
                try {
                    // Use asynchronous high-performance data pump!
                    de.schlichtherle.io.File.cat(in, this);
                } finally {
                    closeEntry();
                }
            } finally {
                in.close();
            }
        } finally {
            if (!temp.delete()) // may fail on Windoze if in.close() failed!
                temp.deleteOnExit(); // we're bullish never to leavy any temps!
        }
    }

    public synchronized void storeDirectory(ArchiveEntry entry)
    throws IOException {
        putNextEntry((TarEntry) entry);
        entries.put(entry.getName(), entry);
        closeEntry();
    }

    public void close() throws IOException {
        try {
            super.close();
        } finally {
            deleteAllRemainingTempEntries();
        }
    }

    private void deleteAllRemainingTempEntries()
    throws IOException {
        for (Iterator it = temps.entrySet().iterator(); it.hasNext();) {
            final Map.Entry elem = (Map.Entry) it.next();
            final File temp = (File) elem.getValue();
            if (!temp.delete()) // may fail on Windoze if in.close() failed!
                temp.deleteOnExit(); // we're bullish never to leavy any temps!
            it.remove();
        }
        assert temps.isEmpty();
    }

    //
    // Metadata stuff.
    //

    private OutputArchiveMetaData metaData;

    public OutputArchiveMetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(OutputArchiveMetaData metaData) {
        this.metaData = metaData;
    }
}
