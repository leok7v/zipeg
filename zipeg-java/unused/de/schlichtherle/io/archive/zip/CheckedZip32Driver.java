/*
 * CheckedZip32Driver.java
 *
 * Created on 29. Juni 2006, 20:58
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

import de.schlichtherle.io.rof.ReadOnlyFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.ZipException;

import javax.swing.Icon;

/**
 * A {@link Zip32Driver} which checks the CRC-32 values for all ZIP entries
 * in input archives.
 * The additional CRC-32 computation also disables the Direct Data Copying
 * (DDC) feature, which makes this class comparably slow.
 * <p>
 * If there is a mismatch of the CRC-32 values for a ZIP entry in an input
 * archive, the <code>close()</code> method of the corresponding input stream
 * for the archive entry will throw a
 * {@link de.schlichtherle.util.zip.CRC32Exception}.
 * This exception is then propagated through to the corresponding file
 * operation in the package <code>de.schlichtherle.io</code> where it is
 * either allowed to pass on or is catched and processed accordingly.
 * For example, the {@link de.schlichtherle.io.FileInputStream#close()}
 * method passes the <code>CRC32Exception</code> on to the client
 * application, whereas the
 * {@link de.schlichtherle.io.File#catTo(OutputStream)} method returns
 * <code>false</code> (as with most methods in the <code>File</code> class).
 * Other than this, the archive entry will be processed normally.
 * So if just the CRC-32 value for the entry in the archive file has been
 * modified, you can still read its contents.
 * <p>
 * Instances of this class are immutable.
 * 
 * @see Zip32Driver
 * @see CheckedZip32InputArchive
 * @see CheckedZip32OutputArchive
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.1
 */
public class CheckedZip32Driver extends Zip32Driver {

    /**
     * Equivalent to {@link #CheckedZip32Driver(String, boolean, boolean, Icon, Icon)
     * this("CP437", false, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public CheckedZip32Driver() {
        this("CP437", false, false, null, null);
    }

    /**
     * Equivalent to {@link #CheckedZip32Driver(String, boolean, boolean, Icon, Icon)
     * this(encoding, false, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public CheckedZip32Driver(String encoding) {
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
     * @see #CheckedZip32Driver()
     */
    public CheckedZip32Driver(
            String encoding,
            boolean preambled,
            boolean postambled,
            Icon openIcon,
            Icon closedIcon) {
        super(encoding, preambled, postambled, openIcon, closedIcon);
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
        return new CheckedZip32InputArchive(rof, encoding, preambled, postambled);
    }

    protected Zip32OutputArchive createZip32OutputArchive(
            final OutputStream out,
            final String encoding,
            final Zip32InputArchive source)
    throws  NullPointerException,
            UnsupportedEncodingException,
            IOException {
        return new CheckedZip32OutputArchive(out, encoding, source);
    }
}
