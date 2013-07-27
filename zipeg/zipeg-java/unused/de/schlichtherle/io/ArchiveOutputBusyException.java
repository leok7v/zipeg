/*
 * ArchiveOutputBusyException.java
 *
 * Created on 5. Oktober 2006, 17:12
 */

package de.schlichtherle.io;

/**
 * Like its super class, but signals the existance of open output streams.
 *
 * @author Christian Schlichtherle
 */
public class ArchiveOutputBusyException extends ArchiveBusyException {

    private final int numStreams;

    public ArchiveOutputBusyException(
            ArchiveException priorException, String canPath, int numStreams) {
        super(priorException, canPath);
        this.numStreams = numStreams;
    }

    /**
     * Returns the number of open entry output streams, whereby an open stream
     * is a stream which's <code>close()</code> method hasn't been called.
     */
    public int getNumStreams() {
        return numStreams;
    }
}
