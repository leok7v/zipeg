/*
 * ArchiveEntryStreamClosedException.java
 *
 * Created on 8. März 2006, 21:07
 */

package de.schlichtherle.io;


import java.io.IOException;

/**
 * Thrown to indicate that an input or output stream for an archive entry
 * has been forcibly closed by {@link File#update()} or {@link File#umount()}.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class ArchiveEntryStreamClosedException extends IOException {
    
    public ArchiveEntryStreamClosedException() {
        super("This archive entry stream has been forced to close() when File.update() or File.umount() has been called, which disables all I/O to it!");
    }
}
