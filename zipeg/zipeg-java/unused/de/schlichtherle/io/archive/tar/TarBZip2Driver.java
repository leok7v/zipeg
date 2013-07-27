/*
 * TarBZip2Driver.java
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

import org.apache.tools.bzip2.CBZip2InputStream;
import org.apache.tools.bzip2.CBZip2OutputStream;

/**
 * A {@link TarDriver} which builds BZIP2 compressed TAR archives.
 * Instances of this class are immutable.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class TarBZip2Driver extends TarDriver {

    /**
     * Equivalent to {@link TarDriver#TarDriver(String, Icon, Icon)
     * super(ENCODING, null, null)}.
     * These parameters are based on heuristics.
     */
    public TarBZip2Driver() {
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
    public TarBZip2Driver(String encoding) {
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
                archive, createVerifiedBZip2InputStream(in));
    }

    /**
     * Returns a newly created and verified {@link CBZip2InputStream}.
     * This method performs a simple verification by computing the checksum
     * for the first record only.
     * This method is required because the <code>CBZip2InputStream</code>
     * unfortunately does not do any verification!
     */
    private static CBZip2InputStream createVerifiedBZip2InputStream(
            final InputStream in)
    throws IOException {
        
        final ReadAheadInputStream pin
                = new ReadAheadInputStream(in, 4);
        final byte[] magic = new byte[4];
        pin.readFully(magic);
        pin.unread(magic, 2, 2);
        if (magic[0] != 'B' || magic[1] != 'Z'
                || magic[2] != 'h' || '1' > magic[3] || magic[3] > '9')
            throw new IOException("Not a BZIP2 compressed input stream!");
        
        return new CBZip2InputStream(pin);
    }

    public OutputArchive createOutputArchive(
            final Archive archive,
            final OutputStream out,
            final InputArchive source)
    throws IOException {
        out.write(new byte[] { 'B', 'Z' });
        return super.createOutputArchive(
                archive, new CBZip2OutputStream(out), source);
    }
}
