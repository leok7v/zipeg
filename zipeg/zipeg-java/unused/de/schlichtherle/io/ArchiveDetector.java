/*
 * ArchiveDetector.java
 *
 * Created on 31. Juli 2005, 00:00
 */
/*
 * Copyright 2005-2006 Schlichtherle IT Services
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

import de.schlichtherle.io.archive.spi.ArchiveDriver;

import java.io.FileNotFoundException;
import java.net.URI;

/**
 * Detects an archive file solely by looking at the file's pathname -
 * usually by scanning for file name suffixes like <code>".zip"</code> or the
 * like - and provides resources required to access their contents.
 * If a pathname actually denotes an archive file, the file is said to be 
 * a <i>prospective archive file</i>. Later on, TrueZIP will try to access the
 * file via an appropriate archive driver, which is an instance of the
 * {@link ArchiveDriver} interface.
 * The archive driver then checks the file contents for compatibility to the
 * respective archive file format.
 * If the file is actually a directory or not compatible to the archive file
 * format, it is said to be a <i>false positive archive file</i>.
 * With the support of the archive driver, TrueZIP always detects and handles
 * all kinds of false positives correctly.
 * <p>
 * Implementations of this interface must be immutable.
 * Otherwise, an application of the <code>File*</code> classes in this package
 * may get lots of bad surprises!
 * <p>
 * Rather than implementing your own <code>ArchiveDetector</code>, you could
 * instantiate or subclass the {@link DefaultArchiveDetector} class and
 * provide an instance as the parameter for
 * {@link File#setDefaultArchiveDetector(ArchiveDetector)} or any of the
 * {@link File} constructors whenever you need to configure the way archive
 * files are detected and configured.
 * <p>
 * Migration notes for users of TrueZIP 5.X or earlier:
 * <ul>
 * <li>The convenient ZipDetector and String constants for various archive
 *     types present in the old ZipDetector interface (for example
 *     <code>JAR</code> or <code>JAR_SUFFIXES</code>) have been removed
 *     in this version.
 *     With the new architecture of TrueZIP 6.0, the archive types supported
 *     are now determined by the available plug-in drivers and their
 *     configuration resource files.
 *     See {@link DefaultArchiveDetector} for more information.
 * </ul>
 * 
 * @see DefaultArchiveDetector
 * @see File
 * @see ArchiveDriver
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0 (refactored from the former <code>ZipDetector</code>)
 */
public interface ArchiveDetector extends FileFactory {

    //
    // Predefined default implementations:
    // 

    /**
     * The nullary archive detector as implemented by the
     * {@link DefaultArchiveDetector} class.
     * As the name implies, this <code>ArchiveDetector</code> never detects
     * an archive file in a pathname.
     *
     * @see DefaultArchiveDetector
     */
    DefaultArchiveDetector NULL = new DefaultArchiveDetector(null);
    
    /**
     * The archive types recognized by default.
     * <p>
     * This value is determined by processing all configuration files on the
     * class path which are provided by the TrueZIP JAR and (optionally)
     * the client application.
     * The class path is searched by the current thread's context class
     * loader when the {@link DefaultArchiveDetector} class is loaded.
     *
     * @see DefaultArchiveDetector
     */
    DefaultArchiveDetector DEFAULT = new DefaultArchiveDetector(
            DefaultArchiveDetector.DEFAULT_SUFFIXES);

    /**
     * All archive types registered with the {@link DefaultArchiveDetector}
     * implementation.
     * <p>
     * This value is determined by processing all configuration files on the
     * class path which are provided by the TrueZIP JAR and (optionally)
     * the client application.
     * The class path is searched by the current thread's context class
     * loader when the {@link DefaultArchiveDetector} class is loaded.
     *
     * @see DefaultArchiveDetector
     */
    DefaultArchiveDetector ALL = new DefaultArchiveDetector(
            DefaultArchiveDetector.ALL_SUFFIXES);

    //
    // The one and only method this interface really adds:
    //
    
    /**
     * Detects whether the given <code>pathname</code> identifies an archive
     * file or not by applying heuristics to the path name only and returns
     * an appropriate <code>ArchiveDriver</code> to use or <code>null</code>
     * if the pathname does not denote an archive file or an appropriate
     * <code>ArchiveDriver</code> is not available for some reason.
     * <p>
     * Please note that implementations <em>must not</em> check the actual
     * contents of the file identified by <code>pathname</code>!
     * This is because this method may be used to detect archive files
     * by their names before they are actually created or to detect archive
     * files which are enclosed in other archive files, in which case there
     * is no way to check the file contents in the native file system.
     * 
     * @param pathname The (not necessarily absolute) pathname of the
     *        prospective archive file.
     *        This does not actually need to be accessible in the native file
     *        system!
     *
     * @return An <code>ArchiveDriver</code> instance for this archive file
     *         or <code>null</code> if the pathname does not denote an
     *         archive file (i.e. the pathname does not have a known suffix)
     *         or an appropriate <code>ArchiveDriver</code> is not available
     *         for some reason.
     *
     * @throws NullPointerException If pathname is <code>null</code>.
     */
    ArchiveDriver getArchiveDriver(String pathname);

