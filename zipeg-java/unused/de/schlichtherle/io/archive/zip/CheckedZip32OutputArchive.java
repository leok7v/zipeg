/*
 * CheckedZip32OutputArchive.java
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
import de.schlichtherle.io.archive.zip.Zip32OutputArchive.EntryOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * A {@link Zip32OutputArchive} which must be used in conjunction with
 * {@link CheckedZip32InputArchive}.
 * This class the Direct Data Copying (DDC) feature disabled, which makes
 * it comparably slow.
 * However, it must be used in an archive driver whenever
 * <code>CheckedZip32InputArchive</code> is used too,
 * in order to ensure that ZIP entries can be properly copied from one
 * ZIP file to another.
 * 
 * @see Zip32OutputArchive
 * @see CheckedZip32InputArchive
 * @see CheckedZip32Driver
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.1
 */
public class CheckedZip32OutputArchive extends Zip32OutputArchive {

    public CheckedZip32OutputArchive(
            OutputStream out,
            String encoding,
            Zip32InputArchive source)
    throws  NullPointerException,
            UnsupportedEncodingException,
            IOException {
        super(out, encoding, source);
    }

    /**
     * Overwritten so that the provided data is <em>always</em>
     * deflated in order to match the data provided by streams created
     * from the {@link CheckedZip32InputArchive} class.
     */
    protected OutputStream createEntryOutputStream(
            Zip32Entry entry,
            ArchiveEntry srcEntry)
    throws  IOException {
        return new EntryOutputStream(entry, true);
    }
}