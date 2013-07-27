/*
 * InputArchiveMetaData.java
 *
 * Created on 8. März 2006, 12:23
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

package de.schlichtherle.io;

import de.schlichtherle.io.ArchiveBusyWarningException;
import de.schlichtherle.io.ArchiveEntryStreamClosedException;
import de.schlichtherle.io.ArchiveException;
import de.schlichtherle.io.ArchiveWarningException;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.util.ThreadLocalCounter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * <b>This class is <em>not</em> intended for public use!</b>
 * It's only public in order to implement
 * {@link de.schlichtherle.io.archive.spi.ArchiveDriver}s.
 * <p>
 * This class annotates an {@link InputArchive} with the fields and methods
 * required for safe reading of archive entries.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
// TODO: Make this an inner class of InputArchive!
public final class InputArchiveMetaData {
    
    private final InputArchive inArchive;

    /**
     * The cache of all entry streams currently open.
     * This is implemented as a weak hash map where the keys are the streams
     * and the value is (always) this object.
     * This allows the garbage collector to pick up an entry stream if there
     * are no more references to it.
     * This reduces the likeliness of an {@link ArchiveBusyWarningException}
     * in case a client has forgot to close a stream before calling
     * {@link File#update()} or {@link File#umount()}.
     */
    private final Map streams = new WeakHashMap();

    /**
     * A counter for the number of entry streams opened (and not yet closed)
     * by the current thread.
     */
    private final ThreadLocalCounter localThreadStreams = new ThreadLocalCounter();

    private volatile boolean stopped;

    /** Creates a new instance of <code>InputArchiveMetaData</code>. */
    InputArchiveMetaData(final InputArchive inArchive) {
        assert inArchive != null;

        this.inArchive = inArchive;
    }

    InputStream getStream(
            final ArchiveEntry entry,
            final ArchiveEntry dstEntry)
    throws IOException {
        assert entry != null;

        final InputStream in = inArchive.getInputStream(entry, dstEntry);
        if (in != null)
            return new EntryInputStream(in);
        else
            return null;
    }

    /**
     * Returns the number of open entry streams, whereby an open entry stream is
     * an entry stream which's <code>close()</code> method hasn't been called.
     */
    synchronized int getNumStreams() {
        return streams.size();
    }

    /**
     * Waits until all entry streams opened (and not yet closed) by all other
     * threads are closed or a timeout occurs.
     * Streams opened by the current thread are ignored.
     * In addition, if the current thread is interrupted while waiting,
     * this method returns.
     * Furthermore, unless otherwise prevented, another thread could
     * immediately open another stream upon return of this method.
     * So there is actually no guarantee that really <em>all</em> streams
     * are closed upon return of this method - use carefully!
     *
     * @return The number of open streams, <em>including</em> the current thread.
     */
    synchronized int waitAllStreamsByOtherThreads(final long timeout) {
        final long start = System.currentTimeMillis();
        final int localStreams = localThreadStreams.getCounter();
        //Thread.interrupted(); // clear interruption status for the upcoming wait() call
        try {
            while (streams.size() > localStreams) {
                long toWait;
                if (timeout > 0) {
                    toWait = timeout - (System.currentTimeMillis() - start);
                    if (toWait <= 0)
                        break;
                } else {
                    toWait = 0;
                }
                System.gc(); // trigger garbage collection
                System.runFinalization(); // trigger finalizers - is this required at all?
                wait(toWait);
            }
        } catch (InterruptedException ignored) {
        }
        return streams.size();
    }

    /**
     * Closes and disconnects <em>all</em> entry streams for the archive
     * containing this metadata object.
     * <i>Disconnecting</i> means that any subsequent operation on the entry
     * streams will throw an {@link IOException}, with the exception of their
     * <code>close()</code> method.
     */
    synchronized ArchiveException closeAllStreams(
            ArchiveException exceptionChain) {
        for (final Iterator i = streams.keySet().iterator(); i.hasNext(); ) {
            final EntryInputStream in = (EntryInputStream) i.next();
            try {
                in.doClose();
            } catch (IOException failure) {
                exceptionChain = new ArchiveWarningException(
                        exceptionChain, failure);
            }
        }

        stopped = true;
        streams.clear();

        return exceptionChain;
    }

    /**
     * An {@link InputStream} to read the entry data from an
     * {@link InputArchive}.
     * This input stream provides support for finalization and throws an
     * {@link IOException} on any subsequent attempt to read data after
     * {@link #closeAllStreams} has been called.
     */
    private final class EntryInputStream extends FilterInputStream {

        private EntryInputStream(final InputStream in) {
            super(in);
            assert in != null;
            synchronized (InputArchiveMetaData.this) {
                streams.put(this, InputArchiveMetaData.this);
                localThreadStreams.increment();
                InputArchiveMetaData.this.notify(); // there can be only one waiting thread!
            }
        }

        private final void ensureNotStopped() throws IOException {
            if (stopped)
                throw new ArchiveEntryStreamClosedException();
        }

        public int read() throws IOException {
            ensureNotStopped();
            return in.read();
        }

        public int read(byte[] b) throws IOException {
            ensureNotStopped();
            return in.read(b);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            ensureNotStopped();
            return in.read(b, off, len);
        }

        public long skip(long n) throws IOException {
            ensureNotStopped();
            return in.skip(n);
        }

        public int available() throws IOException {
            ensureNotStopped();
            return in.available();
        }

        private volatile boolean closed;

        /**
         * Closes this archive entry input stream and releases any resources
         * associated with it.
         * This method tolerates multiple calls to it and calls
         * {@link #doClose} on the first call only.
         * 
         * @see #doClose
         */
        public final void close() throws IOException {
            if (closed)
                return;
            // Order is important!
            synchronized (InputArchiveMetaData.this) {
                Object removed = streams.remove(this);
                assert removed == InputArchiveMetaData.this;
                localThreadStreams.decrement();
                InputArchiveMetaData.this.notify(); // there can be only one waiting thread!
                doClose();
            }
        }

        /**
         * Closes this archive entry input stream and releases any resources
         * associated with it. 
         * <p>
         * This method tolerates multiple calls to it and calls
         * <code>in.close()</code> on the first call only.
         *
         * @throws IOException If an I/O exception occurs.
         *
         * @see FilterInputStream#close
         */
        private void doClose() throws IOException {
            if (closed)
                return;
            closed = true;
            in.close();
        }

        public void reset() throws IOException {
            ensureNotStopped();
            in.reset();
        }

        /**
         * The finalizer in this class forces this archive entry input stream
         * to close.
         * This is used to ensure that an archive can be updated by the
         * {@link de.schlichtherle.io} package although the client may
         * have "forgot" to close this input stream before.
         */
        protected void finalize() throws IOException {
            doClose();
        }
    } // class EntryInputStream
}
