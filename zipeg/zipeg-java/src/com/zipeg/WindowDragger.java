package com.zipeg;

import java.awt.*;
import java.awt.event.*;

// because: apple.awt.draggableWindowBackground is broken (too greedy)
// http://explodingpixels.wordpress.com/2008/05/03/sexy-swing-app-the-unified-toolbar-now-fully-draggable/

public class WindowDragger {

    private static Point down = null;
    private static Dimension drag;

    public static void makeDraggable(Component c) {
        c.addMouseMotionListener(mouseMotionListener);
        c.addMouseListener(mouseListener);
    }

    public static void makeDraggableChildren(Component c) {
        if (c instanceof Container) {
            for (Component child : ((Container)c).getComponents()) {
                makeDraggableChildren(child);
            }
        }
        makeDraggable(c);
    }

    private static final MouseMotionListener mouseMotionListener = new MouseMotionAdapter() {
        public void mouseDragged(MouseEvent e) {
            if (down != null) {
                drag = new Dimension(e.getX() - down.x, e.getY() - down.y);
                dragParentWindow(e.getComponent());
            }
        }
    };

    private static final MouseListener mouseListener = new MouseAdapter() {

        public void mousePressed(MouseEvent e) {
            down = e.getPoint();
        }

        public void mouseReleased(MouseEvent e) {
            dragParentWindow(e.getComponent());
            down = null;
        }

    };

    private static void dragParentWindow(Component c) {
        if (drag != null) {
            Container p = c.getParent();
            while (p != null && !(p instanceof Window)) {
                p = p.getParent();
            }
            if (p != null) {
                Window w = (Window)p;
                Point pt = w.getLocation();
                pt.x += drag.width;
                pt.y += drag.height;
                w.setLocation(pt);
                drag = null;
            }
        }
    }

}
