package com.zipeg.win;

import com.zipeg.*;
import sun.awt.shell.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.awt.*;

// workaround for: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6372808

public class Win32ShellFolderManager6372808 extends Win32ShellFolderManager2 {

    private static Method getDrives;
    private static boolean patched;
    private static Comparator comparator = new Comparator() {
        public int compare(Object o1, Object o2) {
            ShellFolder shellFolder1 = (ShellFolder)o1;
            ShellFolder shellFolder2 = (ShellFolder)o2;
            boolean isDrive = shellFolder1.getPath().endsWith(":\\");
            if (isDrive ^ shellFolder2.getPath().endsWith(":\\")) {
                return isDrive ? -1 : 1;
            } else {
                return shellFolder1.getPath().compareTo(shellFolder2.getPath());
            }
        }
    };

    public Win32ShellFolderManager6372808() {
    }

    @SuppressWarnings("UnusedDeclaration")
    public static Boolean workaround() { // called via reflection
        try {
            if (Util.isWindows) {
                Toolkit kit = Toolkit.getDefaultToolkit();
                Object o = kit.getDesktopProperty("Shell.shellFolderManager");
                Class sfmc = o instanceof Class ? (Class)o : Class.forName((String)o);
                if ("sun.awt.shell.Win32ShellFolderManager".equals(sfmc.getName()) ||
                    "sun.awt.shell.Win32ShellFolderManager2".equals(sfmc.getName())) {
                    Method setDesktopProperty = calls.getDeclaredMethod("java.awt.Toolkit.setDesktopProperty",
                                                                  new Class[]{String.class, Object.class});
                    calls.call(kit, setDesktopProperty,
                            new Object[]{"Shell.shellFolderManager",
                            Win32ShellFolderManager6372808.class});
                }
                getDrives = calls.getDeclaredMethod("sun.awt.shell.Win32ShellFolderManager2.getDrives", new Class[]{});
                patchShellFolderManager();
                Debug.traceln("Win32ShellFolderManager6372808 patched=" + patched);
            }
            return Boolean.TRUE;
        } catch (Throwable ignore) {
            Debug.printStackTrace("no longer valid", ignore);
            return Boolean.FALSE;
        }
    }

    public Object get(String key) {
        if (key.equals("fileChooserComboBoxFolders")) {
            File desktop = Util.getDesktop();
            if (desktop == null || !desktop.exists()) {
                return super.get(key);
            }
            ArrayList folders = new ArrayList();
            File drives = getDrives == null ? null :
                    (File)calls.call(getDrives, null, Util.NONE);
            folders.add(desktop);
            // Add all second level folders
            File[] secondLevelFolders = desktop.listFiles();
            if (secondLevelFolders != null) {
                Arrays.sort(secondLevelFolders);
                for (int j = 0; j < secondLevelFolders.length; j++) {
                    File folder = secondLevelFolders[j];
                    String name = folder.getName().toLowerCase();
                    boolean isArchive = Util.isArchiveFileType(name);
                    if (!isArchive && (folder.isDirectory() || !isFileSystem(folder))) {
                        folders.add(folder);
                        // Add third level for "My Computer"
                        if (drives != null && folder.equals(drives)) {
                            File[] thirdLevelFolders = folder.listFiles();
                            if (thirdLevelFolders != null) {
                                Arrays.sort(thirdLevelFolders, comparator);
                                folders.addAll(Arrays.asList(thirdLevelFolders));
                            }
                        }
                    }
                }
            }
            return folders.toArray(new File[folders.size()]);
        }
        try {
            return super.get(key);
        } catch (Throwable x) {
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6449933
            // java.lang.ArrayIndexOutOfBoundsException: 3184
//          Debug.printStackTrace("get(" + key + ")", x);
            Debug.traceln("exception in get(" + key + ") " + x.getClass().getName() + " " + x.toString());
            return reliefForFailedGet(key);
        }
    }

