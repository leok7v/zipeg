package com.zipeg;

import java.io.*;

public class Registry implements FileAssociationHandler {

    private static final String ZIPEG = "Zipeg";
    private static final int iconIndex = Util.isWindows ?
            (Util.osVersion > 5.1 ? -112 : -113) : -1;
    private static boolean loaded;
    private static Registry instance;
    private static Key CU_CR; // CURRENT_USER/Classes

    private Registry() {
        loadLibrary();
    }

    public static Registry getInstance() {
        if (instance == null) {
            instance = new Registry();
        }
        return instance;
    }

    public boolean isAvailable() {
        return true;
    }

    public long initializeOLE() {
        loadLibrary();
        return initializeOle();
    }

    public static long getMyProcessId() {
        loadLibrary();
        return getCurrentProcessId();
    }

    /** Terminates all processes based on module name but not current process.
     * @param name process module name e.g. "notepad.exe"
     * @return number of terminated processes.
     */
    public static long killProcessByName(String name) {
        loadLibrary();
        return killProcess(name);
    }

    public long shellExecute(long mask, String verb, String file, String params, String dir) {
        return shellExec(mask, verb, file, params, dir);
    }

    public static void moveFileToRecycleBin(String abspathname) throws IOException {
        loadLibrary();
        moveToRecycleBin(abspathname);
    }

    public void setHandled(long selected) {
        long was = getHandled();
        boolean changed = false;
        for (int i = 0; i < ext2uti.length; i++) {
            if ("chm".equals(ext2uti[i][0]) || "iso".equals(ext2uti[i][0])) {
                continue; // leave chm and iso alone on Window
            }
            long flag = 1L << i;
            if ((flag & selected) != (flag & was)) {
                String ext = "." + ext2uti[i][0];
                if (ext.equalsIgnoreCase(".chm")) {
                    continue; // do not associate with chm!
                }
                String set = null;
                String current = getHandler(ext);
                if ((flag & selected) != 0) {
                    // save existing:
                    if (current != null && !"".equals(current) && !ZIPEG.equals(current)) {
                        Presets.put("Registry.Handler:" + ext, current);
                        Presets.sync();
                    }
                    if (!ZIPEG.equals(current)) {
                        set = ZIPEG;
                    }
                } else {
                    if (ZIPEG.equals(current)) {
                        set = Presets.get("Registry.Handler:" + ext, null);
                        if (set == null) {
                            set = "CompressedFolder";
                            Debug.traceln("Warning: no stored association for " + ext + " reverting to \"" + set + "\")");
                        }
                    }
                }
                if (set != null) {
                    Debug.traceln("Registry.setHandler(" + ext + ", " + set + ")");
                    setHandler(ext, set);
                    changed = true;
                } else {
                    Debug.traceln("not changed for " + ext);
                }
            }
        }
        if (changed) {
            notifyShellAssociationsChanged();
            notifyShellAllChanged();
        }
    }

    public long getHandled() {
        long result = 0;
        for (int i = 0; i < ext2uti.length; i++) {
            if (ZIPEG.equals(getHandler("." + ext2uti[i][0]))) {
                result |= (1L << i);
            } else {
//              Debug.traceln("foreign: " + ct + " = " + ch);
            }
        }
        return result;
    }

    public static class Key {

        private long key;
        private boolean isroot;

        public final static Key CLASSES_ROOT = new Key(0x80000000L);
        public final static Key CURRENT_USER =  new Key(0x80000001L);
        public final static Key LOCAL_MACHINE =  new Key(0x80000002L);
        public final static Key USERS =  new Key(0x80000003L);
        public final static Key PERFORMANCE_DATA =  new Key(0x80000004L);
        public final static Key PERFORMANCE_TEXT =  new Key(0x80000050L);
        public final static Key PERFORMANCE_NLSTEXT =  new Key(0x80000060L);
        public final static Key CURRENT_CONFIG =  new Key(0x80000005L);
        public final static Key DYN_DATA =  new Key(0x80000006L);

        private Key(long k) {
            key = k;
            isroot = true;
        }

        private Key() {
            loadLibrary();
        }

        public static boolean exists(Key parent, String path) {
            try {
                loadLibrary();
                long k = openKey(parent.key, path, KEY_ALL_ACCESS);
                closeKey(k);
                return true;
            } catch (IOException iox){
                return false;
            }
        }

        public boolean exists(String path) {
            return exists(this, path);
        }

        public Key open(String path) {
            try {
                loadLibrary();
                long k = openKey(key, path, KEY_ALL_ACCESS);
                Key key = new Key();
                key.key = k;
                return key;
            } catch (IOException iox){
 //             iox.printStackTrace();
                return null;
            }
        }

        public Key create(String subkey) {
            try {
                loadLibrary();
                int[] disposition = new int[1];
                long k = createKey(key, subkey, null, REG_OPTION_NON_VOLATILE,
                                   KEY_ALL_ACCESS, disposition);
                Key key = new Key();
                key.key = k;
                return key;
            } catch (IOException iox){
//              iox.printStackTrace();
                return null;
            }
        }

