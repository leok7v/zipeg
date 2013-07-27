package com.zipeg;

import javax.swing.tree.TreeNode;
import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import java.io.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

@SuppressWarnings({"unchecked", "OctalInteger"})
public final class ArchiveProcessor implements Archive, Archiver.Progress {

    private static final String ROOT = "/";
    private static final String MACOSX = "__MACOSX/";
    private static final Integer ZERO = new Integer(0);
    private static final int YES = 0, NO = 1, ALL = 2, NONE = 3;
    private static final ArrayList emptyArrayList = new ArrayList();
    private ArchiveData data;
    private final LinkedList queue = new LinkedList();
    private final Thread thread;
    private float lastProgress;
    private long total;
    private long completed;
    private long itemTotal;
    private float lastRatio = -1;
    private boolean cancel;
    private static final Enumeration EMPTY = new Enumeration() {
                public boolean hasMoreElements() { return false; }
                public Object nextElement() { return null; }
            };

    private static final int
        S_IXUSR = 0100, // octal
        S_IXGRP = 0010,
        S_IXOTH = 0001;


    public boolean progress(String op, long value, String info) {
//      Debug.traceln(">progress(" + op + ", " + value + ", " + info);
        if (lastRatio < 0) {
            return !cancel;
        }
        if (Util.isWindows && Debug.isDebug()) {
            Registry.trace(op + "(" + value + "," + info + ")\n");
        }
        if ("setTotal".equals(op)) {
            completed += itemTotal;
            itemTotal = value;
//          Debug.traceln("itemTotal=" + itemTotal);
        } else if ("setCompleted".equals(op)) {
            long c = value + completed;
            float ratio = (float)(Math.round((c * 1000.) / total) / 1000.);
            if (ratio >= 0.01 && ratio <= 0.999 && ratio > lastRatio + 0.001) {
                Actions.postEvent("setInfo", (int)(100 * ratio) + "%");
                Actions.postEvent("setProgress", new Float(ratio));
                lastRatio = ratio;
            }
//          Debug.traceln("total=" + total + " c=" + c + " ratio=" + ((double)c / total));
        }
//      Debug.traceln("<progress cancel=" + cancel);
        return !cancel; // carry on
    }

    private static class IntValue {
        int value;
    }

    private static class LongValue {
        long value;
    }

    private static class ArchiveData {
        Archiver zipfile;
        String password;
        File cache;
        ArchiveTreeNode root;
        String[] entries;
        Map children;   // ix -> Set(ix)
        Map extensions; // ".ext" -> IntValue
        // number of children that are directories
        Map dirsCount;  // ix -> IntValue
        // number of children that are directories
        Map filesCount;  // ix -> IntValue
        // sum of size() of all non-directories children
        Map fileBytes;  // ix -> LongValue
        File parent;  // parent archive for nested archives
    }

