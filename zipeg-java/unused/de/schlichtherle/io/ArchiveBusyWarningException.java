/*
 * ArchiveBusyWarningException.java
 *
 * Created on 10. September 2005, 11:44
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

/**
 * This exception is thrown if an archive controller is trying to update or
 * umount an archive file while some input or output streams for its entries
 * are still open.
 * It signals that the archive has been updated and any of its entry streams
 * have been forced to close.
 * Hence, with the exception of their <code>close()</code> method, any
 * subsequent I/O operation on the entry streams will throw an
 * {@link ArchiveEntryStreamClosedException}.
 * The canonical path name of the archive file is provided as the detail message.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0
 */
public class ArchiveBusyWarningException extends ArchiveWarningException {

    ArchiveBusyWarningException(ArchiveException priorException, String canPath) {
        super(priorException, canPath);
    }

    // TODO: Remove this.
    /**
     * @deprecated You should not use this constructor.
     * It will vanish in the next major version.
     */
    public ArchiveBusyWarningException(
            ArchiveException priorException, java.io.File target) {
        super(priorException, target.getPath());
    }
}
