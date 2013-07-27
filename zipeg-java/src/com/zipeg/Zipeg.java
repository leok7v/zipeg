package com.zipeg;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.nio.charset.*;
import java.util.*;
import java.util.List;
import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.beans.*;

@SuppressWarnings({"unchecked", "UnusedDeclaration"})
public final class Zipeg implements Runnable {

    public static final String APPLICATION = "Zipeg";
    static {
        mac.init(APPLICATION);
        Memory.reserveSafetyPool();
    }
    public static String[] mainArgs;
    private static ArrayList args;
    private static Set options = new HashSet();
    private static Archive archive = null;
    private static boolean associationsChecked;
    private static Iterator testArchives;
    private static boolean test;
    private static boolean exitConfirmed;
    private static long exitTime;
    private static boolean isExitPending;
    private String fileToOpen;
    private static int paintCount;
    private static int fileOpenDialog;
    private static boolean firstRun;
    private static final ArrayList recent = new ArrayList();
    private static Runnable openSample;
    private static boolean installReported;
    private static boolean dockIconSet;
    public static boolean isCtrlDown;
    public static boolean isShiftDown;
    public static boolean isAltDown;
    public static File lastFile;
    public static long lastTime;
    public static String javaAppFolder;


    private Zipeg() {
    }

    public static void main(String[] a) {
        Clock.start("main");
        mainArgs = a;
        // order of initialization is important.
        args = new ArrayList(Arrays.asList(a));
/*
        System.err.println(args.toString());
        Map<String, String> env = System.getenv();
        for (String envName : env.keySet()) {
            System.out.format("%s=%s%n", envName, env.get(envName));
        }
*/
        if (args.contains("--clean")) {
            // reset preferences to initial state
            Presets.clear();
            return;
        }
        parseOptions();
        Debug.init(options.contains("debug") || options.contains("g"));
        fixUserTimeZone();
        // add start directory to java.library.path
        findJavaAppFolder();
//      MessageBox.show(javaAppFolder, "javaAppFolder", JOptionPane.INFORMATION_MESSAGE);
        fixLibraryPath();
        setSystemLookAndFeel();
        if (Util.isWindows) {
            Registry.getInstance();
        } else if (Util.isMac && Util.osVersion >= 10.4) {
            DefaultRoleHandler.getInsance();
        }
        if (options.contains("uninstall-cleanup")) {
//          JOptionPane.showMessageDialog(null, "uninstall-cleanup");
            FileAssociations fa = new FileAssociations();
            if (fa.isAvailable()) {
                fa.setHandled(0);
//              JOptionPane.showMessageDialog(null, "setHandled(0)");
                Presets.flushNow();
            }
            System.exit(0);
        }
        if (Util.isMac) {
            moveUpstairs(); // moves Info.plist and icons upstairs
        }
        IdlingEventQueue.init();
        setWindowsSpecialFolders();
        EventQueue.invokeLater(new Zipeg());
        Clock.end("main");
    }