        public Key openOrCreate(String path) {
            Key key = open(path);
            if (key == null) {
                int ix = path.lastIndexOf('\\');
                if (ix > 0) {
                    Key parent = open(path.substring(0, ix));
                    if (parent != null) {
                        key = parent.create(path.substring(ix + 1));
                        parent.close();
                    }
                }
            }
            return key;
        }


        public boolean deleteKey(String subkey) {
            try {
                loadLibrary();
                Registry.deleteKey(key, subkey);
                return true;
            } catch (IOException iox){
                iox.printStackTrace();
                return false;
            }
        }

        public boolean deleteValue(String name) {
            try {
                loadLibrary();
                Registry.deleteValue(key, name);
                return true;
            } catch (IOException iox){
                iox.printStackTrace();
                return false;
            }
        }

        public void close() {
            try {
                if (key != 0 && !isroot) {
                    Registry.closeKey(key);
                    key = 0;
                }
            } catch (IOException iox){
                iox.printStackTrace();
            }
        }

        public void flush() {
            try {
                loadLibrary();
                Registry.flushKey(key);
            } catch (IOException iox){
                iox.printStackTrace();
            }
        }

        public void put(String name, long value) {
            try {
                Registry.setValue(key, name, value);
            } catch (IOException iox){
                iox.printStackTrace();
            }
        }

        public void put(String name, String value) {
            try {
                Registry.setValue(key, name, value);
            } catch (IOException iox){
                iox.printStackTrace();
            }
        }

        public void put(String name, byte[] data) {
            try {
                Registry.setValue(key, name, data);
            } catch (IOException iox){
                iox.printStackTrace();
            }
        }

        public boolean isString(String name) {
            try {
                return Registry.getValue(key, name) instanceof String;
            } catch (IOException iox){
                return false;
            }
        }

        public boolean isLong(String name) {
            try {
                return Registry.getValue(key, name) instanceof Long;
            } catch (IOException iox){
                return false;
            }
        }

        public boolean isBinary(String name) {
            try {
                return Registry.getValue(key, name) instanceof byte[];
            } catch (IOException iox){
                return false;
            }
        }

        public Object get(String name) {
            try {
                return Registry.getValue(key, name);
            } catch (IOException iox){
                iox.printStackTrace();
                return null;
            }
        }

        public String get(String name, String defvalue) {
            try {
                return Registry.getValueString(key, name);
            } catch (IOException iox){
                return defvalue;
            }
        }

        public long get(String name, long defvalue) {
            try {
                Long v = Registry.getValueLong(key, name);
                return v != null ? v.longValue() : defvalue;
            } catch (IOException iox){
                return defvalue;
            }
        }

        public byte[] get(String name, byte[] defvalue) {
            try {
                return (byte[])Registry.getValue(key, name);
            } catch (IOException iox){
                return defvalue;
            }
        }

        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }

        public String toString() {
            return isroot ? "Key[" + Long.toHexString(key) + "]" : "Key(" + Long.toHexString(key) + ")";
        }

    }

    private static boolean createApplicationKeys(Key key, String installdir) {
        Key software = key.open("SOFTWARE");
        if (software == null) {
            trace("failed: " + key + "open(\"SOFTWARE\")\n");
            return false;
        }
        Key zipeg = software.create("Zipeg");
        if (zipeg == null) {
            trace("failed: " + key + "software.create(\"Zipeg\")\n");
            return false;
        }
        zipeg.put("", "Zipeg: Rar and Zip Opener");
        zipeg.put("DefaultIcon", installdir + "\\Zipeg.exe," + iconIndex);
        zipeg.put("Path", installdir);
        zipeg.flush();
        zipeg.close();
        software.close();
        trace("done: createApplicationKeys()\n");
        return true;
    }

    private static void createShellCommands(Key key, String exe) {
        Key shell = key.create("shell");

        Key action = shell.create("open");
        action.put("", "Open with Zipeg");
        Key command = action.create("command");
        command.put("", "\"" + exe + "\" \"%1\"");
        command.close();
        action.close();
/*      TODO: implement me
        action = shell.create("open.zipeg.extract.to");
        action.put("", "Zipeg: Extract to ...");
        command = action.create("command");
        command.put("", "\"" + exe + "\" --extract-to \"%1\"");
        command.close();
        action.close();

        action = shell.create("open.zipeg.extract.here");
        action.put("", "Zipeg: Extract here");
        command = action.create("command");
        command.put("", "\"" + exe + "\" --extract-here \"%1\"");
        command.close();
        action.close();
*/
        shell.close();
        trace("done: createShellCommands()\n");
    }

    private static void createDefaultIcon(Key key, String exe) {
        if (key != null && exe != null) {
            Key defaultIcon = key.create("DefaultIcon");
            if (defaultIcon != null) {
                defaultIcon.put("", "\"" + exe + "\"," + iconIndex);
                defaultIcon.close();
                trace("done: createDefaultIcon()\n");
            }
        }
    }

    private static Key getCurrentUserClasses() {
        if (CU_CR == null) {
            CU_CR = Key.CURRENT_USER.openOrCreate("Software\\Classes");
        }
        if (CU_CR == null) {
            trace("failed: getCurrentUserClasses CURRENT_USER.openOrCreate(\"Software\\\\Classes\")\n");
            // let's assume it's locked for a while... Plan B:
            Util.sleep(200);
            CU_CR = Key.CURRENT_USER.openOrCreate("Software\\Classes");
        }
        if (CU_CR == null) {
            Actions.reportFatalError("registry key:\n" +
                    "\\My Computer\\HKEY_CURRENT_USER\\Software\\Classes\n" +
                    "is locked or damaged.\n\n" +
                    "Zipeg cannot be installed now.\n\n" +
                    "Try to reboot, close all other running programs\n" +
                    "and reinstall Zipeg.");
        }
        assert CU_CR != null && CU_CR.key != 0;
        return CU_CR;
    }

    private void setHandler(String ext, String handler) {
        Key key = getCurrentUserClasses().open(ext); // Key.CLASSES_ROOT.open(ext);
        if (key == null) {
            key = getCurrentUserClasses().create(ext);
        }
        if (key != null) {
            key.put("", handler);
            key.put("PerceivedType", "compressed");
            Key openWithList = key.create("OpenWithList");
            if (openWithList == null) {
                openWithList = key.open("OpenWithList");
            }
            if (openWithList != null) {
                openWithList.create("Zipeg.exe");
                openWithList.close();
            }
            key.close();
            trace("done: setHandler(" + ext + "," + handler + ")\n");
        } else {
            trace("failed: setHandler openOrCreate(" + ext + ")\n");
        }
    }

    private String getHandler(String ext) {
        Key handler = getCurrentUserClasses().open(ext);
        if (handler == null) {
            handler = Key.CLASSES_ROOT.open(ext);
        }
        if (handler != null) {
            String h = handler.get("", "");
            handler.close();
            return h;
        }
        return "";
    }

    private static void fixJavaSoftPrefs() {
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5097859
        Key[] keys = new Key[]{Key.LOCAL_MACHINE, Key.CURRENT_USER};
        for (Key k : keys) {
            try {
                Key js = k.open("SOFTWARE\\JavaSoft");
                if (js != null) {
                    js.create("Prefs");
                }
            } catch (Throwable x) {
                /* ignore */
                trace("failed: fixJavaSoftPrefs() ignored\n");
            }
        }
        trace("done: fixJavaSoftPrefs()\n");
    }

    private static boolean addZipegToAppPaths(Key root, String installdir, String exe) {
        {
            /* With an App Path registry entry, the system will load DLLs in the following order.
            1. The directories listed in the App Path registry key
            2. The directory where the executable module for the current process is located.
            3. The current directory.
            4. The Windows system directory. The GetSystemDirectory function retrieves the path of this directory.
            5. The Windows directory. The GetWindowsDirectory function retrieves the path of this directory.
            6. The directories listed in the PATH environment variable.
            http://www.codeguru.com/Cpp/W-P/dll/article.php/c99
            http://code.google.com/p/chromium/issues/detail?id=39994
            */
            Key appPaths = root.openOrCreate("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths");
            // 2.3.0.896 NullPointerException at com.zipeg.Registry.registerZipeg 5/6/2008
            if (appPaths != null) {
                Key zipeg = appPaths.create("Zipeg.exe");
                if (zipeg != null) {
                    zipeg.put("", exe);
                    zipeg.put("Path", installdir);
                    zipeg.close();
                    appPaths.close();
                } else {
                    trace("failed: root.open" +
                          "(\"SOFTWARE\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\App Paths\")." +
                          "create(\"zipeg.exe\")\n");
                    return false;
                }
            } else {
                trace("failed: root.open" +
                        "(\"SOFTWARE\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\App Paths\")\n");
                return false;
            }
        }
        trace("done: addZipegToAppPaths()\n");
        return true;
    }

    public static boolean registerZipeg(String installdir) {
        fixJavaSoftPrefs();
        String exe = installdir + "\\Zipeg.exe";
        if (!createApplicationKeys(Key.CURRENT_USER, installdir)) {
            return false;
        }
        if (!addZipegToAppPaths(Key.CURRENT_USER, installdir, exe)) {
            return false;
        }
        // ??? HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts
        /*
        http://www.ureader.com/message/1392987.aspx
        SystemFileAssociations is explained in the MSDN documentation.

        SystemFileAssociations
        The SystemFileAssociations keys exist to guarantee that Shell extensions are
        installed regardless of the current default PROGID or user customization.
        These keys enable Windows XP to define fallback attributes for file types
        and enable shared file associations. Supplemental verbs should be added
        under SystemFileAssociations .

        Source: PerceivedTypes, SystemFileAssociations, and Application
        Registration:
        http://msdn.microsoft.com/library/default.asp?url=/library/en-us/shellcc/platform/shell/programmersguide/shell_basics/shell_basics_extending/fileassociations/fa_perceived_types.asp
         2005 Microsoft Corporation. All rights reserved

        Whereas the Fileexts is used when you permanently associate a filetype using
        the Open-with dialog. The values (Fileexts\.nnn\Application) and
        (Fileexts\.nnn\ProgID) takes precedence over the central file associations.

        There is yet another layer of possible file associations via
        HKEY_CURRENT_USER\SOFTWARE\Classes\. This is the per-user override, that
        you've read in Q257592 article posted by Dave.

        To best explain this, HKEY_CLASSES_ROOT is nothing but a merger of
        HKEY_CURRENT_USER\SOFTWARE\Classes and HKEY_LOCAL_MACHINE\SOFTWARE\Classes\
        (It was David Candy who explained this part in simple terms so everyone can
        understand.)
         */
        {
            Key associations = getCurrentUserClasses().openOrCreate("SystemFileAssociations");
            if (associations != null) {
                Key compressed = associations.create("compressed");
                if (compressed != null) {
                    createShellCommands(compressed, exe);
                    compressed.close();
                }
                associations.close();
            }
        }
        {
            // CLASSES_ROOT/Applications
            Key applications = getCurrentUserClasses().open("Applications");
            if (applications != null) {
                Key zipeg = applications.create("Zipeg.exe");
                if (zipeg != null) {
                    createDefaultIcon(zipeg, exe);
                    createShellCommands(zipeg, exe);
                    Key supportedTypes = zipeg.create("SupportedTypes");
                    supportedTypes.put(".zip", "");
                    supportedTypes.close();
                    zipeg.close();
                }
                applications.close();
            }
        }
        {
            // IE7 way of doing the same:
            Key assoc = getCurrentUserClasses().create("Zipeg.AssocFile.ZIP");
            if (assoc != null) {
                assoc.put("", "Compressed Zip Archive");
                createDefaultIcon(assoc, exe);
                createShellCommands(assoc, exe);
                assoc.close();
            }
        }
        {
            // Legacy way of doing it:
            Key zipeg = getCurrentUserClasses().create("Zipeg");
            if (zipeg != null) {
                createDefaultIcon(zipeg, exe);
                createShellCommands(zipeg, exe);
                zipeg.close();
            }
        }
        {
            Key software = Key.CURRENT_USER.open("SOFTWARE");
            if (software != null) {
                Key zipeg = software.create("Zipeg");
                Key capabilities = zipeg.create("Capabilities");
                capabilities.put("ApplicationDescription",
                                 "Zipeg Rar and Zip file opener - the safer way to look inside archives");
                Key assoc = capabilities.create("FileAssociations");
                assoc.put(".zip", "Zipeg.AssocFile.ZIP");
                assoc.close();
                Key mime = capabilities.create("MimeAssociations");
                mime.put("application/x-zip-compressed", "Zipeg.AssocFile.ZIP");
                mime.close();
                capabilities.close();
                zipeg.close();

                Key registeredApplications = software.create("RegisteredApplications");
                if (registeredApplications != null) {
                    // on one instance of XP there is crash report of registeredApplications == null
                    registeredApplications.put("Zipeg", "Software\\Zipeg\\Capabilities");
                    registeredApplications.close();
                }
                Key classes = software.open("Classes");
                if (classes == null) {
                    classes = software.create("Classes");
                }
                if (classes != null) {
                    zipeg = classes.create("Zipeg");
                    if (zipeg != null) {
                        createDefaultIcon(zipeg, exe);
                        createShellCommands(zipeg, exe);
                        zipeg.close();
                    }
                    classes.close();
                } else {
                    software.close();
                    return false;
                }
                software.close();
            }
        }
        return true;
    }

    /* registerZipegAdmin is an onld XPway for Admins */
    public static boolean registerZipegAdmin(String installdir) {
        fixJavaSoftPrefs();
        String exe = installdir + "\\zipeg.exe";
        if (!createApplicationKeys(Key.LOCAL_MACHINE, installdir)) {
            return false;
        }
        if (!addZipegToAppPaths(Key.LOCAL_MACHINE, installdir, exe)) {
            return false;
        }
        Key associations = Key.CLASSES_ROOT.open("SystemFileAssociations");
        if (associations != null) {
            Key compressed = associations.create("compressed");
            if (compressed != null) {
                createShellCommands(compressed, exe);
                compressed.close();
            }
            associations.close();
        }
        Key zipeg;
        // CLASSES_ROOT/Applications
        Key applications = Key.CLASSES_ROOT.open("Applications");
        if (applications != null) {
            zipeg = applications.create("Zipeg.exe");
            if (zipeg != null) {
                createDefaultIcon(zipeg, exe);
                createShellCommands(zipeg, exe);
                Key supportedTypes = zipeg.create("SupportedTypes");
                supportedTypes.put(".zip", "");
                supportedTypes.close();
                zipeg.close();
            }
            applications.close();
        }
        // IE7 way of doing the same:
        Key classes = Key.LOCAL_MACHINE.open("SOFTWARE\\Classes");
        if (classes != null) {
            Key assoc = classes.create("Zipeg.AssocFile.ZIP");
            if (assoc != null) {
                assoc.put("", "Compressed Zip Archive");
                createDefaultIcon(assoc, exe);
                createShellCommands(assoc, exe);
                assoc.close();
            }
            classes.close();
        }
        // Legacy way of doing it:
        zipeg = Key.CLASSES_ROOT.create("Zipeg");
        if (zipeg != null) {
            createDefaultIcon(zipeg, exe);
            createShellCommands(zipeg, exe);
            zipeg.close();
        }
        Key software = Key.LOCAL_MACHINE.open("SOFTWARE");
        if (software != null) {
            zipeg = software.create("Zipeg");
            Key capabilities = zipeg.create("Capabilities");
            capabilities.put("ApplicationDescription",
                    "Zipeg Rar and Zip file opener");
            Key assoc = capabilities.create("FileAssociations");
            assoc.put(".zip", "Zipeg.AssocFile.ZIP");
            assoc.close();
            Key mime = capabilities.create("MimeAssociations");
            mime.put("application/x-zip-compressed", "Zipeg.AssocFile.ZIP");
            mime.close();
            capabilities.close();
            zipeg.close();

            Key registeredApplications = software.create("RegisteredApplications");
            if (registeredApplications != null) {
                // on one instance of XP there is crash report of registeredApplications == null
                registeredApplications.put("Zipeg", "Software\\Zipeg\\Capabilities");
                registeredApplications.close();
            }
            software.close();
        }
        classes = Key.LOCAL_MACHINE.open("SOFTWARE\\Classes");
        if (classes == null && software != null) {
            classes = software.create("Classes");
            software.close();
        }
        if (classes != null) {
            zipeg = classes.create("Zipeg");
            createDefaultIcon(zipeg, exe);
            createShellCommands(zipeg, exe);
            zipeg.close();
            classes.close();
        } else {
            return false;
        }
        return true;
    }

    private static void setValue(long key, String subkey, byte[] data) throws IOException {
        setValue(key, subkey, REG_BINARY, data);
    }

    private static void setValue(long key, String subkey, String s) throws IOException {
        byte[] data = new byte[(s.length() + 1) * 2];
        int k = 0;
        for (int i = 0; i < s.length(); i++) {
            int c = ((int)s.charAt(i)) & 0xFFFF;
            data[k++] = (byte)c;
            c >>>= 8;
            data[k++] = (byte)c;
        }
        setValue(key, subkey, REG_SZ, data);
    }

    private static void setValue(long key, String subkey, long v) throws IOException {
        if (v < 0 || v > 0xFFFFFFFFFL) {
            throw new IllegalArgumentException("value out of range: 0x" + Long.toHexString(v));
        }
        byte[] data = new byte[4];
        for (int i = 0; i < 4; i++) {
            data[i] = (byte)v;
            v >>>= 8;
        }
        setValue(key, subkey, REG_DWORD, data);
    }

    private static Object getValue(long key, String subkey) throws IOException {
        int[] type = new int[1];
        byte[] data = getValue(key, subkey, type);
        if (type[0] == REG_DWORD) {
            assert data.length == 4 : subkey;
            long v = 0;
            for (int i = 3; i >= 0; i--) {
                v = (v << 8) | (((int)data[i]) & 0xFF);
            }
            return new Long(v);
        } else if (type[0] == REG_SZ || type[0] == REG_EXPAND_SZ) {
            if (data.length == 1 && (data[0] == 0 || data[0] == '@' || data[0] == 0x20)) {
                return "";
            }
            assert data.length % 2 == 0 : "\"" + subkey + "\" len=" + data.length + " type=" + type[0] +
                    " data[0]=0x" + Integer.toHexString(data[0]);
            int len = data.length;
            while (len >= 2 && data.length > 2 && data[len - 1] == 0 && data[len - 2] == 0) {
                len -= 2;
            }
            if (len == 0) {
                return "";
            } else {
                len = len / 2;
                char[] text = new char[len];
                int k = 0;
                for (int i = 0; i < len; i++) {
                    int lo = ((int)data[k++]) & 0xFF;
                    int hi = ((int)data[k++]) & 0xFF;
                    text[i] = (char)((hi << 8) | lo);
                }
                return new String(text);
            }
        } else if (type[0] == REG_BINARY) {
            return data;
        } else {
            throw new IllegalArgumentException("unsupported registry type: " + type[0]);
        }
    }

    private static String getValueString(long key, String subkey) throws IOException {
        return (String)getValue(key, subkey);
    }

    private static Long getValueLong(long key, String subkey) throws IOException {
        return (Long)getValue(key, subkey);
    }

    public static String getSpecialFolder(int location) {
        loadLibrary();
        return getSpecialFolderLocation(location);
    }

    public static void trace(String s) {
        loadLibrary();
        outputDebugString(s);
    }

    public static boolean loadLibrary() {
        if (!loaded) {
            String lib = getLibraryName();
            String path = new File(".", lib).getAbsolutePath();
            try {
                System.loadLibrary(lib);
                loaded = true;
            } catch (Throwable ex) {
                System.err.println("Zipeg: " + ex.getMessage());
                Zipeg.redownload();
            }
        }
        return loaded;
    }

    public static String getLibraryName() {
        assert Util.isWindows;
        return "win32reg";
    }

    // native JNI registry bridge:

    public static final int
        CSIDL_DESKTOP                   = 0x0000,        // <desktop>
        CSIDL_INTERNET                  = 0x0001,        // Internet Explorer (icon on desktop)
        CSIDL_PROGRAMS                  = 0x0002,        // Start Menu\Programs
        CSIDL_CONTROLS                  = 0x0003,        // My Computer\Control Panel
        CSIDL_PRINTERS                  = 0x0004,        // My Computer\Printers
        CSIDL_PERSONAL                  = 0x0005,        // My Documents
        CSIDL_FAVORITES                 = 0x0006,        // <user name>\Favorites
        CSIDL_STARTUP                   = 0x0007,        // Start Menu\Programs\Startup
        CSIDL_RECENT                    = 0x0008,        // <user name>\Recent
        CSIDL_SENDTO                    = 0x0009,        // <user name>\SendTo
        CSIDL_BITBUCKET                 = 0x000a,        // <desktop>\Recycle Bin
        CSIDL_STARTMENU                 = 0x000b,        // <user name>\Start Menu
        CSIDL_MYDOCUMENTS               = CSIDL_PERSONAL, //  Personal was just a silly name for My Documents
        CSIDL_MYMUSIC                   = 0x000d,        // "My Music" folder
        CSIDL_MYVIDEO                   = 0x000e,        // "My Videos" folder
        CSIDL_DESKTOPDIRECTORY          = 0x0010,        // <user name>\Desktop
        CSIDL_DRIVES                    = 0x0011,        // My Computer
        CSIDL_NETWORK                   = 0x0012,        // Network Neighborhood (My Network Places)
        CSIDL_NETHOOD                   = 0x0013,        // <user name>\nethood
        CSIDL_FONTS                     = 0x0014,        // windows\fonts
        CSIDL_TEMPLATES                 = 0x0015,
        CSIDL_COMMON_STARTMENU          = 0x0016,        // All Users\Start Menu
        CSIDL_COMMON_PROGRAMS           = 0x0017,        // All Users\Start Menu\Programs
        CSIDL_COMMON_STARTUP            = 0x0018,        // All Users\Startup
        CSIDL_COMMON_DESKTOPDIRECTORY   = 0x0019,        // All Users\Desktop
        CSIDL_APPDATA                   = 0x001a,        // <user name>\Application Data
        CSIDL_PRINTHOOD                 = 0x001b,        // <user name>\PrintHood

        CSIDL_LOCAL_APPDATA             = 0x001c,        // <user name>\Local Settings\Applicaiton Data (non roaming)

        CSIDL_ALTSTARTUP                = 0x001d,        // non localized startup
        CSIDL_COMMON_ALTSTARTUP         = 0x001e,        // non localized common startup
        CSIDL_COMMON_FAVORITES          = 0x001f,

        CSIDL_INTERNET_CACHE            = 0x0020,
        CSIDL_COOKIES                   = 0x0021,
        CSIDL_HISTORY                   = 0x0022,
        CSIDL_COMMON_APPDATA            = 0x0023,        // All Users\Application Data
        CSIDL_WINDOWS                   = 0x0024,        // GetWindowsDirectory()
        CSIDL_SYSTEM                    = 0x0025,        // GetSystemDirectory()
        CSIDL_PROGRAM_FILES             = 0x0026,        // C:\Program Files
        CSIDL_MYPICTURES                = 0x0027,        // C:\Program Files\My Pictures

        CSIDL_PROFILE                   = 0x0028,        // USERPROFILE
        CSIDL_SYSTEMX86                 = 0x0029,        // x86 system directory on RISC
        CSIDL_PROGRAM_FILESX86          = 0x002a,        // x86 C:\Program Files on RISC

        CSIDL_PROGRAM_FILES_COMMON      = 0x002b,        // C:\Program Files\Common

        CSIDL_PROGRAM_FILES_COMMONX86   = 0x002c,        // x86 Program Files\Common on RISC
        CSIDL_COMMON_TEMPLATES          = 0x002d,        // All Users\Templates

        CSIDL_COMMON_DOCUMENTS          = 0x002e,        // All Users\Documents
        CSIDL_COMMON_ADMINTOOLS         = 0x002f,        // All Users\Start Menu\Programs\Administrative Tools
        CSIDL_ADMINTOOLS                = 0x0030,        // <user name>\Start Menu\Programs\Administrative Tools

        CSIDL_CONNECTIONS               = 0x0031,        // Network and Dial-up Connections
        CSIDL_COMMON_MUSIC              = 0x0035,        // All Users\My Music
        CSIDL_COMMON_PICTURES           = 0x0036,        // All Users\My Pictures
        CSIDL_COMMON_VIDEO              = 0x0037,        // All Users\My Video
        CSIDL_RESOURCES                 = 0x0038,        // Resource Direcotry

        CSIDL_RESOURCES_LOCALIZED       = 0x0039,        // Localized Resource Direcotry

        CSIDL_COMMON_OEM_LINKS          = 0x003a,        // Links to All Users OEM specific apps
        CSIDL_CDBURN_AREA               = 0x003b,        // USERPROFILE\Local Settings\Application Data\Microsoft\CD Burning
        CSIDL_COMPUTERSNEARME           = 0x003d,        // Computers Near Me (computered from Workgroup membership)

        CSIDL_FLAG_CREATE               = 0x8000,        // combine with CSIDL_ value to force folder creation in SHGetFolderPath()

        CSIDL_FLAG_DONT_VERIFY          = 0x4000,        // combine with CSIDL_ value to return an unverified folder path
        CSIDL_FLAG_DONT_UNEXPAND        = 0x2000,        // combine with CSIDL_ value to avoid unexpanding environment variables
        CSIDL_FLAG_NO_ALIAS             = 0x1000,        // combine with CSIDL_ value to insure non-alias versions of the pidl
        CSIDL_FLAG_PER_USER_INIT        = 0x0800,        // combine with CSIDL_ value to indicate per-user init (eg. upgrade)
        CSIDL_FLAG_MASK                 = 0xFF00;        // mask for all possible flag values

    private static final int
        // access:
        KEY_QUERY_VALUE         = 0x0001,
        KEY_SET_VALUE           = 0x0002,
        KEY_CREATE_SUB_KEY      = 0x0004,
        KEY_ENUMERATE_SUB_KEYS  = 0x0008,
        KEY_NOTIFY              = 0x0010,
        KEY_CREATE_LINK         = 0x0020,
