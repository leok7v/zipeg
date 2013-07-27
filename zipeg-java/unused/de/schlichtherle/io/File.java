/*
 * File.java
 *
 * Created on 23. Oktober 2004, 00:31
 */
/*
 * Copyright 2004-2006 Schlichtherle IT Services
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

import de.schlichtherle.io.util.Path;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilenameFilter;
import java.io.FileFilter;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.swing.Icon;

/**
 * This class extends {@link java.io.File java.io.File} to represent a path
 * name of a file or directory which actually may or may not be an archive
 * file or an entry in an archive file.
 * <p>
 * <b>WARNING:</b>
 * This class accesses archive files as external resources.
 * In order to do so it must assume that it has <em>exclusive</em> access
 * to these archive files.
 * If other processes or other classes or even other class loader
 * definitions of this class need to access the same archive files
 * concurrently, you must call {@link File#umount} in order to update
 * the internal state of these archive files to the native file system and
 * release any resources associated with them before the other party can
 * safely access them.
 * Furthermore, after the call to <code>umount()</code> you must not call
 * <em>any of the methods</em> in this class for these archive files or
 * <em>any of their entries</em> while the other party is accessing these
 * archive files.
 * Failure to comply to this rule will result in unpredictable behaviour
 * and may even <em>cause loss of data</em>!
 * <p>
 * <b>Notes:</b>
 * <ul>
 * <li>When using characters in file names which have no representation in
 *     the character set encoding of a archive file, then the methods
 *     of this class will fail gracefully with an IOException or a boolean
 *     return value of false (depending on the method's way to indicate an
 *     error condition).
 *     This is to protect applications from creating archive entries which
 *     cannot get encoded and decoded again correctly.
 *     An example is the Euro sign which does not have a representation in
 *     the IBM437 character set used by ordinary ZIP files.
 * <li>All instances of this class are immutable.
 * </ul>
 * Please refer to the documentation of the base class for any undocumented
 * methods.
 *
 * @see java.io.File java.io.File
 * @see <a href="package-summary.html#package_description">Package Description</a>
 *
 * @author  Christian Schlichtherle
 * @version @version@
 */
public class File extends java.io.File implements FileConstants, Cloneable {

    /** The filesystem roots. */
    private static final HashSet roots = new HashSet(Arrays.asList(listRoots()));

    /** The prefix of a UNC (a Windows concept). */
    private static final String uncPrefix = separator + separator;

    private static final Executor readerExecutor
            = getExecutor("TrueZIP InputStream Reader");

    /**
     * @see #setLenient(boolean)
     * @see #isLenient()
     */
    private static boolean lenient = true;

    private static ArchiveDetector defaultArchiveDetector
            = ArchiveDetector.DEFAULT;

    /**
     * The entry is used to implement the behaviour of most methods in case
     * this file object represents neither a archive file or an entry
     * in a archive file.
     * If this file object is constructed from another file object, then this
     * field is initialized with that file object. This is provided to support
     * subclasses of <code>File</code> which are provided by third parties and
     * their behaviour is required as a default.
     * This is an essential feature to support archive files as
     * directories for <code>javax.swing.JFileChooser</code>, because the
     * <code>FileSystemView</code> class creates objects which are actually
     * instances of {@link sun.awt.shell.ShellFolder}, which is a subclass of
     * {@link java.io.File}.
     * <p>
     * Finally, to support <code>JFileChooser</code>, we are providing an
     * customized {@link javax.swing.filechooser.FileSystemView} which
     * simply wraps most methods and creates an instance of this class from
     * the object returned by the method of the base <code>FileSystemView</code>
     * class as a file.
     */
    private final java.io.File delegate;

    /**
     * @see #getArchiveDetector
     */
    private final ArchiveDetector archiveDetector;
    
    /**
     * This field should be considered final!
     * 
     * @see #getInnerArchive
     */
    private File innerArchive;
    
    /**
     * This field should be considered final!
     * 
     * @see #getInnerEntryName
     */
    private String innerEntryName;
    
    /**
     * This field should be considered final!
     * 
     * @see #getEnclArchive
     */
    private File enclArchive;
    
    /**
     * This field should be considered final!
     * 
     * @see #getEnclEntryName
     */
    private String enclEntryName;
    
    /**
     * This field should be considered final!
     */
    private ArchiveController archiveController;

    /**
     * Equivalent to {@link #File(java.io.File, ArchiveDetector)
     * File(file, getDefaultArchiveDetector())}.
     */
    public File(java.io.File blueprint) {
        this(blueprint, defaultArchiveDetector);
    }
    
    /**
     * Constructs a new <code>File</code> instance which may use the given
     * {@link ArchiveDetector} to detect any archive files in its pathname.
     * 
     * @param blueprint The file to use as a blueprint. If this is an instance
     *        of this class, its fields are copied and the
     *        <code>archiveDetector</code> parameter is ignored.
     * @param archiveDetector The object used to detect any archive
     *        files in the pathname and configure their parameters.
     */
    public File(
            final java.io.File blueprint,
            final ArchiveDetector archiveDetector) {
        super(blueprint.getPath());
        
        if (blueprint instanceof File) {
            final File file = (File) blueprint;
            this.delegate = file.delegate;
            this.archiveDetector = file.archiveDetector;
            this.enclArchive = file.enclArchive;
            this.enclEntryName = file.enclEntryName;
            this.innerArchive = file.isArchive() ? this : file.innerArchive;
            this.innerEntryName = file.innerEntryName;
            this.archiveController = file.archiveController;
        } else {
            this.delegate = blueprint;
            this.archiveDetector = archiveDetector;
            init((File) null);
        }
        
        assert invariants(true);
    }

    /**
     * Equivalent to {@link #File(String, ArchiveDetector)
     * File(pathname, getDefaultArchiveDetector())}.
     */
    public File(String pathName) {
        this(pathName, defaultArchiveDetector);
    }
    
    /**
     * Constructs a new <code>File</code> instance which uses the given
     * {@link ArchiveDetector} to detect any archive files in its pathname
     * and configure their parameters.
     * 
     * @param archiveDetector The object used to detect any archive
     *        files in the pathname and configure their parameters.
     * @param pathName The pathname of the file.
     */
    public File(
            final String pathName,
            final ArchiveDetector archiveDetector) {
        super(pathName);

        delegate = new java.io.File(pathName);
        this.archiveDetector = archiveDetector;
        init((File) null);
        
        assert invariants(true);
    }

    /**
     * Equivalent to {@link #File(String, String, ArchiveDetector)
     * File(parent, child, getDefaultArchiveDetector())}.
     */
    public File(String parent, String child) {
        this(parent, child, defaultArchiveDetector);
    }
    
    /**
     * Constructs a new <code>File</code> instance which uses the given
     * {@link ArchiveDetector} to detect any archive files in its pathname
     * and configure their parameters.
     * 
     * @param archiveDetector The object used to detect any archive
     *        files in the pathname and configure their parameters.
     * @param parent The parent pathname as a {@link String}.
     * @param child The child pathname as a {@link String}.
     */
    public File(
            final String parent,
            final String child,
            final ArchiveDetector archiveDetector) {
        super(parent, child);
        
        delegate = new java.io.File(parent, child);
        this.archiveDetector = archiveDetector;
        init((File) null);
        
        assert invariants(true);
    }
    
    /**
     * Equivalent to {@link #File(java.io.File, String, ArchiveDetector)
     * File(parent, child, null)}.
     *
     * @param parent The parent directory as a <code>File</code> instance.
     *        If this parameter is an instance of this class, its
     *        <code>ArchiveDetector</code> is used to detect any archive files
     *        in the pathname of this <code>File</code> instance.
     *        Otherwise, the {@link #getDefaultArchiveDetector()} is used.
     *        This is used in order to make this <code>File</code> instance
     *        behave as if it had been created by one of the {@link #listFiles}
     *        methods called on <code>parent</code> instead.
     * @param child The child pathname as a {@link String}.
     */
    public File(java.io.File parent, String child) {
        this(parent, child, null);
    }
    
    /**
     * Constructs a new <code>File</code> instance which uses the given
     * {@link ArchiveDetector} to detect any archive files in its pathname
     * and configure their parameters.
     * 
     * @param parent The parent directory as a <code>File</code> instance.
     * @param child The child pathname as a {@link String}.
     * @param archiveDetector The object used to detect any archive
     *        files in the pathname of this <code>File</code> instance.
     *        If this is <code>null</code>, the <code>ArchiveDetector</code>
     *        is inherited from <code>parent</code> if it's an instance of
     *        this class or the {@link #getDefaultArchiveDetector()} otherwise.
     *        This may be used in order to make this <code>File</code> instance
     *        behave as if it had been created by one of the {@link #listFiles}
     *        methods called on <code>parent</code> instead.
     */
    public File(
            final java.io.File parent,
            final String child,
            final ArchiveDetector archiveDetector) {
        super(parent, child);
        
        delegate = new java.io.File(parent, child);
        if (parent instanceof File && child.length() > 0) {
            final File p = (File) parent;
            this.archiveDetector = archiveDetector != null
                    ? archiveDetector
                    : p.archiveDetector;
            init((File) parent);
        } else {
            this.archiveDetector = archiveDetector != null
                    ? archiveDetector
                    : defaultArchiveDetector;
            init((File) null);
        }
        
        assert invariants(true);
    }
    
    /**
     * Constructs a new <code>File</code> instance from the given
     * <code>uri</code>. This method behaves similar to
     * {@link java.io.File#File(URI) new java.io.File(uri)} with the following
     * amendment:
     * If the URI matches the pattern
     * <code>(jar:)*file:(<i>path</i>!/)*<i>entry</i></code>, then the
     * constructed file object treats the URI like a (possibly ZIPped) file.
     * <p>
     * For example, in a Java application which is running from a JAR in the
     * local file system you could use this constructor to arbitrarily access
     * (and modify) all entries in the JAR file from which the application is
     * currently running by using the following simple method:
     * <pre>
     * public File getResourceAsFile(String resource) {
     *   URL url = getClass().getResource(resource);
     *   try {
     *     return new File(new URI(url.toExternalForm()));
     *   } catch (Exception notAJaredFileURI) {
     *     return null;
     *   }
     * }
     * </pre>
     * The newly created <code>File</code> instance uses
     * {@link ArchiveDetector#ALL} as its <code>ArchiveDetector</code>.
     * 
     * @param uri an absolute, hierarchical URI with a scheme equal to
     *        <code>file</code> or <code>jar</code>, a non-empty path component,
     *        and undefined authority, query, and fragment components.
     * @throws NullPointerException if <code>uri</code> is <code>null</code>.
     * @throws IllegalArgumentException if the preconditions on the
     *         parameter <code>uri</code> do not hold.
     */
    public File(URI uri) {
        this(uri, ArchiveDetector.ALL);
    }
    
    // Unfortunately, this constructor has a significant overhead as the jar:
    // schemes need to be processed twice, first before initializing the super
    // class and second when initializing this sub class.
    File(
            final URI uri,
            final ArchiveDetector archiveDetector) {
        super(unjarFileURI(uri));
        
        delegate = new java.io.File(super.getPath());
        this.archiveDetector = archiveDetector;
        init(uri);
        
        assert invariants(true);
    }
    
    /**
     * Converts a (jar:)*file: URI to a plain file: URI or returns the
     * provided URI again if it doesn't match this pattern.
     */
    private static final URI unjarFileURI(final URI uri) {
        try {
            final String scheme = uri.getScheme();
            final String ssp = Path.normalize(uri.getSchemeSpecificPart(), '/');
            return unjarFileURIImpl(new URI(scheme, ssp, null));
        } catch (URISyntaxException ignored) {
            // Ignore any exception with possibly only a subpart of the
            // original URI.
        }
        throw new IllegalArgumentException(uri + ": Not a valid (possibly jared) file URI!");
    }

    private static final URI unjarFileURIImpl(final URI uri)
    throws URISyntaxException {
        final String scheme = uri.getScheme();
        if ("jar".equalsIgnoreCase(scheme)) {
            final String rssp = uri.getRawSchemeSpecificPart();
            final int i;
            if (rssp.endsWith("!"))
                i = rssp.length() - 1;
            else
                i = rssp.lastIndexOf("!/");

            if (i <= 0)
                return unjarFileURI(new URI(rssp)); // ignore redundant jar: scheme

            final URI subURI = new URI(
                    rssp.substring(0, i) + rssp.substring(i + 1)); // cut out '!'
            final String subScheme = subURI.getScheme();
            if ("jar".equalsIgnoreCase(subScheme)) {
                final URI processedSubURI = unjarFileURIImpl(subURI);
                if (processedSubURI != subURI)
                    return processedSubURI;
                // No match, e.g. "jar:jar:http://host/dir!/dir!/file".
            } else if ("file".equalsIgnoreCase(subScheme)) {
                return subURI; // e.g. "file:///usr/bin"
            }
        } else if ("file".equalsIgnoreCase(scheme)) {
            return uri;
        }
        throw new URISyntaxException(uri.toString(), "Not a valid (possibly jared) file URI!");
    }

    /**
     * This constructor is <em>not</em> for public use - do not use it!
     *
     * @see FileFactory
     */
    public File(
            final java.io.File delegate,
            final File innerArchive,
            final ArchiveDetector archiveDetector) {
        super(delegate.getPath());

        assert assertParams(delegate, innerArchive, archiveDetector);

        this.delegate = delegate;
        
        final String path = delegate.getPath();
        if (innerArchive != null) {
            final int innerArchivePathLength
                    = innerArchive.getPath().length();
            if (path.length() == innerArchivePathLength) {
                this.archiveDetector = innerArchive.archiveDetector;
                this.innerArchive = this;
                this.innerEntryName = EMPTY;
                this.enclArchive = innerArchive.enclArchive;
                this.enclEntryName = innerArchive.enclEntryName;
                this.archiveController = ArchiveController.getInstance(this);
            } else {
                this.archiveDetector = archiveDetector;
                this.innerArchive = this.enclArchive = innerArchive;
                this.innerEntryName = this.enclEntryName
                        = path.substring(innerArchivePathLength + 1) // cut off leading separatorChar
                        .replace(separatorChar, ENTRY_SEPARATOR_CHAR);
            }
        } else {
            this.archiveDetector = archiveDetector;
        }
        
        assert invariants(true);
    }

    /**
     * If assertions are disabled, the call to this method is thrown away by
     * the HotSpot compiler, so there is no performance penalty.
     */
    private static final boolean assertParams(
            final java.io.File delegate,
            final File innerArchive,
            final ArchiveDetector archiveDetector)
    throws AssertionError {
        assert delegate != null : "delegate is null!";
        assert !(delegate instanceof File) : "delegate must not be a de.schlichtherle.io.File!";
        if (innerArchive != null) {
            assert innerArchive.isArchive() : "innerArchive must be an archive!";
            assert containsImpl(innerArchive, delegate)
                    : "innerArchive must contain delegate!";
        }
        assert archiveDetector != null : "archiveDetector is null!";

        return true;
    }