    private Object reliefForFailedGet(String key) {
        File desktop = Util.getDesktop();
        File home = new File(Util.getHome());
        if (key.equals("fileChooserDefaultFolder")) {
            return home.isDirectory() ? home : desktop;
        } else if (key.equals("roots")) {
            return new File[]{desktop};
        } else if (key.equals("fileChooserComboBoxFolders")) {
            return new File[]{desktop};
        } else if (key.equals("fileChooserShortcutPanelFolders")) {
            return new File[]{desktop};
        } else if (key.startsWith("fileChooserIcon ") ||
                key.startsWith("optionPaneIcon ") ||
                key.startsWith("shell32Icon ")) {
            return null;
        }
        return null;
    }

    // http://www.google.com/codesearch/p?hl=en#vrjsOGoIshc/openjdk/sun/awt/shell/Win32ShellFolderManager2.java
    /*
     * @param key a <code>String</code>
     *  "fileChooserDefaultFolder":
     *    Returns a <code>File</code> - the default shellfolder for a new filechooser
     *  "roots":
     *    Returns a <code>File[]</code> - containing the root(s) of the displayable hierarchy
     *  "fileChooserComboBoxFolders":
     *    Returns a <code>File[]</code> - an array of shellfolders representing the list to
     *    show by default in the file chooser's combobox
     *   "fileChooserShortcutPanelFolders":
     *    Returns a <code>File[]</code> - an array of shellfolders representing well-known
     *    folders, such as Desktop, Documents, History, Network, Home, etc.
     *    This is used in the shortcut panel of the filechooser on Windows 2000
     *    and Windows Me.
     *  "fileChooserIcon nn":
     *    Returns an <code>Image</code> - icon nn from resource 216 in shell32.dll,
     *      or if not found there from resource 124 in comctl32.dll (Windows only).
     *  "optionPaneIcon iconName":
     *    Returns an <code>Image</code> - icon from the system icon list
     *
     * @return An Object matching the key string.
     */
/*
    public Object get_from_Java_1_7(String key) {
        if (key.equals("fileChooserDefaultFolder")) {
            File file = getPersonal();
            if (file == null) {
                file = getDesktop();
            }
            return file;
        } else if (key.equals("roots")) {
            // Should be "History" and "Desktop" ?
            if (roots == null) {
                File desktop = getDesktop();
                if (desktop != null) {
                    roots = new File[] { desktop };
                } else {
                    roots = (File[])super.get(key);
                }
            }
            return roots;
        } else if (key.equals("fileChooserComboBoxFolders")) {
            Win32ShellFolder2 desktop = getDesktop();

            if (desktop != null) {
                ArrayList<File> folders = new ArrayList<File>();
                Win32ShellFolder2 drives = getDrives();

                Win32ShellFolder2 recentFolder = getRecent();
                if (recentFolder != null && OSInfo.getWindowsVersion().compareTo(OSInfo.WINDOWS_2000) >= 0) {
                    folders.add(recentFolder);
                }

                folders.add(desktop);
                // Add all second level folders
                File[] secondLevelFolders = desktop.listFiles();
                Arrays.sort(secondLevelFolders);
                for (File secondLevelFolder : secondLevelFolders) {
                    Win32ShellFolder2 folder = (Win32ShellFolder2) secondLevelFolder;
                    if (!folder.isFileSystem() || folder.isDirectory()) {
                        folders.add(folder);
                        // Add third level for "My Computer"
                        if (folder.equals(drives)) {
                            File[] thirdLevelFolders = folder.listFiles();
                            if (thirdLevelFolders != null) {
                                Arrays.sort(thirdLevelFolders, driveComparator);
                                for (File thirdLevelFolder : thirdLevelFolders) {
                                    folders.add(thirdLevelFolder);
                                }
                            }
                        }
                    }
                }
                return folders.toArray(new File[folders.size()]);
            } else {
                return super.get(key);
            }
        } else if (key.equals("fileChooserShortcutPanelFolders")) {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            ArrayList<File> folders = new ArrayList<File>();
            int i = 0;
            Object value;
            do {
                value = toolkit.getDesktopProperty("win.comdlg.placesBarPlace" + i++);
                try {
                    if (value instanceof Integer) {
                        // A CSIDL
                        folders.add(new Win32ShellFolder2((Integer)value));
                    } else if (value instanceof String) {
                        // A path
                        folders.add(createShellFolder(new File((String)value)));
                    }
                } catch (IOException e) {
                    // Skip this value
                }
            } while (value != null);

            if (folders.size() == 0) {
                // Use default list of places
                for (File f : new File[] {
                    getRecent(), getDesktop(), getPersonal(), getDrives(), getNetwork()
                }) {
                    if (f != null) {
                        folders.add(f);
                    }
                }
            }
            return folders.toArray(new File[folders.size()]);
        } else if (key.startsWith("fileChooserIcon ")) {
            int i = -1;
            String name = key.substring(key.indexOf(" ")+1);
            try {
                i = Integer.parseInt(name);
            } catch (NumberFormatException ex) {
                if (name.equals("ListView")) {
                    i = (useShell32Icons) ? 21 : 2;
                } else if (name.equals("DetailsView")) {
                    i = (useShell32Icons) ? 23 : 3;
                } else if (name.equals("UpFolder")) {
                    i = (useShell32Icons) ? 28 : 8;
                } else if (name.equals("NewFolder")) {
                    i = (useShell32Icons) ? 31 : 11;
                } else if (name.equals("ViewMenu")) {
                    i = (useShell32Icons) ? 21 : 2;
                }
            }
            if (i >= 0) {
                return Win32ShellFolder2.getFileChooserIcon(i);
            }
        } else if (key.startsWith("optionPaneIcon ")) {
            cli.System.Drawing.Icon icon;
            if (key == "optionPaneIcon Error") {
                icon = SystemIcons.get_Error();
            } else if (key == "optionPaneIcon Information") {
                icon = SystemIcons.get_Information();
            } else if (key == "optionPaneIcon Question") {
                icon = SystemIcons.get_Question();
            } else if (key == "optionPaneIcon Warning") {
                icon = SystemIcons.get_Warning();
            } else {
                return null;
            }
            return new BufferedImage(icon.ToBitmap());
        } else if (key.startsWith("shell32Icon ")) {
            int i;
            String name = key.substring(key.indexOf(" ")+1);
            try {
                i = Integer.parseInt(name);
                if (i >= 0) {
                    return Win32ShellFolder2.getShell32Icon(i);
                }
            } catch (NumberFormatException ex) {
            }
        }
        return null;
    }
*/


