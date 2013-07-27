/*
 * ArchiveFileSystem.java
 *
 * Created on 3. November 2004, 21:57
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

import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.InputArchive;

import java.io.CharConversionException;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.Icon;

/**
 * This class implements a virtual file system of archvie entries in order
 * to provide the elementary means to operate with files and directories
 * while ensuring the file system's integrity.
 * <p>
 * Note that the archive entries in this file system are shared with the
 * {@link InputArchive} object provided to this class' constructor.
 * <p>
 * Note that in general all of its methods are reentrant on exceptions.
 * This is important because the {@link File} class may repeatedly call
 * them in a regular application.
 * <p>
 * <b>WARNING:</b>This class is <em>not</em> thread safe!
 * All calls to non-static methods <em>must</em> be synchronized on the
 * respective <tt>ArchiveController</tt> object!
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0 (refactored from the former <code>ZipFileSystem</code>)
 */
final class ArchiveFileSystem implements FileConstants {

    /**
     * The entry name of the root directory.
     * This is for use by the {@link ArchiveController} class only!
     */
    static final String ROOT = ENTRY_SEPARATOR;

    /** A comparator for entry names in reverse order. */
    private static final Comparator REVERSE_ENTRIES_COMPARATOR
            = new Comparator() {
        public int compare(Object s1, Object s2) {
            // Thanks to Robert Courchaine for the following patch, which
            // makes it work for non-Sun J2SE APIs.
            return ((String) s2).compareTo((String) s1); // reverse order
            //return ((String) s2).compareTo(s1); // reverse order
        }
    };

    /**
     * A path split utility which knows the format of entry names in archive
     * files.
     *
     * @param entryName The name of the entry which's parent and base name
     *        are to be returned in <tt>result</tt>.
     * @param result An array of at least two strings:
     *        <ul>
     *        <li>Index 0 holds the parent name or <tt>null</tt> if the
     *            entry does not name a parent. If a parent exists, its name
     *            will always end with a slash.</li>
     *        <li>Index 1 holds the base name.</li>
     *        </ul>
     *
     * @return <tt>result</tt>.
     */
    // Note: The only reason why this is not private is to enable unit tests.
    static String[] split(final String entryName, final String[] result) {
        assert entryName != null;
        assert result != null;
        assert result.length >= 2;

        // Calculate index of last character, ignoring trailing slash.
        int end = entryName.length();
        if (--end >= 0)
            if (entryName.charAt(end) == ENTRY_SEPARATOR_CHAR)
                end--;

        // Now look for the slash.
        int i = entryName.lastIndexOf(ENTRY_SEPARATOR_CHAR, end);
        end++;

        // Finally split according to our findings.
        if (i != -1) { // found slash?
            i++;
            result[0] = entryName.substring(0, i); // include separator, may produce only separator!
            result[1] = entryName.substring(i, end); // between separator and trailing separator
        } else { // no slash
            if (end > 0) { // At least one character exists, excluding a trailing separator?
                result[0] = ROOT;
            } else {
                result[0] = null; // no parent
            }
            result[1] = entryName.substring(0, end); // between prefix and trailing separator
        }

        return result;
    }
    
    /**
     * The controller that this filesystem belongs to.
     */
    private final ArchiveController controller;

    /**
     * The read only status of this file system.
     */
    private final boolean readOnly; // Defaults to false!

    /**
     * The map of ArchiveEntries in this file system.
     * If this is a read-only file system, this is actually an unmodifiable
     * map. This field should be considered final!
     * <p>
     * Note that the ArchiveEntries in this map are shared with the 
     * {@link InputArchive} object provided to this class' constructor.
     */
    private Map master;
    
    private final ArchiveEntry root;

    private long modCount;
    
    /**
     * For use by {@link #fixParents(ArchiveEntry)} and
     * {@link #unlink(String)} only!
     */
    private final String[] split = new String[2];
    
