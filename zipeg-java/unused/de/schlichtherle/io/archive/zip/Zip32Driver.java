/*
 * Zip32Driver.java
 *
 * Created on 24. Dezember 2005, 00:01
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

import de.schlichtherle.io.archive.Archive;
import de.schlichtherle.io.archive.spi.AbstractArchiveDriver;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.ArchiveDriver;
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.io.archive.spi.OutputArchive;
import de.schlichtherle.io.rof.ReadOnlyFile;

import java.io.CharConversionException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.ZipException;

import javax.swing.Icon;

/**
 * An {@link ArchiveDriver} which builds ZIP32 archives.
 * Note that this driver does not check the CRC value of any entries in
 * existing archives.
 * <p>
 * Instances of this class are immutable.
 *
 * @see CheckedZip32Driver
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class Zip32Driver extends AbstractArchiveDriver {

    /**
     * Equivalent to {@link #Zip32Driver(String, boolean, boolean, Icon, Icon)
     * this("CP437", false, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public Zip32Driver() {
        this("CP437", false, false, null, null);
    }

    /**
     * Equivalent to {@link #Zip32Driver(String, boolean, boolean, Icon, Icon)
     * this(encoding, false, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public Zip32Driver(String encoding) {
        this(encoding, false, false, null, null);
    }

    /**
     * @param encoding The character set encoding to use for entry names and
     *        comments in any archive file read or written by this driver.
     * @param preambled <code>true</code> if and only if a prospective ZIP
     *        compatible file is allowed to contain preamble data before the
     *        actual ZIP file data.
     *        Self Extracting Archives use this feature in order to store the
     *        application code that is required to extract the ZIP file contents.
     *        <p>
     *        Please note that any ZIP compatible file may actually have a
     *        preamble. However, for performance reasons this parameter should
     *        be set to <code>false</code>, unless required.
     * @param postambled <code>true</code> if and only if a prospective ZIP
     *        compatible file is allowed to have a postamble of arbitrary
     *        length.
     *        If set to <code>false</code>, the ZIP compatible file may still
     *        have a postamble. However, the postamble must not exceed 64KB
     *        size, including the End Of Central Directory record and thus
     *        the ZIP file comment. This causes the initial ZIP file
     *        compatibility test to fail fast if the file is not compatible
     *        to the ZIP File Format Specification.
     *        For performance reasons, this parameter should be set to
     *        <code>false</code> unless you need to support Self Extracting
     *        Archives with very large postambles.
     * @param openIcon The icon which should be displayed if an archive of
     *        this type is in the "open" state in a
     *        {@link de.schlichtherle.io.swing.JFileChooser} or
     *        {@link de.schlichtherle.io.swing.JFileTree}.
     * @param closedIcon The icon which should be displayed if an archive of
     *        this type is in the "closed" state in a
     *        {@link de.schlichtherle.io.swing.JFileChooser} or
     *        {@link de.schlichtherle.io.swing.JFileTree}.
     *
     * @see #Zip32Driver()
     */
    public Zip32Driver(
            String encoding,
            boolean preambled,
            boolean postambled,
            Icon openIcon,
            Icon closedIcon) {
        super(encoding, openIcon, closedIcon);

        this.preambled = preambled;
        this.postambled = postambled;
    }

    //
    // Properties:
    //

    private final boolean preambled;
    
    /**
     * Returns <code>true</code> if and only if a prospective ZIP compatible
     * file is allowed to contain preamble data before the actual ZIP file data.
     * Self Extracting Archives use this feature in order to store the
     * application code that is required to extract the ZIP file contents.
     */
    public boolean getPreambled() {
        return preambled;
    }

    private final boolean postambled;

    /**
     * Returns <code>true</code> if and only if a prospective ZIP compatible
     * file is allowed to have a postamble of arbitrary length.
     * If set to <code>false</code>, the ZIP compatible file may still have
     * a postamble. However, the postamble must not exceed 64KB size, including
     * the End Of Central Directory record and thus the ZIP file comment.
     * This causes the initial ZIP file compatibility test to fail fast if
     * the file is not compatible to the ZIP File Format Specification.
     * For performance reasons, this parameter should be set to
     * <code>false</code> unless you need to support Self Extracting Archives
     * with very large postambles.
     */
    public boolean getPostambled() {
        return postambled;
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

        final Zip32Entry entry;
        if (blueprint != null) {
            if (blueprint instanceof Zip32Entry) {
                entry = new Zip32Entry(entryName, (Zip32Entry) blueprint);
            } else {
                entry = new Zip32Entry(entryName);
                entry.setTime(blueprint.getTime());
            }
        } else {
            entry = new Zip32Entry(entryName);
        }
        
        return entry;
    }

    public InputArchive createInputArchive(Archive archive, ReadOnlyFile rof)
    throws UnsupportedEncodingException, FileNotFoundException, IOException {
        return createZip32InputArchive(
                rof, getEncoding(), getPreambled(), getPostambled());
    }

    protected Zip32InputArchive createZip32InputArchive(
            ReadOnlyFile rof,
            String encoding,
            boolean preambled,
            boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        return new Zip32InputArchive(rof, encoding, preambled, postambled);
    }

    public OutputArchive createOutputArchive(
            final Archive archive,
            final OutputStream out,
            final InputArchive source)
    throws UnsupportedEncodingException, IOException {
        return createZip32OutputArchive(
                out, getEncoding(), (Zip32InputArchive) source);
    }

    protected Zip32OutputArchive createZip32OutputArchive(
            final OutputStream out,
            final String encoding,
            final Zip32InputArchive source)
    throws  NullPointerException,
            UnsupportedEncodingException,
            IOException {
        return new Zip32OutputArchive(out, encoding, source);
    }
}
