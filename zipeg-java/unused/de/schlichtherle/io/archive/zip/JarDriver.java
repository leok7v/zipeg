/*
 * JarDriver.java
 *
 * Created on 24. Dezember 2005, 00:01
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

package de.schlichtherle.io.archive.zip;

import java.io.UnsupportedEncodingException;

/**
 * A {@link Zip32Driver} which builds JAR archives.
 * JAR archives use <code>UTF-8</code> as the character set encoding for
 * entry names and comments.
 * Other than this, they are treated like regular ZIP32 files.
 * In particular, this class does <em>not</em> check the contents of an
 * archive file for the existance of a manifest or any other typical
 * artifacts of JAR files.
 * <p>
 * Instances of this class are immutable.
 * 
 * @see CheckedJarDriver
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class JarDriver extends Zip32Driver {

    public JarDriver() {
        super("UTF-8", false, false, null, null);
    }
}