    /**
     * Creates a new archive file system and ensures the existence of the root
     * directory with its last modification time set to the system's current
     * time. The file system is modifiable and its marked as touched!
     */
    ArchiveFileSystem(final ArchiveController controller)
    throws IOException {
        this.controller = controller;
        touch();
        master = new CompoundMap(REVERSE_ENTRIES_COMPARATOR, 64);
        root = createEntry(ROOT, null);
        root.setTime(System.currentTimeMillis());
        master.put(ROOT, root);

        readOnly = false;

        assert isTouched();
    }
    
    /**
     * Mounts a newly created archive file system from <tt>controller</tt> and
     * ensures the existence of a root directory with the given
     * <tt>rootTime</tt> as its last modification time.
     * If <tt>readOnly</tt> is true, any subsequent modifying operation
     * will result in a <tt>ArchiveReadOnlyException</tt>.
     * 
     * <p>The file system will be checked and any missing parent directories
     * will be created using the system's current time as their last
     * modification time. Existing directories will never be replaced.
     * In addition, if a commitCreateEntry to a child is missing, the commitCreateEntry will be added.
     * 
     * <p>Note that this method will never load the root directory from the
     * archive file.
     */
    ArchiveFileSystem(
            final ArchiveController controller,
            final InputArchive archive,
            final long rootTime,
            final boolean readOnly) {
        this.controller = controller;

        final int iniCap = (int) (archive.getNumArchiveEntries() / 0.75f);
        master = new CompoundMap(REVERSE_ENTRIES_COMPARATOR, iniCap);

        try {
            // Setup root.
            root = createEntry(ROOT, null);
            // Do NOT yet touch the file system!
            root.setTime(rootTime);
            master.put(ROOT, root);

            Enumeration entries = archive.getArchiveEntries();
            while (entries.hasMoreElements()) {
                final ArchiveEntry entry = (ArchiveEntry) entries.nextElement();
                final String name = entry.getName();
                // Map entry if it doesn't address the implicit root directory.
                if (!ROOT.equals(name) && !("." + ENTRY_SEPARATOR).equals(name)) {
                    entry.setMetaData(new ArchiveEntryMetaData(entry));
                    master.put(name, entry);
                }
            }

            // Now perform a file system check to fix missing parent directories.
            // This needs to be done separately!
            entries = archive.getArchiveEntries();
            while (entries.hasMoreElements()) {
                final ArchiveEntry entry = (ArchiveEntry) entries.nextElement();
                if (isLegalEntryName(entry.getName()))
                    fixParents(entry);
            }
        /*} catch (ArchiveReadOnlyException cannotHappen) {
            // read-only status will be processed later in this constructor.
            throw new AssertionError(cannotHappen);*/
        } catch (CharConversionException cannotHappen) {
            // The slash character is part of all character sets!
            throw new AssertionError(cannotHappen);
        }

        // Reset master map to be unmodifiable if this is a readonly file system
        this.readOnly = readOnly;
        if (readOnly)
            master = Collections.unmodifiableMap(master);

        assert !isTouched();
    }

    /**
     * Checks whether the given entry entryName is a legal entry entryName.
     * A legal entry name does not name the root directory (<code>"/"</code>)
     * or the dot directory (<code>"."</code>) or the dot-dot directory
     * (<code>".."</code>) or any of their descendants.
     */
    private static boolean isLegalEntryName(final String entryName) {
        final int l = entryName.length();

        if (l <= 0)
            return false; // never fix empty pathnames

        switch (entryName.charAt(0)) {
        case ENTRY_SEPARATOR_CHAR:
            return false; // never fix root or absolute pathnames

        case '.':
            if (l >= 2) {
                switch (entryName.charAt(1)) {
                case '.':
                    if (l >= 3) {
                        if (entryName.charAt(2) == ENTRY_SEPARATOR_CHAR) {
                            assert entryName.startsWith(".." + ENTRY_SEPARATOR);
                            return false;
                        }
                        // Fall through.
                    } else {
                        assert "..".equals(entryName);
                        return false;
                    }
                    break;

                case ENTRY_SEPARATOR_CHAR:
                    assert entryName.startsWith("." + ENTRY_SEPARATOR);
                    return false;

                default:
                    // Fall through.
                }
            } else {
                assert ".".equals(entryName);
                return false;
            }
            break;

        default:
            // Fall through.
        }
        
        return true;
    }

