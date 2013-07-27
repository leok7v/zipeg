/*
 * OutputArchive.java
 *
 * Created on 27. Februar 2006, 00:57
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

import de.schlichtherle.io.OutputArchiveMetaData;
import de.schlichtherle.io.archive.spi.InputArchiveBusyException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

/**
 * Defines the interface used to write entries to an archive file.
 * <p>
 * In general, implementations of this class do not need to be thread safe.
 * However, the streams produced by {@link #getOutputStream} must be thread
 * safe.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public interface OutputArchive {

    /**
     * Returns the number of {@link ArchiveEntry} instances in this archive.
     * <p>
     * This method may be called before the archive is closed and must also
     * reflect entries which have not yet been completed.
     */
    int getNumArchiveEntries();

    /**
     * Returns an enumeration of the {@link ArchiveEntry} instances in this
     * archive (i.e. written so far).
     * <p>
     * This method may be called before the archive is closed and must also
     * reflect entries which have not yet been completed.
     */
    Enumeration getArchiveEntries();

    /**
     * Returns the {@link ArchiveEntry} for the given name or
     * <code>null</code> if no entry with this path name has been written
     * or just started to be written.
     * <p>
     * This method may be called before the archive is closed and must also
     * reflect entries which have not yet been completed.
     * 
     * @param name The name of the <code>ArchiveEntry</code>.
     */
    ArchiveEntry getArchiveEntry(String name);

    /**
     * Returns a <em>thread safe</em> <code>OutputStream</code> for writing
     * the contents of the given entry.
     * <p>
     * Note that the returned stream must have "limited support for
     * multi-threading" and is guaranteed to be closed before the
     * {@link #close()} method of this archive is called!
     * "Limited support for multi-threading" means that multiple streams may
     * be requested using this method and the returned streams may be used
     * by different threads to operate on this archive concurrently.
     * However, whether a returned stream may be safely shared by multiple
     * threads is an implementation detail.
     * TrueZIP provides no guarantee on that to its clients and it is
     * recommended better not to implement such a feature for performance
     * reasons.
     * 
     * @param entry The archive entry to write. This is <em>never</em>
     *        <code>null</code> and safe to be casted to the archive entry
     *        type actually created by the
     *        {@link ArchiveDriver#createArchiveEntry} method.
     * @param srcEntry If not <code>null</code>, this identifies the entry
     *        from which TrueZIP is actually copying data from and should be
     *        used to implement the Direct Data Copying (DDC) feature.
     *        Note that there is no guarantee on the runtime type of this
     *        object; it may have been created by other drivers.
     *        Furthermore, this <em>not</em> exclusively used for archive
     *        copies, so you should <em>not</em> simply copy all properties
     *        of the source entry to the entry (see
     *        {@link ArchiveDriver#createArchiveEntry(Archive, String, ArchiveEntry)}
     *        for comparison).
     *        <p>
     *        For example, the {@link de.schlichtherle.io.archive.zip.Zip32Driver}
     *        uses this to copy the already deflated data if the source entry
     *        is another {@link de.schlichtherle.io.archive.zip.Zip32Entry}.
     *        As another example, the {@link de.schlichtherle.io.archive.tar.TarDriver}
     *        uses this to determine the size of the input file, thereby
     *        removing the need to create (yet another) temporary file.
     *
     * @return A <em>thread safe</em> {@link OutputStream} to write the entry
     *         data to - never <code>null</code>.
     *
     * @throws OutputArchiveBusyExceptionn If the archive is currently busy
     *         on output for another entry.
     *         This exception is guaranteed to be recoverable, meaning it
     *         should be possible to write the same entry again as soon as
     *         the archive is not hasOpenStreams on output anymore.
     * @throws FileNotFoundException On any other exceptional condition which
     *         is guaranteed to be recoverable.
     * @throws IOException On any other exceptional condition which is most
     *         probably not recoverable.
     */
    OutputStream getOutputStream(
            ArchiveEntry entry,
            ArchiveEntry srcEntry)
    throws InputArchiveBusyException, FileNotFoundException, IOException;

    /**
     * Writes the given <code>entry</code> as a directory enry.
     */
    void storeDirectory(ArchiveEntry entry) throws IOException;
    
    /**
     * Closes this output archive and releases any system resources
     * associated with it.
     * 
     * @throws IOException On any I/O related issue.
     */
    void close() throws IOException;

    /**
     * Returns the meta data for this output archive.
     * This property is used by TrueZIP's virtual file system class and is
     * transparent to the driver implementation.
     */
    OutputArchiveMetaData getMetaData();

    /**
     * Sets the meta data for this output archive.
     * This property is used by TrueZIP's virtual file system class and is
     * transparent to the driver implementation.
     */
    void setMetaData(OutputArchiveMetaData metaData);
}
