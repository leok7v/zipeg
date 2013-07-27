package com.zipeg;

import javax.swing.*;
import java.awt.event.*;

public class OSX {
    
    private OSX() {
    }

    public static boolean setFrameMinSize(final JFrame frame, final int width, final int height) {
        if (!mac.loadJniLibrary()) {
            return false;
        } else {
            frame.addWindowListener(new WindowAdapter() {
                public void windowOpened(WindowEvent e) {
                    try {
                        setContentMinSize(frame, width, height);
                    } catch (Throwable t) {
                        throw new Error(t);
                    }
                }
            });
            return true;
        }
    }

    private static native void setContentMinSize(Object frame, int width, int height);

    private static native long getpid();

    public static long getMyProcessId() {
        return mac.loadJniLibrary() ? getpid() : 0;
    }

}
