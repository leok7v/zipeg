/*
 * FileBusyException.java
 *
 * Created on 11. September 2005, 18:10
 */
/*
 * Copyright 2005 Schlichtherle IT Services
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
import de.schlichtherle.io.archive.spi.InputArchiveBusyException;
import de.schlichtherle.io.archive.spi.OutputArchiveBusyException;

import java.io.FileNotFoundException;

/**
 * This exception is thrown by the constructors of either the class
 * {@link FileInputStream} or {@link FileOutputStream} or those copy methods
 * of the {@link File} class which throw an <code>IOException</code> to
 * indicate that an archive entry cannot get accessed because the client
 * application is trying to input or output to the same archive file
 * simultaneously and the respective archive driver does not support this
 * or the archive file needs an automatic update which cannot get performed
 * because the client is still using other open {@link FileInputStream}s or
 * {@link FileOutputStream}s for other entries in the same archive file.
 * <p>
 * As a resolution to this (rather complex) issue, please make sure that you
 * always close your streams using the following idiom:
 * <p>
 * <pre>
 *      FileOutputStream fos = new FileOutputStream(file);
 *      try {
 *          // access fos here
 *      }
 *      finally {
 *          fos.close();
 *      }
 * </pre>
 * <p>
 * <b>Notes:</b>
 * <ul>
 * <li>This issue is not at all specific to TrueZIP: For example, if you use
 *     a <code>FileInputStream</code> or <code>FileOutputStream</code> on the
 *     Windows platform, certain file operations such as <code>delete()</code>
 *     and others will fail as long the stream hasn't been closed.
 * <li>Archive entry streams are actually automatically closed if they get
 *     finalized, but you should never rely on the garbage collector to do
 *     your work.
 * <li>You may call {@link File#update()} or {@link File#umount()} in order
 *     to force any archive entry streams to be closed and disconnected from
 *     the archive.
 *     A subsequent try to create an archive entry stream should succeed
 *     unless other exceptional conditions apply.
 *     However, if the client application is still using the disconnected
 *     streams, it will receive <code>IOExceptions</code> on other method
 *     than <code>close()</code>.
 *
 * @see FileInputStream
 * @see FileOutputStream
 * @see File#update()
 * @see File#umount()
 *
 * @author Christian Schlichtherle
 * @version @version@
 */
public class FileBusyException extends FileNotFoundException {

    /**
     * For use by
     * {@link de.schlichtherle.io.archive.spi.InputArchiveBusyException} and
     * {@link de.schlichtherle.io.archive.spi.OutputArchiveBusyException} only.
     */
    protected FileBusyException() {
    }

    // TODO: Remove this.
    /**
     * @deprecated You should not use this constructor.
     * It will vanish in the next major version.
     */
    public FileBusyException(InputArchiveBusyException cause) {
        super(cause != null ? cause.getLocalizedMessage() : null);
        initCause(cause);
    }

    // TODO: Remove this.
    /**
     * @deprecated You should not use this constructor.
     * It will vanish in the next major version.
     */
    public FileBusyException(OutputArchiveBusyException cause) {
        super(cause != null ? cause.getLocalizedMessage() : null);
        initCause(cause);
    }

    public FileBusyException(ArchiveBusyException cause) {
        super(cause != null ? cause.getLocalizedMessage() : null);
        initCause(cause);
    }
}
