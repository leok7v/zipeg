/*
 * TarGZipDriver.java
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

package de.schlichtherle.io.archive.tar;

import de.schlichtherle.io.archive.Archive;
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.io.archive.spi.OutputArchive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A {@link TarDriver} which builds GZIP compressed TAR archives.
 * Instances of this class are immutable.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class TarGZipDriver extends TarDriver {

    private static final int BUFSIZE = 4096;

    /**
     * Equivalent to {@link TarDriver#TarDriver(String, Icon, Icon)
     * super(ENCODING, null, null)}.
     * These parameters are based on heuristics.
     */
    public TarGZipDriver() {
        super(ENCODING, null, null);
    }

    /**
     * Equivalent to {@link TarDriver#TarDriver(String, Icon, Icon)
     * super(encoding, null, null)}.
     * These parameters are based on heuristics.
     * <p>
     * Warning: The encoding parameter is currently not yet supported by this
     * driver!
     */
    public TarGZipDriver(String encoding) {
        super(encoding, null, null);
    }

    //
    // Driver implementation:
    //

    public InputArchive createInputArchive(
            final Archive archive,
            final InputStream in)
    throws IOException {
        return super.createInputArchive(
                archive, new GZIPInputStream(in, BUFSIZE));
    }

    public OutputArchive createOutputArchive(
            final Archive archive,
            final OutputStream out,
            final InputArchive source)
    throws IOException {
        return super.createOutputArchive(
                archive, new GZIPOutputStream(out, BUFSIZE), source);
    }
}
