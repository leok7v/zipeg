/*
 * FileConstants.java
 *
 * Created on 12. März 2006, 11:06
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

package de.schlichtherle.io;

/**
 * A package private interface with constants common to some (package private)
 * classes in this package.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
interface FileConstants {

    /**
     * Denotes the empty archive entry name.
     * This name is used for the <code>innerEntryName</code> field if a
     * <code>File</code> object denotes an archive file itself.
     * This constant is primarily used for identity comparison with archive
     * entry names in order to detect archive files.
     * Note that this is not really required as string constants are interned
     * by the JVM, but this is "clean style".
     */
    String EMPTY = "";

    /**
     * The file name separator in archive entry names as a string.
     */
    String ENTRY_SEPARATOR = "/";

    /**
     * The file name separator in archive entry names as a character.
     */
    char ENTRY_SEPARATOR_CHAR = '/';

}
