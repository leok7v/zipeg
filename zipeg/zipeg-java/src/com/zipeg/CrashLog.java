package com.zipeg;

import java.util.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;

public final class CrashLog {

    private CrashLog() {
    }

    public static void report(String message, String log) {
        try {
            File file = new File(log);
            InputStream input = new FileInputStream(file);
            int len = (int)file.length();
            byte[] bytes = new byte[len];
            int n = input.read(bytes, 0, len);
            assert n == len;
            Util.close(input);
            String body = new String(bytes);
            send("[zipeg crash] " + Util.javaVersion + " " +
                 (Util.isMac ? "m" : "w") + " " + Util.osVersion + " " +
                 message, body);
            Util.delete(file);
            boolean force =
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6449933
                body.indexOf("OutOfBoundsException: 3184") >= 0 ||
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6828938
                body.indexOf("FontDesignMetrics.charsWidth") >= 0 ||
                // most likely java installation is still in progress
                body.indexOf("java.util.zip.Inflater") >= 0;
            updateJava(force);
            String exception = "";
            String method = "";
            String prefix = "at com.zipeg.";
            int ix1 = message.indexOf(prefix);
            if (ix1 > 0) {
                method = message.substring(ix1 + prefix.length()).trim().replaceAll(" ", "_");
            }
            int ix0 = message.indexOf(' ');
            if (ix0 > 0 && ix0 < ix1) {
                while (ix0 <= ix1 && Character.isWhitespace(message.charAt(ix0))) {
                    ix0++;
                }
                if (ix0 < ix1) {
                    exception = message.substring(ix0, ix1).trim().replaceAll(" ", "_") + ".";
                }
            }
            Util.openUrl("http://www.zipeg.com/faq.crash.html" +
                    Zipeg.urlAppendInfo(exception + method));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void updateJava(boolean force) {
        if (!force) {
            double version = Util.javaVersion;
            if (Util.isWindows && version >= 1.6029) {
                return;
            }
            if (Util.isMac) {
                if (version >= 1.6029) {
                    return;
                }
                if (Util.osVersion > 10.4 && version >= 1.6026) {
                    return;
                }
                if (Util.osVersion > 10.3 && version >= 1.5030) {
                    return;
                }
            }
        }
        JEditorPane info = new JEditorPane();
        info.setContentType("text/html");
        info.setEditable(false);
        info.setOpaque(false);
        Font font = new JLabel().getFont();
        String url = "http://www.java.com/getjava";
        if (Util.isMac) {
            if (Util.osVersion >= 10.7) {
                url = "http://support.apple.com/kb/DL1421"; // 1.6.0_29 Lion
            } else if (Util.osVersion >= 10.6) {
                url = "http://support.apple.com/kb/DL1360"; // 1.6.0_29 Sno Leo
            } else if (Util.osVersion < 10.6) {
                url = "http://support.apple.com/kb/DL1359"; // 1.6.0_26
            } else if (Util.osVersion < 10.5) {
                url = "http://support.apple.com/downloads/Java_for_Mac_OS_X_10_4__Release_9";
            } else {
                url = "http://www.apple.com/support/downloads/javaformacosx103update5.html";
            }
        }
        info.setText(
                "<html><body>" +
                "<font face=\"" + font.getName() + "\">" +
                "Your Java installation is not up to date.<br>" +
                "Please consider updating to the latest version:<br>" +
                "<a href=\"" + url + "\">Update Java Now</a>" +
                "</font></body></html>");
        final boolean[] clicked = new boolean[1];
        info.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    Util.openUrl(e.getURL().toString());
                    clicked[0] = true;
                }
            }
        });
        info.revalidate();
        MessageBox.show(info, "Update Java", JOptionPane.PLAIN_MESSAGE);
        if (!clicked[0]) {
            Util.openUrl(url);
        }
    }

    public static void send(String subject, String body) {
        assert IdlingEventQueue.isDispatchThread();
        int r = MessageBox.notAgain(
                "<html><body>Zipeg experienced technical difficulties.<br><br>" +
                "Is it OK to send technical information<br>" +
                "to help the developers to investigate<br>" +
                "and fix the problem?<br><br>" +
                "<small>Feel free to contact " +
                "<a href=\"mailto:support@zipeg.com\">support@zipeg.com</a><br>" +
                "with any additional information about<br>" +
                "the problem you have experienced.</small><br><br>" +
                "</body></html>",
                "Zipeg: Crashed",
                JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, "send.crash.always");
        if (r != JOptionPane.YES_OPTION && r != MessageBox.NOT_AGAIN_OPTION) {
            return;
        }
        Map headers = new HashMap();
        char[] c = new char[15];
        c[1] = 'r';
        c[0] = 'c';
        c[2] = 'a';
        c[3] = 's';
        c[5] = '@';
        c[4] = 'h';
        c[6] = 'z';
        c[7] = 'i';
        c[8] = 'p';
        c[9] = 'e';
        c[10] = 'g';
        c[12] = 'c';
        c[11] = '.';
        c[13] = 'o';
        c[14] = 'm';
        headers.put("subject", subject);
        headers.put("email", new String(c));
        headers.put("name", new String(c));
        headers.put("body", body);
        try {
            char[] m = new char[8];
            m[1] ='a';
            m[0] ='m';
            m[4] ='.';
            m[2] ='i';
            m[6] ='h';
            m[3] ='l';
            m[5] ='p';
            m[7] ='p';
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            Util.httpGetPost(true, "http://www.zipeg.com/" + new String(m), headers, null, reply);
            System.err.println(reply.toString());
        } catch (IOException e) {
            Util.sendMail(new String(c), subject, body);
        }
    }

}
