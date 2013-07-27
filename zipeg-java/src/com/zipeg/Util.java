package com.zipeg;


import javax.swing.*;
import java.io.*;
import com.sun.management.*;
import java.lang.management.ManagementFactory;
import java.text.*;
import java.util.*;
import java.util.List;
import java.awt.event.*;
import java.awt.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.SecureRandom;
import java.nio.channels.FileChannel;
import javax.swing.Timer;

@SuppressWarnings("UnusedDeclaration")
public final class Util {

    private static long startupTime = System.nanoTime();
    public static final boolean isMac = mac.isMac;
    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0;
    public static final boolean isLinux = System.getProperty("os.name").toLowerCase().indexOf("linux") >= 0;
    public static final float osVersion = parseOsVersion(); // 10.48 for Mac: 10.4.8
    public static final float javaVersion = parseJavaVersion(); // 1.5006 for 1.5.0_6
    public static final String arch = System.getProperty("os.arch");

    public static final int KB = 1024;
    public static final int MB = 1024 * KB;
    public static final int GB = 1024 * MB;
    public static final Class[] VOID = new Class[]{};
    public static final Class[] OBJECT = new Class[]{Object.class};
    public static final Class[] STRING = new Class[]{String.class};
    public static final Class[] MAP = new Class[]{Map.class};
    public static final Object[] NONE = new Object[]{};
    private static final String version = readVersionAndRevision();
    private static int revision;
    private static File tmp; // temp directory
    private static File desktop;
    private static String desktopPath;
    private static File cache; // cache directory
    private static SpecialFolders folders;
    private static Robot robot;
    private static Method getDesktop;
    private static long luid;
    private static long pid;
    private static File localAppData;
    private static File userPreferences;
    private static NumberFormat nf = null;
    private static Map writable = new HashMap<File, Boolean>();
    private static Random random = null;
    private static File home = null;

