/*
 * ReadFullyPushBackInputStream.java
 *
 * Created on 28. Februar 2006, 11:53
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

class ReadAheadInputStream extends PushbackInputStream {
    public ReadAheadInputStream(InputStream in, int size) {
        super(in, size);
    }
    
    public void readFully(final byte[] buf) throws IOException {
        final int l = buf.length;
        int n = 0;
        do  {
            final int r = read(buf, n, l - n);
            if (r == -1)
                throw new EOFException();
            n += r;
        } while (n < l);
    }
}