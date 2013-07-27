package com.zipeg;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;

public class MessageBox {

    /** Return value from class method if YES is chosen. */
    public static final int NOT_AGAIN_OPTION = -1;

    private MessageBox() {
    }

    public static int show(Object message, String title, int messageType) {
        OptionPane pane = new OptionPane(message, messageType);
        pane.setMessage(message);
        pane.createDialog(MainFrame.getTopFrame(), title);
        return pane.showDialog();
    }

    public static int show(Object message, String title, int optionType, int messageType) {
        return show(message, title, optionType, messageType, null, null);
    }

    public static int show(Object message, String title, int optionType, int messageType,
            Object[] options, Object defau1t) {
        return show(message, title, optionType, messageType, options, defau1t, null);
    }

    public static int show(Object message, String title, int optionType, int messageType,
            Object[] options, Object defau1t, Icon icon) {
        OptionPane pane = new OptionPane(message, messageType);
        pane.setMessage(message);
        // noinspection MagicConstant
        pane.setOptionType(optionType);
        if (icon != null) {
            pane.setIcon(icon);
        }
        if (options != null) {
            pane.setOptions(options);
            if (defau1t != null) {
                pane.setInitialValue(defau1t);
            }
        }
        pane.createDialog(MainFrame.getTopFrame(), title);
        return pane.showDialog();
    }

    public static int notAgain(Object message, String title, int messageType, final String preset) {
        return notAgain(message, title, JOptionPane.DEFAULT_OPTION, messageType, preset);
    }

    public static int notAgain(Object message, String title, int optionType,
            int messageType, final String preset) {
        if (!Presets.getBoolean(preset, false)) {
            JPanel panel = new JPanel(new BorderLayout());
            JComponent msg;
            if (message instanceof String) {
                msg = new JLabel((String)message);
            } else {
                assert message instanceof JComponent : message;
                msg = (JComponent)message;
            }
            final JCheckBox never_again = new JCheckBox("Don't show this message again.");
            never_again.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    Presets.putBoolean(preset, never_again.isSelected());
                }
            });
            panel.add(msg, BorderLayout.CENTER);
            never_again.setAlignmentX(1.0f);
            panel.add(never_again, BorderLayout.SOUTH);
            return MessageBox.show(panel, title, optionType, messageType);
        } else {
            return NOT_AGAIN_OPTION;
        }
    }

    private static class OptionPane extends JOptionPane {

        private JDialog dialog;
        private boolean gotFocus = false;

        private WindowAdapter windowAdapter = new WindowAdapter() {

            public void windowClosing(WindowEvent we) {
                setValue(null);
            }

            public void windowGainedFocus(WindowEvent we) {
                // Once window gets focus, set initial focus
                if (!gotFocus) {
                    selectInitialValue();
                    gotFocus = true;
                }
            }
        };

        private ComponentListener componentListener = new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                // reset value to ensure closing works properly
                setValue(JOptionPane.UNINITIALIZED_VALUE);
            }
        };

        private PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                // Let the defaultCloseOperation handle the closing
                // if the user closed the window without selecting a button
                // (newValue = null in that case).  Otherwise, close the dialog.
                if (dialog != null && dialog.isVisible() && event != null &&
                        event.getSource() == OptionPane.this &&
                        VALUE_PROPERTY.equals(event.getPropertyName()) &&
                        event.getNewValue() != null &&
                        event.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
                    if (dialog.isVisible()) {
                        EventQueue.invokeLater(new Runnable(){
                            public void run() {
                                if (dialog != null && dialog.isVisible()) {
                                    dialog.setVisible(false);
                                }
                            }
                        });
                    }
                }
            }
        };

        public OptionPane(Object message, int messageType) {
            // noinspection MagicConstant
            super(message, messageType);
        }

