/*
 * FileFactory.java
 *
 * Created on 25. Februar 2006, 18:55
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

import java.io.FileNotFoundException;
import java.net.URI;

/**
 * An undocumented factory interface which creates {@link File}s,
 * {@link FileInputStream}s and {@link FileOutputStream}s
 * - it is not intended for public use!
 * You should not implement or use this interface directly
 * - implement {@link ArchiveDetector} instead.
 * This interface is solely used to hide the existence of
 * {@link ArchiveDetector}s from some methods in {@link File}.
 *
 * @see ArchiveDetector
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public interface FileFactory {

    File createFile(java.io.File blueprint);

    File createFile(java.io.File delegate, File innerArchive);

    File createFile(File blueprint, java.io.File delegate, File enclArchive);

    File createFile(String pathName);

    File createFile(String parent, String child);

    File createFile(java.io.File parent, String child);

    File createFile(URI uri);

    FileInputStream createFileInputStream(java.io.File file)
    throws FileNotFoundException;

    FileOutputStream createFileOutputStream(java.io.File file, boolean append)
    throws FileNotFoundException;
}
