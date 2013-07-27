package com.zipeg;

import javax.swing.*;
import java.awt.*;

public final class About {

    private About() {
    }

    public static void showMessage() {
        Font font = new JLabel().getFont();
        String message =
                "<html><body>" +
                "<font face=\"" + font.getName() + "\">" +
                "<table width=256>" +
                 "<tr width=236>" +
                 "<td>" +
                  "<b>Zipeg</b> for " +
                    (Util.isMac ? "OS X" : "Windows") +"<br>" +
                  "version " + Util.getVersion() + "<br>" +
                  "Copyright&nbsp;&copy;&nbsp;2006-2012&nbsp;Leo&nbsp;Kuznetsov<br>" +
                 "<a href=\"http://www.zipeg.com\">http://www.zipeg.com</a>" +
                 "</td>" +
                 "</tr>" +

                 "<tr width=236>" +
                 "<td>" +
                  "<font=\"" + font.getName() + "\" size=\"-1\" >" +
                  "<b>p7zip</b>&nbsp;-&nbsp;7zip&nbsp;plugin&nbsp;version&nbsp;4.43<br>" +
                  "Copyright&nbsp;&copy;&nbsp;2006&nbsp;Igor&nbsp;Pavlov<br>" +
                  "<a href=\"http://p7zip.sourceforge.net\">http://p7zip.sourceforge.net</a>" +
                  "</font>" +
                 "</td>" +
                 "</tr>" +

                 "<tr width=236>" +
                 "<td>" +
                  "<font=\"" + font.getName() + "\" size=\"-1\" >" +
                  "<b>juniversalchardet</b>&nbsp;version&nbsp;1.0.2<br>" +
                  "Shy&nbsp;Shalom,&nbsp;Kohei&nbsp;TAKETA<br>" +
                  "<a href=\"http://code.google.com/p/juniversalchardet\">http://code.google.com/p/juniversalchardet</a>" +
                  "</font>" +
                 "</td>" +
                 "</tr>" +

                 "<tr width=236>" +
                 "<td>" +
                  "<font=\"" + font.getName() + "\" size=\"-1\" >" +
                  "<b>Original Icon Design</b><br>" +
                  "<a href=\"http://www.milenkaya.org/\">Olya Milenkaya</a> and " +
                  "<a href=\"http://www.ovechkin.net/\">Oleg Ovechkin</a>" +
                  "</font>" +
                 "</td>" +
                 "</tr>" +
                 (Util.isWindows ?
                 "<tr width=236>" +
                 "<td>" +
                  "<font=\"" + font.getName() + "\" size=\"-1\" >" +
                  "<b>Graphics by &quot;Kudesnik&quot;</b><br>" +
                  "<a href=\"http://kudesnick.blogspot.com\">http://kudesnick.blogspot.com</a>" +
                  "</font>" +
                 "</td>" :

                 "</tr>" +
                 "<tr width=236>" +
                 "<td>" +
                  "<font=\"" + font.getName() + "\" size=\"-1\" >" +
                  "<b>Graphics by McDo Design (Susumu Yoshida)</b><br>" +
                  "<a href=\"http://dribbble.com/susumu\">http://dribbble.com/susumu</a>" +
                  "</font>" +
                 "</td>" +
                 "</tr>" ) +
/*
McDo Design (Susumu Yoshida)
http://www.mcdodesign.com/?page_id=2
*/

                "<tr width=236>" +
                "<td>" +
                 "<font=\"" + font.getName() + "\" size=\"-1\" >" +
                 "<b>Java version:</b> " + Util.javaVersion +
                 "</font>" +
                "</td>" +
                "</tr>" +

                "</table>" +
                "</font></body></html>";
        ImageIcon icon = Resources.getImageIcon("zipeg64x64");
        MessageBox.show(message, "About Zipeg",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, null, icon);
        if (Zipeg.isShiftDown && Zipeg.isCtrlDown) {
            throw new Error("test");
        }
    }

}
