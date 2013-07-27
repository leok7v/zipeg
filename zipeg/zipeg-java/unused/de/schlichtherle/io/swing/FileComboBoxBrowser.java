/*
 * FileComboBoxBrowser.java
 *
 * Created on 2. August 2006, 23:45
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

package de.schlichtherle.io.swing;

import de.schlichtherle.io.File;
//import de.schlichtherle.key.PromptingKeyManager;
import de.schlichtherle.swing.AbstractComboBoxBrowser;

import java.io.FilenameFilter;
import java.util.Arrays;

import javax.swing.JComboBox;

/**
 * An implementation of {@link AbstractComboBoxBrowser} which completes
 * relative and absolute path names of files.
 * This class uses instances of TrueZIP's custom {@link File} class in
 * order to support the browsing of archive files for auto completion.
 * <p>
 * To use it, do something like this:
 * <pre>
    JComboBox box = new JComboBox();
    new FileComboBoxBrowser(box);
    box.setEditable(true);
 * </pre>
 * 
 * @author Christian Schlichtherle
 * @since TrueZIP 6.2
 * @version @version@
 */
public class FileComboBoxBrowser extends AbstractComboBoxBrowser {

    private File directory;

    /**
     * Creates a new combo box auto completion browser.
     * {@link #setComboBox} must be called in order to use this object.
     */
    public FileComboBoxBrowser() {
    }

    /**
     * Creates a new combo box auto completion browser.
     *
     * @param comboBox The combo box to enable browsing for auto completions.
     *        May be <code>null</code>.
     */
    public FileComboBoxBrowser(final JComboBox comboBox) {
        super(comboBox);
    }

    /**
     * Returns the directory which is used to complete relative path names.
     * This defaults to the current directory; <code>null</code> is never
     * returned.
     */
    public File getDirectory() {
        if (directory == null)
            directory = new File(".");
        return directory;
    }

    /**
     * Returns the directory which is used to complete relative path names.
     *
     * @param directory The directory to use for completion.
     *        If this is <code>null</code>, the directory is reset to the
     *        current directory.
     */
    public void setDirectory(final File directory) {
        this.directory = directory;
    }

    /**
     * Interpretes the specified <code>initials</code> as the initial
     * characters of an absolute or relative path name of a node in the file
     * system and updates the contents of the combo box model with possible
     * completions.
     * The elements in the combo box model are sorted according to their
     * natural comparison order.
     * 
     * @param initials The initial characters of a file or directory path name.
     *
     * @return <code>true</code> if and only if the file system contains a
     *         node with <code>initials</code> as its initial characters and
     *         hence the popup window with the completions should be shown.
     *
     * @throws IllegalStateException If no combo box has been
     *         {@link #setComboBox set}.
     */
    protected boolean update(final String initials) {
        // This is actually a pretty ugly piece of code, but I don't know
        // of any other way to implement this so that a user can use his
        // platform specific file separator (e.g. '\\' on Windows)
        // AND the standard Internet file name separator '/' in a file path
        // name.

        final JComboBox comboBox = getComboBox();
        if (comboBox == null)
            throw new IllegalStateException("No combo box set!");

        // Identify the directory to list, the prefix of the elements we want
        // to find and the base string which prepends the elements we will find.
        File dir = getDirectory();
        final String prefix, base;
        if ("".equals(initials)) {
            prefix = "";
            base = initials;
        } else {
            File node = new File(initials, dir.getArchiveDetector());
            if (node.isAbsolute()) {
                final boolean dirPath = node.getPath().length() < initials.length();
/*
                if (dirPath)
                    PromptingKeyManager.resetCancelledPrompts();
*/
                // The test order is important here because isDirectory() may
                // actually prompt the user for a key if node!
                if (dirPath && node.isDirectory()) {
                    dir = node;
                    prefix = "";
                } else {
                    dir = (File) node.getParentFile();
                    if (dir == null) {
                        dir = node;
                        prefix = "";
                    } else {
                        prefix = node.getName();
                    }
                }
                if (dir.getPath().endsWith(File.separator)) {
                    // dir is the root of a file system.
                    base = initials.substring(0, dir.getPath().length());
                } else {
                    // Otherwise keep the user provided file separator.
                    base = initials.substring(0, dir.getPath().length() + 1);
                }
            } else {
                final File directory = dir;
                node = new File(directory, initials); // inherits archive detector from directory
                final boolean dirPath = node.getPath().length()
                        < (directory.getPath() + File.separator + initials).length();
/*
                if (dirPath)
                    PromptingKeyManager.resetCancelledPrompts();
*/
                // The test order is important here because isDirectory() may
                // actually prompt the user!
                if (dirPath && node.isDirectory()) {
                    dir = node;
                    prefix = "";
                } else {
                    dir = (File) node.getParentFile();
                    prefix = node.getName();
                }
                // Keep the user provided file separator.
                base = initials.substring(0, dir.getPath().length() - directory.getPath().length());
            }
        }

        final FilenameFilter filter;
        if (File.separatorChar != '\\') {
            // Consider case (Unix).
            filter = new FilenameFilter() {
                public boolean accept(java.io.File d, String child) {
                    return child.startsWith(prefix);
                }
            };
        } else {
            // Ignore case (Windows).
            filter = new FilenameFilter() {
                final int pl = prefix.length();

                public boolean accept(java.io.File d, String child) {
                    if (child.length() >= pl)
                        return prefix.equalsIgnoreCase(child.substring(0, pl));
                    else
                        return false;
                }
            };
        }
        final String[] children = dir.list(filter);

        // Update combo box model.
        // Note that the list MUST be cleared and repopulated because its
        // current content does not need to reflect the status of the edited
        // initials.
        comboBox.removeAllItems();
        final int l = children != null ? children.length : 0;
        if (l > 0) {
            Arrays.sort(children);
            for (int i = 0; i < l; i++)
                comboBox.addItem(base + children[i]);
            return true; // show popup
        } else {
            // Leave edited initials als sole content of the list.
            comboBox.addItem(initials);
            return false; // hide popup
        }
    }
}