    private static void findJavaAppFolder() {
        String lp = System.getProperty("java.library.path");
//      MessageBox.show(lp, "java.library.path", JOptionPane.INFORMATION_MESSAGE);
        if (Util.isMac && lp != null && lp.indexOf("Zipeg.app/Contents/Java") >= 0) {
            String[] parts = lp.split(File.pathSeparator);
            for (int i = 0; parts != null && i < parts.length; i++) {
                File d = new File(parts[i]);
                if (d.isDirectory() && new File(d, "zipeg.jar").exists()) {
                    javaAppFolder = d.getAbsolutePath();
                    break;
                }
            }
        }
        if (javaAppFolder == null) {
            File d = new File(System.getProperty("user.dir"));
            if (d.isDirectory() && new File(d, "zipeg.jar").exists()) {
                javaAppFolder = d.getAbsolutePath();
            } else {
                d = new File(d, "Contents/Java");
                if (d.isDirectory() && new File(d, "zipeg.jar").exists()) {
                    javaAppFolder = d.getAbsolutePath();
                }
            }
        }
        if (javaAppFolder == null) {
            File jar = new File(Zipeg.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            if (jar.getName().equalsIgnoreCase("zipeg.jar")) {
                javaAppFolder = jar.getParent();
            }
//          MessageBox.show(javaAppFolder, "javaAppFolder", JOptionPane.INFORMATION_MESSAGE);
        }
        assert javaAppFolder != null : "failed to find /Java folder";
        assert new File(javaAppFolder).isDirectory() : javaAppFolder;
        assert new File(javaAppFolder, "zipeg.jar").exists() :
                javaAppFolder + "/zipeg.jar does not exist";
    }

    private static void fixLibraryPath() {
        String lp = System.getProperty("java.library.path");
        if (lp == null || lp.length() == 0) {
            lp = javaAppFolder;
        } else if (lp.indexOf(javaAppFolder) < 0) {
            lp = javaAppFolder + File.pathSeparator + lp;
        }
        // see:
        // http://stackoverflow.com/questions/5419039/is-djava-library-path-equivalent-to-system-setpropertyjava-library-path
        try {
            // This enables the java.library.path to be modified at runtime
            // From a Sun engineer at http://forums.sun.com/thread.jspa?threadID=707176
            // TODO: may become a problem in jdk 1.7 or 1.8
            Field usr_paths = ClassLoader.class.getDeclaredField("usr_paths");
            if (usr_paths != null) {
                usr_paths.setAccessible(true);
                String[] paths = (String[])usr_paths.get(null);
                if (paths == null) {
                    paths = new String[0];
                }
                for (int i = 0; i < paths.length; i++) {
                    if (javaAppFolder.equals(paths[i])) {
                        return;
                    }
                }
                String[] ext = new String[paths.length + 1];
                System.arraycopy(paths, 0, ext, 1, paths.length);
                ext[0] = javaAppFolder;
                usr_paths.set(null, ext);
/*
                paths = (String[])usr_paths.get(null);
                for (int i = 0; i < paths.length; i++) {
                    MessageBox.show(paths[i], "usr_paths[" + i + "]", JOptionPane.INFORMATION_MESSAGE);
                }
*/
            }
        } catch (IllegalAccessException e) {
            Debug.trace("WARNING: failed to get permissions to set library path");
        } catch (NoSuchFieldException e) {
            Debug.trace("WARNING: failed to get field handle to set library path");
        } catch (Throwable x) {
            Debug.trace("WARNING: failed to get field handle to set library path: " + x.getMessage());
        }
        System.setProperty("java.library.path", lp);
/*
        System.out.println("java.library.path=" + System.getProperty("java.library.path"));
        MessageBox.show(System.getProperty("java.library.path").replaceAll(":", "\n"), "java.library.path", JOptionPane.INFORMATION_MESSAGE);
*/
    }

    public static boolean isExitPending() {
        return isExitPending;
    }

    public static boolean inProgress() {
        MainFrame mf = MainFrame.getInstance();
        return mf != null && mf.inProgress();
    }

    static void testCrashLog() {
        // testing crash log UI
        try {
            boolean b = new File("./test-crash.txt").delete();
            Debug.traceln("test-crash.txt deleted: " + b);
            b = new File("./test-crash.txt").createNewFile();
            assert b : "failed to create: ./test-crash.txt";
        } catch (IOException e) {
            e.printStackTrace();
        }
        setDockIcon();
        CrashLog.report("2.x.0.123456 ArrayIndexOutOfBoundsException: " +
                "10 at com.zipeg.IdlingEventQueue.dispatchEvent", "./test-crash.txt");
    }


    public void run() {
/*
        testCrashLog();
*/
/*      // after reading http://www.javatester.org history of versions
        // I decided to comment out this code as being premature...
        if (Util.isWindows && Util.javaVersion < 1.6) {
            MessageBox.show(
                    "<html><body>" +
                    "Zipeg cannot continue because your Java runtime is out of date.<br>" +
                    "Please update your Java runtime environment from<br>" +
                    "<a href=\"http://www.java.com\">http://www.java.com</a> " +
                    "to most recent version." +
                    "</body></html>", "Zipeg",
                    JOptionPane.ERROR_MESSAGE);
            Util.openUrl("http://www.java.com");
            System.exit(1);
        }
*/
        Clock.start("run");
        UIManager.put("FileChooser.readOnly", Boolean.TRUE);
        checkCrasher();
        if (Util.isWindows) {
            Registry.getInstance().initializeOLE();
        }
        firstRun = options.contains("first-run");
        firstRun = firstRun || Presets.get("zipeg.uuid", null) == null;
        createGuid();
        if (firstRun && (args == null || args.size() == 0)) {
            openSample = new Runnable() {
                public void run() {
                    commandFileOpen(Util.getCanonicalPath(new File(".", "sample.zip")));
                    openSample = null;
                }
            };
        }
        if (!firstRun && Updater.getUpdateFile().canRead()) {
            updateDownloaded(Updater.getUpdateFile());
        }
        if (firstRun && Util.isWindows) {
            registerWindows();
        }
        checkMinDiskSpace();
        String failedLib = null;
        if (!Z7.loadLibrary()) {
            failedLib = Z7.getLibraryName();
        }
        if (failedLib == null) {
            if (Util.isWindows) {
                if (!Registry.loadLibrary()) {
                    failedLib = Registry.getLibraryName();
                }
            } else {
                if (!mac.loadJniLibrary()) {
                    failedLib = mac.getLibraryName();
                }
            }
        }
        if (failedLib != null) {
            redownload("failed to load: \"" + failedLib + "\"");
        }
        Actions.addListener(this);
        if (getArgumentCount() > 0) {
            fileToOpen = getArgument(0);
        }
        final MainFrame frame = new MainFrame() {
            public void paint(Graphics g) {
                super.paint(g);
                if (paintCount == 0) {
                    Util.invokeLater(100, new Runnable(){
                        public void run() { afterFirstPaint(); }
                    });
                }
                paintCount++;
            }
        };
        assert !frame.isVisible();
        addMacListeners(); // must be called after frame is created
        mac.enableFullScreen(frame);
        FileDialogs.preLoadForMac();
        loadRecent();
        if (Util.isWindows) {
            workaroundMenuDropShadowBorder();
            workaroundDnDAutoscrollCursorHysteresis();
        }
        if (firstRun) {
            // first update in few days from installation date
            Presets.putLong("nextUpdate", System.currentTimeMillis() + Updater.DAYS);
            Presets.flushNow();
//          Util.sleep(2 * 1000);
//          JOptionPane.showMessageDialog(null, "first run", "Zipeg Setup", JOptionPane.INFORMATION_MESSAGE);
        }
        Updater.cleanUpdateFiles();
        showMainFrame();
        if (hasOption("test")) {
            test();
        }
        setupDropTarget();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
            new KeyEventDispatcher() {
                public boolean dispatchKeyEvent(KeyEvent e) {
                    isCtrlDown = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
                    isShiftDown = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
                    isAltDown = (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0;
                    return false; // all other KeyEvents continue down the chain
                }
            }
        );
        Clock.end("run");
/*
        Actions.postEvent("setMessage", "Long long long long test. Long long long long test. " +
                "Long long long long test. Long long long long test. ");
*/
        // updateFailed();
    }

    private void checkCrasher() {
        File lastCrash = lastCrash();
        if (options.contains("report-crash") && args.size() >= 2 || lastCrash != null) {
            try {
                setDockIcon();
                Presets.putLong("nextUpdate", System.currentTimeMillis() - 1); // check for update on next run
                Presets.sync();
                if (lastCrash != null) {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(new FileInputStream(lastCrash), Charset.forName("UTF-8")));
                    CrashLog.report(br.readLine(), Util.getCanonicalPath(lastCrash));
                    cleanCrashes();
                } else {
                    CrashLog.report((String)args.get(0), (String)args.get(1));
                    Util.delete(new File((String)args.get(1)));
                }
            } catch (Throwable x) {
                // ignore
            }
            if (lastCrash == null) {
                System.exit(153);
            }
        }
    }

