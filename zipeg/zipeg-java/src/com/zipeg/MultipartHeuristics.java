package com.zipeg;

import javax.swing.*;
import java.io.*;

public class MultipartHeuristics {

    private static final String SINGLE_PART =
            "<small>Opening a single part from the multipart " +
            "archive is <b>not</b><br>" +
            "recommended.<br>" +
            "It is most likely to result in CRC errors (incomplete files)<br>" +
            "at the beginning and/or end of this part of the archive.<br></small>";


    private MultipartHeuristics() {
    }

    public static boolean checkDownloadComplete(final File file) {
        // FireFox create empty file on the beginning of download
        // and growing file ".part" next to it which will be renamed
        // at the end of download.
        File part = new File(file.getParentFile(), file.getName() + ".part");
        if (file.length() == 0 && part.exists()) {
            if (checkIfStillGrowing(part)) {
                return false;
            }
        }
        if (!file.exists() || file.length() <= 0) {
            String absent_or_empty = !file.exists() ? "is absent" : "is empty and cannot be opened";
            MessageBox.show(
                    "<html><body>The file \"" + file.getName() + "\"<br>" +
                    absent_or_empty + ".<br><br>" +
                    "May be it is still being downloaded<br>" +
                    "or it was corrupted in transit?<br><br>" +
                    "Please wait until the download is complete or<br>" +
                    "cancel pending download and download it again<br>" +
                    "then try to open it.<br>" +
                    "</body></html>", "Zipeg: Empty File",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!file.canRead()) {
            MessageBox.show(
                    "<html><body><p>The file \"" + file.getName() + "\" " +
                    "cannot be opened.</p><br>" +
                    "<p>Your do not have sufficient security privileges<br>" +
                    "to open this file.</p><br>" +
                    "<p><small>Please contact file owner or system administrator<br>" +
                    "and ask to share this file with you.</small></p>" +
                    "</body></html>", "Zipeg: Unreadable File",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        // check not locked growing download file:
        return !checkIfStillGrowing(file);
    }

    public static File checkMultipart(File file) {
        final String PART = ".part";
        final int n = PART.length();
        String name = file.getName();
        int i0 = name.toLowerCase().indexOf(PART);
        int i1 = i0 < 0 ? -1 : name.indexOf('.', i0 + 1);
        if (i0 > 0 && name.indexOf('.', i0 + 1) > i0) {
            String part = name.substring(i0 + 1, i1);
            if (part.length() > 1 && Character.isDigit(part.charAt(part.length() - 1))) {
                int no = Util.a2i(part.substring(n - 1), -1);
                String prefix = name.substring(0, i0);
                String suffix = name.substring(i1);
                if (no == 1) {
                    String nn = prefix + ".part2" + suffix;
                    File second = new File(file.getParentFile(), nn);
                    if (second.exists()) {
                        MessageBox.notAgain(
                            "<html><body>The file \"" + file.getName() + "\"<br>" +
                            "is a part of multipart archive.<br><br>" +
                            "All part files will be combined and<br>" +
                            "the whole archive will be opened.<br><br>" +
                            "<small>There is no need to open other parts manually.</small>" +
                            "<br>" +
                            "</body></html>",
                            "Zipeg: Multi-part Archive File",
                                    JOptionPane.INFORMATION_MESSAGE, "grok.multipart");
                    } else {
                        int r = MessageBox.notAgain(
                            "<html><body>The file \"" + file.getName() + "\"<br>" +
                            "seems to be a part of multipart archive<br>" +
                            "while other parts are not found in this folder.<br><br>" +
                            "Do you want to download other parts before opening it?<br><br>" +
                            SINGLE_PART +
                            "</body></html>",
                            "Zipeg: Multi-part Archive File",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.INFORMATION_MESSAGE, "grok.singlepart");
                        return r == JOptionPane.NO_OPTION ? file : null;
                    }
                } else {
                    int r = MessageBox.show(
                            "<html><body>The file \"" + file.getName() + "\"<br>" +
                            "is a part of multipart archive.<br><br>" +
                            "Do you want to open the <b>whole</b> archive instead?<br><br>" +
                            SINGLE_PART +
                            "</body></html>", "Zipeg: Multi-part Archive File",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (r == JOptionPane.NO_OPTION) {
                        return file;
                    } else {
                        String nn = prefix + ".part1" + suffix;
                        File first = new File(file.getParentFile(), nn);
                        if (!first.exists() || !first.canRead() || !checkParts(file, prefix, suffix, no)) {
                            MessageBox.show(
                                    "<html><body>Unable to open file because<br>" +
                                    "some parts of multipart archive<br>" +
                                    "\"" + prefix + "\"<br>" +
                                    "are missing from the folder<br>" +
                                    "\"" + file.getParent() + "\"<br><br>" +
                                    "<small>In order to open multipart archive all parts<br>" +
                                    "of the archive must be in the same folder.</small>" +
                                    "</body></html>", "Zipeg: Multipart File",
                                    JOptionPane.ERROR_MESSAGE);
                            return null;
                        } else {
                            assert first.exists() && first.canRead() : first +
                                    " exist: " + first.exists() + " canRead: " + first.canRead();
                            return first;
                        }
                    }
                }
            }
        }
        return file;
    }

    private static boolean checkParts(File file, String prefix, String suffix, int no) {
        int i = 1;
        for (;;) {
            String nn = prefix + ".part" + i + suffix;
            File part = new File(file.getParent(), nn);
            if (!part.exists() && i > no) {
                break;
            }
            if (!file.exists() || !checkDownloadComplete(part)) {
                return false;
            }
            i++;
        }
        return true;
    }

    public static void stillDownloading(File f) {
        MessageBox.show(
                "<html><body>The file \"" + f.getName() + "\"<br>" +
                "is still being downloaded.<br><br>" +
                "Please wait until the download is complete<br>" +
                "and try to open it again later." +
                "</body></html>", "Zipeg: Incomplete File",
                JOptionPane.ERROR_MESSAGE);
    }

    private static boolean checkIfStillGrowing(File f) {
        long size0 = f.length();
        Util.sleep(500);
        long size1 = f.length();
        if (size0 != size1) {
            stillDownloading(f);
            return true;
        }
        return false;
    }

}