    //
    // Specification of the (undocumented) contract inherited from FileFactory:
    //

    /**
     * Constructs a new {@link File} instance from the given
     * <code>blueprint</code>.
     * 
     * @param blueprint The file to use as a blueprint. If this is an instance
     *        of the {@link File} class, its fields are simply copied.
     *
     * @return A newly created instance of the class {@link File}.
     */
    File createFile(java.io.File blueprint);

    /**
     * This factory method is <em>not</em> for public use - do not use it!
     */
    // It is used by {@link File#getParentFile()} for fast file construction
    // without rescanning the pathname for archive files,
    // which could even lead to wrong results.
    // 
    // Calling this constructor with illegal arguments may result in
    // <code>IllegalArgumentException</code>, <code>AssertionError</code> or
    // may even silently fail!
    File createFile(java.io.File delegate, File innerArchive);

    /**
     * This factory method is <em>not</em> for public use - do not use it!
     */
    // It is used by some methods for fast file
    // construction without rescanning the pathname for archive files
    // when rewriting the pathname of an existing <code>File</code> instance.
    // <p>
    // Calling this method with illegal arguments may result in
    // <code>IllegalArgumentException</code>, <code>AssertionError</code> or
    // may even silently fail!
    File createFile(File blueprint, java.io.File delegate, File enclArchive);

    /**
     * Constructs a new {@link File} instance which uses this
     * {@link ArchiveDetector} to detect any archive files in its pathname.
     * 
     * @param pathName The pathname of the file.
     *
     * @return A newly created instance of the class {@link File}.
     */
    File createFile(String pathName);

    /**
     * Constructs a new {@link File} instance which uses this
     * {@link ArchiveDetector} to detect any archive files in its pathname.
     * 
     * @param parent The parent pathname as a {@link String}.
     * @param child The child pathname as a {@link String}.
     *
     * @return A newly created instance of the class {@link File}.
     */
    File createFile(String parent, String child);

    /**
     * Constructs a new {@link File} instance which uses this
     * {@link ArchiveDetector} to detect any archive files in its pathname.
     * 
     * @param parent The parent pathname as a <code>File</code>.
     * @param child The child pathname as a {@link String}.
     *
     * @return A newly created instance of the class {@link File}.
     */
    File createFile(java.io.File parent, String child);

    /**
     * Constructs a new {@link File} instance from the given
     * <code>uri</code>. This method behaves similar to
     * {@link java.io.File#File(URI) new java.io.File(uri)} with the following
     * amendment:
     * If the URI matches the pattern
     * <code>(jar:)*file:(<i>path</i>!/)*<i>entry</i></code>, then the
     * constructed file object treats the URI like a (possibly ZIPped) file.
     * <p>
     * The newly created {@link File} instance uses this
     * {@link ArchiveDetector} to detect any archive files in its pathname.
     * 
     * @param uri an absolute, hierarchical URI with a scheme equal to
     *        <code>file</code> or <code>jar</code>, a non-empty path component,
     *        and undefined authority, query, and fragment components.
     *
     * @return A newly created instance of the class {@link File}.
     *
     * @throws NullPointerException if <code>uri</code> is <code>null</code>.
     * @throws IllegalArgumentException if the preconditions on the
     *         parameter <code>uri</code> do not hold.
     */
    File createFile(URI uri);

    /**
     * Creates a new {@link FileInputStream} to read the content of the
     * given file.
     * 
     * @param file The file to read.
     *
     * @return A newly created instance of the class {@link FileInputStream}.
     *
     * @throws FileNotFoundException On any I/O related issue when opening the file.
     */
    FileInputStream createFileInputStream(java.io.File file)
    throws FileNotFoundException;

    /**
     * Creates a new {@link FileOutputStream} to write the new content of the
     * given file.
     * 
     * @param file The file to write.
     * @param append If <code>true</code> the new content should be appended
     *        to the old content rather than overwriting it.
     *
     * @return A newly created instance of the class {@link FileOutputStream}.
     *
     * @throws FileNotFoundException On any I/O related issue when opening the file.
     */
    FileOutputStream createFileOutputStream(java.io.File file, boolean append)
    throws FileNotFoundException;
}