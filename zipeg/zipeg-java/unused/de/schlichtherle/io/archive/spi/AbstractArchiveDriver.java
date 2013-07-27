/*
 * AbstractArchiveDriver.java
 *
 * Created on 3. April 2006, 19:04
 */

package de.schlichtherle.io.archive.spi;

import de.schlichtherle.io.archive.Archive;
import java.io.CharConversionException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import javax.swing.Icon;

/**
 * An abstract {@link ArchiveDriver} which provides default implementations
 * for character set encodings and icon handling.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public abstract class AbstractArchiveDriver implements ArchiveDriver {

    /**
     * Creates a new instance of AbstractArchiveDriver.
     * This constructor may throw a number of <code>RuntimeException</code>s
     * if the encoding is not properly supported by this JVM.
     */
    protected AbstractArchiveDriver(
            String encoding,
            Icon openIcon,
            Icon closedIcon) {
        this.encoding = encoding;
        this.openIcon = openIcon;
        this.closedIcon = closedIcon;

        tl.get(); // fail fast test
    }

    private final String encoding;

    /**
     * Returns the encoding for the archives read or written by this driver.
     * 
     * @return The character set encoding to use for entry names and probably
     *         other meta data in any archive file read or written by this
     *         driver.
     */
    public final String getEncoding() {
        return encoding;
    }

    /**
     * Returns a {@link CharsetEncoder} suitable for {@link #encoding} when
     * its {@link ThreadLocal#get} method is called.
     * This method may throw a number of <code>RuntimeException</code>s
     * if the encoding is not properly supported by this JVM.
     */
    private final ThreadLocal tl = new ThreadLocal() {
        protected Object initialValue() {
            return Charset.forName(getEncoding()).newEncoder();
        }
    };

    /**
     * Ensures that the given entry name is representable in this driver's
     * character set encoding.
     * Should be called by sub classes in their implementation of the method
     * {@link ArchiveDriver#createArchiveEntry}.
     *
     * @see #getEncoding
     *
     * @throws UndeclaredThrowableException If the character set encoding is
     *         unsupported.
     */
    protected final void ensureEncodable(final String entryName)
    throws CharConversionException {
        final CharsetEncoder encoder = (CharsetEncoder) tl.get();
        if (!encoder.canEncode(entryName))
            throw new CharConversionException(entryName +
                    ": Illegal characters in entry name!");
    }

    private final Icon openIcon;
    
    public Icon getOpenIcon(Archive archive) {
        return openIcon;
    }

    private final Icon closedIcon;
    
    public Icon getClosedIcon(Archive archive) {
        return closedIcon;
    }
}