    private static File lastCrash() {
        String logs[] = Util.getTmp().list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("zipeg.crash.") && name.endsWith(".log");
            }
        });
        if (logs != null && logs.length > 0) {
            Arrays.sort(logs);
            return new File(Util.getTmp(), logs[logs.length - 1]);
        } else {
            return null;
        }
    }

    private static void cleanCrashes() {
        String logs[] = Util.getTmp().list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("zipeg.crash.") && name.endsWith(".log");
            }
        });
        if (logs != null && logs.length > 0) {
            for (int i = 0; i < logs.length; i++) {
                // Debug.traceln("will delete: " + new File(Util.getTmp(), logs[i]));
                Util.delete(new File(Util.getTmp(), logs[i]));
            }
        }
    }

    private void afterFirstPaint() {
        checkLicense();
        checkAssociations();
        if (fileToOpen != null) {
            final String file = fileToOpen;
            fileToOpen = null;
            IdlingEventQueue.invokeOnIdle(new Runnable() { // give UI time to paint itself
                public void run() {
                    open(new File(file));
                }
            });
        } else {
            Updater.checkForUpdate(false);
        }
        if (firstRun && (args == null || args.size() == 0) && openSample != null) {
            Util.invokeLater(1500, openSample);
        } else {
            if (Presets.getBoolean("update.run", false)) {
                Util.invokeLater(1000, new Runnable(){
                    public void run() { Donate.askForDonation(); }
                });
            }
            Presets.putBoolean("update.run", false);
        }
        if (Util.isMac) {
            Util.invokeLater(200, new Runnable(){
                public void run() { setDockIcon(); }
            });
        }
        if (firstRun) {
            Actions.postEvent("setMessage", "Zipeg is installed and ready to be used.");
        }
    }

    public static void setDockIcon() {
        if (Util.isMac && !dockIconSet && Util.osVersion >= 10.6) {
            Image image = Resources.getImage("zipeg128x128");
            mac.setDockIconImage(image);
            dockIconSet = true;
        }
    }

    private static void createGuid() {
        // Only once create installation guid
        if (Presets.get("zipeg.uuid", null) == null) {
            Presets.switchToUserSettings(); // use file
            Presets.put("zipeg.uuid", Util.uuid());
            Presets.flushNow();
        }
        if ("".equals(Presets.get("zipeg.install.date", ""))) {
            Date now = new Date(System.currentTimeMillis());
            Debug.trace("now=" + now.toString());
            Presets.put("zipeg.install.date", now.toString());
        }
    }

    private static void checkLicense() {
        if (mac.isFromAppStore()) {
            Presets.putBoolean("licenseAccepted", true);
        }
        boolean b = Presets.getBoolean("licenseAccepted", false);
        if (!b && !Dialogs.isShown()) {
            if (!Util.isWindows) {
                // on Win32 license is accepted at the installer
                License.showLicence(true);
                firstRun = true;
            }
            Presets.putBoolean("licenseAccepted", true);
            Presets.flushNow();
        }
    }

    private static void registerWindows() {
        String installdir = javaAppFolder;
        if (!Registry.registerZipeg(installdir)) {
            MessageBox.show(
                    "<html><body>Zipeg installation failed<br><br>" +
                    "<b>Please reboot your computer before making another attempt to install.</b>" +
                    "</body></html>", "Zipeg Setup",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        removeOldInstallationOfZipeg();
    }

    private static void removeOldInstallationOfZipeg() {
        try {
            String ud = javaAppFolder;
            File d = new File("C:\\Program Files\\Zipeg");
            File z = new File(d, "zipeg.exe");
            if (ud != null && ud.contains("\\Zipeg\\Application") && z.exists() && z.canWrite()) {
                if (Util.delete(z)) {
                    Util.rmdirs(d);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void addMacListeners() {
        if (Util.isMac) {
            assert MainFrame.getInstance() != null;
            mac.setEnabledPreferencesMenu(true);
            mac.setEnabledAboutMenu(true);
            mac.addListener(new mac.EventsHandler() {

                public boolean handleAbout() {
                    Zipeg.commandHelpAbout();
                    return true;
                }

                public boolean handleQuit() {
                    return Zipeg.commandFileExit(false);
                }

                public boolean handlePreferences() {
                    Zipeg.commandToolsOptions();
                    return true;
                }

                public boolean handleOpenApplication() {
                    Zipeg.commandOpenApplication();
                    return true;
                }

                public boolean handleOpenFile(final String filename) {
                    // avoid stack overflow when commandFileOpen shows UI and
                    // Finder starts to call handleOpenFile again and again and again...
                    IdlingEventQueue.invokeLater(new Runnable() {
                        public void run() {
                            Zipeg.commandFileOpen(filename);
                        }
                    });
                    return true;
                }

                public boolean handlePrintFile(String filename) {
                    Zipeg.commandFilePrint(filename);
                    return true;
                }

                public boolean handleReOpenApplication() {
                    Zipeg.commandReOpenApplication();
                    return true;
                }

            });
        }
    }

    private void setupDropTarget() {
        DropTargetAdapter dta = new DropTargetAdapter() {

            public void dragOver(DropTargetDragEvent dtde) {
                if (dtde != null) {
                    if (dtde.getCurrentDataFlavorsAsList().contains(DataFlavor.javaFileListFlavor) &&
                        dtde.getTransferable() != null) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    } else {
                        dtde.rejectDrag();
                    }
                }
            }

            public void drop(DropTargetDropEvent dtde) {
                if (dtde != null) {
                    try {
                        Transferable t = dtde.getTransferable();
                        if (t != null) {
                            dtde.acceptDrop(DnDConstants.ACTION_COPY);
                            List files = (List)t.getTransferData(DataFlavor.javaFileListFlavor);
                            if (files != null && files.size() == 1) {
                                File file = (File)files.get(0);
                                if (file.exists() && !file.isDirectory() &&
                                    Util.isArchiveFileType(Util.getCanonicalPath(file))) {
                                    open(file);
                                }
                            }
                        }
                    } catch (UnsupportedFlavorException e) {
                        /* ignore */
                    } catch (IOException e) {
                        /* ignore */
                    }
                }
            }
        };
        MainFrame.getInstance().addDropTargetAdapter(dta);
    }

    private static void setWindowsSpecialFolders() {
        if (Util.isWindows) {
            Util.setSpecialFolders(new Util.SpecialFolders(){
                public File getAppData() {
                    return Util.fileOf(Registry.getSpecialFolder(Registry.CSIDL_APPDATA));
                }
                public File getLocalAppData() {
                    return Util.fileOf(Registry.getSpecialFolder(Registry.CSIDL_LOCAL_APPDATA));
                }
                public File getDesktopDirectory() {
                    return Util.fileOf(Registry.getSpecialFolder(Registry.CSIDL_DESKTOPDIRECTORY));
                }
                public File getDocuments() {
                    return Util.fileOf(Registry.getSpecialFolder(Registry.CSIDL_MYDOCUMENTS));
                }
                public long getPID() {
                    return Registry.getMyProcessId();
                }
            });
            if (Debug.isDebug()) {
                Registry.trace(Util.dump());
            }
        }
    }

    private static void checkMinDiskSpace() {
        if (!checkDiskSpace()) {
            MessageBox.show(
                    "<html><body>Not enough free disk space left.<br>" +
                    "Zipeg cannot continue.<br>Try to empty " +
                    (Util.isMac ? "Trash" : "Recycle") + " Bin,<br>" +
                    "and to restart the application." +
                    "</body></html>", "Zipeg",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    @SuppressWarnings({"ConstantConditions"})
    private static boolean checkDiskSpace() {
        final int REQUIRED = 32 * Util.MB;
        File file = null;
        try {
            file = File.createTempFile("test", "tmp");
            file.deleteOnExit();
            try {
                long kb = DiskSpace.freeSpaceKb(Util.getCanonicalPath(file));
                Util.delete(file);
                return kb * Util.KB > REQUIRED;
            } catch (Throwable ignore) {
                /* ignore */
            }
            Class c = Class.forName("java.io.FileSystem");
            Field su = c.getField("SPACE_USABLE");
            su.setAccessible(true);
            final int SPACE_USABLE = su.getInt(null);
            Method getFileSystem = c.getMethod("getFileSystem", Util.VOID);
            getFileSystem.setAccessible(true);
            Object fs = getFileSystem.invoke(c, Util.NONE);
            Method getSpace = fs.getClass().getMethod("getSpace", File.class, int.class);
            getSpace.setAccessible(true);
            Long space = (Long)getSpace.invoke(fs, file, new Integer(SPACE_USABLE));
            Util.delete(file);
            return space.longValue() > REQUIRED;
        } catch (Throwable ignore) {
            // ClassNotFoundException, NoSuchMethodException, IllegalAccessException
            // InvocationTargetException, NativeMethodNotFound
            // ignore.printStackTrace();
        }
        try {
            if (file == null) {
                file = File.createTempFile("test", "tmp");
            }
            FileOutputStream os = new FileOutputStream(file);
            FileChannel out = os.getChannel();
            out.position(REQUIRED);
            ByteBuffer bb = ByteBuffer.allocate(1);
            out.write(bb, REQUIRED);
            out.close();
            os.close();
            Util.delete(file);
            return true;
        } catch (IOException iox) {
            if (file != null) {
                Util.delete(file);
            }
            return false;
        }
    }

    private void test() {
        File f = new File(Util.getHome(), "zipeg.test.txt");
        if (f.exists()) {
            try {
                ArrayList files = new ArrayList();
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                for (;;) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    File a = new File(line);
                    if (!a.isDirectory() || a.canRead()) {
                        files.add(new File(line));
                    }
                }
                test = files.size() > 0;
                testArchives = files.iterator();
            } catch (IOException e) {
                throw new Error(e);
            }
        }
    }

    public static String getRecent(int ix) {
        return ix < recent.size() ? (String)recent.get(ix) : null;
    }

    private static void parseOptions() {
        for (Iterator i = args.iterator(); i.hasNext();) {
            String opt = (String)i.next();
            if (opt.startsWith("--")) {
                options.add(opt.substring(2));
                i.remove();
            } else if (opt.startsWith("-")) {
                options.add(opt.substring(1));
                i.remove();
            }
        }
    }

    public boolean hasOption(String option) {
        return options.contains(option);
    }

    public int getArgumentCount() {
        return args.size();
    }

    public String getArgument(int i) {
        return ((String)args.get(i)).trim();
    }

    public void updateCommandState(Map m) {
        Boolean enabled = Boolean.valueOf(!Dialogs.isShown() && openSample == null && fileOpenDialog == 0);
        MainFrame mf = MainFrame.getInstance();
        boolean visible = mf != null && mf.isVisible();
        m.put("commandFileOpen", enabled);
        m.put("commandFileNew", enabled);
        m.put("commandFileExit", Boolean.TRUE);
        m.put("commandToolsOptions", enabled);
        m.put("commandHelpLicense", enabled);
        m.put("commandHelpIndex", Boolean.TRUE);
        m.put("commandHelpWeb", Boolean.TRUE);
        m.put("commandLike", Boolean.TRUE);
        m.put("commandHelpDonate", enabled);
        m.put("commandHelpSupport", Boolean.TRUE);
        m.put("commandHelpAbout", enabled);
        m.put("commandHelpCheckForUpdate", enabled);
        m.put("commandWindowMinimize", Boolean.valueOf(visible));
        m.put("commandWindowZoom", Boolean.valueOf(visible));
        m.put("commandFileClose", Boolean.valueOf(!Dialogs.isShown() && visible));
        mac.setEnabledAboutMenu(enabled.booleanValue());
        mac.setEnabledPreferencesMenu(enabled.booleanValue());
    }

    public static void commandHelpCheckForUpdate() {
        Updater.checkForUpdate(true);
    }

    private static void exit(boolean quit) {
        FileChooser.dispose();
        Dialogs.dispose();
        MainFrame mf = MainFrame.getInstance();
        checkAssociations();
        if (mf != null) {
            mf.setVisible(false);
            commandFileClose(true);
            mf.dispose();
        }
        File cache = Util.getCacheDirectory(false);
        if (cache != null && cache.isDirectory()) {
            Util.rmdirs(cache);
        }
        Presets.flushNow();
        if (quit) {
            EventQueue.invokeLater(new Runnable() {
                public void run() { System.exit(0); }
            });
        }
    }

    /** Immediate return from commandFileExit on Mac OS X will quit
     *  the application w/o saving latest presets and other exit
     *  time housekeeping.
     *  nestedDispatch uses zero size off the screen dialog to dispatch
     *  pending events before application quits.
     */
    private static void nestedDispatch() {
        class D extends Dialog {
            public D(Frame owner) {
                super(owner);
                addComponentListener(new ComponentAdapter() {
                    public void componentShown(ComponentEvent e) {
                        D.this.dispose();
//                      Debug.traceln("nestedDispatch: dispose()");
                    }
                });
            }
            public Dimension getMaximumSize() {
                return new Dimension(0, 0);
            }
        }
        Frame f = new Frame();
        f.setUndecorated(true);
        f.setBounds(-16000, -16000, 0, 0);
        D d = new D(f);
        d.setModal(true);
        d.setUndecorated(true);
        d.setBounds(-16000, -16000, 0, 0);
        d.pack();
        MainFrame mf = MainFrame.getInstance();
        if (mf != null) {
            mf.setVisible(false);
            mf.setBounds(-16000, -16000, 0, 0);
        }
        d.setVisible(true);
//      Debug.traceln("return from nestedDispatch");
    }

    private static boolean confirmExit() {
        if (exitTime == 0) {
            exitTime = System.currentTimeMillis();
        } else if (!exitConfirmed && System.currentTimeMillis() > exitTime + 2 * 1000) {
            exitConfirmed = true;
            String exiting = Util.isMac ? "Quiting" : "Exiting";
            String exit = Util.isMac ? "quit" : "exit";
            int i = JOptionPane.showConfirmDialog(MainFrame.getTopFrame(),
                "<html><body>" + exiting + " will cancel an operation in progress.<br>" +
                "This may leave partially extracted (corrupted) files behind.<br><br>" +
                "Are you sure you want Zipeg to " + exit + " now?</body></html>",
                "Zipeg: Operation In Progress",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            return i == JOptionPane.YES_OPTION;
        }
        return false;
    }

    public static boolean askToCancelArchiveProcessing() {
        int i = JOptionPane.showConfirmDialog(MainFrame.getTopFrame(),
            "<html><body>This will cancel an operation in progress.<br>" +
            "This may leave partially extracted (corrupted) files behind.<br><br>" +
            "Are you sure you want Zipeg to cancel operation in progress now?</body></html>",
            "Zipeg: Operation In Progress",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return i == JOptionPane.YES_OPTION;
    }

    public static boolean commandFileExit(final boolean quit) {
        Dialogs.dispose();
//      Debug.traceln("commandFileExit");
        isExitPending = true;
        MainFrame mf = MainFrame.getInstance();
        if (mf == null) {
            Util.cleanupCaches();
            installed();
            return true;
        } else if (!inProgress() || confirmExit()) {
            closeArchive();
            EventQueue.invokeLater(new Runnable(){
                // give others last chance to save preferences
                public void run() { Util.cleanupCaches(); installed(); exit(quit); }
            });
            if (Util.isMac) {
                nestedDispatch();
            }
            return true;
        } else {
            // There is no known way to disable Quit menu item on Mac OS X
            // just do nothing while operation is in progress
            Util.invokeLater(250, new Runnable(){
                public void run() { Actions.postEvent("commandFileExit"); }
            });
        }
        return false;
    }

    private static void installed() {
        if (firstRun && !installReported) {
            installReported = true;
            String url = "http://www.zipeg.com/installed." +
                    (Util.isMac ? "mac" : "win") + ".html" + urlAppendInfo("installed");
            Util.openUrl(url);
        }
    }

    public static void commandFileExit() {
        commandFileExit(true);
    }

    private static void checkAssociations() {
        FileAssociations fa = new FileAssociations();
        if (fa.isAvailable() && !associationsChecked) {
            if (Util.isMac || Util.isWindows && options.contains("first-run")) {
                associationsChecked = true;
                long handled = fa.getHandled();
                if (handled == 0 || handled == 1 && // only zip
                    (!Presets.getBoolean("zipOnlyOK", false))) {
                    fa.askHandleAll();
                    Presets.putBoolean("zipOnlyOK", true);
                    Presets.sync();
                }
            }
        }
    }

    public static void commandToolsOptions() {
//      Debug.traceln("commandToolsOptions");
        if (!Dialogs.isShown()) {
            Settings.showPreferences();
        }
    }

    public static void commandHelpAbout() {
//      Debug.traceln("commandHepAbout");
        if (!Dialogs.isShown()) {
            About.showMessage();
        }
    }

    public static void commandHelpIndex() {
//      Debug.traceln("commandHepIndex");
        Util.openUrl("http://www.zipeg.com/faq.html");
    }

    public static void commandHelpWeb() {
//      Debug.traceln("commandHepWeb");
        Util.openUrl("http://www.zipeg.com");
    }

    public static void commandWindowMinimize() {
        Debug.traceln("commandWindowMinimize");
        MainFrame mf = MainFrame.getInstance();
        if (mf != null && Util.isMac && mf.isVisible()) {
            mf.setState(Frame.ICONIFIED);
        }
    }

    public static void commandWindowZoom() {
        Debug.traceln("commandWindowZoom");
        MainFrame mf = MainFrame.getInstance();
        if (mf != null && Util.isMac && mf.isVisible()) {
            mf.setState(Frame.MAXIMIZED_BOTH);
/*
            GraphicsConfiguration config = mf.getGraphicsConfiguration();
            Rectangle usableBounds = SunGraphicsEnvironment.getUsableBounds(config.getDevice());
            mf.setMaximizedBounds(new Rectangle(0, 0, usableBounds.width, usableBounds.height));
*/
            mf.setExtendedState((mf.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH ?
                    JFrame.NORMAL : JFrame.MAXIMIZED_BOTH);
        }
    }

    public static void commandLike() {
//      Debug.traceln("commandHepWeb");
        int like_count = Presets.getInt("like.count", 0);
//        Util.openUrl("http://www.zipeg.com/index.html?fb_like=highlight");
// TODO: Not sure:
        Util.openUrl("https://www.facebook.com/sharer/sharer.php?u=http%3A%2F%2Fwww.zipeg.com");
// TODO: One above is not exactly Like but may be even better. commandShare?
        Presets.putInt("like.count", like_count + 1);
    }

    public static void commandHelpDonate() {
//      Debug.traceln("commandHepDonate");
        int donate_count = Presets.getInt("donate.count", 0);
        Util.openUrl(Donate.getDonateURL());
        Presets.putInt("donate.count", donate_count + 1);
    }

    public static void commandHelpSupport() {
//      Debug.traceln("commandHepDonate");
        Util.openUrl("http://www.zipeg.com/support.html");
    }

    public static void commandHelpLicense() {
        License.showLicence(false);
    }

    public static void commandEditCut() {
        Debug.traceln("commandEditCut");
    }

    public static void commandEditCopy() {
        Debug.traceln("commandEditCopy");
    }

    public static void commandEditCopyPath() {
        Debug.traceln("commandEditCopy");
    }

    public static void commandEditPaste() {
        Debug.traceln("commandEditPaste");
    }

    public static void commandFileNew() {
        Debug.traceln("commandFileNew - not implemented");
    }

    public static void commandFilePrint() {
        Debug.traceln("commandFilePrint");
    }

    public static void commandFilePrint(String filename) {
        Debug.traceln("commandFilePrint(" + filename + ")");
    }

    public static void updateAvailable(Object param) {
        Object[] p = (Object[])param;
        updateAvailable((Integer)p[0], (String)p[1], (String)p[2], (String)p[3], (Boolean)p[4]);
    }

    private static int updating;

    /**
     * updateAvailable event handler is invoked from Updater.checkForUpdate
     * @param rev  revision
     * @param ver version
     * @param url URL of file to download
     * @param msg message
     * @param now update was requested by UI?
     */
    public static void updateAvailable(Integer rev,
            String ver, String url, String msg, Boolean now) {
        assert IdlingEventQueue.isDispatchThread();
        if (updating > 0) {
            return;
        }
        updating++;
        // Sample message with Angry Birds reference:
        // msg = "Zipeg Pro is available " +
        //       "<a href=\"macappstore://itunes.apple.com/app/id403961173?mt=12\">here</a>";
        // or:
        // http://itunes.apple.com/search?entity=macSoftware&term=Sparrow
        // returns json with "artistId":413053902, that can be fed into macappstore:// schema
        if (rev.intValue() <= Util.getRevision()) {
            if (now.booleanValue()) {
                MessageBox.show(
                        "<html><body>Your version of Zipeg is up to date." +
                        "</body></html>", "Zipeg: Check for Updates",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } else if (askUpdate(ver, msg)) {
            if (mac.isFromAppStore()) {
                Util.openUrl("macappstore://showUpdatesPage");
            } else {
                Updater.download(url);
            }
        }
        updating--;
    }

    private static void updated() {
        int update_count = Presets.getInt("update.count", 0);
        Presets.putInt("update.count", update_count + 1);
        Presets.putBoolean("update.run", true);
    }

    /**
     * updateDownloaded event handler is invoked after update has been downloaded
     * @param param - file into which update has been downloaded
     */
    public static void updateDownloaded(Object param) {
        File file = (File)param;
        assert IdlingEventQueue.isDispatchThread();
        if (!file.canRead()) {
            Updater.cleanUpdateFiles();
            return;
        }
        if (!confirmRestart()) {
            return;
        }
        if (Util.isWindows) {
            try {
                // kill all other instances of Zipeg
                Registry.killProcessByName("zipeg.exe");
                Runtime.getRuntime().exec(Util.getCanonicalPath(file));
                updated();
                Presets.flushNow();
                System.exit(0);
            } catch (Throwable e) {
                System.err.println("Zipeg: " + e.getMessage());
                updateFailed();
            }
        } else {
            File wd = new File(Util.getCanonicalPath(new File(".")));
            String location = "CopyAndRestart.class";
            InputStream i = null;
            FileOutputStream o = null;
            try {
                i = Resources.getResourceAsStream(location);
                File car = new File(".", "com/zipeg/CopyAndRestart.class");
                boolean b = Util.delete(car);
                if (!b || car.exists()) {
                    car.deleteOnExit();
                    updateFailed();
                    return; // will install later...
                }
                Util.mkdirs(car.getParentFile());
                Util.createNewFile(car);
                o = new FileOutputStream(car);
                byte[] buff = Util.readBytes(i);
                o.write(buff);
                Util.close(i);
                i = null;
                Util.close(o);
                o = null;
                String java = Util.getJava();
                Runtime.getRuntime().exec(new String[]{ java, "com.zipeg.CopyAndRestart",
                                          Util.getCanonicalPath(file),
                                          Util.getCanonicalPath(wd), java});
                updated();
                Presets.flushNow();
                System.exit(0);
            } catch (IOException e) {
                System.err.println("Zipeg: " + e.getMessage());
                updateFailed();
            } finally {
                Util.close(i);
                Util.close(o);
            }
        }
    }

    private static boolean confirmRestart() {
        assert IdlingEventQueue.isDispatchThread();
        return !Dialogs.isShown() && MessageBox.show(
                "<html><body>New version has been downloaded successfully.<br>" +
                "Do you want to restart the application now?<br></body></html>",
                "Zipeg: Update Downloaded", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static boolean askUpdate(String ver, String msg) {
        assert IdlingEventQueue.isDispatchThread();
        return !Dialogs.isShown() && MessageBox.show(
                "<html><body>new version " + ver + " is available<br><br>" +
                "<b>Do you want to download it now" +
                (mac.isFromAppStore() ? " from App Store" : "") +
                "?</b><br>" + msg +
                "</body></html>",
                "Zipeg: Update Available",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    public static void commandReopenArchive() {
        if (archive != null && archive.getName() != null) {
             commandFileOpen(archive.getName());
        }
        showMainFrame();
    }

    public static void commandFileOpen(String filename) {
//      Debug.traceln("commandFileOpen(" + filename + ")");
        if (filename != null && new File(filename).exists()) {
            open(new File(filename));
        } else {
            commandFileOpen();
        }
    }

    public static void commandFileOpen() {
        MainFrame mf = MainFrame.getInstance();
        if (mf == null || inProgress() || Dialogs.isShown()) {
            return;
        }
        File chosen;
        try {
            fileOpenDialog++;
            chosen = Util.isMac ? FileDialogs.openArchiveOnMac() : FileDialogs.openArchive();
        } catch (Throwable x) {
            MessageBox.show(
                        "<html><body>Something went wrong. " +
                        "Try to double click file you want to open,<br>" +
                        "or right mouse click the file and choose " +
                        "\"Open with Zipeg\" or,<br>" +
                        "simply drag and drop file into Zipeg or onto Zipeg icon.<br>" +
                        "</body></html>", "Zipeg: File Open Failed",
                        JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            fileOpenDialog--;
        }
        if (chosen != null) {
            final File file = chosen;
            System.gc(); // to force dialog close and repaint
            Util.invokeLater(250, new Runnable(){
                public void run() {
                    MainFrame mf = MainFrame.getInstance();
                    JRootPane r = mf != null ? mf.getRootPane() : null;
                    if (r != null) {
                        r.paintImmediately(r.getBounds());
                        open(file);
                    }
                }
            });
        }
    }

    public void commandActionsExtract() {
        assert IdlingEventQueue.isDispatchThread();
//      Debug.traceln("commandActionsExtract");
        MainFrame mf = MainFrame.getInstance();
        if (archive != null && mf != null && !inProgress()) {
            List list = mf.getSelected();
            if (list != null && list.size() == 0) {
                list = null; // extract all
            }
            if (list == null || Flags.getFlag(Flags.EXTRACT_WHOLE)) {
                list = null;
            } else if (!Flags.getFlag(Flags.EXTRACT_SELECTED)) {
                // the assert below fires if registry does not work properly.
                // TODO: investigate. For now commented out.
                // assert Flags.getFlag(Flags.EXTRACT_ASK);
                int i = askSelected(list);
                if (i < 0) {
                    return;
                } else if (i > 0) {
                    list = null;
                }
            }
            extractList(list, Flags.getFlag(Flags.CLOSE_AFTER_EXTRACT));
        }
    }

    private static boolean equalsOrExtends(String dest, String zipname) {
        if (dest.equals(zipname)) {
            return true;
        }
        if (!dest.startsWith(zipname)) {
            return false;
        }
        String s = dest.substring(zipname.length());
        if (s.length() >= 2) {
            if (s.charAt(0) != '.') {
                return false;
            }
            for (int i = 1; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean equalsIgnoreCaseSansExtensions(String file, String child) {
        if (!file.equalsIgnoreCase(child)) {
            int ix1 = file.lastIndexOf('.');
            if (ix1 > 0) {
                file = file.substring(0, ix1);
            }
            int ix2 = child.lastIndexOf('.');
            if (ix2 > 0) {
                child = child.substring(0, ix2);
            }
        }
        return file.equalsIgnoreCase(child);
    }

    private static File sans(File d, String childName) {
        if (childName == null || !equalsIgnoreCaseSansExtensions(d.getName(), childName)) {
            return d;
        }
        File p = d.getParentFile();
        return p == null || TrashBin.isSystemFolder(new File(p, childName)) ? d : p;
    }

    public static void extractList(List list, boolean quit) {
        MainFrame mf = MainFrame.getInstance();
        if (mf == null || archive == null || inProgress()) {
            return;
        }
        ContentPane cp = (ContentPane)mf.getContentPane();
        String destination = mf.getDestination();
        if (Util.isEmpty(destination)) {
            cp.chooseDestination();
            destination = mf.getDestination();
        }
        if (Util.isEmpty(destination)) {
            return;
        }
        String childName = null;
        String rootname = null;
        String archivename = null;
        String zipname = null;
        File dst;
        if (Flags.getFlag(Flags.APPEND_ARCHIVE_NAME)) {
            TreeElement root = (TreeElement)archive.getRoot();
            rootname = root.getName();
            archivename = new File(archive.getName()).getName();
            zipname = archivename;
            int ix = zipname.lastIndexOf(".");
            if (ix > 0) {
                zipname = zipname.substring(0, ix);
            }
            ix = zipname.lastIndexOf(".part");
            if (ix > 0) {
                zipname = zipname.substring(0, ix);
            }
            int childcount = root.getChildrenCount();
            TreeElement child = childcount == 1 ? (TreeElement)root.getChildren().next() : null;
//          boolean isChildDir = child != null && child.isDirectory();
            childName = child != null && child.isDirectory() ? child.getName() : null;
            // do not append twice
            File d = new File(destination);
            if (zipname.equals(childName)) {
                dst = sans(d, childName);
            } else if (equalsOrExtends(d.getName(), zipname)) {
                dst = sans(d, childName);
            } else {
                dst = sans(new File(destination, zipname), childName);
                if (dst.exists() && !dst.isDirectory()) {
                    dst = new File(destination, rootname);
                }
                if (dst.exists() && !dst.isDirectory()) {
                    dst = new File(destination, archivename);
                }
                if (dst.exists() && !dst.isDirectory()) {
                    dst = new File(destination, zipname + ".unziped");
                }
            }
        } else {
            dst = new File(destination);
        }
        assert dst != null : "destination=" + destination +
                " childName=" + childName + " zipname=" + zipname +
                " archivename=" + archivename + " rootname=" + rootname;
        if (dst.exists() && !dst.isDirectory()) {
            Actions.reportError("Destination: " + destination + "\n" +
                    "Cannot extract over existing file. " +
                    "Please specify another destination " +
                    "folder to extract to.");
            return;
        }
        if (!dst.exists()) {
            if (!askCreateFolder(dst)) {
                return;
            }
            String error = null;
            try {
                boolean b = dst.mkdirs();
                if (!b) {
                    error = "create folder error";
                    if (dst.getParent() != null) {
                        error += ".\nCheck that " + dst.getParent() + "" + " exists\n" +
                                "and you have sufficient rights\n" +
                                "to create folders at that location.";
                    }
                }
            } catch (SecurityException iox) {
                error = iox.getMessage();
            }
            if (error != null) {
                Actions.reportError("Failed to create folder:\n" + dst +
                        "\n" + error);
                return;
            }
        }
        if (archive != null) { // may become null with close on completion and all the "ask" business
            cp.setProgress(0.01f);
            archive.extract(list, dst, quit);
        }
        if (Flags.getFlag(Flags.LOCATION_LAST)) {
            mf.saveDestination();
        }
    }

    private int askSelected(List selected) {
        if (selected == null) {
            return +1; // whole archive
        }
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("<html><body>Extract:</body></html>"));
        JRadioButton sel = new JRadioButton("<html><body>" +
                                            Util.plural(selected.size(),
                                            "<b>selected</b> item"));
        JRadioButton all = new JRadioButton("<html><body><b>whole</b> archive</body></html>");
        ButtonGroup choice = new ButtonGroup();
        choice.add(all);
        choice.add(sel);
        panel.add(all);
        panel.add(sel);
        if (Presets.getBoolean("extractPreferSelected", false)) {
            choice.setSelected(sel.getModel(), true);
        } else {
            choice.setSelected(all.getModel(), true);
        }
        JCheckBox cbx = new JCheckBox("Always do the same without asking.");
        panel.add(cbx);
        int i = JOptionPane.showConfirmDialog(MainFrame.getTopFrame(), panel,
            "Zipeg: What to Extract?",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (cbx.isSelected()) {
            Flags.removeFlag(Flags.EXTRACT_ASK|Flags.EXTRACT_WHOLE|Flags.EXTRACT_SELECTED);
            Flags.addFlag(all.isSelected() ? Flags.EXTRACT_WHOLE : Flags.EXTRACT_SELECTED);
        }
        Presets.putBoolean("extractPreferSelected", sel.isSelected());
        Presets.sync();
        if (i == JOptionPane.CANCEL_OPTION) {
            return -1;
        }
        return all.isSelected() ? +1 : 0;
    }

    private static boolean askCreateFolder(File dst) {
        int i = JOptionPane.YES_OPTION;
        if (Flags.getFlag(Flags.PROMPT_CREATE_FOLDERS)) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.add(new JLabel("<html><body>Folder <b>" + dst + "</b> does not exist.<br><br>" +
                "Do you want to create it?<br>&nbsp;</body></html>"));
            JCheckBox cbx = new JCheckBox("Always create folders without asking.");
            panel.add(cbx);
            i = MessageBox.show(panel, "Zipeg: Create Folder",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (cbx.isSelected()) {
                Flags.removeFlag(Flags.PROMPT_CREATE_FOLDERS);
            }
        }
        return i == JOptionPane.YES_OPTION;
    }

    public static Archive getArchive() {
        return archive;
    }

    public static void archiveOpened(Object param) {
        assert IdlingEventQueue.isDispatchThread();
        assert param != null;
        if (archive != null) {
            final Archive toClose = archive;
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    toClose.close();
                    Cache.getInstance().clear();
                }
            });
        }
        archive = (Archive)param;
        recent.add(0, archive.getName());
        while (recent.size() > 8) {
            recent.remove(recent.size() - 1);
        }
        saveRecent();
        showMainFrame();
        if (test) {
            IdlingEventQueue.invokeOnIdle(new Runnable(){
                public void run() {
                    if (testArchives.hasNext()) {
                        File f = (File)testArchives.next();
                        Debug.traceln("testing: " + f);
                        open(f);
                    }
                }
            });
        }
        if (!Flags.getFlag(Flags.DONT_OPEN_NESTED)) {
            TreeElement r = archive != null ? (TreeElement)archive.getRoot() : null;
            if (r != null && r.getDescendantFileCount() == 1) {
                TreeElement c = getSingleDescendant(r);
                if (c != null) {
//                  Debug.traceln("single descendant " + c);
                    boolean composite = Util.isCompositeArchive(archive.getName());
                    String fn = c.getFile().toLowerCase();
                    // do not open .jar, .chm inside other archives
                    boolean a = Util.isArchiveFileType(c.getFile()) &&
                            // special "do not open nested" cases:
                            !fn.endsWith(".jar") &&
                            !fn.endsWith(".chm") &&
                            !fn.endsWith(".iso");
                    if (composite || a) {
//                      Debug.traceln("extract and open " + c);
                        archive.extractAndOpen(c);
                    }
                }
            }
        }
    }

    private static TreeElement getSingleDescendant(TreeElement root) {
        for (Iterator i = root.getChildren(); i.hasNext(); ) {
            TreeElement child = (TreeElement)i.next();
            if (child.getDescendantFileCount() > 0) {
                return getSingleDescendant(child);
            } else {
                if (!child.isDirectory()) {
                    return child;
                }
            }
        }
        return null;
    }

    private static void commandFileClose(boolean exiting) {
        assert IdlingEventQueue.isDispatchThread();
        MainFrame mf = MainFrame.getInstance();
        if (mf != null) {
            if (Util.isMac) {
                mf.setVisible(false);
            }
            if (!exiting) {
                Updater.checkForUpdate(false);
            }
        }
//      Debug.traceln("commandFileClose");
        EventQueue.invokeLater(new Runnable(){
            // give DirectoryTree chance to get rid of all ArchiveTreeNodes
            public void run() {
                closeArchive();
            }
        });
    }

    private static void closeArchive() {
        if (archive != null) {
            archive.close();
            Cache.getInstance().clear();
            archive = null;
            Util.cleanupCaches();
            System.gc();
        }
    }


    public static void commandFileClose() {
        commandFileClose(false);
    }

    public void extractionCompleted(Object param) { // {error, quit} or {null, quit}
        MainFrame mf = MainFrame.getInstance();
        Object[] p = (Object[])param;
        String error = (String)p[0];
        Boolean quit = (Boolean)p[1];
        if (error == null && quit.booleanValue()) {
            Util.invokeLater(2000, new Runnable() {
                public void run() {
                    Actions.postEvent("commandFileExit");
                }
            });
        } else if (mf != null) {
            mf.toFront();
            Updater.checkForUpdate(false);
        }
    }

    // Mac specific:

    public static void commandReOpenApplication() {
//      Debug.traceln("commandReOpenApplication");
        showMainFrame();
        Updater.checkForUpdate(false);
    }

    public static void commandOpenApplication() {
//      Debug.traceln("commandOpenApplication");
        showMainFrame();
        Updater.checkForUpdate(false);
    }

    private static void showMainFrame() {
        MainFrame mf = MainFrame.getInstance();
        if (mf != null) {
            mf.setVisible(true);
            mf.repaint(); // dirty fix fof StatusBar first paint problem
            mf.toFront();
        }
    }

    private static void open(File f) {
        MainFrame mf = MainFrame.getInstance();
        if (f == null || mf ==  null || !f.exists()) {
            return;
        }
        showMainFrame();
        checkLicense();
        if (f.isDirectory()) {
            if (f.getName().toLowerCase().endsWith(".download")) {
                MultipartHeuristics.stillDownloading(f);
            } else {
                MessageBox.show(
                        "<html><body>Unable to open \"" + f.getName() + "\"<br>" +
                        "because it is a folder.<br><br>" +
                        "</body></html>", "Zipeg: Cannot open Folder",
                        JOptionPane.ERROR_MESSAGE);
            }
            return;
        }
        final File file = MultipartHeuristics.checkMultipart(f);
        if (file == null || !MultipartHeuristics.checkDownloadComplete(file)) {
            return;
        }
        if (inProgress()) {
            long delta = Math.abs(System.currentTimeMillis() - lastTime);
            if (delta < 60000 && sameFile(f)) {
                return; // double double double click from impatient user
            }
            if (!askToCancelArchiveProcessing()) {
                return;
            } else {
                closeArchive();
            }
        }
        int count = Presets.getInt("extract.count", 0);
        Presets.putInt("extract.count", count + 1);
        Presets.sync();
        ContentPane cp = (ContentPane)mf.getContentPane();
        cp.setProgress(0.01f);
        lastFile = file;
        lastTime = System.currentTimeMillis();
        EventQueue.invokeLater(new Runnable(){
            public void run() {
                ArchiveProcessor.open(file);
            }
        });
    }

    private static boolean sameFile(File f) {
        if (lastFile == null) {
            return false;
        }
        String last = lastFile.getAbsolutePath().toLowerCase();
        String file = f.getAbsolutePath().toLowerCase();
        long t0 = lastFile.lastModified();
        long t1 = f.lastModified();
        if (last.equals(file)) {
            return t0 == t1;
        }
        last = Util.getCanonicalPath(lastFile);
        file = Util.getCanonicalPath(f);
        last = last == null ? null : last.toLowerCase();
        file = file == null ? null : file.toLowerCase();
        return Util.equals(last, file) &&  t0 == t1;
    }

    private static void saveRecent() {
        for (int i = 0; i < recent.size(); i++) {
            String name = (String)recent.get(i);
            Presets.put("recent.archive." + i, name);
            Presets.sync();
        }
    }

    private static void loadRecent() {
        boolean done = false;
        recent.clear();
        for (int i = 0; !done; i++) {
            String name = Presets.get("recent.archive." + i, null);
            if (name != null) {
                recent.add(name);
            } else {
                done = true;
            }
        }
    }

    private void workaroundMenuDropShadowBorder() {
        if (Util.javaVersion < 1.4212) {
            return; // pre 1.4.2.3 has shadows broken
        }
        Border defaultBorder = UIManager.getBorder("PopupMenu.border");
        UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(
                new DropShadowBorder(Color.WHITE, 0, false),
                defaultBorder));
        AWTEventListener listener = new AWTEventListener() {
            public void eventDispatched(AWTEvent e) {
                if (e.getID() == ContainerEvent.COMPONENT_ADDED) {
                    Component c = ((ContainerEvent)e).getChild();

                    if (c instanceof JPopupMenu) {
                        JPopupMenu menu = (JPopupMenu)c;
                        menu.setOpaque(true);
                        ((JComponent)menu.getParent()).setOpaque(false);
                        ((JComponent)menu.getComponent()).setOpaque(false);
                    }

                    if (c instanceof JSeparator && c.getParent() instanceof JPopupMenu) {
                        JSeparator separator = (JSeparator)c;
                        separator.setOpaque(true);

                    }
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.CONTAINER_EVENT_MASK);
    }

    /*
    A bit more complex. Somebody replaces Integer(10) by String("10")
    and setDesktopProperty(null) by String("win.drag.x") later in the game.
    */
    private static void setDnDAutoscrollCursorHysteresis() {
        try {
            Toolkit t = Toolkit.getDefaultToolkit();
            Object value = t.getDesktopProperty("DnD.Autoscroll.cursorHysteresis");
            // in 1.7-ea String("win.drag.x")
            if (!(value instanceof Integer)) {
                Method setDesktopProperties = Toolkit.class.getDeclaredMethod(
                        "setDesktopProperty", String.class, Object.class);
                setDesktopProperties.setAccessible(true);
                setDesktopProperties.invoke(t, "DnD.Autoscroll.cursorHysteresis", new Integer(10));
            }
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    private static void workaroundDnDAutoscrollCursorHysteresis() {
        // for 1.4 it is DnD.gestureMotionThreshold = Integer(5)
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4407536
        if (Util.javaVersion >= 1.7) {
            Toolkit t = Toolkit.getDefaultToolkit();
            t.addPropertyChangeListener("DnD.Autoscroll.cursorHysteresis", new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    Debug.traceln(evt.getPropertyName() + "(" + evt.getOldValue() + ")" + "=" + evt.getNewValue());
                    setDnDAutoscrollCursorHysteresis();
                }
            });
            setDnDAutoscrollCursorHysteresis();
            Debug.traceln("getDesktopProperty(\"DnD.Autoscroll.cursorHysteresis\") = " +
                           t.getDesktopProperty("DnD.Autoscroll.cursorHysteresis"));
        }
    }

    private static boolean setSystemLookAndFeel() {
        System.setProperty("swing.aatext","true");
        System.setProperty("awt.useSystemAAFontSettings","on");
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6342514
// Bad idea: completely resets XP L&F to classic
//      System.setProperty("swing.noxp","true");
        try {
            // /Users/leo/code/zipeg/xcode/src/PlugIns/runtime.jre/Contents/Home/lib/
            // System.loadLibrary("libfreetype.6.dylib");
            Toolkit.getDefaultToolkit().setDynamicLayout(true);
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        } catch (Throwable e) {
            throw new Error(e);
        }
        return true;
    }

    public static void redownload() {
        redownload(null);
    }

    private static void redownload(String error) {
        File cd = new File(javaAppFolder);
        if (Util.isMac && !cd.isDirectory()) {
            MessageBox.show(
                    "Zipeg has been moved from current location.\n" +
                    "Please restart Zipeg from the folder it has been moved to\n" +
                    "(e.g. /Applications/Zipeg.app).\n" +
                    "Application will quit now.",
                    "Zipeg: Warning", JOptionPane.WARNING_MESSAGE);
            File zipeg = new File("/Applications/Zipeg.app");
            if (zipeg.isDirectory()) {
               long time = zipeg.lastModified();
               long now = System.currentTimeMillis();
               if (Math.abs(time - now) < 60 * 1000 * 1000) {
                   try {
                       Runtime.getRuntime().exec("open -a /Applications/Zipeg.app");
                   } catch (IOException e) {
                       // ignore
                   }
               }
            }
        } else {
            MessageBox.show(
                    "This installation of Zipeg has been corrupted.\n" +
                    (error == null ? "" : error) + "\n" +
                    "Please download and reinstall Zipeg\n" +
                    "from http://www.zipeg.com\n" +
                    "Application will quit now.",
                    "Zipeg: Fatal Error", JOptionPane.ERROR_MESSAGE);
            Util.openUrl("http://www.zipeg.com");
        }
        System.exit(1);
    }

    private static void updateFailed() {
        MessageBox.show(
                "<html><body>Automatic update cannot proceed.<br>" +
                "Please download and install new version manually<br>" +
                "from <a href=\"http://www.zipeg.com\">http://www.zipeg.com</a><br>" +
                "We apologise for the inconvenience.</body></html>",
                "Zipeg: Automatic Update Failed", JOptionPane.ERROR_MESSAGE);
        Util.openUrl("http://www.zipeg.com" + urlAppendInfo("updateFailed"));
    }

    static String urlAppendInfo(String label) {
        long ms = Util.getTotalPhysicalMemorySize();
        String mem = ms < 0 ? "" :
                (".m" + ms / Util.MB + "MB");
        return "?q=" + label + (Util.isMac ? ".mac" : ".win") + Util.osVersion +
                    "_" + Util.arch + "_x" + Runtime.getRuntime().availableProcessors() +
                    ".j" + Util.javaVersion + ".v" + Util.getVersion() + mem;
    }

    private static void moveUpstairs() {
        File cd = new File(javaAppFolder);
        String [] files = new String[] {"Archive.icns", "Zipeg.icns", "Info.plist", "Icon\r"};
        String [] to = new String[] {"..", "..", "../..", "../../.."};
        for (int i = 0; i < files.length; i++) {
            String file = files[i];
            File f = new File(cd, file);
            if (f.exists()) {
                boolean b = f.renameTo(new File(new File(cd, to[i]), file));
                if (!b) {
                    Debug.traceln("failed to move " + file);
                }
            }
        }
    }

    private static void fixUserTimeZone() {
        // fix for "java.lang.NullPointerException TimeZone.parseCustomTimeZone" (10 occurrences)
        // all reported crashes have "user.timezone=<empty string>"
        String utz = System.getProperty("user.timezone");
        if (Util.isEmpty(utz)) {
            TimeZone dtz = TimeZone.getDefault();
            if (dtz != null && !Util.isEmpty(dtz.getID())) {
                System.setProperty("user.timezone", dtz.getID());
            } else {
                // most Zipeg customers are in GMT-8:00
                System.setProperty("user.timezone", "GMT-08:00");
            }
            TimeZone tz = TimeZone.getTimeZone(System.getProperty("user.timezone"));
            assert tz != null : "user.timezone=" + System.getProperty("user.timezone");
        }
    }

}

