package com.zipeg;

import sun.nio.ch.DirectBuffer;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** MappedByteBuffer to InputStream adapter.
 * not a very good idea because of numerious unmap issues...
 * Better not to use unless absolutely necessary.
 */
public final class MappedInputStream extends InputStream {

    private MappedByteBuffer bb;

    public MappedInputStream(File file) throws IOException {
        RandomAccessFile raf = null;
        FileChannel channel = null;
        try {
            raf = new RandomAccessFile(file, "r");
            if (raf.length() >= 2L * Util.GB) {
                throw new IOException("files bigger than 2GB are not supported");
            }
            channel = raf.getChannel();
            bb = channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
            raf.close();
            raf = null;
            channel.close(); // already closed by raf, but better be safe than sorry
        } finally {
            if (channel != null) {
                try { channel.close();  } catch (IOException iox) { /* ignore */ }
            }
            if (raf != null) {
                try { raf.close(); } catch (IOException iox) { /* ignore */ }
            }
        }
    }

    public int available() {
        return bb.remaining();
    }

    public void close() {
        sun.misc.Cleaner cleaner = null;
        if (bb instanceof DirectBuffer) {
            cleaner = ((DirectBuffer)bb).cleaner();
        }
        bb = null;
        // see: http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=6417205
        //      http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4724038
        if (cleaner != null) {
            cleaner.clean();
        }
        System.gc();
        System.runFinalization();
    }

    public void mark(int readlimit) {
        bb.mark();
    }

    public boolean markSupported() {
        return true;
    }

    public int read() {
        return bb.get();
    }

    public int read(byte[] b) {
        bb.get(b);
        return b.length;
    }

    public int read(byte[] b, int off, int len) {
        len = Math.min(len, bb.remaining());
        if (len > 0) {
            bb.get(b, off, len);
        }
        return len;
    }

    public void reset() {
        bb.reset();
    }

    public long skip(long n) throws IOException {
        if (n + bb.position() > Integer.MAX_VALUE) {
            throw new IOException("2GB limit");
        }
        return bb.position((int)(bb.position() + n)).remaining();
    }

}
