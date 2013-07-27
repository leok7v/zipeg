/*
 * FileReader.java
 *
 * Created on 8. November 2006, 19:42
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

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

/**
 * A drop-in-replacement for {@link java.io.FileReader java.io.FileReader}
 * which supports reading from an archive entry file.
 *
 * @see File#update()
 * @see File#umount()
 * @see <a href="package-summary.html#package_description">Package Description</a>
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.4
 */
public class FileReader extends InputStreamReader {

    public FileReader(String path) throws FileNotFoundException {
	super(new FileInputStream(path));
    }

    public FileReader(File file) throws FileNotFoundException {
	super(new FileInputStream(file));
    }

    public FileReader(FileDescriptor fd) {
	super(new FileInputStream(fd));
    }
}