    /**
     * Called from a constructor to fix the parent directories of
     * <tt>entry</tt>, ensuring that all parent directories of the entry
     * exist and that they contain the respective child.
     * If a parent directory does not exist, it is created using an
     * unkown time as the last modification time - this is defined to be a
     * <i>ghost<i> directory.
     * If a parent directory does exist, the respective child is added
     * (possibly yet again) and the process is continued.
     */
    private void fixParents(final ArchiveEntry entry)
    throws CharConversionException {
        final String name = entry.getName();
        // When recursing into this method, it may be called with the root
        // directory as its parameter, so we may NOT skip the following test.
        if (name.length() <= 0 || name.charAt(0) == ENTRY_SEPARATOR_CHAR)
            return; // never fix root or empty or absolute pathnames
        assert isLegalEntryName(name);

        final String split[] = split(name, this.split);
        final String parentName = split[0];
        final String baseName = split[1];

        ArchiveEntry parent = (ArchiveEntry) master.get(parentName);
        if (parent == null) {
            parent = createEntry(parentName, null);
            //parent.setTime(0); // mark as ghost directory!
            master.put(parentName, parent);
        }

        fixParents(parent);
        parent.getMetaData().children.add(baseName);
    }

    /**
     * Indicates whether this file system is read only or not.
     * The default is <tt>false</tt>.
     */
    boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Ensures that the controller's data structures required to output
     * entries are properly initialized and marks this virtual archive
     * file system as touched.
     *
     * @throws ArchiveReadOnlyExceptionn If this virtual archive file system
     *         is read only.
     * @throws IOException If setting up the required data structures in the
     *         controller fails for some reason.
     */
    private void touch() throws IOException {
        if (isReadOnly())
            throw new ArchiveReadOnlyException();

        // Order is important here because of exceptions!
        if (modCount == 0) {
            controller.fileSystemTouched();
        }
        modCount++;
    }
    
    /**
     * Indicates whether this file system has been modified since
     * its time of creation or the last call to <tt>resetTouched()</tt>.
     */
    boolean isTouched() {
        return modCount != 0;
    }

    /**
     * Resets this file system's touch status so that an instant call to
     * <tt>isTouched()</tt> will return false.
     */
    private void resetTouched() {
        modCount = 0;
    }

    /**
     * Returns an enumeration of all ArchiveEntry objects in this file system
     * in reversed pathname order, i.e. all getArchiveEntries in a directory are
     * enumerated before their containing directory.
     * <p>
     * Example:<pre>
     * a/b/c
     * a/b/
     * a/
     * /
     * </pre>
     */
    Enumeration getReversedEntries() {
        // The comparator already reverses the archive entries!
        return Collections.enumeration(master.values());
    }
    
    /**
     * Returns the root directory of this file system. This will always
     * exist.
     */
    private ArchiveEntry getRoot() {
        return root;
    }

    /**
     * Looks up the specified entry in the file system and returns it or
     * <tt>null</tt> if not existent.
     */
    ArchiveEntry get(String entryName) {
        return (ArchiveEntry) master.get(entryName);
    }

    /**
     * Equivalent to {@link #beginCreateAndLink(String, boolean, ArchiveEntry)
     * beginCreateAndLink(entryName, createParents, null)}.
     */
    Delta beginCreateAndLink(
            final String entryName,
            final boolean createParents)
    throws CharConversionException, ArchiveIllegalOperationException {
        return new CreateAndLinkDelta(entryName, createParents, null);
    }