    /**
     * This constructor is <em>not</em> for public use - do not use it!
     *
     * @see FileFactory
     */
    public File(
            final File blueprint,
            final java.io.File delegate,
            final File enclArchive) {
        super(delegate.getPath());

        assert parameters(blueprint, delegate, enclArchive);

        this.delegate = delegate;
        this.archiveDetector = blueprint.archiveDetector;
        this.enclArchive = enclArchive;
        this.enclEntryName = blueprint.enclEntryName;
        this.innerArchive = blueprint.isArchive() ? this : enclArchive;
        this.innerEntryName = blueprint.innerEntryName;
        this.archiveController = blueprint.archiveController;
        
        assert invariants(blueprint.archiveController != null);
    }

    /**
     * Initialize this file object by scanning its pathname for archive
     * files, using the given <code>ancestor</code> file (i.e. a direct or
     * indirect parent file) if any.
     * <code>entry</code> and <code>archiveDetector</code> must already be
     * initialized!
     * Must not be called to re-initialize this object!
     */
    private void init(final File ancestor) {
        assert ancestor == null || super.getPath().startsWith(ancestor.getPath());
        assert delegate.getPath().equals(super.getPath());
        assert archiveDetector != null;

        init(ancestor, archiveDetector, 0, super.getPath());

        if (innerArchive == this) {
            // archiveController init has been deferred until now in
            // order to provide the ArchiveController with a fully
            // initialized object.
            archiveController = ArchiveController.getInstance(this);
        }
    }

    private void init(
            File ancestor,
            ArchiveDetector archiveDetector,
            int skip,
            final String path) {
        if (path == null) {
            assert enclArchive == null;
            enclEntryName = null;
            return;
        }

        final String[] split = Path.split(path, separatorChar);
        final String parent = split[0];
        final String base = split[1]; // TODO: Review: intern()?

        if (base.length() == 0 || ".".equals(base)) {
            // Fall through.
        } else if ("..".equals(base)) {
            skip++;
        } else if (skip > 0) {
            skip--;
        } else {
            if (ancestor != null) {
                final int pathLen = path.length();
                final int ancestorPathLen = ancestor.getPath().length();
                if (pathLen == ancestorPathLen) {
                    // Found ancestor. Process it and stop.
                    assert enclEntryName != null;
                    enclArchive = ancestor.innerArchive;
                    if (!ancestor.isArchive()) {
                        if (ancestor.isEntry()) {
                            enclEntryName
                                    = ancestor.enclEntryName
                                    + "/" + enclEntryName;
                        } else {
                            enclEntryName = null;
                        }
                    }
                    if (innerArchive != this) {
                        innerArchive = enclArchive;
                        innerEntryName = enclEntryName;
                    }
                    return;
                } else if (pathLen < ancestorPathLen) {
                    archiveDetector = ancestor.archiveDetector;
                    ancestor = ancestor.enclArchive;
                }
            }

            final boolean isArchive
                    = archiveDetector.getArchiveDriver(path) != null;
            if (enclEntryName != null) {
                if (isArchive) {
                    enclArchive = archiveDetector.createFile(path); // use the same detector for the parent directory
                    if (innerArchive != this) {
                        innerArchive = enclArchive;
                        innerEntryName = enclEntryName;
                    }
                    return;
                }
                enclEntryName = base + "/" + enclEntryName;
            } else {
                if (isArchive) {
                    innerArchive = this;
                    innerEntryName = EMPTY;
                }
                enclEntryName = base;
            }
        }

        init(ancestor, archiveDetector, skip, parent);
    }

    /**
     * Uses the given (jar:)*file: URI to initialize this file object.
     * Note that we already know that the provided URI matches this pattern!
     * <code>entry</code> and <code>archiveDetector</code> must already be
     * initialized!
     * Must not be called to re-initialize this object!
     */
    private void init(final URI uri) {
        assert uri != null;
        assert delegate.getPath().equals(super.getPath());
        assert archiveDetector != null;

        init(uri, 0,
                Path.cutTrailingSeparator(uri.getSchemeSpecificPart(), '/'));

        if (innerArchive == this) {
            // archiveController init has been deferred until now in
            // order to provide the ArchiveController with a fully
            // initialized object.
            archiveController = ArchiveController.getInstance(this);
        }
    }

    /**
     * TODO: Provide a means to detect other archive schemes, not only
     * <code>"jar:"</code>.
     */
    private void init(
            URI uri,
            int skip,
            final String path) {
        String scheme = uri.getScheme();
        if (path == null || !"jar".equalsIgnoreCase(scheme)) {
            assert enclArchive == null;
            enclEntryName = null;
            return;
        }

        final String[] split = Path.split(path, '/');
        String parent = split[0];
        final String base = split[1]; // TODO: Review: intern()?

        if (base.length() == 0 || ".".equals(base)) {
            // Fall through.
        } else if ("..".equals(base)) {
            skip++;
        } else if (skip > 0) {
            skip--;
        } else {
            final int baseEnd = base.length() - 1;
            final boolean isArchive = base.charAt(baseEnd) == '!';
            if (enclEntryName != null) {
                if (isArchive) {
                    enclArchive = archiveDetector.createFile(createURI(scheme, path)); // use the same detector for the parent directory
                    if (innerArchive != this) {
                        innerArchive = enclArchive;
                        innerEntryName = enclEntryName;
                    }
                    return;
                }
                enclEntryName = base + "/" + enclEntryName;
            } else {
                if (isArchive) {
                    innerArchive = this;
                    innerEntryName = EMPTY;
                    int i = parent.indexOf(':');
                    assert i >= 0;
                    scheme = parent.substring(0, i);
                    assert scheme.matches("[a-zA-Z]+");
                    if (i == parent.length() - 1) // scheme only?
                        return;
                    uri = createURI(parent.substring(0, i), parent.substring(i + 1));
                    enclEntryName = base.substring(0, baseEnd); // cut off trailing '!'!
                    parent = uri.getSchemeSpecificPart();
                } else {
                    enclEntryName = base;
                }
            }
        }

        init(uri, skip, parent);
    }

    /**
     * Creates a URI from a scheme and a scheme specific part.
     * Note that the scheme specific part may contain whitespace.
     * {@link "https://truezip.dev.java.net/issues/show_bug.cgi?id=1"}
     */
    private URI createURI(String scheme, String ssp)
    throws IllegalArgumentException {
        try {
            return new URI(scheme, ssp, null);
        } catch (URISyntaxException syntaxError) {
	    IllegalArgumentException iae = new IllegalArgumentException(syntaxError.toString());
	    iae.initCause(syntaxError);
	    throw iae;
        }
    }

    /**
     * This is called by package private constructors if and only if
     * assertions are enabled to assert that their parameters are valid.
     * If assertions are disabled, the call to this method is thrown away by
     * the HotSpot compiler, so there is no performance penalty.
     */
    private static boolean parameters(
            final File blueprint,
            final java.io.File delegate,
            final File enclArchive)
    throws AssertionError {
        assert delegate != null : "delegate is null!";
        assert !(delegate instanceof File)
                : "delegate must not be a de.schlichtherle.io.File!";
        assert blueprint != null : "blueprint is null!";

        String delegatePath = delegate.getPath();
        final java.io.File normalizedBlueprint = normalize(blueprint);
        String normalizedBlueprintPath = normalizedBlueprint.getPath();
        String normalizedBlueprintBase = normalizedBlueprint.getName();
        // Windows and MacOS are case preserving, however UNIX is case
        // sensitive. If we meet an unknown platform, we assume that it is
        // case preserving, which means that two pathnames are considered
        // equal if they differ by case only.
        // In the context of this constructor, this implements a liberal
        // in-dubio-pro-reo parameter check.
        if (separatorChar != '/') {
            delegatePath = delegatePath.toLowerCase();
            normalizedBlueprintPath = normalizedBlueprintPath.toLowerCase();
            normalizedBlueprintBase = normalizedBlueprintBase.toLowerCase();
        }
        if (!".".equals(normalizedBlueprintBase)
            && !"..".equals(normalizedBlueprintBase)
            && !normalizedBlueprintPath.startsWith("." + separator)
            && !normalizedBlueprintPath.startsWith(".." + separator)) {
            assert delegatePath.endsWith(normalizedBlueprintPath)
                    : "delegate and blueprint must identify the same directory!";
            if (enclArchive != null) {
                assert enclArchive.isArchive()
                        : "enclArchive must be an archive file!";
                assert containsImpl(enclArchive, delegate.getParentFile())
                        : "enclArchive must be an ancestor of delegate!";
            }
        }
        
        return true;
    }

    /**
     * This is called by all constructors if and only if assertions
     * are enabled to assert that the instance invariants are properly obeyed.
     * If assertions are disabled, the call to this method is thrown away by
     * the HotSpot compiler, so there is no performance penalty.
     */
    private boolean invariants(final boolean controllerInit) 
    throws AssertionError {
        assert delegate != null;
        assert !(delegate instanceof File);
        assert delegate.getPath().equals(super.getPath());
        assert archiveDetector != null;
        assert (innerArchive != null) == (innerEntryName != null);
        assert (enclArchive != null) == (enclEntryName != null);
        assert (innerArchive == this
                    && innerArchive != enclArchive
                    && innerEntryName == EMPTY
                    && innerEntryName != enclEntryName
                    && (controllerInit
                        ? archiveController != null
                        : archiveController == null))
                ^ (innerArchive == enclArchive
                    && innerEntryName == enclEntryName
                    && archiveController == null);
        assert enclArchive == null
                || containsImpl(enclArchive, delegate.getParentFile())
                    && enclEntryName.length() > 0
                    && (separatorChar == '/'
                        || enclEntryName.indexOf(separatorChar) == -1);
        
        return true;
    }

    /**
     * Removes any <code>"."</code> and <code>".."</code> directories from the pathname
     * wherever possible.
     * 
     * @param file The file object which's path is to be normalized.
     * @return <code>file</code> if it was already in normalized form.
     *         Otherwise, an instance of the class {@link java.io.File
     *         java.io.File} is returned.
     *         Note that the returned object is never an instance of this
     *         class, so it safe to use it as a entry for constructing
     *         a new object of this class.
     */
    static java.io.File normalize(java.io.File file) {
        final String possiblyDotifiedPath = file.getPath();
        final String path = Path.normalize(possiblyDotifiedPath, separatorChar);
        if (path != possiblyDotifiedPath)
            return new java.io.File(path);
        else
            return file;
    }
    
    /**
     * Equivalent to {@link #update(boolean, boolean, boolean, boolean)
     * update(false, true, false, true)}.
     */
    public static final void update()
    throws ArchiveException {
        ArchiveController.updateAll("",
                false, true,
                false, true,
                false, true);
    }
    
    /**
     * Equivalent to {@link #update(boolean, boolean, boolean, boolean)
     * update(false, closeStreams, false, closeStreams)}.
     */
    public static final void update(boolean closeStreams)
    throws ArchiveException {
        ArchiveController.updateAll("",
                false, closeStreams,
                false, closeStreams,
                false, true);
    }

