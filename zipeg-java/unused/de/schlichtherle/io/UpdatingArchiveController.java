/*
 * UpdatingArchiveController.java
 *
 * Created on 28. März 2006, 17:40
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

import de.schlichtherle.io.ArchiveController.CountingOutputStream;
import de.schlichtherle.io.ArchiveController.CountingReadOnlyFile;
import de.schlichtherle.io.ArchiveController.FalsePositiveDirectoryEntryException;
import de.schlichtherle.io.ArchiveController.FalsePositiveFileEntryException;
import de.schlichtherle.io.ArchiveController.FalsePositiveNativeException;
import de.schlichtherle.io.ArchiveController.IORunnable;
import de.schlichtherle.io.archive.spi.ArchiveEntry;
import de.schlichtherle.io.archive.spi.ArchiveDriver;
import de.schlichtherle.io.archive.spi.InputArchive;
import de.schlichtherle.io.archive.spi.OutputArchive;
import de.schlichtherle.io.rof.ReadOnlyFile;
import de.schlichtherle.io.rof.SimpleReadOnlyFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An archive controller which's update strategy for updated archive entries
 * is to do a full update of the target archive file
 * (as opposed to appending the updated archive entries to the target archive
 * file).
 * This class also contains all the code which deals with states and
 * transitions.
 * 
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0 (refactored from the former <code>ZipController</code>)
 */
// TODO: Refactor this to the state pattern and introduce strategy pattern
// in order to support different update strategies.
final class UpdatingArchiveController extends ArchiveController {

    //
    // Static fields.
    //

    private static final String CLASS_NAME
            = "de/schlichtherle/io/UpdatingArchiveController".replace('/', '.'); // beware of code obfuscation!
    private static final Logger logger = Logger.getLogger(CLASS_NAME, CLASS_NAME);

    /** Prefix for temporary files created by this class. */
    static final String TEMP_FILE_PREFIX = "tzp";

    /**
     * Suffix for temporary files created by this class
     * - should <em>not</em> be <code>null</code> for enhanced unit tests.
     */
    static final String TEMP_FILE_SUFFIX = ".tmp";

    //
    // Instance fields.
    //

    /**
     * The actual archive file as a plain <code>java.io.File</code> object
     * which serves as the input file for the virtual file system managed
     * by this {@link ArchiveController} object.
     * Note that this will be set to a tempory file if the archive file is
     * enclosed within another archive file.
     */
    private java.io.File inFile;
    
    /**
     * An {@link InputArchive} object used to mount the virtual file system
     * and read the entries from the archive file.
     */
    private InputArchive inArchive;
    
    /**
     * The virtual archive file system.
     */
    private ArchiveFileSystem fileSystem;
    
    /**
     * Plain <code>java.io.File</code> object used for temporary output.
     * Maybe identical to <code>inFile</code>.
     */
    private java.io.File outFile;
    
    /**
     * The (possibly temporary) {@link OutputArchive} we are writing newly
     * created or modified entries to.
     */
    private OutputArchive outArchive;
    
    /**
     * Whether or not nesting this archive file to its enclosing
     * archive file has been deferred.
     */
    private boolean needsReassembly;

    //
    // Constructors.
    //

    UpdatingArchiveController(
            java.io.File target,
            ArchiveController enclController,
            String enclEntryName,
            ArchiveDriver driver) {
        super(target, enclController, enclEntryName, driver);
    }

    //
    // Methods.
    //
    
    protected ArchiveFileSystem getFileSystem(final boolean autoCreate)
    throws IOException {
        assert readLock().isLocked() || writeLock().isLocked();

        if (fileSystem == null) {
            runWriteLocked(new IORunnable() {
                public void run() throws IOException {
                    if (fileSystem == null) // check again!
                        mount(autoCreate);
                }
            });
        }
        return fileSystem;
    }

    private void mount(final boolean autoCreate)
    throws IOException {
        logger.log(Level.FINER, "mount.entering", // NOI18N
                new Object[] {
                    getPath(),
                    Boolean.valueOf(autoCreate),
        });
        try {
            mountImpl(autoCreate);
        } catch (Throwable failure) {
            // Log at FINER level. This is mostly because of false positives.
            logger.log(Level.FINER, "mount.throwing", failure); // NOI18N
            if (failure instanceof IOException)
                throw (IOException) failure;
            else if (failure instanceof RuntimeException)
                throw (RuntimeException) failure;
            else
                throw (Error) failure; // must be Error, throws ClassCastException otherwise!
        } finally {
            logger.log(Level.FINER, "mount.finally", // NOI18N
                    new Object[] {
                        getPath(),
                        Boolean.valueOf(autoCreate),
            });
        }
    }

