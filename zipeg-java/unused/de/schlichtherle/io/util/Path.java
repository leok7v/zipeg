/*
 * Path.java
 *
 * Created on 2. März 2006, 20:12
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

package de.schlichtherle.io.util;

import java.io.File;

/**
 * Utility methods for file path names.
 *
 * @author Christian Schlichtherle
 * @since TrueZIP 6.1 (refactored from {@link Paths})
 * @version @version@
 */
public class Path {

    /**
     * Equivalent to {@link #normalize(String, char)
     * normalize(path, File.separatorChar)}.
     */
    public static final String normalize(
            final String path) {
        return normalize(path, File.separatorChar);
    }

    /**
     * Removes any <code>"."</code> and <code>".."</code> directories and
     * empty directory members from the path name wherever possible.
     * A present trailing separator character is retained.
     * 
     * @param path The path name which is to be normalized.
     * @param separator The path separator to use for this operation.
     *
     * @return <code>path</code> if it was already in normalized form.
     *         Otherwise, a new String with the normalized form of the given
     *         path name.
     *
     * @throws NullPointerException If path is <code>null</code>.
     */
    public static final String normalize(
            final String path,
            final char separator) {
        final String cutPath = cutTrailingSeparator(path, separator);
        final String nPath = normalizeImpl(cutPath, separator);
        if (path != cutPath)
            if (cutPath != nPath)
                return nPath + separator;
            else
                return path;
        else
            return nPath;
    }

    /**
     * Cuts off a trailing separator character of the pathname, unless the
     * pathname contains of only the separator character (i.e. denotes the
     * root directory).
     *
     * @return <code>path</code> if it is a path name without a trailing
     *          separator character or contains the separator character only.
     *          Otherwise, the substring up to the last character is returned.
     *
     * @throws NullPointerException If path is <code>null</code>.
     */
    public final static String cutTrailingSeparator(
            final String path,
            final char separator) {
        final int pathEnd = path.length() - 1;
        if (pathEnd > 0 && path.charAt(pathEnd) == separator)
            return path.substring(0, pathEnd);
        else
            return path;
    }

    private static String normalizeImpl(
            final String path,
            final char separator) {
        final String[] split = split(path, separator);
        final String possiblyDotifiedParent = split[0];
        final String base = split[1]; // TODO: Evaluate: intern()?
        if (possiblyDotifiedParent == null)
            return cutTrailingSeparator(path, separator);

        final String parent = normalizeImpl(possiblyDotifiedParent, separator);

        if (base.length() == 0 || ".".equals(base)) {
            return parent;
        } else if ("..".equals(base)) {
            final String[] splitParent = split(parent, separator);
            final String parentParent;
            final String parentBase = splitParent[1];

            if ("".equals(parentBase)) {
                // parent is a file system root, possibly prefixed
                // (like on Windows).
                return parent;
            } else if (".".equals(parentBase)) {
                assert ".".equals(parent);
                return "..";
            } else if ("..".equals(parentBase)) {
                // Fall through.
            } else if ((parentParent = splitParent[0]) != null) {
                return parentParent; // pop off parent
            } else {
                // parent was the last element remaining in the path.
                return ".";
            }
        } else if (".".equals(parent)) {
            return base;
        }
            
        if (parent != possiblyDotifiedParent) {
            assert !"".equals(parent);
            return parent + separator + base;
        } else {
            return cutTrailingSeparator(path, separator);
        }
    }

    /**
     * Equivalent to {@link #split(String, char)
     * split(path, File.separatorChar)}.
     */
    public static final String[] split(
            final String path) {
        return split(path, File.separatorChar);
    }

    /**
     * Splits a path name into the parent path name and the base name,
     * recognizing platform specific file system roots.
     * 
     * @param path The name of the path which's parent path name and base
     *        name are to be returned.
     * @param separator The path separator to use for this operation.
     *
     * @return An array of at least two strings:
     *         <ol>
     *         <li>Index 0 holds the parent name or <code>null</code> if the
     *             path name does not name a parent. This name compares
     *             equal with {@link java.io.File#getParent()},
     *             except that redundant separators are kept (i.e. empty
     *             path elements between two separators are not removed).</li>
     *         <li>Index 1 holds the base name. This name compares
     *             equal with {@link java.io.File#getName()}.</li>
     *         </ol>
     *
     * @throws NullPointerException If path is <code>null</code>.
     */
    public static String[] split(
            final String path,
            final char separator) {
        int last = path.length() - 1;

        // Calculate prefix length in path.
        int prefix = 0; // normal prefix length
        if (last >= 0) {
            if (path.charAt(0) == separator) {
                prefix++;
                if (separator == '\\' && last >= 1 && path.charAt(1) == separator)
                    prefix++; // this path is a UNC on the Windows platform
            } else if (last >= 1 && path.charAt(1) == ':') {
                final char drive = path.charAt(0);
                if ('A' <= drive && drive <= 'Z'
                        || 'a' <= drive && drive <= 'z') { // US-ASCII letters only
                    // path is prefixed with drive, e.g. "C:\\Programs".
                    prefix = 2;
                    if (last >= prefix && path.charAt(2) == '\\')
                        prefix++;
                }
            }
        }

        // Now look for the separator, ignoring optional trailing separator.
        int i = -1;
        if (prefix <= last) {
            if (path.charAt(last) == separator) {
                last--;
            }
            i = path.lastIndexOf(separator, last);
            if (i < prefix)
                i = -1;
        }

        // Finally split according to our findings.
        final String[] split = new String[2];
        last++; //  convert last index into length again
        if (i != -1) { // found separator after the prefix?
            split[0] = path.substring(0, i);     // exclude separator
            split[1] = path.substring(i + 1, last);
        } else { // no separator
            if (0 < prefix && prefix < last) // prefix exists and we have more?
                split[0] = path.substring(0, prefix); // prefix is parent
            else
                split[0] = null;                      // no parent
            split[1] = path.substring(prefix, last);
        }

        return split;
    }
    
    /** You cannot instantiate this class. */
    protected Path() {
    }
}
