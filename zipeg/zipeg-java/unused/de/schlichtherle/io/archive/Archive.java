/*
 * Archive.java
 *
 * Created on 2. März 2006, 00:47
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

package de.schlichtherle.io.archive;

import de.schlichtherle.io.archive.spi.ArchiveDriver;

/**
 * Describes some properties of an archive.
 * A single instance of this interface is created for every
 * canonical path name representation of an archive file.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public interface Archive {

    /**
     * Returns the <em>canonical</em> path name of the archive file.
     * For various reasons, implementations must not assume that the file
     * identified by the returned path actually exists in the native file
     * system.
     * However, the path name may be used to determine some archive
     * specific parameters, such as passwords or similar.
     *
     * @return A valid reference to a {@link String} object
     *         - never <code>null</code>.
     */
    String getPath();
    
    /**
     * Returns the driver instance which is used for this archive.
     *
     * @return A valid reference to an {@link ArchiveDriver} object
     *         - never <code>null</code>.
     */
    //ArchiveDriver getDriver();
}
