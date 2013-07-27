/*
 * ArchiveEntryMetaData.java
 *
 * Created on 26. Februar 2006, 22:03
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

package de.schlichtherle.io;

import de.schlichtherle.io.archive.Archive;
import de.schlichtherle.io.archive.spi.ArchiveDriver;
import de.schlichtherle.io.archive.spi.ArchiveEntry;

import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

/**
 * <b>This class is <em>not</em> intended for public use!</b>
 * It's only public for technical reasons and may get renamed or entirely
 * disappear without notice.
 * <p>
 * This class annotates an {@link ArchiveEntry} with the fields and methods
 * required to implement the concept of a directory.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class ArchiveEntryMetaData {

    /**
     * If the entry from which this object has been created represents a
     * directory, then this is a valid reference to a set of Strings,
     * representing the children names.
     * Otherwise this field is initialized with <code>null</code>.
     */
    final Set children;

    /**
     * A package private constructor.
     * Used by the factory in this package only.
     */
    ArchiveEntryMetaData(final ArchiveEntry entry) {
        this.children = entry.isDirectory() ? new HashSet() : null;
    }

    /**
     * Returns the names of the members in this directory in a newly
     * created array. The returned array is <em>not</em> sorted.
     * This is the most efficient list method.
     *
     * @throws NullPointerException If the entry from which this object has
     *         been created is not a directory.
     */
    String[] list() {
        final String[] list = new String[children.size()];
        children.toArray(list);

        return list;
    }

    /**
     * This thread local variable returns an {@link ArrayList} which is used
     * as a temporary buffer to implement filtered list methods.
     */
    private static final ThreadLocal threadLocal = new ThreadLocal() {
        protected Object initialValue() {
            return new ArrayList(64);
        }
    };

    /**
     * Returns the names of the members in this directory which are
     * accepted by <code>filenameFilter</code> in a newly created array.
     * <code>dir</code> is used as the directory argument for the
     * <code>filenameFilter</code>. The returned array is <em>not</em> sorted.
     *
     * @param filenameFilter a valid object - must not be <code>null</code>.
     * @param dir the directory represented as a File object.
     *
     * @throws NullPointerException If the entry from which this object has
     *         been created is not a directory.
     */
    String[] list(
            final FilenameFilter filenameFilter,
            final File dir) {
        final List filteredList = (List) threadLocal.get();
        assert filteredList.isEmpty();
        try {
            for (final Iterator i = children.iterator(); i.hasNext(); ) {
                final String child = (String) i.next();
                if (filenameFilter.accept(dir, child))
                    filteredList.add(child);
            }
            final String[] list = new String[filteredList.size()];
            filteredList.toArray(list);

            return list;
        } finally {
            filteredList.clear(); // support garbage collection of zip controllers!
        }
    }

    /**
     * Returns <code>File</code> objects for the members in this directory
     * which are accepted by <code>filenameFilter</code> in a newly created
     * array.
     * <code>dir</code> is used as the directory argument for the
     * <code>filenameFilter</code>. The returned array is <em>not</em> sorted.
     *
     * @param filenameFilter may be <code>null</code> to accept all members.
     * @param dir the directory represented as a File object.
     *
     * @throws NullPointerException If the entry from which this object has
     *         been created is not a directory.
     */
    File[] listFiles(
            FilenameFilter filenameFilter,
            final File dir,
            final FileFactory factory) {
        final List filteredList = (List) threadLocal.get();
        assert filteredList.isEmpty();
        try {
            for (final Iterator i = children.iterator(); i.hasNext(); ) {
                final String child = (String) i.next();
                if (filenameFilter == null
                    || filenameFilter.accept(dir, child))
                    filteredList.add(factory.createFile(dir, child));
            }
            final File[] list = new File[filteredList.size()];
            filteredList.toArray(list);

            return list;
        } finally {
            filteredList.clear(); // support garbage collection of zip controllers!
        }
    }

    /**
     * Returns <code>File</code> objects for the members in this directory
     * which are accepted by <code>filenameFilter</code> in a newly created
     * array.
     * <code>dir</code> is used as the directory argument for the
     * <code>filenameFilter</code>. The returned array is <em>not</em> sorted.
     *
     * @param fileFilter may be <code>null</code> to accept all members.
     * @param dir the directory represented as a File object.
     *
     * @throws NullPointerException If the entry from which this object has
     *         been created is not a directory.
     */
    File[] listFiles(
            final FileFilter fileFilter,
            final File dir,
            final FileFactory factory) {
        final List filteredList = (List) threadLocal.get();
        assert filteredList.isEmpty();
        try {
            for (final Iterator i = children.iterator(); i.hasNext(); ) {
                final String child = (String) i.next();
                final File file = factory.createFile(dir, child);
                if (fileFilter == null || fileFilter.accept(file))
                    filteredList.add(file);
            }
            final File[] list = new File[filteredList.size()];
            filteredList.toArray(list);

            return list;
        } finally {
            filteredList.clear(); // support garbage collection of zip controllers!
        }
    }
}
