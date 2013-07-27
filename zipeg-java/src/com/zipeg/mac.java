package com.zipeg;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;
import java.io.*;
import java.net.*;

// http://developer.apple.com/reference/Java/index-rev-date.html
// http://developer.apple.com/documentation/Java/Reference/Java_InfoplistRef/Articles/JavaDictionaryInfo.plistKeys.html
// http://developer.apple.com/documentation/Java/Reference/Java_PropertiesRef/Articles/JavaSystemProperties.html
// http://developer.apple.com/documentation/Java/Reference/Java_VMOptionsRef/Articles/JavaVirtualMachineOptions.html
// http://developer.apple.com/documentation/Java/Reference/1.5.0/appledoc/api/index.html
// http://developer.apple.com/technotes/tn2007/tn2196.html

@SuppressWarnings({"unchecked", "UnusedDeclaration"})
public class mac {

    private static final String ApplicationEvent = "com.apple.eawt.ApplicationEvent";
    public static final boolean isMac = System.getProperty("os.name").toLowerCase().indexOf("mac os x") >= 0;
    private static boolean loaded;
    private static boolean error;
    private static Object sharedWorkspace;
    private static boolean sharedWorkspaceError;
    private static int fromAppStore;
    public static final int
    /*
        /System/Library/Frameworks/CoreServices.framework/Versions/A/Frameworks/CarbonCore.framework/Versions/A/Headers/Folders.h
    */
    // folderDomain:
    kOnSystemDisk                 = -32768, // previously was 0x8000 but that is an unsigned value whereas vRefNum is signed
    kOnAppropriateDisk            = -32767, // Generally, the same as kOnSystemDisk, but it's clearer that this isn't always the 'boot' disk.
                                            // Folder Domains - Carbon only.  The constants above can continue to be used, but the folder/volume returned will
                                            // be from one of the domains below.
    kSystemDomain                 = -32766, // Read-only system hierarchy.
    kLocalDomain                  = -32765, // All users of a single machine have access to these resources.
    kNetworkDomain                = -32764, // All users configured to use a common network server has access to these resources.
    kUserDomain                   = -32763, // Read/write. Resources that are private to the user.
    kClassicDomain                = -32762, // Domain referring to the currently configured Classic System Folder

    // folderType:

    kSystemFolderType             = fourCC("macs"), // the system folder
    kDesktopFolderType            = fourCC("desk"), // the desktop folder; objects in this folder show on the desk top.
    kSystemDesktopFolderType      = fourCC("sdsk"), // the desktop folder at the root of the hard drive), never the redirected user desktop folder
    kTrashFolderType              = fourCC("trsh"), // the trash folder; objects in this folder show up in the trash
    kSystemTrashFolderType        = fourCC("strs"), // the trash folder at the root of the drive), never the redirected user trash folder
    kWhereToEmptyTrashFolderType  = fourCC("empt"), // the "empty trash" folder; Finder starts empty from here down
    kPrintMonitorDocsFolderType   = fourCC("prnt"), // Print Monitor documents
    kStartupFolderType            = fourCC("strt"), // Finder objects (applications), documents), DAs), aliases), to...) to open at startup go here
    kShutdownFolderType           = fourCC("shdf"), // Finder objects (applications), documents), DAs), aliases), to...) to open at shutdown go here
    kAppleMenuFolderType          = fourCC("amnu"), // Finder objects to put into the Apple menu go here
    kControlPanelFolderType       = fourCC("ctrl"), // Control Panels go here (may contain INITs)
    kSystemControlPanelFolderType = fourCC("sctl"), // System control panels folder - never the redirected one), always "Control Panels" inside the System Folder
    kExtensionFolderType          = fourCC("extn"), // System extensions go here
    kFontsFolderType              = fourCC("font"), // Fonts go here
    kPreferencesFolderType        = fourCC("pref"), // preferences for applications go here
    kSystemPreferencesFolderType  = fourCC("sprf"), // System-type Preferences go here - this is always the system's preferences folder), never a logged in user's
                                                    //   On Mac OS X), items in the temporary items folder on the boot volume will be deleted a certain amount of time after their
                                                    //    last access.  On non-boot volumes), items in the temporary items folder may never get deleted.  Thus), the use of the
                                                    //    temporary items folder on Mac OS X is discouraged), especially for long lived data.  Using this folder temporarily ( like
                                                    //    to write a temporary copy of a document to during a save), after which you FSpExchangeFiles() to swap the new contents with
                                                    //    the old version ) is certainly ok), but using the temporary items folder to cache data is not a good idea.  Instead), look
                                                    //    at tmpfile() and its cousins for a better way to do this kind of thing.  On Mac OS X 10.4 and later), this folder is inside a
                                                    //    folder named ".TemporaryItems" and in earlier versions of Mac OS X this folder is inside a folder named "Temporary Items".
                                                    //    On Mac OS 9.x), items in the the Temporary Items folder are never automatically deleted.  Instead), when a 9.x machine boots
                                                    //    up the temporary items folder on a volume ( if one still exists), and is not empty ) is moved into the trash folder on the
                                                    //    same volume and renamed "Rescued Items from <diskname>".
    kTemporaryFolderType          = fourCC("temp"); // temporary files go here (deleted periodically), but don't rely on it.)