    private static boolean isFileSystem(File folder) {
        String lcname = folder.getName().toLowerCase();
        if (Util.isArchiveFileType(lcname) || lcname.endsWith(".lnk")) {
            return false;
        }
        if (folder instanceof ShellFolder) {
            ShellFolder sf = (ShellFolder)folder;
            // Shortcuts to directories are treated as not being file system objects,
            // so that they are never returned by JFileChooser.
            return sf.isFileSystem() && !(sf.isLink() && sf.isDirectory());
        } else {
            return true;
        }
    }

    // workaround for regression:
    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6544857

    public static class MyInvoker implements sun.awt.shell.ShellFolder.Invoker {

        private sun.awt.shell.ShellFolder.Invoker delegate;

        MyInvoker(sun.awt.shell.ShellFolder.Invoker i) {
            delegate = i;
        }

        public <T> T invoke(java.util.concurrent.Callable<T> tCallable) throws java.lang.Exception {
            try {
                return delegate.invoke(tCallable);
            } catch (Throwable t) {
                Debug.traceln("delegate exception: " + t.getMessage());
                return null;
            }
        }

    }

    protected sun.awt.shell.ShellFolder.Invoker createInvoker() {
        return new MyInvoker(super.createInvoker());
    }

    private static void patchShellFolderManager() {
        try {
            // 1.6, 1.7 staticly initialize ShellFolder.shellFolderManager field.
            // Order of initialization is too complex to be predictable.
            Field shellFolderManager = ShellFolder.class.getDeclaredField("shellFolderManager");
            shellFolderManager.setAccessible(true);
            Object sfm = shellFolderManager.get(null);
            if (sfm != null && !(sfm instanceof Win32ShellFolderManager6372808)) {
                shellFolderManager.set(null, new Win32ShellFolderManager6372808());
            }
            patched = true;
        } catch (Throwable t) {
            Debug.printStackTrace("failed to patch ShellFolder.shellFolderManager", t);
        }
    }

}
