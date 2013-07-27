/*
 * InputIOException.java
 *
 * Created on 8. Januar 2006, 19:41
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

import java.io.IOException;

/**
 * Thrown to indicate that an {@link IOException} happened on the input side
 * rather than the output side when copying an InputStream to an OutputStream.
 * This exception is always initialized with an <code>IOException</code> as
 * its cause, so it is safe to cast the return value of
 * {@link Throwable#getCause} to an <code>IOException</code>.
 *
 * @author Christian Schlichtherle
 * @version @version@
 */
public class InputIOException extends IOException {
    
    public InputIOException(IOException cause) {
        super(cause != null ? cause.getLocalizedMessage() : null);
        initCause(cause);
    }
}
