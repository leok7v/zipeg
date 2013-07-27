/*
 * ArchiveInputBusyException.java
 *
 * Created on 5. Oktober 2006, 17:12
 */

package de.schlichtherle.io;

/**
 * Like its super class, but signals the existance of open input streams.
 *
 * @author Christian Schlichtherle
 */
public class ArchiveInputBusyWarningException extends ArchiveBusyWarningException {

    private final int numStreams;

    public ArchiveInputBusyWarningException(
            ArchiveException priorException, String canPath, int numStreams) {
        super(priorException, canPath);
        this.numStreams = numStreams;
    }

    /**
     * Returns the number of open entry input streams, whereby an open stream
     * is a stream which's <code>close()</code> method hasn't been called.
     */
    public int getNumStreams() {
        return numStreams;
    }
}
