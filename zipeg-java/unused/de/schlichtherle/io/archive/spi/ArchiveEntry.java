/*
 * ArchiveEntry.java
 *
 * Created on 26. Februar 2006, 19:08
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

import de.schlichtherle.io.ArchiveEntryMetaData;
import de.schlichtherle.io.util.Path;

import javax.swing.Icon;

/**
 * A simple interface for entries in an archive.
 * Drivers need to implement this interface in order to allow TrueZIP to
 * read and write entries for the supported archive types.
 * Implementations of this class do not need to be thread safe.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public interface ArchiveEntry {
    
    // TODO: Review: Could we refactor File, ArchiveController and
    // ArchiveFileSystem so that the dependencies on the entry name format
    // are removed?!
    /**
     * Returns the entry name.
     * An entry name is a path name where the individual components are
     * separated by a forward slash character (<code>"/"</code>).
     * A component must not be an empty string (<code>""</code>), a dot
     * (<code>"."</code>), or a dot-dot (<code>".."</code>).
     * For example, <code>"foo/bar"</code> would denote a valid path name for
     * a file entry, but <code>"./abc/../foo/./def/./../bar/."</code> would
     * not (although the refer to the same entry).
     * <p>
     * Furthermore, if the entry is a directory, it's name must end with a
     * trailing forward slash. For example <code>"dir/"</code> would denote
     * a valid name for a directory entry, but <code>"dir"</code> would not.
     *
     * @return The path name of the entry within the archive
     *         - never <code>null</code>.
     *
     * @see Path#normalize Path.normalize() for utility methods for
     *      converting path names with empty, dot or dot-dot components to
     *      a "normalized" path name.
     */
    String getName();

    /**
     * Returns <code>true</code> if and only if this entry represents a
     * directory.
     */
    boolean isDirectory();

    /**
     * Returns the (uncompressed) size of the archive entry in bytes,
     * or <code>-1</code> if not specified.
     * This method is not meaningful for directory entries.
     */
    long getSize();

    /**
     * Returns the last modification time of this archive entry since the
     * epoch, or <code>-1</code> if not specified.
     *
     * @see #setTime(long)
     */
    long getTime();

    /**
     * Sets the last modification time of this archive entry.
     *
     * @param time The last modification time of this archive entry in
     *             milliseconds since the epoch.
     *
     * @see #getTime()
     */
    void setTime(long time);

    /**
     * Returns the icon that {@link de.schlichtherle.io.swing.tree.FileTreeCellRenderer}
     * should display for this entry if it is open/expanded in the view.
     * If <code>null</code> is returned, a default icon will be used,
     * depending on the type of this entry and its state in the view.
     */
    Icon getOpenIcon();

    /**
     * Returns the icon that {@link de.schlichtherle.io.swing.FileSystemView}
     * and {@link de.schlichtherle.io.swing.tree.FileTreeCellRenderer} should
     * display for this entry if it is closed/collapsed in the view.
     * If <code>null</code> is returned, a default icon will be used,
     * depending on the type of this entry and its state in the view.
     */
    Icon getClosedIcon();

    /**
     * Returns the meta data for this archive entry.
     * This property is used by TrueZIP's virtual file system class and is
     * transparent to the driver implementation.
     */
    ArchiveEntryMetaData getMetaData();

    /**
     * Sets the meta data for this archive entry.
     * This property is used by TrueZIP's virtual file system class and is
     * transparent to the driver implementation.
     */
    void setMetaData(ArchiveEntryMetaData metaData);
}
