/*
 * FileSystemView.java
 *
 * Created on 28. November 2004, 13:26
 */
/*
 * Copyright 2005-2006 Schlichtherle IT Services
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

import de.schlichtherle.io.ArchiveDetector;
import de.schlichtherle.io.archive.spi.ArchiveDriver;

import java.io.FileFilter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * This is the custom filesystem view required to browse archive files
 * like directories with a JFileChooser.
 * It is also used by
 * {@link de.schlichtherle.io.swing.tree.FileTreeCellRenderer}
 * to render files and directories in a
 * {@link de.schlichtherle.io.swing.JFileTree}.
 *
 * @author  Christian Schlichtherle
 * @version @version@
 */
//
// Unfortunately this is a pretty ugly piece of code.
// The reason for this is the completely broken design of the genuine
// JFileChooser, FileSystemView, FileView, ShellFolder and BasicFileChooserUI
// classes.
// The FileSystemView uses a lot of "instanceof" runtime type detections
// in conjunction with Sun's proprietory (and platform dependent) ShellFolder
// class, which subclasses java.io.File.
// Other classes like BasicFileChooserUI also rely on the use of the
// ShellFolder class, which they really shouldn't.
// The use of the ShellFolder class is also the sole reason for the existence
// of the file delegate property in de.schlichtherle.io.File.
// For many methods in this class, we need to pass in the delegate to the
// superclass implementation in order for the JFileChooser to work as expected.
//
// Dear Sun: Please COMPLETELY redesign the JFileChooser, FileSystemView,
// FileView and ShellFolder classes. A little bit of fixing will not do the
// job!
// My number one guideline would be to define clear responsibilities for
// each of the redesigned classes: Most importantly, all (meta) properties of
// a file (like its name, icon, description, etc.) should be clearly located
// in ONE class and whoever uses this should rely on inheritance rather than
// instanceof conditionals.
// Finally, please put your new design to test with browsing a virtual file
// system (like TrueZIP provides) - the current JFileChooser is just not able
// to do this right.
//
public class FileSystemView extends javax.swing.filechooser.FileSystemView {

    private static final String CLASS_NAME
            = "de/schlichtherle/io/swing/FileSystemView".replace('/', '.'); // beware of code obfuscation!
    private static final ResourceBundle resources
            = ResourceBundle.getBundle(CLASS_NAME);

    public static javax.swing.filechooser.FileSystemView getFileSystemView() {
        return getFileSystemView(null);
    }

    public static javax.swing.filechooser.FileSystemView getFileSystemView(
            ArchiveDetector archiveDetector) {
        return new FileSystemView(
            javax.swing.filechooser.FileSystemView.getFileSystemView(),
            archiveDetector);
    }
    
    /** May not be null. */
    private javax.swing.filechooser.FileSystemView delegate;

    /** Maybe null - uses default then. **/
    private ArchiveDetector archiveDetector;

    private FileSystemView(
            javax.swing.filechooser.FileSystemView delegate,
            ArchiveDetector archiveDetector) {
        this.delegate = delegate;
        this.archiveDetector = archiveDetector;
    }

    public javax.swing.filechooser.FileSystemView getDelegate() {
        return delegate;
    }

    public void setDelegate(javax.swing.filechooser.FileSystemView delegate) {
        if (delegate == null)
            throw new NullPointerException();
        if (delegate == this)
            throw new IllegalArgumentException();
        this.delegate = delegate;
    }

    /**
     * Returns a valid archive detector to use with this class.
     * If no archive detector has been explicitly set for this file system
     * view or the archive detector has been set to <code>null</code>,
     * then {@link de.schlichtherle.io.File#getDefaultArchiveDetector} is
     * returned.
     */
    public ArchiveDetector getArchiveDetector() {
        final ArchiveDetector archiveDetector = this.archiveDetector;
        if (archiveDetector != null)
            return archiveDetector;
        else
            return de.schlichtherle.io.File.getDefaultArchiveDetector();
    }

