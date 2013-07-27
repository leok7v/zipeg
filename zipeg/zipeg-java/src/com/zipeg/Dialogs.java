package com.zipeg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Dialogs {

    private static Dialog shown;

    private Dialogs() {
    }

    public static Dialog shown() {
        return shown;
    }

    public static boolean isShown() {
        return shown != null;
    }

    public static void dispose() {
        if (shown != null) {
            shown.setVisible(false);
            shown.dispose();
            shown = null;
        }
    }

    public static boolean setModalSheet(Dialog d) {
        d.setModal(true); // old way (before trying mac DOCUMENT_MODAL (SHEET)
        return mac.setModalityType(d, "DOCUMENT_MODAL");
    }

    public static void showModal(Dialog d) {
        d.setModal(true);
        show(d);
    }

    public static void show(Dialog d) {
        if (Debug.isDebug()) {
            assert shown == null : "shown=" + shown().getTitle() + " already shown";
            assert !d.isVisible();
            assert d != shown;
        }
        dispose();
        if (d instanceof JDialog) {
            final JDialog jd = (JDialog)d;
            jd.getRootPane().getActionMap().put("cancelAction", new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    dispose();
                }
            });
            jd.getRootPane().getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "cancelAction");
        }
        shown = d;
        d.setVisible(true);
        dispose();
    }

}