    private static final String[] voidHandles = new String[]{
        "handleAbout",
        "handleQuit",
        "handlePreferences",
        "handleOpenApplication",
        "handleReOpenApplication"
    };
    private static final String[] stringHandles = new String[]{
        "handleOpenFile",
        "handlePrintFile"
    };

    private static final String WINDOW_DOC_MODAL_SHEET = "apple.awt.documentModalSheet";
    private static final String WINDOW_FADE_DELEGATE = "apple.awt._windowFadeDelegate";
    private static final String WINDOW_FADE_IN = "apple.awt._windowFadeIn";
    private static final String WINDOW_FADE_OUT = "apple.awt._windowFadeOut";
    private static final String WINDOW_FULLSCREENABLE = "apple.awt.fullscreenable";

    /* make sure init() is called on top static section of the application main class
       (before first AWT call
    static {
        init("AppName");
    }
    */

    public static void init(String name) {
        /* it is very important that some system properties e.g. apple.awt.brushMetalLook
           are set before any code from ATW is executed. E.g. having static Dimension field
           that is initialized before setSystemLookAndFeel will make metal to disappear
           on the Macintosh. For this purpose setSystemLookAndFeel is actually called
           from static field initialization which will still not guarantee that it is
           executed before AWT initialized. If you experience lose of Brushed Metal L&F
           hunt via versions and see when AWT initialization kicked in the static section.
          */
        if (isMac) {
//          http://developer.apple.com/mac/library/documentation/Java/Reference/Java_PropertiesRef/Articles/JavaSystemProperties.html
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", name);
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.graphics.UseQuartz", "true");
            System.setProperty("apple.awt.graphics.EnableQ2DX", "true");
            System.setProperty("apple.awt.textantialiasing","true");
            System.setProperty("apple.awt.graphics.EnableLazyDrawing", "true");
            System.setProperty("apple.awt.graphics.EnableLazyDrawingQueueSize", "2");
            System.setProperty("com.apple.mrj.application.live-resize", "true");
            System.setProperty("com.apple.macos.smallTabs","false");

            System.setProperty("com.apple.mrj.application.growbox.intrudes", "false");
            System.setProperty("apple.awt.showGrowBox", "false");
            System.setProperty("apple.awt.brushMetalLook", "true");
            System.setProperty("apple.awt.brushMetalRounded",
                     10.5 <= Util.osVersion && Util.osVersion < 10.7
                    ? "false" : "true"); // buggy and unsupported on 10.5, 10.6 fixed on 10.7
            System.setProperty("apple.awt.draggableWindowBackground", "false"); // broken!

//          System.setProperty("apple.awt.SystemTray.EnableColorIcons", "true");
            System.setProperty("sun.awt.noerasebackground", "true");
            System.setProperty("sun.awt.erasebackgroundonresize", "false");
            System.setProperty("awt.nativeDoubleBuffering",
                    Util.javaVersion < 1.7 ?
                    "true" : "false"); // flickering on 1.7.0_8b4
        }
    }

