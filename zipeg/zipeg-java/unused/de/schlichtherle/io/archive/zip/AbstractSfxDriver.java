/*
 * AbstractSfxDriver.java
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;

/**
 * A {@link Zip32Driver} which builds Self Executable (SFX) ZIP32 archives.
 * Instances of this class are immutable.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
abstract public class AbstractSfxDriver extends Zip32Driver {

    private static final String CLASS_NAME
            = "de/schlichtherle/io/archive/zip/AbstractSfxDriver".replace('/', '.'); // beware of code obfuscation!

    /**
     * The character set encoding used in SFX archives.
     * This is the platform's default encoding, as returned by
     * {@link System#getProperty(String) System.getProperty("file.encoding")}.
     */
    public static final String ENCODING = System.getProperty("file.encoding");

    static {
        Logger.getLogger(CLASS_NAME, CLASS_NAME).log(Level.CONFIG, "encoding", ENCODING);
    }

    /**
     * Equivalent to {@link Zip32Driver#Zip32Driver(String, boolean, boolean, Icon, Icon)
     * super(ENCODING, true, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public AbstractSfxDriver() {
        super(ENCODING, true, false, null, null);
    }

    /**
     * Equivalent to {@link Zip32Driver#Zip32Driver(String, boolean, boolean, Icon, Icon)
     * super(encoding, true, false, null, null)}.
     * These parameters are based on heuristics.
     */
    public AbstractSfxDriver(String encoding) {
        super(encoding, true, false, null, null);
    }

    /**
     * Equivalent to {@link Zip32Driver#Zip32Driver(String, boolean, boolean, Icon, Icon)
     * super(encoding, preambled, postambled, openIcon, closedIcon)}.
     */
    public AbstractSfxDriver(
            String encoding,
            boolean preambled,
            boolean postambled,
            Icon openIcon,
            Icon closedIcon) {
        super(encoding, preambled, postambled, openIcon, closedIcon);
    }

    abstract public OutputArchive createOutputArchive(
            final Archive archive,
            final OutputStream out,
            final InputArchive source)
    throws UnsupportedEncodingException, IOException;
}
