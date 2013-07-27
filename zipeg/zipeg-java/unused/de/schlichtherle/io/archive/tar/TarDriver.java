/*
 * TarDriver.java
 *
 * Created on 28. Februar 2006, 17:48
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

import de.schlichtherle.io.archive.Archive;
import de.schlichtherle.io.archive.spi.AbstractArchiveDriver;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.ArchiveDriver;
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.io.archive.spi.OutputArchive;
import de.schlichtherle.io.rof.ReadOnlyFile;
import de.schlichtherle.io.rof.ReadOnlyFileInputStream;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;

/**
 * An {@link ArchiveDriver} which builds TAR archives.
 * Instances of this class are immutable.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class TarDriver extends AbstractArchiveDriver {

    /**
     * The character set encoding used by TAR archives, which is
     * <code>US-ASCII</code>.
     * TARs should actually be able to use the system's native character set
     * encoding. However, the low level TAR code as of Ant 1.6.5 doesn't
     * support that, hence this constraint.
     */
    public static final String ENCODING = "US-ASCII";

    /**
     * Equivalent to {@link #TarDriver(String, Icon, Icon)
     * this(ENCODING, null, null)}.
     * These parameters are based on heuristics.
     */
    public TarDriver() {
        super(ENCODING, null, null);
    }

    /**
     * Equivalent to {@link #TarDriver(String, Icon, Icon)
     * this(encoding, null, null)}.
     * These parameters are based on heuristics.
     * <p>
     * Warning: The encoding parameter is currently not yet supported by this
     * driver!
     */
    public TarDriver(String encoding) {
        super(encoding, null, null);
    }

    /**
     * Constructs a new TAR driver with the given parameters.
     * <p>
     * Warning: The encoding parameter is currently not yet supported by this
     * driver!
     *
     * @param encoding The character set encoding to use for entry names and
     *        comments in any archive file read or written by this driver.
     * @param openIcon The icon which should be displayed if an archive of
     *        this type is in the "open" state in a
     *        {@link de.schlichtherle.io.swing.JFileChooser} or
     *        {@link de.schlichtherle.io.swing.JFileTree}.
     * @param closedIcon The icon which should be displayed if an archive of
     *        this type is in the "closed" state in a
     *        {@link de.schlichtherle.io.swing.JFileChooser} or
     *        {@link de.schlichtherle.io.swing.JFileTree}.
     *
     * @see #TarDriver()
     */
    public TarDriver(
            String encoding,
            Icon openIcon,
            Icon closedIcon) {
        super(encoding, openIcon, closedIcon);
    }

    //
    // Factory methods:
    //

    public ArchiveEntry createArchiveEntry(
            final Archive archive,
            final String entryName,
            final ArchiveEntry blueprint)
    throws CharConversionException {
        ensureEncodable(entryName);

        final TarEntry entry;
        if (blueprint != null) {
            if (blueprint instanceof TarEntry) {
                entry = new TarEntry(entryName, (TarEntry) blueprint);
            } else {
                entry = new TarEntry(entryName);
                entry.setTime(blueprint.getTime());
            }
        } else {
            entry = new TarEntry(entryName);
        }

        return entry;
    }

    public InputArchive createInputArchive(
            final Archive archive,
            final ReadOnlyFile rof)
    throws IOException {
        final InputStream in = new ReadOnlyFileInputStream(rof);
        try {
            return createInputArchive(archive, in);
        } finally {
            in.close();
        }
    }

    // May be overwritten by subclasses to decorate the input stream before
    // presenting it to the TarInputArchive.
    public InputArchive createInputArchive(
            final Archive archive,
            final InputStream in)
    throws IOException {
        return new TarInputArchive(archive, in);
    }

    public OutputArchive createOutputArchive(
            final Archive archive,
            final OutputStream out,
            final InputArchive source)
    throws IOException {
        return new TarOutputArchive(out);
    }
}
