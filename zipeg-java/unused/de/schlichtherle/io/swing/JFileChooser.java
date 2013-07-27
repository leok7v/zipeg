/*
 * JFileChooser.java
 *
 * Created on 26. Juli 2005, 00:14
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

/**
 * This class extends {@link javax.swing.JFileChooser} in order to allow
 * browsing of ZIP compatible files in a JFileChooser.
 *
 * @author Christian Schlichtherle
 * @version @version@
 */
public class JFileChooser extends javax.swing.JFileChooser {
    
    public JFileChooser() {
        super(FileSystemView.getFileSystemView());
    }
    
    public JFileChooser(ArchiveDetector archiveDetector) {
        super(FileSystemView.getFileSystemView(archiveDetector));
    }

    /**
     * Returns a {@link de.schlichtherle.io.File de.schlichtherle.io.File}
     * instead of {@link java.io.File java.io.File}.
     *
     * @see javax.swing.JFileChooser#getSelectedFile()
     */
    public java.io.File getSelectedFile() {
        java.io.File file = super.getSelectedFile();
        return ((FileSystemView) getFileSystemView()).wrap(file);
    }

    /**
     * Returns an array of
     * {@link de.schlichtherle.io.File de.schlichtherle.io.File}
     * objects instead of {@link java.io.File java.io.File} objects.
     *
     * @see javax.swing.JFileChooser#getSelectedFiles()
     */
    public java.io.File[] getSelectedFiles() {
        java.io.File files[] = super.getSelectedFiles();
        if (files != null) {
            FileSystemView fsv = (FileSystemView) getFileSystemView();
            for (int i = files.length; --i >= 0; ) {
                files[i] = fsv.wrap(files[i]);
                //files[i] = files[i] != null ? new File(files[i]) : null;
            }
        }
        return files;
    }
}
