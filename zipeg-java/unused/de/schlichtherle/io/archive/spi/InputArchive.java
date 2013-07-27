/*
 * InputArchive.java
 *
 * Created on 27. Februar 2006, 03:19
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

import de.schlichtherle.io.InputArchiveMetaData;
import de.schlichtherle.io.archive.spi.InputArchiveBusyException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;

/**
 * Defines the interface used to read entries from an archive file.
 * <p>
 * In general, implementations of this class do not need to be thread safe.
 * However, the streams produced by {@link #getInputStream} must be thread
 * safe.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public interface InputArchive {

    /**
     * Returns the number of {@link ArchiveEntry} instances in this archive.
     */
    int getNumArchiveEntries();

    /**
     * Returns an enumeration of the {@link ArchiveEntry} instances in this
     * archive.
     */
    Enumeration getArchiveEntries();

    /**
     * Returns the {@link ArchiveEntry} for the given pathname or
     * <code>null</code> if no entry with this pathname exists.
     * 
     * @param name The name of the <code>ArchiveEntry</code>.
     */
    ArchiveEntry getArchiveEntry(String name);

    /**
     * Returns a <em>thread safe</em> <code>InputStream</code> for reading the
     * contents of the given entry.
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
     * @param entry The archive entry to read. This is <em>never</em>
     *        <code>null</code> and safe to be casted to the archive entry
     *        type actually returned by the {@link #getArchiveEntry} method.
     * @param dstEntry If not <code>null</code>, this identifies the entry
     *        to which TrueZIP is actually copying data to and should be
     *        used to implement the Direct Data Copying (DDC) feature.
     *        Note that there is no guarantee on the runtime type of this
     *        object; it may have been created by other drivers.
     *        <p>
     *        For example, the {@link de.schlichtherle.io.archive.zip.Zip32Driver}
     *        uses this to determine if data should be provided in its deflated
     *        form if the destination entry is another
     *        {@link de.schlichtherle.io.archive.zip.Zip32Entry}.
     *
     * @return A <em>thread safe</em> {@link InputStream} to read the entry
     *         data from or <code>null</code> if the entry does not exist.
     *
     * @throws InputArchiveBusyException If the archive is currently busy
     *         on input for another entry.
     *         This exception is guaranteed to be recoverable, meaning it
     *         should be possible to read the same entry again as soon as
     *         the archive is not hasOpenStreams on input anymore.
     * @throws FileNotFoundException On any other exceptional condition which
     *         is guaranteed to be recoverable.
     * @throws IOException On any other exceptional condition which is most
     *         probably not recoverable.
     */
    InputStream getInputStream(ArchiveEntry entry, ArchiveEntry dstEntry)
    throws InputArchiveBusyException, FileNotFoundException, IOException;

    /**
     * Closes this input archive and releases any system resources
     * associated with it.
     * 
     * @throws IOException On any I/O related issue.
     */
    void close() throws IOException;

    /**
     * Returns the meta data for this input archive.
     * This property is used by TrueZIP's virtual file system class and is
     * transparent to the driver implementation.
     */
    InputArchiveMetaData getMetaData();

    /**
     * Sets the meta data for this input archive.
     * This property is used by TrueZIP's virtual file system class and is
     * transparent to the driver implementation.
     */
    void setMetaData(InputArchiveMetaData metaData);
}
