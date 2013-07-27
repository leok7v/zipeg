/*
 * ReadOnlySfxDriver.java
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
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.io.archive.spi.OutputArchive;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link Zip32Driver} which reads and writes Self Executable (SFX) ZIP32
 * archives.
 * <p>
 * <b>Warning:</b> Modifying SFX archives usually voids the SFX code in the
 * preamble!
 * This is because most SFX implementations do not tolerate the contents of
 * the archive to be modified (by intention or accident).
 * When executing the SFX code of a modified archive, anything may happen:
 * The SFX code may be terminating with an error message, crash, silently
 * produce corrupted data, or even something more evil.
 * However, an archive modified with this driver is still a valid ZIP file.
 * So you may still extract the modified archive using a regular ZIP utility.
 * <p>
 * Instances of this class are immutable.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class ReadWriteSfxDriver extends AbstractSfxDriver {

    /**
     * Equivalent to {@link AbstractSfxDriver#AbstractSfxDriver(String, boolean, boolean, Icon, Icon)
     * super(ENCODING, true, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public ReadWriteSfxDriver() {
        super(ENCODING, true, false, null, null);
    }

    /**
     * Equivalent to {@link AbstractSfxDriver#AbstractSfxDriver(String, boolean, boolean, Icon, Icon)
     * super(encoding, true, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public ReadWriteSfxDriver(String encoding) {
        super(encoding, true, false, null, null);
    }

    public OutputArchive createOutputArchive(
            final Archive archive,
            final OutputStream out,
            final InputArchive source)
    throws UnsupportedEncodingException, IOException {
        return createZip32OutputArchive(
                out, getEncoding(), (Zip32InputArchive) source);
    }
}