    public static void enableFullScreen(Window w) {
        if (Util.osVersion >= 10.7) {
            try {
                Method m = calls.getMethod("com.apple.eawt.FullScreenUtilities.setWindowCanFullScreen",
                        new Class[]{Window.class, boolean.class});
                calls.callStatic(m, new Object[]{w, true});
            } catch (Throwable ignore) {
                // ignore
            }
            if (w instanceof RootPaneContainer && ((RootPaneContainer)w).getRootPane() != null) {
                ((RootPaneContainer)w).getRootPane().putClientProperty(WINDOW_FULLSCREENABLE, "true");
            }
        }
    }

/*  // this is buggy in Java 1.6.x on Lion (hides menu bar for good).
    public static void toggleFullScreen(Window w) {
        if (Util.osVersion >= 10.7) {
            try {
                callApplicationMethod("requestToggleFullScreen",
                        new Class[]{Window.class},
                        new Object[]{w});
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }
*/

    public static void setDocumentModified(JRootPane rp, boolean b) {
        if (isMac) {
            rp.putClientProperty("Window.documentModified", Boolean.valueOf(b));
        }
    }

    public static void setDocumentFile(JRootPane rp, File file) {
        if (isMac) {
            rp.putClientProperty("Window.documentFile", file);
        }
    }