    /**
     * Begins a "create and link entry" transaction to ensure that either a
     * new entry for the given <tt>entryName</tt> will be created or an
     * existing entry is replaced within this virtual archive file system.
     * <p>
     * This is the first step of a two-step process to create an archive entry
     * and link it into this virtual archive file system.
     * To commit the transaction, call {@link Delta#commit} on the returned object
     * after you have successfully conducted the operations which compose the
     * transaction.
     * <p>
     * Upon a <code>commit</code> operation, the last modification time of
     * the newly created and linked entries will be set to the system's
     * current time at the moment the transaction has begun and the file
     * system will be marked as touched at the moment the transaction has
     * been committed.
     * <p>
     * Note that there is no rollback operation: After this method returns,
     * nothing in the virtual file system has changed yet and all information
     * required to commit the transaction is contained in the returned object.
     * Hence, if the operations which compose the transaction fails, the
     * returned object may be safely collected by the garbage collector,
     * 
     * @param entryName The full path name of the entry to create or replace.
     *        This must be a relative path name.
     * @param createParents If <tt>true</tt>, any non-existing parent
     *        directory will be created in this file system with its last
     *        modification time set to the system's current time.
     * @param blueprint If not <code>null</code>, then the newly created or
     *        replaced entry shall inherit as much attributes from this
     *        object as possible (with the exception of the name).
     *        This is typically used for archive copy operations and requires
     *        some support by the archive driver.
     *        However, the last modification time is always retained.
     * @throws CharConversionException If <code>entryName</code> contains
     *         characters which cannot be represented by the underlying
     *         archive driver.
     * @throws ArchiveIllegalOperationException If one of the following is true:
     *         <ul>
     *         <li>The entry name indicates a directory (trailing <tt>/</tt>)
     *             and its entry does already exist within this file system.
     *         <li>The entry is a file or directory and does already exist as
     *             the respective other type within this file system.
     *         <li>The parent directory does not exist and
     *             <tt>createParents</tt> is <tt>false</tt>.
     *         <li>One of the entry's parents denotes a file.
     *         </ul>
     * @throws ArchiveReadOnlyExceptionn If this virtual archive file system
     *         is read only.
     * @return A transaction object. You must call its
     *         {@link Delta#commit} method in order to commit
     *         link the newly created entry into this virtual archive file
     *         system.
     */
    Delta beginCreateAndLink(
            final String entryName,
            final boolean createParents,
            final ArchiveEntry blueprint)
    throws CharConversionException, ArchiveIllegalOperationException {
        return new CreateAndLinkDelta(entryName, createParents, blueprint);
    }

    /**
     * A simple transaction for creating (and hence probably replacing) and
     * linking an entry in this virtual archive file system.
     *
     * @see #beginCreateAndLink
     */
    private class CreateAndLinkDelta extends AbstractDelta {
        private final long time = System.currentTimeMillis();
        private final Element[] elements;

        private CreateAndLinkDelta(
                final String entryName,
                final boolean createParents,
                final ArchiveEntry blueprint)
        throws ArchiveIllegalOperationException, CharConversionException {
            assert entryName.length() > 0;
            assert entryName.charAt(0) != ENTRY_SEPARATOR_CHAR;

            if (isReadOnly())
                throw new ArchiveReadOnlyException();
            elements = createElements(entryName, createParents, blueprint, 1);
        }