    /**
     * Updates <em>all</em> archive files in the native file system
     * with the contents of their respective virtual file system.
     * This method is thread safe and re-entrant.
     * <p>
     * Use {@link #umount} instead to allow subsequent changes to the archive
     * files by other processes or via the <code>java.io.File*</code> classes
     * <em>before</em> this package is used for read or write access to
     * these archive files again.
     * <p>
     * If any RAES encrypted archive is used, this method will also prompt
     * the user for new passwords in case the user has requested to change the
     * password and no automatic update has been performed meanwhile.
     * The prompting will be done by the configured
     * {@link de.schlichtherle.key.KeyManager}.
     * <p>
     * This method is guaranteed to process all archive files which are
     * in use or have been touched by this package.
     * However, it may fail on some of these archive files and hence
     * terminate with a chain of exceptions once processing the remaining
     * archive files has finished.
     * If such a warning or an error exception has occured, the exception is
     * stored (with its low level cause associated to it) in a chain of
     * {@link ArchiveException} objects.
     * Finally, the exception chain is sorted according to (1) descending
     * order of priority and (2) ascending order of appearance,
     * and finally the resulting head exception is thrown.
     * This has the effect that any instance of {@link ArchiveWarningException}
     * is pushed to the end of the chain, so that an application can use the
     * following simple idiom to detect if only some warning exceptions or at
     * least one error exception has occured:
     * <p>
     * <pre>
     *      try {
     *          File.update(); // or File.umount() - with or without parameters
     *      }
     *      catch (ArchiveWarningException ignore) {
     *          // Only instances of the class ArchiveWarningException exist in
     *          // the chain of exceptions. We decide to ignore this.
     *      }
     *      catch (ArchiveException failure) {
     *          // At least one exception occured which is not just a
     *          // ArchiveWarningException. This is a severe situation that
     *          // needs to be handled.
     *
     *          // Print the chain of exceptions in order of descending
     *          // priority and ascending appearance.
     *          //failure.printStackTrace();
     *
     *          // Print the chain of exceptions in order of appearance  instead.
     *          failure.sortAppearance().printStackTrace();
     *      }
     * </pre>
     * Please note that the {@link Exception#getMessage()} method (and hence
     * {@link Exception#printStackTrace()} will concatenate the detail
     * messages of the exceptions in the chain in the given order.
     * <p>
     * Depending on the size and amount of the archive files your application
     * is using, this can be a lengthy operation. Therefore, you may opt to:
     * <ol>
     * <li>
     * Call this method from a background thread with <code>false</code> as
     * its parameter in order not to force the closing of any open streams
     * which may be used concurrently. This is a thread safe operation and may
     * provide a big performance boost, depending on the system configuration.
     * The disadvantage is that there is no guarantee that all archive
     * files get really updated.
     * <li>
     * Call {@link #update(File)} or {@link #umount(File)} whenever you're
     * finished processing an archive file.
     * This is a good strategy if your application is using a large number
     * of archive files.
     * <li>
     * Implement a progress monitor using
     * {@link ArchiveStatistics#getUpdateTotalByteCountRead()} and
     * {@link ArchiveStatistics#getUpdateTotalByteCountRead()} upon the
     * instance returned by {@link #getLiveArchiveStatistics()}.
     * Please refer to the source code of {@link nzip#startProgressMonitor()}
     * for a simple example.
     * </ol>
     * <p>
     * Please note that there is a shutdown hook which actually calls
     * this method and prints a stack trace for any exception to the
     * standard error output, but still the minimum requirement is that
     * you should always call File.update() before your application
     * terminates because shutdown hooks need to complete fast and cannot
     * prompt the user for new passwords in case an RAES encrypted ZIP
     * file has been touched and changing the password has been requested
     * by the user (in which specific case the old password is retained).
     * This is preferrable done in a <code>finally</code> block of a
     * <em>try-finally</em> clause at the top level of your main method in
     * order to ensure that it is called regardless of any thrown
     * {@link Throwable}.
     * 
     * @param waitInputStreams Suppose any other thread has still one or more
     *        archive entry input streams open.
     *        Then if and only if this parameter is <code>true</code>, this
     *        method will wait until all other threads have closed their
     *        archive entry input streams.
     *        Archive entry input streams opened (and not yet closed) by the
     *        current thread are always ignored.
     *        If the current thread gets interrupted while waiting, it will
     *        stop waiting and proceed normally as if this parameter were
     *        <code>false</code>.
     *        Be careful with this parameter value: If a stream has not been
     *        closed because the client application does not always properly
     *        close its streams, even on an {@link IOException} (which is a
     *        typical bug in many Java applications), then this method may
     *        not return until the current thread gets interrupted!
     * @param closeInputStreams Suppose there are any open input streams
     *        for any archive entries because the application has forgot to
     *        close all {@link FileInputStream} objects or another thread is
     *        still busy doing I/O on an archive.
     *        Then if this parameter is <code>true</code>, an update is forced
     *        and an {@link ArchiveBusyWarningException} is finally thrown to
     *        indicate that any subsequent operations on these streams
     *        will fail with an {@link ArchiveEntryStreamClosedException}
     *        because they have been forced to close.
     *        This may be used to recover an application from a
     *        {@link FileBusyException} thrown by a constructor of
     *        {@link FileInputStream} or {@link FileOutputStream}.
     *        If this parameter is <code>false</code>, the respective archive
     *        file is <em>not</em> updated and an {@link ArchiveBusyException}
     *        is thrown to indicate that the application must close all entry
     *        input streams first.
     * @param waitOutputStreams Similar to <code>waitInputStreams</code>,
     *        but applies to archive entry output streams instead.
     * @param closeOutputStreams Similar to <code>closeInputStreams</code>,
     *        but applies to archive entry output streams instead.
     *        If this parameter is <code>true</code>, then
     *        <code>closeInputStreams</code> must be <code>true</code>, too.
     *        Otherwise, an <code>IllegalArgumentException</code> is thrown.
     * @throws ArchiveBusyWarningExcepion If an archive file has been updated
     *         while the application is using any open streams to access it
     *         concurrently.
     *         These streams have been forced to close and the entries of
     *         output streams may contain only partial data.
     * @throws ArchiveWarningException If only warning conditions occur
     *         throughout the course of this method which imply that the
     *         respective archive file has been updated with constraints,
     *         such as a failure to set the last modification time of the
     *         archive file to the last modification time of its implicit
     *         root directory.
     * @throws ArchiveBusyException If an archive file could not get updated
     *         because the application is using an open stream.
     *         No data is lost and the archive file can still get updated by
     *         calling this method again.
     * @throws ArchiveException If any error conditions occur throughout the
     *         course of this method which imply loss of data.
     *         This usually means that at least one of the archive files
     *         has been created externally and was corrupted or it cannot
     *         get updated because the file system of the temp file or target
     *         file folder is full.
     * @throws IllegalArgumentException If <code>closeInputStreams</code> is
     *         <code>false</code> and <code>closeOutputStreams</code> is
     *         <code>true</code>.
     * @see #update(File)
     * @see #umount()
     * @see #umount(File)
     */
    public static final void update(
            boolean waitInputStreams, boolean closeInputStreams,
            boolean waitOutputStreams, boolean closeOutputStreams)
    throws ArchiveException {
        ArchiveController.updateAll("",
                waitInputStreams, closeInputStreams,
                waitOutputStreams, closeOutputStreams,
                false, true);
    }
    
    /**
     * Equivalent to {@link #update(File, boolean, boolean, boolean, boolean)
     * update(archive, false, true, false, true)}.
     */
    public static final void update(File archive)
    throws ArchiveException {
        update(archive, false, true, false, true);
    }
    
    /**
     * Equivalent to {@link #update(File, boolean, boolean, boolean, boolean)
     * update(archive, false, closeStreams, false, closeStreams)}.
     */
    public static final void update(File archive, boolean closeStreams)
    throws ArchiveException {
        update(archive, false, closeStreams, false, closeStreams);
    }

    /**
     * Similar to
     * {@link #update(boolean, boolean, boolean, boolean)
     * update(waitInputStreams, closeInputStreams, waitOutputStreams, closeOutputStreams)},
     * but will only update the given <code>archive</code> and all enclosed
     * (nested) archives.
     *
     * @param archive A top level archive file.
     * @throws NullPointerException If <code>archive</code> is <code>null</code>.
     * @throws IllegalArgumentException If <code>archive</code> is not an
     *         archive or is enclosed in another archive (is not top level).
     * @see #update()
     * @see #umount()
     * @see #umount(File)
     */
    public static final void update(
            File archive,
            boolean waitInputStreams, boolean closeInputStreams,
            boolean waitOutputStreams, boolean closeOutputStreams)
    throws ArchiveException {
        if (!archive.isArchive())
            throw new IllegalArgumentException(archive.getPath() + " (not an archive)");
        if (archive.getEnclArchive() != null)
            throw new IllegalArgumentException(archive.getPath() + " (not a top level archive)");
        ArchiveController.updateAll(archive.getCanOrAbsPath(),
                waitInputStreams, closeInputStreams,
                waitOutputStreams, closeOutputStreams,
                false, true);
    }

    /**
     * Equivalent to {@link #umount(boolean, boolean, boolean, boolean)
     * umount(false, true, false, true)}.
     */
    public static final void umount()
    throws ArchiveException {
        ArchiveController.updateAll("", false, true, false, true, true, true);
    }
    
    /**
     * Equivalent to {@link #umount(boolean, boolean, boolean, boolean)
     * umount(false, closeStreams, false, closeStreams)}.
     */
    public static final void umount(boolean closeStreams)
    throws ArchiveException {
        ArchiveController.updateAll("",
                false, closeStreams,
                false, closeStreams,
                true, true);
    }

    /**
     * Like {@link #update(boolean, boolean, boolean, boolean)
     * update(waitInputStreams, closeInputStreams, waitOutputStreams, closeOutputStreams)},
     * but releases <em>all</em> cached information, too.
     * This includes all temporary files for nested archive files which would
     * be retained by {@link #update()} for faster processing of subsequent
     * operations otherwise.
     * This method performs slightly slower than {@link #update()} and also
     * might slow down subsequent access to any archive files.
     * <p>
     * Use this method if you want to allow subsequent access to the archive
     * files by third parties: Other processes, the <code>java.io.File*</code>
     * classes or even the same class defined by a different class loader!
     * If you want TrueZIP to recognize any changes made to an archive file
     * by the third party, you <em>must not</em> access the archive file
     * with this package again before the third party has finished its
     * modifications.
     *
     * @see #update()
     * @see #update(File)
     * @see #umount(File)
     */
    public static final void umount(
            boolean waitInputStreams, boolean closeInputStreams,
            boolean waitOutputStreams, boolean closeOutputStreams)
    throws ArchiveException {
        ArchiveController.updateAll("",
                waitInputStreams, closeInputStreams,
                waitOutputStreams, closeOutputStreams,
                true, true);
    }

    /**
     * Equivalent to {@link #umount(File, boolean, boolean, boolean, boolean)
     * umount(archive, false, true, false, true)}.
     */
    public static final void umount(File archive)
    throws ArchiveException {
        umount(archive, false, true, false, true);
    }
    
    /**
     * Equivalent to {@link #umount(File, boolean, boolean, boolean, boolean)
     * umount(archive, false, closeStreams, false, closeStreams)}.
     */
    public static final void umount(File archive, boolean closeStreams)
    throws ArchiveException {
        umount(archive, false, closeStreams, false, closeStreams);
    }

    /**
     * Similar to
     * {@link #umount(boolean, boolean, boolean, boolean)
     * umount(waitInputStreams, closeInputStreams, waitOutputStreams, closeOutputStreams)},
     * but will only update the given <code>archive</code> and all enclosed
     * (nested) archives.
     *
     * @param archive A top level archive file.
     * @throws NullPointerException If <code>archive</code> is <code>null</code>.
     * @throws IllegalArgumentException If <code>archive</code> is not an
     *         archive or is enclosed in another archive (is not top level).
     * @see #update()
     * @see #update(File)
     * @see #umount()
     */
    public static final void umount(
            File archive,
            boolean waitInputStreams, boolean closeInputStreams,
            boolean waitOutputStreams, boolean closeOutputStreams)
    throws ArchiveException {
        if (!archive.isArchive())
            throw new IllegalArgumentException(archive.getPath() + " (not an archive)");
        if (archive.getEnclArchive() != null)
            throw new IllegalArgumentException(archive.getPath() + " (not a top level archive)");
        ArchiveController.updateAll(archive.getCanOrAbsPath(),
                waitInputStreams, closeInputStreams,
                waitOutputStreams, closeOutputStreams,
                true, true);
    }

    /**
     * Returns a proxy instance which encapsulates <i>live</i> statistics
     * about the total set of archives operated by this package.
     * Any call to a method of the returned instance returns an element of
     * the statistics which is lively updated, so there is no need to
     * repeatedly call this method in order to get updated statistics.
     * <p>
     * Note that this method returns <em>live</em> statistics rather than
     * <em>real time</em> statistics.
     * So there may be a slight delay until the values returned reflect
     * the actual state of this package.
     * This delay increases if the system is under heavy load.
     *
     * @see ArchiveStatistics
     */
    public static final ArchiveStatistics getLiveArchiveStatistics() {
        return ArchiveController.getLiveStatistics();
    }

    /**
     * This class attribute controls whether the enclosing archive
     * file(s) and the parent directories in the(se) archive file(s)
     * must already exist if an application tries to create a new entry.
     * <p>
     * If set to <code>false</code>, this package will behave as if the
     * archive entry trying to be created would be a regular node in the
     * real file system.
     * Thus, its parent directories and any enclosing archive files
     * must already exist.
     * <p>
     * If set to <code>true</code>, any missing enclosing archive files
     * and directories are created automatically instead,
     * with the exception of the parent directory of the outermost enclosing
     * archive file which must already exist.
     * This allows an application to create archive files and their
     * directories on the go.
     * <p>
     * By default, this class attribute is set to <code>true</code>.
     * Setting it to <code>false</code> will restrict you from using a nifty
     * feature of this package, but may be reasonable if an application
     * requires a strict emulation of a native filesystem.
     */
    public static final void setLenient(final boolean lenient) {
        File.lenient = lenient;
    }
    
    /** @see #setLenient(boolean) */
    public static final boolean isLenient() {
        return lenient;
    }

    /**
     * This class property controls how archive files are recognized.
     * When a new <code>File</code> instance is created and no
     * {@link ArchiveDetector} is provided to the constructor,
     * or when some method of this class are called which accept an
     * <code>ArchiveDetector</code> parameter,
     * then this class property is used.
     * Changing this value affects all newly created <code>File</code>
     * instances, but not any existing ones.
     * 
     * @param defaultArchiveDetector The default {@link ArchiveDetector} to use
     *        for newly created <code>File</code> instances which has not
     *        been created with an explicit <code>ArchiveDetector</code>.
     *
     * @throws NullPointerException If <code>defaultArchiveDetector</code> is
     *         <code>null</code>.
     *
     * @see ArchiveDetector
     * @see #getDefaultArchiveDetector()
     */
    public static final void setDefaultArchiveDetector(
            final ArchiveDetector defaultArchiveDetector) {
        if (defaultArchiveDetector == null)
            throw new NullPointerException();

        File.defaultArchiveDetector = defaultArchiveDetector;
    }

    /**
     * Returns the default {@link ArchiveDetector} to be used if no
     * <code>ArchiveDetector</code> is passed explicitly to the constructor
     * of a <code>File</code> instance or methods which accept this parameter.
     * <p>
     * This is initially set to <code>ArchiveDetector.DEFAULT</code>
     *
     * @see ArchiveDetector
     * @see #setDefaultArchiveDetector(ArchiveDetector)
     */
    public static final ArchiveDetector getDefaultArchiveDetector() {
        return defaultArchiveDetector;
    }

    /**
     * Equivalent to <code>archiveDetector.createFile(this)</code>.
     * Subclasses should not to overwrite this method, but provide a custom
     * implementation of the {@link FileFactory} interface instead.
     */
    public Object clone() {
        return archiveDetector.createFile(this);
    }

    /**
     * Behaves like the superclass implementation, but actually either
     * returns <code>null</code> or a new instance of this class, so you can
     * safely cast it.
     */
    public java.io.File getParentFile() {
        final java.io.File parent = delegate.getParentFile();
        if (parent == null)
            return null;

        assert super.getName().equals(delegate.getName());
        if (enclArchive != null
                && enclArchive.getPath().length() == parent.getPath().length()) {
            assert enclArchive.getPath().equals(parent.getPath());
            return enclArchive;
        }

        // This must not only be called for performance reasons, but also in
        // order to prevent the parent pathname from being rescanned for
        // archive files with a different archiveDetector, which could
        // trigger an update and reconfiguration of the respective
        // archive controller!
        return archiveDetector.createFile(parent, enclArchive);
    }
    
    /**
     * Returns the first parent directory (starting from this file) which is
     * <em>not</em> an archive file or a file located in an archive file.
     */
    public File getNonArchivedParentFile() {
        final File enclArchive = this.enclArchive;
        return enclArchive != null
                ? enclArchive.getNonArchivedParentFile()
                : (File) getParentFile();
    }
    
    /**
     * Behaves like the superclass implementation, but actually either
     * returns <code>null</code> or a new instance of this class, so you can
     * safely cast it.
     *
     * @see java.io.File#getAbsoluteFile() java.io.File.getAbsoluteFile()
     */
    public java.io.File getAbsoluteFile() {
        File enclArchive = this.enclArchive;
        if (enclArchive != null)
            enclArchive = (File) enclArchive.getAbsoluteFile();
        return archiveDetector.createFile(this, delegate.getAbsoluteFile(), enclArchive);
    }

    /**
     * Similar to {@link #getAbsoluteFile()}, but removes any <code>"."</code>
     * and <code>".."</code> directories from the pathname wherever possible.
     * The result is similar to {@link #getCanonicalFile()}, but symbolic
     * links are not resolved.
     * This could be used if <code>getCanonicalFile()</code> throws an
     * IOException.
     *
     * @see #getNormalizedFile()
     */
    public File getNormalizedAbsoluteFile() {
        File enclArchive = this.enclArchive;
        if (enclArchive != null)
            enclArchive = enclArchive.getNormalizedAbsoluteFile();
        return archiveDetector.createFile(this, normalize(delegate.getAbsoluteFile()), enclArchive);
    }