/*
        KEY_WOW64_32KEY         = 0x0200,
        KEY_WOW64_64KEY         = 0x0100,
        KEY_WOW64_RES           = 0x0300,
*/
        SYNCHRONIZE             = 0x00100000,
        STANDARD_RIGHTS_ALL     = 0x001F0000,
/*
        STANDARD_RIGHTS_WRITE   = 0x00020000,
        STANDARD_RIGHTS_READ    = 0x00020000,
        KEY_READ = (STANDARD_RIGHTS_READ |
                    KEY_QUERY_VALUE |
                    KEY_ENUMERATE_SUB_KEYS |
                    KEY_NOTIFY)
                    &
                    ~SYNCHRONIZE,
        KEY_WRITE = (STANDARD_RIGHTS_WRITE |
                    KEY_SET_VALUE |
                    KEY_CREATE_SUB_KEY)
                    &
                    ~SYNCHRONIZE,
        KEY_EXECUTE = KEY_READ & ~SYNCHRONIZE,
*/
        KEY_ALL_ACCESS = (STANDARD_RIGHTS_ALL |
                          KEY_QUERY_VALUE |
                          KEY_SET_VALUE |
                          KEY_CREATE_SUB_KEY |
                          KEY_ENUMERATE_SUB_KEYS |
                          KEY_NOTIFY |
                          KEY_CREATE_LINK)
                          &
                          ~SYNCHRONIZE,
        // options
        REG_OPTION_NON_VOLATILE = 0x00000000,   // Key is preserved when system is rebooted
