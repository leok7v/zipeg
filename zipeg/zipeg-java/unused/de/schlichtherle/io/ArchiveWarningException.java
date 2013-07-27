/*
 * ZipUpdateConflictException.java
 *
 * Created on 30. Oktober 2004, 13:26
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


import java.io.IOException;

/**
 * This exception is thrown by the {@link File#update()} and
 * {@link File#umount()} methods to indicate a non-fatal error condition.
 * 
 * <p>Both methods collect any exceptions occuring throughout their course 
 * to build an ordered exception chain so that if the head of the chain
 * (this is the object which you can actually catch) is an instance of
 * <tt>ArchiveWarningException</tt>, only this class of objects is in the chain.
 * This allows you to do a simple case distinction with a try-catch statement
 * to opt for ignoring warning-only error conditions.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0 (refactored from the former
 *        <code>ArchiveWarningException</code> in the package {@link de.schlichtherle.io})
 */
public class ArchiveWarningException extends ArchiveException {
    
    // TODO: Make this constructor package private!
    public ArchiveWarningException(
            ArchiveException priorZipException,
            String message) {
        super(priorZipException, message);
    }

    // TODO: Make this constructor package private!
    public ArchiveWarningException(
            ArchiveException priorZipException,
            String message,
            IOException cause) {
        super(priorZipException, message, cause);
    }

    // TODO: Make this constructor package private!
    public ArchiveWarningException(
            ArchiveException priorZipException,
            IOException cause) {
        super(priorZipException, cause);
    }
    
    public int getPriority() {
        return -1;
    }
}