    /**
     * Removes any <code>"."</code> and <code>".."</code> directories from the
     * absolute pathname wherever possible.
     * 
     * @return The normalized absolute pathname of this file as a {@link String}.
     *
     * @since TrueZIP 6.0
     */
    public String getNormalizedAbsolutePath() {
        return Path.normalize(getAbsolutePath(), separatorChar);
    }

    /**
     * Removes any <code>"."</code> and <code>".."</code> directories from the
     * pathname wherever possible.
     * 
     * @return If this file is already normalized, it is returned.
     *         Otherwise a new instance of this class is returned.
     */
    public File getNormalizedFile() {
        final java.io.File normalizedFile = normalize(this);
        if (normalizedFile == this)
            return this;
        assert normalizedFile != null;
        assert !(normalizedFile instanceof File);
        assert normalize(enclArchive) == enclArchive;
        return archiveDetector.createFile(this, normalizedFile, enclArchive);
    }

    /**
     * Removes any <code>"."</code> and <code>".."</code> directories from the
     * pathname wherever possible.
     * 
     * @return The normalized pathname of this file as a {@link String}.
     *
     * @since TrueZIP 6.0
     */
    public String getNormalizedPath() {
        return Path.normalize(getPath(), separatorChar);
    }

    /**
     * Behaves like the superclass implementation, but actually either
     * returns <code>null</code> or a new instance of this class, so you can
     * safely cast it.
     *
     * @see java.io.File#getCanonicalFile() java.io.File.getCanonicalFile()
     */
    public java.io.File getCanonicalFile() throws IOException {
        File enclArchive = this.enclArchive;
        if (enclArchive != null)
            enclArchive = (File) enclArchive.getCanonicalFile();
        // Note: entry.getCanonicalFile() may change case!
        return archiveDetector.createFile(this, delegate.getCanonicalFile(), enclArchive);
    }

    /**
     * This convenience method simply returns the canonical form of this
     * abstract pathname or the normalized absolute form if resolving the
     * prior fails.
     *
     * @return The canonical or absolute pathname of this file as a
     *         <code>File</code> instance.
     */
    public final File getCanOrAbsFile() {
        File enclArchive = this.enclArchive;
        if (enclArchive != null)
            enclArchive = enclArchive.getCanOrAbsFile();
        return archiveDetector.createFile(this, getCanOrAbsFile(delegate), enclArchive);
    }