    /**
     * Sets the archive detector to use within this class.
     *
     * @param archiveDetector The archive detector to use.
     *        May be <code>null</code> to indicate that
     *        {@link de.schlichtherle.io.File#getDefaultArchiveDetector}
     *        should be used.
     */
    public void setArchiveDetector(ArchiveDetector archiveDetector) {
        this.archiveDetector = archiveDetector;
    }

    /**
     * Wraps the given file in a ZIP enabled file.
     */
    protected de.schlichtherle.io.File wrap(final java.io.File file) {
        if (file == null)
            return null;
        else if (file instanceof de.schlichtherle.io.File)
            return (de.schlichtherle.io.File) file;
        else
            return getArchiveDetector().createFile(file);
    }

    /**
     * Unwraps the delegate of a possibly ZIP enabled file.
     */
    protected java.io.File unwrap(final java.io.File file) {
        if (file instanceof de.schlichtherle.io.File)
            return ((de.schlichtherle.io.File) file).getDelegate();
        else
            return file;
    }

    /**
     * Creates a ZIP enabled file where necessary only,
     * otherwise the blueprint is simply returned.
     */
    public java.io.File createFileObject(final java.io.File file) {
        if (file == null)
            return null;
        if (!isFileSystem(file) || isFileSystemRoot(file) || isRoot(file)
                || isComputerNode(file) || isDrive(file) || isFloppyDrive(file)
                || getDefaultDirectory().equals(unwrap(file)))
            return unwrap(file);
        else
            return wrap(file);
    }

    //
    // Overridden or implemented methods from base class.
    //
    
    /**
     * Creates a ZIP enabled file where necessary only,
     * otherwise the file system view delegate is used to create the file.
     */
    public java.io.File createFileObject(final String str) {
        return createFileObject(delegate.createFileObject(str));
    }

    /**
     * Creates a ZIP enabled file where necessary only,
     * otherwise the file system view delegate is used to create the file.
     */
    public java.io.File createFileObject(
            final java.io.File dir,
            final String str) {
        return createFileObject(delegate.createFileObject(dir, str));
    }

    /*protected java.io.File createFileSystemRoot(java.io.File file) {
        // As an exception to the rule, we will not delegate this call as this
        // method has protected access.
        // Instead, we will delegate it to our superclass and wrap a file
        // object around it.
        return super.createFileSystemRoot(unwrapFile(file));
    }*/

    public java.io.File createNewFolder(final java.io.File containingDir)
    throws IOException {
        final de.schlichtherle.io.File archiveEnabledDir = wrap(containingDir);
        if (archiveEnabledDir.isArchive() || archiveEnabledDir.isEntry()) {
            de.schlichtherle.io.File newFolder = getArchiveDetector().createFile(
                    archiveEnabledDir,
                    UIManager.getString(java.io.File.separatorChar == '\\'
                            ? "FileChooser.win32.newFolder"
                            : "FileChooser.other.newFolder"));

            for (int i = 2; !newFolder.mkdirs(); i++) {
                if (i > 100)
                    throw new IOException(archiveEnabledDir + ": Could not create new directory entry!");
                newFolder = getArchiveDetector().createFile(
                        archiveEnabledDir,
                        MessageFormat.format(
                            UIManager.getString(java.io.File.separatorChar == '\\'
                                ? "FileChooser.win32.newFolder.subsequent"
                                : "FileChooser.other.newFolder.subsequent"),
                            new Object[] { new Integer(i) }));
            }
            
            return newFolder;
        } else {
            return createFileObject(delegate.createNewFolder(unwrap(containingDir)));
        }
    }

    public java.io.File getChild(java.io.File parent, String child) {
        final de.schlichtherle.io.File archiveEnabledParent = wrap(parent);
        if (archiveEnabledParent.isArchive() || archiveEnabledParent.isEntry()) {
            return createFileObject(delegate.getChild(archiveEnabledParent, child));
        } else {
            return createFileObject(delegate.getChild(unwrap(parent), child));
        }
    }

    public java.io.File getDefaultDirectory() {
        return delegate.getDefaultDirectory();
    }

