/*
 * ReadOnlyFileInputStream.java
 *
 * Created on 12. Dezember 2005, 17:23
 */
/*
 * Copyright 2005 Schlichtherle IT Services
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

package de.schlichtherle.io.rof;

import java.io.IOException;
import java.io.InputStream;

/**
 * A very simple adapter class turning a provided {@link ReadOnlyFile} into
 * an {@link InputStream}.
 *
 * @author Christian Schlichtherle
 */
public class ReadOnlyFileInputStream extends InputStream {

    protected ReadOnlyFile rof;

    public ReadOnlyFileInputStream(ReadOnlyFile rof) {
        this.rof = rof;
    }

    public int read() throws IOException {
        return rof.read();
    }

    public int read(byte[] b) throws IOException {
        return rof.read(b);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return rof.read(b, off, len);
    }

    public long skip(long n) throws IOException {
        if (n > 0x7FFFFFFFl)
            throw new IllegalArgumentException("Too many bytes to skip!");
        return rof.skipBytes((int) n);
    }

    public void close() throws IOException {
        rof.close();
    }
}
