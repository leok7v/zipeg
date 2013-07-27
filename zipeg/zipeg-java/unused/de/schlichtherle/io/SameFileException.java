/*
 * SameFileException.java
 *
 * Created on 4. November 2006, 12:26
 */

package de.schlichtherle.io;

import java.io.FileNotFoundException;

/**
 * Thrown to indicate that two path names are referring to the same file.
 * This exception is typically thrown from {@link File#cp}.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.3.2
 */
public class SameFileException extends FileNotFoundException {

    private final java.io.File pathA, pathB;

    /**
     * Creates a new instance of <code>SameFileException</code>.
     */
    public SameFileException(
            final java.io.File pathA,
            final java.io.File pathB) {
        super("");
        this.pathA = pathA;
        this.pathB = pathB;
    }

    public java.io.File getPathA() {
        return pathA;
    }

    public java.io.File getPathB() {
        return pathB;
    }
}
