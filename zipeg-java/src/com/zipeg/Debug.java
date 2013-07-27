package com.zipeg;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.io.*;
import java.awt.*;

public final class Debug {

    private static boolean debug;
    private static ExceptionHandler crashHandler;
    private static Thread.UncaughtExceptionHandler ueh;
    private static final Set exclude = new HashSet() {{
        add("user.name");
        add("java.runtime.name");
        add("swing.handleTopLevelPaint");
        add("sun.awt.exception.handler");
        add("sun.awt.noerasebackground");
        add("java.vendor.url.bug");
        add("file.separator");
        add("swing.aatext");
        add("java.vendor");
        add("sun.awt.erasebackgroundonresize");
        add("java.specification.vendor");
        add("java.vm.specification.version");
        add("java.awt.printerjob");
        add("java.class.version");
        add("sun.management.compiler");
        add("java.specification.name");
        add("user.variant");
        add("java.vm.specification.vendor");
        add("line.separator");
        add("java.endorsed.dirs");
        add("java.awt.graphicsenv");
        add("java.vm.specification.name");
        add("sun.java.launcher");
        add("path.separator");
        add("java.vendor.url");
        add("java.vm.name");
        add("java.vm.vendor");
        add("socksNonProxyHosts");
        add("http.nonProxyHosts");
        add("ftp.nonProxyHosts");
        add("sun.cpu.isalist");
        add("gopherProxySet");
    }};

    private Debug() {
    }