    private Util() {
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static Random getRandom() {
        if (random == null) {
            try {
                random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            } catch (Throwable e) {
                random = new Random();
            }
        }
        return random;
    }

    public interface SpecialFolders {
        // may return null:
        File getAppData();
        File getLocalAppData();
        File getDesktopDirectory();
        File getDocuments();
        long getPID();
    }

    public static void setSpecialFolders(SpecialFolders sf) {
        assert folders == null : "call only once";
        folders = sf;
    }

    public static boolean equals(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    public static void sleep(int milliseconds) {
        try { Thread.sleep(milliseconds); } catch (InterruptedException e) { /* ignore */ }
    }

    public static void invokeLater(int milliseconds, final Runnable r) {
        Timer timer = new Timer(milliseconds, new ActionListener(){
            public void actionPerformed(ActionEvent actionEvent) {
                r.run();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    public static boolean isVista() { // or worse
        return isWindows && osVersion >= 6;
    }

    public static String getVersion() {
        return version;
    }

    public static int getRevision() {
        return revision;
    }

    public static int a2i(String s, int defau1t) {
        try{
            return s == null ? defau1t : Integer.decode(s.trim()).intValue();
        } catch (Throwable x) {
            return defau1t;
        }
    }

    public static boolean sameFile(File f1, File f2) {
        if (f1 == null) {
            return f2 == null;
        } else if (f2 == null) {
            return false;
        }
        if (equals(f1, f2)) {
            return true;
        }
        String p1 = f1.getAbsolutePath();
        String p2 = f2.getAbsolutePath();
        if (p1.toLowerCase().equals(p2.toLowerCase())) {
            return true;
        }
        try {
            p1 = f1.getCanonicalPath();
            p2 = f2.getCanonicalPath();
            if (p1.toLowerCase().equals(p2.toLowerCase())) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static String getHome() {
        return System.getProperty("user.home");
    }

    public static File getHomeFolder() {
        if (home == null) {
            home = new File(getHome());
        }
        return home;
    }

    public static File getDocuments() {
        if (folders != null) {
            File d = folders.getDocuments();
            if (d != null && d.isDirectory()) {
                return d;
            }
        }
        return new File(getHome(), isMac ? "Documents" : "My Documents");
    }

    public static File getDesktop() {
        if (desktop == null) {
            if (folders != null) {
                File d = folders.getDesktopDirectory();
                if (d != null && d.isDirectory()) {
                    desktop = d;
                    return desktop;
                }
            }
            if (getDesktop == null && isWindows) {
                getDesktop = calls.getDeclaredMethod(
                        "sun.awt.shell.Win32ShellFolderManager2.getDesktop", VOID);
            }
            if (isMac) {
                desktop = mac.getDeskFolder();
            } else if (getDesktop != null) {
                desktop = (File)calls.call(getDesktop, null, NONE);
            }
            if (desktop == null) {
                // TODO: this only works on US_EN locale
                // for windows GetSpecialFolderLocation is due here
                desktop = new File(getHome(), "Desktop");
            }
        }
        if (!desktop.isDirectory()) {
            mkdirs(desktop);
        }
        if (!desktop.isDirectory()) {
            desktop = getHomeFolder(); // expect at least user home to be a directory
        }
        assert desktop.isDirectory() : "desktop is not Directory: \"" + desktop + "\"";
        return desktop;
    }

    public static String getDesktopPath() {
        if (desktopPath == null) {
            desktopPath = getCanonicalPath(getDesktop());
        }
        return desktopPath;
    }

    public static File getUserPreferences() {
        if (userPreferences == null) {
            File p;
            File local = null;
            if (folders != null) {
                File d = folders.getLocalAppData();
                if (d != null && d.isDirectory()) {
                    local = d;
                }
            }
            if (isMac) {
                p = mac.getPreferencesFolder();
            } else if (isVista()) {
                p = local != null ? local : new File(getHome(), "AppData\\Local");
            } else if (isWindows) {
                p = local != null ? local : new File(getHome(), "Local Settings\\Application Data");
            } else {
                // TODO: for Un*x there should be some kind of standard:
                p = new File(getHome(), ".java-apps-user-prefs");
            }
            try {
                p = p.getCanonicalFile();
                mkdirs(p);
                userPreferences = p;
            } catch (IOException e) {
                throw new Error(e);
            }
        }
        return userPreferences;
    }

    public static File getTmp() {
        if (tmp == null) {
            try {
                if (isMac) {
                    tmp = mac.getTempFolder();
                } else {
                    File temp = File.createTempFile("zipeg.com", "test", null);
                    tmp = temp.getParentFile();
                    if (!delete(temp)) {
                        temp.deleteOnExit();
                    }
                }
            } catch (IOException e) {
                throw new Error(e);
            }
        }
        assert tmp.isDirectory() : tmp;
        return tmp;
    }

    /**
     * @return full pathname of java executable
     */
    public static String getJava() {
        File bin = new File(System.getProperty("java.home"), "bin");
        return getCanonicalPath(new File(bin, isMac ? "java" : "javaw.exe"));
    }

    private static File getApplicationData() {
        if (localAppData == null) {
            if (folders != null) {
                File d = folders.getLocalAppData();
                if (d != null && d.isDirectory()) {
                    localAppData = new File(d, "Zipeg");
                }
            }
            if (localAppData == null) {
               localAppData = new File(!isWindows ?
                                     getTmp() :
                                     new File(System.getProperty("user.home"),
                                     "Application Data"), "Zipeg");
            }
            if (isWindows) {
                localAppData = new File(localAppData, "Cache");
            }
            if (!localAppData.isDirectory()) {
                mkdirs(localAppData);
            }
        }
        return localAppData;
    }

    public static String[] getEnvFilterOutMacCocoaCFProcessPath() {
        // http://lists.apple.com/archives/printing/2003/Apr/msg00074.html
        // it definitely breaks Runtime.exec("/usr/bin/open", ...) on Leopard
        String[] env = null;
        Map v = System.getenv();
        if (v != null) {
            ArrayList a = new ArrayList();
            for (Iterator i = v.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry e = (Map.Entry)i.next();
                String key = (String)e.getKey();
//              Debug.traceln(key + "=" + e.getValue());
                if (!"CFProcessPath".equalsIgnoreCase(key)) {
                    a.add(key + "=" + e.getValue());
                }
            }
            env = (String[])a.toArray(new String[a.size()]);
        }
        return env;
    }

    public static File getCacheDirectory(boolean create) {
        // System.getProperty("user.home")/Library/Caches/com.zipeg/ is unhappy place for Finder!
        // see study/DnD project
        if (cache == null && create) {
            File data = getApplicationData();
            cache = new File(data, luid());
            int retry = 10;
            while (retry > 0 && !cache.mkdirs()) {
                sleep((int)(getRandom().nextFloat() * 100) + 100);
                cleanupCaches();
                cache = new File(data, luid());
                retry--;
            }
            if (retry <= 0) { // try on the temp directory instead
                File tmpdir;
                if (isWindows) {
                    // C:/Documents and Settings/user/Local Settings/Temp
                    tmpdir = new File(getHome(), "Local Settings\\Temp\\Zipeg");
                } else {
                    tmpdir = new File("/tmp/zipeg");
                }
                if (!tmpdir.isDirectory()) {
                    boolean b = tmpdir.mkdirs();
                    if (!b) {
                        if (Debug.isDebug()) {
                            throw new Error("cannot create " + tmpdir);
                        } else {
                            Debug.traceln("failed to create " + tmpdir);
                        }
                    }
                } else {
                    cleanupCaches(tmpdir);
                }
                cache = new File(tmpdir, luid());
                boolean b = cache.mkdirs();
                if (!b && !cache.isDirectory()) {
                    throw new Error("failed to create: " + cache);
                }
            }
            File lock = new File(cache, ".lock");
            try { createNewFile(lock); } catch (IOException e) { throw new Error(e); }
            lock.deleteOnExit();
            cache.deleteOnExit();
            cleanupCaches();
        }
        return cache;
    }

    /**
     * searches and destroys all caches older than 3 days
     */
    public static void cleanupCaches() {
        cleanupCaches(getApplicationData());
    }

    private static void cleanupCaches(File where) {
        final long TOO_OLD = 3L * 24L * (3600 * 1000); // 3 days
        File[] dirs = where.listFiles();
        if (dirs == null) {
            return;
        }
        for (int i = 0; i < dirs.length; i++) {
            File dir = dirs[i];
            // paranoia - because the code is about to do rmdirs()
            // as a minimum: /tmp/zipeg/
            if (dir.exists() && dir.toString().length() > "/tmp/zipeg".length() &&
                dir.toString().toLowerCase().indexOf("zipeg") > 0) {
                long delta; // how many milliseconds ago it was modified?
                File lock = new File(dir, ".lock");
                if (lock.exists()) {
                    delta = System.currentTimeMillis() - lock.lastModified();
                    Debug.traceln(lock + " " + delta / (3600 * 1000) + " hrs old " + new Date(lock.lastModified()));
                } else {
                    delta = System.currentTimeMillis() - dir.lastModified();
                    Debug.traceln(dir + " " + delta / (3600 * 1000) + " hrs old " + new Date(dir.lastModified()));
                    Debug.traceln(dir + "/.lock absent");
                }
                if (delta > TOO_OLD) {
                    delete(lock);
                    rmdirs(dir);
                    Debug.traceln("removed " + dir);
                }
            }
        }
        Debug.traceln();
    }

    private static InputStream getVersionFileInputStream() throws FileNotFoundException {
        File file = getVersionFile(); // always try the Content/version first
        return file.canRead() ? new FileInputStream(file) :
               Resources.getResourceAsStream("version.txt");
    }

    public static File getVersionFile() {
        String url = Resources.class.getResource("Util.class").toString();
        // jar:file:/Users/~/xepec.com/svn/src/trunk/zipeg/Zipeg.app/Contents/Resources/Java/zipeg.jar!/...
        int ix = url.indexOf(".jar!/");
        if (ix > 0) { // running from jar
            ix = url.lastIndexOf('/', ix);
            assert ix >= 0;
            url = url.substring(0, ix + 1) + "version.txt";
        } else {
            ix = url.lastIndexOf("/classes/");
            url = url.substring(0, ix) + "/Zipeg.app/Contents/Resources/Java/version.txt";
        }
        ix = url.indexOf("file:");
        if (ix >= 0) {
            url = url.substring(ix + 5);
        }
        return new File(url);
    }

    public static String getCanonicalPath(File file) {
        try {
            String cp = file.getCanonicalPath();
            assert cp != null : file;
            return cp;
        } catch (IOException e) {
            Debug.traceln("getCanonicalPath failed: " + file);
            Debug.printStackTrace(e);
            String ap = file.getAbsolutePath();
            assert ap != null : ap;
            return ap;
//          assert false : "" + file;
//          throw new Error(e);
        }
    }

    public static boolean mkdirs(File folder) {
        if (!folder.mkdirs() && !folder.isDirectory()) {
            Debug.traceln("failed to create directory: " + folder);
            return false;
        } else {
            return folder.isDirectory();
        }
    }

    public static boolean delete(File file) {
        if (file.exists() && !file.delete()) {
            Debug.traceln("failed to delete file: " + file);
            return false;
        } else {
            return !file.exists();
        }
    }

    public static void createNewFile(File file) throws IOException {
        if (!file.createNewFile()) {
            throw new IOException("failed to create new file: " + file);
        }
    }

    public static void setLastModified(File file, long time) {
        if (!file.setLastModified(time)) {
            Debug.traceln("failed to setLastModified(" + file + ", " +
                    new Date(time) + ")");
        }
    }

    /** Constructs primitive plural form of English nouns
     * @param n number of things
     * @param s singular noun for a thing
     * @return string like "123 items" or "21 file"
     */
    public static String plural(int n, String s) {
        return n + " " + (n != 11 && n % 10 == 1 ? s : (s + "s"));
    }

    // TODO: 1. isMac() 2. On Win32 does not work with Unicode names

    public static void openDoc(String filepath) {
        assert isWindows : "only on Win32";
        try {
            Runtime.getRuntime().exec("rundll32.exe shell32.dll,ShellExec_RunDLL " + filepath);
          } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openDocWith(String filepath) {
        assert isWindows : "only on Win32";
        try {
            Runtime.getRuntime().exec("rundll32.exe shell32.dll,OpenAs_RunDLL " + filepath);
          } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void previewImage(String file) {
        try {
            if (isMac) {
                Runtime.getRuntime().exec(new String[]{"open", "/Applications/Preview.app", file});
            } else if (isWindows) {
                Runtime.getRuntime().exec("rundll32 shimgvw.dll,ImageView_Fullscreen " + file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param value number to format
     * @return string formatted with "," or "." in 3 decimal places.
     * E.g. 153248 becomes "153,248" or "153.248" depending on default locale.
     */
    public static String formatNumber(long value) {
        if (nf == null) {
            // getInstance is heavy operation, thus cached
            nf = NumberFormat.getInstance();
        }
        try {
            return nf.format(value); // known to be broken (rarely).
        } catch (Throwable t) {
            DecimalFormat df = new DecimalFormat();
            df.applyPattern("###,###.###");
            nf = df;
            return nf.format(value);
        }
    }

    /**
     * @param bytes number to format
     * @return for numbers <= 10KB returns number of bytes formatted with comas
     *         for numbers > 10KB returns number of kilobytes
     *         for 0 returns empty string
     */
    public static String formatKB(long bytes) {
        String v;
        if (bytes > 10 * KB) {
            v = formatNumber(bytes / KB) + " KB";
        } else if (bytes > 0) {
            v = formatNumber(bytes) + " bytes";
        } else {
            v = "";
        }
        return v;
    }

    /**
     * @param bytes number to format
     * @return for numbers > 10MB return number of megabytes formatted with comas
     *         for smaller numbers return formatKB()
     */
    public static String formatMB(long bytes) {
        if (bytes > 10 * MB) {
            return formatNumber(bytes / MB) + " MB";
        }
        else {
            return formatKB(bytes);
        }
    }

    public static File fileOf(String s) {
        return s == null ? null : new File(s);
    }

    public static void close(InputStream s) {
        if (s != null) {
            try { s.close(); } catch (IOException x) { throw new Error(x); }
        }
    }

    public static void close(OutputStream s) {
        if (s != null) {
            try { s.close(); } catch (IOException x) { throw new Error(x); }
        }
    }

    public static void close(Reader r) {
        if (r != null) {
            try { r.close(); } catch (IOException x) { throw new Error(x); }
        }
    }

    public static byte[] readBytes(InputStream s) throws IOException {
        int block = Math.max(s.available(), 16*1024);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(block);
        byte[] b = new byte[block];
        for (;;) {
            int k = s.read(b);
            if (k < 0) {
                break;
            }
            baos.write(b, 0, k);
        }
        return baos.toByteArray();
    }

    public static byte[] readFile(File f) {
        InputStream s = null;
        try {
            s = new FileInputStream(f);
            return readBytes(s);
        } catch (IOException e) {
            throw new Error(e);
        } finally {
            close(s);
        }
    }

    public static boolean isDirectoryWritable(File dir) {
        if (writable.containsKey(dir)) {
            return ((Boolean)writable.get(dir)).booleanValue();
        }
        if (dir.isDirectory() && dir.canWrite()) {
            File f = new File(dir, "." + Long.toHexString(getRandom().nextLong()) + "-zipeg.tmp");
            try {
                boolean b = f.createNewFile();
                if (b) {
                    b = f.delete();
                }
                writable.put(dir, Boolean.valueOf(b));
                return b;
            } catch (Throwable t) {
                if (f.exists() && !f.delete()) {
                    f.deleteOnExit();
                }
            }
        }
        writable.put(dir, Boolean.FALSE);
        return false;
    }


    /**
     * callMainOnNewProcess copy out single class (should not have package dependencies)
     * and starts new process calling main(String[] args) from that class
     * @param cls  class name e.g. "CopyAndRestart"
     * @param args arguments to pass to CopyAndRestart.main()
     */
    public static void callMainOnNewProcess(String cls, String[] args) {
        File wd = new File(getCanonicalPath(new File(".")));
        InputStream i = null;
        FileOutputStream o = null;
        try {
            String c = cls + ".class";
            i = Util.class.getResource(c).openStream();
            File car = new File(wd, "com/zipeg/" + c);
            delete(car);
            mkdirs(car.getParentFile());
            createNewFile(car);
            o = new FileOutputStream(car);
            copyStream(i, o);
            close(i);
            i = null;
            close(o);
            o = null;
            String[] a = new String[args.length + 2];
            a[0] = getJava();
            a[1] = "com.zipeg." + cls;
            System.arraycopy(args, 0, a, 2, args.length);
            Runtime.getRuntime().exec(a);
/*
            Process p = Runtime.getRuntime().exec(a);
            p.waitFor();
*/
        } catch (Throwable e) { // IOException or InterruptedException
            throw new Error(e);
        } finally {
            close(i);
            close(o);
        }
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16*1024];
        int len;
        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }
    }

    /**
     * TODO: make atomic
     * Copy full content of existing file "from" into <b>new</b> file "to".
     * On OSX does not preserve resource fork and file finder attributes. Use
     * only when absolutely sure that those are not needed.
     * @param from source file (must exist and be readable)
     * @param to destination file (must not exist)
     * @throws IOException if failed to copy or close files
     */
    public static void copyFile(File from, File to) throws IOException {
        if (to.exists()) {
            throw new IOException("cannot copy file: \"" + from +
                                  "\" over existing file: \"" + to + "\"");
        }
        RandomAccessFile fin = null;
        FileChannel cin = null;
        RandomAccessFile fout = null;
        FileChannel cout = null;
        createNewFile(to);
        try {
            fin = new RandomAccessFile(from, "r");
            fout = new RandomAccessFile(to, "rw");
            cin = fin.getChannel();
            cout = fout.getChannel();
            cout.transferFrom(cin, 0, fin.length());
        } finally {
            if (cin != null) cin.close();
            if (cout != null) cout.close();
            if (fin != null) fin.close();
            if (fout != null) fout.close();
        }
    }

    /**
     * TODO: make atomic
     * Copy full content of existing file "from" into <b>new</b> file "to"
     * preserving resource fork and dir attributes. Does not copy over!
     * @param from source file (must exist and be readable)
     * @param to destination file (must not exist)
     * @throws IOException if failed to copy or close files
     */
    private static void cpFile(File from, File to) throws IOException {
        assert isMac : " cpFile uses OSX specific options \"cp -n -p\"";
        String f = getCanonicalPath(from);
        String t = getCanonicalPath(to);
        Process cp = Runtime.getRuntime().exec(new String[]{"cp", "-n", "-p", f, t});
        try {
            int res = cp.waitFor();
            if (res != 0) {
                throw new IOException("error=" + res + " while copy \"" + f + "\" \"" + t + "\"");
            }
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Renames file and if fails copy full content of existing file "from" into <b>new</b> file "to".
     * @param from source file (must exist and be readable)
     * @param to destination file (must not exist)
     * @throws IOException if failed to copy or close files
     */
    public static void renameOrCopyFile(File from, File to) throws IOException {
        if (!from.renameTo(to)) { // this java call preserves resource fork and dir attributes
            if (isMac) {
                cpFile(from, to); // slower but correct
            } else {
                copyFile(from, to);
            }
            if (from.exists() && !delete(from)) {
                throw new IOException("failed to delete: " + from);
            }
        }
    }

    public static boolean rmdirs(File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        boolean b = true;
        File[] list = dir.listFiles();
        if (list != null) { // dir stopped being directory (e.g. deleted from another process)
            for (int i = 0; i < list.length; i++) {
                b = (list[i].isDirectory() ? rmdirs(list[i]) : delete(list[i])) && b;
            }
        }
        b = delete(dir) && b;
        return b;
    }

    public static void openUrl(String url) {
        if (isMac) {
            calls.callStatic("com.apple.eio.FileManager.openURL", new Object[]{url});
        } else {
            try {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void sendMail(String email, String title, String body) {
        try {
            final int MAX_LENGTH = 1200;
            if (isWindows && body.length() > MAX_LENGTH) {
                body = body.substring(0, MAX_LENGTH);
            }
            body = spaces(URLEncoder.encode(body, "UTF-8"));
            title = spaces(URLEncoder.encode(title, "UTF-8"));
            openUrl("mailto:" + email + "?subject=" + title + "&body=" + body);
        }
        catch (IOException e) {
            // noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    /**
     * HTTP GET
     * @param url to get from
     * @param headers Map of none-null String -> String value pairs (params itself can be null)
     * @param reply headers from http server (ignored if reply == null)
     * @param body of a reply will be written there if body is not null
     * @return true if request was successful
     * @throws IOException on i/o errors
     */
    public static boolean getFromUrl(String url, Map headers, Map reply, ByteArrayOutputStream body)
            throws IOException {
        return httpGetPost(false, url, headers, reply, body);
    }

    /**
     * HTTP POST
     * @param url to post to
     * @param headers Map of none-null String -> String value pairs (params itself can be null)
     * @param reply headers from http server (ignored if reply == null)
     * @param body of a reply will be written there if body is not null
     * @return true if request was successful
     * @throws IOException on i/o errors
     */
    public static boolean postToUrl(String url, Map headers, Map reply, ByteArrayOutputStream body)
            throws IOException {
        return httpGetPost(true, url, headers, reply, body);
    }

    /**
     * HTTP GET/POST
     * @param post true if POST is requested
     * @param url to get from
     * @param headers Map of none-null String -> String value pairs (params itself can be null)
     * @param reply headers from http server (ignored if reply == null)
     * @param body of a reply will be written there if body is not null
     * @return true if request was successful
     * @throws IOException on i/o errors
     */
    static boolean httpGetPost(boolean post, String url, Map headers, Map reply, ByteArrayOutputStream body)
            throws IOException {
        StringBuilder content = new StringBuilder(2 * 1024);
        if (headers != null) {
            for (Iterator i = headers.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry e = (Map.Entry)i.next();
                assert e.getKey() instanceof String;
                assert e.getValue() instanceof String;
                String value = URLEncoder.encode((String)e.getValue(), "UTF-8");
                if (content.length() == 0) {
                    content.append(e.getKey()).append('=').append(value);
                } else {
                    content.append('&').append(e.getKey()).append('=').append(value);
                }
            }
            if (!post) {
                url = url + "&" + content;
            }
        }
        URLConnection conn = new URL(url).openConnection();
        conn.setUseCaches(false);
        conn.setDoInput(true);
        if (post) {
            conn.setDoOutput(true);
        }
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "Mozilla/4.0");
        conn.setDefaultUseCaches(false);
        conn.setRequestProperty("Cache-Control", "max-age=0");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Cache-Control", "no-store");
        conn.setRequestProperty("Pragma", "no-cache");
        if (post) {
            conn.setRequestProperty("Content-Length", "" + content.length());
            DataOutputStream printout = new DataOutputStream(conn.getOutputStream());
            if (content.length() > 0) {
                printout.writeBytes(content.toString());
            }
            printout.flush();
            close(printout);
        }
        Map fields = conn.getHeaderFields();
        if (reply != null) {
            reply.putAll(fields);
        }
        String response = getHttpReplyHeaderString(fields, null);
        DataInputStream input = new DataInputStream(conn.getInputStream());
        byte[] buf = new byte[1024];
        for (;;) {
            int k = input.read(buf);
            if (k <= 0) {
                break;
            }
            body.write(buf, 0, k);
        }
        close(input);
        return responseCode(response) == 200; // "HTTP/1.1 200 OK"
    }

    private static String getHttpReplyHeaderString(Map fields, Object key) {
        if (fields == null) {
            return "";
        }
        Object value = fields.get(key);
        if (!(value instanceof List)) {
            return "";
        }
        List list = (List)value;
        if (list.size() <= 0) {
            return "";
        }
        return (String)list.get(0);
    }

    private static long getHttpReplyHeaderLong(Map fields, Object key) {
        String s = getHttpReplyHeaderString(fields, key);
        try {
            return s.length() > 0 ? Long.decode(s).longValue() : -1;
        } catch (NumberFormatException x) {
            return -1;
        }
    }

    private static int responseCode(String s) {
        // simple parser for : "HTTP/1.1 200 OK"
        if (s == null) return -1;
        StringTokenizer st = new StringTokenizer(s);
        if (!st.hasMoreTokens()) return -1;
        st.nextToken(); // HTTP/1.1
        if (!st.hasMoreTokens()) return -1;
        try{
            return Integer.decode(st.nextToken()).intValue();
        } catch (NumberFormatException x) {
            return -1;
        }
    }

    private static float parseOsVersion() {
        String v = System.getProperty("os.version");
        // Mac: 10.4.8
        // Windows: 5.1
        if (!isMac && !isWindows) {
            assert false : v + " debug me on Linux";
        }
        int ix = v.indexOf('.');
        if (ix > 0) {
            String s = v.substring(ix + 1);
            v = v.substring(0, ix + 1) + s.replaceAll("\\.", "").replaceAll("_", "");
        }
        ix = 0;
        while (ix < v.length() && (Character.isDigit(v.charAt(ix)) ||
                                   v.charAt(ix) == '.')) {
            ix++;
        }
        v = v.substring(0, ix);
        return Float.parseFloat(v);
    }

    private static String readVersionAndRevision() {
        Properties p = new Properties();
        String v = "0.0.0.0 (development)";
        InputStream is = null;
        try {
            is = getVersionFileInputStream();
            if (is != null) {
                p.load(is);
                String s = p.getProperty("version").trim();
                int ix = s == null ? -1 : s.lastIndexOf('.');
                if (s != null && ix > 0) {
                    revision = Integer.decode(s.substring(ix + 1)).intValue();
                    v = s;
                }
            }
        } catch (IOException iox) {
            /* ignore */
        } catch (NumberFormatException nfx) {
            /* ignore */
        } finally {
            close(is);
        }
        Debug.traceln("zipeg " + v);
        return v;
    }

    private static String spaces(String s) {
        StringBuilder r = new StringBuilder(s.length());
        int i = 0;
        for (;;) {
            int j = s.indexOf('+', i);
            if (j < 0) break;
            r.append(s.substring(i, j));
            r.append("%20");
            i = j + 1;
        }
        r.append(s.substring(i));
        return r.toString();
    }

    private static float parseJavaVersion() {
        String v = System.getProperty("java.version");
        int ix = v.indexOf('.');
        if (ix > 0) {
            String s = v.substring(ix + 1);
            v = v.substring(0, ix + 1) + s.replaceAll("\\.", "").replaceAll("_", "");
        }
        ix = 0;
        while (ix < v.length() && (Character.isDigit(v.charAt(ix)) ||
                                   v.charAt(ix) == '.')) {
            ix++;
        }
        v = v.substring(0, ix);
        return Float.parseFloat(v);
    }

    public static boolean isPreviewable(String filename) {
        String name = filename.toLowerCase();
        return  name.endsWith(".jpg")   ||
                name.endsWith(".jpeg")  ||
                name.endsWith(".png")   ||
                name.endsWith(".bmp")   ||
                name.endsWith(".pdf") && isMac ||
                name.endsWith(".rtf") && isMac ||
                name.endsWith(".txt") && isMac ||
                name.endsWith(".tiff")  ||
                name.endsWith(".tif");
    }

    public static boolean isArchiveFileType(String filename) {
        String name = filename.toLowerCase();
        return  name.endsWith(".zip")   ||
                name.endsWith(".cbz")   ||
                name.endsWith(".rar")   ||
                name.endsWith(".cbr")   ||
                name.endsWith(".7z")    ||
                name.endsWith(".arj")   ||
                name.endsWith(".bz")    ||
                name.endsWith(".bz2")   ||
                name.endsWith(".cab")   ||
                name.endsWith(".lzh")   ||
                name.endsWith(".lha")   ||
                name.endsWith(".chm")   ||
                name.endsWith(".gzip")  ||
                name.endsWith(".bz2")   ||
                name.endsWith(".bzip2") ||
                name.endsWith(".gz")    ||
                name.endsWith(".tar")   ||
                name.endsWith(".tz")    ||
                name.endsWith(".tgz")   ||
                name.endsWith(".tbz")   ||
                name.endsWith(".tbz2")  ||
                name.endsWith(".rpm")   ||
                name.endsWith(".cpio")  ||
                name.endsWith(".cpgz")  ||
                name.endsWith(".iso")   ||
                name.endsWith(".nsis")  ||
                name.endsWith(".z")     ||
                name.endsWith(".war")   ||
                name.endsWith(".ear")   ||
                name.endsWith(".zap")   ||
                name.endsWith(".zpg")   ||
                name.endsWith(".jar");
    }

    public static boolean isCompositeArchive(String filename) {
        String name = filename.toLowerCase();
        int ix = name.indexOf(".tar.");
        return ix > 0 && isArchiveFileType(name.substring(ix + 1)) ||
                name.endsWith(".tz") ||
                name.endsWith(".tgz") ||
                name.endsWith(".tbz") ||
                name.endsWith(".tbz2") ||
                name.endsWith(".cpgz");
    }

    /**
     * creates document modal dialog and centers it in the owner window
     * @param parent window
     * @return modal JDialog
     */
    public static JDialog createDocumentModalDialog(final JFrame parent) {
        Throwable x = null;
        JDialog d = null;
        boolean documentModalSheet = isMac && javaVersion >= 1.6 && javaVersion < 1.7 && parent != null;
        if (documentModalSheet) {
            try {
                Class mt = Class.forName("java.awt.Dialog$ModalityType");
                Field dm = mt.getField("DOCUMENT_MODAL");
                Class[] sig = new Class[]{Window.class, mt};
                Constructor c = JDialog.class.getConstructor(sig);
                Object[] params = new Object[]{parent, dm.get(null)};
                d = (JDialog)c.newInstance(params);
            } catch (ClassNotFoundException e) { x = e;
            } catch (NoSuchFieldException e) { x = e;
            } catch (NoSuchMethodException e) { x = e;
            } catch (IllegalAccessException e) { x = e;
            } catch (InvocationTargetException e) { x = e;
            } catch (InstantiationException e) { x = e;
            }
            if (x != null) {
                if (Debug.isDebug()) {
                    throw new Error(x);
                }
            }
        }
        if (d == null) {
            d = new JDialog(parent) {
                public void validate() {
                    super.validate();
                    if (getParent() != null) {
                        try {
                            setLocationRelativeTo(getParent());
                        } catch (Throwable t) {
                            // known not to work sometimes
                        }
                    }

                }
            };
            d.setModal(true);
        }
        final JDialog dlg = d;
        if (documentModalSheet) {
            parent.getRootPane().putClientProperty("apple.awt.documentModalSheet", "true");
            dlg.getRootPane().putClientProperty("apple.awt.documentModalSheet", "true");
        } else {
            // in case validate() overwrite fails:
            dlg.addComponentListener(new ComponentAdapter() {
                public void componentShown(ComponentEvent e) {
                    Window owner = parent != null ? parent : dlg.getOwner();
                    assert owner != null : "search for SwingUtilities.getSharedOwnerFrame() usage";
                    dlg.setLocationRelativeTo(owner);
                    dlg.removeComponentListener(this);
                }
            });
        }
        return dlg;
    }

    /**
     * @return locally unique id for the user
     */
    public static synchronized String luid() {
        if (pid == 0) {
            if (isWindows && folders != null) {
                pid = folders.getPID();
            } else {
                pid = OSX.getMyProcessId();
            }
        }
        long next = Clock.nanoTime();
        if (next == luid) {
            next++;
        }
        luid = next;
        String r = Long.toHexString(luid).toUpperCase();
        return pid != 0 ? r + "." + Long.toHexString(pid) : r; // append pid to luid
    }

    public static Robot getRobot() {
        if (robot == null) {
            try {
                robot = new Robot();
            } catch (Throwable e) {
                return null;
            }
        }
        return robot;
    }

    /**
     * See: http://www.opengroup.org/onlinepubs/009629399/apdxa.htm
     *      http://www.opengroup.org/dce/info/draft-leach-uuids-guids-01.txt
     * @return random UUID
     */
    public static String uuid() {
        return new UUID().toString().toUpperCase();
    }

    private static class UUID {

        private long msb; // most significant bits
        private long lsb; // least significant bits

        UUID() {
            byte[] bytes = new byte[16];
            getRandom().nextBytes(bytes);
            bytes[6]  &= 0x0f;  /* clear version        */
            bytes[6]  |= 0x40;  /* set to version 4     */
            bytes[8]  &= 0x3f;  /* clear variant        */
            bytes[8]  |= 0x80;  /* set to IETF variant  */
            for (int i=0; i <  8; i++) msb = (msb << 8) | (bytes[i] & 0xff);
            for (int i=8; i < 16; i++) lsb = (lsb << 8) | (bytes[i] & 0xff);
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof UUID)) return false;
            UUID id = (UUID)obj;
            return msb == id.msb && lsb == id.lsb;
        }

        public String toString() {
            return (hex(msb >> 32, 8) + "-" + hex(msb >> 16, 4) + "-" +
                    hex(msb, 4) + "-" + hex(lsb >> 48, 4) + "-" + hex(lsb, 12));
        }

        private static String hex(long val, int digits) {
            long hi = 1L << (digits * 4); // make sure we have leading zeros
            return Long.toHexString(hi | (val & (hi - 1))).substring(1);
        }

    }

    private static StringBuilder append(StringBuilder sb, long bytes, String label) {
        if (bytes > 0) {
            sb.append(label).append("\t").append(bytes / MB).append("MB\n");
        }
        return sb;
    }

    public static String dump() {
        StringBuilder sb = new StringBuilder(8 * 1024);
        sb.append("getOsVersion=").append(Util.osVersion).append("\n");
        if (Util.isWindows) {
            sb.append("isVistaOrW7=").append(Util.isVista()).append("\n");
        }
        sb.append("cores=").append(Runtime.getRuntime().availableProcessors()).append("\n");
        sb.append("getUserPreferences=")
                .append(Util.getUserPreferences()).append("\n" + "getHome=")
                .append(Util.getHome()).append("\n");
        if (folders != null) {
            sb.append("getSpecialFolder(CSIDL_LOCAL_APPDATA)=")
                    .append(folders.getLocalAppData())
                    .append("\n" + "getSpecialFolder(CSIDL_APPDATA)=")
                    .append(folders.getAppData())
                    .append("\n" + "getSpecialFolder(CSIDL_DESKTOPDIRECTORY)=")
                    .append(folders.getDesktopDirectory())
                    .append("\n" + "getSpecialFolder(CSIDL_MYDOCUMENTS)=")
                    .append(folders.getDocuments()).append("\n");
        }
        append(sb, getCommittedVirtualMemorySize(), "CommittedVirtualMemorySize");
        append(sb, getTotalSwapSpaceSize(), "TotalSwapSpaceSize");
        append(sb, getFreePhysicalMemorySize(), "FreePhysicalMemorySize");
        append(sb, getTotalPhysicalMemorySize(), "TotalPhysicalMemorySize");
        sb.append("RuntimeMemory" + "\nMax   (MB): ")
            .append(Runtime.getRuntime().maxMemory() / Util.MB).append("\nTotal (MB): ")
            .append(Runtime.getRuntime().totalMemory() / Util.MB).append("\nFree  (MB): ")
            .append(Runtime.getRuntime().freeMemory() / Util.MB).append("\n");
        if (getProcessCpuTime() > 0) {
            sb.append("\nProcessCpuTime: \t").append(Clock.formatNanoTime(getProcessCpuTime()));
        }
        sb.append("\nRunningWallTime: \t")
                .append(Clock.formatNanoTime(System.nanoTime() - startupTime)).append("\n");
        return sb.toString();
    }

    private static OperatingSystemMXBean getOperatingSystemMXBean() throws ClassCastException {
        return (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
    }

    public static long getCommittedVirtualMemorySize() {
        try {
            OperatingSystemMXBean mxb = getOperatingSystemMXBean();
            return mxb.getCommittedVirtualMemorySize();
        } catch (Throwable ignore) {
            return -1;
        }
    }

    public static long getTotalSwapSpaceSize() {
        try {
            OperatingSystemMXBean mxb = getOperatingSystemMXBean();
            return mxb.getTotalSwapSpaceSize();
        } catch (Throwable ignore) {
            return -1;
        }
    }

    public static long getFreeSwapSpaceSize() {
        try {
            OperatingSystemMXBean mxb = getOperatingSystemMXBean();
            return mxb.getFreeSwapSpaceSize();
        } catch (Throwable ignore) {
            return -1;
        }
    }

    public static long getProcessCpuTime() {
        try {
            OperatingSystemMXBean mxb = getOperatingSystemMXBean();
            return mxb.getProcessCpuTime();
        } catch (Throwable ignore) {
            return -1;
        }
    }

    public static long getFreePhysicalMemorySize() {
        try {
            OperatingSystemMXBean mxb = getOperatingSystemMXBean();
            return mxb.getFreePhysicalMemorySize();
        } catch (Throwable ignore) {
            return -1;
        }
    }

    public static long getTotalPhysicalMemorySize() {
        try {
            OperatingSystemMXBean mxb = getOperatingSystemMXBean();
            return mxb.getTotalPhysicalMemorySize();
        } catch (Throwable ignore) {
            return -1;
        }
    }

}