    private ArchiveProcessor() {
        Z7.loadLibrary();
//      Debug.traceln("new ArchiveProcessor 0x" + Integer.toHexString(hashCode()));
        thread = new Thread(new Runnable() {
            public void run() {
                Debug.execute(new Runnable() {
                    public void run() {
                        doWork();
                        IdlingEventQueue.reportThreadExit();
                    }
                });
            }
        });
        thread.setName("ArchiveProcessor");
        thread.setDaemon(false);
        // wait till the thread has started and ready to recieve commands
        synchronized (queue) {
            try {
                thread.start();
                queue.wait();
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    protected void finalize() throws Throwable {
//      Debug.traceln("finalize ArchiveProcessor 0x" + Integer.toHexString(hashCode()));
        super.finalize();
    }

    public int getExtensionCounter(String dotExtension) {
        assert IdlingEventQueue.isDispatchThread();
        IntValue c = (IntValue)data.extensions.get(dotExtension);
        return c == null ? 0 : c.value;
    }

    public void close() {
        assert IdlingEventQueue.isDispatchThread();
        cancel = true;
        enqueue("stop");
        try {
            synchronized (thread) {
                thread.join();
            }
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    public boolean isOpen() {
        return data != null;
    }

    public boolean isNested() {
        return data != null && data.parent != null;
    }

    public String getParentName() {
        return data != null && data.parent != null ? Util.getCanonicalPath(data.parent) : getName();
    }

    public TreeNode getRoot() {
        assert IdlingEventQueue.isDispatchThread();
        return data == null ? null : data.root;
    }

    public String getName() {
        assert IdlingEventQueue.isDispatchThread();
        return data == null || data.zipfile == null ? "" : data.zipfile.getName();
    }

    public boolean isEncrypted() {
        assert IdlingEventQueue.isDispatchThread();
        return !(data == null || data.zipfile == null) && data.zipfile.isEncrypted();
    }

    public File getCacheDirectory() {
        assert IdlingEventQueue.isDispatchThread();
        return data.cache;
    }

    public void extract(List treeElements, File directory, boolean quit) {
        assert IdlingEventQueue.isDispatchThread();
        enqueue("extract", new Object[]{treeElements, directory, quit ? Boolean.TRUE : Boolean.FALSE});
    }

    public void extract(TreeElement element, Runnable done) {
        assert IdlingEventQueue.isDispatchThread();
        enqueue("extract", new Object[]{element, done, Boolean.FALSE});
    }

    public void extractAndOpen(TreeElement element) {
        assert IdlingEventQueue.isDispatchThread();
        File cache = createCacheDirectory();
        if (cache != null) {
            enqueue("extractAndOpen", new Object[]{element, createCacheDirectory()});
        }
    }

    private void doWork() {
        assert !IdlingEventQueue.isDispatchThread();
        try {
            synchronized (queue) {
                queue.notifyAll(); // notify constructor
            }
            boolean die = false;
            while (!die) {
                try {
                    IdlingEventQueue.reportThreadIsWorking();
                    for (;;) {
                        Object r;
                        Object[] params = null;
                        synchronized (queue) {
                            r = queue.isEmpty() ? null : queue.removeFirst();
                            if ("open".equals(r) || "extract".equals(r) || "extractAndOpen".equals(r)) {
                                params = (Object[])queue.removeFirst();
                            }
                        }
                        if (r == null) {
                            break;
                        } else if ("stop".equals(r)) {
                            die = true;
                            break;
                        } else if ("open".equals(r)) {
                            if (Memory.isMemoryLow()) {
                                Actions.reportFatalError(Memory.OUT_OF_MEMORY);
                                die = true;
                            } else {
                                File file  = (File)params[0];
                                File cache = (File)params[1];
                                File parent = (File)params[2];
                                assert cache != null;
                                load(file, cache, parent);
                                if (data == null) {
                                    die = true;
                                    break;
                                }
                            }
                        } else if ("extract".equals(r)) {
                            if (params[1] instanceof File) {
                                doExtract((List)params[0], (File)params[1], (Boolean)params[2]);
                            } else {
                                doExtract((TreeElement)params[0], (Runnable)params[1]);
                            }
                        } else if ("extractAndOpen".equals(r)) {
                            List list = new ArrayList(1);
                            list.add(params[0]);
                            doExtract(list, (File)params[1], null, true, Boolean.FALSE);
                        } else {
                            assert false : r;
                        }
                    }
                } finally {
                    IdlingEventQueue.reportThreadIsIdling();
                }
                if (!die) {
                    synchronized (queue) {
                        if (queue.isEmpty()) {
                            queue.wait();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            boolean outOfMemory = Memory.isOutOfMemory(e);
            while (e.getCause() != null) {
                e = e.getCause();
            }
            e.printStackTrace();
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            if (outOfMemory) { // do not rethrow out of memory, just:
                closeZipFile();
                Actions.reportFatalError(Memory.OUT_OF_MEMORY);
            } else {
                Actions.reportError("Error: " + error);
                final Error rethrow = new Error(e);
                EventQueue.invokeLater(new Runnable(){
                    public void run() { throw rethrow; }
                });
            }
        } finally {
            closeZipFile();
        }
    }

    private void closeZipFile() {
        assert !IdlingEventQueue.isDispatchThread() : "must be called from background thread only";
//      Debug.traceln("closeZipFile ArchiveProcessor 0x" + Integer.toHexString(this.hashCode()));
        if (data != null) {
            File zip = data.zipfile != null ? new File(data.zipfile.getName()) : null;
            if (data.zipfile != null) {
                try {
                    data.zipfile.close();
                } catch (IOException e) {
                    Actions.reportError("failed to close " + e.getMessage());
                }
            }
            if (isNested() && zip != null) {
                boolean b = Util.delete(zip); // after close
//              Debug.traceln("deleted nested archive: (" + b + ") " + zip);
            }
            if (data.cache != null) {
                Util.rmdirs(data.cache);
            }
            data.root = null;
            data.zipfile = null;
            data = null;
        }
    }

    private void enqueue(Object o) {
        synchronized (queue) {
            queue.add(o);
            queue.notifyAll();
        }
    }

    private void enqueue(Object o1, Object o2) {
        synchronized (queue) {
            queue.add(o1);
            queue.add(o2);
            queue.notifyAll();
        }
    }

    public static void open(File file) {
        open(file, null);
    }

    private static void open(File file, File parent) {
        File cache = createCacheDirectory();
        if (cache != null) {
            open(file, cache, parent);
        }
    }

    private static void open(File file, File cache, File parent) {
        assert cache != null;
        new ArchiveProcessor().enqueue("open", new Object[]{file, cache, parent});
    }

    private void setProgress(int i, int max) {
        assert !IdlingEventQueue.isDispatchThread();
        assert i <= max;
        float f = i / (float)max;
        if (f > lastProgress + 0.01) {
            Actions.postEvent("setProgress", new Float(f));
            lastProgress = f;
        }
//      Util.sleep(500); // DEBUG
    }

    private static File createCacheDirectory() {
        try {
            return _createCacheDirectory();
        } catch (Throwable x) {
            Actions.reportError(x.getMessage());
            Actions.postEvent("setProgress", new Float(0)); // in case of exceptions
            Actions.postEvent("setStatus", "");
            Actions.postEvent("setInfo", "");
            return null;
        }
    }

    private static File _createCacheDirectory() throws IOException {
        assert IdlingEventQueue.isDispatchThread();
        File cache = Util.getCacheDirectory(true);
        int retry = 32;
        while (retry > 0) {
            File cacheDirectory = new File(cache, Util.luid());
            if (!cacheDirectory.exists()) {
                Util.mkdirs(cacheDirectory);
                if (cacheDirectory.isDirectory()) {
                    return cacheDirectory;
                }
            }
            retry--;
        }
        File f = File.createTempFile("zipeg", "cache");
        if (f.delete()) {
            Util.mkdirs(f);
        }
        if (f.isDirectory()) {
            return f;
        }
        throw new IOException("failed to create cache folder at " + cache);
    }

    private void load(File file, File cache, File parentArchive) {
        assert !IdlingEventQueue.isDispatchThread();
        lastProgress = 0;
        Actions.postEvent("setStatus", "opening archive: " + file.getName() + " ...");
        Actions.postEvent("setProgress", new Float(0.01));
        long time = System.currentTimeMillis();
        int entryCount = 0;
        try {
            data = new ArchiveData();
            data.zipfile = new Z7(file);
            data.parent = parentArchive;
            data.cache = cache;
            if (data.zipfile.isDirEncrypted() || data.zipfile.isEncrypted()) {
                data.password = askPassword(file.getName());
                if (data.zipfile.isDirEncrypted()) {
                    data.zipfile.close();
                    data.zipfile = new Z7(file, data.password);
                }
            }
            entryCount = data.zipfile.size();
            int m = (entryCount + 1) * 2;
            int i = 1;
            int maxLength = ROOT.length();
            data.entries = new String[entryCount + 1];
            data.entries[0] = ROOT; // root
            // assume about 10 files per directory (experimental result)
            // and double the hashmap size to minimize collisions and rehash
            HashMap dirs = new HashMap(Math.min(10, entryCount / 5));
            dirs.put(ROOT, ZERO);
            int r = 0; // number of resource forks in archive
            for (Iterator e = data.zipfile.getEntries(); e.hasNext();) {
                try {
                    ZipEntry entry = (ZipEntry)e.next();
                    String name = entry != null ? entry.getName() : null;
                    // the is PKZIP "optimization" for Zero length files
                    // that stores the names but not entries
                    if (entry != null && name != null && data.zipfile.getEntry(name) != null) {
                        int n = name.length();
                        maxLength = Math.max(maxLength, n);
                        boolean dir = name.charAt(n - 1) == '/';
                        boolean skip = isAppleDouble(name) || isDSStore(name);
                        if (!skip) {
                            if (Util.isMac) {
                                assert !name.startsWith(MACOSX);
                            }
                            if (dir && dirs.containsKey(name)) {
                                // such zip files happened to be in the field.
                                // skip double entry for the directory.
                            }
                            else {
                                data.entries[i] = name;
                                if (dir) {
                                    dirs.put(name, new Integer(i));
                                }
                                i++;
                                setProgress(i, m);
                            }
                        } else {
                            r++;
                        }
                    }
                } catch (InternalError ie) {
                    break;
                }
            }
            m -= r;
            int N = i;
            char[] buff = new char[maxLength];
            // some archives may have parent directories missing
            int j = i;
            Map extra = new HashMap();
            for (int k = 0; k < N; k++) {
                String name = data.entries[k];
                int n = name.length();
                name.getChars(0, n, buff, 0);
                String parent = getParent(name, buff, n);
                while (!ROOT.equals(parent) && !extra.containsKey(parent) &&
                                               !dirs.containsKey(parent)) {
                    extra.put(parent, new Integer(j));
                    j++;
                    parent = getParent(parent, buff, parent.length());
                }
            }
            if (extra.size() > 0 || N != data.entries.length) {
                String[] entries = new String[N + extra.size()];
                System.arraycopy(data.entries, 0, entries, 0, N);
                for (Iterator x = extra.entrySet().iterator(); x.hasNext();) {
                    Map.Entry e = (Map.Entry)x.next();
                    String name = (String)e.getKey();
                    Integer ix = (Integer)e.getValue();
                    assert entries[ix.intValue()] == null;
                    entries[ix.intValue()] = name;
                    assert !dirs.containsKey(name);
                    dirs.put(name, ix);
                }
                data.entries = entries;
                m += extra.size();
            }
            ArrayList sorted = new ArrayList(dirs.size());
            sorted.addAll(dirs.keySet());
            Collections.sort(sorted);
            int d32 = dirs.size() * 3 / 2; // 1.5 of dirs.size()
            data.children = new HashMap(d32);
            data.dirsCount = new HashMap(d32);
            data.fileBytes = new HashMap(d32);
            data.filesCount = new HashMap(d32);
            data.extensions = new HashMap(1024);
            for (int ix = 1; ix < data.entries.length; ix++) {
                Integer index = new Integer(ix);
                String name = data.entries[ix];
                int n = name.length();
                boolean dir = name.charAt(n - 1) == '/';
                name.getChars(0, n, buff, 0);
                String parent = getParent(name, buff, n);
                Integer pix = (Integer)dirs.get(parent);
                assert pix != null : "parent " + parent + " is missing";
                Set childs = (Set)data.children.get(pix);
                if (childs == null) {
                    data.children.put(pix, childs = new HashSet());
                }
                assert !childs.contains(index);
                childs.add(index);
                if (!dir) {
                    ZipEntry entry = data.zipfile.getEntry(name);
                    LongValue sum = (LongValue)data.fileBytes.get(pix);
                    if (sum == null) {
                        data.fileBytes.put(pix, sum = new LongValue());
                    }
                    sum.value += entry.getSize();
                } else {
                    IntValue cnt = (IntValue)data.dirsCount.get(pix);
                    if (cnt == null) {
                        data.dirsCount.put(pix, cnt = new IntValue());
                    }
                    cnt.value++;
                }
                String ext = getExtension(name, buff, n);
                if (ext != null) {
                    IntValue c = (IntValue)data.extensions.get(ext);
                    if (c == null) {
                        data.extensions.put(ext, c = new IntValue());
                    }
                    c.value++;
                }
                i++;
                setProgress(i, m);
            }
            // TODO: need to account for resource forks
            calculateCumulateCounts(ZERO);
            data.root = new ArchiveTreeNode(null, ZERO);
            data.root.fillCache();
            data.root.path = data.zipfile.getName();
            data.root.name = new File(data.root.path).getName();
            Actions.postEvent("setProgress", new Float(0)); // must be posted before archiveOpened
            Actions.postEvent("archiveOpened", this);
        } catch (IOException io) {
            String msg = io.getMessage();
            if (data != null && data.password != null) {
                msg = msg == null ? "invalid password" : (msg + " or invalid password");
            }
            Actions.reportError("failed to open archive\n\"" + file.getName() + "\"\n" + msg);
            closeZipFile();
        } catch (OutOfMemoryError oom) {
            Memory.releaseSafetyPool();
            closeZipFile();
            Actions.reportFatalError("failed to open archive\n\"" +
                    file.getName() + "\"\n" +
                    "Archive is too big (" + entryCount + " items).\n" +
                    Memory.OUT_OF_MEMORY);
        } finally {
            Actions.postEvent("setProgress", new Float(0)); // in case of exceptions
            Actions.postEvent("setStatus", "");
            Actions.postEvent("setInfo", "");
            if (data != null && data.zipfile != null) {
//              time = System.currentTimeMillis() - time;
//              Debug.traceln("entryCount " + entryCount + " time " + time + " milli");
            }
        }
    }

    private static boolean isAppleDouble(String name) {
        if (name.startsWith(MACOSX)) return true;
        int n = name.length();
        if (name.charAt(n - 1) == '/') return false;
        int k = n - 1;
        while (k >= 0 && name.charAt(k) != '/') {
            k--;
        }
        return k + 2 < n && name.charAt(k + 1) == '.' && name.charAt(k + 2) == '_';
    }

    private static boolean isDSStore(String name) {
        return !Util.isMac && (name.endsWith("/.DS_Store") || ".DS_Store".equals(name));
    }

    private long getFileBytes(Integer ix) {
        if (data == null) {
            return 0;
        }
        LongValue bytes = (LongValue)data.fileBytes.get(ix);
        return bytes == null ? 0 : bytes.value;
    }

    private int getDirsCount(Integer ix) {
        if (data == null) {
            return 0;
        }
        IntValue c = (IntValue)data.dirsCount.get(ix);
        return c == null ? 0 : c.value;
    }

    private int getFilesCount(Integer ix) {
        if (data == null) {
            return 0;
        }
        IntValue c = (IntValue)data.filesCount.get(ix);
        if (c != null) return c.value;
        Set s = (Set)data.children.get(ix);
        return s == null ? 0 : s.size() - getDirsCount(ix);
    }

    private void calculateCumulateCounts(Integer ix) {
        if (data == null) {
            return;
        }
        Set c = (Set)data.children.get(ix);
        if (c == null) {
            return;
        }
        IntValue files = (IntValue)data.filesCount.get(ix);
        if (files == null) {
            long s = 0;
            int f = getFilesCount(ix);
            int d = 0;
            for (Iterator i = c.iterator(); i.hasNext();) {
                Integer cix = (Integer)i.next();
                calculateCumulateCounts(cix);
                s += getFileBytes(cix);
                f += getFilesCount(cix);
                d += getDirsCount(cix);
            }
            LongValue bytes = (LongValue)data.fileBytes.get(ix);
            if (bytes == null) {
                data.fileBytes.put(ix, bytes = new LongValue());
            }
            bytes.value += s;
            IntValue dirs = (IntValue)data.dirsCount.get(ix);
            if (dirs == null) {
                data.dirsCount.put(ix, dirs = new IntValue());
            }
            dirs.value += d;
            data.filesCount.put(ix, files = new IntValue());
            files.value += f;
        }
        assert data.fileBytes.get(ix) instanceof LongValue;
        assert data.filesCount.get(ix) instanceof IntValue;
        assert data.dirsCount.get(ix) instanceof IntValue;
    }

    private String getParent(String name, char[] buff, int n) {
        for (int i = n - 2; i >= 0; i--) {
            if (buff[i] == '/') {
                return name.substring(0, i + 1);
            }
        }
        return ROOT;
    }

    private String getExtension(String name, char[] buff, int n) {
        int m = Math.max(0, n - 8); // maximum extension length 8
        for (int i = n - 1; i >= m; i--) {
            if (buff[i] == '/') {
                return null;
            } else if (buff[i] == '.') {
                return name.substring(i);
            }
        }
        return "";
    }

    private void doExtract(final TreeElement e, Runnable done) {
        assert !IdlingEventQueue.isDispatchThread();
        assert e != null;
        if (Memory.isMemoryLow()) {
            done.run();
        } else {
            List list = new LinkedList() {{ add(e); }};
            doExtract(list, data.cache, done, false, Boolean.FALSE);
        }
    }

    private boolean intoCache(File dir) {
        assert dir != null : "dir is null";
        assert data != null : "data is null";
        assert data.cache != null : "data.cache is null";
        String dcp = Util.getCanonicalPath(dir);
        String ccp = Util.getCanonicalPath(data.cache);
        return dcp.startsWith(ccp);
    }

    private void doExtract(List list, File dir, Boolean quit) {
        assert !intoCache(dir) :
            "\"" + dir + "\" must not start with \"" + data.cache + "\"";
        if (Memory.isMemoryLow()) {
            Actions.reportError("Not enough memory");
        } else {
            doExtract(list, dir, null, false, quit);
        }
    }

    private static String askPassword(final String name) throws IOException {
        final String[] r = new String[1];
        try {
            IdlingEventQueue.invokeAndWait(new Runnable(){
                public void run() {
                    r[0] = MainFrame.getPassword(name);
                }
            });
        } catch (InterruptedException e) {
            r[0] = null;
        } catch (InvocationTargetException e) {
            r[0] = null;
        }
        if (r[0] == null || r[0].length() == 0) {
            throw new IOException("password is required to open archive");
        }
        return r[0];
    }

    private Iterator iterateList(final List list) {

        return new Iterator() {

            final Iterator i = list.iterator();

            public boolean hasNext() {
                return i.hasNext();
            }

            public Object next() {
                return ((ArchiveTreeNode)i.next()).entry;
            }

            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        };
    }

    private Iterator iterateAll() {

        return new Iterator() {

            Iterator e = data.zipfile.getEntries();
            ZipEntry next = e.hasNext() ? (ZipEntry)e.next() : null;
            {
                skipDirs();
            }

            public boolean hasNext() {
                return next != null;
            }

            public Object next() {
                Object r = next;
                next = e.hasNext() ? (ZipEntry)e.next() : null;
                skipDirs();
                return r;
            }

            public void remove() {
                throw new UnsupportedOperationException("remove");
            }

            private void skipDirs() {
                while (next != null && (next.isDirectory() || isAppleDouble(next.getName()))) {
                    next = e.hasNext() ? (ZipEntry)e.next() : null;
                }
            }

        };
    }

    private Iterator iterateAllDirs() {

        return new Iterator() {

            Iterator e = data.zipfile.getEntries();
            ZipEntry next = e.hasNext() ? (ZipEntry)e.next() : null;
            {
                skipNoneDirs();
            }

            public boolean hasNext() {
                return next != null;
            }

            public Object next() {
                Object r = next;
                next = e.hasNext() ? (ZipEntry)e.next() : null;
                skipNoneDirs();
                return r;
            }

            public void remove() {
                throw new UnsupportedOperationException("remove");
            }

            private void skipNoneDirs() {
                while (next != null && (!next.isDirectory() || isAppleDouble(next.getName()))) {
                    next = e.hasNext() ? (ZipEntry)e.next() : null;
                }
            }

        };
    }

    private void doneExtract(String message, String error, Runnable done, Boolean quit) {
        if (done == null) {
            message = message == null ? "" : message;
            Actions.postEvent("setProgress", new Float(0));
            Actions.postEvent("setStatus", "");
            Actions.postEvent("setInfo", "");
            Actions.postEvent("extractionCompleted", new Object[]{error, quit});
            Actions.postEvent("setMessage", message);
        } else {
            done.run();
        }
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o != null && o instanceof Integer ? ((Integer)o).intValue() : 0;
    }

    private void doExtract(List list, final File dir, Runnable done, boolean open, Boolean quit) {
        final String TEMP = ".tmp.zipeg.";
        File tmp = dir;
        boolean intoCache = intoCache(dir);
        if (!intoCache) {
            long nano = System.nanoTime();
            tmp = new File(dir, TEMP  + nano);
            int i = 1;
            while (tmp.isDirectory() && i < 99) {
                tmp = new File(dir, TEMP + (nano + i));
                i++;
            }
            if (tmp.isDirectory()) {
//              Debug.traceln("failed to create working folder " + tmp);
                String error = "Unable to write to destination. Check permissions.";
                String message = "error: Unable to write to destination folder\n" +
                        Util.getCanonicalPath(dir) + "\nCheck permissions";
                Actions.reportError(error);
                doneExtract(message, error, done, quit);
                return;
            }
            Util.mkdirs(tmp);
            if (!tmp.isDirectory()) {
//              Debug.traceln("failed to create working folder at " + tmp);
                String error = "Unable to write to destination. Check permissions.";
                String message = "error: Unable to write to destination folder<br>" +
                        Util.getCanonicalPath(dir) + "<br>Check permissions.";
                Actions.reportError(error);
                doneExtract(message, error, done, quit);
                return;
            }
        }
        Map<String, Object> result = doExtract(list, dir, tmp, done, open, quit);
        String message = (String)result.get("message");
        String error   = (String)result.get("error");
        int resources = getInt(result, "resources");
        int extracted = getInt(result, "extracted");
        if (error == null && resources > 0) {
            try {
                Process p = Runtime.getRuntime().exec(
                            new String[]
                            {"/System/Library/CoreServices/FixupResourceForks",
                             "-q", Util.getCanonicalPath(dir)},
                            Util.getEnvFilterOutMacCocoaCFProcessPath());
                int ec = p.waitFor();
                if (ec != 0) {
                    // happens with Aspire-Brun-Sweeti_4.94x10.5-A Folder.zip
                    // ._Aspire-Brun-whtTank_4.94x10.5-A" (-2123)
                    // returning 181
/*                  TODO: this is usually not critical - need to convert it into warning...
                    message = "error: fixing up resource forks for " +
                               Util.getCanonicalPath(dir) + " error code " + ec;
*/
                    extracted -= resources;
                }
            } catch (Throwable e) {
                Debug.printStackTrace(e);
                error = e.getMessage();
                message = "error: " + error;
            }
        }
        if (!intoCache) {
/*
            try {
                int lastPrompt = 0;
                File[] all = tmp.listFiles();
                for (int k = 0; k < all.length; k++) {
                    File dest = new File(dir, all[k].getName());
                    if (dest.exists()) {
                        // TODO: merge directories
                        if (!TrashBin.moveToTrash(dest)) {
                            throw new IOException("\"" + dest.getName() + "\" " +
                                    "is locked or system folder.<br>" +
                                    "Try another destination.");
                        }
                    }
                    if (!all[k].renameTo(dest)) {
                        throw new IOException("failed to unpack file " + dest.getName());
                    }
                }
            } catch (Throwable e) {
                Debug.printStackTrace(e);
                error = e.getMessage();
                message = "error: " + error;
            }
*/
            Util.rmdirs(tmp);
        }
        if (extracted > 0 && (error == null || error.length() == 0) &&
                (message == null || message.length() == 0)) {
            message = "Successfully Extracted " + Util.plural(extracted, "item");
        }
        if (open && error == null) {
            assert done == null;
            // IMPORTANT: Do not setProgress to 0.0 and do NOT postEvent("extractionCompleted")
            // if the QuitAfterExtract option is set this will lead to application exiting.
            Actions.postEvent("setProgress", new Float(0.5));
            Actions.postEvent("setStatus", "");
            Actions.postEvent("setInfo", "");
            ArchiveTreeNode node = (ArchiveTreeNode)list.get(0);
            final File file = new File(dir, node.entry.getName());
            final File parent = isNested() ? data.parent : new File(data.zipfile.getName());
            try {
                EventQueue.invokeLater(new Runnable() {
                    public void run() { open(file, parent); }
                });
            } catch (Exception e) {
                // ignore because cannot throw here anymore
            }
        } else {
            doneExtract(message, error, done, quit);
        }
    }

    private Map<String, Object> doExtract(List list, final File dir, final File tmp,
            Runnable done, boolean open, Boolean quit) {
        Map<String, Object> result = new HashMap<String, Object>();
        assert !IdlingEventQueue.isDispatchThread();
        // expect no more than 50% of symlinks (they gotta link to something inside archive, right?)
        ArrayList symlinks = new ArrayList(list != null ? list.size() / 2 : data.entries.length / 2);
        Set set = null;
        if (list != null) {
            set = new HashSet(list.size() * 2);
            for (Iterator i = list.iterator(); i.hasNext(); ) {
                TreeElement e = (TreeElement)i.next();
                assert !e.isDirectory() : e.getFile();
                set.add(e.getFile());
            }
        }
        int n = list == null ? data.zipfile.size() : set.size();
        long time = System.currentTimeMillis();
        File created = null; // last created file;
        String filename = data.zipfile.getName(); // for error reporting
        int[] lastPropmt = new int[]{done == null ? YES : ALL};
        int extracted = 0;
        int resources = 0;
        try {
            if (done == null) {
                Actions.postEvent("setStatus", "extracting files...");
                Actions.postEvent("setInfo", "");
                Actions.postEvent("setProgress", new Float(0.01));
            }
            int i = 0;

            total = 0;
            Set s = set == null ? null  : new HashSet(set);
            Iterator e = list != null && list.size() < data.entries.length / 2 ?
                         iterateList(list) : iterateAll();
            while (e.hasNext() && (s == null || !s.isEmpty())) {
                ZipEntry entry = (ZipEntry)e.next();
                assert !entry.getName().startsWith(MACOSX) : entry.getName();
                assert !entry.isDirectory() : entry.getName();
                boolean unpack = s == null || s.contains(entry.getName());
                if (s != null) {
                    s.remove(entry.getName());
                }
                if (unpack) {
                    total += entry.getSize();
                }
            }
//          Debug.traceln("total=" + total);
            lastRatio = done == null ? 0 : -1;
            completed = 0;
            float last = 0;
            e = list != null && list.size() < data.entries.length / 2 ?
                         iterateList(list) : iterateAll();
            while (e.hasNext() && (set == null || !set.isEmpty())) {
                ZipEntry entry = (ZipEntry)e.next();
                assert !entry.getName().startsWith(MACOSX) : entry.getName();
                assert !entry.isDirectory() : entry.getName();
                boolean unpack = set == null || set.contains(entry.getName());
                if (set != null) {
                    set.remove(entry.getName());
                }
                if (unpack && !entry.isDirectory() && !isSymLinkEntry(entry)) {
                    i++;
                    float progress = (float)i / n;
                    if (done == null && progress > last + 0.01) {
                        Actions.postEvent("setStatus", entry.getName());
                        last = progress;
                    }
                    final File file = new File(dir, entry.getName());
                    final File temp = new File(tmp, entry.getName());
                    filename = Util.getCanonicalPath(file);
                    if (done != null) {
                        created = file;
                    }
                    assert !file.getName().startsWith("._") : file.getName();
                    ZipEntry res = null;
                    if (Util.isMac) {
                        res = getResourceFork(entry.getName());
                    }
                    // use cached file as a source only if:
                    // 1. No resource fork or dir attrs on OSX
                    // 2. It is not extraction into cache.
                    File cached = done == null && res == null ?
                                  new File(data.cache, entry.getName()) : null;
                    if (filename.startsWith(Util.getCanonicalPath(data.cache))) {
                        assert cached == null : "paranoia";
                    }
                    extracted += unzip(entry, cached, file, temp, lastPropmt);
                    if (lastPropmt[0] == NONE) {
                        break;
                    }
                    if (res != null && lastPropmt[0] != NO) {
                        String rpath = res.getName();
                        if (rpath.startsWith(MACOSX)) {
                            rpath = rpath.substring(MACOSX.length());
                        }
                        File rfile = new File(dir, rpath);
                        File rtemp = new File(tmp, rpath);
                        int a[] = new int[1]; // unpack resource silently:
                        a[0] = lastPropmt[0] == YES ? ALL : lastPropmt[0];
                        resources += unzip(res, null, rfile, rtemp, a);
                    }
                } else if (unpack && isSymLinkEntry(entry)) {
                    symlinks.add(entry);
                }
            }
            // process empty directories:
            if (list == null) {
                for (Iterator dirs = iterateAllDirs(); dirs.hasNext(); ) {
                    ZipEntry entry = (ZipEntry)dirs.next();
                    final File file = new File(tmp, entry.getName());
                    try {
                        data.zipfile.extractEntry(entry, file, data.password, null);
                    } catch (IOException ignore) {
                        // ignore
                    }
                    if (!file.exists()) {
                        filename = Util.getCanonicalPath(file);
                        Util.mkdirs(new File(filename));
                    }
                }
            }
            for (Iterator sl = symlinks.iterator(); sl.hasNext(); ) {
                Z7.Zip7Entry entry = (Z7.Zip7Entry)sl.next();
                assert isSymLinkEntry(entry) : "expected symlink: " + entry.getName();
                final File file = new File(dir, entry.getName());
                final File temp = new File(tmp, entry.getName());
                extracted += unzip(entry, null, file, temp, lastPropmt);
                if (lastPropmt[0] == NONE) {
                    return result;
                }
            }
            result.put("extracted", new Integer(extracted));
            result.put("resources", new Integer(resources));
        } catch (IOException io) {
            if (done == null) { // all errors are silently ignored for the cache extraction
                String error = cancel ? "canceled by user request" : io.getMessage();
                if (lastPropmt[0] != NONE) {
                    String fname = new File(filename).getName();
                    String lc = error.toLowerCase();
                    if (lc.contains(fname.toLowerCase())) {
                        if (!lc.contains("item") || !lc.contains("extract")) {
                            error = "failed to extract item\n" + error;
                        }
                    } else {
                        error = "failed to extract item\n\"" + fname + "\"\n" + error;
                    }
                    Actions.reportError(error);
                    String message = "error: " + error;
                    if (extracted > 0) {
                        message += "\n<br><font color=green>extracted " +
                                    Util.plural(extracted, "item") + ".</font>";
                    }
                    result.put("error", error);
                    result.put("message", message);
                } else {
                    result.put("error", error);
                    result.put("message", "error: " + error);
                }
            } else { // done != null file was requested for the cache
                if (created != null && created.exists()) {
                    Util.delete(created);
                }
            }
        }
        return result;
    }

    private void ensureParent(File f) throws IOException {
        File fp = f.getParentFile();
        if (fp != null && !fp.isDirectory()) {
            Util.mkdirs(fp);
            if (!fp.isDirectory()) { // mkdirs do not throw IOException
                throw new IOException("failed to create folder \"" + fp + "\"");
            }
        }
    }

    private int unzip(ZipEntry entry, File cached, File file, File temp, int[] lastPropmt)
            throws IOException {
        if (lastPropmt[0] != ALL && file.exists()) {
            lastPropmt[0] = lastPropmt[0] != ALL ? askOverwrite(file) : ALL;
            if (lastPropmt[0] == NONE) {
                throw new IOException("canceled by user request");
            }
            if (lastPropmt[0] == NO) {
                return 0;
            }
        }
        boolean symlink = isSymLinkEntry(entry);
        if (temp.exists()) {
            boolean d = Util.delete(temp);
            if (!d) {
                throw new IOException("file \"" + temp.getName() + "\" is locked.");
            }
        }
        ensureParent(temp);
        ensureParent(file);
        long attributes = 0;
        if (entry instanceof Z7.Zip7Entry) {
            Z7.Zip7Entry z7 = (Z7.Zip7Entry)entry;
            attributes = z7.getAttributes();
        }
        long st_mode = attributes >>> 16;
        boolean executable = Util.isMac && !entry.isDirectory() && (st_mode & (S_IXUSR|S_IXGRP|S_IXOTH)) != 0;
        if (Debug.isDebug() && executable) {
            Debug.traceln("executable: " + entry.getName());
        }
        if (!symlink && !executable && cached != null && !cached.isDirectory() && cached.canRead()) {
            Util.copyFile(cached, temp);
        } else if (entry instanceof Z7.Zip7Entry) {
            Z7.Zip7Entry z7e = (Z7.Zip7Entry)entry;
            data.zipfile.extractEntry(z7e, temp, z7e.isEncrypted() ? data.password : null, this);
        } else {
            data.zipfile.extractEntry(entry, temp, data.password, this);
        }
        if (entry.getTime() > 0 && !symlink) {
            Util.setLastModified(temp, entry.getTime());
        }
        if (!file.equals(temp)) {
            if (file.exists() && !TrashBin.moveToTrash(file)) {
                throw new IOException("\"" + file.getPath() + "\" " +
                        "is locked or system folder.<br>" +
                        "Try another destination.");
            }
            if (!temp.renameTo(file)) {
                throw new IOException("\"" + file.getPath() + "\" " +
                        " failed to create file.");
            }
        }
        return 1;
    }

    private ZipEntry getResourceFork(String path) {
        ZipEntry ze = data.zipfile.getEntry(formResourcePath(path, true));
        return ze != null ? ze : data.zipfile.getEntry(formResourcePath(path, false));
    }

    private String formResourcePath(String path, boolean prefix) {
        int ix = path.lastIndexOf('/');
        if (ix < 0 || ix == path.length() - 1) {
            path = "._" + path;
        } else {
            path = path.substring(0, ix + 1) + "._" + path.substring(ix + 1);
        }
        return prefix ? MACOSX + path : path;
    }

    private static String breakLongPathInTwo(String path) {
        if (path.length() < 50) {
            return path;
        }
        char sep = Util.isWindows ? '\\' : '/';
        int i = path.length() / 2;
        for (int j = 0; j < i / 2; j++) {
            int k = -1;
            if (path.charAt(i - j) == sep) {
                k = i - j;
            } else if (path.charAt(i + j) == sep) {
                k = i + j;
            }
            if (k > 0) {
                return breakLongPathInTwo(path.substring(0, k + 1)) + "<br>" +
                       breakLongPathInTwo(path.substring(k + 1));
            }
        }
        return path;
    }

    private static int askOverwrite(final File file) {
        assert !IdlingEventQueue.isDispatchThread();
        final int[] result = new int[1];
        try {
            EventQueue.invokeAndWait(new Runnable(){
                public void run() {
                    assert IdlingEventQueue.isDispatchThread();
                    Object[] options = {" Yes ", "  No  ", " All ", "None"};
                    boolean folder = file.isDirectory();
                    String f = folder ? "<b>folder</b>" : "file";
                    String warning = "<font color='red'>" +
                        "<i>(existing contents of the folder will be moved to " +
                        (Util.isMac ? "Trash Bin" : "Recycle Bin") + ")</i></font><br><br>" +
                        "If this is <b>not</b> what you want - choose <b>None</b> to cancel, extract to<br>" +
                        "different destination and merge content manually.<br>";
                    result[0] = MessageBox.show(
                        "<html><body>Do you want to overwrite existing " + f + "?<br><br><b>" +
                        breakLongPathInTwo(Util.getCanonicalPath(file)) + "</b><br>" +
                        (folder ? warning : "") +
                        "</body></html>",
                        "Zipeg: Overwrite",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        options, options[ALL]);
                }
            });
        } catch (InterruptedException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new Error(e);
        }
        return result[0];
    }

    private class ArchiveTreeNode implements TreeNode, TreeElement {

        private final ArchiveTreeNode parent;
        private final Integer ix;
        private ArrayList all;
        private ArrayList dirs;
        private ZipEntry entry;
        private ZipEntry res; // resource entry on MacOSX
        private String path;
        private String name;

        ArchiveTreeNode(ArchiveTreeNode p, Integer i) {
            parent = p;
            assert i != null;
            ix = i;
        }

        public int getChildCount() {
            fillCache();
            return dirs == null ? 0 : dirs.size();
        }

        public TreeNode getChildAt(int ix) {
            fillCache();
            return dirs == null ? null : (TreeNode)dirs.get(ix);
        }

        public TreeNode getParent() {
            return parent;
        }

        public boolean isDirectory() {
            fillCache();
            return path != null && path.charAt(path.length() - 1) == '/';
        }

        public int getIndex(TreeNode node) {
            fillCache();
            int ix = 0;
            for (Iterator i = dirs.iterator(); i.hasNext();) {
                if (node == i.next()) {
                    return ix;
                }
                ix++;
            }
            throw new Error("not found");
        }

        public boolean getAllowsChildren() {
            fillCache();
            return !isLeaf();
        }

        public boolean isLeaf() {
            fillCache();
            return dirs == null || dirs.size() == 0;
        }

        public Enumeration children() {
            fillCache();
            return dirs == null ? EMPTY : new Enumeration(){

                private Iterator i = dirs.iterator();

                public boolean hasMoreElements() {
                    return i.hasNext();
                }

                public Object nextElement() {
                    return i.next();
                }

            };
        }

        public String toString() {
            fillCache();
            return name;
        }

        public long getSize() {
            fillCache();
            return entry == null ? 0 : entry.getSize();
        }

        public long getResourceForkSize() {
            fillCache();
            return res == null ? 0 : res.getSize();
        }

        public long getCompressedSize() {
            fillCache();
            return entry == null ? 0 : entry.getCompressedSize();
        }

        public long getResourceForkCompressedSize() {
            fillCache();
            return res == null ? 0 : res.getCompressedSize();
        }

        public int getDescendantFileCount() {
            return getFilesCount(ix);
        }

        public int getDescendantDirectoryCount() {
            return getDirsCount(ix);
        }

        public long getDescendantSize() {
            return getFileBytes(ix);
        }

        public void collectDescendants(List list) {
            if (!isDirectory()) {
                list.add(this);
            } else {
                for (Iterator i = getChildren(); i.hasNext();) {
                    TreeElement c = (TreeElement)i.next();
                    c.collectDescendants(list);
                }
            }
        }

        public String getName() {
            fillCache();
            return name;
        }

        public boolean isEncrypted() {
            fillCache();
            return entry != null && entry instanceof Z7.Zip7Entry && ((Z7.Zip7Entry)entry).isEncrypted();
        }

        public String getError() {
            fillCache();
            return entry != null && entry instanceof Z7.Zip7Entry ? ((Z7.Zip7Entry)entry).getError() : null;
        }

        public String getFile() {
            fillCache();
            return path;
        }

        public long getTime() {
            fillCache();
            return entry == null ? 0 : entry.getTime();
        }

        public String getComment() {
            return entry == null ? "" : entry.getComment();
        }

        public int getChildrenCount() {
            fillCache();
            return all == null ? 0 : all.size();
        }

        public Iterator getChildren() {
            fillCache();
            return all == null ? new LinkedList().iterator() :
                    Collections.unmodifiableCollection(all).iterator();
        }

        public boolean isSymLink() {
            fillCache();
            return isSymLinkEntry(entry);
        }

        private void fillCache() {
            if (dirs != null) {
                return;
            }
            if (data == null || data.entries == null) {
                return;
            }
            path = data.entries[ix.intValue()];
            String n = path;
            boolean dir = n.charAt(n.length() - 1) == '/';
            if (dir) {
                n = n.substring(0, n.length() - 1);
            }
            int ps = n.lastIndexOf('/');
            name = ps >= 0 ? n.substring(ps + 1) : n;
            entry = data.zipfile.getEntry(path);
            res = Util.isMac ? getResourceFork(path) : null;
            if (entry == null) { // happens for extra parents
                // e.g. "memtest86-3.1a.iso" has no entries for "/" and "[BOOT]"
                entry = new ZipEntry(path);
            }
            Set s = (Set)data.children.get(ix);
            ArrayList ids;
            if (s == null) {
                all  = emptyArrayList;
                dirs = emptyArrayList;
            } else {
                ids = new ArrayList(s);
                Collections.sort(ids, new Comparator(){
                    public int compare(Object o1, Object o2) {
                        String s1 = data.entries[((Integer)o1).intValue()];
                        String s2 = data.entries[((Integer)o2).intValue()];
                        return s1.compareTo(s2);
                    }
                });
                int d = 0;
                all = new ArrayList(ids.size());
                for (Iterator i = ids.iterator(); i.hasNext();) {
                    Integer cix = (Integer)i.next();
                    ArchiveTreeNode tn = new ArchiveTreeNode(this, cix);
                    all.add(tn);
                    String path = data.entries[cix.intValue()];
                    if (path.charAt(path.length() - 1) == '/') d++;
                }
                dirs = new ArrayList(d);
                for (Iterator i = all.iterator(); i.hasNext();) {
                    ArchiveTreeNode c = (ArchiveTreeNode)i.next();
                    String path = data.entries[c.ix.intValue()];
                    if (path.charAt(path.length() - 1) == '/') {
                        dirs.add(c);
                    }
                }
            }
        }

    }

    private static boolean isSymLinkEntry(ZipEntry e) {
        if (e == null) {
            return false;
        } else if (!(e instanceof Z7.Zip7Entry)) {
            return false;
        } else {
            Z7.Zip7Entry z7e = (Z7.Zip7Entry)e;
            long xa = (z7e.getAttributes() >>> 16) ;
            /*  S_ISLNK(m)  ((m & 0170000) == 0120000)  // BSD symbolic link
                S_IFLNK 	              0120000 	       symbolic link
            */
            // noinspection OctalInteger
            return (xa & 0170000) == 0120000;
        }
    }

}