    private static java.io.File getCanOrAbsFile(java.io.File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException failure) {
            final java.io.File parent = file.getParentFile();
            return normalize(parent != null
                    ? new java.io.File(getCanOrAbsFile(parent), file.getName())
                    : file.getAbsoluteFile());
        }
    }

    /**
     * This convenience method simply returns the canonical form of this
     * abstract pathname or the normalized absolute form if resolving the
     * prior fails.
     *
     * @return The canonical or absolute pathname of this file as a
     *         <code>String</code> instance.
     *
     * @since TrueZIP 6.0
     */
    public String getCanOrAbsPath() {
        return getCanOrAbsFile().getPath();
    }

    /**
     * Returns <code>true</code> if and only if this abstract file name
     * represents a valid name for an archive file.
     * Whether or not this is true depends on the {@link ArchiveDetector} used
     * to construct this file object or the
     * {@link #getDefaultArchiveDetector()} if no <code>ArchiveDetector</code>
     * was explicitly passed to the constructor.
     * <p>
     * Please note that no tests on the actual file contents are performed!
     * If you need to know whether this file is really an archive file
     * (and the correct password has been entered in case its RAES encrypted),
     * you should call {@link #isDirectory()} too.
     * This will mount the virtual file system from the archive and return
     * <code>true</code> if and only if this is a valid archive.
     *
     * @see #isDirectory()
     */
    public final boolean isArchive() {
        return innerArchive == this;
    }
    
    /**
     * Returns true if and only if this object is an entry located within an
     * archive.
     */
    public final boolean isEntry() {
        return enclEntryName != null;
    }
    
    /**
     * Returns the innermost archive file in this pathname.
     * I.e. if this object is a archive file, then this method returns
     * this object.
     * If this object is a file or directory located within a
     * archive file, then this methods returns the file representing the
     * enclosing archive file, or <code>null</code> otherwise.
     * <p>
     * This method always returns an undotified pathname, i.e. all
     * occurences of <code>"."</code> and <code>".."</code> in the pathname are
     * removed according to their meaning wherever possible.
     * <p>
     * In order to support nesting levels greater than one, this method returns
     * a <code>File</code>, i.e. it could be an entry within another archive
     * file again.
     */
    public final File getInnerArchive() {
        return innerArchive;
    }
    
    /**
     * Returns the entry name in the innermost archive file.
     * I.e. if this object is a archive file, then this method returns
     * the empty string <code>""</code>.
     * If this object is a file or directory located within an
     * archive file, then this method returns the relative pathname of
     * the entry in the enclosing archive file separated by the entry
     * separator character <code>'/'</code>, or <code>null</code>
     * otherwise.
     * <p>
     * This method always returns an undotified pathname, i.e. all
     * occurences of <code>"."</code> and <code>".."</code> in the pathname are
     * removed according to their meaning wherever possible.
     */
    public final String getInnerEntryName() {
        return innerEntryName;
    }

    /**
     * Returns the enclosing archive file in this pathname.
     * I.e. if this object is an entry located within an archive file,
     * then this method returns the file representing the enclosing archive
     * file, or <code>null</code> otherwise.
     * <p>
     * This method always returns an undotified pathname, i.e. all
     * occurences of <code>"."</code> and <code>".."</code> in the pathname are
     * removed according to their meaning wherever possible.
     * <p>
     * In order to support nesting levels greater than one, this method returns
     * a <code>File</code>, i.e. it could be an entry within another archive
     * file again.
     */
    public final File getEnclArchive() {
        return enclArchive;
    }
    
    /**
     * Returns the entry pathname in the enclosing archive file.
     * I.e. if this object is an entry located within a archive file,
     * then this method returns the relative pathname of the entry in the
     * enclosing archive file separated by the entry separator character
     * <code>'/'</code>, or <code>null</code> otherwise.
     * <p>
     * This method always returns an undotified pathname, i.e. all
     * occurences of <code>"."</code> and <code>".."</code> in the pathname are
     * removed according to their meaning wherever possible.
     */
    public final String getEnclEntryName() {
        return enclEntryName;
    }

    /**
     * Returns the {@link ArchiveDetector} that was used to construct this
     * object - never <code>null</code>.
     */
    public final ArchiveDetector getArchiveDetector() {
        return archiveDetector;
    }

    // TODO: Remove this method!
    /**
     * Returns the legacy {@link java.io.File java.io.File} object to which
     * most methods of this class delegate if this object does not represent
     * an archive file or one of its entries.
     * This is required for cooperation with some other pacakages which
     * inherit from our super class.
     * 
     * @return An instance of the {@link java.io.File java.io.File} class or
     *         one of its subclasses, but never an instance of this class or
     *         its subclasses and never <code>null</code>.
     * @deprecated This method exists for implementation purposes only!
     *             You should actually never use it - it is a candidate to
     *             vanish for the next major version number.
     */
    public final java.io.File getDelegate() {
        return delegate;
    }
    
    /**
     * Returns the archive controller for this file if this is an archive file,
     * or <code>null</code> otherwise.
     */
    final ArchiveController getArchiveController() {
        return archiveController;
    }
    
    /**
     * Returns <code>true</code> if and only if the path name represented
     * by this instance is a direct or indirect parent of the path name
     * represented by the specified <code>file</code>.
     * <p>
     * <b>Note:</b>
     * <ul>
     * <li>This method uses the canonical pathnames or, if failing to
     *     canonicalize the path names, at least the normalized absolute
     *     pathnames in order to compute reliable results.
     * <li>This method does <em>not</em> test the actual status
     *     of any file or directory in the file system.
     *     It just tests the path names.
     * </ul>
     *
     * @param file The path name to test for being a child of this path name.
     *
     * @throws NullPointerException If the parameter is <code>null</code>.
     */
    public boolean isParentOf(java.io.File file) {
        // Canonicalise both files and call the actual implementation
        File canOrAbsFile = getCanOrAbsFile();
        try {
            return containsImpl(canOrAbsFile, file.getCanonicalFile().getParentFile());
        } catch (IOException exc) {
            return containsImpl(canOrAbsFile, normalize(file.getAbsoluteFile()).getParentFile());
        }
    }
    
    /**
     * Returns <code>true</code> if and only if the path name represented
     * by this instance contains the path name represented by the specified
     * <code>file</code>,
     * where a path name is said to contain another path name if and only
     * if it is equal or a parent of the other path name.
     * <p>
     * <b>Note:</b>
     * <ul>
     * <li>This method uses the canonical pathnames or, if failing to
     *     canonicalize the path names, at the least normalized absolute
     *     pathnames in order to compute reliable results.
     * <li>This method does <em>not</em> test the actual status
     *     of any file or directory in the file system.
     *     It just tests the path names.
     * </ul>
     *
     * @param file The path name to test for being contained by this path name.
     *
     * @throws NullPointerException If the parameter is <code>null</code>.
     *
     * @since TrueZIP 5.1
     */
    public boolean contains(java.io.File file) {
        return contains(this, file);
    }

    /**
     * Returns <code>true</code> if and only if the path name represented
     * by <code>a</code> contains the path name represented by <code>b</code>,
     * where a path name is said to contain another path name if and only
     * if it is equal or a parent of the other path name.
     * <p>
     * <b>Note:</b>
     * <ul>
     * <li>This method uses the canonical pathnames or, if failing to
     *     canonicalize the path names, at least the normalized absolute
     *     pathnames in order to compute reliable results.
     * <li>This method does <em>not</em> test the actual status
     *     of any file or directory in the file system.
     *     It just tests the path names.
     * </ul>
     *
     * @param a The path name to test for containing <code>b</code>.
     * @param b The path name to test for being contained by <code>a</code>.
     * @throws NullPointerException If any parameter is <code>null</code>.
     * @since TrueZIP 5.1
     */
    public static boolean contains(java.io.File a, java.io.File b) {
        a = getCanOrAbsFile(a);
        b = getCanOrAbsFile(b);
        return containsImpl(a, b);
    }
    
    private static boolean containsImpl(
            final java.io.File a,
            final java.io.File b) {
        if (b == null)
            return false;

        String aPath = a.getPath();
        String bPath = b.getPath();
        // Windows and MacOS are case preserving, however UNIX is case
        // sensitive. If we meet an unknown platform, we assume that it is
        // case preserving, which means that two pathnames are considered
        // equal if they differ by case only.
        // In the context of this method, this implements a conservative
        // (in-dubio-contra-reo) parameter check.
        if (separatorChar != '/') {
            aPath = aPath.toLowerCase();
            bPath = bPath.toLowerCase();
        }

        if (!bPath.startsWith(aPath))
            return false;
        final int aLength = aPath.length();
        final int bLength = bPath.length();
        if (aLength == bLength)
            return true;
        else if (aLength < bLength)
            return bPath.charAt(aLength) == separatorChar;
        else
            return false;

        // Old, somewhat slower implementation
        /*if (a.equals(b))
            return true;
        return containsImpl(a, b.getParentFile());*/
    }
    
    /**
     * Returns <code>true</code> if and only if this file denotes a file system
     * root or a UNC (if running on the Windows platform).
     */
    public boolean isFileSystemRoot() {
        File canOrAbsFile = getCanOrAbsFile();
        return roots.contains(canOrAbsFile) || isUNC(canOrAbsFile.getPath());
    }
    
    /**
     * Returns <code>true</code> if and only if this file denotes a UNC.
     * Note that this may be only relevant on the Windows platform.
     */
    public boolean isUNC() {
        return isUNC(getCanOrAbsFile().getPath());
    }

    // TODO: Make this private!
    /**
     * Returns <code>true</code> if and only if the given path is a UNC.
     * Note that this may be only relevant on the Windows platform.
     *
     * @deprecated This method will be made private in the next major version.
     */
    protected static final boolean isUNC(final String path) {
        return path.startsWith(uncPrefix) && path.indexOf(separatorChar, 2) > 2;
    }
    
    public int hashCode() {
        // Note that we cannot just return the pathnames' hash code:
        // Some platforms consider the case of files when comparing file
        // paths and some don't.
        // However, the entries INSIDE a archive file ALWAYS consider
        // case.
        // In addition, on Mac OS the Java implementation is not consistent
        // with the filesystem, i.e. the fs ignores case whereas
        // java.io.File.equals(...) and java.io.File.hashcode() consider case.
        // The following code distinguishes these cases.
        final File enclArchive = this.enclArchive;
        if (enclArchive != null) {
            // This file IS enclosed in a archive file.
            return enclArchive.hashCode() + enclEntryName.hashCode();
        } else {
            // This file is NOT enclosed in a archive file.
            return delegate.hashCode();
        }
    }
    
    /**
     * Tests this abstract pathname for equality with the given object.
     * Returns <code>true</code> if and only if the argument is not
     * <code>null</code> and is an abstract pathname that denotes the same
     * abstract pathname for a file or directory as this abstract pathname.
     * <p>
     * If the given file is not an instance of this class, the call is
     * forwarded to the superclass in order to ensure the required symmetry
     * of {@link Object#equals(Object)}.
     * <p>
     * Otherwise, whether or not two abstract pathnames are equal depends upon the
     * underlying operating and file system:
     * On UNIX systems, alphabetic case is significant in comparing pathnames.
     * On Microsoft Windows systems it is not unless the pathname denotes
     * an entry in an archive file. In the latter case, the left part of the
     * pathname up to the (leftmost) archive file is compared ignoring case
     * while the remainder (the entry name) is compared considering case.
     * This case distinction allows an application on Windows to deal with
     * archive files generated on other platforms which may contain different
     * entry with names that just differ in case (like e.g. hello.txt and
     * HELLO.txt).
     * <p>
     * Thus, on Windows the following assertions all succeed:
     * <pre>
     * File a, b;
     * a = new File("c:\\any.txt");
     * b = new File("C:\\ANY.TXT");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("c:\\any.zip\\test.txt"),
     * b = new File("C:\\ANY.ZIP\\test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("c:/any.zip/test.txt");
     * b = new File("C:\\ANY.ZIP\\test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("c:\\any.zip\\test.txt");
     * b = new File("C:/ANY.ZIP/test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("c:/any.zip/test.txt");
     * b = new File("C:/ANY.ZIP/test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("\\\\localhost\\any.zip\\test.txt");
     * b = new File("\\\\LOCALHOST\\ANY.ZIP\\test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("//localhost/any.zip/test.txt");
     * b = new File("\\\\LOCALHOST\\ANY.ZIP\\test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("\\\\localhost\\any.zip\\test.txt");
     * b = new File("//LOCALHOST/ANY.ZIP/test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("//localhost/any.zip/test.txt");
     * b = new File("//LOCALHOST/ANY.ZIP/test.txt");
     * assert a.equals(b);
     * assert b.equals(a);
     * a = new File("c:\\any.zip\\test.txt");
     * b = new File("c:\\any.zip\\TEST.TXT");
     * assert !a.equals(b); // two different entries in same ZIP file!
     * assert !b.equals(a);
     * </pre>
     *
     * @param other The object to be compared with this abstract pathname.
     *
     * @return <code>true</code> if and only if the objects are equal,
     *         <code>false</code> otherwise
     *
     * @see #compareTo(Object)
     * @see Object#equals(Object)
     */
    public boolean equals(final Object other) {
        if (other instanceof File)
            return compareTo((File) other) == 0;
        else
            return super.equals(other); // don't use entry - would break symmetry requirement!
    }
    
    /**
     * Compares this file's pathname to the given file's pathname.
     * <p>
     * If the given file is not an instance of this class, the call is
     * forwarded to the superclass in order to ensure the required symmetry
     * of {@link Comparable#compareTo(Object)}.
     * <p>
     * Otherwise, whether or not two abstract pathnames compare equal depends
     * upon the underlying operating and file system:
     * On UNIX platforms, alphabetic case is significant in comparing pathnames.
     * On the Windows platform it is not unless the pathname denotes
     * an entry in an archive file. In the latter case, the left part of the
     * pathname up to the (leftmost) archive file is compared in platform
     * dependent manner (hence ignoring case) while the remainder (the entry
     * name) is compared considering case.
     * This case distinction allows an application on the Windows platform to
     * deal with archive files generated on other platforms which may contain
     * different entries with names that just differ in case
     * (like e.g. <code>"hello.txt"</code> and <code>"HELLO.txt"</code>).
     *
     * @param other The file to be compared with this abstract pathname.
     *
     * @return A negative integer, zero, or a positive integer as this object
     *         is less than, equal to, or greater than the given file.
     *
     * @see #equals(Object)
     * @see Comparable#compareTo(Object)
     */
    public int compareTo(java.io.File other) {
        if (this == other)
            return 0;
        
        if (!(other instanceof File)) {
            // Degrade this file to a plain file in order to ensure
            // sgn(this.compareTo(other)) == -sgn(other.compareTo(this)).
            return super.compareTo(other); // don't use entry - would break antisymmetry requirement!
        }
        
        final File file = (File) other;
        
        // Note that we cannot just compare the pathnames:
        // Some platforms consider the case of files when comparing file
        // paths and some don't.
        // However, the entries INSIDE a archive file ALWAYS consider
        // case.
        // The following code distinguishes these cases.
        final File enclArchive = this.enclArchive;
        if (enclArchive != null) {
            // This file IS enclosed in a archive file.
            final File fileEnclArchive = file.enclArchive;
            if (fileEnclArchive != null) {
                // The given file IS enclosed in a archive file, too.
                int ret = enclArchive.compareTo(fileEnclArchive);
                if (ret == 0) {
                    // Now that the paths of the enclosing archive
                    // files compare equal, let's compare the entry names.
                    ret = enclEntryName.compareTo(file.enclEntryName);
                }

                return ret;
            }
        }
        
        // Degrade this file to a plain file in order to ensure
        // sgn(this.compareTo(other)) == -sgn(other.compareTo(this)).
        return super.compareTo(other); // don't use entry - would break antisymmetry requirement!
    }
    
    /**
     * Returns The top level archive file in the pathname or <code>null</code>
     * if this pathname does not denote an archive.
     * A top level archive is not enclosed in another archive.
     * If this does not return <code>null</code>, this denotes the longest
     * part of the pathname which actually may (but does not need to) exist
     * as a regular file in the native file system.
     */
    public File getTopLevelArchive() {
        final File enclArchive = this.enclArchive;
        return enclArchive != null
                ? enclArchive.getTopLevelArchive()
                : innerArchive;
    }
    
    public String getAbsolutePath() {
        return delegate.getAbsolutePath();
    }
    
    public String getCanonicalPath() throws IOException {
        return delegate.getCanonicalPath();
    }
    
    public String getName() {
        return delegate.getName();
    }
    
    public String getParent() {
        return delegate.getParent();
    }
    
    public String getPath() {
        return delegate.getPath();
    }
    
    public boolean isAbsolute() {
        return delegate.isAbsolute();
    }
    
    public boolean isHidden() {
        return delegate.isHidden();
    }
    
    public String toString() {
        return delegate.toString();
    }
    
    public java.net.URI toURI() {
        return delegate.toURI();
    }
    
    public java.net.URL toURL() throws java.net.MalformedURLException {
        return delegate.toURL();
    }

    public boolean exists() {
        if (enclArchive == null)
            return delegate.exists();
        else
            return exists(enclArchive.getArchiveController(), enclEntryName);
    }
    
    private boolean exists(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.exists(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return exists(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.exists();
        } catch (IOException failure) {
            return false;
        }
    }
    
    public boolean isFile() {
        if (innerArchive == null)
            return delegate.isFile();
        else
            return isFile(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private boolean isFile(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.isFile(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            if (isArchive()
                    && failure.getCause() instanceof FileNotFoundException) {
                // This appears to be an archive file, but we could not
                // access it.
                // One of the many reasons may be that the target file is an
                // RAES encrypted ZIP file for which password prompting has
                // been disabled or cancelled by the user.
                // In any of these cases we do not want this package to treat
                // this file like a plain file.
                // For the forementioned case, this implies that the RAES
                // encrypted file is identified as a special file, i.e.
                // exists() returns true, while both isFile() and isDirectory()
                // return false.
                return false;
            } else {
                return isFile(controller.getEnclController(),
                        controller.enclEntryName(entryName));
            }
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            if (isArchive()
                    && failure.getCause() instanceof FileNotFoundException) {
                // dito
                return false;
            } else {
                return delegate.isFile();
            }
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Similar to the super class implementation, but also recognizes archive
     * files.
     * In case an RAES encrypted ZIP file is tested which is accessed for the
     * first time, the user is prompted for the password (if password based
     * encryption is used).
     * 
     * @see #isArchive()
     * @see java.io.File#isDirectory() java.io.File.isDirectory()
     */
    public boolean isDirectory() {
        if (innerArchive == null)
            return delegate.isDirectory();
        else
            return isDirectory(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private boolean isDirectory(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.isDirectory(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return isDirectory(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.isDirectory();
        } catch (IOException failure) {
            // The controller's target file or one of its enclosing archive
            // files is actually a plain file which is not compatible to the
            // archive file format.
            return false;
        }
    }
    
    /**
     * Returns 
     */
    public Icon getOpenIcon() {
        if (innerArchive == null)
            return null;
        else
            return getOpenIcon(innerArchive.getArchiveController(), innerEntryName);
    }

    /**
     * Returns an icon for this file or directory if it is in <i>open</i>
     * state for {@link de.schlichtherle.io.swing.JFileTree}
     * or <code>null</code> if the default should be used.
     */
    private Icon getOpenIcon(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.getOpenIcon(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return getOpenIcon(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (IOException failure) {
            return null;
        }
    }

    /**
     * Returns an icon for this file or directory if it is in <i>closed</i>
     * state for {@link de.schlichtherle.io.swing.JFileTree}
     * or <code>null</code> if the default should be used.
     */
    public Icon getClosedIcon() {
        if (innerArchive == null)
            return null;
        else
            return getClosedIcon(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private Icon getClosedIcon(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.getClosedIcon(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return getOpenIcon(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (IOException failure) {
            return null;
        }
    }
    
    public boolean canRead() {
        // More thorough test than exists
        if (innerArchive == null)
            return delegate.canRead();
        else
            return canRead(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private boolean canRead(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.canRead(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return canRead(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.canRead();
        } catch (IOException failure) {
            return false;
        }
    }
    
    public boolean canWrite() {
        if (innerArchive == null)
            return delegate.canWrite();
        else
            return canWrite(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private boolean canWrite(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.canWrite(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return canWrite(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.canWrite();
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Like the super class implementation, but is aware of archive
     * files in its path.
     * For entries in a archive file, this is effectively a no-op:
     * The method will only return <code>true</code> if the entry exists and the
     * archive file was mounted read only.
     */
    public boolean setReadOnly() {
        if (innerArchive == null)
            return delegate.setReadOnly();
        else
            return setReadOnly(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private boolean setReadOnly(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.setReadOnly(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return setReadOnly(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.setReadOnly();
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Returns the (uncompressed) length of the file.
     * The length returned of a archive file is 0 in order to
     * correctly emulate a directory across all platforms.
     */
    public long length() {
        if (innerArchive == null)
            return delegate.length();
        else
            return length(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private long length(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.length(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return length(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.length();
        } catch (IOException failure) {
            return 0;
        }
    }
    
    /**
     * Returns a <code>long</code> value representing the time this file was last
     * modified, measured in milliseconds since the epoch (00:00:00 GMT,
     * January 1, 1970), or <code>0L</code> if the file does not exist or if an
     * I/O error occurs or if this is a ghost directory in a archive
     * file.
     *
     * @see <a href="package.html">Package description for more information
     *      about ghost directories.</a>
     */
    public long lastModified() {
        if (innerArchive == null)
            return delegate.lastModified();
        else
            return lastModified(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private long lastModified(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.lastModified(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return lastModified(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.lastModified();
        } catch (IOException failure) {
            return 0;
        }
    }
    
    /**
     * Behaves similar to the super class.
     * <p>
     * Please note that calling this method causes a severe performance
     * penalty if the file is an entry in a archive file which has
     * just been written (such as after a normal copy operation).
     * If you want to copy a file's contents as well as its last modification
     * time, use {@link #archiveCopyFrom(java.io.File)} or
     * {@link #archiveCopyTo(java.io.File)} instead.
     *
     * @see #archiveCopyFrom(java.io.File)
     * @see #archiveCopyTo(java.io.File)
     * @see java.io.File#setLastModified(long)
     */
    public boolean setLastModified(long time) {
        if (innerArchive == null)
            return delegate.setLastModified(time);
        else
            return setLastModified(innerArchive.getArchiveController(), innerEntryName, time);
    }
    
    private boolean setLastModified(
            final ArchiveController controller,
            final String entryName,
            final long time) {
        try {
            return controller.setLastModified(entryName, time);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return setLastModified(controller.getEnclController(),
                    controller.enclEntryName(entryName), time);
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.setLastModified(time);
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Returns the names of the members in this directory in a newly
     * created array.
     * The returned array is <em>not</em> sorted.
     * This is the most efficient list method.
     * <p>
     * <b>Note:</b> Archive entries with absolute pathnames are ignored by
     * this method and are never returned.
     */
    public String[] list() {
        if (innerArchive == null)
            return delegate.list();
        else
            return list(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private String[] list(
            final ArchiveController controller,
            final String entryName) {
        try {
            return controller.list(entryName);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return list(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.list();
        } catch (IOException failure) {
            return null;
        }
    }

    /**
     * Returns the names of the members in this directory which are
     * accepted by <code>filenameFilter</code> in a newly created array.
     * The returned array is <em>not</em> sorted.
     * <p>
     * <b>Note:</b> Archive entries with absolute pathnames are ignored by
     * this method and are never returned.
     *
     * @return <code>null</code> if this is not a directory or an archive file,
     *         a valid (but maybe empty) array otherwise.
     */
    public String[] list(final FilenameFilter filenameFilter) {
        if (innerArchive == null)
            return delegate.list(filenameFilter);
        else
            return list(innerArchive.getArchiveController(), innerEntryName,
                    filenameFilter);
    }

    private String[] list(
            final ArchiveController controller,
            final String entryName,
            final FilenameFilter filenameFilter) {
        try {
            return controller.list(entryName, filenameFilter, this);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return list(controller.getEnclController(),
                    controller.enclEntryName(entryName), filenameFilter);
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegate.list(filenameFilter);
        } catch (IOException failure) {
            return null;
        }
    }

    /**
     * Equivalent to {@link #listFiles(FilenameFilter, FileFactory)
     * listFiles((FilenameFilter) null, getArchiveDetector())}.
     */
    public java.io.File[] listFiles() {
        return listFiles((FilenameFilter) null, archiveDetector);
    }

    /**
     * Returns <code>File</code> objects for the members in this directory
     * in a newly created array.
     * The returned array is <em>not</em> sorted.
     * <p>
     * Since TrueZIP 6.4, the returned array is an array of this class.
     * Previously, the returned array was an array of <code>java.io.File</code>
     * which solely contained instances of this class.
     * <p>
     * Note that archive entries with absolute pathnames are ignored by this
     * method and are never returned.
     *
     * @param factory The factory used to create the member file of this
     *        directory.
     *        This could be an {@link ArchiveDetector} in order to detect any
     *        archives by the member file names.
     * @return <code>null</code> if this is not a directory or an archive file,
     *         a valid (but maybe empty) array otherwise.
     */
    public File[] listFiles(final FileFactory factory) {
        return listFiles((FilenameFilter) null, factory);
    }

    /**
     * Equivalent to {@link #listFiles(FilenameFilter, FileFactory)
     * listFiles(filenameFilter, getArchiveDetector())}.
     */
    public java.io.File[] listFiles(final FilenameFilter filenameFilter) {
        return listFiles(filenameFilter, archiveDetector);
    }

    /**
     * Returns <code>File</code> objects for the members in this directory
     * which are accepted by <code>filenameFilter</code> in a newly created
     * array.
     * The returned array is <em>not</em> sorted.
     * <p>
     * Since TrueZIP 6.4, the returned array is an array of this class.
     * Previously, the returned array was an array of <code>java.io.File</code>
     * which solely contained instances of this class.
     * <p>
     * Note that archive entries with absolute pathnames are ignored by this
     * method and are never returned.
     *
     * @param factory The factory used to create the member file of this
     *        directory.
     *        This could be an {@link ArchiveDetector} in order to detect any
     *        archives by the member file names.
     * @return <code>null</code> if this is not a directory or an archive file,
     *         a valid (but maybe empty) array otherwise.
     */
    public File[] listFiles(
            final FilenameFilter filenameFilter,
            final FileFactory factory) {
        if (innerArchive == null)
            return convert(delegate.listFiles(filenameFilter), factory);
        else
            return listFiles(innerArchive.getArchiveController(), innerEntryName,
                    filenameFilter, factory);
    }

    private File[] listFiles(
            final ArchiveController controller,
            final String entryName,
            final FilenameFilter filenameFilter,
            final FileFactory factory) {
        try {
            return controller.listFiles(entryName, filenameFilter, this, factory);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return listFiles(controller.getEnclController(),
                    controller.enclEntryName(entryName),
                    filenameFilter, factory);
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return convert(
                    delegate.listFiles(filenameFilter), factory);
        } catch (IOException failure) {
            return null;
        }
    }

    private static File[] convert(
            final java.io.File[] files,
            final FileFactory factory) {
        if (files == null)
            return null;

        File[] results = new File[files.length];
        for (int i = files.length; 0 <= --i; )
            results[i] = factory.createFile(files[i]);
        
        return results;
    }
    
    /**
     * Equivalent to {@link #listFiles(FileFilter, FileFactory)
     * listFiles(fileFilter, getArchiveDetector())}.
     */
    public final java.io.File[] listFiles(final FileFilter fileFilter) {
        return listFiles(fileFilter, archiveDetector);
    }
    
    /**
     * Returns <code>File</code> objects for the members in this directory
     * which are accepted by <code>fileFilter</code> in a newly created array.
     * The returned array is <em>not</em> sorted.
     * <p>
     * Since TrueZIP 6.4, the returned array is an array of this class.
     * Previously, the returned array was an array of <code>java.io.File</code>
     * which solely contained instances of this class.
     * <p>
     * Note that archive entries with absolute pathnames are ignored by this
     * method and are never returned.
     *
     * @param factory The factory used to create the member file of this
     *        directory.
     *        This could be an {@link ArchiveDetector} in order to detect any
     *        archives by the member file names.
     * @return <code>null</code> if this is not a directory or an archive file,
     *         a valid (but maybe empty) array otherwise.
     */
    public File[] listFiles(
            final FileFilter fileFilter,
            final FileFactory factory) {
        if (innerArchive == null)
            return delegateListFiles(fileFilter, factory);
        else
            return listFiles(innerArchive.getArchiveController(), innerEntryName,
                    fileFilter, factory);
    }

    private File[] delegateListFiles(
            final FileFilter fileFilter,
            final FileFactory factory) {
        // When filtering, we want to pass in <code>de.schlichtherle.io.File</code>
        // objects rather than <code>java.io.File</code> objects, so we cannot
        // just call <code>entry.listFiles(FileFilter)</code>.
        // Instead, we will query the entry for the children names (i.e.
        // Strings) only, construct <code>de.schlichtherle.io.File</code>
        // instances from this and then apply the filter to construct the
        // result list.

        final List filteredList = new ArrayList();
        final String[] children = delegate.list();
        if (children == null)
            return null;

        for (int i = 0, l = children.length; i < l; i++) {
            final String child = children[i];
            final File file = factory.createFile(this, child);
            if (fileFilter == null || fileFilter.accept(file))
                filteredList.add(file);
        }
        final File[] list = new File[filteredList.size()];
        filteredList.toArray(list);

        return list;
    }

    private File[] listFiles(
            final ArchiveController controller,
            final String entryName,
            final FileFilter fileFilter,
            final FileFactory factory) {
        try {
            return controller.listFiles(entryName, fileFilter, this, factory);
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return listFiles(controller.getEnclController(),
                    controller.enclEntryName(entryName),
                    fileFilter, factory);
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            return delegateListFiles(fileFilter, factory);
        } catch (IOException failure) {
            return null;
        }
    }
    
    /**
     * Creates a new, empty file similar to its superclass implementation.
     * <p>
     * Please note that you cannot link a archive file with this
     * method, even if this file's pathname looks like a archive file
     * (like e.g. <code>"archive.zip"</code>) and hence {@link #isArchive()}
     * returns <code>true</code>.
     * <p>
     * To link a archive file, use {@link #mkdir()} on a file object
     * which's pathname looks like a archive file instead or,
     * if {@link #isLenient()} returns <code>true</code>, simply link any of
     * its entries (like e.g. <code>"archive.zip/file.txt"</code>).
     * <p>
     * Please note that this is <em>not</em> an atomic operation if this file
     * is actually an entry located in a archive file.
     * The call will succeed, but other processes will <em>not</em> be able
     * to see this archive entry unless {@link #update()} or {@link #umount()}
     * get called.
     * However, the created file can be seen by this JVM instance just as
     * normal if they are using the classes in this package.
     * 
     * @see java.io.File#createNewFile()
     * @see DefaultArchiveDetector 
     */
    public boolean createNewFile() throws IOException {
        if (enclArchive == null)
            return delegate.createNewFile();
        else
            return createNewFile(enclArchive.getArchiveController(),
                    enclEntryName);
    }
    
    private boolean createNewFile(
            final ArchiveController controller,
            final String entryName)
    throws IOException {
        try {
            return controller.createNewFile(entryName, isLenient());
        } catch (ArchiveController.FalsePositiveException failure) {
            return false; // the exception implies that the entry already exists
        } catch (IOException failure) {
            throw failure;
        }
    }
    
    /**
     * Like the superclass implementation, but also creates empty archive
     * files.
     */
    public boolean mkdir() {
        if (innerArchive == null)
            return delegate.mkdir();
        else
            return mkdir(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private boolean mkdir(
            final ArchiveController controller,
            final String entryName) {
        try {
            controller.mkdir(entryName, isLenient());
            return true;
        } catch (ArchiveController.FalsePositiveEntryException failure) {
            return mkdir(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            // We are trying to link a directory which is enclosed a false
            // positive archive file which is actually a regular
            // directory in the native file system.
            // Now the directory we are trying to link must not be an archive
            // file, because otherwise its controller would have identified
            // the enclosing archive file as a false positive native directory
            // and created its file system accordingly, to the effect that
            // we would never get here.
            assert !isArchive();
            return delegate.mkdir();
        } catch (IOException failure) {
            return false;
        }
    }
    
    public boolean mkdirs() {
        if (innerArchive == null)
            return delegate.mkdirs();
        
        final File parent = (File) getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();

        // TODO: Profile: return parent.isDirectory() && mkdir();
        // May perform better in certain situations where (probably false
        // positive) archive files are involved.
        return mkdir();
    }

    /**
     * Deletes an archive entry, archive or regular node in the native file
     * system.
     *
     * In a nutshell, the rules for a file to be deletable are like this:
     * <ol>
     * <li>
     * If an entry in an archive is to be deleted, the operation will
     * succeed unless the archive is read only, the entry refers to a
     * non-empty directory or there is an open output stream for this
     * entry.
     * An open input stream will not prevent an archive entry to be deleted.
     * <li>
     * If an archive is to be deleted, the operation will succeed unless
     * the archive is not empty or there are any open input or output streams
     * for its entries.
     * <li>
     * If a node in the native file system is to be deleted, the rules of
     * the respective file system apply.
     * </ol>
     *
     * @see java.io.File#delete
     */
    public boolean delete() {
        if (innerArchive == null)
            return delegate.delete();
        else
            return delete(innerArchive.getArchiveController(), innerEntryName);
    }
    
    private boolean delete(
            final ArchiveController controller,
            final String entryName) {
        try {
            controller.delete(entryName);
            return true;
        } catch (ArchiveController.FalsePositiveDirectoryEntryException failure) {
            return delete(controller.getEnclController(),
                    controller.enclEntryName(entryName));
        } catch (ArchiveController.FalsePositiveFileEntryException failure) {
            if (isArchive()
                    && failure.getCause() instanceof FileNotFoundException) {
                // This appears to be an archive file, but we could not
                // access it.
                // One of the many reasons may be that the target file is an
                // RAES encrypted ZIP file for which password prompting has
                // been disabled or cancelled by the user.
                // Another reason could be that the archive has been tampered
                // with and hence authentication failed.
                // In any of these cases we do not want this package to treat
                // this file like a plain file and delete it.
                return false;
            } else {
                return delete(controller.getEnclController(),
                        controller.enclEntryName(entryName));
            }
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            if (isArchive()
                    && !delegate.isDirectory()
                    && failure.getCause() instanceof FileNotFoundException) {
                // dito
                return false;
            } else {
                return delegate.delete();
            }
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Deletes the entire directory tree represented by this object,
     * regardless whether this is a file or directory, whether the directory
     * is empty or not and whether the file or directory is actually an
     * archive file, an entry in an archive file or not enclosed in an
     * archive file at all.
     *
     * @return <code>true</code> if and only if the entire directory tree was
     *         successfully deleted.
     */
    public boolean deleteAll() {
        boolean ok = true;
        if (isDirectory()) {
            java.io.File[] members = listFiles(ArchiveDetector.NULL);
            for (int i = members.length; --i >= 0; )
                ok &= ((File) members[i]).deleteAll();
        }
        return ok && delete();
    }
    
    public void deleteOnExit() {
        if (innerArchive == null) {
            delegate.deleteOnExit();
            return;
        }

        if (isArchive()) {
            // We cannot prompt the user for a password in the shutdown hook
            // in case this is an RAES encrypted ZIP file.
            // So we do this now instead.
            isDirectory();
        }

        ArchiveController.ShutdownHook.deleteOnExit.add(this);
    }
    
    /**
     * Equivalent to {@link #renameTo(java.io.File, ArchiveDetector)
     * renameTo(dst, getArchiveDetector())}.
     */
    public final boolean renameTo(final java.io.File dst) {
        return renameTo(dst, archiveDetector);
    }

    /**
     * Behaves similar to the super class, but renames this file or directory
     * by recursively copying its data if this object or the <code>dst</code>
     * object is either an archive file or an entry located in an archive file.
     * 
     * @param archiveDetector The object used to detect archive files
     *        in the pathname.
     */
    public boolean renameTo(
            final java.io.File dst,
            final ArchiveDetector archiveDetector) {
        if (innerArchive == null) {
            if (!(dst instanceof File) || ((File) dst).innerArchive == null)
                return delegate.renameTo(dst);
        }
        
        return !dst.exists()
            && !contains(this, dst)
            &&  mv(this, dst, archiveDetector);
    }
    
    private static boolean mv(
            final java.io.File src,
            final java.io.File dst,
            final ArchiveDetector archiveDetector) {
        boolean ok = true;
        if (src.isDirectory()) {
            dst.mkdir();
            //ok = dst.mkdir();
            //if (ok) {
                final String[] members = src.list();
                if (dst instanceof File && ((File) dst).innerArchive != null) {
                    // Create sorted entries if writing a new archive file.
                    // This is courtesy only, so natural order is sufficient.
                    Arrays.sort(members);
                }
                for (int i = 0, l = members.length; i < l; i++) {
                    String member = members[i];
                    ok &= mv(
                            archiveDetector.createFile(src, member),
                            archiveDetector.createFile(dst,  member),
                            archiveDetector);
                }
                long srcLastModified = src.lastModified();
                // Use current time for copies of ghost directories!
                if (srcLastModified > 0
                        || !((src instanceof File) && ((File) src).isEntry()))
                    ok &= dst.setLastModified(srcLastModified);
            //}
        } else if (src.isFile()) { // !isDirectory()
            try {
                cp_p(src, dst);
            } catch (IOException failure) {
                ok = false;
            }
        } else {
            ok = false;
        }
        return ok && src.delete(); // does not unlink if not ok!
    }
    
    /**
     * This method copies from the input stream <code>in</code> to this file or
     * entry in an archive file and closes the input stream.
     * <p>
     * This method provides limited support for transactions, i.e. if the
     * destination file cannot get opened for writing, nothing is changed.
     * If the destination file can get opened for writing, but the writing
     * fails for any reason (e.g. not enough capacity on the destination file
     * system), the destination file gets deleted.
     * If the destination file cannot get deleted, it gets truncated.
     * <p>
     * Like all <code>copy</code> methods in this class, this method
     * is guaranteed to <em>always</em> close any parameter stream
     * - even if the copying fails and an IOException is thrown!
     *
     * @return <code>true</code> if and only if the operation succeeded.
     */
    public boolean copyFrom(final InputStream in) {
        try {
            final OutputStream out = archiveDetector.createFileOutputStream(this, false);
            try {
                cp(in, out); // always closes in and out
                return true;
            } catch (IOException failure) {
                if (!delete()) {
                    archiveDetector.createFileOutputStream(this, false).close(); // truncate
                }
            }
        } catch (IOException failure) {
        }
        return false;
    }
    
    /**
     * This method copies from the file <code>src</code>
     * to this file or entry in an archive file.
     * It perfectly supports nested archive files for this object.
     * <p>
     * This method provides limited support for transactions, i.e. if the
     * destination file cannot get opened for writing, nothing is changed.
     * If the destination file can get opened for writing, but the writing
     * fails for any reason (e.g. not enough capacity on the destination file
     * system), the destination file gets deleted.
     * If the destination file cannot get deleted, it gets truncated.
     * <p>
     * This method supports Direct Data Copying (DDC):
     * If your application uses this method to copy actually one archive entry
     * to another archive entry, the DDC feature directly copies the deflated
     * data of the source archive entry to the destination archive entry
     * without decompressing the source data and recompressing the destination
     * data again. DDC works regardless of where the source and destination
     * archive entries are located, so they could be located in separate
     * archive files, the same archive file or even archive files enclosed
     * in other archive files (like with copying from e.g.
     * <code>"outer.zip/inner.zip/src.txt"</code> to
     * <code>"outer.zip/inner.zip/nuts.zip/dst.txt"</code>).
     * <p>
     * Please note that the interpretation of the DDC feature is archive
     * driver specific. The interpretation explained above is specific to
     * TrueZIP's default ZIP32 driver family and may or may not apply to
     * other drivers.
     * In particular, for TrueZIP's default TAR driver family, the DDC
     * implementation avoids the need to create an additional temporary file,
     * but does not affect compression because the TAR file format does not
     * support compression.
     *
     * @return <code>true</code> if and only if the operation succeeded.
     */
    public boolean copyFrom(final java.io.File src) {
        try {
            cp(src, this);
            return true;
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Similar to {@link #copyFrom(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * This version uses the {@link ArchiveDetector} which was used to construct
     * this object to detect any archive files in both directory trees.
     */
    public boolean copyAllFrom(final java.io.File src) {
        return cp_r(src, this, archiveDetector, archiveDetector, false);
    }
    
    /**
     * Similar to {@link #copyFrom(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * 
     * 
     * 
     * @param archiveDetector The object used to detect any archive files
     *        in both directory trees.
     */
    public boolean copyAllFrom(
            final java.io.File src,
            final ArchiveDetector archiveDetector) {
        return cp_r(src, this, archiveDetector, archiveDetector, false);
    }
    
    /**
     * Similar to {@link #copyFrom(java.io.File)}, but works recursively.
     * By using different {@link ArchiveDetector}s for the source and destination,
     * this method can be used to do fancy stuff like replacing any archive
     * file in the source tree with a plain directory in the
     * destination tree (where <code>srcArchiveDetector</code> could be
     * {@link #getArchiveDetector()} and <code>dstArchiveDetector</code> would be
     * {@link ArchiveDetector#NULL}) or changing the encoding by subclassing the
     * {@link DefaultArchiveDetector}.
     * <p>
     * This method does not copy or overwrite special files.
     * 
     * @param srcArchiveDetector The object used to detect any archive files
     *        in the source directory tree.
     * @param dstArchiveDetector The object used to detect any archive files
     *        in the destination directory tree.
     */
    public boolean copyAllFrom(
            final java.io.File src,
            final ArchiveDetector srcArchiveDetector,
            final ArchiveDetector dstArchiveDetector) {
        return cp_r(src, this, srcArchiveDetector, dstArchiveDetector, false);
    }
    
    /**
     * This method copies this file or entry in an archive file to the output
     * stream <code>out</code>.
     * <p>
     * Like all <code>copy</code> methods in this class, this method
     * is guaranteed to <em>always</em> close all parameter streams
     * - even if the copying fails and an IOException is thrown!
     *
     * @return <code>true</code> if and only if the operation succeeded.
     */
    public boolean copyTo(final OutputStream out) {
        try {
            final InputStream in = archiveDetector.createFileInputStream(this);
            cp(in, out); // always closes in and out
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
    
    /**
     * This method copies this file or entry in an archive file to the file
     * <code>dst</code>. It perfectly supports nested archive files for this
     * object.
     * <p>
     * This method provides limited support for transactions, i.e. if the
     * destination file cannot get opened for writing, nothing is changed.
     * If the destination file can get opened for writing, but the writing
     * fails for any reason (e.g. not enough capacity on the destination file
     * system), the destination file gets deleted.
     * If the destination file cannot get deleted, it gets truncated.
     * <p>
     * This method supports Direct Data Copying (DDC):
     * If your application uses this method to copy actually one archive entry
     * to another archive entry, the DDC feature directly copies the deflated
     * data of the source archive entry to the destination archive entry
     * without decompressing the source data and recompressing the destination
     * data again. DDC works regardless of where the source and destination
     * archive entries are located, so they could be located in separate
     * archive files, the same archive file or even archive files enclosed
     * in other archive files (like with copying from e.g.
     * <code>"outer.zip/inner.zip/src.txt"</code> to
     * <code>"outer.zip/inner.zip/nuts.zip/dst.txt"</code>).
     * <p>
     * Please note that the interpretation of the DDC feature is archive
     * driver specific. The interpretation explained above is specific to
     * TrueZIP's default ZIP32 driver family and may or may not apply to
     * other drivers.
     * In particular, for TrueZIP's default TAR driver family, the DDC
     * implementation avoids the need to create an additional temporary file,
     * but does not affect compression because the TAR file format does not
     * support compression.
     *
     * @return <code>true</code> if the file has been successfully copied.
     */
    public boolean copyTo(final java.io.File dst) {
        try {
            cp(this, dst);
            return true;
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Similar to {@link #copyTo(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * This version uses the {@link ArchiveDetector} which was used to construct
     * this object to detect any archive files in both directory trees.
     */
    public boolean copyAllTo(final java.io.File dst) {
        return cp_r(this, dst, archiveDetector, archiveDetector, false);
    }
    
    /**
     * Similar to {@link #copyTo(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * 
     * 
     * 
     * @param archiveDetector The object used to detect any archive files
     *        in both directory trees.
     */
    public boolean copyAllTo(
            final java.io.File dst,
            final ArchiveDetector archiveDetector) {
        return cp_r(this, dst, archiveDetector, archiveDetector, false);
    }
    
    /**
     * Similar to {@link #copyTo(java.io.File)}, but works recursively.
     * By using different {@link ArchiveDetector}s for the source and destination,
     * this method can be used to do fancy stuff like replacing any archive
     * file in the source tree with a plain directory in the
     * destination tree (where <code>srcArchiveDetector</code> could be
     * {@link #getArchiveDetector()} and <code>dstArchiveDetector</code> would be
     * {@link ArchiveDetector#NULL}) or changing the encoding by subclassing the
     * {@link DefaultArchiveDetector}.
     * <p>
     * This method does not copy or overwrite special files.
     * 
     * @param srcArchiveDetector The object used to detect any archive files
     *        in the source directory tree.
     * @param dstArchiveDetector The object used to detect any archive files
     *        in the destination directory tree.
     */
    public boolean copyAllTo(
            final java.io.File dst,
            final ArchiveDetector srcArchiveDetector,
            final ArchiveDetector dstArchiveDetector) {
        return cp_r(this, dst, srcArchiveDetector, dstArchiveDetector, false);
    }
    
    /**
     * This method copies from the file <code>src</code>
     * to this file or entry in an archive file and sets the last modication time
     * of this file or entry in an archive file to the last modification time
     * of the source file.
     * It perfectly supports nested archive files for this object.
     * <p>
     * This method provides limited support for transactions, i.e. if the
     * destination file cannot get opened for writing, nothing is changed.
     * If the destination file can get opened for writing, but the writing
     * fails for any reason (e.g. not enough capacity on the destination file
     * system), the destination file gets deleted.
     * If the destination file cannot get deleted, it gets truncated.
     * <p>
     * This method supports Direct Data Copying (DDC):
     * If your application uses this method to copy actually one archive entry
     * to another archive entry, the DDC feature directly copies the deflated
     * data of the source archive entry to the destination archive entry
     * without decompressing the source data and recompressing the destination
     * data again. DDC works regardless of where the source and destination
     * archive entries are located, so they could be located in separate
     * archive files, the same archive file or even archive files enclosed
     * in other archive files (like with copying from e.g.
     * <code>"outer.zip/inner.zip/src.txt"</code> to
     * <code>"outer.zip/inner.zip/nuts.zip/dst.txt"</code>).
     * <p>
     * Please note that the interpretation of the DDC feature is archive
     * driver specific. The interpretation explained above is specific to
     * TrueZIP's default ZIP32 driver family and may or may not apply to
     * other drivers.
     * In particular, for TrueZIP's default TAR driver family, the DDC
     * implementation avoids the need to create an additional temporary file,
     * but does not affect compression because the TAR file format does not
     * support compression.
     *
     * @return <code>true</code> if the file has been successfully copied.
     * @param src source file
     */
    public boolean archiveCopyFrom(final java.io.File src) {
        try {
            cp_p(src, this);
            return true;
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Similar to {@link #archiveCopyFrom(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * This version uses the {@link ArchiveDetector} which was used to construct
     * this object to detect any archive files in both directory trees.
     */
    public boolean archiveCopyAllFrom(final java.io.File src) {
        return cp_r(src, this, archiveDetector, archiveDetector, true);
    }
    
    /**
     * Similar to {@link #archiveCopyFrom(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * 
     * @param archiveDetector The object used to detect any archive files
     *        in both directory trees.
     */
    public boolean archiveCopyAllFrom(
            final java.io.File src,
            final ArchiveDetector archiveDetector) {
        return cp_r(src, this, archiveDetector, archiveDetector, true);
    }
    
    /**
     * Similar to {@link #archiveCopyFrom(java.io.File)}, but works recursively.
     * By using different {@link ArchiveDetector}s for the source and destination,
     * this method can be used to do fancy stuff like replacing any archive
     * file in the source tree with a plain directory in the
     * destination tree (where <code>srcArchiveDetector</code> could be
     * {@link #getArchiveDetector()} and <code>dstArchiveDetector</code> would be
     * {@link ArchiveDetector#NULL}) or changing the encoding by subclassing the
     * {@link DefaultArchiveDetector}.
     * <p>
     * This method does not copy or overwrite special files.
     * 
     * @param srcArchiveDetector The object used to detect any archive files
     *        in the source directory tree.
     * @param dstArchiveDetector The object used to detect archive files
     *        in the destination directory tree.
     */
    public boolean archiveCopyAllFrom(
            final java.io.File src,
            final ArchiveDetector srcArchiveDetector,
            final ArchiveDetector dstArchiveDetector) {
        return cp_r(src, this, srcArchiveDetector, dstArchiveDetector, true);
    }
    
    /**
     * This method copies this file or entry in an archive file to the file
     * <code>dst</code> and sets the last modification
     * time of the destination to the last modification time of this file
     * or entry in an archive file.
     * It perfectly supports nested archive files for this object.
     * <p>
     * This method provides limited support for transactions, i.e. if the
     * destination file cannot get opened for writing, nothing is changed.
     * If the destination file can get opened for writing, but the writing
     * fails for any reason (e.g. not enough capacity on the destination file
     * system), the destination file gets deleted.
     * If the destination file cannot get deleted, it gets truncated.
     * <p>
     * This method supports Direct Data Copying (DDC):
     * If your application uses this method to copy actually one archive entry
     * to another archive entry, the DDC feature directly copies the deflated
     * data of the source archive entry to the destination archive entry
     * without decompressing the source data and recompressing the destination
     * data again. DDC works regardless of where the source and destination
     * archive entries are located, so they could be located in separate
     * archive files, the same archive file or even archive files enclosed
     * in other archive files (like with copying from e.g.
     * <code>"outer.zip/inner.zip/src.txt"</code> to
     * <code>"outer.zip/inner.zip/nuts.zip/dst.txt"</code>).
     * <p>
     * Please note that the interpretation of the DDC feature is archive
     * driver specific. The interpretation explained above is specific to
     * TrueZIP's default ZIP32 driver family and may or may not apply to
     * other drivers.
     * In particular, for TrueZIP's default TAR driver family, the DDC
     * implementation avoids the need to create an additional temporary file,
     * but does not affect compression because the TAR file format does not
     * support compression.
     *
     * @return <code>true</code> if the file has been successfully copied.
     */
    public boolean archiveCopyTo(java.io.File dst) {
        try {
            cp_p(this, dst);
            return true;
        } catch (IOException failure) {
            return false;
        }
    }
    
    /**
     * Similar to {@link #archiveCopyTo(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * This version uses the {@link ArchiveDetector} which was used to construct
     * this object to detect any archive files in both directory trees.
     */
    public boolean archiveCopyAllTo(final java.io.File dst) {
        return cp_r(this, dst, archiveDetector, archiveDetector, true);
    }
    
    /**
     * Similar to {@link #archiveCopyTo(java.io.File)}, but works recursively.
     * This method does not copy or overwrite special files.
     * 
     * @param archiveDetector The object used to detect any archive files
     *        in both directory trees.
     */
    public boolean archiveCopyAllTo(
            final java.io.File dst,
            final ArchiveDetector archiveDetector) {
        return cp_r(this, dst, archiveDetector, archiveDetector, true);
    }
    
    /**
     * Similar to {@link #archiveCopyTo(java.io.File)}, but works recursively.
     * By using different {@link ArchiveDetector}s for the source and destination,
     * this method can be used to do fancy stuff like replacing any archive
     * file in the source tree with a plain directory in the
     * destination tree (where <code>srcArchiveDetector</code> could be
     * {@link #getArchiveDetector()} and <code>dstArchiveDetector</code> would be
     * {@link ArchiveDetector#NULL}) or changing the encoding by subclassing the
     * {@link DefaultArchiveDetector}.
     * <p>
     * This method does not copy or overwrite special files.
     * 
     * @param srcArchiveDetector The object used to detect any archive files
     *        in the source directory tree.
     * @param dstArchiveDetector The object used to detect any archive files
     *        in the destination directory tree.
     */
    public boolean archiveCopyAllTo(
            final java.io.File dst,
            final ArchiveDetector srcArchiveDetector,
            final ArchiveDetector dstArchiveDetector) {
        return cp_r(this, dst, srcArchiveDetector, dstArchiveDetector, true);
    }
    
    private static final boolean cp_r(
            final java.io.File src,
            final java.io.File dst,
            final ArchiveDetector srcArchiveDetector,
            final ArchiveDetector dstArchiveDetector,
            final boolean preserve) {
        return !contains(src, dst)
            && cp_rImpl(src, dst, srcArchiveDetector, dstArchiveDetector, preserve);
    }
        
    private static boolean cp_rImpl(
            final java.io.File src,
            final java.io.File dst,
            final ArchiveDetector srcArchiveDetector,
            final ArchiveDetector dstArchiveDetector,
            final boolean preserve) {
        if (src.isDirectory()) {
            boolean ok = dst.mkdir() || dst.isDirectory();
            if (ok) {
                final String[] members = src.list();
                if (dst instanceof File && ((File) dst).innerArchive != null) {
                    // Create sorted entries if writing a new archive.
                    // This is a courtesy only, so natural order is sufficient.
                    Arrays.sort(members);
                }
                for (int i = 0, l = members.length; i < l; i++) {
                    final String member = members[i];
                    ok &= cp_rImpl(
                            srcArchiveDetector.createFile(src, member),
                            dstArchiveDetector.createFile(dst, member),
                            srcArchiveDetector, dstArchiveDetector,
                            preserve);
                }
                if (preserve) {
                    long srcLastModified = src.lastModified();
                    // Use current time for copies of ghost directories!
                    if (srcLastModified > 0
                            || !((src instanceof File) && ((File) src).isEntry()))
                        ok &= dst.setLastModified(srcLastModified);
                }
            }
            return ok;
        } else if (src.isFile() && (!dst.exists() || dst.isFile())) {
            try {
                cp(src, dst, preserve);
                return true;
            } catch (IOException failure) {
                return false;
            }
        } else {
            return false;
        }
    }
    
    /**
     * Like {@link #cp(java.io.File, java.io.File)}, but also preserves the
     * last modification time, i.e. the destination file will have the last
     * modification time as the source file.
     *
     * @throws FileBusyException If an archive entry cannot get accessed
     *         because the client application is trying to input or output
     *         to the same archive file simultaneously and the respective
     *         archive driver does not support this or the archive file needs
     *         an automatic update which cannot get performed because the
     *         client is still using other open {@link FileInputStream}s or
     *         {@link FileOutputStream}s for other entries in the same archive
     *         file.
     * @throws FileNotFoundException If either the source or the destination
     *         cannot get accessed.
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     */
    public static final void cp_p(java.io.File src, java.io.File dst)
    throws IOException {
        cp(src, dst, true);
    }
    
    /**
     * Copies <code>src</code> to <code>dst</code>, considering that any of these
     * might be actually located in an archive file.
     * If both files actually <em>are</em> located in an archive file,
     * then the data is copied using Direct Deflated Data Copying (D3C),
     * i.e. the entry data is directly transferred without decompressing
     * (inflating) it first and then compressing (deflating) it again.
     * In addition, the method locks out any concurrent modifications to any
     * of these archive files using the classes within this package.
     *
     * @throws FileBusyException If an archive entry cannot get accessed
     *         because the client application is trying to input or output
     *         to the same archive file simultaneously and the respective
     *         archive driver does not support this or the archive file needs
     *         an automatic update which cannot get performed because the
     *         client is still using other open {@link FileInputStream}s or
     *         {@link FileOutputStream}s for other entries in the same archive
     *         file.
     * @throws FileNotFoundException If either the source or the destination
     *         cannot get accessed.
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     * @throws NullPointerException If any parameter is <code>null</code>.
     */
    public static final void cp(java.io.File src, java.io.File dst)
    throws IOException {
        cp(src, dst, false);
    }

    private static final void cp(
            final java.io.File src,
            final java.io.File dst,
            final boolean preserve)
    throws IOException {
        if (src == null || dst == null)
            throw new NullPointerException();

        final java.io.File canSrc = getCanOrAbsFile(src);
        final java.io.File canDst = getCanOrAbsFile(dst);
        if (canSrc.equals(canDst))
            throw new SameFileException(src, dst);

        try {
            ArchiveController.cp(src, dst, preserve);
        } catch (FileNotFoundException failure) {
            throw failure;
        } catch (IOException failure) {
            dst.delete();
            throw failure;
        }
    }

    /**
     * Copies all data from one stream to another.
     * <p>
     * Like all <code>copy</code> methods in this class, this method
     * is guaranteed to <em>always</em> close all parameter streams
     * - even if the copying fails and an IOException is thrown!
     *
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the input stream.
     * @throws IOException If copying the data fails because of an
     *         IOException in the output stream.
     */
    public static void cp(
            final InputStream in,
            final OutputStream out)
            throws IOException {
        try {
            try {
                cat(in, out);
            } finally {
                out.close();
            }
        } finally {
            try {
                in.close();
            } catch (IOException failure) {
                throw new InputIOException(failure);
            }
        }
    }
    
    /**
     * This method copies from the input stream <code>in</code> to this file or
     * entry in an archive file. It does <b>not</b> close the input stream.
     * It perfectly supports nested archive files for this object.
     * <p>
     * This method provides limited support for transactions, i.e. if the
     * destination file cannot get opened for writing, nothing is changed.
     * If the destination file can get opened for writing, but the writing
     * fails for any reason (e.g. not enough capacity on the destination file
     * system), the destination file gets deleted.
     * <p>
     * Like all of the <code>cat</code> methods in this class, this method
     * is guaranteed <em>never</em> to close any parameter stream.
     *
     * @return <code>true</code> if and only if the operation succeeded.
     */
    public boolean catFrom(final InputStream in) {
        try {
            final OutputStream out = archiveDetector.createFileOutputStream(this, false);
            try {
                try {
                    cat(in, out);
                    return true;
                } finally {
                    out.close();
                }
            } catch (IOException failure) {
                delete();
            }
        } catch (IOException failure) {
        }
        return false;
    }
    
    /**
     * This method copies this file or entry in an archive file to the output
     * stream <code>out</code>. It does <b>not</b> close the stream.
     * It perfectly supports nested archive files for this object.
     * <p>
     * Like all of the <code>cat</code> methods in this class, this method
     * is guaranteed <em>never</em> to close any parameter stream.
     *
     * @return <code>true</code> if and only if the operation succeeded.
     */
    public boolean catTo(final OutputStream out) {
        try {
            final InputStream in = archiveDetector.createFileInputStream(this);
            try {
                cat(in, out);
                return true;
            } finally {
                in.close();
            }
        } catch (IOException failure) {
        }
        return false;
    }
    
    /**
     * Copies all data from one stream to another without closing them.
     * <p>
     * This method uses asynchronous read/write operations on shared buffers
     * in order to provide a performance equivalent to JSE's <code>nio</code>
     * package. The buffers are reclaimable by the garbage collector.
     * <p>
     * Like all of the <code>cat</code> methods in this class, this method
     * is guaranteed <em>never</em> to close any parameter stream.
     *
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the input stream.
     * @throws IOException If copying the data fails because of an
     *         IOException in the output stream.
     */
    public static void cat(final InputStream in, final OutputStream out)
    throws IOException {
        if (in == null || out == null)
            throw new NullPointerException();

        // Note that we do not use PipedInput/OutputStream because these
        // classes are slooowww. This is partially because they are using
        // Object.wait()/notify() in a suboptimal way and partially because
        // they copy data to and from an additional buffer byte array, which
        // is redundant if the data to be transferred is already held in
        // another byte array.
        // As an implication of the latter reason, although the idea of
        // adopting the pipe concept to threads looks tempting it is actually
        // bad design: Pipes are a good means of interprocess communication,
        // where processes cannot access each others data directly without
        // using an external data structure like the pipe as a commonly shared
        // FIFO buffer.
        // However, threads are different: They share the same memory and thus
        // we can use much more elaborated algorithms for data transfer.

        // Finally, in this case we will simply cycle through an array of
        // byte buffers, where an additionally created reader executor will fill
        // the buffers with data from the input and the current executor will
        // flush the filled buffers to the output.

        final Buffer[] buffers = allocateBuffers();

        /*
         * The task that cycles through the buffers in order to fill them
         * with input.
         */
        class Reader implements Runnable {

            /** The index of the next buffer to be written. */
            int off;

            /** The number of buffers filled with data to be written. */
            int len;
            
            /** The IOException that happened in this task, if any. */
            volatile InputIOException exception;
            
            public void run() {
                // Cache some data for better performance.
                final InputStream _in = in;
                final Buffer[] _buffers = buffers;
                final int _buffersLen = buffers.length;

                // The writer executor interrupts this executor to signal
                // that it cannot handle more input because there has been
                // an IOException during writing.
                // We stop processing in this case.
                int read;
                do {
                    // Wait until a buffer is available.
                    final Buffer buffer;
                    synchronized (this) {
                        while (len >= _buffersLen) {
                            try {
                                wait();
                            } catch (InterruptedException interrupted) {
                                return;
                            }
                        }
                        buffer = _buffers[(off + len) % _buffersLen];
                    }

                    // Fill buffer until end of file or buffer.
                    // This should normally complete in one loop cycle, but
                    // we do not depend on this as it would be a violation
                    // of InputStream's contract.
                    final byte[] buf = buffer.buf;
                    try {
                        read = _in.read(buf, 0, buf.length);
                    } catch (IOException failure) {
                        read = -1;
                        exception = new InputIOException(failure);
                    }
                    if (Thread.interrupted())
                        read = -1; // throws away buf - OK in this context
                    buffer.read = read;

                    // Advance head and notify writer.
                    synchronized (this) {
                        len++;
                        notify(); // only the writer could be waiting now!
                    }
                } while (read != -1);
            }
        } // class Reader

        try {
            final Reader reader = new Reader();
            final Controller controller = readerExecutor.submit(reader);

            // Cache some data for better performance.
            final int buffersLen = buffers.length;

            int write;
            while (true) {
                // Wait until a buffer is available.
                final int off;
                final Buffer buffer;
                synchronized (reader) {
                    while (reader.len <= 0) {
                        try {
                            reader.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    off = reader.off;
                    buffer = buffers[off];
                }

                // Stop on last buffer.
                write = buffer.read;
                if (write == -1)
                    break; // reader has terminated because of EOF or exception

                // Process buffer.
                final byte[] buf = buffer.buf;
                try {
                    out.write(buf, 0, write);
                } catch (IOException failure) {
                    // Cancel reader thread synchronously.
                    // Cancellation of the reader thread is required
                    // so that a re-entry to the cat(...) method by the same
                    // thread cannot not reuse the same shared buffers that
                    // an unfinished reader thread of a previous call is
                    // still using.
                    controller.cancel();
                    throw failure;
                }

                // Advance tail and notify reader.
                synchronized (reader) {
                    reader.off = (off + 1) % buffersLen;
                    reader.len--;
                    reader.notify(); // only the reader could be waiting now!
                }
            }

            if (reader.exception != null)
                throw reader.exception;
        } finally {
            releaseBuffers(buffers);
        }
    }

    private static Executor getExecutor(String threadName) {
        try {
            Class.forName("java.util.concurrent.Executors");
            // Take advantage of Java 5 cached thread pools.
            return new ReflectiveJRE15Executor(threadName);
        } catch (ClassNotFoundException cnfe) {
            return new JRE14Executor(threadName);
        }
    }

    private interface Executor {
        Controller submit(Runnable target);
    }

    private interface Controller {
        void cancel();
    }

    /*private static final class JRE15Executor implements Executor {
        private final java.util.concurrent.ExecutorService service;
        
        public JRE15Executor(final String threadName) {
            assert threadName != null;
            service = java.util.concurrent.Executors.newCachedThreadPool(
                    new java.util.concurrent.ThreadFactory() {
                public Thread newThread(final Runnable r) {
                    final Thread t = new Thread(r, threadName);
                    t.setDaemon(true);
                    return t;
                }
            });
        }

        public Controller submit(Runnable target) {
            assert target != null;
            return new JRE15Controller(service.submit(target));
        }
    }

    private static class JRE15Controller implements Controller {
        private final java.util.concurrent.Future future;

        public JRE15Controller(java.util.concurrent.Future future) {
            assert future != null;
            this.future = future;
        }

        public void cancel() {
            future.cancel(true);
            while (true) {
                try {
                    future.get();
                    break;
                } catch (java.util.concurrent.CancellationException cancelled) {
                    break;
                } catch (java.util.concurrent.ExecutionException readerFailure) {
                    assert false : readerFailure;
                    break;
                } catch (InterruptedException ignored) {
                }
            }
        }
    }*/

    private static final class ReflectiveJRE15Executor implements Executor {
        private final Object service;
        private final Method submit;
        
        public ReflectiveJRE15Executor(final String threadName) {
            assert threadName != null;

            try {
                final ClassLoader cl = getClass().getClassLoader();

                // Locate newCachedThreadPool method.
                final Class tfc
                        = Class.forName("java.util.concurrent.ThreadFactory");
                final Method newCachedThreadPool
                        = Class.forName("java.util.concurrent.Executors")
                        .getMethod(
                            "newCachedThreadPool",
                            new Class[] { tfc });

                // Locate submit method.
                submit = Class.forName("java.util.concurrent.ExecutorService")
                        .getMethod("submit", new Class[] { Runnable.class });

                // Create thread factory.
                final InvocationHandler ih
                        = new ThreadFactoryInvocationHandler(threadName);
                final Object tf = Proxy.newProxyInstance(
                        cl, new Class[] { tfc }, ih);

                // Create cached thread pool.
                service = newCachedThreadPool.invoke(null, new Object[] { tf });
            } catch (Exception ex) {
                // Since this class would never actually be used on JRE 1.4*,
                // we can safely throw an assertion error.
                throw new AssertionError(ex);
            }
        }

        public Controller submit(Runnable target) {
            assert target != null;

            try {
                final Object future = submit.invoke(service, new Object[] {target});
                return new ReflectiveJRE15Controller(future);
            } catch (InvocationTargetException ite) {
                throw (RuntimeException) ite.getCause();
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }

        private static class ThreadFactoryInvocationHandler implements InvocationHandler {
            private final String threadName;

            public ThreadFactoryInvocationHandler(final String threadName) {
                this.threadName = threadName;
            }

            public Object invoke(
                    final Object proxy,
                    final Method method,
                    final Object[] args) {
                assert "newThread".equals(method.getName());
                assert args.length == 1;
                final Thread t = new Thread((Runnable) args[0], threadName);
                t.setDaemon(true);
                return t;
            }
        }
    }

    private static class ReflectiveJRE15Controller implements Controller {
        private static final Method cancel;
        private static final Method get;
        private static final Class[] BOOLEAN = new Class[] { boolean.class };
        private static final Class[] VOID = new Class[] { };
        static {
            try {
                // Locate cancel and get methods.
                final Class fc = Class.forName("java.util.concurrent.Future");
                cancel = fc.getMethod("cancel", BOOLEAN);
                get = fc.getMethod("get", VOID);
            } catch (Exception ex) {
                // This could only happen if we are running on JRE 1.4*
                // AND an eager class loader is used.
                // Since this class would never actually be used on JRE 1.4*,
                // we can ignore this unless assertions are also enabled.
                throw new AssertionError(ex);
            }
        }

        private final Object future;
        private static final Object[] NONE = new Object[]{};

        public ReflectiveJRE15Controller(final Object future) {
            assert future != null;
            this.future = future;
        }

        public void cancel() {
            try {
                try {
                    cancel.invoke(future, new Object[] { Boolean.TRUE });
                } catch (InvocationTargetException ite) {
                    throw new AssertionError(ite);
                }
                while (true) {
                    try {
                        get.invoke(future, NONE);
                        break;
                    } catch (InvocationTargetException ite) {
                        // java.lang.InterruptedException
                        // java.util.concurrent.CancellationException
                        // NEVER: java.util.concurrent.ExecutionException
                        try {
                            final Class c = ite.getCause().getClass();
                            if (Class.forName(
                                    "java.util.concurrent.CancellationException")
                                    .isAssignableFrom(c))
                                break; // reader thread cancelled - horray!
                            assert Class.forName(
                                    "java.lang.InterruptedException")
                                    .isAssignableFrom(c);
                            continue;
                        } catch (ClassNotFoundException cnfe) {
                            throw new AssertionError(cnfe);
                        }
                    }
                }
            } catch (IllegalAccessException iae) {
                iae.printStackTrace();
            }
        }
    }

    private static final class JRE14Executor implements Executor {
        private final String threadName;
        
        public JRE14Executor(String threadName) {
            assert threadName != null;
            this.threadName = threadName;
        }

        public Controller submit(Runnable target) {
            assert target != null;
            return new JRE14Controller(target, threadName);
        }
    }

    private static class JRE14Controller implements Controller {
        private final Thread thread;

        public JRE14Controller(Runnable target, String threadName) {
            assert target != null;
            assert threadName != null;
            thread = new Thread(target, threadName);
            thread.start();
        }

        public void cancel() {
            thread.interrupt();
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static final Buffer[] allocateBuffers() {
        synchronized (Buffer.list) {
            Buffer[] buffers;
            for (Iterator i = Buffer.list.iterator(); i.hasNext(); ) {
                buffers = (Buffer[]) ((Reference) i.next()).get();
                i.remove();
                if (buffers != null)
                    return buffers;
            }
        }

        // A minimum of two buffers is required.
        // The actual number is optimized to compensate for oscillating
        // I/O bandwidths like e.g. with network shares.
        final Buffer[] buffers = new Buffer[4];
        for (int i = buffers.length; --i >= 0; )
            buffers[i] = new Buffer();
        return buffers;
    }

    private static final void releaseBuffers(Buffer[] buffers) {
        synchronized (Buffer.list) {
            Buffer.list.add(new SoftReference(buffers));
        }
    }

    private static final class Buffer {
        /**
         * Each entry in this list holds a soft reference to an array
         * initialized with instances of this class.
         */
        static final List list = new LinkedList();

        /** The byte buffer used for asynchronous reading and writing. */
        byte[] buf = new byte[64 * 1024]; // TODO: Reuse FLATER_BUF_LENGTH of de.schlichtherle.util.zip.ZipConstants
        
        /** The actual number of bytes read into the buffer. */
        int read;
    }
}
