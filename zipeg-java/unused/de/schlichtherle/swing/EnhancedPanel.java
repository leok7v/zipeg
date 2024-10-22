/*
 * EnhancedPanel.java
 *
 * Created on 21. Februar 2006, 10:17
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

package de.schlichtherle.swing;

import de.schlichtherle.swing.event.PanelEvent;
import de.schlichtherle.swing.event.PanelListener;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.EventListener;
import java.util.logging.Logger;

import javax.swing.JPanel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.EventListenerList;

/**
 * This class adds methods to fire {@link PanelEvent}s.
 * <p>
 * Note that in TrueZIP 6.1, this class has been refactored to coalesce
 * multiple panel events for the same cause by posting them to the AWT's
 * Event Queue, from which the coalesced event would then be dispatched by
 * AWT's Event Dispatching Thread.
 * <p>
 * However, since TrueZIP 6.4, these events are fired <em>synchronously</em>
 * again, whereby it is ensured that only a single event is fired for each
 * cause.
 * 
 * @author Christian Schlichtherle
 * @version @version@
 * @since TrueZIP 5.1
 */
public class EnhancedPanel extends JPanel  {

    private static final Logger logger
            = Logger.getLogger(EnhancedPanel.class.getName());
    
    /**
     * Creates a new <code>EnhancedPanel</code> with the specified layout
     * manager and buffering strategy.
     *
     * @param layout The {@link LayoutManager} to use.
     * @param isDoubleBuffered A boolean, true for double-buffering, which
     *        uses additional memory space to achieve fast, flicker-free 
     *        updates.
     */
    public EnhancedPanel(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
        init();
    }

    /**
     * Create a new buffered <code>EnhancedPanel</code> with the specified
     * layout manager.
     *
     * @param layout The {@link LayoutManager} to use.
     */
    public EnhancedPanel(LayoutManager layout) {
        super(layout);
        init();
    }

    /**
     * Creates a new <code>EnhancedPanel</code> with <code>FlowLayout</code>
     * and the specified buffering strategy.
     * If <code>isDoubleBuffered</code> is true, the <code>EnhancedPanel</code>
     * will use a double buffer.
     *
     * @param isDoubleBuffered A boolean, true for double-buffering, which
     *        uses additional memory space to achieve fast, flicker-free 
     *        updates.
     */
    public EnhancedPanel(boolean isDoubleBuffered) {
        super(isDoubleBuffered);
        init();
    }

    /**
     * Creates a new <code>EnhancedPanel</code> with a double buffer
     * and a flow layout.
     */
    public EnhancedPanel() {
        init();
    }

