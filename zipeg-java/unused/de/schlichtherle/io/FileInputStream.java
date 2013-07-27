/*
 * FileInputStream.java
 *
 * Created on 24. Oktober 2004, 13:06
 */
/*
 * Copyright 2005 Schlichtherle IT Services
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

import java.io.FileDescriptor;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * This class behaves like a
 * {@link java.io.FileInputStream java.io.FileInputStream}, but also supports
 * files which may be located in an archive file.
 * To read an entry file from an archive file, simply create an instance
 * of this class with a pathname that contains the archive file as one
 * of the parent directories.
 * The remainder of the pathname will then be used as the relative path
 * of the entry in the archive file to be read.
 * <p>
 * For example on UNIX like systems using the path
 * <code>/home/user/archive.zip/directory/readme</code> will read
 * the relative path <code>directory/readme</code> in ZIP file
 * <code>/home/user/archive.zip</code>.
 * <p>
 * To prevent a {@link FileBusyException} to be thrown subsequently,
 * please make sure that you always close your streams using the following
 * idiom:
 * <p>
 * <pre>
 *      FileInputStream fis = new FileInputStream(file);
 *      try {
 *          // access fis here
 *      }
 *      finally {
 *          fis.close();
 *      }
 * </pre>
 * <p>
 * This idiom is not specific to this class. You should always use it for
 * any input or output stream, because some platforms will restrict an
 * application from using certain file operations if the file is busy on I/O.
 * E.g. on Windows, an application cannot delete a file that is open for I/O
 * by any other process.
 * <p>
 * <b>Notes:</b>
 * <ul>
 * <li>If you would like to use this class in order to copy files,
 *     please consider using the <code>*copy*</code> methods in the {@link File}
 *     class instead. These offer advanced fail-safety and performance features.
 * <li>This class supports nested archive files. You can use as many nesting
 *     levels as you like, although there is a performance penalty for every
 *     nesting level, as an enclosed archive file first gets extracted from
 *     its enclosing archive file to a temporary file before you can actually
 *     read its entries or even list its file system.
 * <li>Please refer to the documentation of the base class for any undocumented
 *     methods.
 * <li>If you have configured TrueZIP to use a <em>checked</em> driver for ZIP
 *     compatible files (the class
 *     {@link de.schlichtherle.io.archive.zip.CheckedZip32Driver} for example)
 *     and there is a mismatch of the CRC-32 values for the ZIP entry
 *     addressed by this input stream, the <code>close()</code> method
 *     will throw a {@link de.schlichtherle.util.zip.CRC32Exception}.
 *     Other than this, the ZIP entry will be processed normally.
 *     So if just the CRC-32 value for the entry in the ZIP file has been
 *     modified, you can still read its contents.
 * </ul>
 * <p>
 * <b>Warnings:</b>
 * <ul>
 * <li>Whether or not you can read from more than one entry in the same archive
 *     archive file concurrently is now (since TrueZIP 6.0) an implementation
 *     detail of the respective archive driver.
 *     TrueZIP's default ZIP32 and TAR driver family do not limit this.
 *     However, other drivers may limit
 *     <p>
 *     If you try to exceed the number of entry streams allowed to operate
 *     on the same archive file, you will receive a {@link FileBusyException}
 *     by the constructors of this class.
 *     In this case, you must close (or discard and rely on the finalizer
 *     thread) the previously created stream objects or
 *     call {@link File#update()} or {@link File#umount()} to force these
 *     streams to be closed and then recreate this stream object again to
 *     resolve this issue.
 * <li>You cannot read from an archive file if an automatic update is
 *     required but cannot be performed because the application is still
 *     using other <code>FileInputStream</code> or <code>FileOutputStream</code>
 *     objects which haven't been closed or discarded yet.
 *     Again, failure to comply to this restriction will result in a
 *     <code>FileBusyException</code> to be thrown by the constructors of this
 *     class.
 *     Call {@link File#update()} or {@link File#umount()} to force these
 *     other streams to be closed and recreate this stream again to resolve
 *     this issue.
 * <li>
 * This class accesses archive files as external resources.
 * In order to do so it must assume that it has exclusive access to these
 * archive files.
 * If other processes or other classes or even other implementations of
 * this class which have been defined by a different class loader
 * need to access the same archive files concurrently, you must
 * call {@link File#umount()} in order to synchronize the internal state
 * of these archive files to the real file system and release any
 * resources associated with them before the other party can safely access them.
 * Furthermore, after the call to <code>update()</code> or <code>umount()</code>
 * you must not call <em>any of the methods</em> in this class for these
 * archive files or <em>any of their entries</em> while the other
 * party is accessing these archive files.
 * Failure to comply to this rule will result in unpredictable behaviour
 * and may even cause <em>loss of data</em>!
 * </ul>
 *
 * @see FileBusyException
 * @see File#update()
 * @see File#umount()
 * @see java.io.FileInputStream java.io.FileInputStream
 * @see <a href="package-summary.html#package_description">Package Description</a>
 * @author Christian Schlichtherle
 * @version @version@
 */
public class FileInputStream extends FilterInputStream {
    
    private static InputStream createInputStream(final java.io.File file)
            throws FileNotFoundException {
        if (file instanceof File) {
            final File smartFile = (File) file;
            if (smartFile.isArchive()
                    && (smartFile.isDirectory()
                        || (smartFile.exists() && !smartFile.isFile())))
                throw new FileNotFoundException(file.getPath()
                + " (cannot read possibly inaccessible archive file)");
            final String entryName = smartFile.getEnclEntryName();
            if (entryName != null)
                return createInputStream(
                        smartFile.getEnclArchive().getArchiveController(),
                        entryName, smartFile);
        }
        return new java.io.FileInputStream(file);
    }
    
    private static InputStream createInputStream(
            final ArchiveController controller,
            final String entryName,
            final File file)
            throws FileNotFoundException {
        try {
            return controller.getInputStream(entryName);
        } catch (ArchiveController.FalsePositiveDirectoryEntryException failure) {
            return createInputStream(controller.getEnclController(),
                    controller.enclEntryName(entryName), file);
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            final java.io.File delegate = file.getDelegate();
            if (delegate.exists() && !delegate.isDirectory()) // file or special file
                return new java.io.FileInputStream(file);
            else
                throw failure;
        } catch (ArchiveBusyException failure) {
            throw new FileBusyException(failure);
        } catch (FileNotFoundException failure) {
            throw failure;
        } catch (IOException failure) {
            final FileNotFoundException exc
                    = new FileNotFoundException(file.getPath());
            exc.initCause(failure);
            throw exc;
        }
    }
    
    /**
     * Behaves like the super class, but also supports archive entry files.
     *
     * @see java.io.FileInputStream java.io.FileInputStream
     */
    public FileInputStream(String name)
    throws FileNotFoundException {
        super(createInputStream(File.getDefaultArchiveDetector().createFile(name)));
    }
    
    /**
     * Behaves like the super class, but also supports archive entry files.
     *
     * @see java.io.FileInputStream java.io.FileInputStream
     */
    public FileInputStream(java.io.File file)
    throws FileNotFoundException {
        super(createInputStream(file));
    }

    /**
     * @see java.io.FileInputStream java.io.FileInputStream
     * @since TrueZIP 6.4
     */
    public FileInputStream(FileDescriptor fd) {
        super(new java.io.FileInputStream(fd));
    }
   
    public int read(byte b[]) throws IOException {
        return in.read(b, 0, b.length);
    }
}