/*      // finalize() is delayed for both JDialog and OptionsPane but it does work. No leaks.
        protected void finalize() throws Throwable {
            Debug.traceln("OptionsPane.finalize()");
            super.finalize();
        }
*/

        public void addNotify() {
            super.addNotify();
            addPropertyChangeListener(propertyChangeListener);
            dialog.addWindowListener(windowAdapter);
            dialog.addWindowFocusListener(windowAdapter);
            dialog.addComponentListener(componentListener);
        }

        public void removeNotify() {
            dialog.removeComponentListener(componentListener);
            dialog.removeWindowFocusListener(windowAdapter);
            dialog.removeWindowListener(windowAdapter);
            removePropertyChangeListener(propertyChangeListener);
            super.removeNotify();
        }

        public JDialog createDialog(Component parentComponent, String title)
            throws HeadlessException {
            int style = styleFromMessageType(getMessageType());
            return createDialog(parentComponent, title, style);
        }

        private JDialog createDialog(Component parentComponent, String title, int style) {
            Window window = getWindowForComponent(parentComponent);
            if (window instanceof Frame) {
                dialog = new JDialog((Frame)window, title, true) /* {
                    protected void finalize() throws Throwable {
                        Debug.traceln("JDialog.finalize()");
                        super.finalize();
                    }
                }*/;
            } else {
                dialog = new JDialog((Dialog)window, title, true) /* {
                    protected void finalize() throws Throwable {
                        Debug.traceln("JDialog.finalize()");
                        super.finalize();
                    }
                }*/;
            }
            initDialog(style, parentComponent);
            return dialog;
        }

        private void initDialog(int style, Component parentComponent) {
            if (parentComponent != null) {
                Dialogs.setModalSheet(dialog);
            } else {
                Zipeg.setDockIcon();
            }
            dialog.setComponentOrientation(OptionPane.this.getComponentOrientation());
            Container contentPane = dialog.getContentPane();
            contentPane.setLayout(new BorderLayout());
            contentPane.add(OptionPane.this, BorderLayout.CENTER);
            if (JDialog.isDefaultLookAndFeelDecorated()) {
                boolean supportsWindowDecorations =
                  UIManager.getLookAndFeel().getSupportsWindowDecorations();
                if (supportsWindowDecorations) {
                    dialog.setUndecorated(true);
                    // noinspection MagicConstant
                    getRootPane().setWindowDecorationStyle(style);
                }
            }
            dialog.pack();
            dialog.setResizable(false);
            dialog.setLocationRelativeTo(parentComponent);
        }

        private static int styleFromMessageType(int messageType) {
            switch (messageType) {
            case ERROR_MESSAGE:
                return JRootPane.ERROR_DIALOG;
            case QUESTION_MESSAGE:
                return JRootPane.QUESTION_DIALOG;
            case WARNING_MESSAGE:
                return JRootPane.WARNING_DIALOG;
            case INFORMATION_MESSAGE:
                return JRootPane.INFORMATION_DIALOG;
            case PLAIN_MESSAGE:
            default:
                return JRootPane.PLAIN_DIALOG;
            }
        }

        private static Window getWindowForComponent(Component parentComponent)
            throws HeadlessException {
            if (parentComponent == null)
                return getRootFrame();
            if (parentComponent instanceof Frame || parentComponent instanceof Dialog)
                return (Window)parentComponent;
            return getWindowForComponent(parentComponent.getParent());
        }

        public void setMessage(Object message) {
            if (message instanceof InputStream) {
                try {
                    InputStream i = (InputStream)message;
                    StringWriter w = new StringWriter(i.available());
                    int c;
                    for (;;) {
                        c = i.read();
                        if (c < 0) break;
                        w.write(c);
                    }
                    message = w.toString();
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
            if (message instanceof String && ((String)message).toLowerCase().contains("<html>")) {
                String text = (String)message;
                JEditorPane pane = new JEditorPane();
                pane.setContentType("text/html");
                pane.setEditable(false);
                pane.setOpaque(false);
                pane.addHyperlinkListener(new HyperlinkListener() {
                    public void hyperlinkUpdate(HyperlinkEvent e) {
                        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            if (e.getDescription().indexOf("macappstore:") >= 0) {
                                Util.openUrl(e.getDescription());
                            } else if (e.getURL() != null) {
                                Util.openUrl(e.getURL().toString());
                            }
                        }
                    }
                });
                pane.setText(text);
                pane.revalidate();
                message = pane;
            }
            super.setMessage(message);
        }

        private int showDialog() {
            selectInitialValue();
            Dialogs.show(dialog); // it already called dispose()
            Container cp = dialog.getContentPane();
            if (cp != null) {
                cp.remove(OptionPane.this);
            }
            dialog = null;
            // despite all the efforts above the dialog still leaks.
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6464022
            Object selectedValue = getValue();
            if (selectedValue == null) {
                return JOptionPane.CLOSED_OPTION;
            }
            Object[] options = getOptions();
            if (options == null) {
                if (selectedValue instanceof Integer) {
                    return ((Integer)selectedValue).intValue();
                }
                return JOptionPane.CLOSED_OPTION;
            }
            for (int counter = 0; counter < options.length; counter++) {
                if (options[counter].equals(selectedValue)) {
                    return counter;
                }
            }
            return JOptionPane.CLOSED_OPTION;
        }

    }

}
