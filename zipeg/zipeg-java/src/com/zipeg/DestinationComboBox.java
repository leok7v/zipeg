package com.zipeg;

import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;

public class DestinationComboBox extends AutoCompleteDropDown {

    private ArrayList files = new ArrayList();
    private String parent = null;

    DestinationComboBox() {
        setCoboBoxModel(new Model());
        setTransferHandler(new TransferHandler() {

            public boolean canImport(JComponent c, DataFlavor[] dfs) {
                for (DataFlavor df : dfs) {
                    if (DataFlavor.javaFileListFlavor.equals(df)) {
                        selectAll();
                        return true;
                    }
                }
                return false;
            }

            public boolean importData(JComponent c, Transferable t) {
                try {
                    List fl = (List)t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (fl != null && fl.size() == 1) {
                        File file = (File)fl.get(0);
                        if (file.isDirectory() && file.canWrite() && Util.isDirectoryWritable(file)) {
                            setText(Util.getCanonicalPath(file));
                            return true;
                        }
                    }
                } catch (Throwable e) {
                    Debug.printStackTrace(e);
                }
                return false;
            }

            public int getSourceActions(JComponent c) {
                return DnDConstants.ACTION_COPY;
            }

        });

        getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                changed(e);
            }

            public void removeUpdate(DocumentEvent e) {
                changed(e);
            }

            public void changedUpdate(DocumentEvent e) {
                changed(e);
            }

            private void changed(DocumentEvent e) {
                if (Util.isMac && "~".equals(getText())) {
                    Util.invokeLater(1, new Runnable() {
                        public void run() {
                            setText(Util.getHome() + "/");
                            select(getText().length(), getText().length());
                        }
                    });
                }
            }
        });
    }

    private ArrayList getFiles() {
        String text = getText();
        int s = getSelectionStart();
        int e = getSelectionEnd();
        if (0 <= s && s <= e && e == text.length()) {
            text = text.substring(0, s);
        }
        File file = new File(text);
        String n = file.isDirectory() ? text : file.getParent();
        File dir = n == null ? null : new File(n);
        while (dir != null && !dir.isDirectory()) {
            String p = dir.getParent();
            dir = p == null ? null : new File(p);
        }
        if (dir == null) {
            dir = new File(File.separator); // root
        }
        if (Util.getCanonicalPath(dir).equals(parent) && files.size() > 0) {
            return files;
        }
        files.clear();
        File[] list = dir.listFiles();
        for (int i = 0; list != null && i < list.length; i++) {
            if (list[i].isDirectory() && !list[i].isHidden() &&
               !list[i].getName().startsWith(".")) {
                files.add(Util.getCanonicalPath(list[i]));
            }
        }
        parent = Util.getCanonicalPath(dir);
        return files;
    }

    public Dimension getMaximumSize() {
        Dimension p = getPreferredSize();
        Dimension x = super.getMaximumSize();
        return new Dimension(x.width, p.height);
    }

    private final class Model extends DefaultComboBoxModel {

        public int getSize() {
            return getFiles().size();
        }

        public Object getElementAt(int index) {
            return getFiles().get(index);
        }

    }

}