/*
        REG_OPTION_VOLATILE = 0x00000001,   // Key is not preserved when system is rebooted
        REG_OPTION_CREATE_LINK = 0x00000002,   // Created key is a symbolic link
        REG_OPTION_BACKUP_RESTORE = 0x00000004,   // open for backup or restore special access rules
        REG_OPTION_OPEN_LINK = 0x00000008,   // Open symbolic link
        // disposition
        REG_CREATED_NEW_KEY = 0x00000001,   // created New Registry Key
        REG_OPENED_EXISTING_KEY = 0x00000002,   // opened Existing Key
        // data type:
        REG_NONE                    = 0,   // No value type
*/
        REG_SZ                      = 1,   // Unicode nul terminated string
        REG_EXPAND_SZ               = 2,   // Unicode nul terminated string
                                           // (with environment variable references)
        REG_BINARY                  = 3,   // Free form binary
        REG_DWORD                   = 4;   // 32-bit number
/*
        REG_DWORD_LITTLE_ENDIAN     = 4,   // 32-bit number =same as REG_DWORD)
        REG_DWORD_BIG_ENDIAN        = 5,   // 32-bit number
        REG_LINK                    = 6,   // Symbolic Link =unicode)
        REG_MULTI_SZ                = 7,   // Multiple Unicode strings
        REG_RESOURCE_LIST           = 8,   // Resource list in the resource map
        REG_FULL_RESOURCE_DESCRIPTOR = 9,  // Resource list in the hardware description
        REG_RESOURCE_REQUIREMENTS_LIST = 10,
        REG_QWORD                   = 11,  // 64-bit number
        REG_QWORD_LITTLE_ENDIAN     = 11;  // 64-bit number =same as REG_QWORD)
*/

    //  private native static long connectRegistry(long key, String host) throws IOException;
    private native static long openKey(long key, String subkey, int access) throws IOException;
    private native static long createKey(long key, String subkey, String regclass, int options, int access,
                                         int[] disposition) throws IOException;
    private native static void closeKey(long key) throws IOException;
    private native static void flushKey(long key) throws IOException;
    private native static void deleteKey(long key, String subkey) throws IOException;
    private native static void deleteValue(long key, String subkey) throws IOException;
    private native static void setValue(long key, String subkey, int type, byte[] data) throws IOException;
    private native static byte[] getValue(long key, String subkey, int[] type) throws IOException;
    private native static void moveToRecycleBin(String abspathname) throws IOException;
    private native static void notifyShellAssociationsChanged();
    private native static void notifyShellAllChanged();
    private native static long initializeOle();
    private native static long getCurrentProcessId();
    private native static long killProcess(String name);
    private native static String getSpecialFolderLocation(int location);
    private native static void outputDebugString(String s);
    public static final int
        SEE_MASK_CLASSNAME         = 0x00000001,
        SEE_MASK_CLASSKEY          = 0x00000003,
        // Note INVOKEIDLIST overrides IDLIST
        SEE_MASK_IDLIST            = 0x00000004,
        SEE_MASK_INVOKEIDLIST      = 0x0000000c,
        SEE_MASK_ICON              = 0x00000010,
        SEE_MASK_HOTKEY            = 0x00000020,
        SEE_MASK_NOCLOSEPROCESS    = 0x00000040,
        SEE_MASK_CONNECTNETDRV     = 0x00000080,
        SEE_MASK_FLAG_DDEWAIT      = 0x00000100,
        SEE_MASK_DOENVSUBST        = 0x00000200,
        SEE_MASK_FLAG_NO_UI        = 0x00000400,
        SEE_MASK_UNICODE           = 0x00004000,
        SEE_MASK_NO_CONSOLE        = 0x00008000,
        SEE_MASK_ASYNCOK           = 0x00100000,
        SEE_MASK_HMONITOR          = 0x00200000,
        SEE_MASK_NOZONECHECKS      = 0x00800000,
        SEE_MASK_NOQUERYCLASSSTORE = 0x01000000,
        SEE_MASK_WAITFORINPUTIDLE  = 0x02000000,
        SEE_MASK_FLAG_LOG_USAGE    = 0x04000000,
        // return codes: > 32 OK
        SE_ERR_FNF              = 2,       // file not found
        SE_ERR_PNF              = 3,       // path not found
        SE_ERR_ACCESSDENIED     = 5,       // access denied
        SE_ERR_OOM              = 8,       // out of memory
        SE_ERR_DLLNOTFOUND      = 32,
        // error values for ShellExecute() beyond the regular WinExec() codes
        SE_ERR_SHARE            = 26,
        SE_ERR_ASSOCINCOMPLETE  = 27,
        SE_ERR_DDETIMEOUT       = 28,
        SE_ERR_DDEFAIL          = 29,
        SE_ERR_DDEBUSY          = 30,
        SE_ERR_NOASSOC          = 31;

    private native static long shellExec(long mask, String verb, String file, String params, String dir);
    /*
        The SEE_MASK_FLAG_DDEWAIT flag must be specified if the thread
        calling ShellExecuteEx does not have a message loop or if the thread or process
        will terminate soon after ShellExecuteEx returns.
        Under such conditions, the calling thread will not be available
        to complete the DDE conversation, so it is important that ShellExecuteEx
        complete the conversation before returning control to the caller.
        Failure to complete the conversation can result in an unsuccessful
        launch of the document.

        To include double quotation marks in lpParameters,
        enclose each mark in a pair of quotation marks, as in the following example.

        sei.lpParameters = "An example: \"\"\"quoted text\"\"\"";
        In this case, the application receives
        three parameters: An, example:, and "quoted text".
     */
}
