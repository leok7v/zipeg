package com.zipeg;

import java.io.*;
import java.util.*;

public class TrashBin {

    private static Map<File, File> trashes = Collections.synchronizedMap(new HashMap<File, File>());
    private static final String[] macFolders = new String[] {
            "desktop",
            "documents",
            "downloads",
            "library",
            "movies",
            "music",
            "pictures",
            "public",
            "sites"
    };

    private static final String[] winFolders = new String[] {
            "desktop",
            "my documents",
            "my videos",
            "my music",
            "my pictures"
    };

    private static final Set<String> folders = new HashSet<String>(30);


    private TrashBin() {
    }

    public static boolean isSystemFolder(File file) {
        if (!file.isDirectory()) {
            return false;
        }
        if (folders.size() == 0) {
            folders.addAll(Arrays.asList(Util.isMac ? macFolders : winFolders));
        }
        return Util.sameFile(file, Util.getHomeFolder()) ||
               Util.sameFile(file, Util.getDesktop()) ||
               Util.sameFile(file, Util.getDocuments()) ||
               Util.sameFile(Util.getHomeFolder(), file.getParentFile()) &&
               folders.contains(file.getName().toLowerCase());
    }

    public static boolean moveToTrash(File item) {
        if (isSystemFolder(item)) {
            return false;
        }
        if (!Flags.getFlag(Flags.DONT_USE_TRASH_BIN)) {
            File trash = findVolumeTrashOrUserTrash(item);
            if (ensureTrashBinExists(trash)) {
                if (item.isDirectory()) {
                    moveFolderToTrash(trash, item);
                } else {
                    moveFileToTrash(trash, item);
                }
            }
        }
        if (item.isDirectory()) {
            Util.rmdirs(item);
        } else if (item.exists()) {
            Util.delete(item);
        }
        return !item.exists();
    }

    private static File findVolumeTrashOrUserTrash(File item) {
        if (Util.isMac) {
            try {
                File p = item.isDirectory() ? item : item.getParentFile();
                File t = trashes.get(item);
                if (t != null && t.isDirectory()) {
                    return t;
                }
                for (; ;) {
                    if (p.isDirectory()) {
                        File trs = new File(p, ".Trashes");
                        if (trs.isDirectory()) {
                            File trashes501 = new File(trs, "501");
                            if (trashes501.isDirectory()) {
                                String s = Util.getCanonicalPath(item);
                                File test = new File(s + System.nanoTime());
                                for (int i = 0; i < 100; i++) {
                                    if (test.createNewFile()) {
                                        break;
                                    }
                                    test = new File(s + System.nanoTime());
                                }
                                File dest = new File(trashes501, test.getName());
                                if (test.renameTo(dest)) {
                                    if (Util.delete(dest)) {
                                        trashes.put(item, trashes501);
                                        return trashes501;
                                    }
                                } else {
                                    if (!Util.delete(test)) {
                                        Debug.traceln("Zipeg: error " + test + " file stuck");
                                    }
                                }
                            }
                        }
                    }
                    p = p.getParentFile();
                    if (p == null) {
                        break;
                    }
                }
            } catch (Throwable e) {
                Debug.printStackTrace(e);
            }
            File homeTrash = new File(Util.getHome(), ".Trash");
            trashes.put(item, homeTrash);
            return homeTrash;
        }
        if (Util.isWindows) {
            // TODO: Volume Recycle Bin?
        }
        return null;
    }

    private static boolean ensureTrashBinExists(File trash) {
        if (Util.isMac) {
            // see:
            // http://osxfaq.com/Tutorials/LearningCenter/HowTo/Trash/page2.ws
            // to learn how Mac users "trash" their ".Trash" folder.
            if (!trash.exists()) {
                boolean b = trash.mkdirs();
                if (!b && !trash.isDirectory()) {
                    return false; // .Trash is corrupted - just keep deleting files on overwrite
                }
            }
            if (!trash.isDirectory() || !trash.canWrite()) {
                return false; // rare situation. Might be security consideration.
                // E.g. User does not want anything to go to .Trash ever.
                // May be user or admin did chmod 000 .Trash?
            }
            assert trash.isDirectory() : "\"" + trash.getAbsolutePath() + "\" is not a directory";
        }
        return true;
    }

    private static void moveFolderToTrash(File trash, File folder) {
        File[] items = folder.listFiles();
        for (int i = 0; items != null && i < items.length; i++) {
            if (!items[i].isDirectory()) {
                moveFileToTrash(trash, items[i]);
            }
        }
        for (int i = 0; items != null && i < items.length; i++) {
            if (items[i].isDirectory()) {
                moveFolderToTrash(trash, items[i]);
            }
        }
        Util.rmdirs(folder);
    }

    private static void moveFileToTrash(File trash, File file) {
        if (Util.isMac) {
            String path = Util.getCanonicalPath(file);
            String desktop = Util.getDesktopPath();
            if (path.startsWith(desktop)) {
                path = path.substring(desktop.length());
            } else if (path.startsWith(Util.getHome())) {
                path = path.substring(Util.getHome().length());
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.length() == 0) {
                return;
            }
            File trashed = new File(trash, path);
            Util.mkdirs(new File(trashed.getParent()));
            if (trashed.exists()) {
                int i = 1;
                for (;;) {
                    File f = new File(trashed.getParent(),  "copy " + i + " " + trashed.getName());
                    if (!f.exists()) {
                        trashed = f;
                        break;
                    } else {
                        i++;
                    }
                }
            }
            assert !trashed.exists();
            try {
                Util.renameOrCopyFile(file, trashed);
            } catch (IOException iox) {
                Debug.printStackTrace(iox);
            }
        } else {
            if (Util.isWindows) {
                try {
                    Registry.moveFileToRecycleBin(file.getAbsolutePath());
                } catch (IOException x) {
                    Debug.traceln("warning: moveToRecycleBin(" + file.getAbsolutePath() +
                                  ") failed " + x.getMessage());
                    Util.delete(file);
                }
            } else {
                // TODO: does Linux has Trash can? Where is it?
                // Is it the same in different distros?
                Util.delete(file); // for now: Linux customers suppose to be tough guys
            }
        }
    }

}