    private void mountImpl(final boolean autoCreate)
    throws FileNotFoundException, IOException {
        assert writeLock().isLocked();
        assert fileSystem == null;
        assert inArchive == null;
        assert outArchive == null;
        assert outFile == null;

        // We need to mount the virtual file system from the input file.
        // and so far we have not successfully opened the input file.
        if (usesNativeTargetFile()) {
            // The target file of this controller is NOT enclosed
            // in another archive file.
            // Test modification time BEFORE opening the input file!
            if (inFile == null)
                inFile = getTarget();
            final long time = inFile.lastModified();
            if (time != 0) {
                // The archive file exists.
                // Thoroughly test read-only status BEFORE opening
                // the device file!
                final boolean isReadOnly = !isWritableOrCreatable(inFile);
                try {
                    initInArchive(inFile);
                } catch (IOException failure) {
                    // Wrap cause so that a matching catch block can assume
                    // that it can access the target in the native file system.
                    throw new FalsePositiveNativeException(failure);
                }
                fileSystem = new ArchiveFileSystem(
                        this, inArchive, time, isReadOnly);
            } else if (!autoCreate) {
                // The archive file does not exist.
                throw new ArchiveNotFoundException();
            } else {
                // The archive file does NOT exist, but we may create
                // it automatically.
                // Setup output first to implement fail-fast behavior.
                // This may fail e.g. if the target file is a RAES
                // encrypted ZIP file and the user cancels password
                // prompting.
                ensureOutArchive();
                fileSystem = new ArchiveFileSystem(this);
            }
        } else {
            // The target file of this controller IS (or appears to be)
            // enclosed in another archive file.
            if (inFile == null) {
                unwrap(getEnclController(), getEnclEntryName(), autoCreate);
            } else {
                // The enclosed archive file has already been updated.
                // Reuse temporary input file.
                // If this throws an exception, most likely we have already
                // tried to unwrap(...) the entry for the target file from the
                // enclosing archive file before and failed to mount it
                // as a archive file, in which case a
                // FalsePositiveEntryException has been thrown.
                // Then this try would be a repetition of the initial try
                // without the need to go through unwrap(...) again., which
                // is a good solution in terms of performance.
                // The update(...) method and finalize() will take care of
                // cleaning up the temporary file.
                // Another scenario is that someone has messed around with the
                // temporary file, in which case we should better let the
                // IOException pass. However, we cannot distingush these
                // scenarios and the latter is very unlikely, so we stick with
                // this for performance reasons.
                try {
                    initInArchive(inFile);
                } catch (IOException failure) {
                    throw new FalsePositiveFileEntryException(
                            getEnclController(), getEnclEntryName(), failure);
                }
                fileSystem = new ArchiveFileSystem(
                        this,
                        inArchive,
                        inFile.lastModified(),
                        false);
            }
        }

        assert fileSystem != null;
    }

    /**
     * Returns <code>true</code> if the given file exists or can be created
     * and at least one byte can be successfully written to it - the file is
     * restored to its previous state afterwards.
     * This is a much stronger test than <code>File.canWrite()</code>.
     * <p>
     * Please note that if the file is actually open for reading or other
     * activities this method may not be able to reset the last modification
     * time of the file after testing, in which case <code>false</code> is
     * returned.
     * This is known to apply to the Windows platform, but not the Linux
     * platform.
     */
    static boolean isWritableOrCreatable(final java.io.File file) {
        try {
            if (!file.exists()) {
                final boolean created = file.createNewFile();
                try {
                    return isWritableOrCreatable(file);
                } finally {
                    if (created && !file.delete())
                        return false; // be conservative!
                }
            } else if (file.canWrite()) {
                // Some operating and file system combinations make File.canWrite()
                // believe that the file is writable although it's not.
                // We are not that gullible, so let's test this...
                final long time = file.lastModified();
                if (!file.setLastModified(time + 1)) {
                    // This may happen on Windows and normally means that
                    // somebody else has opened this file
                    // (regardless of read or write mode).
                    // Be conservative: We don't allow writing to this file!
                    return false;
                }
                try {
                    // Open the file for reading and writing, requiring any
                    // update to its contents to be written to the filesystem
                    // synchronously.
                    // As Dr. Simon White from Catalysoft, Cambridge, UK reported,
                    // "rws" does NOT work on Mac OS X with Apple's Java 1.5
                    // Release 1 (equivalent to Sun's Java 1.5.0_02), however
                    // it DOES work with Apple's Java 1.5 Release 3.
                    // Dr. White also confirmed that "rwd" works on Apple's
                    // Java 1.5 Release 1, so we use this instead.
                    // Thank you very much for spending the time to fix this
                    // issue, Dr. White!
                    final RandomAccessFile raf = new RandomAccessFile(file, "rwd");
                    try {
                        final boolean empty;
                        int octet = raf.read();
                        if (octet == -1) {
                            octet = 0; // assume first byte is 0
                            empty = true;
                        } else {
                            empty = false;
                        }
                        // Let's test if we can (over)write the first byte.
                        raf.seek(0);
                        raf.write((octet ^ -1) & 0xFF); // write complement
                        try {
                            // Rewrite original content and check success.
                            raf.seek(0);
                            raf.write(octet);
                            raf.seek(0);
                            final int check = raf.read();
                            // This should always return true unless the storage
                            // device is faulty.
                            return octet == check;
                        } finally {
                            if (empty)
                                raf.setLength(0);
                        }
                    } finally {
                        raf.close();
                    }
                } finally {
                    if (!file.setLastModified(time)) {
                        // This may happen on Windows and normally means that
                        // somebody else has opened this file meanwhile
                        // (regardless of read or write mode).
                        // Be conservative: We don't allow (further) writing to
                        // this file!
                        return false;
                    }
                }
            } else { // if (file.exists() && !file.canWrite()) {
                return false;
            }
        } catch (IOException failure) {
            return false; // don't allow writing if anything goes wrong!
        }
    }