    public static boolean setModalityType(Dialog d, String type) {
        if (Util.isMac && Util.javaVersion >= 1.6 && Util.javaVersion < 1.7) {
            assert "DOCUMENT_MODAL".equals(type) : type;
            Throwable x = null;
            try {
                Class mt = Class.forName("java.awt.Dialog$ModalityType");
                Field dm = mt.getField("DOCUMENT_MODAL");
                Method setModalityType = Dialog.class.getMethod("setModalityType", new Class[]{mt});
                setModalityType.invoke(d, dm.get(mt));

            } catch (ClassNotFoundException e) { x = e;
            } catch (NoSuchFieldException e) { x = e;
            } catch (NoSuchMethodException e) { x = e;
            } catch (IllegalAccessException e) { x = e;
            } catch (InvocationTargetException e) { x = e;
            }
            if (d instanceof JDialog) {
                JDialog jd = (JDialog)d;
                jd.getRootPane().putClientProperty("apple.awt.documentModalSheet", Boolean.TRUE);
            }
            if (x != null) {
                if (Debug.isDebug()) {
                    throw new Error(x);
                }
                return false;
            }
            try {
                Method m = calls.getMethod("sun.awt.SunToolkit.checkAndSetPolicy",
                        new Class[]{java.awt.Container.class, boolean.class});
                calls.callStatic(m, new Object[]{d, false});
            } catch (Throwable ignore) {
                // 1.7 version
                try {
                    Method m = calls.getMethod("sun.awt.SunToolkit.checkAndSetPolicy",
                            new Class[]{java.awt.Container.class});
                    calls.callStatic(m, new Object[]{d});
                } catch (Throwable does_not_matter) {
                    // ignore
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static boolean isFromAppStore() {
        if (isMac && fromAppStore == 0) {
            File codeSignature = new File(Zipeg.javaAppFolder, "../_CodeSignature");
            File plugins = new File(Zipeg.javaAppFolder, "../PlugIns");
/*
            MessageBox.show(codeSignature + " " + codeSignature.isDirectory(), "codeSignature",
                            JOptionPane.PLAIN_MESSAGE);
            MessageBox.show(plugins + " " + plugins.exists(), "plugins", JOptionPane.PLAIN_MESSAGE);
*/
            fromAppStore = codeSignature.isDirectory() &&
                           plugins.isDirectory() ? +1 : -1;
        }
        return isMac && fromAppStore > 0;
    }

    public interface EventsHandler {
        boolean handleAbout();
        boolean handleQuit();
        boolean handlePreferences();
        boolean handleOpenApplication();
        boolean handleOpenFile(String filename);
        boolean handlePrintFile(String filename);
        boolean handleReOpenApplication();
    }

    public static class EventsAdapter implements EventsHandler {
        public boolean handleAbout() { return false; }
        public boolean handleQuit() { return false; }
        public boolean handlePreferences() { return false; }
        public boolean handleOpenApplication() { return false; }
        public boolean handleOpenFile(String filename) { return false; }
        public boolean handlePrintFile(String filename) { return false; }
        public boolean handleReOpenApplication() { return false; }
    }

    public static void addListener(final EventsHandler eh) {
        if (isMac) {
            try {
                InvocationHandler handler = new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        for (String name: voidHandles) {
                            if (handleVoid(name, method, eh, args)) {
                                break;
                            }
                        }
                        for (String name: stringHandles) {
                            if (handleString(name, method, eh, args)) {
                                break;
                            }
                        }
                        return null;
                    }
                };
                Class ApplicationListener = Class.forName("com.apple.eawt.ApplicationListener");
                Object proxy = Proxy.newProxyInstance(ApplicationListener.getClassLoader(),
                                                          new Class[] { ApplicationListener },
                                                          handler);
                callApplicationMethod("addApplicationListener",
                        new Class[]{ApplicationListener},
                        new Object[]{proxy});
            } catch (ClassNotFoundException e) {
                throw new Error(e);
            }
        }
    }

    public static void setDockIconImage(java.awt.Image image) {
        if (Util.osVersion >= 10.5) {
            callApplicationMethod("setDockIconImage",
                    new Class[]{java.awt.Image.class}, new Object[]{image});
        }
    }

    public static void setEnabledPreferencesMenu(boolean b) {
        callEnabledMenu("setEnabledPreferencesMenu", b);
    }

    public static void setEnabledAboutMenu(boolean b) {
        callEnabledMenu("setEnabledAboutMenu", b);
    }

    public static boolean loadJniLibrary() {
        if (isMac && !loaded && !error) {
            try {
                System.loadLibrary(getLibraryName());
                loaded = true;
                return loaded;
            } catch (Throwable ex) {
                try {
                    System.loadLibrary("zipeg_osx"); // legacy
                    loaded = true;
                    return loaded;
                } catch (Throwable x) {
                    /* ignore */
                }
                error = true;
            }
        }
        return loaded;
    }

    public static String getLibraryName() {
        assert Util.isMac;
        String p = Util.arch.toLowerCase();
        String cpu = "powerpc".equals(p) || "ppc".equals(p) ? "ppc" : "i386";
        return "zipeg-osx-" + cpu;
    }

    public static String getCocoaApplicationForFile(String file) {
        if (isMac) {
            // The NSWorkspace.applicationForFile() does not exist starting with Snow Leopard
            // Still go thru the hoops for the older versions of OS:
            if (sharedWorkspace == null && !sharedWorkspaceError) {
                String NSWorkspace = "com.apple.cocoa.application.NSWorkspace";
                try {
                    Util.class.getClassLoader().loadClass(NSWorkspace);
                    sharedWorkspace = calls.callStatic(NSWorkspace + ".sharedWorkspace", Util.NONE);
                } catch (Throwable t) {
                    try {
                        String path = "/System/Library/Java/com/apple/cocoa" +
                                "/application/NSWorkspace.class";
                        if (new File(path).exists()) {
                            ClassLoader classLoader = new URLClassLoader(
                                    new URL[]{new File("/System/Library/Java").toURI().toURL()});
                            Class c = classLoader.loadClass(NSWorkspace);
                            // do not use Util.getDeclaredMethod because of custom classloader
                            Method m = c.getMethod("sharedWorkspace", Util.VOID);
                            sharedWorkspace = m.invoke(null, Util.NONE);
                            Debug.traceln("sharedWorkspace=" + sharedWorkspace);
                        }
                    } catch (Throwable ignore) {
                        sharedWorkspaceError = true;
                    }
                }
            }
            try {
                if (sharedWorkspace != null) {
                    // do not use Util.getDeclaredMethod because of custom classloader
                    Method applicationForFile = sharedWorkspace.getClass().getMethod(
                            "applicationForFile", Util.STRING);
                    return (String)calls.call(sharedWorkspace,
                            applicationForFile, new Object[]{file});
                }
            } catch (Throwable t) {
                /* ignore */
            }
        }
        // and if nothing worked call JNI:
        return DefaultRoleHandler.getInsance().applicationForFile(file,
                DefaultRoleHandler.kRoleAll);
    }

    public static File getHome() {
        File h = Util.fileOf(System.getProperty("user.home"));
        if (h == null || !h.isDirectory()) {
            String un = System.getProperty("user.name");
            assert un != null && un.trim().length() > 0 : "user.name=`" + un + "`";
            h = new File("/Users", un);
        }
        if (!h.isDirectory()) {
            if (!h.mkdirs()) {
                Debug.trace("failed to create " + h);
            }
        }
        return h;
    }

    public static File getTempFolder() {
        File t = Util.fileOf(findFolder(kUserDomain, kTemporaryFolderType, true));
        if (t == null || !t.isDirectory()) {
            t = Util.fileOf(System.getProperty("java.io.tmpdir"));
        }
        if (t == null || !t.isDirectory()) {
            t = new File("/tmp");
        }
        if (!t.isDirectory()) {
            if (!t.mkdirs()) {
                Debug.trace("failed to create " + t);
            }
        }
        return t;
    }

    public static File getDeskFolder() {
        File d = Util.fileOf(findFolder(kUserDomain, kDesktopFolderType, true));
        if (d == null || !d.isDirectory()) {
            d = new File(getHome(), "Desktop");
        }
        if (!d.isDirectory()) {
            if (!d.mkdirs()) {
                Debug.trace("failed to create " + d);
            }
        }
        return d;
    }

    public static File getPreferencesFolder() {
        File p;
        String up = findFolder(kUserDomain, kPreferencesFolderType, true);
        if (up != null) {
            p = new File(up);
        } else {
            p = new File(getHome(), "Library/Preferences");
        }
        Util.mkdirs(p);
        return p;
    }

    public static String findFolder(int folderDomainInt, int folderType, boolean create) {
        short folderDomain = (short)folderDomainInt;
        Class[] s = new Class[]{short.class, int.class, boolean.class};
        Object[] p = new Object[]{folderDomain, folderType, create};
        try {
            Method m = calls.getDeclaredMethod("com.apple.eio.FileManager.findFolder", s);
            return isMac && m != null ?
                    (String)calls.callStatic(m, p) : null;
        } catch (Throwable x) {
            Debug.traceln("mac.findFolder: " + x.getMessage());
            return null;
        }
   }

    public static int fourCC(String s) {
        // should fourCC work in reverse on PPC? Hell if I know!
        assert s.length() == 4;
        long cc4 = 0;
        for (int i = 0; i < s.length(); i++) {
            cc4 = (cc4 << 8) | (s.charAt(i) & 0xFF);
        }
        return (int)cc4;
    }

    private static boolean handleVoid(String name, Method method, EventsHandler eh, Object[] args) {
        if (name.equals(method.getName())) {
            String mn = eh.getClass().getName() + "." + name;
            Boolean b = (Boolean)calls.call(eh, mn, calls.VOID, calls.NONE);
            // if handler crashes result == null
            setHandled(args[0], b == null || b.booleanValue());
            return true;
        } else {
            return false;
        }
    }

    private static boolean handleString(String name, Method method, EventsHandler eh, Object[] args) {
        if (name.equals(method.getName())) {
            String filename = getFileName(args[0]);
            Boolean b = (Boolean)calls.call(eh, name, calls.STRING, new Object[]{filename});
            // if handler crashes result == null
            setHandled(args[0], b == null || b.booleanValue());
            return true;
        } else {
            return false;
        }
    }


    private static String getFileName(Object event) {
        return (String)call(event, ApplicationEvent + ".getFilename", calls.VOID, calls.NONE);
    }

    private static void setHandled(Object event, boolean e) {
        call(event, ApplicationEvent + ".setHandled", calls.BOOLEAN, b(e));
    }

    private static Object getApplication() {
        return isMac ?
                calls.callStatic("com.apple.eawt.Application.getApplication", calls.NONE) : null;
    }

    private static void callEnabledMenu(String method, boolean e) {
        callApplicationMethod(method, calls.BOOLEAN, b(e));
    }

    private static Object callApplicationMethod(String method, Class[] signature, Object[] params) {
        return isMac ? call(getApplication(), method, signature, params) : null;
    }

    private static Object call(Object that, String method, Class[] s, Object[] p) {
        return isMac ? calls.call(that, method, s, p) : null;
    }

    private static Object[] b(boolean b) {
        return new Object[]{Boolean.valueOf(b)};
    }

    private mac() {}

}
