/*
 * ArchiveController.java
 *
 * Created on 23. Oktober 2004, 20:41
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

import de.schlichtherle.io.archive.Archive;
import de.schlichtherle.io.archive.spi.ArchiveDriver;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.rof.SimpleReadOnlyFile;

import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;

/**
 * This is the base class for any archive controller.
 * Each instance of this class manages a globally unique archive file
 * (the <i>target file</i>) in order to allow random access to it as if it
 * were a regular directory in the native file system.
 * <p>
 * In terms of software patterns, an <code>ArchiveController</code> is
 * similar to a Builder, with the <code>ArchiveDriver</code> interface as
 * its Abstract Factory.
 * However, an archive controller does not necessarily build a new archive.
 * It may also simply be used to access an existing archive for read-only
 * operations, such as listing its top level directory, or reading entry data.
 * Whatever type of operation it's used for, an archive controller provides
 * and controls <em>all</em> access to any particular archive file by the
 * client application and deals with the rather complex details of its
 * states and transitions.
 * <p>
 * Each instance of this class maintains a virtual file system, provides input
 * and output streams for the entries of the archive file and methods
 * to update the contents of the virtual file system with the archive file
 * in the native file system.
 * In cooperation with the {@link File} class, it also knows how to deal with
 * nested archive files (such as <code>"outer.zip/inner.tar.gz"</code>
 * and <i>false positives</i>, i.e. plain files or directories or file or
 * directory entries in an enclosing archive file which have been incorrectly
 * recognized to be <i>prospective archive files</i> by the
 * {@link ArchiveDetector} interface.
 * <p>
 * To ensure that for each archive file there is at most one
 * <code>ArchiveController</code>, the path name of the archive file (called
 * <i>target</i>) is canonicalized, so it doesn't matter whether the
 * {@link File} class addresses an archive file as <code>"archive.zip"</code>
 * or <code>"/dir/archive.zip"</code> if <code>"/dir"</code> is the client
 * application's current directory.
 * <p>
 * Note that in general all of its methods are reentrant on exceptions.
 * This is important because the {@link File} class may repeatedly call them,
 * triggered by the client application. Of course, depending on the context,
 * some or all of the archive file's data may be lost in this case.
 * For more information, please refer to {@link File#update()} and
 * {@link File#umount()}.
 * <p>
 * This class is actually the abstract base class for any archive controller.
 * It encapsulates all the code which is not depending on a particular entry
 * synchronization strategy and the corresponding state of the controller.
 * Though currently unused, this is intended to be helpful for future
 * extensions of TrueZIP, where different synchronization strategies may be
 * implemented.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
abstract class ArchiveController implements Archive, FileConstants {

    //
    // Static fields.
    //

    private static final String CLASS_NAME
            = "de/schlichtherle/io/ArchiveController".replace('/', '.'); // beware of code obfuscation!
    private static final Logger logger = Logger.getLogger(CLASS_NAME, CLASS_NAME);

    /**
     * The map of all archive controllers.
     * The keys are plain {@link java.io.File} instances and the values
     * are either <code>ArchiveController</code>s or {@link WeakReference}s
     * to <code>ArchiveController</code>s.
     * All access to this map must be externally synchronized!
     */
    private static final Map controllers = new WeakHashMap();

    private static final LiveStatistics liveStats = new LiveStatistics();

    /**
     * A lock used when copying data from one archive to another.
     * This lock must be acquired before any other locks on the controllers
     * are acquired in order to prevent dead locks.
     */
    private static final CopyLock copyLock = new CopyLock();

    private static final Comparator REVERSE_CONTROLLERS = new Comparator() {
        public int compare(Object o1, Object o2) {
            return  ((ArchiveController) o2).getTarget().compareTo(
                    ((ArchiveController) o1).getTarget());
        }
    };

    //
    // Instance fields.
    //

    /**
     * A weak reference to this archive controller.
     * This field is for exclusive use by {@link #schedule(boolean)}.
     */
    private final WeakReference weakThis = new WeakReference(this);

    /**
     * the canonicalized or at least normalized absolute path name
     * representation of the target file.
     */
    private final java.io.File target;

    /**
     * The archive controller of the enclosing archive, if any.
     */
    private final ArchiveController enclController;
    
    /**
     * The name of the entry for this archive in the enclosing archive, if any.
     */
    private final String enclEntryName;

    /**
     * The {@link ArchiveDriver} to use for this controller's target file.
     */
    private /*volatile*/ ArchiveDriver driver;

    private final ReentrantLock  readLock;
    private final ReentrantLock writeLock;

    //
    // Static initializers.
    //

    static {
        Runtime.getRuntime().addShutdownHook(ShutdownHook.singleton);
    }

    //
    // Constructors.
    //

    /**
     * This constructor schedules this controller to be thrown away if no
     * more <code>File</code> objects are referring to it.
     * The subclass must update this schedule according to the controller's
     * state.
     * For example, if the controller has started to update some entry data,
     * it must call {@link #schedule} in order to force the
     * controller to be updated on the next call to {@link #update} even if
     * no more <code>File</code> objects are referring to it.
     * Otherwise, all changes may get lost!
     * 
     * @see #schedule(boolean)
     */
    protected ArchiveController(
            final java.io.File target,
            final ArchiveController enclController,
            final String enclEntryName,
            final ArchiveDriver driver) {
        assert target != null;
        assert target.isAbsolute();
        assert (enclController != null) == (enclEntryName != null);
        assert driver != null;
        
        this.target = target;
        this.enclController = enclController;
        this.enclEntryName = enclEntryName;
        this.driver = driver;

        ReadWriteLock rwl = new ReentrantReadWriteLock();
        this.readLock  = rwl.readLock();
        this.writeLock = rwl.writeLock();

        schedule(false);
    }

    //
    // Methods.
    //

    protected final ReentrantLock readLock() {
        return readLock;
    }

    protected final ReentrantLock writeLock() {
        return writeLock;
    }

    /**
     * Runs the given {@link IORunnable} while this controller has acquired
     * its write lock regardless of the state of this controller's read lock.
     * You must use this method if this controller may have acquired a
     * read lock in order to prevent a dead lock.
     * <p>
     * <b>Warning:</b> This method temporarily releases the read lock before
     * the write lock is acquired and the runnable is run!
     * Hence, the runnable should recheck the state of the controller before
     * it proceeds with any write operations.
     *
     * @param runnable The {@link IORunnable} to run while the write lock
     *        is acquired.
     *        No read lock is acquired while this is running!
     */
    protected final void runWriteLocked(IORunnable runnable)
    throws IOException {
        // A read lock cannot get upgraded to a write lock.
        // Hence the following mess is required.
        // Note that this is not just a limitation of the current
        // implementation in JSE 5: If automatic upgrading were implemented,
        // two threads holding a read lock try to upgrade concurrently,
        // they would dead lock each other!
        final int lockCount = readLock().lockCount();
        for (int c = lockCount; c > 0; c--)
            readLock().unlock();

        // The current thread may get deactivated here!
        writeLock().lock();
        try {
            try {
                runnable.run();
            } finally {
                // Restore lock count - effectively downgrading the lock
                for (int c = lockCount; c > 0; c--)
                    readLock().lock();
            }
        } finally {
            writeLock().unlock();
        }
    }

    /**
     * Factory method returning a <code>ArchiveController</code> object for the
     * archive file <code>target</code> and all its enclosing archive files.
     * <p>
     * <b>Notes:</b>
     * <ul>
     * <li>Neither <code>file</code> nor the enclosing archive file(s)
     *     need to actually exist for this to return a valid <code>ArchiveController</code>.
     *     Just the parent directories of <code>file</code> need to look like either
     *     an ordinary directory or an archive file, e.g. their lowercase
     *     representation needs to have a .zip or .jar ending.</li>
     * <li>It is an error to call this method on a target file which is
     *     not a valid name for an archive file</li>
     * </ul>
     */
    static ArchiveController getInstance(final File file) {
        assert file != null;
        assert file.isArchive();
        
        java.io.File target = file.getDelegate();
        try {
            target = target.getCanonicalFile();
        } catch (IOException failure) {
            target = File.normalize(target.getAbsoluteFile());
        }

        final ArchiveDriver driver = file.getArchiveDetector()
                .getArchiveDriver(target.getPath());

        ArchiveController controller = null;
        boolean reconfigure = false;
        try {
            synchronized (controllers) {
                final Object value = controllers.get(target);
                if (value instanceof Reference) {
                    controller = (ArchiveController) ((Reference) value).get();
                    // Check that the controller hasn't been garbage collected
                    // meanwhile!
                    if (controller != null) {
                        // If required, reconfiguration of the ArchiveController
                        // must be deferred until we have released the lock on
                        // controllers in order to prevent dead locks.
                        reconfigure = controller.getDriver() != driver;
                        return controller;
                    }
                } else if (value != null) {
                    // Do NOT reconfigure this ArchiveController with another
                    // ArchiveDetector: This controller is touched, i.e. it
                    // most probably has mounted the virtual file system and
                    // using another ArchiveDetector could potentially break
                    // the update process.
                    // In effect, for an application this means that the
                    // reconfiguration of a previously used ArchiveController
                    // is only guaranteed to happen if
                    // (1) File.update() or File.umount() has been called and
                    // (2) a new File instance referring to the previously used
                    // archive file as either the file itself or one
                    // of its ancestors is created with a different
                    // ArchiveDetector.
                    return (ArchiveController) value;
                }

                final File enclArchive = file.getEnclArchive();
                final ArchiveController enclController;
                final String enclEntryName;
                if (enclArchive != null) {
                    enclController = enclArchive.getArchiveController();
                    enclEntryName = file.getEnclEntryName();
                } else {
                    enclController = null;
                    enclEntryName = null;
                }

                // TODO: Refactor this to a more flexible design which supports
                // different update strategies, like update or append.
                controller = new UpdatingArchiveController(
                        target, enclController, enclEntryName, driver);
            }
        } finally {
            if (reconfigure) {
                controller.writeLock().lock();
                try {
                    controller.setDriver(driver);
                } finally {
                    controller.writeLock().unlock();
                }
            }
        }
        
        return controller;
    }

    /**
     * (Re)schedules this archive controller for the next call to
     * {@link #updateAll(String, boolean, boolean, boolean, boolean, boolean, boolean)}.
     * 
     * @param force If set to <code>true</code>, this controller and hence its
     *        target archive file is guaranteed to get updated during the next
     *        call to <code>updateAll()</code> even if there are no more
     *        {@link File} objects referring to it meanwhile.
     *        Call this method with this parameter value whenever the virtual
     *        file system has been touched, i.e. modified.
     *        <p>
     *        If set to <code>false</code>, this controller is conditionally
     *        scheduled to get updated.
     *        In this case, the controller gets automatically removed from
     *        the controllers weak hash map and discarded once the last file
     *        object directly or indirectly referring to it has been discarded
     *        unless <code>schedule(true)</code> has been called meanwhile.
     *        Call this method if the archive controller has been newly created
     *        or successfully updated.
     */
    protected void schedule(final boolean force) {
        synchronized (controllers) {
            if (force)
                controllers.put(getTarget(), this);
            else
                controllers.put(getTarget(), weakThis);
        }
    }

    /**
     * Returns the canonical or at least normalized absolute
     * <code>java.io.File</code> object for the archive file to control.
     */
    public final java.io.File getTarget() {
        return target;
    }

    public final String getPath() {
        return target.getPath();
    }

    /**
     * Returns the {@link ArchiveController} of the enclosing archive file,
     * if any.
     */
    public final ArchiveController getEnclController() {
        return enclController;
    }

    /**
     * Returns the entry name of this controller within the enclosing archive
     * file, if any.
     */
    public final String getEnclEntryName() {
        return enclEntryName;
    }

    public final String enclEntryName(final String entryName) {
        return EMPTY != entryName
                ? enclEntryName + ENTRY_SEPARATOR + entryName
                : enclEntryName;
    }

    /**
     * Returns the driver instance which is used for the target archive.
     * All access to this method must be externally synchronized on this
     * controller's read lock!
     *
     * @return A valid reference to an {@link ArchiveDriver} object
     *         - never <code>null</code>.
     */
    protected final ArchiveDriver getDriver() {
        return driver;
    }

    /**
     * Sets the driver instance which is used for the target archive.
     * All access to this method must be externally synchronized on this
     * controller's write lock!
     *
     * @param driver A valid reference to an {@link ArchiveDriver} object
     *        - never <code>null</code>.
     */
    protected final void setDriver(ArchiveDriver driver) {
        // This affects all subsequent creations of the driver's products
        // (In/OutputArchive and ArchiveEntry) and hence ArchiveFileSystem.
        // Normally, these are initialized together in mountFileSystem(...)
        // which is externally synchronized on this controller's write lock,
        // so we don't need to be afraid of this.
        this.driver = driver;
    }

    static final ArchiveStatistics getLiveStatistics() {
        return liveStats;
    }

    /**
     * Returns <code>true</code> if and only if the target file of this
     * controller should be considered to be a true file in the native
     * file system (although it does not actually need to exist).
     */
    protected final boolean usesNativeTargetFile() {
        // May be called from FileOutputStream while unlocked!
        //assert readLock().isLocked() || writeLock().isLocked();

        // True iff not enclosed or the enclosing target is actually a plain
        // directory or the target is a plain file.
        return enclController == null
                || enclController.getTarget().isDirectory();
    }
    
    /**
     * This method ensures that the virtual archive file system is mounted
     * and returns it.
     * <p>
     * This method is reentrant with respect to any exceptions it may throw.
     *
     * <p><b>Warning:</b> This method requires external synchronisation on this
     * controller's read lock!
     *
     * @param autoCreate If the archive file does not exist
     *        and this is <code>true</code>, a new file system with only a root
     *        directory is created with its last modification time set to the
     *        system's current time.
     *
     * @return A valid archive file system - <code>null</code> is never returned.
     *
     * @throws FalsePositiveNativeException
     * @throws FalsePositiveFileEntryException
     * @throws FalsePositiveDirectoryException
     * @throws IOException On any other I/O related issue with <code>inFile</code>
     *         or the <code>inFile</code> of any enclosing archive file's
     *         controller.
     */
    protected abstract ArchiveFileSystem getFileSystem(boolean autoCreate)
    throws IOException;

    /**
     * A factory method returning an input stream which is positioned
     * at the beginning of the given entry in the target archive file.
     *
     * @param entryName An entry in the virtual archive file system.
     *        <code>null</code> is not allowed.
     *
     * @return A valid InputStream object - <code>null</code> is never returned.
     *
     * @throws FileNotFoundException if the archive file does not exist or the
     *         requested entry is a directory or does not exist in the archive
     *         file.
     * @throws IOException if the entry can't be opened for reading from the
     *         archive file (this usually means that the archive file is
     *         corrupted).
     */
    protected abstract InputStream getInputStream(String entryName)
    throws IOException;

    /**
     * <p><b>Warning:</b> This method requires external synchronisation on this
     * controller's read lock!
     */
    protected abstract InputStream getInputStream(
            ArchiveEntry entry,
            ArchiveEntry dstEntry)
    throws IOException;

    /**
     * A factory method returning an <code>OutputStream</code> allowing to
     * (re)write entries in the archive file managed by this object.
     * <p>
     * <b>Notes:</b>
     * <ul>
     * <li>If you have called this method on the same archive controller
     *     object before, you must have finished all file operations on the
     *     returned stream before calling this method again! The current
     *     implementation does not support concurrent use of output streams
     *     which are working on the same archive file.</li>
     * <li>If entry is <code>null</code>, output to the archive file is set up,
     *     but no entry header is actually written and <code>null</code> is
     *     returned. Use this to test if writing to the archive file is possible
     *     and to link the archive file when necessary.</li>
     * <li>This method will <b>not</b> link a file system and thus
     *     should not be called with a <code>null</code> argument from outside
     *     this class!
     * </ul>
     * 
     * @param entryName the entry to be written to the archive file
     *        - may <em>not</em> be <code>null</code>.
     *
     * @return <code>null</code> if <code>entry</code> was <code>null</code>.
     *         A valid output stream object for writing the requested entry
     *         otherwise.
     *
     * @throws IOException If the (possibly temporary) output file
     *         cannot be opened for writing for any reason.
     */
    protected abstract OutputStream getOutputStream(final String entryName)
    throws IOException;

    /**
     * <p><b>Warning:</b> This method requires external synchronisation on this
     * controller's write lock!
     */
    protected abstract OutputStream getOutputStream(
            ArchiveEntry entry,
            ArchiveEntry srcEntry)
    throws IOException;

    // TODO: Document this!
    protected abstract void fileSystemTouched()
    throws IOException;
    
    /**
     * Tests if the archive entry with the given name has new data that has
     * not yet been written to the archive file.
     * As an implication, the entry cannot receive new data from another
     * output stream before the next call to {@link File#update()} or
     * {@link File#umount()}.
     * Note that for directories this method will always return
     * <code>false</code>!
     */
    protected abstract boolean hasNewData(String entryName);

    /**
     * Updates all archive files in the native file system which's canonical
     * path name start with <code>prefix</code> with the contents of their
     * respective virtual file system.
     * 
     * @param prefix The prefix of the canonical path name of the archive files
     *        which shall get updated - <code>null</code> is not allowed!
     *        If the canonical pathname of an archive file does not start with
     *        this string, then it is not updated.
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
     * @param umount If <code>true</code>, all temporary files get deleted, too.
     *        Thereafter, the archive controller will behave as if it has just been
     *        created and any subsequent operations on its entries will remount
     *        the virtual file system from the archive file again.
     *        Use this to allow subsequent changes to the archive files
     *        by other processes or via the <code>java.io.File*</code> classes
     *        <em>before</em> this package is used for read or write access to
     *        these archive files again.
     * @param resetCounters If and only if this parameter is <code>true</code>,
     *        the input and output stream counters will be reset if this hasn't
     *        yet happened automatically.
     * @throws ArchiveBusyWarningExcepion If a archive file has been updated
     *         while the application is using any open streams to access it
     *         concurrently.
     *         These streams have been forced to close and the entries of
     *         output streams may contain only partial data.
     * @throws ArchiveWarningException If only warning conditions occur
     *         throughout the course of this method which imply that the
     *         respective archive file has been updated with
     *         constraints, such as a failure to set the last modification
     *         time of the archive file to the last modification time of its
     *         implicit root directory.
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
     * @throws NullPointerException If <code>prefix</code> is <code>null</code>.
     * @throws IllegalArgumentException If <code>closeInputStreams</code> is
     *         <code>false</code> and <code>closeOutputStreams</code> is
     *         <code>true</code>.
     */
    static void updateAll(
            final String prefix,
            final boolean waitInputStreams,
            final boolean closeInputStreams,
            final boolean waitOutputStreams,
            final boolean closeOutputStreams,
            final boolean umount,
            final boolean resetCounters)
    throws ArchiveException {
        if (prefix == null)
            throw new NullPointerException();
        if (!closeInputStreams && closeOutputStreams)
            throw new IllegalArgumentException();
        
        int controllersTotal = 0;
        int controllersTouched = 0;
        
        logger.log(Level.FINE, "updateAll.entering", // NOI18N
                new Object[] {
            prefix,
            Boolean.valueOf(waitInputStreams),
            Boolean.valueOf(closeInputStreams),
            Boolean.valueOf(waitOutputStreams),
            Boolean.valueOf(closeOutputStreams),
            Boolean.valueOf(umount),
            Boolean.valueOf(resetCounters),
        });

        if (resetCounters) {
            // This if-block prevents the statistics from being
            // cleared in case the application has called
            // File.update() or File.umount() and there is no
            // work to do.
            CountingReadOnlyFile.resetOnReuse();
            CountingOutputStream.resetOnReuse();
        }

        try {
            // We must ensure that archive controllers which have been removed
            // from the weak hash map finally umount, i.e. remove their
            // temporary files.
            // Running the garbage collector would be an alternative and may
            // even remove more archive controllers from the weak hash map, but
            // this could also be a severe performance penalty in large
            // applications.
            // In addition, there is no guarantee that the garbage collector
            // immediately runs the finalize() methods.
            if (umount) {
                System.runFinalization();
                // TODO: Consider this to ensure the finalizer thread has a
                // chance to do its work before we create the controller's
                // enumeration.
                /*Thread.interrupted(); // clear interruption status
                try {
                    Thread.sleep(50); // allow concurrent finalization
                } catch (InterruptedException ignored) {
                }*/
            }
            
            // Used to chain archive exceptions.
            ArchiveException exceptionChain = null;

            // The general algorithm is to sort the targets in descending order
            // of their pathnames (considering the system's default name
            // separator character) and then walk the array in reverse order to
            // call the update() method on each respective archive controller.
            // This ensures that an archive file will always be updated
            // before its enclosing archive file.
            final Enumeration e = new ControllerEnumeration(
                    prefix, REVERSE_CONTROLLERS);
            while (e.hasMoreElements()) {
                final ArchiveController controller
                        = (ArchiveController) e.nextElement();
                controller.writeLock().lock();
                try {
                    if (controller.isTouched())
                        controllersTouched++;
                    try {
                        // Upon return, some new ArchiveWarningException's may
                        // have been generated. We need to remember them for
                        // later throwing.
                        controller.update(exceptionChain,
                                waitInputStreams, closeInputStreams,
                                waitOutputStreams, closeOutputStreams,
                                umount, true);
                    } catch (ArchiveException exception) {
                        // Updating the archive file or wrapping it back into
                        // one of it's enclosing archive files resulted in an
                        // exception for some reason.
                        // We are bullheaded and store the exception chain for
                        // later throwing only and continue updating the rest.
                        exceptionChain = exception;
                    }
                } finally {
                    controller.writeLock().unlock();
                }
                controllersTotal++;
            }
            
            // Reorder exception chain if necessary to support conditional
            // exception catching based on their priority (i.e. class).
            if (exceptionChain != null)
                throw (ArchiveException) exceptionChain.sortPriority();
        } catch (ArchiveWarningException failure) {
            logger.log(Level.FINE, "updateAll.throwing", failure);// NOI18N
            throw failure;
        } catch (ArchiveException failure) {
            logger.log(Level.FINE, "updateAll.throwing", failure);// NOI18N
            throw failure;
        } finally {
            CountingReadOnlyFile.setResetOnReuse();
            CountingOutputStream.setResetOnReuse();
            logger.log(Level.FINE, "updateAll.finally", // NOI18N
                    new Object[] {
                new Integer(controllersTotal),
                new Integer(controllersTouched)
            });
        }
    }
    
    /**
     * Equivalent to
     * {@link #update(ArchiveException, boolean, boolean, boolean, boolean, boolean, boolean)
     * update(null, false, false, false, false, false, false)}.
     * <p>
     * <b>Warning:</b> As a side effect, all data structures returned by this
     * controller get reset (filesystem, entries, streams, etc.)!
     * Hence, this method requires external synchronization on this
     * controller's write lock!
     * 
     * @see #update(ArchiveException, boolean, boolean, boolean, boolean, boolean, boolean)
     * @see #updateAll
     * @see ArchiveException
     */
    protected final void update() throws ArchiveException {
        assert writeLock().isLocked();
        update(null, false, false, false, false, false, false);
    }
    
    /**
     * Updates the contents of the archive file managed by this archive
     * controller to the native file system.
     * <p>
     * <b>Warning:</b> As a side effect, all data structures returned by this
     * controller get reset (filesystem, entries, streams, etc.)!
     * Hence, this method requires external synchronization on this
     * controller's write lock!
     * 
     * @param waitInputStreams See {@link #updateAll}.
     * @param closeInputStreams See {@link #updateAll}.
     * @param waitOutputStreams See {@link #updateAll}.
     * @param closeOutputStreams See {@link #updateAll}.
     * @param umount See {@link #updateAll}.
     * @param reassemble Let's assume this archive file is enclosed
     *        in another archive file.
     *        Then if this parameter is <code>true</code>, the updated archive
     *        file is also written to its enclosing archive file.
     *        Note that this parameter <em>must</em> be set if <code>umount</code>
     *        is set as well. Failing to comply to this requirement may throw
     *        a {@link java.lang.AssertionError} and will incur loss of data!
     * @return If any warning condition occurs throughout the course of this
     *         method, an {@link ArchiveWarningException} is created (but not
     *         thrown), prepended to <code>exceptionChain</code> and finally
     *         returned.
     *         If multiple warning conditions occur,
     *         the prepended exceptions are ordered by appearance so that the
     *         <i>last</i> exception created is the head of the returned
     *         exception chain.
     * @throws ArchiveException If any exception condition occurs throughout
     *         the course of this method, an {@link ArchiveException}
     *         is created, prepended to <code>exceptionChain</code> and finally
     *         thrown.
     * @see #update()
     * @see #updateAll
     * @see ArchiveException
     */
    protected abstract void update(
            ArchiveException exceptionChain,
            final boolean waitInputStreams,
            final boolean closeInputStreams,
            final boolean waitOutputStreams,
            final boolean closeOutputStreams,
            final boolean umount,
            final boolean reassemble)
    throws ArchiveException;

    // TODO: Document this!
    protected abstract int waitAllInputStreamsByOtherThreads(long timeout);

    // TODO: Document this!
    protected abstract int waitAllOutputStreamsByOtherThreads(long timeout);

    // TODO: Document this!
    protected abstract boolean isTouched();

    /**
     * Resets the archive controller to its initial state - all changes to the
     * archive file which have not yet been updated get lost!
     * <p>
     * Thereafter, the archive controller will behave as if it has just been
     * created and any subsequent operations on its entries will remount
     * the virtual file system from the archive file again.
     */
    protected abstract void reset() throws IOException;

    public String toString() {
        return getClass().getName() + "@" + System.identityHashCode(this) + "(" + getPath() + ")";
    }

    //
    // File system operations used by the File class.
    // Read only operations.
    //
    
    final boolean exists(final String entryName) throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.exists(entryName);
        } finally {
            readLock().unlock();
        }
    }

    final boolean isFile(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.isFile(entryName);
        } finally {
            readLock().unlock();
        }
    }
    
    final boolean isDirectory(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.isDirectory(entryName);
        } finally {
            readLock().unlock();
        }
    }
    
    final Icon getOpenIcon(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false); // detect false positives!
            if (EMPTY != entryName) { // possibly assigned by init(...)
                return fileSystem.getOpenIcon(entryName);
            } else {
                return getDriver().getOpenIcon(this);
            }
        } finally {
            readLock().unlock();
        }
    }

    final Icon getClosedIcon(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false); // detect false positives!
            if (EMPTY != entryName) { // possibly assigned by init(...)
                return fileSystem.getClosedIcon(entryName);
            } else {
                return getDriver().getClosedIcon(this);
            }
        } finally {
            readLock().unlock();
        }
    }
    
    final boolean canRead(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.exists(entryName);
        } finally {
            readLock().unlock();
        }
    }

    final boolean canWrite(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.canWrite(entryName);
        } finally {
            readLock().unlock();
        }
    }
    
    final long length(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.length(entryName);
        } finally {
            readLock().unlock();
        }
    }
    
    final long lastModified(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.lastModified(entryName);
        } finally {
            readLock().unlock();
        }
    }
    
    final String[] list(final String entryName)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.list(entryName);
        } finally {
            readLock().unlock();
        }
    }

    final String[] list(
            final String entryName,
            final FilenameFilter filenameFilter,
            final File dir)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.list(entryName, filenameFilter, dir);
        } finally {
            readLock().unlock();
        }
    }

    final File[] listFiles(
            final String entryName,
            final FilenameFilter filenameFilter,
            final File dir,
            final FileFactory factory)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.listFiles(entryName, filenameFilter, dir, factory);
        } finally {
            readLock().unlock();
        }
    }

    final File[] listFiles(
            final String entryName,
            final FileFilter fileFilter,
            final File dir,
            final FileFactory factory)
    throws IOException {
        readLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.listFiles(entryName, fileFilter, dir, factory);
        } finally {
            readLock().unlock();
        }
    }

    //
    // File system operations used by the File class.
    // Write operations.
    //

    final boolean setReadOnly(final String entryName)
    throws IOException {
        writeLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(false);
            return fileSystem.setReadOnly(entryName);
        } finally {
            writeLock().unlock();
        }
    }
    
    final boolean setLastModified(final String entryName,final long time)
    throws IOException {
        writeLock().lock();
        try {
            ArchiveFileSystem fileSystem = getFileSystem(false);
            if (fileSystem.isReadOnly())
                return false;
            if (hasNewData(entryName)) {
                update();
                fileSystem = getFileSystem(false); // fileSystem has been reset by update!
            }
            return fileSystem.setLastModified(entryName, time);
        } finally {
            writeLock().unlock();
        }
    }
    
    final boolean createNewFile(final String entryName, final boolean autoCreate)
    throws IOException {
        writeLock().lock();
        try {
            final ArchiveFileSystem fileSystem = getFileSystem(autoCreate);
            if (fileSystem.isFile(entryName))
                return false;

            // If we got until here without an exception,
            // write an empty file now.
            getOutputStream(entryName).close();

            return true;
        } finally {
            writeLock().unlock();
        }
    }
    
    final void mkdir(final String entryName, final boolean autoCreate)
    throws IOException {
        writeLock().lock();
        try {
            if (EMPTY != entryName) { // possibly assigned by init(...)
                // This file is a regular archive entry.
                final ArchiveFileSystem fileSystem = getFileSystem(autoCreate);
                fileSystem.mkdir(entryName, autoCreate);
            } else { // EMPTY == entryName
                // This is the root of an archive file system, so we are
                // actually working on the controller's target file.
                try {
                    // Try mounting file system.
                    getFileSystem(false);
                    // File system mounted and root already exists.
                    throw new IOException("archive file already exists!");
                } catch (FileNotFoundException fnfe) {
                    // Ensure file system existence.
                    getFileSystem(true);
                }
            }
        } finally {
            writeLock().unlock();
        }
    }
    
    final void delete(final String entryName)
    throws IOException {
        writeLock().lock();
        try {
            // update() invalidates the file system, so it has to be
            // done before getFileSystem().
            // TODO: Consider adding configuration switch to allow
            // rewriting an archive entry to the same output archive
            // multiple times, whereby only the last written entry is
            // added to the central directory of the archive
            // (if the archive type supports this concept).
            if (hasNewData(entryName))
                update();

            final ArchiveFileSystem fileSystem = getFileSystem(false);
            if (EMPTY != entryName) { // possibly assigned by init()
                fileSystem.delete(entryName);
            } else { // EMPTY == entryName
                // We are actually working on the controller's target file.
                // Do not use the number of entries in the file system
                // for the following test - it's size would count absolute
                // pathnames as well!
                final String[] members = fileSystem.list(entryName);
                if (members != null && members.length != 0)
                    throw new IOException("archive file system not empty!");
                final int outputStreams = waitAllOutputStreamsByOtherThreads(50);
                // TODO: Review: This policy may be changed - see method start.
                assert outputStreams <= 0
                        : "Entries for open output streams should not be deletable!";
                // Note: Entries for open input streams ARE deletable!
                final int inputStreams = waitAllInputStreamsByOtherThreads(50);
                if (inputStreams > 0 || outputStreams > 0)
                    throw new IOException("archive file has open streams!");
                reset();
                // Just in case our target is an RAES encrypted ZIP file,
                // forget it's password as well.
                // TODO: Review: This is an archive driver dependency!
                // Calling it doesn't harm, but please consider a more transparent
                // way to model this.
//              PromptingKeyManager.resetKeyProvider(getPath());
                // Delete the native file or the entry in the enclosing
                // archive file, too.
                if (usesNativeTargetFile()) {
                    // The target file of the controller is NOT enclosed
                    // in another archive file.
                    if (!getTarget().delete())
                        throw new IOException("couldn't delete archive file!");
                } else {
                    // The target file of the controller IS enclosed in
                    // another archive file.
                    enclController.delete(enclEntryName(entryName));
                }
            }
        } finally {
            writeLock().unlock();
        }
    }

    //
    // Static copy methods:
    //

    /**
     * Copies a source file to a destination file, optionally preserving the
     * source's last modification time.
     * We know nothing about the source or destination file yet.
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
    protected static void cp(
            final java.io.File src,
            final java.io.File dst,
            final boolean preserve)
            throws IOException {
        assert src != null;
        assert dst != null;

        try {
            if (src instanceof File) {
                final File srcFile = (File) src;
                if (srcFile.isArchive() && (srcFile.isDirectory()
                        || (srcFile.exists() && !srcFile.isFile()))) {
                    throw new FileNotFoundException(src.getPath() +
                            ": Cannot read (possibly inaccessible) archive file!");
                }
                final String srcEntryName = srcFile.getEnclEntryName();
                if (srcEntryName != null) {
                    cp( srcFile,
                        srcFile.getEnclArchive().getArchiveController(),
                        srcEntryName, dst,
                        preserve);
                    return;
                }
            }

            // Treat the source like a regular file.
            final InputStream in;
            try {
                in = new java.io.FileInputStream(src);
            } catch (FileNotFoundException failure) {
                throw new InputIOException(failure);
            }
            try {
                cp(src, in, dst, preserve);
            } finally {
                try {
                    in.close();
                } catch (IOException failure) {
                    throw new InputIOException(failure);
                }
            }
        } catch (ArchiveBusyException failure) {
            throw new FileBusyException(failure);
        }
    }
    
    /**
     * Copies a source file to a destination file, optionally preserving the
     * source's last modification time.
     * We already have an input stream to read the source file,
     * but we know nothing about the destination file yet.
     * Note that this method <em>never</em> closes the given input stream!
     *
     * @throws FileNotFoundException If either the source or the destination
     *         cannot get accessed.
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     */
    protected static void cp(
            final java.io.File src,
            final InputStream in,
            final java.io.File dst,
            final boolean preserve)
            throws IOException {
        if (dst instanceof File) {
            final File dstFile = (File) dst;
            if (dstFile.isArchive() && (dstFile.isDirectory()
                    || (dstFile.exists() && !dstFile.isFile()))) {
                throw new FileNotFoundException(dst.getPath() +
                        ": Cannot overwrite (possibly inaccessible) archive file!");
            }
            final String dstEntryName = dstFile.getEnclEntryName();
            if (dstEntryName != null) {
                cp( src, in, dstFile,
                    dstFile.getEnclArchive().getArchiveController(),
                    dstEntryName, preserve);
                return;
            }
        }

        // Treat the destination like a regular file.
        final OutputStream out = new java.io.FileOutputStream(dst);
        try {
            File.cat(in, out);
        } finally {
            out.close();
        }
        if (preserve && !dst.setLastModified(src.lastModified())) {
            throw new FileNotFoundException(dst.getPath() +
                    ": Couldn't preserve last modification time!");
        }
    }

    /**
     * Copies a source file to a destination file, optionally preserving the
     * source's last modification time.
     * We know that the source file appears to be an entry in an archive
     * file, but we know nothing about the destination file yet.
     * <p>
     * Note that this method synchronizes on the class object in order
     * to prevent dead locks by two threads copying archive entries to the
     * other's source archive concurrently!
     *
     * @throws FalsePositiveException If the source or the destination is a
     *         false positive and the exception
     *         cannot get resolved within this method.
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     */
    protected static void cp(
            final File srcFile,
            final ArchiveController srcController,
            final String srcEntryName,
            final java.io.File dst,
            final boolean preserve)
    throws IOException {
        // Do not assume anything about the lock status of the controller:
        // This method may be called from a subclass while a lock is acquired!
        //assert !srcController.readLock().isLocked();
        //assert !srcController.writeLock().isLocked();

        try {
            try {
                if (dst instanceof File) {
                    final File dstFile = (File) dst;
                    if (dstFile.isArchive() && (dstFile.isDirectory()
                            || (dstFile.exists() && !dstFile.isFile()))) {
                        throw new FileNotFoundException(dst.getPath() +
                            ": Cannot overwrite (possibly inaccessible) archive file!");
                    }
                    final String dstEntryName = dstFile.getEnclEntryName();
                    if (dstEntryName != null) {
                        cp( srcFile, srcController, srcEntryName,
                            dstFile,
                            dstFile.getEnclArchive().getArchiveController(),
                            dstEntryName,
                            preserve);
                        return;
                    }
                }

                final InputStream in;
                final long time;
                srcController.readLock().lock();
                try {
                    try {
                        in = srcController.getInputStream(srcEntryName); // detects false positives!
                    } catch (IOException failure) {
                        throw new InputIOException(failure);
                    }
                    time = srcController.lastModified(srcEntryName);
                } finally {
                    srcController.readLock().unlock();
                }

                // Treat the destination like a regular file.
                final OutputStream out;
                try {
                    out = new java.io.FileOutputStream(dst);
                } catch (IOException failure) {
                    try {
                        in.close();
                    } catch (IOException inFailure) {
                        throw new InputIOException(inFailure);
                    }
                    throw failure;
                }

                File.cp(in, out);
                if (preserve && !dst.setLastModified(time))
                    throw new FileNotFoundException(dst.getPath() +
                            ": Couldn't preserve last modification time!");
            } catch (FalsePositiveDirectoryEntryException failure) {
                assert srcController == failure.getSource();
                // Reroute call to the source's enclosing archive controller.
                cp( srcFile, srcController.getEnclController(),
                    srcController.enclEntryName(srcEntryName),
                    dst, preserve);
            }
        } catch (FalsePositiveNativeException failure) {
            assert srcController == failure.getSource();
            // Reroute call to treat the source like a regular file.
            cp(srcFile.getDelegate(), dst, preserve);
        }
    }

    /**
     * Copies a source file to a destination file, optionally preserving the
     * source's last modification time.
     * We know that the source and destination files both appear to be entries
     * in an archive file.
     *
     * @throws FalsePositiveException If the source or the destination is a
     *         false positive and the exception for the destination
     *         cannot get resolved within this method.
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     */
    protected static void cp(
            final File srcFile,
            final ArchiveController srcController,
            final String srcEntryName,
            final File dstFile,
            final ArchiveController dstController,
            final String dstEntryName,
            final boolean preserve)
    throws IOException {
        // Do not assume anything about the lock status of the controller:
        // This method may be called from a subclass while a lock is acquired!
        //assert !srcController.readLock().isLocked();
        //assert !srcController.writeLock().isLocked();
        //assert !dstController.readLock().isLocked();
        //assert !dstController.writeLock().isLocked();

        try {
            class IOStreamCreator implements IORunnable {
                InputStream in;
                OutputStream out;

                public void run() throws IOException {
                    final ArchiveEntry srcEntry, dstEntry;
                    final ArchiveFileSystem.Delta delta;

                    // Update controllers.
                    // This may invalidate the file system object, so it must be
                    // done first in case srcController and dstController are the
                    // same!
                    class SrcControllerUpdater implements IORunnable {
                        public void run() throws IOException {
                            if (srcController.hasNewData(srcEntryName))
                                srcController.update();
                            srcController.readLock().lock(); // downgrade lock
                        }
                    }
                    srcController.runWriteLocked(new SrcControllerUpdater());
                    try {
                        if (dstController.hasNewData(dstEntryName))
                            dstController.update();

                        // Get source archive entry.
                        final ArchiveFileSystem srcFileSystem
                                = srcController.getFileSystem(false);
                        srcEntry = srcFileSystem.get(srcEntryName);

                        // Get destination archive entry.
                        final boolean isLenient = File.isLenient();
                        final ArchiveFileSystem dstFileSystem
                                = dstController.getFileSystem(isLenient);
                        delta = dstFileSystem.beginCreateAndLink(
                                dstEntryName, isLenient, preserve ? srcEntry : null);
                        dstEntry = delta.getEntry();

                        // Get input stream.
                        try {
                            in = srcController.getInputStream(srcEntry, dstEntry);
                        } catch (IOException failure) {
                            throw new InputIOException(failure);
                        }
                    } finally {
                        srcController.readLock().unlock();
                    }

                    try {
                        // Get output stream.
                        out = dstController.getOutputStream(dstEntry, srcEntry);

                        try {
                            // Commit the transaction to create the destination entry.
                            delta.commit();
                        } catch (IOException failure) {
                            out.close();
                            throw failure;
                        }
                    } catch (IOException failure) {
                        try {
                            in.close();
                        } catch (IOException inFailure) {
                            throw new InputIOException(inFailure);
                        }
                        throw failure;
                    }
                }
            }

            final IOStreamCreator runnable = new IOStreamCreator();
            synchronized (copyLock) {
                dstController.runWriteLocked(runnable);
            }
            final InputStream in = runnable.in;
            final OutputStream out = runnable.out;

            // Finally copy the entry data.
            File.cp(in, out);
        } catch (FalsePositiveDirectoryEntryException failure) {
            // Both the source and/or the destination may be false positives,
            // so we need to use the exception's additional information to
            // find out which controller actually detected the false positive.
            if (dstController != failure.getSource())
                throw failure; // not my job - pass on!

            // Reroute call to the destination's enclosing archive controller.
            cp( srcFile, srcController, srcEntryName,
                dstFile, dstController.getEnclController(),
                dstController.enclEntryName(dstEntryName),
                preserve);
        } catch (FalsePositiveNativeException failure) {
            // Both the source and/or the destination may be false positives,
            // so we need to use the exception's additional information to
            // find out which controller actually detected the false positive.
            if (dstController != failure.getSource())
                throw failure; // not my job - pass on!

            // Reroute call to treat the destination like a regular file.
            cp( srcFile, srcController, srcEntryName,
                dstFile.getDelegate(),
                preserve);
        }
    }

    /**
     * Copies a source file to a destination file, optionally preserving the
     * source's last modification time.
     * We already have an input stream to read the source file and the
     * destination appears to be an entry in an archive file.
     * Note that this method <em>never</em> closes the given input stream!
     * <p>
     * Note that this method synchronizes on the class object in order
     * to prevent dead locks by two threads copying archive entries to the
     * other's source archive concurrently!
     *
     * @throws FalsePositiveException If the destination is a
     *         false positive and the exception
     *         cannot get resolved within this method.
     * @throws InputIOException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     */
    protected static void cp(
            final java.io.File src,
            final InputStream in,
            final File dstFile,
            final ArchiveController dstController,
            final String dstEntryName,
            final boolean preserve)
    throws IOException {
        // Do not assume anything about the lock status of the controller:
        // This method may be called from a subclass while a lock is acquired!
        //assert !dstController.readLock().isLocked();
        //assert !dstController.writeLock().isLocked();

        try {
            class OStreamCreator implements IORunnable {
                OutputStream out;

                public void run() throws IOException {
                    // Update controller.
                    // This may invalidate the file system object, so it must be
                    // done first in case srcController and dstController are the
                    // same!
                    if (dstController.hasNewData(dstEntryName))
                        dstController.update();

                    final boolean isLenient = File.isLenient();

                    // Get source archive entry.
                    final ArchiveEntry srcEntry
                            = new File2ArchiveEntryAdapter(src);

                    // Get destination archive entry.
                    final ArchiveFileSystem dstFileSystem
                            = dstController.getFileSystem(isLenient);
                    final ArchiveFileSystem.Delta transaction
                            = dstFileSystem.beginCreateAndLink(
                                dstEntryName, isLenient, preserve ? srcEntry : null);
                    final ArchiveEntry dstEntry = transaction.getEntry();

                    // Get output stream.
                    out = dstController.getOutputStream(dstEntry, srcEntry);

                    // Commit the transaction to create the destination entry.
                    transaction.commit();
                }
            }

            // Create the output stream while the destination controller is
            // write locked.
            final OStreamCreator runnable = new OStreamCreator();
            dstController.runWriteLocked(runnable);
            final OutputStream out = runnable.out;

            // Finally copy the entry data.
            try {
                File.cat(in, out);
            } finally {
                out.close();
            }
        } catch (FalsePositiveDirectoryEntryException failure) {
            assert dstController == failure.getSource();
            // Reroute call to the destination's enclosing ArchiveController.
            cp( src, in,
                dstFile, dstController.getEnclController(),
                dstController.enclEntryName(dstEntryName),
                preserve);
        } catch (FalsePositiveNativeException failure) {
            assert dstController == failure.getSource();
            // Reroute call to treat the destination like a regular file.
            cp(src, in, dstFile.getDelegate(), preserve);
        }
    }

    //
    // Static member interfaces.
    //

    protected interface IORunnable {
        void run() throws IOException;
    }

    //
    // Static member classes.
    //

    /**
     * A lock used when copying data from one archive to another.
     * This lock must be acquired before any other locks on the controllers
     * are acquired in order to prevent dead locks.
     */
    private static final class CopyLock { }

    /**
     * This class is required to decorate an instance of JSE 1.5's
     * <code>java.util.concurrent.locks.ReentrantLock</code> interface in order
     * to provide additional information about the status of the lock
     * for the current thread.
     */
    /*protected static final class TestableLock implements ReentrantLock {
        private final ReentrantLock lock;
        private final ThreadLocal count = new ThreadLocal() {
            protected Object initialValue() {
                return new Integer(0);
            }
        };

        private TestableLock(ReentrantLock lock) {
            this.lock = lock;
        }

        public boolean isLocked() {
            return get() > 0;
        }

        public int get() {
            return ((Integer) count.get()).intValue();
        }

        public void lock() {
            lock.lock();
            incCount();
        }

        public void lockInterruptibly() throws InterruptedException {
            lock.lockInterruptibly();
            incCount();
        }

        public boolean tryLock() {
            boolean locked = lock.tryLock();
            if (locked)
                incCount();
            return locked;
        }

        public boolean tryLock(long time, TimeUnit unit)
        throws InterruptedException {
            boolean locked = lock.tryLock(time, unit);
            if (locked)
                incCount();
            return locked;
        }

        public void unlock() {
            lock.unlock();
            decCount();
        }

        public Condition newCondition() {
            return lock.newCondition();
        }

        private void incCount() {
            int count = ((Integer) this.count.get()).intValue();
            this.count.set(new Integer(count + 1));
        }

        private void decCount() {
            int count = ((Integer) this.count.get()).intValue();
            if (count > 0)
                this.count.set(new Integer(count - 1));
        }
    }*/

    static final class ShutdownHook extends Thread {
        private static final ShutdownHook singleton = new ShutdownHook();

        /**
         * The set of files to delete when the shutdown hook is run.
         * When iterating over it, its elements are returned in insertion order.
         */
        static final Set deleteOnExit
                = Collections.synchronizedSet(new LinkedHashSet());

        private ShutdownHook() {
            super("TrueZIP ArchiveController Shutdown Hook");
            setPriority(Thread.MAX_PRIORITY);
        }

        /**
         * Deletes all files that have been marked by
         * {@link File#deleteOnExit} and finally unmounts all controllers.
         * <p>
         * Logging and password prompting will be disabled (they wouldn't work
         * in a JVM shutdown hook anyway) in order to provide a deterministic
         * behaviour and in order to avoid RuntimeExceptions or even Errors
         * in the API.
         * <p>
         * Any exceptions thrown throughout the update will be printed on
         * standard error output.
         * <p>
         * Note that this method is <em>not</em> re-entrant and should not be
         * directly called except for unit testing (you couldn't do a unit test
         * on a shutdown hook otherwise, could you?).
         */
        public void run() {
            try { // paranoid, but safe.
                logger.setLevel(Level.OFF);

                for (Iterator i = deleteOnExit.iterator(); i.hasNext(); ) {
                    final File file = (File) i.next();
                    if (file.exists() && !file.delete()) {
                        System.err.println(
                                file.getPath() + ": failed to deleteOnExit()!");
                    }
                }
            } finally {
                try {
                    updateAll("", false, true, false, true, false, false);
                } catch (ArchiveException oops) {
                    oops.printStackTrace();
                }
            }
        }
    }

    private static final class LiveStatistics implements ArchiveStatistics {
        public long getUpdateTotalByteCountRead() {
            return CountingReadOnlyFile.getTotal();
        }

        public long getUpdateTotalByteCountWritten() {
            return CountingOutputStream.getTotal();
        }

        public int getArchivesTotal() {
            // This is not 100% correct:
            // Controllers which have been removed from the WeakReference
            // VALUE in the map meanwhile, but not yet removed from the map
            // are counted as well.
            // But hey, this is only statistics, right?
            return controllers.size();
        }

        public int getArchivesTouched() {
            int result = 0;

            final Enumeration e = new ControllerEnumeration();
            while (e.hasMoreElements()) {
                final ArchiveController c = (ArchiveController) e.nextElement();
                c.readLock().lock();
                try {
                    if (c.isTouched())
                        result++;
                } finally {
                    c.readLock().unlock();
                }
            }

            return result;
        }

        public int getTopLevelArchivesTotal() {
            int result = 0;

            final Enumeration e = new ControllerEnumeration();
            while (e.hasMoreElements()) {
                final ArchiveController c = (ArchiveController) e.nextElement();
                if (c.getEnclController() == null)
                    result++;
            }

            return result;
        }

        public int getTopLevelArchivesTouched() {
            int result = 0;

            final Enumeration e = new ControllerEnumeration();
            while (e.hasMoreElements()) {
                final ArchiveController c = (ArchiveController) e.nextElement();
                c.readLock().lock();
                try {
                    if (c.getEnclController() == null && c.isTouched())
                        result++;
                } finally {
                    c.readLock().unlock();
                }
            }

            return result;
        }
    }

    private static final class ControllerEnumeration implements Enumeration {
        private final Iterator it;

        public ControllerEnumeration() {
            this("", null);
        }

        public ControllerEnumeration(final String prefix, final Comparator c) {
            assert prefix != null;

            final Set snapshot;
            synchronized (controllers) {
                if (c != null) {
                    snapshot = new TreeSet(c);
                } else {
                    snapshot = new HashSet((int) (controllers.size() / 0.75f));
                }

                final Iterator it = controllers.values().iterator();
                while (it.hasNext()) {
                    Object value = it.next();
                    if (value instanceof Reference) {
                        value = ((Reference) value).get(); // dereference
                        if (value != null) {
                            assert value instanceof ArchiveController;
                            if (((ArchiveController) value).getPath().startsWith(prefix))
                                snapshot.add(value);
                        //} else {
                            // This may happen if there are no more strong
                            // references to the controller and it has been removed
                            // from the weak reference in the hash map's value
                            // before it's been removed from the hash map's key
                            // (shit happens)!
                        }
                    } else {
                        assert value != null;
                        assert value instanceof ArchiveController;
                        if (((ArchiveController) value).getPath().startsWith(prefix))
                            snapshot.add(value);
                    }
                }
            }

            it = snapshot.iterator();
        }

        public boolean hasMoreElements() {
            return it.hasNext();
        }

        public Object nextElement() {
            return it.next();
        }
    }

    // TODO: Document this!
    protected static final class CountingReadOnlyFile extends SimpleReadOnlyFile {
        private static volatile long _total;
        //private static volatile boolean _resetOnReuse;

        public static long getTotal() {
            return _total;
        }

        private static void setResetOnReuse() {
            //_resetOnReuse = true;
        }

        private static void resetOnReuse() {
            /*if (_resetOnReuse) {
                _resetOnReuse = false;*/
                _total = 0;
            //}
        }
        
        protected CountingReadOnlyFile(java.io.File file)
        throws FileNotFoundException {
            super(file);
            resetOnReuse();
        }

        public int read() throws IOException {
            int ret = super.read();
            if (ret != -1)
                _total++;
            return ret;
        }

        public int read(byte[] b) throws IOException {
            int ret = super.read(b);
            if (ret != -1)
                _total += ret;
            return ret;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int ret = super.read(b, off, len);
            if (ret != -1)
                _total += ret;
            return ret;
        }

        public int skipBytes(int n) throws IOException {
            int ret = super.skipBytes(n);
            _total += ret;
            return ret;
        }
    }

    /*protected static final class CountingInputStream extends FilterInputStream {
        private static volatile long _total;
        private static volatile boolean _resetOnReuse;

        public static long getTotal() {
            return _total;
        }

        private static void setResetOnReuse() {
            _resetOnReuse = true;
        }

        private static void resetOnReuse() {
            if (_resetOnReuse) {
                _resetOnReuse = false;
                _total = 0;
            }
        }

        protected CountingInputStream(InputStream in) {
            super(in);
            resetOnReuse();
        }
        
        public int read() throws IOException {
            int ret = in.read();
            if (ret != -1)
                _total++;
            return ret;
        }
        
        public int read(byte b[], int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n != -1)
                _total += n;
            return n;
        }
        
        public long skip(long n) throws IOException {
            n = in.skip(n);
            _total += n;
            return n;
        }
        
        public boolean markSupported() {
            return false;
        }
    }*/

    protected static final class CountingOutputStream extends FilterOutputStream {
        private static volatile long _total;
        private static volatile boolean _resetOnReuse;

        public static long getTotal() {
            return _total;
        }

        private static void setResetOnReuse() {
            _resetOnReuse = true;
        }

        private static void resetOnReuse() {
            if (_resetOnReuse) {
                _resetOnReuse = false;
                _total = 0;
            }
        }

        protected CountingOutputStream(OutputStream out) {
            super(out);
            resetOnReuse();
        }
        
        public void write(final int b) throws IOException {
            out.write(b);
            _total++;
        }
        
        public void write(byte b[], int off, int len) throws IOException {
            out.write(b, off, len);
            _total += len;
        }
    }

    private static final class File2ArchiveEntryAdapter implements ArchiveEntry {
        private final java.io.File file;

        private File2ArchiveEntryAdapter(final java.io.File file) {
            assert file != null;
            this.file = file;
        }

        public String getName() {
            assert false : "Drivers should never call this method!";
            // The returned name is not really useful, but should be OK for
            // this simple adapter.
            if (file.isDirectory())
                return file.getName() + "/";
            else
                return file.getName();
        }

        public boolean isDirectory() {
            return file.isDirectory();
        }

        public long getSize() {
            return file.length();
        }

        public long getTime() {
            return file.lastModified();
        }

        public void setTime(long time) {
            assert false : "Drivers should never call this method!";
            file.setLastModified(time); // ignores return value
        }

        public Icon getOpenIcon() {
            return null;
        }

        public Icon getClosedIcon() {
            return null;
        }
        
        public ArchiveEntryMetaData getMetaData() {
            throw new AssertionError("Drivers should never call this method!");
        }

        public void setMetaData(ArchiveEntryMetaData metaData) {
            throw new AssertionError("Drivers should never call this method!");
        }
    }

    //
    // Exception classes.
    // These are all instance (inner) classes, not just static member classes.
    //

    /**
     * Thrown to indicate that a controller's target file is a false positive
     * archive file which actually exists as a plain file or directory
     * in the native file system or in an enclosing archive file.
     */
    protected abstract class FalsePositiveException
            extends FileNotFoundException {
        protected FalsePositiveException(String path) {
            super(path);
        }

        /**
         * Returns the archive controller which has thrown this exception.
         * This is the controller which detected the false positive archive
         * file.
         */
        public ArchiveController getSource() {
            return ArchiveController.this;
        }
    }

    /**
     * Thrown to indicate that a controller's target file is a false positive
     * archive file which actually exists as a plain file or directory
     * in the native file system.
     * <p>
     * Instances of this class are always associated with an IOException as
     * its cause.
     */
    protected class FalsePositiveNativeException
            extends FalsePositiveException {
        protected FalsePositiveNativeException(IOException cause) {
            super(getPath());
            initCause(cause);
        }
    }

    /**
     * Thrown to indicate that a controller's target file is a false positive
     * archive file which actually exists as a plain file or directory
     * in an enclosing archive file.
     */
    protected abstract class FalsePositiveEntryException
            extends FalsePositiveException {
        /**
         * @param target The controller in which the archive file exists
         *        as a false positive - never <code>null</code>.
         * @param entryName The entry path name of the archive file
         *        which is a false positive - never <code>null</code>.
         */
        protected FalsePositiveEntryException(
                ArchiveController target,
                String entryName) {
            this(target, entryName, null);
        }

        /**
         * @param target The controller in which the archive file exists
         *        as a false positive - never <code>null</code>.
         * @param entryName The entry path name of the archive file
         *        which is a false positive - never <code>null</code>.
         * @param cause The IOException which caused this false positive
         *        indication exception.
         */
        protected FalsePositiveEntryException(
                ArchiveController target,
                String entryName,
                IOException cause) {
            super(target.getPath() + File.separator + entryName);
            assert target != null;
            assert entryName != null;
            this.target = target;
            this.entryName = entryName;
            if (cause != null)
                initCause(cause);
        }

        private final ArchiveController target;

        /**
         * Returns the controller which's target file contains the
         * false positive archive file as an entry.
         * Never <code>null</code>.
         */
        public ArchiveController getTarget() {
            return target;
        }

        private final String entryName;

        /**
         * Returns the entry path name of the false positive archive file.
         * Never <code>null</code>.
         */
        public String getEntryName() {
            return entryName;
        }
    }

    /**
     * Thrown to indicate that a controller's target file is a false positive
     * archive file which actually exists as a plain file in an enclosing
     * archive file.
     * <p>
     * Instances of this class are always associated with an IOException as
     * its cause.
     */
    protected class FalsePositiveFileEntryException
            extends FalsePositiveEntryException {
        protected FalsePositiveFileEntryException(
                ArchiveController enclController,
                String enclEntryName,
                IOException cause) {
            super(enclController, enclEntryName, cause);
        }
    }

    /**
     * Thrown to indicate that a controller's target file is a false positive
     * archive file which actually exists as a plain directory in an enclosing
     * archive file.
     */
    protected class FalsePositiveDirectoryEntryException
            extends FalsePositiveEntryException {
        protected FalsePositiveDirectoryEntryException(
                ArchiveController enclController,
                String enclEntryName) {
            super(enclController, enclEntryName);
        }
    }
}