    private void unwrap(
            final ArchiveController controller,
            final String entryName,
            final boolean autoCreate)
    throws IOException {
        assert writeLock().isLocked();
        assert controller != null;
        assert entryName != null;
        assert File.EMPTY != entryName;
        assert fileSystem == null;
        assert inArchive == null;
        assert inFile == null;
        assert outArchive == null;
        assert outFile == null;

        try {
            class Unwrapper implements IORunnable {
                public void run() throws IOException {
                    final ArchiveFileSystem controllerFileSystem;
                    try {
                        controllerFileSystem = controller.getFileSystem(
                                autoCreate && File.isLenient());
                    } catch (FalsePositiveNativeException failure) {
                        // Unwrap cause so that we don't catch recursively here and
                        // disable any other matching catch blocks for failure.
                        throw (IOException) failure.getCause();
                    }
                    if (controllerFileSystem.isFile(entryName)) {
                        // This archive file DOES exist in the enclosing archive.
                        // The input file is only temporarily used for the
                        // archive file entry.
                        final java.io.File tmp = File.createTempFile(
                                TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
                        // We do properly delete our temps, so this is not required.
                        // In addition, this would be dangerous as the deletion
                        // could happen before our shutdown hook has a chance to
                        // process this controller!!!
                        //tmp.deleteOnExit();
                        try {
                            // Now un-archive the entry to the temporary file.
                            File.cp(controller.getInputStream(entryName),
                                    new java.io.FileOutputStream(tmp));
                            try {
                                initInArchive(tmp);
                            } catch (IOException failure) {
                                throw new FalsePositiveFileEntryException(
                                        controller, entryName, failure);
                            }
                        } catch (Throwable failure) {
                            // failure could be a NoClassDefFoundError if target is an RAES
                            // encrypted ZIP file and Bouncycastle's Lightweight
                            // Crypto API is not in the classpath.
                            // We are just catching all kinds of Throwables to make sure
                            // that we always delete the newly created temp file.
                            // Finally, we pass on the catched exception.
                            if (!tmp.delete()) {
                                // This should normally never happen...
                                throw new IOException(tmp.getPath()
                                        + ": Couldn't delete corrupted input file!");
                            }
                            if (failure instanceof IOException)
                                throw (IOException) failure;
                            else if (failure instanceof RuntimeException)
                                throw (RuntimeException) failure;
                            else
                                throw (Error) failure; // must be Error, throws ClassCastException otherwise!
                        }
                        inFile = tmp; // init on success only!
                        fileSystem = new ArchiveFileSystem(
                                UpdatingArchiveController.this,
                                inArchive,
                                controllerFileSystem.lastModified(entryName),
                                controllerFileSystem.isReadOnly());
                    } else if (controllerFileSystem.isDirectory(entryName)) {
                        throw new FalsePositiveDirectoryEntryException(
                                controller, entryName);
                    } else if (!autoCreate) {
                        // The entry does NOT exist in the enclosing archive
                        // file and we may not link it automatically.
                        throw new ArchiveNotFoundException();
                    } else {
                        assert autoCreate;

                        // The entry does NOT exist in the enclosing archive
                        // file, but we may create it automatically.
                        // TODO: Why do we need to pass File.isLenient() instead
                        // of just true? Document this!
                        final ArchiveFileSystem.Delta delta
                                = controllerFileSystem.beginCreateAndLink(
                                    entryName, File.isLenient());

                        // This may fail if e.g. the target file is an RAES
                        // encrypted ZIP file and the user cancels password
                        // prompting.
                        ensureOutArchive();

                        // Now try to create the entry in the enclosing controller.
                        try {
                            delta.commit();
                        } catch (IOException failure) {
                            // The delta on the *enclosing* controller failed.
                            // Hence, we need to revert our state changes.
                            try {
                                try {
                                    outArchive.close();
                                } finally {
                                    outArchive = null;
                                }
                            } finally {
                                boolean deleted = outFile.delete();
                                assert deleted;
                                outFile = null;
                            }

                            throw failure;
                        }

                        fileSystem = new ArchiveFileSystem(
                                UpdatingArchiveController.this);
                    }
                } // public void run() ...
            } // class Unwrapper...
            controller.runWriteLocked(new Unwrapper());
        } catch (FalsePositiveDirectoryEntryException failure) {
            // We could have catched this exception in the inner try-catch
            // block where we access the controller's file system as well,
            // but then we would still hold the lock on controller, which
            // is not necessary while accessing the file system of the
            // enclosing controller.
            if (failure.getTarget() == controller)
                throw failure; // just created - pass on

            unwrap( controller.getEnclController(),
                    controller.enclEntryName(entryName),
                    autoCreate);
        }
        
        assert fileSystem != null;
    }
    
    /**
     * Initializes <code>inArchive</code> with a newly created
     * {@link InputArchive} for reading <code>inFile</code>.
     * 
     * 
     * @throws IOException On any I/O related issue with <code>inFile</code>.
     */
    private void initInArchive(final java.io.File inFile)
    throws IOException {
        assert writeLock().isLocked();
        assert inArchive == null;

        logger.log(Level.FINEST, "initInArchive.entering", inFile); // NOI18N
        try {
            final ReadOnlyFile rof;
            if (usesNativeTargetFile())
                rof = new CountingReadOnlyFile(inFile);
            else
                rof = new SimpleReadOnlyFile(inFile);
            try {
                inArchive = getDriver().createInputArchive(this, rof);
                inArchive.setMetaData(new InputArchiveMetaData(inArchive));
            } catch (Throwable failure) {
                // failure could be a NoClassDefFoundError if target is an RAES
                // encrypted ZIP file and Bouncycastle's Lightweight
                // Crypto API is not in the classpath.
                // We are just catching all kinds of Throwables to make sure
                // that we always close the read only file.
                // Finally, we will pass on the catched exception.
                rof.close();
                if (failure instanceof IOException)
                    throw (IOException) failure;
                else if (failure instanceof RuntimeException)
                    throw (RuntimeException) failure;
                else if (failure instanceof Error)
                    throw (Error) failure;
                else
                    throw new AssertionError(failure); // cannot happen!
            }
        } catch (IOException failure) {
            assert inArchive == null;
            logger.log(Level.FINEST, "initInArchive.throwing", failure); // NOI18N
            throw failure;
        } finally {
            logger.log(Level.FINEST, "initInArchive.finally",
                    new Object[] {
                        inFile,
                        new Integer(inArchive != null
                                ? inArchive.getNumArchiveEntries()
                                : 0)
                    }); // NOI18N
        }

        assert inArchive != null;
    }

    /**
     * A factory method returning an input stream which is positioned
     * at the beginning of the given entry in the target archive file.
     *
     * @param entryName An entry in the virtual archive file system.
     *        <code>null</code> is not allowed.
     *
     * @return A valid InputStream object - <code>null</code> is never returned.
     *
     * @throws FileNotFoundException If the archive file does not exist or the
     *         requested entry is a directory or does not exist in the archive
     *         file.
     * @throws IOException if the entry can't be opened for reading from the
     *         archive file (this usually means that the archive file is corrupted).
     */
    protected InputStream getInputStream(final String entryName)
    throws IOException {
        assert entryName != null;

        readLock().lock();
        try {
            if (hasNewData(entryName)) {
                runWriteLocked(new IORunnable() {
                    public void run() throws IOException {
                        if (hasNewData(entryName)) // check again!
                            update();
                    }
                });
            }

            ArchiveEntry entry = getFileSystem(false).get(entryName);
            if (entry == null)
                throw new ArchiveEntryNotFoundException(entryName, "No such entry!");

            return getInputStream(entry, null);
        } finally {
            readLock().unlock();
        }
    }

    protected InputStream getInputStream(
            final ArchiveEntry entry,
            final ArchiveEntry dstEntry)
    throws IOException {
        assert readLock().isLocked() || writeLock().isLocked();
        assert !hasNewData(entry.getName());
        
        if (entry.isDirectory())
            throw new ArchiveEntryNotFoundException(
                    entry.getName(), "Cannot read directory entry!");
        
        final InputStream in = inArchive.getMetaData().getStream(entry, dstEntry);
        if (in == null) {
            // This entry is actually a newly created archive file which is now
            // accessed by another file which's ArchiveDetector doesn't recognize
            // it as a directory.
            throw new ArchiveEntryNotFoundException(
                    entry.getName(), "Illegal access to archive file!");
        }

        return in;
    }

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
    protected OutputStream getOutputStream(final String entryName)
    throws IOException {
        assert entryName != null;

        class OutputStreamGetter implements IORunnable {
            OutputStream out;

            public void run() throws IOException {
                if (hasNewData(entryName))
                    update();

                final boolean lenient = File.isLenient();
                final ArchiveFileSystem fileSystem = getFileSystem(lenient);

                // Start transaction, creating a new archive entry.
                final ArchiveFileSystem.Delta delta
                        = fileSystem.beginCreateAndLink(entryName, lenient);

                // Get output stream.
                out = getOutputStream(delta.getEntry(), null);

                // Commit the transaction, linking the entry into the virtual
                // archive file system.
                delta.commit();
            }
        }
        final OutputStreamGetter runnable = new OutputStreamGetter();
        runWriteLocked(runnable);

        return runnable.out;
    }

    protected OutputStream getOutputStream(
            final ArchiveEntry entry,
            final ArchiveEntry srcEntry)
    throws IOException {
        assert writeLock().isLocked();
        assert !hasNewData(entry.getName());

        ensureOutArchive();
        return outArchive.getMetaData().getStream(entry, srcEntry);
    }

    protected void fileSystemTouched()
    throws IOException {
        assert writeLock().isLocked();

        ensureOutArchive();
        schedule(true);
    }

    private void ensureOutArchive()
    throws IOException {
        assert writeLock().isLocked();

        if (outArchive != null)
            return;

        java.io.File tmp = outFile;
        if (tmp == null) {
            if (usesNativeTargetFile() && !getTarget().isFile()) {
                tmp = getTarget();
            } else {
                // Use a new temporary file as the output archive file.
                tmp = File.createTempFile(
                        TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
                // We do properly delete our temps, so this is not required.
                // In addition, this would be dangerous as the deletion
                // could happen before our shutdown hook has a chance to
                // process this controller!!!
                //tmp.deleteOnExit();
            }
        }

        initOutArchive(tmp);
        outFile = tmp; // init outFile on success only!
    }

    /**
     * Initializes <code>outArchive</code> with a newly created
     * {@link OutputArchive} for writing <code>outFile</code>.
     * This method will delete <code>outFile</code> if it has successfully
     * opened it for overwriting, but failed to write the archive file header.
     * 
     * 
     * @throws IOException On any I/O related issue with <code>outFile</code>.
     */
    private void initOutArchive(final java.io.File outFile)
    throws IOException {
        assert writeLock().isLocked();
        assert outArchive == null;

        logger.log(Level.FINEST, "initOutArchive.entering", outFile); // NOI18N
        try {
            OutputStream out = new java.io.FileOutputStream(outFile);
            // If we are actually writing to the target file,
            // we want to remember the byte count in the total byte count.
            if (outFile == getTarget())
                out = new CountingOutputStream(out);
            try {
                outArchive = getDriver().createOutputArchive(this, out, inArchive);
                outArchive.setMetaData(new OutputArchiveMetaData(outArchive));
            } catch (Throwable failure) {
                // failure could be a NoClassDefFoundError if target is an RAES
                // encrypted archive file and Bouncycastle's Lightweight
                // Crypto API is not in the classpath.
                // We are just catching all kinds of Throwables to make sure
                // that we always delete the newly created temp file.
                // Finally, we will pass on the catched exception.
                out.close();
                if (!outFile.delete()) {
                    // This could happen in situations where the file system
                    // allows us to open the file for overwriting, then
                    // overwriting failed (e.g. because of a cancelled password
                    // for an RAES encrypted ZIP file) and finally the file
                    // system also denied deleting the corrupted file.
                    // Shit happens!
                    final IOException ioe = new IOException(outFile.getPath()
                            + " (couldn't delete corrupted output file)");
                    ioe.initCause(failure);
                    throw ioe;
                }
                if (failure instanceof IOException)
                    throw (IOException) failure;
                else if (failure instanceof RuntimeException)
                    throw (RuntimeException) failure;
                else if (failure instanceof Error)
                    throw (Error) failure;
                else
                    throw new AssertionError(failure); // cannot happen!
            }
        } catch (IOException failure) {
            assert outArchive == null;
            logger.log(Level.FINEST, "initOutArchive.throwing", failure); // NOI18N
            throw failure;
        } finally {
            logger.log(Level.FINEST, "initOutArchive.finally", outFile); // NOI18N
        }
        
        assert outArchive != null;
    }

    protected boolean hasNewData(String entryName) {
        assert readLock().isLocked() || writeLock().isLocked();

        return outArchive != null && outArchive.getArchiveEntry(entryName) != null;
    }

    protected void update(
            ArchiveException exceptionChain,
            final boolean waitInputStreams,
            final boolean closeInputStreams,
            final boolean waitOutputStreams,
            final boolean closeOutputStreams,
            final boolean umount,
            final boolean reassemble)
    throws ArchiveException {
        assert closeInputStreams || !closeOutputStreams; // closeOutputStreams => closeInputStreams
        assert !umount || reassemble; // umount => reassemble
        assert writeLock().isLocked();
        assert inArchive == null || inFile != null; // input archive implies input file!
        assert outArchive == null || outFile != null; // output archive implies output file!

        logger.log(Level.FINER, "update.entering", // NOI18N
                new Object[] {
                    getPath(),
                    exceptionChain,
                    Boolean.valueOf(waitInputStreams),
                    Boolean.valueOf(closeInputStreams),
                    Boolean.valueOf(waitOutputStreams),
                    Boolean.valueOf(closeOutputStreams),
                    Boolean.valueOf(umount),
                    Boolean.valueOf(reassemble),
        });
        try {
            final ArchiveException oldExceptionChain = exceptionChain;

            // Check output streams first, because closeInputStreams may be
            // true and closeOutputStreams may be false in which case we
            // don't even need to check open input streams if there are
            // some open output streams.
            if (outArchive != null) {
                final OutputArchiveMetaData outMetaData = outArchive.getMetaData();
                final int outStreams = outMetaData.waitAllStreamsByOtherThreads(
                        waitOutputStreams ? 0 : 50);
                if (outStreams > 0) {
                    if (!closeOutputStreams)
                        throw new ArchiveOutputBusyException(
                                exceptionChain, getPath(), outStreams);
                    exceptionChain = new ArchiveOutputBusyWarningException(
                            exceptionChain, getPath(), outStreams);
                }
            }
            if (inArchive != null) {
                final InputArchiveMetaData inMetaData = inArchive.getMetaData();
                final int inStreams = inMetaData.waitAllStreamsByOtherThreads(
                        waitOutputStreams ? 0 : 50);
                if (inStreams > 0) {
                    if (!closeInputStreams)
                        throw new ArchiveInputBusyException(
                                exceptionChain, getPath(), inStreams);
                    exceptionChain = new ArchiveInputBusyWarningException(
                            exceptionChain, getPath(), inStreams);
                }
            }

            try {
                if (isTouched()) {
                    needsReassembly = true;
                    try {
                        exceptionChain = updateOutArchive(exceptionChain);
                        assert fileSystem == null;
                        assert inArchive == null;
                    } finally {
                        assert outArchive == null;
                    }
                    try {
                        if (reassemble) {
                            exceptionChain = reassembleTargetFile(exceptionChain);
                            needsReassembly = false;
                        }
                    } finally {
                        shutdownStep3(umount && !needsReassembly);
                    }
                } else if (reassemble && needsReassembly) {
                    // Nesting this archive file to its enclosing archive file
                    // has been deferred until now.
                    assert outFile == null; // isTouched() otherwise!
                    assert inFile != null; // !needsReassembly otherwise!
                    // Beware: inArchive or fileSystem may be initialized!
                    shutdownStep2(exceptionChain);
                    outFile = inFile;
                    inFile = null;
                    try {
                        exceptionChain = reassembleTargetFile(exceptionChain);
                        needsReassembly = false;
                    } finally {
                        shutdownStep3(umount && !needsReassembly);
                    }
                } else if (umount) {
                    assert reassemble;
                    assert !needsReassembly;
                    shutdownStep2(exceptionChain);
                    shutdownStep3(true);
                } else {
                    // This may happen if File.update() or File.umount() has
                    // been called and no modifications have been applied to
                    // this ArchiveController since its creation or last update.
                    assert outArchive == null;
                }
            } catch (IOException failure) {
                throw new ArchiveException(exceptionChain, failure);
            } finally {
                schedule(needsReassembly);
            }

            if (exceptionChain != oldExceptionChain) {
                logger.log(Level.FINER, "update.warning", exceptionChain); // NOI18N
                throw exceptionChain;
            }
        } catch (ArchiveException failure) {
            logger.log(Level.FINE, "update.throwing", failure); // NOI18N
            throw failure;
        }
        logger.log(Level.FINEST, "update.exiting"); // NOI18N
    }

    protected final int waitAllInputStreamsByOtherThreads(long timeout) {
        return inArchive != null
                ? inArchive.getMetaData().waitAllStreamsByOtherThreads(timeout)
                : 0;
    }

    protected final int waitAllOutputStreamsByOtherThreads(long timeout) {
        return outArchive != null
                ? outArchive.getMetaData().waitAllStreamsByOtherThreads(timeout)
                : 0;
    }

    protected final boolean isTouched() {
        return fileSystem != null && fileSystem.isTouched();
    }

    /**
     * Updates all nodes in the virtual file system to the (temporary) output
     * archive file.
     * <p>
     * <b>This method is intended to be called by <code>update()</code> only!</b>
     * 
     * @param exceptionChain the head of a chain of exceptions created so far.
     * @return If any warning exception condition occurs throughout the course
     *         of this method, an {@link ArchiveWarningException} is created
     *         (but not thrown), prepended to <code>exceptionChain</code> and
     *         finally returned.
     *         If multiple warning exception conditions occur, the prepended
     *         exceptions are ordered by appearance so that the <i>last</i>
     *         exception created is the head of the returned exception chain.
     * @see ArchiveController#update(ArchiveException, boolean, boolean, boolean, boolean, boolean, boolean)
     * @throws ArchiveException If any exception condition occurs throughout
     *         the course of this method, an {@link ArchiveException}
     *         is created, prepended to <code>exceptionChain</code> and finally
     *         thrown unless it's an {@link ArchiveWarningException}.
     */
    private ArchiveException updateOutArchive(
            ArchiveException exceptionChain)
    throws ArchiveException {
        assert writeLock().isLocked();
        assert isTouched();
        //assert inArchive != null; // maybe null if archive file is new!
        assert outArchive != null;
        assert fileSystem != null;

        synchronized (outArchive) {
            // Check if we have written out any entries that have been
            // deleted from the master directory meanwhile and prepare
            // to throw a warning exception.
            final Enumeration e = outArchive.getArchiveEntries();
            while (e.hasMoreElements()) {
                final String name
                        = ((ArchiveEntry) e.nextElement()).getName();
                if (!fileSystem.isFile(name)) { // isFile will just lookup name, not (name + '/').
                    // The entry has been written out already, but also
                    // has been deleted from the master directory meanwhile.
                    // Create a warning exception, but do not yet throw it.
                    exceptionChain = new ArchiveWarningException(
                            exceptionChain,
                            getPath()
                                + ": Couldn't remove archive entry: "
                                + name);
                }
            }
        }

        final long rootTime;
        try {
            try {
                synchronized (outArchive) {
                    try {
                        exceptionChain = shutdownStep1(exceptionChain);

                        ArchiveWarningException inputEntryCorrupted = null;
                        ArchiveWarningException outputEntryCorrupted = null;

                        // Entries are always written in reverse order so that
                        // their containing directories will be written last.
                        // This is in order to support dumb ZIP utilities which are
                        // not sorting the entries before unzipping them, in which
                        // case they would process the directories last and thus
                        // hopefully apply their timestamps.
                        // Note: This doesn't help with WinZIP. WinZIP seems to
                        // ignore directory entries completely.
                        final Enumeration e = fileSystem.getReversedEntries();
                        while (e.hasMoreElements()) {
                            final ArchiveEntry entry = (ArchiveEntry) e.nextElement();
                            final String name = entry.getName();
                            if (hasNewData(name))
                                continue; // we have already written this entry
                            if (entry.isDirectory()) {
                                if (name.equals(ArchiveFileSystem.ROOT))
                                    continue; // never write the root directory
                                if (entry.getTime() < 0)
                                    continue; // never write ghost directories
                                // 'entry' will never be used again, so it is safe
                                // to hand over this entry from the InputArchive
                                // to the OutputArchive.
                                outArchive.storeDirectory(entry);
                            } else if (inArchive != null && inArchive.getArchiveEntry(name) != null) {
                                assert entry == inArchive.getArchiveEntry(name);
                                InputStream in;
                                try {
                                    in = inArchive.getInputStream(entry, entry);
                                } catch (IOException failure) {
                                    if (inputEntryCorrupted == null) {
                                        exceptionChain = inputEntryCorrupted
                                                = new ArchiveWarningException(
                                                    exceptionChain,
                                                    getPath()
                                                        + ": Skipped one or more corrupted archive entries from the input!",
                                                    failure);
                                    }
                                    continue;
                                }
                                try {
                                    // 'entry' will never be used again, so it is
                                    // safe to hand over this entry from the
                                    // InputArchive to the OutputArchive.
                                    final OutputStream out
                                            = outArchive.getOutputStream(
                                                entry, entry);
                                    try {
                                        File.cat(in, out);
                                    } catch (InputIOException failure) {
                                        if (outputEntryCorrupted == null) {
                                            exceptionChain = outputEntryCorrupted
                                                    = new ArchiveWarningException(
                                                        exceptionChain,
                                                        getPath()
                                                            + ": One or more archive entries in the output may be corrupted!",
                                                        failure);
                                        }
                                    } finally {
                                        out.close();
                                    }
                                } finally {
                                    in.close();
                                }
                            } else {
                                // This may happen if the entry is an archive
                                // which has been newly created and not yet been
                                // reassembled into this archive.
                                // Write an empty entry now as a marker in order to
                                // recreate the entry when the file system gets
                                // reloaded from the archive.
                                outArchive.getOutputStream(entry, null).close();
                            }
                        }
                    } finally {
                        // We MUST do cleanup here because (1) any entries in the
                        // filesystem which were successfully written (this is the
                        // normal case) have been modified by the OutputArchive
                        // and thus cannot get used anymore to access the input;
                        // and (2) if there has been any IOException on the
                        // output archive there is no way to recover from it.
                        rootTime = fileSystem.lastModified(ArchiveFileSystem.ROOT);
                        shutdownStep2(exceptionChain);
                    } // finally
                } // synchronized (outArchive)
            } catch (IOException failure) {
                // The output file is corrupted! We must remove it now to
                // prevent it from being reused as the input file.
                // We do this even if the output file is the target file, i.e.
                // the archive file has just been created, because it
                // doesn't make any sense to keep a corrupted archive file:
                // There is no way to recover it and it could spoil any
                // attempts to redo the file operations, because TrueZIP would
                // normaly correctly identify it as a false positive archive
                // file and would not allow to treat it like a directory again.
                boolean deleted = outFile.delete();
                outFile = null;
                assert deleted;
                throw failure;
            }
        } catch (ArchiveException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new ArchiveException(
                    exceptionChain,
                    getPath()
                        + ": Could not update archive file - all changes are lost!",
                    failure);
        } // catch (IOException)
        
        // Set the last modification time of the output archive file
        // to the last modification time of the root directory
        // in the virtual file system, effectively preserving it.
        if (!outFile.setLastModified(rootTime)) {
            exceptionChain = new ArchiveWarningException(
                    exceptionChain,
                    getPath()
                        + ": Couldn't preserve last modification time!");
        }
        
        return exceptionChain;
    }
    
    /**
     * Uses the updated output archive file to reassemble the
     * target archive file, which may be an entry in an enclosing
     * archive file.
     * <p>
     * <b>This method is intended to be called by <code>update()</code> only!</b>
     * 
     * 
     * 
     * @param exceptionChain the head of a chain of exceptions created so far.
     * @return If any warning condition occurs throughout the course of this
     *         method, a <code>ArchiveWarningException</code> is created (but not
     *         thrown), prepended to <code>exceptionChain</code> and finally
     *         returned.
     *         If multiple warning conditions occur,
     *         the prepended exceptions are ordered by appearance so that the
     *         <i>last</i> exception created is the head of the returned
     *         exception chain.
     * @return If any warning exception condition occurs throughout the course
     *         of this method, an {@link ArchiveWarningException} is created
     *         (but not thrown), prepended to <code>exceptionChain</code> and
     *         finally returned.
     *         If multiple warning exception conditions occur, the prepended
     *         exceptions are ordered by appearance so that the <i>last</i>
     *         exception created is the head of the returned exception chain.
     * @throws ArchiveException If any exception condition occurs throughout
     *         the course of this method, an {@link ArchiveException}
     *         is created, prepended to <code>exceptionChain</code> and finally
     *         thrown unless it's an {@link ArchiveWarningException}.
     */
    private ArchiveException reassembleTargetFile(
            ArchiveException exceptionChain)
    throws ArchiveException {
        assert writeLock().isLocked();

        if (usesNativeTargetFile()) {
            // The archive file managed by this object is NOT enclosed in
            // another archive file.
            if (outFile != getTarget()) {
                // The archive file existed before and we have written
                // to a temporary output file.
                // Now copy the temporary output file to the target file.
                try {
                    final OutputStream out = new CountingOutputStream(
                            new java.io.FileOutputStream(getTarget()));
                    final InputStream in;
                    try {
                        in = new java.io.FileInputStream(outFile);
                    } catch (IOException failure) {
                        out.close();
                        throw failure;
                    }
                    File.cp(in , out); // always closes in and out
                } catch (IOException cause) {
                    throw new ArchiveException(
                            exceptionChain,
                            getPath()
                                + " (could not update archive file - all changes are lost)",
                            cause);
                }
                
                // Set the last modification time of the target archive file
                // to the last modification time of the output archive file,
                // which has been set to the last modification time of the root
                // directory during updateOutArchive(...).
                final long time = outFile.lastModified();
                if (time != 0 && !getTarget().setLastModified(time)) {
                    exceptionChain = new ArchiveWarningException(
                            exceptionChain,
                            getPath()
                                + " (couldn't preserve last modification time)");
                }
            }
        } else {
            // The archive file managed by this archive controller IS
            // enclosed in another archive file.
            try {
                wrap(getEnclController(), getEnclEntryName());
            } catch (IOException cause) {
                throw new ArchiveException(
                        exceptionChain,
                        getEnclController().getPath() + "/" + getEnclEntryName()
                            + " (could not update archive entry - all changes are lost)",
                        cause);
            }
        }
        
        return exceptionChain;
    }

    private void wrap(
            final ArchiveController controller,
            final String entryName)
    throws IOException {
        assert writeLock().isLocked();
        assert controller != null;
        assert entryName != null;
        assert File.EMPTY != entryName;

        class Wrapper implements IORunnable {
            public void run() throws IOException {
                // Write the updated output archive file as an entry
                // to its enclosing archive file, preserving the
                // last modification time of the root directory as the last
                // modification time of the entry.
                final InputStream in = new java.io.FileInputStream(outFile);
                try {
                    // We know that the enclosing controller's entry is not a false
                    // positive, so we may safely pass in null as the destination
                    // de.schlichtherle.io.File.
                    cp( outFile, in,
                        null /*new File(controller.target)*/, controller, entryName,
                        true);
                } finally {
                    in.close();
                }
            }
        }
        controller.runWriteLocked(new Wrapper());
    }

    /**
     * Resets the archive controller to its initial state - all changes to the
     * archive file which have not yet been updated get lost!
     * <p>
     * Thereafter, the archive controller will behave as if it has just been
     * created and any subsequent operations on its entries will remount
     * the virtual file system from the archive file again.
     */
    protected void reset() throws IOException {
        assert writeLock().isLocked();

        ArchiveException exceptionChain = shutdownStep1(null);
        shutdownStep2(exceptionChain);
        shutdownStep3(true);
        schedule(false);
        
        if (exceptionChain != null)
            throw exceptionChain;
    }
    
    protected void finalize()
    throws Throwable {
        logger.log(Level.FINEST, "finalize.entering", getPath()); // NOI18N
        // Note: If fileSystem or inArchive are not null, then the controller
        // has been used to perform read-only operations only.
        // If outArchive is null, the controller has been used to perform
        // write operations, but however, but all file system transactions
        // must have failed.
        // Otherwise, the fileSystem would have been marked as touched and
        // we should never be made elegible for finalization!
        // Tactical note: Assertions don't work in a finalizer, so we use
        // logging.
        if (isTouched())
            logger.log(Level.WARNING, "finalize.hasUpdatedEntries", getPath());
        shutdownStep1(null);
        shutdownStep2(null);
        shutdownStep3(true);
        super.finalize();
    }

    /**
     * Closes and disconnects all entry streams of the output and input
     * archive.
     */
    private ArchiveException shutdownStep1(ArchiveException exceptionChain) {
        if (outArchive != null)
            exceptionChain = outArchive.getMetaData().closeAllStreams(
                    exceptionChain);
        if (inArchive != null)
            exceptionChain = inArchive.getMetaData().closeAllStreams(
                    exceptionChain);

        return exceptionChain;
    }
    
    /**
     * Discards the file system and closes the output and input archive.
     */
    private void shutdownStep2(ArchiveException exceptionChain)
    throws ArchiveException {
        final ArchiveException oldExceptionChain = exceptionChain;

        fileSystem = null;

        // The output archive must be closed BEFORE the input archive is
        // closed. This is because the input archive has been presented
        // to output archive as the "source" when it was created and may
        // be using the input archive when its closing to retrieve some
        // meta data information.
        // E.g. Zip32OutputArchive copies the postamble from the
        // Zip32InputArchive when it closes.
        if (outArchive != null) {
            try {
                outArchive.close();
            } catch (IOException failure) {
                exceptionChain = new ArchiveException(exceptionChain, failure);
            } finally {
                outArchive = null;
            }
        }

        if (inArchive != null) {
            try {
                inArchive.close();
            } catch (IOException failure) {
                exceptionChain = new ArchiveException(exceptionChain, failure);
            } finally {
                inArchive = null;
            }
        }
        
        if (exceptionChain != oldExceptionChain)
            throw exceptionChain;
    }
    
    /**
     * Cleans up temporary files.
     *
     * @param umount If this parameter is <code>true</code>,
     *        this method also deletes the temporary output file if it's
     *        not the target archive file (i.e. if the archive file has not been
     *        newly created).
     */
    private void shutdownStep3(final boolean umount) {
        if (inFile != null) {
            if (inFile != getTarget()) {
                boolean deleted = inFile.delete();
                assert deleted;
            }
            inFile = null;
        }

        if (outFile != null) {
            if (umount) {
                if (outFile != getTarget()) {
                    boolean deleted = outFile.delete();
                    assert deleted;
                }
            } else {
                //assert outFile != target; // may have been newly created
                assert outFile.isFile();
                inFile = outFile;
            }
            outFile = null;
        }

        if (umount) {
            needsReassembly = false;
        }
    }

    //
    // Exception classes.
    // These are all instance (inner) classes, not just static member classes.
    //

    private class ArchiveNotFoundException extends FileNotFoundException {
        public String getMessage() {
            return getPath();
        }
    }

    private class ArchiveEntryNotFoundException extends FileNotFoundException {
        private final String entry, msg;

        private ArchiveEntryNotFoundException(String entry, String msg) {
            this.entry = entry;
            this.msg = msg;
        }
        
        public String getMessage() {
            return getPath() + entry + ": " + msg;
        }
    }
}
