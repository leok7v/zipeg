/*
 * ReadOnlyFile.java
 *
 * Created on 5. Oktober 2005, 17:14
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

/**
 * A minimal interface to allow random read only access to a file.
 * This interface is required by the class {@link java.util.zip.ZipFile} to
 * read a ZIP compatible file which may or may not be encrypted.
 *
 * @author Christian Schlichtherle
 */
public interface ReadOnlyFile {

    long length() throws IOException;

    long getFilePointer() throws IOException;

    /**
     * Sets the file pointer offset, measured from the beginning of this 
     * file, at which the next read occurs.
     * Whether the offset may be set beyond the end of the file is up to
     * the implementor. For example, {@link java.io.RandomAccessFile}
     * (which can be simply subclassed to implement this interface) does
     * allow this, even if the file is opened read-only.
     * However, this is not very meaningful on a read only file and thus
     * not recommended.
     *
     * @param pos The offset position, measured in bytes from the beginning
     *        of the file, at which to set the file pointer.
     * @throws IOException If <tt>pos</tt> is less than <tt>0</tt> or if
     *         an I/O error occurs.
     */
    void seek(long pos) throws IOException;

    int read() throws IOException;

    int read(byte[] b) throws IOException;

    /**
     * Reads up to <tt>len</tt> bytes of data from this read only file into
     * the given array.
     * This method blocks until at least one byte of input is available.
     *
     * @param b The buffer to fill with data.
     * @param off The start offset of the data.
     * @param len The maximum number of bytes to read.
     *
     * @return The total number of bytes read, or <tt>-1</tt> if there is
     *         no more data because the end of the file has been reached.
     *
     * @throws IOException On any I/O related issue.
     */
    int read(byte[] b, int off, int len) throws IOException;

    void readFully(byte[] b) throws IOException;

    void readFully(byte[] b, int off, int len) throws IOException;

    int skipBytes(int n) throws IOException;

    void close() throws IOException;
}