    private void init() {
        addHierarchyListener(new HierarchyListener() {
            public void hierarchyChanged(final HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)
                        != HierarchyEvent.SHOWING_CHANGED)
                    return;

                if (!isVisible())
                    return;

                final Window window = getAncestorWindow();
                final boolean windowShown = window.isShowing();
                assert windowShown == isShowing();
                processPanelEvent(new PanelEvent(EnhancedPanel.this,
                        windowShown
                            ? PanelEvent.ANCESTOR_WINDOW_SHOWN
                            : PanelEvent.ANCESTOR_WINDOW_HIDDEN));
            }
        });

        /*final ComponentListener cl = new ComponentAdapter() {
            public void componentShown(ComponentEvent evt) {
                // Depending on which method the ancestor window uses to show
                // itself, multiple ComponentEvents may occur for the same event.
                // By posting the subsequent PanelEvents to the AWT Event Queue,
                // they can be coalesced into one event again.
                final PanelEvent event = new PanelEvent(EnhancedPanel.this,
                        PanelEvent.ANCESTOR_WINDOW_SHOWN);
                logger.finer("Posting " + event);
                getToolkit().getSystemEventQueue().postEvent(event);
                //processPanelEvent(event);
            }

            public void componentHidden(ComponentEvent evt) {
                // Depending on which method the ancestor window uses to hide
                // itself, multiple ComponentEvents may occur for the same event.
                // By posting the subsequent PanelEvents to the AWT Event Queue,
                // they can be coalesced into one event again.
                final PanelEvent event = new PanelEvent(EnhancedPanel.this,
                        PanelEvent.ANCESTOR_WINDOW_HIDDEN);
                logger.finer("Posting " + event);
                getToolkit().getSystemEventQueue().postEvent(event);
                //processPanelEvent(event);
            }
        };

        addAncestorListener(new AncestorListener() {
            public void ancestorAdded(final AncestorEvent evt) {
                // Note that with J2SE 1.4.2_10 this event happens BEFORE
                // the ancestor window is made visible, while with
                // J2SE 1.5.0_06 this event happens AFTER the ancestor is
                // made visible.
                final Window window = getAncestorWindow(evt.getAncestor());
                window.addComponentListener(cl);
                final boolean windowShown = window.isShowing();
                assert windowShown == isShowing();
                if (windowShown)
                    cl.componentShown(null);
            }

            public void ancestorRemoved(final AncestorEvent evt) {
                // Note that with J2SE 1.4.2_10 this event happens BEFORE
                // the ancestor window is made visible, while with
                // J2SE 1.5.0_06 this event happens AFTER the ancestor is
                // made visible.
                final Window window = getAncestorWindow(evt.getAncestor());
                window.removeComponentListener(cl);
                final boolean windowShown = window.isShowing();
                assert windowShown == isShowing();
                if (!windowShown)
                    cl.componentHidden(null);
            }

            public void ancestorMoved(AncestorEvent evt) {
            }
        });*/
    }

    /**
     * Overridden in order to prevent this component to deliver a
     * {@link PanelEvent} multiple times from the same source.
     *
     * @deprecated See {@link EnhancedPanel}.
     */
    protected AWTEvent coalesceEvents(
            final AWTEvent existingEvent,
            final AWTEvent newEvent) {
        assert existingEvent.getSource() == newEvent.getSource();
        
        // Coalesce arbitrary sequences of ANCESTOR_WINDOW_* events into
        // the last one.
        if (existingEvent instanceof PanelEvent && newEvent instanceof PanelEvent) {
            //assert false : "This is dead code since the refactoring for TrueZIP 6.4!";
            final int id = newEvent.getID();
            assert id == existingEvent.getID();
            switch (id) {
                case PanelEvent.ANCESTOR_WINDOW_SHOWN:
                case PanelEvent.ANCESTOR_WINDOW_HIDDEN:
                    return newEvent;
            }
        }

        return super.coalesceEvents(existingEvent, newEvent);
    }

    /**
     * Overridden in order to process {@link PanelEvent}s.
     *
     * @deprecated See {@link EnhancedPanel}.
     */
    protected void processEvent(final AWTEvent event) {
        if (event instanceof PanelEvent) {
            //assert false : "This is dead code since the refactoring for TrueZIP 6.4!";
            processPanelEvent((PanelEvent) event);
        } else {
            super.processEvent(event);
        }
    }

    /**
     * Calls {@link #fireAncestorWindowShown} or
     * {@link #fireAncestorWindowHidden}, depending on the ID of the given
     * <code>event</code>.
     */
    protected void processPanelEvent(final PanelEvent event) {
        logger.fine("Delivering " + event);
        switch (event.getID()) {
            case PanelEvent.ANCESTOR_WINDOW_SHOWN:
                fireAncestorWindowShown(event);
                break;

            case PanelEvent.ANCESTOR_WINDOW_HIDDEN:
                fireAncestorWindowHidden(event);
                break;

            default:
                throw new AssertionError();
        }
    }

    /**
     * Returns the ancestor {@link Window} of this <code>Panel</code> or
     * <code>null</code> if the component is not (yet) placed in a
     * <code>Window</code>.
     */
    public Window getAncestorWindow() {
        return getAncestorWindow(this);
    }

    private static final Window getAncestorWindow(Component c) {
        while (c != null && !(c instanceof Window))
            c = c.getParent();

        return (Window) c;
    }

    /**
     * Utility field used by event firing mechanism.
     * Note that the listeners don't get serialized with this component!
     */
    private transient EventListenerList listenerList;

    /**
     * Adds the <code>listener</code> to the list of receivers for
     * {@link PanelEvent}s.
     * <p>
     * Note that the listener doesn't get serialized with this component!
     *
     * @param listener The listener to add.
     *        If this method is called <code>n</code> times with the same
     *        listener, any events generated will be delivered to this
     *        listener <code>n</code> times.
     *
     * @throws NullPointerException If <code>listener</code> is
     *         <code>null</code>.
     */
    public void addPanelListener(final PanelListener listener) {
        if (listener == null)
            throw new NullPointerException();
        if (listenerList == null )
            listenerList = new EventListenerList();
        listenerList.add(PanelListener.class, listener);
    }

    /**
     * Removes the <code>listener</code> from the list of receivers for
     * {@link PanelEvent}s.
     *
     * @param listener The listener to remove.
     *        If this listener has been {@link #addPanelListener added}
     *        multiple times, it is removed from the list only once.
     *
     * @throws NullPointerException If <code>listener</code> is
     *         <code>null</code>.
     */
    public void removePanelListener(final PanelListener listener) {
        if (listener == null)
            throw new NullPointerException();
        if (listenerList == null)
            return;
        listenerList.remove(PanelListener.class, listener);
    }

    /**
     * Returns an array of all the panel listeners
     * registered on this component.
     *
     * @return All of this panel's <code>PanelListener</code>s or an empty
     *         array if no panel listeners are currently registered.
     *
     * @see #addPanelListener
     * @see #removePanelListener
     */
    public PanelListener[] getPanelListeners() {
        if (listenerList != null)
            return (PanelListener[]) listenerList.getListeners(PanelListener.class);
        else
            return new PanelListener[0];
    }

    public EventListener[] getListeners(Class listenerType) {
        if (listenerType == PanelListener.class)
            return getPanelListeners();
        else
            return super.getListeners(listenerType);
    }

    /**
     * Notifies all registered listeners about the event.
     * This is a synchronous operation.
     * 
     * @param event The event to be fired.
     *
     * @deprecated You should not call this method directly.
     */
    protected void fireAncestorWindowShown(final PanelEvent event) {
        if (listenerList == null)
            return;
        final Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == PanelListener.class)
                ((PanelListener) listeners[i+1]).ancestorWindowShown(event);
        }
    }

    /**
     * Notifies all registered listeners about the event.
     * This is a synchronous operation.
     * 
     * @param event The event to be fired.
     *
     * @deprecated You should not call this method directly.
     */
    protected void fireAncestorWindowHidden(final PanelEvent event) {
        if (listenerList == null)
            return;
        final Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == PanelListener.class)
                ((PanelListener) listeners[i+1]).ancestorWindowHidden(event);
        }
    }
}
