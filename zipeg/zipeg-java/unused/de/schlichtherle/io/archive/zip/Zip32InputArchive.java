/*
 * Zip32InputArchive.java
 *
 * Created on 27. Februar 2006, 09:12
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

import de.schlichtherle.io.InputArchiveMetaData;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.io.rof.ReadOnlyFile;
import de.schlichtherle.io.util.Path;
import de.schlichtherle.util.zip.ZipEntry;
import de.schlichtherle.util.zip.ZipFile;
import java.io.FileNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.zip.ZipException;

/**
 * An implementation of {@link InputArchive} to read ZIP32 archives.
 *
 * @see Zip32Driver
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class Zip32InputArchive extends ZipFile implements InputArchive {
    
    public Zip32InputArchive(
            ReadOnlyFile rof,
            String encoding,
            boolean preambled,
            boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        super(rof, encoding, preambled, postambled);
    }

    protected ZipEntry createZipEntry(String name) {
        return new Zip32Entry(Path.normalize(name, '/'));
    }

    public int getNumArchiveEntries() {
        return super.size();
    }

    public Enumeration getArchiveEntries() {
        return super.entries();
    }

    public ArchiveEntry getArchiveEntry(final String name) {
        return (Zip32Entry) super.getEntry(name);
    }

    public InputStream getInputStream(
            final ArchiveEntry entry,
            final ArchiveEntry dstEntry)
    throws IOException {
        return super.getInputStream(
                (Zip32Entry) entry, !(dstEntry instanceof Zip32Entry));
    }

    //
    // Metadata implementation.
    //

    private InputArchiveMetaData metaData;

    public InputArchiveMetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(InputArchiveMetaData metaData) {
        this.metaData = metaData;
    }
}