    static void init(boolean b) {
        debug = b;
        crashHandler = new ExceptionHandler();
        if (Util.javaVersion >= 1.7) {
            ueh = new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    crashHandler.handle(e);
                }
            };
            try { Thread.setDefaultUncaughtExceptionHandler(ueh); } catch (Throwable ignore) { /* ignore */ }
            try { Thread.currentThread().setUncaughtExceptionHandler(ueh); } catch (Throwable ignore) {
                /* ignore */
            }
        } else {
            System.setProperty("sun.awt.exception.handler", ExceptionHandler.class.getName());
        }
    }

    public static void traceln(String s) {
        if (isDebug()) {
            System.err.println(s);
        }
    }

    public static void trace(String s) {
        if (isDebug()) {
            System.err.print(s);
        }
    }

    public static void traceln() {
        if (isDebug()) {
            System.err.println();
        }
    }

    public static void printStackTrace(String msg, Throwable t) {
        if (isDebug()) {
            traceln(msg);
            t.printStackTrace();
        }
    }

    public static void printStackTrace(Throwable t) {
        if (isDebug()) {
            t.printStackTrace();
        }
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void execute(Runnable r) {
        try {
            r.run();
        } catch (Throwable x) {
            if (crashHandler != null) {
                crashHandler.handle(x);
            } else {
                x.printStackTrace();
            }
        }
    }

    public static class ExceptionHandler {

        private static boolean reported;

        public void handle(final Throwable x) {
            if (this != crashHandler) {
                Memory.releaseSafetyPool();
                crashHandler.handle(x);
            } else if (!reported) {
                reported = true;
                report(x);
            }
        }

        public static void report(Throwable x) {
            int crash_count = 0;
            try {
                crash_count = Presets.getInt("crash.count", 0);
                crash_count++;
                Presets.putInt("crash.count", crash_count);
            } catch (Throwable ignore) {
                // ignore
            }
            if (Util.isMac) {
                String cd = Zipeg.javaAppFolder;
                if (cd == null || cd.toLowerCase().indexOf(".dmg/") >= 0) {
                    System.exit(0);
                }
            }
            Throwable cause = x;
            for (; ;) {
                if (cause instanceof InvocationTargetException &&
                    ((InvocationTargetException)cause).getTargetException() != null) {
                    cause = ((InvocationTargetException)cause).getTargetException();
                } else if (cause.getCause() != null) {
                    cause = cause.getCause();
                } else {
                    break;
                }
            }
            // noinspection CallToPrintStackTrace
            cause.printStackTrace();
            StringWriter sw = new StringWriter();
            PrintWriter pw = null;
            String method;
            try {
                pw = new PrintWriter(sw) {
                    public void println() { super.write('\n'); }
                };
                Archive a = Zipeg.getArchive();
                if (a != null) {
                    String name = new File(a.getName()).getName();
                    pw.println("Archive: \"" + name + "\"\n");
                }
                String archive = Zipeg.getRecent(0) != null ? Zipeg.getRecent(0) : "";
                if (!"".equals(archive)) {
                    String name = new File(archive).getName();
                    pw.println("Last Archive: \"" + name + "\"\n");
                }
                if (Zipeg.mainArgs != null && Zipeg.mainArgs.length > 0) {
                    pw.print("args[]= ");
                    for (int i = 0; i < Zipeg.mainArgs.length; i++) {
                        pw.print("\"" + Zipeg.mainArgs[i] + "\"" + " ");
                    }
                    pw.println();
                }
                method = printStackTrace(cause, pw);
            } finally {
                if (pw != null) {
                    pw.close();
                }
            }
            StringBuffer sb = sw.getBuffer();
            sb.append("\n\n");
            Map p = System.getProperties();
            for (Iterator j = p.keySet().iterator(); j.hasNext();) {
                Object key = j.next();
                if (!exclude.contains(key)) {
                    sb.append(key).append("=").append(p.get(key)).append("\n");
                }
            }
            sb.append("\n").append(Util.dump());
            sb.append(ls());
            String sep = File.separatorChar == '\\' ? "\\\\" : "/";
            String user = sep + p.get("user.name");
            String body = sb.toString();
            body = body.replaceAll(user, sep + "<user>");
            String subject = Util.getVersion() + " " + shorten(cause.toString()) +
                             (method != null ? " at " + shorten(method) : "");
            if (isIgnorable(cause, body)) {
                reported = false;
                return;
            }
            try {
                String install_date = Presets.get("zipeg.install.date", "");
                int extract_count = Presets.getInt("extract.count", 0);
                int donate_count = Presets.getInt("donate.count", 0);
                int update_count = Presets.getInt("update.count", 0);
                String uuid = Presets.get("zipeg.uuid", "");
                body += "\r\ninstalled: " + install_date +
                        " [x" + extract_count + ":d" + donate_count +
                        ":c" + crash_count + ":u" + update_count + "] " +
                        uuid + "\r\n";
            } catch (Throwable ignore) {
                // ignore
            }
            report(subject, body);
            File cache = Util.getCacheDirectory(false);
            if (cache != null && cache.isDirectory() &&
                    Util.getCanonicalPath(cache).toLowerCase().contains("zipeg")) {
                Util.rmdirs(cache);
            }
            System.exit(1);
        }

        private static StringBuilder ls() {
            StringBuilder sb = new StringBuilder(1024);
            try {
                if (Zipeg.javaAppFolder != null) {
                    File ud = new File(Zipeg.javaAppFolder);
                    sb.append("\n").append(Util.getCanonicalPath(ud));
                    if (ud.isDirectory()) {
                        sb.append(" ").append(new Date(ud.lastModified()));
                    }
                    sb.append("\n");
                    File[] files = ud.listFiles();
                    for (int i = 0; files != null && i < files.length; i++) {
                        sb.append(pad(files[i].length(), 9)).append(" \t");
                        sb.append(new Date(files[i].lastModified())).append(" \t");
                        sb.append(files[i].getName()).append("\n");
                    }
                }
                File temp = File.createTempFile("test", "tmp");
                long free = DiskSpace.freeSpaceKb(Util.getCanonicalPath(temp));
                if (!Util.delete(temp)) {
                    temp.deleteOnExit();
                }
                long desktop = DiskSpace.freeSpaceKb(Util.getCanonicalPath(Util.getDesktop()));
                // KB / KB == MB
                if (desktop == free) {
                    sb.append("Free Disk Space: ").append(free / 1024).append("MB\n");
                } else {
                    sb.append("Free Disk Space: tmp: ").append(free / 1024).append("MB ");
                    sb.append("\tdesktop: ").append(free / 1024).append("MB\n");
                }
            } catch (Throwable ignore) {
                // ignore
            }
            return sb;
        }

        private static String pad(long value, int digits) {
            String s = Long.toString(value);
            while (s.length() < digits) {
                s = " " + s;
            }
            return s;
        }

        /**
         * writes crashlog into a file and starts itself on another instantiation of JVM to send it.
         * @param subject of email to send
         * @param body of email with crash log
         */
        private static void report(String subject, String body) {
            try {
                File file = new File(Util.getTmp(), "zipeg.crash." + System.currentTimeMillis() + ".log");
                String path = Util.getCanonicalPath(file);
                file = new File(path);
                Util.createNewFile(file);
                PrintStream out = new PrintStream(new FileOutputStream(file));
                out.print(subject + "\n\n" + body);
                Util.close(out);
                String[] a = null;
                if (new File(Util.getJava()).isFile()) {
                    a = new String[7];
                    a[0] = Util.getJava();
                    a[1] = "-cp";
                    a[2] = "zipeg.jar";
                    a[3] = "com.zipeg.Zipeg";
                    a[4] = "--report-crash";
                    a[5] = subject;
                    a[6] = path;
                } else if (true || mac.isFromAppStore()) {
                    String app = Util.getCanonicalPath(
                            new File(System.getProperty("java.home"), "../../../../.."));
                    a = new String[6];
                    a[0] = "/usr/bin/open";
                    a[1] = "Zipeg.app";
                    a[2] = "--args";
                    a[3] = "--report-crash";
                    a[4] = subject;
                    a[5] = path;
                }
                if (a != null) {
                    Process p = Runtime.getRuntime().exec(a);
                    if (isDebug()) {
                        p.waitFor();
                        traceln("exit = " + p.exitValue());
                    }
                }
            } catch (Throwable x) {
                x.printStackTrace();
            }
        }

        private static String printStackTrace(Throwable t, PrintWriter s) {
            String method = null;
            String first = null;
            // noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (t) {
                s.println(t);
                StackTraceElement[] trace = t.getStackTrace();
                for (int i = 0; i < trace.length; i++) {
                    String f = "" + trace[i];
                    if (first == null) {
                        first = trace[i].getClassName() + "." + trace[i].getMethodName();
                    }
                    if (f.startsWith("com.zipeg.")) {
                        f = f.substring("com.zipeg.".length());
                        if (method == null) {
                            method = trace[i].getClassName() + "." + trace[i].getMethodName();
                        }
                    }
                    else if (f.startsWith("java.util.")) {
                        f = f.substring("java.util.".length());
                    }
                    else if (f.startsWith("java.io.")) {
                        f = f.substring("java.io.".length());
                    }
                    else if (f.startsWith("java.awt.")) {
                        f = f.substring("java.awt.".length());
                    }
                    else if (f.startsWith("javax.swing.")) {
                        f = f.substring("javax.swing.".length());
                    }
                    f = f.replaceAll(".java:", ":");
                    if (f.indexOf("EventDispatchThread.pumpOneEventForHierarchy") >= 0) {
                        break; // cut bottom of the stack
                    }
                    s.println(f);
                }
                Throwable ourCause = t.getCause();
                if (ourCause != null) {
                    s.println("caused by: ");
                    printStackTrace(t, s);
                }
            }
            return method == null ? first : method;
        }

        private static String shorten(String message) {
            if (message.startsWith("java.lang.")) {
                return message.substring("java.lang.".length());
            } else if (message.startsWith("java.util.")) {
                return message.substring("java.util.".length());
            } else if (message.startsWith("java.io.")) {
                return message.substring("java.io.".length());
            } else if (message.startsWith("javax.swing.")) {
                return message.substring("javax.swing.".length());
            } else {
                return message;
            }
        }

        /*
         * Determines if the exception can be ignored.
         * https://www.limewire.org/fisheye/browse/limecvs/gui/com/limegroup/gnutella/gui/DefaultErrorCatcher.java?r=1.12
         * https://www.limewire.org/jira/browse/GUI-235
         */
        private static boolean isIgnorable(Throwable x, String msg) {
            if (msg.indexOf("plaf.basic.BasicListUI$Actions.adjustScrollPositionIfNecessary") >= 0) {
                return true; // happened 3 times in a past 3 years. No repro
            }
            if (x instanceof StackOverflowError &&
                msg.indexOf("DefaultKeyboardFocusManager.typeAheadAssertions") >= 0) {
                return true; // FocusManager goes crazy (rarely but shurely). Cannot repro.
            }
            if (msg.indexOf("RepaintManager") >= 0) {
                return true;
            }
            if (msg.indexOf("sun.awt.RepaintArea.paint") >= 0) {
                return true;
            }
            // http://groups.google.com.pk/group/Google-Web-Toolkit/browse_thread/thread/44df53c5c7ef6df2/2b68528d3fb70048?lnk=raot
            if (msg.indexOf("apple.awt.CGraphicsEnvironment.displayChanged") >= 0) {
                return true;
            }
            // http://lists.apple.com/archives/java-dev/2004/May/msg00192.html
            // http://www.thinkingrock.com.au/forum/viewtopic.php?p=4279&sid=0abfa9f43f7a52d33e3960f575973c5c
            // http://www.jetbrains.net/jira/browse/IDEADEV-8692
            // http://www.jetbrains.net/jira/browse/IDEADEV-9931
            // http://groups.google.com/group/comp.soft-sys.matlab/browse_thread/thread/5c56f6d6d08cb5e0/db152a7adc8b8e25?lnk=raot
            // display manager on OSX goes out of whack
            if (msg.indexOf("apple.awt.CWindow.displayChanged") >= 0) {
                return true;
            }
            if (x instanceof ArrayIndexOutOfBoundsException) {
                if (msg.indexOf("plaf.basic.BasicTabbedPaneUI.getTabBounds") >= 0)
                    return true;
            }
            if (x instanceof IndexOutOfBoundsException) {
                if (msg.indexOf("DefaultRowSorter.convertRowIndexToModel") >= 0) {
                    return true;
                }
            }
            // system clipboard can be held, preventing us from getting.
            // throws a RuntimeException through stuff we don't control...
            if (x instanceof IllegalStateException) {
                if (msg.indexOf("cannot open system clipboard") >= 0)
                    return true;
            }
            if (x instanceof IllegalComponentStateException) {
                if (msg.indexOf("component must be showing on the screen to determine its location") >= 0)
                    return true;
            }
            if (x instanceof NullPointerException) {
                if (msg.indexOf("MetalFileChooserUI") >= 0 ||
                    msg.indexOf("WindowsFileChooserUI") >= 0 ||
                    msg.indexOf("AquaDirectoryModel") >= 0 ||
                    msg.indexOf("SizeRequirements.calculateAlignedPositions") >= 0 ||
                    msg.indexOf("BasicTextUI.damageRange") >= 0 ||
                    msg.indexOf("null pData") >= 0 ||
                    msg.indexOf("disposed component") >= 0 ||
                    msg.indexOf("com.sun.java.swing.plaf.windows.XPStyle$Skin") >= 0 ||
                    msg.indexOf("FilePane$2.repaintListSelection") >= 0) {
                    return true;
                }
            }
            if (msg.indexOf("InternalError: Unable to bind") >= 0) {
                return true;
            }
            if (msg.indexOf("sun.awt.shell.Win32ShellFolder2.getFileSystemPath0") >= 0) {
                return true;
            }
            if (msg.indexOf("Could not get shell folder ID list") >= 0) {
                return true;
            }
            if (msg.indexOf("RejectedExecutionException") >= 0 &&
                msg.indexOf("Win32ShellFolder2$FolderDisposer.dispose") >= 0) {
                return true;
            }
            if (x instanceof InternalError) {
                if (msg.indexOf("getGraphics not implemented for this component") >= 0)
                    return true;
            }
            return msg.indexOf("ArrayIndexOutOfBoundsException: 3184") >= 0;
        }

    }
}