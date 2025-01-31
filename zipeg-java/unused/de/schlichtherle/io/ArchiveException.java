/*
 * ArchiveException.java
 *
 * Created on 30. Oktober 2004, 03:08
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
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Comparator;

/**
 * Represents a chain of exceptions thrown when updating or unmounting an
 * archive file fails for one or more reasons.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 6.0 (refactored from the former
 *        <code>ArchiveControllerException</code> in the package {@link de.schlichtherle.io})
 */
public class ArchiveException extends ChainableIOException {

    // TODO: Make this constructor package private!
    /**
     * Constructs a new exception with the specified prior exception.
     * This is used when e.g. updating all ZIP files and more than one ZIP
     * compatible file cannot get updated. The prior exception would then be
     * the exception for the ZIP compatible file which couldn't get updated
     * before.
     *
     * @param  priorException An exception that happened before and that was
     *         caught. This is <b>not</b> a cause! May be <tt>null</tt>.
     */
    public ArchiveException(ArchiveException priorException) {
        super(priorException);
    }

    // TODO: Make this constructor package private!
    /**
     * Constructs a new exception with the specified prior exception
     * and a message.
     * This is used when e.g. updating all ZIP files and more than one ZIP
     * compatible file cannot get updated. The prior exception would then be
     * the exception for the ZIP compatible file which couldn't get updated
     * before.
     *
     * @param  priorException An exception that happened before and that was
     *         caught. This is <b>not</b> a cause! May be <tt>null</tt>.
     * @param  message The message for this exception.
     */
    public ArchiveException(
            ArchiveException priorException,
            String message) {
        super(priorException, message);
    }
    
    // TODO: Make this constructor package private!
    /**
     * Constructs a new exception with the specified prior exception and the
     * cause.
     * This is used when e.g. updating all ZIP files and more than one ZIP
     * compatible file cannot get updated. The prior exception would then be
     * the exception for the ZIP compatible file which couldn't get updated
     * before.
     *
     * @param  priorException An exception that happened before and that was
     *         caught. This is <b>not</b> a cause! May be <tt>null</tt>.
     * @param  cause The cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A <tt>null</tt> value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.).
     */
    public ArchiveException(
            ArchiveException priorException,
            IOException cause) {
        super(priorException, cause);
    }

    // TODO: Make this constructor package private!
    /**
     * Constructs a new exception with the specified prior exception,
     * a message and a cause.
     * This is used when e.g. updating all ZIP files and more than one ZIP
     * compatible file cannot get updated. The prior exception would then be
     * the exception for the ZIP compatible file which couldn't get updated
     * before.
     *
     * @param  priorException An exception that happened before and that was
     *         caught. This is <b>not</b> a cause! May be <tt>null</tt>.
     * @param  message The message for this exception.
     * @param  cause The cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A <tt>null</tt> value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.).
     */
    public ArchiveException(
            ArchiveException priorException,
            String message,
            IOException cause) {
        super(priorException, message, cause);
    }
}
