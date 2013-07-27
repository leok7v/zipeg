/*
 * ChannelReadOnlyFile.java
 *
 * Created on 17. Dezember 2005, 18:36
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

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A {@link ReadOnlyFile} using channels.
 *
 * @author Christian Schlichtherle
 */
public class ChannelReadOnlyFile implements ReadOnlyFile {
    
    protected final FileChannel channel;
    
    public ChannelReadOnlyFile(File file) throws FileNotFoundException {
        channel = new RandomAccessFile(file, "r").getChannel();
    }

    public long length() throws IOException {
        return channel.size();
    }

    public long getFilePointer() throws IOException {
        return channel.position();
    }

    public void seek(final long pos) throws IOException {
        channel.position(pos);
    }

    /** For use by {@link #read()} only! */
    private final ByteBuffer singleByteBuffer = ByteBuffer.allocate(1);
    
    public int read() throws IOException {
        if (channel.read(singleByteBuffer) == 1)
            return singleByteBuffer.array()[0] & 0xFF;
        else
            return -1;
    }

    public final int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(final byte[] buf, final int off, final int len)
    throws IOException {
        return channel.read(ByteBuffer.wrap(buf, off, len));
    }

    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    public void readFully(byte[] buf, int off, int len) throws IOException {
        int n = 0;
	do {
	    int count = read(buf, off + n, len - n);
	    if (count < 0)
		throw new EOFException();
	    n += count;
	} while (n < len);
    }

    public int skipBytes(int n) throws IOException {
        if (n <= 0)
            return 0; // for compatibility to RandomAccessFile in case of closed

	final long pos = channel.position();
	final long len = channel.size();
	long newPos = pos + n;
	if (newPos > len)
	    newPos = len;
	channel.position(newPos);

	return (int) (newPos - pos);
    }
    
    public void close() throws IOException {
        channel.close();
    }
}