    public java.io.File[] getFiles(
            final java.io.File dir,
            final boolean useFileHiding) {
        final de.schlichtherle.io.File archiveEnabledDir = wrap(dir);
        if (archiveEnabledDir.isArchive() || archiveEnabledDir.isEntry()) {
            // dir is a ZIP file or an entry in a ZIP file.
            return archiveEnabledDir.listFiles(new FileFilter() {
                public boolean accept(java.io.File file) {
                    return !useFileHiding || !isHiddenFile(file);
                }
            });
        } else {
            final java.io.File files[] = delegate.getFiles(unwrap(dir), useFileHiding);
            for (int i = files.length; --i >= 0; )
                files[i] = createFileObject(files[i]);

            return files;
        }
    }
    
    public java.io.File getHomeDirectory() {
        return delegate.getHomeDirectory();
    }

    public java.io.File[] getRoots() {
        return delegate.getRoots();
    }

    public java.io.File getParentDirectory(java.io.File file) {
        final de.schlichtherle.io.File archiveEnabledFile = wrap(file);
        if (archiveEnabledFile.isEntry())
            return createFileObject(archiveEnabledFile.getParent());
        else
            return createFileObject(delegate.getParentDirectory(unwrap(file)));
    }

    public String getSystemDisplayName(java.io.File file) {
        final de.schlichtherle.io.File archiveEnabledFile = wrap(file);
        if (archiveEnabledFile.isArchive() || archiveEnabledFile.isEntry())
            return archiveEnabledFile.getName();
        else
            return delegate.getSystemDisplayName(unwrap(file));
    }

    /**
     * Other than its super class, this method returns <code>null</code>
     * if the file does not actually exist.
     */
    public Icon getSystemIcon(java.io.File file) {
        final de.schlichtherle.io.File archiveEnabledFile = wrap(file);
        final Icon icon = archiveEnabledFile.getClosedIcon();
        if (icon != null)
            return icon;
        if (archiveEnabledFile.isEntry()) {
            if (archiveEnabledFile.isDirectory())
                return UIManager.getIcon("FileView.directoryIcon");
            else if (archiveEnabledFile.exists())
                return UIManager.getIcon("FileView.fileIcon");
            else
                return null;
        } else if (file.exists()) {
            return delegate.getSystemIcon(unwrap(file));
        } else {
            return null;
        }
    }

    public String getSystemTypeDescription(java.io.File file) {
        final de.schlichtherle.io.File archiveEnabledFile = wrap(file);
        if (archiveEnabledFile.isArchive() && archiveEnabledFile.isDirectory())
            return resources.getString("zipFile");
        else if (archiveEnabledFile.isEntry())
            if (archiveEnabledFile.isDirectory())
                return resources.getString("zipDirectoryEntry");
            else
                return resources.getString("zipFileEntry");
        else
            return delegate.getSystemTypeDescription(unwrap(file));
    }

    public boolean isComputerNode(java.io.File file) {
        return delegate.isComputerNode(unwrap(file));
    }

    public boolean isDrive(java.io.File file) {
        return delegate.isDrive(unwrap(file));
    }

    public boolean isFileSystem(java.io.File file) {
        return delegate.isFileSystem(unwrap(file));
    }

    public boolean isFileSystemRoot(java.io.File file) {
        return delegate.isFileSystemRoot(unwrap(file));
    }

    public boolean isFloppyDrive(java.io.File file) {
        return delegate.isFloppyDrive(unwrap(file));
    }

    public boolean isHiddenFile(java.io.File file) {
        return delegate.isHiddenFile(unwrap(file));
    }

    public boolean isParent(java.io.File folder, java.io.File file) {
        return delegate.isParent(wrap(folder), wrap(file))
            || delegate.isParent(unwrap(folder), unwrap(file));
    }

    public boolean isRoot(java.io.File file) {
        return delegate.isRoot(unwrap(file));
    }

    public Boolean isTraversable(java.io.File file) {
        final Boolean wft = delegate.isTraversable(wrap(file));
        if (Boolean.TRUE.equals(wft))
            return Boolean.TRUE;
        final Boolean uft = delegate.isTraversable(unwrap(file));
        if (Boolean.TRUE.equals(uft))
            return Boolean.TRUE;
        if (wft != null || uft != null)
            return Boolean.FALSE;
        else
            return null;
    }
}