        private Element[] createElements(
                final String entryName,
                final boolean createParents,
                final ArchiveEntry blueprint,
                final int level)
        throws ArchiveIllegalOperationException, CharConversionException {
            // First, retrieve the parent's entryName and base name.
            final String split[]
                    = split(entryName, ArchiveFileSystem.this.split);
            final String parentName = split[0]; // could be separator only to indicate root
            final String baseName = split[1];

            final Element[] elements;

            // Lookup parent entry, creating it where necessary and allowed.
            final ArchiveEntry parent = (ArchiveEntry) master.get(parentName);
            final ArchiveEntry entry;
            if (parent != null) {
                final ArchiveEntry oldEntry
                        = (ArchiveEntry) master.get(entryName);
                ensureMayBeReplaced(entryName, oldEntry);
                elements = new Element[level + 1];
                elements[0] = new Element(File.EMPTY, parent);
                entry = createEntry(entryName, level == 1 ? blueprint : null);
                elements[1] = new Element(baseName, entry);
            } else if (createParents) {
                elements = createElements(
                        parentName, createParents, blueprint, level + 1);
                entry = createEntry(entryName, level == 1 ? blueprint : null);
                elements[elements.length - level]
                        = new Element(baseName, entry);
            } else {
                throw new ArchiveIllegalOperationException(
                        entryName, "Missing parent directory!");
            }
            if (blueprint != null && level == 1) {
                // According to the contract of ArchiveDriver, this should
                // have been done by ArchiveDriver.createEntry(*),
                // but we want to play it safe.
                entry.setTime(blueprint.getTime());
            } else {
                entry.setTime(time);
            }

            return elements;
        }

        private void ensureMayBeReplaced(
                final String entryName,
                final ArchiveEntry oldEntry)
        throws ArchiveIllegalOperationException {
            final int end = entryName.length() - 1;
            if (entryName.charAt(end) == ENTRY_SEPARATOR_CHAR) { // entryName indicates directory
                if (oldEntry != null)
                    throw new ArchiveIllegalOperationException(entryName,
                            "Directories cannot be replaced!");
                if (master.get(entryName.substring(0, end)) != null)
                    throw new ArchiveIllegalOperationException(entryName,
                            "Directories cannot replace files!");
            } else { // entryName indicates file
                if (master.get(entryName + ENTRY_SEPARATOR) != null)
                    throw new ArchiveIllegalOperationException(entryName,
                            "Files cannot replace directories!");
            }
        }

        /** Links the entries into this virtual archive file system. */
        public void commit() throws IOException {
            touch();
            ArchiveEntry parent = elements[0].entry;
            for (int i = 1, l = elements.length; i < l ; i++) {
                final Element element = elements[i];
                final String baseName = element.baseName;
                final ArchiveEntry entry = element.entry;
                if (parent.getMetaData().children.add(baseName)
                        && parent.getTime() >= 0) {
                    parent.setTime(System.currentTimeMillis()); // NOT time!
                }
                master.put(entry.getName(), entry);
                parent = entry;
            }
        }

        public ArchiveEntry getEntry() {
            return elements[elements.length - 1].entry;
        }
    }

    private static abstract class AbstractDelta implements Delta {
        /** A data class for use by subclasses. */
        protected static class Element {
            protected final String baseName;
            protected final ArchiveEntry entry;

            // This constructor is provided for convenience only.
            protected Element(String baseName, ArchiveEntry entry) {
                this.baseName = baseName; // may be null!
                assert entry != null;
                this.entry = entry;
            }
        }
    }

    /**
     * This interface encapsulates the methods required to begin and commit
     * a simplified transaction (a delta) on this virtual archive file system.
     * <p>
     * Note that there is no <code>begin</code> or <code>rollback</code>
     * method in this class.
     * Instead, <code>begin</code> is expected to be implemented by the
     * constructor of the implementation and must not modify the file system,
     * so that an explicit <code>rollback</code> is not required.
     */
    interface Delta {

        /**
         * Returns the entry operated by this delta.
         */
        ArchiveEntry getEntry();

        /**
         * Commits the simplified transaction, possibly modifying this
         * virtual archive file system.
         *
         * @throws IOException If the commit operation fails for any I/O
         *         related reason.
         */
        void commit() throws IOException;
    }

