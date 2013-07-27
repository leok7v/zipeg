package com.zipeg;

import javax.swing.*;
import javax.swing.filechooser.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

public class FileChooser extends JFileChooser {

    private static Set exclude = new HashSet();
    private static FileChooser instance;
    private static int count;
    private FilteredFileSystemView ffsv = null;
    private static final File[] EMPTY = new File[]{};
    private static boolean patchFailed = false; // 6372808 patch failed

    public static void workaround() {
        // see: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4525475
        UIManager.put("FileChooser.readOnly", Boolean.TRUE);
        // reportedly it has been fixed in 6u10 which is 1.6010
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5050516
        double ver = Util.javaVersion;
        if (!patchFailed && Util.isWindows/* && ver < 1.6010*/) {
            // see: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6372808
            try {
                Object b = calls.callStatic("com.zipeg.win.Win32ShellFolderManager6372808.workaround", Util.NONE);
                patchFailed = Util.equals(b, Boolean.FALSE);
            } catch (Throwable x) {
                Debug.printStackTrace(x);
                patchFailed = true;
            }
        }
    }

    public static FileChooser getInstance() {
        if (instance == null) {
            workaround();
            // see: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4711700
            int retry = 32;
            long time0 = System.currentTimeMillis();
            for (;;) {
                try {
                    FileChooser fc = new FileChooser();
                    long time = System.currentTimeMillis() - time0;
//                  Debug.traceln("new FileChooser() " + time + " ms ");
                    instance = fc;
                    break;
                } catch (final Throwable x) {
                    long time = System.currentTimeMillis() - time0;
                    Debug.trace("new JFileChooser() " + time + " ms " + x.getMessage());
                    --retry;
                    if (retry == 0) {
                        if (x instanceof UnsatisfiedLinkError && Util.isWindows && Util.javaVersion <= 1.6017) {
                            // UnsatisfiedLinkError for getFileChooserBitmapBits in 1.6.0_17b4 on Windows
                            CrashLog.updateJava(true);
                            System.exit(0);
                        } else {
                            throw new Error(x);
                        }
                    }
                    else {
                        Util.sleep(10 * (int)(Math.random() * 5) );
                    }
                }
            }
        }
        if (instance != null && patchFailed) {
            instance.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);
        }
        return instance;
    }

    protected final JDialog createDialog() {
        final JDialog d = super.createDialog(MainFrame.getTopFrame());
        addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                String c = e.getActionCommand();
                if (CANCEL_SELECTION.equals(c)) {
                    setSelectedFile(null);
                }
                if (APPROVE_SELECTION.equals(c) ||
                    CANCEL_SELECTION.equals(c)) {
                    d.setVisible(false);
                    d.dispose();
                    Util.sleep(100); // let FolderDisposer to work
                }
            }
        });
        return d;
    }

    public int showDialog(Component parent, String approveButtonText) {
        assert count == 0;
        count++;
        try {
            IdlingEventQueue.setInsideFileChooser(true);
            return super.showDialog(parent, approveButtonText);
        } finally {
            // bug in apple.laf.AquaFileChooserUI.MacListSelectionModel.
            // isSelectableInListIndex
            IdlingEventQueue.setInsideFileChooser(false);
            count--;
        }
    }

    FilteredFileSystemView getFilteredFileSystemView() {
        if (ffsv == null) {
            ffsv = new FilteredFileSystemView(super.getFileSystemView());
        }
        return ffsv;
    }

    // Following overrides try to minimize number of PropertyChange events
    // that javax.swing.plaf.basic.BasicDirectoryModel will receive.
    // On most property change events BasicDirectoryModel will create a file
    // loading background thread.

    public void setFileSystemView(FileSystemView fsv) {
        if (fsv != super.getFileSystemView()) {
            super.setFileSystemView(fsv);
        }
    }

    public void setCurrentDirectory(File dir) {
        if (!exclude.contains(dir) && !Util.equals(dir, super.getCurrentDirectory())) {
            try {
                super.setCurrentDirectory(dir);
            } catch (Throwable bug) { // bug in following Shell .lnk folders
                exclude.add(dir);
            }
        }
    }

    public void setFileView(FileView fileView) {
        if (fileView != super.getFileView()) {
            super.setFileView(fileView);
        }
    }

    public void setFileFilter(FileFilter filter) {
        if (filter != super.getFileFilter()) {
            super.setFileFilter(filter);
        }
    }

    public void setFileHidingEnabled(boolean b) {
        if (b != super.isFileHidingEnabled()) {
            super.setFileHidingEnabled(b);
        }
    }

    public void setFileSelectionMode(int mode) {
        if (mode != super.getFileSelectionMode()) {
            super.setFileSelectionMode(mode);
        }
    }

    public void setAcceptAllFileFilterUsed(boolean b) {
        if (b != super.isAcceptAllFileFilterUsed()) {
            super.setAcceptAllFileFilterUsed(b);
        }
    }

    private static boolean isLinkToArchive(File f) {
        try {
            if (f.length() > 32*1024) {
                return false;
            }
            byte[] content = Util.readFile(f);
            String s = new String(content).toLowerCase();
            return s.indexOf(".zip") >= 0 || s.indexOf(".cab") >= 0 || s.indexOf(".rar") >= 0 || s.indexOf(".tar") >= 0;
        } catch (Throwable x) {
            return false;
        }
    }

    public static void dispose() {
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6612928
        instance = null;
        System.gc();
        Util.sleep(200); // let FolderDisposer to work
    }

    public class FilteredFileSystemView extends FileSystemView {

        private FileSystemView delegate;


        public FilteredFileSystemView(FileSystemView view) {
            this.delegate = view;
        }

        public File[] getFiles(File dir, boolean useFileHiding) {
            // this method is called from MULTIPLE threads.
            // see brain dead implementation of javax.swing.plaf.basic.BasicDirectoryModel.
            // Note that chooser.getFileFilter() is single variable
            // access and is hopefully "volatile" at least for now.
            try {
                // poor man PropertyChange event coalescing solution:
                synchronized(this) { this.wait(50); }
            } catch (InterruptedException x) {
                //Debug.traceln("INTERRUPTED: getFiles " + dir + " thread " + Thread.currentThread().hashCode());
                return EMPTY;
            }
            if (Thread.currentThread().isInterrupted()) {
                //Debug.traceln("INTERRUPTED: getFiles " + dir + " thread " + Thread.currentThread().hashCode());
                return EMPTY;
            }
            //Debug.traceln(">getFiles " + dir + " thread " + Thread.currentThread().hashCode());
            File[] list = delegate.getFiles(dir, useFileHiding);
            int n = 0;
            FileFilter filter = FileChooser.this.getFileFilter();
            for (int i = 0; i < list.length && useFileHiding && filter != null; i++) {
                if (isHiddenFile(list[i]) || !filter.accept(list[i])) {
                    list[i] = null;
                } else {
                    n++;
                }
            }
            File[] files = new File[n];
            int j = 0;
            for (int i = 0; i < list.length && j < n; i++) {
                if (list[i] != null) {
                    files[j] = list[i];
                    j++;
                }
            }
            //Debug.traceln("<getFiles " + dir + " thread " + Thread.currentThread().hashCode());
            return files;
        }

        public boolean isHiddenFile(File f) {
            // same multithreading issues as in getFiles
            FileFilter filter = FileChooser.this.getFileFilter();
            if (!Util.isWindows) {
                return filter != FileChooser.this.getAcceptAllFileFilter() && f.getName().startsWith(".");
            }
            return filter != null && !filter.accept(f);
        }

        public File createNewFolder(File containingDir) throws IOException {
            return delegate.createNewFolder(containingDir);
        }

        public boolean isRoot(File f) {
            return delegate.isRoot(f);
        }

        public Boolean isTraversable(File f) {
            if (Util.isWindows) {
                // workaround for: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6372808
                if (!f.isDirectory()) {
                    String name = f.getName().toLowerCase();
                    if (Util.isArchiveFileType(name)) {
                        return Boolean.FALSE;
                    } else if (name.endsWith(".lnk") && isLinkToArchive(f)) {
                        return Boolean.FALSE;
                    }
                }
            }
            return delegate.isTraversable(f);
        }

        public String getSystemDisplayName(File f) {
            return delegate.getSystemDisplayName(f);
        }

        public String getSystemTypeDescription(File f) {
            return delegate.getSystemTypeDescription(f);
        }

        public Icon getSystemIcon(File f) {
            return delegate.getSystemIcon(f);
        }

        public boolean isParent(File folder, File file) {
            return delegate.isParent(folder, file);
        }

        public File getChild(File parent, String fileName) {
            return delegate.getChild(parent, fileName);
        }

        public boolean isFileSystem(File f) {
            return delegate.isFileSystem(f);
        }

        public boolean isFileSystemRoot(File dir) {
            return delegate.isFileSystemRoot(dir);
        }

        public boolean isDrive(File dir) {
            return delegate.isDrive(dir);
        }

        public boolean isFloppyDrive(File dir) {
            return delegate.isFloppyDrive(dir);
        }

        public boolean isComputerNode(File dir) {
            return delegate.isComputerNode(dir);
        }

        public File[] getRoots() {
            return delegate.getRoots();
        }

        public File getHomeDirectory() {
            return delegate.getHomeDirectory();
        }

        public File getDefaultDirectory() {
            return delegate.getDefaultDirectory();
        }

        public File createFileObject(File dir, String filename) {
            return delegate.createFileObject(dir, filename);
        }

        public File createFileObject(String path) {
            return delegate.createFileObject(path);
        }

        public File getParentDirectory(File dir) {
            return delegate.getParentDirectory(dir);
        }

        protected File createFileSystemRoot(File f) {
            return super.createFileSystemRoot(f);
        }

    }


}