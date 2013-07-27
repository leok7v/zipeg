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
 * A {@link Zip32Driver} which reads Self Executable (SFX) ZIP32 archives,
 * but doesn't allow you to create or update them.
 * <p>
 * Instances of this class are immutable.
 * 
 * @see CheckedReadOnlySfxDriver
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class ReadOnlySfxDriver extends AbstractSfxDriver {

    /**
     * Equivalent to {@link #ReadOnlySfxDriver(String)
     * ReadOnlySfxDriver(ENCODING)}.
     * This parameter is based on heuristics.
     */
    public ReadOnlySfxDriver() {
        this(ENCODING);
    }

    /**
     * Equivalent to
     * {@link AbstractSfxDriver#AbstractSfxDriver(String, boolean, boolean, Icon, Icon)
     * super(encoding, true, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public ReadOnlySfxDriver(String encoding) {
        super(encoding, true, false, null, null);
    }

    public OutputArchive createOutputArchive(
            final Archive archive,
            final OutputStream out,
            final InputArchive source)
    throws FileNotFoundException {
        throw new FileNotFoundException(
                "This driver does not allow modifying SFX archives in order to prevent breaking potential integrity checks in the SFX code!");
    }
}
