package com.zipeg;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

public class DiskSpace {

    private static final int INIT_PROBLEM = -1;
    private static final int OTHER = 0;
    private static final int WINDOWS = 1;
    private static final int UNIX = 2;
    private static final int POSIX_UNIX = 3;
    private static int OS;

    static {
        String os = System.getProperty("os.name");
        if (os == null) {
            OS = INIT_PROBLEM;
        } else {
            os = os.toLowerCase();
            // match
            if (os.indexOf("windows") != -1) {
                OS = WINDOWS;
            } else if (os.indexOf("linux") != -1 || os.indexOf("sun os") != -1 ||
                    os.indexOf("sunos") != -1 || os.indexOf("solaris") != -1 ||
                    os.indexOf("mpe/ix") != -1 || os.indexOf("freebsd") != -1 ||
                    os.indexOf("irix") != -1 || os.indexOf("digital unix") != -1 ||
                    os.indexOf("unix") != -1 || os.indexOf("mac os x") != -1) {
                OS = UNIX;
            } else if (os.indexOf("hp-ux") != -1 || os.indexOf("aix") != -1) {
                OS = POSIX_UNIX;
            } else {
                OS = OTHER;
            }
        }
    }

    public static long freeSpaceKb(String path) throws IOException {
        return freeSpaceOS(path, OS, true);
    }

    private static long freeSpaceOS(String path, int os, boolean kb) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        switch (os) {
            case WINDOWS:
                return (kb ? freeSpaceWindows(path) / 1024 : freeSpaceWindows(path));
            case UNIX:
                return freeSpaceUnix(path, kb, false);
            case POSIX_UNIX:
                return freeSpaceUnix(path, kb, true);
            case OTHER:
                throw new Error("Unsupported OS");
            default:
                throw new Error("Cannot determine OS");
        }
    }

    private static long freeSpaceWindows(String path) throws IOException {
        path = Util.getCanonicalPath(new File(path));
        if (path.length() > 2 && path.charAt(1) == ':') {
            path = path.substring(0, 2);  // seems to make it work
        }
        String[] cmdAttribs = new String[]{"cmd.exe", "/C", "dir /-c " + path};
        List lines = performCommand(cmdAttribs, Integer.MAX_VALUE);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = (String)lines.get(i);
            if (line.length() > 0) {
                return parseDir(line, path);
            }
        }
        throw new IOException(
                "Command line 'dir /-c' did not return any info " + "for path '" + path + "'");
    }

    private static long parseDir(String line, String path) throws IOException {
        int bytesStart = 0;
        int bytesEnd = 0;
        int j = line.length() - 1;
        while (j >= 0) {
            char c = line.charAt(j);
            if (Character.isDigit(c)) {
                bytesEnd = j + 1;
                break;
            }
            j--;
        }
        while (j >= 0) {
            char c = line.charAt(j);
            if (!Character.isDigit(c) && c != ',' && c != '.') {
                bytesStart = j + 1;
                break;
            }
            j--;
        }
        if (j < 0) {
            throw new IOException("Command line 'dir /-c' did not return valid info " +
                    "for path '" + path + "'");
        }
        StringBuffer buf = new StringBuffer(line.substring(bytesStart, bytesEnd));
        for (int k = 0; k < buf.length(); k++) {
            if (buf.charAt(k) == ',' || buf.charAt(k) == '.') {
                buf.deleteCharAt(k--);
            }
        }
        return parseBytes(buf.toString(), path);
    }

    private static long freeSpaceUnix(String path, boolean kb, boolean posix) throws IOException {
        if (path.length() == 0) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        path = Util.getCanonicalPath(new File(path));
        String flags = "-";
        if (kb) {
            flags += "k";
        }
        if (posix) {
            flags += "P";
        }
        String[] cmdAttribs =
                (flags.length() > 1 ? new String[]{"df", flags, path} : new String[]{"df", path});
        List lines = performCommand(cmdAttribs, 3);
        if (lines.size() < 2) {
            throw new IOException("Command line 'df' did not return info as expected " +
                    "for path '" + path + "'- response was " + lines);
        }
        String line2 = (String)lines.get(1);
        StringTokenizer tok = new StringTokenizer(line2, " ");
        if (tok.countTokens() < 4) {
            if (tok.countTokens() == 1 && lines.size() >= 3) {
                String line3 = (String)lines.get(2);
                tok = new StringTokenizer(line3, " ");
            } else {
                throw new IOException("Command line 'df' did not return data as expected " +
                        "for path '" + path + "'- check path is valid");
            }
        } else {
            tok.nextToken();
        }
        tok.nextToken();
        tok.nextToken();
        String freeSpace = tok.nextToken();
        return parseBytes(freeSpace, path);
    }

    private static long parseBytes(String freeSpace, String path) throws IOException {
        try {
            long bytes = Long.parseLong(freeSpace);
            if (bytes < 0) {
                throw new IOException("Command line 'df' did not find free space in response " +
                        "for path '" + path + "'- check path is valid");
            }
            return bytes;

        } catch (NumberFormatException ex) {
            throw new IOException("Command line 'df' did not return numeric data as expected " +
                    "for path '" + path + "'- check path is valid");
        }
    }

    private static List performCommand(String[] cmdAttribs, int max) throws IOException {
        List lines = new ArrayList(20);
        Process proc = null;
        InputStream in = null;
        OutputStream out = null;
        InputStream err = null;
        BufferedReader inr = null;
        try {
            proc = Runtime.getRuntime().exec(cmdAttribs);
            in = proc.getInputStream();
            out = proc.getOutputStream();
            err = proc.getErrorStream();
            inr = new BufferedReader(new InputStreamReader(in));
            String line = inr.readLine();
            while (line != null && lines.size() < max) {
                line = line.toLowerCase().trim();
                lines.add(line);
                line = inr.readLine();
            }
            proc.waitFor();
            if (proc.exitValue() != 0) {
                throw new IOException("Command line returned OS error code '" + proc.exitValue() +
                        "' for command " + Arrays.asList(cmdAttribs));
            }
            if (lines.size() == 0) {
                throw new IOException("Command line did not return any info " + "for command " +
                        Arrays.asList(cmdAttribs));
            }
            return lines;
        } catch (InterruptedException ex) {
            throw new IOException("Command line threw an InterruptedException '" + ex.getMessage() +
                    "' for command " + Arrays.asList(cmdAttribs));
        } finally {
            Util.close(in);
            Util.close(out);
            Util.close(err);
            Util.close(inr);
            if (proc != null) {
                proc.destroy();
            }
        }
    }

}
