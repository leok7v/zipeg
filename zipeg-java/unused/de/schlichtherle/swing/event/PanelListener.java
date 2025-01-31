/*
 * PanelListener.java
 *
 * Created on 21. Februar 2006, 11:33
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

package de.schlichtherle.swing.event;

import java.util.EventListener;

/**
 * Used to notify listeners of an {@link de.schlichtherle.swing.EnhancedPanel}
 * that its ancestor window is shown or hidden.
 *
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 5.1
 */
public interface PanelListener extends EventListener {
    
    /**
     * Invoked when the ancestor window of an
     * {@link de.schlichtherle.swing.EnhancedPanel} is shown.
     */
    void ancestorWindowShown(PanelEvent evt);

    /**
     * Invoked when the ancestor window of an
     * {@link de.schlichtherle.swing.EnhancedPanel} is hidden.
     */
    void ancestorWindowHidden(PanelEvent evt);
}
