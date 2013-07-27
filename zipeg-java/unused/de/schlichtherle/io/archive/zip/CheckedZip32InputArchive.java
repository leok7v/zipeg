/*
 * CheckedZip32InputArchive.java
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

import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.rof.ReadOnlyFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.ZipException;

/**
 * A {@link Zip32InputArchive} which checks the CRC-32 value for all ZIP entries.
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
 * 
 * @see Zip32InputArchive
 * @see CheckedZip32OutputArchive
 * @see CheckedZip32Driver
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.1
 */
public class CheckedZip32InputArchive extends Zip32InputArchive {
    
    public CheckedZip32InputArchive(
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

    /**
     * Overwritten to read from a checked input stream.
     * The returned input stream <em>always</em> provides inflated data and
     * checks the CRC32 checksum on its <code>close()</code> method.
     */
    public InputStream getInputStream(
            ArchiveEntry entry,
            ArchiveEntry dstEntry)
    throws  IOException {
        return super.getCheckedInputStream((Zip32Entry) entry);
    }
}