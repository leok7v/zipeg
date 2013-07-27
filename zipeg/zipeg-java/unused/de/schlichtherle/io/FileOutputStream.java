/*
 * FileOutputStream.java
 *
 * Created on 23. Oktober 2004, 01:08
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
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * This class behaves like a
 * {@link java.io.FileOutputStream java.io.FileOutputStream},
 * but also supports files which may be located in an archive file.
 * To write a file into an archive file, simply create an instance of
 * this class with a pathname that contains the archive file as one
 * of the parent directories.
 * The remainder of the pathname will then be used as the relative path
 * of the entry in the archive file to be created or overwritten.
 * <p>
 * For example on UNIX like systems using the pathname
 * <tt>/home/user/archive.zip/directory/readme</tt> will create or overwrite
 * the relative path <tt>directory/readme</tt> in the ZIP file
 * <tt>/home/user/archive.zip</tt>.
 * <p>
 * To prevent an excepion to be thrown by this constructor,
 * please make sure that you always close your streams using the following
 * idiom:
 * <p>
 * <pre>
 *      FileOutputStream fos = new FileOutputStream(file);
 *      try {
 *          // access fos here
 *      }
 *      finally {
 *          fos.close();
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
 *     please consider using the <tt>*copy*</tt> methods in the {@link File}
 *     class instead. These offer advanced fail-safety and performance features.
 * <li>If {@link File#isLenient()} returns <tt>true</tt> (which is the default)
 *     this class will create any missing parent directories and archive
 *     files up to the outermost enclosing archive file in its path.
 *     Parent directories which are not located in a archive file
 *     and thus are existing in the real file system instead must already
 *     exist, however!
 * <li>Please refer to the documentation of the base class for any undocumented
 *     methods.
 * <li>When using characters in file names which have no representation in
 *     the character set encoding of an archive file, then the constructors
 *     of this class will fail gracefully with an IOException.
 *     This is to protect applications from creating archive entries which
 *     cannot get encoded and decoded again correctly. An examples is the
 *     Euro sign which does not have a representation in the IBM437 character
 *     set used by ordinary ZIP files.
 * <li>All instances of this class are immutable.
 * </ul>
 * <p>
 * <b>Warnings:</b>
 * <ul>
 * <li>Whether or not you can write to more than one entry in the same archive
 *     archive file concurrently is now (since TrueZIP 6.0) an implementation
 *     detail of the respective archive driver.
 *     For example, with TrueZIP's default ZIP32 driver family, you can write
 *     at most one entry in the same archive concurrently, whereas TrueZIP's
 *     default TAR driver family does not limit this.
 *     <p>
 *     If you try to exceed the number of entry streams allowed to operate
 *     on the same archive file, you will receive a {@link FileBusyException}
 *     by the constructors of this class.
 *     In this case, you must close (or discard and rely on the finalizer
 *     thread) the previously created stream objects or
 *     call {@link File#update()} or {@link File#umount()} to force these
 *     streams to be closed and then recreate this stream object again to
 *     resolve this issue.
 * <li>You cannot write to an archive file if an automatic update is
 *     required but cannot be performed because the application is still
 *     using other <tt>FileInputStream</tt> or <tt>FileOutputStream</tt>
 *     objects which haven't been closed or discarded yet.
 *     Again, failure to comply to this restriction will result in a
 *     <tt>FileBusyException</tt> to be thrown by the constructors of this
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
 * and may even <em>cause loss of data</em>!
 * </ul>
 *
 * @see FileBusyException
 * @see File#update()
 * @see File#umount()
 * @see java.io.FileOutputStream java.io.FileOutputStream
 * @see <a href="package-summary.html#package_description">Package Description</a>
 * @author Christian Schlichtherle
 * @version @version@
 */
public class FileOutputStream extends FilterOutputStream {

    private static OutputStream createOutputStream(
            final java.io.File file,
            final boolean append)
            throws FileNotFoundException {
        if (file instanceof File) {
            final File smartFile = (File) file;
            if (smartFile.isArchive()
                    && (smartFile.isDirectory()
                        || (smartFile.exists() && !smartFile.isFile())))
                throw new FileNotFoundException(file.getPath()
                + " (cannot overwrite possibly inaccessible archive file)");
            final String entryName = smartFile.getEnclEntryName();
            if (entryName != null) {
                return createOutputStream(
                        smartFile.getEnclArchive().getArchiveController(),
                        entryName, smartFile, append);
            }
        }
        return new java.io.FileOutputStream(file, append);
    }

    private static OutputStream createOutputStream(
            final ArchiveController controller,
            final String entryName,
            final File file,
            final boolean append)
            throws FileNotFoundException {
        try {
            synchronized (controller) {
                // Order is important here because of side effects in the file
                // system!
                final InputStream in;
                if (append && controller.isFile(entryName))
                    in = controller.getInputStream(entryName);
                else
                    in = null;
                final OutputStream out;
                try {
                    out = controller.getOutputStream(entryName);
                    if (in != null)
                        File.cat(in, out);
                } finally {
                    if (in != null)
                        in.close();
                }
                return out;
            }
        } catch (ArchiveController.FalsePositiveDirectoryEntryException failure) {
            return createOutputStream(controller.getEnclController(),
                    controller.enclEntryName(entryName), file, append);
        } catch (ArchiveController.FalsePositiveNativeException failure) {
            final java.io.File delegate = file.getDelegate();
            final java.io.File parent = delegate.getParentFile();
            if (parent.isDirectory()) // parent is always != null in this context!
                return new java.io.FileOutputStream(file);
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
     * @see java.io.FileOutputStream java.io.FileOutputStream
     */
    public FileOutputStream(String name)
    throws FileNotFoundException {
        super(createOutputStream(
                File.getDefaultArchiveDetector().createFile(name), false));
    }

    /**
     * Behaves like the super class, but also supports archive entry files.
     *
     * @see java.io.FileOutputStream java.io.FileOutputStream
     */
    public FileOutputStream(String name, boolean append)
    throws FileNotFoundException {
        super(createOutputStream(
                File.getDefaultArchiveDetector().createFile(name), append));
    }

    /**
     * Behaves like the super class, but also supports archive entry files.
     *
     * @see java.io.FileOutputStream java.io.FileOutputStream
     */
    public FileOutputStream(java.io.File file)
    throws FileNotFoundException {
        super(createOutputStream(file, false));
    }

    /**
     * Behaves like the super class, but also supports archive entry files.
     *
     * @see java.io.FileOutputStream java.io.FileOutputStream
     */
    public FileOutputStream(java.io.File file, boolean append)
    throws FileNotFoundException {
        super(createOutputStream(file, append));
    }

    /**
     * @see java.io.FileOutputStream java.io.FileOutputStream
     * @since TrueZIP 6.4
     */
    public FileOutputStream(FileDescriptor fd) {
        super(new java.io.FileOutputStream(fd));
    }

    public void write(byte[] buf, int off, int len) throws IOException {
        out.write(buf, off, len);
    }
}