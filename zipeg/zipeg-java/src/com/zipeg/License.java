package com.zipeg;

import javax.swing.*;

public class License {

    private License() {
    }

    public static void showLicence(boolean accept) {
        String location = "resources/license.html";
        Object[] options = accept ? new Object[]{"  I Accept  ", " I Decline "} :
                                    new Object[]{ " OK " };
        int r = MessageBox.show(Resources.getResourceAsStream(location),
                "Zipeg: License Agreement",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                options, options[options.length - 1]) ;
        if (r != 0 && accept) {
            System.exit(1);
        }
    }

}
