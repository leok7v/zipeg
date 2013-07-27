package com.zipeg;

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class FileDialogs {

    private static javax.swing.filechooser.FileFilter
                archives = new javax.swing.filechooser.FileFilter(){
        public boolean accept(File f) {
            try {
                return f.isDirectory() || Util.isArchiveFileType(f.getName());
            } catch (Throwable t) { // InterruptedException
                return false;
            }
        }
        public String getDescription() {
            return "Archives (.zip .rar .arj .lha .7z .iso .jar ...)";
        }
    };

    private FileDialogs() {
    }

    public static File choseDirectoryOnMac(File target) {
        assert Util.isMac;
        System.setProperty("apple.awt.use-file-dialog-package", "false");
        System.setProperty("JFileChooser.packageIsTraversable", "true");
        System.setProperty("JFileChooser.appBundleIsTraversable", "true");
        System.setProperty("apple.awt.fileDialogForDirectories", "true");
        FileDialog open = new FileDialog(MainFrame.getInstance(),
                "Zipeg: Extract to Folder", FileDialog.LOAD);
        if (target.isDirectory()) {
            open.setDirectory(target.getAbsolutePath());
        }
        open.setFilenameFilter(new FilenameFilter(){
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }
        });
        Dialogs.showModal(open);
        File file;
        if (open.getFile() != null) {
            file = new File(open.getDirectory(), open.getFile());
        } else {
            file = null;
        }
        System.setProperty("apple.awt.fileDialogForDirectories", "false");
        return file;
    }

    public static File choseDirectory(File target) {
        final FileChooser fc = FileChooser.getInstance();
        fc.setCurrentDirectory(target.isDirectory() ? target : Util.getDesktop());
        fc.setFileHidingEnabled(true);
        fc.setDialogTitle("Zipeg: Destination Folder");
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        JDialog d = fc.createDialog();
        Dialogs.showModal(d);
        File file = fc.getSelectedFile();
        if (file != null) {
            if (fc.getSelectedFile().isDirectory()) {
                file = fc.getSelectedFile();
            } else {
                file = null;
            }
        }
        return file;
    }

    public static void preLoadForMac() {
        if (Util.isMac) {
            try {
                MainFrame mf = MainFrame.getInstance();
                Frame top = mf.isVisible() ? mf : MainFrame.getOffscreen();
                FileDialog open = new FileDialog(top, "Zipeg: Open Archive", FileDialog.LOAD);
            } catch (Throwable ignore) {
               /* ignore */
            }
        }
    }

    public static File openArchiveOnMac() {
        assert Util.isMac;
        // see: http://developer.apple.com/documentation/Java/Reference/1.4.2/appledoc/api/com/apple/eawt/CocoaComponent.html
        System.setProperty("apple.awt.fileDialogForDirectories", "false");
        System.setProperty("com.apple.eawt.CocoaComponent.CompatibilityMode", "false");
        System.setProperty("apple.awt.use-file-dialog-package", "false");
        System.setProperty("JFileChooser.packageIsTraversable", "true");
        System.setProperty("JFileChooser.appBundleIsTraversable", "true");
        MainFrame mf = MainFrame.getInstance();
        Frame top = mf.isVisible() ? mf : MainFrame.getOffscreen();
        FileDialog open = new FileDialog(top, "Zipeg: Open Archive", FileDialog.LOAD);
        String dir = Presets.get("fileOpenFolder", Util.getCanonicalPath(Util.getDesktop()));
        open.setDirectory(dir);
        open.setFilenameFilter(new FilenameFilter(){
            public boolean accept(File dir, String name) {
                return Util.isArchiveFileType(name);
            }
        });
        Dialogs.showModal(open);
        File chosen = null;
        if (open.getFile() != null) {
            File f = new File(open.getDirectory(), open.getFile());
            chosen = f.getAbsoluteFile();
            dir = chosen.getParent();
            if (!mf.isVisible() && top != mf) {
                mf.setVisible(true);
            }
        }
        if (dir != null) {
            Presets.put("fileOpenFolder", dir);
            Presets.sync();
        }
        return chosen;
    }

    public static File openArchive() {
        FileChooser fc = FileChooser.getInstance();
        // for Mac - FilteredFileSystemView is necessary because of hidden files.
        // for Windows - as a workaround workaround for:
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6372808
        fc.setFileSystemView(fc.getFilteredFileSystemView());
        String dir = Presets.get("fileOpenFolder", Util.getCanonicalPath(Util.getDesktop()));
        File cd = new File(dir);
        fc.setCurrentDirectory(cd.isDirectory() ? cd : Util.getDesktop());
        fc.setFileHidingEnabled(true);
        fc.setDialogTitle("Zipeg: Open Archive");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.addChoosableFileFilter(archives);
        fc.setAcceptAllFileFilterUsed(true);
        fc.setFileFilter(archives);
        fc.setMultiSelectionEnabled(false);
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        JDialog d = fc.createDialog();
        Dialogs.showModal(d);
        File chosen = null;
        if (fc.getSelectedFile() != null) {
            chosen = fc.getSelectedFile();
            dir = chosen == null ? null : chosen.getParent();
        }
        if (dir != null) {
            Presets.put("fileOpenFolder", dir);
            Presets.sync();
        }
        return chosen;
    }

}