    /**
     * Creates an archive entry which is going to be linked into this virtual
     * archive file system in the near future.
     * The returned entry has properly initialized meta data, but is
     * otherwise left as created by the archive driver.
     * 
     * @param entryName The full path name of the entry to create or replace.
     *        This must be a relative path name.
     * @param blueprint If not <code>null</code>, then the newly created entry
     *        shall inherit as much attributes from this object as possible
     *        (with the exception of the name).
     *        This is typically used for archive copy operations and requires
     *        some support by the archive driver.
     *
     * @return An {@link ArchiveEntry} created by the archive driver and
     *         properly initialized with meta data.
     *
     * @throws CharConversionException If <code>entryName</code> contains
     *         characters which cannot be represented by the underlying
     *         archive driver.
     */
    private ArchiveEntry createEntry(String entryName, ArchiveEntry blueprint)
    throws CharConversionException {
        final ArchiveEntry entry = controller.getDriver().createArchiveEntry(
                controller, entryName, blueprint);
        entry.setMetaData(new ArchiveEntryMetaData(entry));
        return entry;
    }

    /**
     * If this method returns, the entry identified by the given
     * <tt>entryName</tt> has been successfully deleted from the virtual
     * archive file system.
     * If the entry is a directory, it must be empty for successful deletion.
     * 
     * @return Only if the entry has been successfully deleted from the
     *         virtual file system.
     *
     * @throws ArchiveReadOnlyExceptionn If the virtual archive file system is
     *         read only.
     * @throws ArchiveIllegalOperationException If the operation failed for
     *         any other reason.
     */
    private void unlink(final String entryName)
    throws IOException {
        assert entryName.length() > 0;
        assert entryName.charAt(0) != ENTRY_SEPARATOR_CHAR;

        try {
            final ArchiveEntry entry = (ArchiveEntry) master.remove(entryName);
            if (entry == null)
                throw new ArchiveIllegalOperationException(entryName,
                        "Entry does not exist!");
            if (entry == root
                    || entry.isDirectory() && entry.getMetaData().children.size() != 0) {
                master.put(entryName, entry); // Restore file system
                throw new ArchiveIllegalOperationException(entryName,
                        "Directory is not empty!");
            }
            final String split[] = split(entryName, this.split);
            final String parentName = split[0];
            final ArchiveEntry parent = (ArchiveEntry) master.get(parentName);
            assert parent != null : "The parent directory of \"" + entryName
                        + "\" is missing - archive file system is corrupted!";
            final boolean ok = parent.getMetaData().children.remove(split[1]);
            assert ok : "The parent directory of \"" + entryName
                        + "\" does not contain this entry - archive file system is corrupted!";
            touch();
            parent.setTime(System.currentTimeMillis());
        }
        catch (UnsupportedOperationException unmodifiableMap) {
            throw new ArchiveReadOnlyException();
        }
    }

    //
    // Exceptions:
    //

    /**
     * This exception is thrown when a client tries to perform an illegal
     * operation on an archive file system.
     * <p>
     * This exception is private by intention: Clients should not even
     * know about the existence of virtual archive file systems.
     * Most methods in the {@link File} class will catch this exception and
     * return the boolean value <code>false</code> instead in order to
     * overwrite a super class method.
     * Even if not (e.g. with {@link File#createNewFile()}, a client
     * will just see "some subclass of an {@link IOException}".
     */
    private static class ArchiveIllegalOperationException extends IOException {
        /** The entry's path name. */
        private final String entryName;

        private ArchiveIllegalOperationException(String message) {
            super(message);
            this.entryName = null;
        }

        private ArchiveIllegalOperationException(String entryName, String message) {
            super(message);
            this.entryName = entryName;
        }

        public String getMessage() {
            // For performance reasons, this string is constructed on demand
            // only!
            if (entryName != null)
                return entryName + " (" + super.getMessage() + ")";
            else
                return super.getMessage();
        }
    }

