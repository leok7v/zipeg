package com.zipeg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Donate {

    private Donate() {
    }

    private static final String
        RU03 = "",
        PT03 = "",
        ES03 = "",
        FR05 = "",
        JP10 = "",
        DE10 = "",
        GB07 = "",
        MAC_US10 = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=SGZEFCMMYP7UN",
        WIN_US10 = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=86QH25DM2AG4S";

    public static String getDonateURL() {
/*
        String cn = System.getProperty("user.country");
        String ln = System.getProperty("user.language");
        cn = cn == null ? null : cn.toLowerCase();
        ln = ln == null ? null : ln.toLowerCase();
        if (ln != null && ln.length() > 2) {
            ln = ln.substring(0, 2);
        }
        if ("gb".equals(cn)) {
            return GB07;
        } else if ("us".equals(cn)) {
            return US10;
        } else if ("de".equals(cn)) {
            return DE10;
        } else if ("jp".equals(cn)) {
            return JP10;
        } else if ("fr".equals(cn)) {
            return FR05;
        } else if ("es".equals(ln)) {
            return ES03;
        } else if ("pt".equals(ln)) {
            return PT03;
        } else if ("ru".equals(ln)) {
            return RU03;
        } else if ("en".equals(ln)) {
            return US10;
        }
*/
        return Util.isMac ? MAC_US10 : WIN_US10;
    }

    public static void askForDonation() {
        if (Dialogs.isShown()) {
            return;
        }
        int extract_count = Presets.getInt("extract.count", 0);
        int donate_count = Presets.getInt("donate.count", 0);
        if (extract_count > 10 && donate_count < 3) {
            class DonateButton extends JButton {

                public DonateButton(String s) {
                    super(s);
                    setForeground(new Color(0, 64, 0));
                    addActionListener(new ActionListener(){
                        public void actionPerformed(ActionEvent e) {
                            Container p = getParent();
                            while (!(p instanceof Dialog)) {
                                p = p.getParent();
                            }
                            assert Dialogs.shown() == p;
                            Dialogs.dispose();
                            Util.openUrl(getDonateURL());
                        }
                    });
                }


            }
            assert IdlingEventQueue.isDispatchThread();
            Object[] options = {"Later", "Already", "Never", new DonateButton("Donate")};
            int result = MessageBox.show(
                    "<html><body>Zipeg has been successfully updated.<br><br>" +
                    "Zipeg helped you to open all that ZIP and RAR files.<br><br>" +
                    "Now it needs your help.<br><br>" +
                    "Would you like to make a small donation now<br>" +
                    "via <b>secure</b> PayPal service?<br><br>" +
                    "<small><i>(you do <b>not</b> need to have PayPal account,<br>" +
                    "&nbsp;you can make your donation with a credit card)</i><br></small>" +
                    "</body></html>",
                    "Donate",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    options, options[3], new ImageIcon(Resources.getImage("paypal_and_cc")));
            if (result == 0) { // later
                Util.openUrl("http://www.zipeg.com/later.html");
            } else if (result == 1) { // already
                Presets.putInt("donate.count", donate_count + 1);
            } else if (result == 2) { // already
                Presets.putBoolean("donate.never", true);
                Util.openUrl("http://www.zipeg.com/never.html");
            }
        }
    }

}
