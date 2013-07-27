/*
 * ArchiveDriver.java
 *
 * Created on 25. Februar 2006, 16:59
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

package de.schlichtherle.io.archive.spi;

import de.schlichtherle.io.File;
import de.schlichtherle.io.FileInputStream;
import de.schlichtherle.io.FileOutputStream;
import de.schlichtherle.io.archive.Archive;
import de.schlichtherle.io.rof.ReadOnlyFile;

import java.io.CharConversionException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import javax.swing.Icon;

/**
 * This "driver" interface is an Abstract Factory used to build archives of
 * a specific type, such as ZIP32, ZIP32.RAES, JAR, TAR, TAR.GZ, TAR.BZ2
 * or the like.
 * Archive drivers are used by the non-public class
 * <code>de.schlichtherle.io.ArchiveController</code> as the Factory side of
 * a Builder pattern and may be shared by multiple archive controllers and
 * instances of the {@link de.schlichtherle.io.ArchiveDetector} interface.
 * <p>
 * The following requirements must be met by any implementation:
 * <ul>
 * <li>
 * Implementations must not assume that they are used as singletons:
 * Multiple instances of an implementation may be used for the same archive
 * type.
 * <li>
 * Implementations must be immutable to avoid all kinds of surprising
 * effects in the behaviour of the {@link de.schlichtherle.io.File} class,
 * including strange exceptions.
 * <li>
 * If the driver should be supported by the driver registration process of
 * the class {@link de.schlichtherle.io.DefaultArchiveDetector},
 * a no-arguments constructor must be provided.
 * This constructor should be fast to process.
 * <li>
 * Implementations should be lightweight in terms of memory.
 * <li>
 * Implementations do <em>not</em> need to be thread safe.
 * </ul>
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public interface ArchiveDriver {
    
    /**
     * Returns the icon that {@link de.schlichtherle.io.swing.tree.FileTreeCellRenderer}
     * should display for the given archive.
     *
     * @param archive The abstract archive representation which TrueZIP's
     *        internal <code>ArchiveController</code> is processing
     *        - never <code>null</code>.
     *
     * @return The icon that should be displayed for the given archive if is
     *         is open/expanded in the view.
     *         If <code>null</code> is returned, a default icon should be used.
     */
    Icon getOpenIcon(Archive archive);

    /**
     * Returns the icon that {@link de.schlichtherle.io.swing.FileSystemView}
     * and {@link de.schlichtherle.io.swing.tree.FileTreeCellRenderer} should
     * display for the given archive.
     *
     * @param archive The abstract archive representation which TrueZIP's
     *        internal <code>ArchiveController</code> is processing
     *        - never <code>null</code>.
     *
     * @return The icon that should be displayed for the given archive if is
     *         is closed/collapsed in the view.
     *         If <code>null</code> is returned, a default icon should be used.
     */
    Icon getClosedIcon(Archive archive);

    /**
     * Creates an {@link InputArchive} object for <code>cPathname</code>
     * from the given {@link ReadOnlyFile} instance.
     * <p>
     * Note that if an exception is thrown, the method must be reentrant!
     * In addition, the exception type determines the behaviour of the classes
     * {@link File}, {@link FileInputStream} and {@link FileOutputStream}
     * as follows:
     * <ul>
     * <li>If a {@link FileNotFoundException} is thrown, then
     *     {@link File#isFile} returns <b><code>false</code></b>,
     *     {@link File#isArchive} returns <code>true</code>,
     *     {@link File#isDirectory} returns <code>false</code>,
     *     {@link File#exists} returns <code>true</code> and
     *     {@link File#delete} returns <code>true</code> unless prohibited
     *     by the native file system.
     * <li>If an {@link IOException} is thrown, then
     *     {@link File#isFile} returns <b><code>true</code></b>,
     *     {@link File#isArchive} returns <code>true</code>,
     *     {@link File#isDirectory} returns <code>false</code>,
     *     {@link File#exists} returns <code>true</code> and
     *     {@link File#delete} returns <code>true</code> unless prohibited
     *     by the native file system.
     * </ul>
     * 
     * @param archive The abstract archive representation which TrueZIP's
     *        internal <code>ArchiveController</code> is processing
     *        - never <code>null</code>.
     * @param rof The {@link ReadOnlyFile} to read the actual archive
     *        contents from - never <code>null</code>.
     *        Hint: If you'ld prefer to have an <code>InputStream</code>,
     *        you could decorate this parameter with a
     *        {@link de.schlichtherle.io.rof.ReadOnlyFileInputStream}.
     *
     * @return A newly created {@link InputArchive}.
     *
     * @throws FileNotFoundException If the input archive is inaccessible
     *         for any reason and you would like the package
     *         {@link de.schlichtherle.io} to mask the archive as a special
     *         file which cannot get read, written or deleted.
     * @throws IOException On any other I/O or data format related issue
     *         and you would like the package {@link de.schlichtherle.io}
     *         to treat the archive like a regular file which may be read,
     *         written or deleted.
     *
     * @see InputArchive
     */
    InputArchive createInputArchive(
            Archive archive,
            ReadOnlyFile rof)
    throws FileNotFoundException, IOException;

    /**
     * Creates a new {@link ArchiveEntry} instance for <code>name</code> which
     * may be used within an {@link OutputArchive}.
     * 
     * @param archive The abstract archive representation which TrueZIP's
     *        internal <code>ArchiveController</code> is processing
     *        - never <code>null</code>.
     * @param entryName The full path name of the entry to create
     *        - never <code>null</code>.
     * @param blueprint If not <code>null</code>, then the newly created entry
     *        shall inherit as much attributes from this object as possible
     *        (with the exception of the name).
     *        This is typically used for archive copy operations.
     *        Note that there is no guarantee on the runtime type of this
     *        object; it may have been created by other drivers.
     *        It is safe to ignore the <code>metaData</code> property when
     *        copying entries.
     *
     * @return A newly created <code>ArchiveEntry</code> instance.
     *
     * @throws CharConversionException If <code>name</code> contains
     *         illegal characters.
     *
     * @see ArchiveEntry
     */
    ArchiveEntry createArchiveEntry(
            Archive archive,
            String entryName,
            ArchiveEntry blueprint)
    throws CharConversionException;

    /**
     * Creates an {@link OutputArchive} object for <code>cPathname</code>
     * from the given {@link OutputStream} instance.
     * 
     * @param archive The abstract archive representation which TrueZIP's
     *        internal <code>ArchiveController</code> is processing
     *        - never <code>null</code>.
     * @param out The {@link OutputStream} to write the archive entries to
     *        - never <code>null</code>.
     * @param source The source {@link InputArchive} if
     *        <code>archive</code> is going to get updated.
     *        If not <code>null</code>, this is guaranteed to be a product
     *        of this driver's {@link #createInputArchive} method.
     *        This may be used to copy some meta data which is specific to
     *        the type of archive this driver supports.
     *        For example, this could be used to copy the comment of a ZIP32
     *        archive file.
     *
     * @return A newly created {@link OutputArchive}.
     *
     * @throws IOException On any issue when writing to the output stream.
     *
     * @see OutputArchive
     */
    OutputArchive createOutputArchive(
            Archive archive,
            OutputStream out,
            InputArchive source)
    throws IOException;

    /**
     * Archive drivers will be put into hash maps as keys,
     * so be sure to implement this properly.
     * Note that this is just a reinforcement of the general contract for
     * {@link Object#hashCode} and the best possible implementation is the
     * default implementation in Object which is most discriminating.
     * Normally this 
     *
     * @since TrueZIP 6.4
     */
    int hashCode();

    /**
     * Archive drivers will be put into hash maps as keys,
     * so be sure to implement this properly.
     * Note that this is just a reinforcement of the general contract for
     * {@link Object#equals} and the best possible implementation is the
     * default implementation in Object which is most discriminating.
     *
     * @since TrueZIP 6.4
     */
    boolean equals(Object o);
}