    /**
     * This exception is thrown when a client tries to modify a read only
     * virtual archive file system.
     */
    private static class ArchiveReadOnlyException extends ArchiveIllegalOperationException {
        private ArchiveReadOnlyException() {
            super("This archive is read-only!");
        }
    }

    //
    // File system operations used by the ArchiveController class:
    //
    
    boolean exists(final String entryName) {
        return get(entryName) != null || get(entryName + ENTRY_SEPARATOR) != null;
    }
    
    boolean isFile(final String entryName) {
        return get(entryName) != null;
    }
    
    boolean isDirectory(final String entryName) {
        return get(entryName + ENTRY_SEPARATOR) != null;
    }

    Icon getOpenIcon(final String entryName) {
        assert !EMPTY.equals(entryName);

        ArchiveEntry entry = get(entryName);
        if (entry == null)
            entry = get(entryName + ENTRY_SEPARATOR_CHAR);
        return entry != null ? entry.getOpenIcon() : null;
    }

    Icon getClosedIcon(final String entryName) {
        assert !EMPTY.equals(entryName);

        ArchiveEntry entry = get(entryName);
        if (entry == null)
            entry = get(entryName + ENTRY_SEPARATOR_CHAR);
        return entry != null ? entry.getClosedIcon() : null;
    }
    
    boolean canWrite(final String entryName) {
        return !isReadOnly() && exists(entryName);
    }

    boolean setReadOnly(final String entryName) {
        return isReadOnly() && exists(entryName);
    }
    
    long length(final String entryName) {
        final ArchiveEntry entry = get(entryName);
        if (entry != null) {
            // TODO: Review: Can we avoid this special case? It's probably Zip32Driver specific!
            // This entry is a plain file in the file system.
            // If entry.getSize() returns -1, the length is yet unknown.
            // This may happen if e.g. a ZIP entry has only been partially
            // written, i.e. not yet closed by another thread, or if this is
            // a ghost directory.
            // As this is not specified in the contract of the File class,
            // return 0 in this case instead.
            final long length = entry.getSize();
            return length != -1 ? length : 0;
        }
        // This entry is a directory in the file system or does not exist.
        return 0;
    }

    long lastModified(final String entryName) {
        ArchiveEntry entry = get(entryName);
        if (entry == null)
            entry = get(entryName + ENTRY_SEPARATOR);
        if (entry != null) {
            // Depending on the driver type, entry.getTime() could return
            // a negative value. E.g. this is the default value that the
            // ArchiveDriver uses for newly created entries in order to indicate
            // an unknown time.
            // As this is not specified in the contract of the File class,
            // return 0 in this case instead.
            final long time = entry.getTime();
            return time >= 0 ? time : 0;
        }
        // This entry does not exist.
        return 0;
    }

    boolean setLastModified(final String entryName, final long time)
    throws IOException {
        if (time < 0)
            throw new IllegalArgumentException(entryName +
                    ": Negative entry modification time!");

        if (isReadOnly())
            return false;

        ArchiveEntry entry = get(entryName);
        if (entry == null) {
            entry = get(entryName + ENTRY_SEPARATOR);
            if (entry == null) {
                // This entry does not exist.
                return false;
            }
        }

        // Order is important here!
        touch();
        entry.setTime(time);

        return true;
    }
    
    String[] list(final String entryName) {
        // Lookup the entry as a directory.
        final ArchiveEntry entry = get(entryName + ENTRY_SEPARATOR);
        if (entry != null)
            return entry.getMetaData().list();
        else
            return null; // does not exist as a directory
    }
    
    String[] list(
            final String entryName,
            final FilenameFilter filenameFilter,
            final File dir) {
        // Lookup the entry as a directory.
        final ArchiveEntry entry = get(entryName + ENTRY_SEPARATOR);
        if (entry != null)
            if (filenameFilter != null)
                return entry.getMetaData().list(filenameFilter, dir);
            else
                return entry.getMetaData().list(); // most efficient
        else
            return null; // does not exist as directory
    }

    File[] listFiles(
            final String entryName,
            final FilenameFilter filenameFilter,
            final File dir,
            final FileFactory factory) { // deprecated warning is OK!
        // Lookup the entry as a directory.
        final ArchiveEntry entry = get(entryName + ENTRY_SEPARATOR);
        if (entry != null)
            return entry.getMetaData().listFiles(filenameFilter, dir, factory);
        else
            return null; // does not exist as a directory
    }
    
    File[] listFiles(
            final String entryName,
            final FileFilter fileFilter,
            final File dir,
            final FileFactory factory) { // deprecated warning is OK!
        // Lookup the entry as a directory.
        final ArchiveEntry entry = get(entryName + ENTRY_SEPARATOR);
        if (entry != null)
            return entry.getMetaData().listFiles(fileFilter, dir, factory);
        else
            return null; // does not exist as a directory
    }

    void mkdir(final String entryName, final boolean createParents)
    throws IOException {
        beginCreateAndLink(entryName + ENTRY_SEPARATOR, createParents).commit();
    }
    
    void delete(final String entryName)
    throws IOException {
        if (get(entryName) != null) {
            unlink(entryName);
            return;
        }
        final String dirEntryName = entryName + ENTRY_SEPARATOR;
        if (get(dirEntryName) != null) {
            unlink(dirEntryName);
            return;
        }
        throw new IOException(entryName + " (archive entry does not exist)");
    }
    
    //
    // Miscellaneous stuff:
    //

    /**
     * A map which combines the fast sorted enumerations of the values in a
     * sorted map with the fast key/value adding/removal/lookup in a hash map
     * at the cost of memory and a slight overhead for a daemon thread.
     * Beware! This is a hack: Only the map operations which are actually
     * used by this class are implemented.
     */
    private static class CompoundMap extends HashMap {
        private static final LinkedList actions = new LinkedList();
        private static final Updater mapper = new Updater();
        static {
            mapper.start();
        }
        private static boolean done;

        private final TreeMap tree;
        
        private CompoundMap(Comparator comparator, int initialCapacity) {
            super(initialCapacity);
            tree = new TreeMap(comparator);
            // Prevent starving of mapper thread.
            int priority = Thread.currentThread().getPriority();
            if (mapper.getPriority() < priority)
                mapper.setPriority(priority);
        }

        public Object remove(Object key) {
            synchronized (actions) {
                actions.addLast(new Action(key));
                done = false;
                actions.notifyAll();
            }
            return super.remove(key);
        }

        public Object put(Object key, Object value) {
            synchronized (actions) {
                actions.addLast(new Action(key, value));
                done = false;
                actions.notifyAll();
            }
            return super.put(key, value);
        }

        public java.util.Collection values() {
            synchronized (actions) {
                while (!done) {
                    try {
                        actions.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                return tree.values();
            }
        }

        private class Action implements Runnable {
            private final boolean put;
            private final Object key;
            private Object value;

            private Action(Object key, Object value) {
                put = true;
                this.key = key;
                this.value = value;
            }

            private Action(Object key) {
                put = false;
                this.key = key;
            }

            public void run() {
                if (put)
                    tree.put(key, value);
                else
                    tree.remove(key);
            }
        }

        private static class Updater extends Thread {
            private Updater() {
                super("TrueZIP CompoundMap Updater");
                setDaemon(true);
            }
            
            public void run() {
                while (true) {
                    final Action action;
                    synchronized (actions) {
                        done = actions.isEmpty();
                        if (done) {
                            // I'm done and going to wait now, so don't wait
                            // for me meanwhile!
                            actions.notifyAll();
                            try {
                                actions.wait();
                            } catch (InterruptedException ignored) {
                            }
                            continue; // play it again, Sam...
                        }
                        action = (Action) actions.removeFirst();
                    }
                    action.run();
                }
            }
        }
    } // class CompoundMap
}
