package com.zipeg;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.List;
import java.awt.event.*;
import java.awt.*;
import java.awt.dnd.*;
import java.lang.reflect.*;

public class MainFrame extends JFrame {

    private static final Rectangle DEFAULT = new Rectangle(100, 100, 800, 600);
    private static final Dimension SMALLEST = new Dimension(600, 440);
    private static MainFrame frame = null;
    private static JFrame offscreen; // hosts menu when main frame is invisible. see setVisible
    private static final LinkedList cursors = new LinkedList();
    private JMenuBar menubar;
    private ComponentAdapter componentAdapter = new ComponentAdapter(){
            public void componentResized(ComponentEvent e) { saveLayout(); }
            public void componentMoved(ComponentEvent e) { saveLayout(); }
        };
    private WindowFocusListener windowFocusListener = new WindowFocusListener(){
            public void windowGainedFocus(WindowEvent e) {
                Actions.postEvent("windowGainedFocus", e);
            }
            public void windowLostFocus(WindowEvent e) {
                Actions.postEvent("windowLostFocus", e);
            }
        };

    MainFrame() {
        assert frame == null;
        frame = this;
        rootPane.putClientProperty("apple.awt.brushMetalLook", Boolean.TRUE);
        menubar = Actions.createMenuBar();
        setJMenuBar(menubar);
        ContentPane cp = new ContentPane();
        setContentPane(cp);
        Color transparent = new Color(255, 255, 255, 0);
        try {
            setBackground(transparent);
        } catch (Throwable ignore) {
            // frame is decorated thrown by jre 1.7.0_05
        }
        cp.setBackground(transparent);
        cp.setOpaque(false);
        setDefaultCloseOperation(Util.isMac ? WindowConstants.HIDE_ON_CLOSE :
                                                WindowConstants.DO_NOTHING_ON_CLOSE);
        setIcons();
        super.setTitle("Zipeg");
        JComponent gp = (JComponent)getGlassPane();
        gp.setLayout(null); // glass pane is used for InputBlocker and AutoCompleteDropDown
        addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e) {
                if (!Util.isMac) {
                    Actions.postEvent("commandFileExit");
                } else {
                    if (inProgress() && !Zipeg.askToCancelArchiveProcessing()) {
                        EventQueue.invokeLater(new Runnable() {
                            public void run() {
                                setVisible(true);
                            }
                        });
                    } else {
                        Actions.postEvent("commandFileClose");
                    }
                }
            }
        });
        if (!setMinSize()) {
            cp.addComponentListener(new MinimumSizeLimiter());
        }
        restoreLayout();
    }

    public void addNotify() {
        super.addNotify();
        Actions.addListener(this);
        addComponentListener(componentAdapter);
        addWindowFocusListener(windowFocusListener);
    }

    public void removeNotify() {
        removeWindowFocusListener(windowFocusListener);
        removeComponentListener(componentAdapter);
        Actions.removeListener(this);
        super.removeNotify();
    }

    public static JMenuBar getMenu() {
        JMenuBar mb = frame != null ? frame.getJMenuBar() : null;
        return mb == null ? mb : (offscreen != null ? offscreen.getJMenuBar() : null);
    }

    public void setVisible(boolean b) {
        if (Util.isMac) { // keep Macintosh menu up
//          http://lists.apple.com/archives/java-dev/2003/Mar/msg00960.html
            if (offscreen == null) {
                offscreen = new JFrame();
                offscreen.setUndecorated(true);
                offscreen.setLocation(Integer.MIN_VALUE, Integer.MIN_VALUE);
                offscreen.setSize(0, 0);
                offscreen.setEnabled(false);
                offscreen.setVisible(true);
            }
            if (b) {
                if (menubar != null) {
                    offscreen.setJMenuBar(null);
                    setJMenuBar(menubar);
                }
            } else {
                if (menubar != null) {
                    setJMenuBar(null);
                    offscreen.setJMenuBar(menubar);
                }
            }
        }
        super.setVisible(b);
        if (b) {
            toFront();
        }
    }

    public static MainFrame getInstance() {
        return frame;
    }

    public static JFrame getOffscreen() {
        return offscreen;
    }

    public void dispose() {
        frame = null;
        super.dispose();
    }

    public List getSelected() {
        return ((ContentPane)getContentPane()).getSelected();
    }

    public String getDestination() {
        return ((ContentPane)getContentPane()).getDestination();
    }

    public void saveDestination() {
        ((ContentPane)getContentPane()).saveDestination();
    }

    public boolean inProgress() {
        return ((ContentPane)getContentPane()).inProgress();
    }

    public static JFrame getTopFrame() {
        if (frame != null && !frame.isVisible()) {
            frame.setVisible(true);
        }
        return frame != null && frame.isVisible() ? frame : null;
    }

    public void pushCursor(Cursor c) {
        assert IdlingEventQueue.isDispatchThread();
        cursors.add(getCursor());
        setCursor(c);
    }

    public void popCursor() {
        assert IdlingEventQueue.isDispatchThread();
        setCursor((Cursor)cursors.removeLast());
    }

    public void setTitle(String s) {
        if (s == null || "".equals(s)) {
            s = "Zipeg";
        }
        if (!s.equals(super.getTitle())) {
            super.setTitle(s);
        }
    }

    public static void showError(Object param) {
        String text = (String)param;
        Dialogs.dispose(); // if we have anything open
        try {
            MessageBox.show(text, "Zipeg: Error", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable first) {
            // try once more to show OutOfMemory errors
            Memory.releaseSafetyPool();
            MessageBox.show(text, "Zipeg: Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void updateCommandState(Map m) {
        m.put("commandHelpAbout", Boolean.valueOf(!Dialogs.isShown()));
    }

    public void addDropTargetAdapter(DropTargetAdapter dta) {
        ((ContentPane)getContentPane()).addDropTargetAdapter(dta);
    }

    private class MinimumSizeLimiter extends ComponentAdapter {

        public void componentResized(ComponentEvent e) {
            Dimension size = getSize();
            if (size.width < SMALLEST.width || size.height < SMALLEST.height) {
                boolean limit = false;
                if (size.width < SMALLEST.width) {
                    size.width = SMALLEST.width;
                    limit = true;
                }
                if (size.height < SMALLEST.height) {
                    size.height = SMALLEST.height;
                    limit = true;
                }
                if (!limit || size.equals(getSize())) {
                    return;
                }
                setResizable(false);
                Robot robot = Util.getRobot();
                if (robot != null) {
                    robot.mouseRelease(InputEvent.BUTTON1_MASK |
                            InputEvent.BUTTON2_MASK |
                            InputEvent.BUTTON3_MASK);
                }
                final Dimension s = size;
                Util.invokeLater(100, new Runnable() {
                    public void run() {
                        setResizable(true);
                        setSize(s);
                    }
                });
            }
        }
    }

    private void saveLayout() {
        if (!Zipeg.isExitPending() && isVisible()) {
            Presets.putInt("x", getX());
            Presets.putInt("y", getY());
            Presets.putInt("width", getWidth());
            Presets.putInt("height", getHeight());
            Presets.sync();
        }
    }

    private void restoreLayout() {
        Rectangle ub = unionBounds();
        int x = Math.max(Presets.getInt("x", DEFAULT.x), ub.x);
        int y = Math.max(Presets.getInt("y", DEFAULT.y), ub.y);
        int width = Math.min(Presets.getInt("width", DEFAULT.width), ub.width);
        int height = Math.min(Presets.getInt("height", DEFAULT.height), ub.width);
        if (x + width > ub.width && x > 0) {
            x = Math.max(0, ub.width - width);
        }
        if (x + width > ub.width) {
            width = ub.width - x;
        }
        if (y + height > ub.height && y > 0) {
            y = Math.max(0, ub.height - height);
        }
        if (y + height > ub.height) {
            height = ub.height - y;
        }
        setBounds(new Rectangle(x, y,
                Math.max(width, SMALLEST.width),
                Math.max(height, SMALLEST.height)));
    }

    private Rectangle unionBounds() {
        Rectangle ub = new Rectangle();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs =
                ge.getScreenDevices();
        for (int j = 0; j < gs.length; j++) {
            GraphicsDevice gd = gs[j];
            GraphicsConfiguration[] gc = gd.getConfigurations();
            for (int i=0; i < gc.length; i++) {
                ub =
                    ub.union(gc[i].getBounds());
            }
        }
        // heuristic: when has single monitor dock is on the bottom on both Mac and Windows
        if (gs.length == 1) {
            ub.height -= 60;
        }
        return ub.union(new Rectangle(SMALLEST));
    }

    private boolean setMinSize() {
        Method setMinimumSize;
        try {
            Class[] signature = new Class[]{Dimension.class};
            setMinimumSize = JFrame.class.getMethod("setMinimumSize", signature);
        } catch (NoSuchMethodException e) {
            setMinimumSize = null;
        }
        if (Util.javaVersion >= 1.6 && setMinimumSize != null) {
            calls.call(this, setMinimumSize, new Object[]{SMALLEST});
            return true;
        } else {
            return OSX.setFrameMinSize(this, SMALLEST.width, SMALLEST.height);
        }
    }


    private void setIcons() {
        Method setIconImages;
        try {
            Class[] signature = new Class[]{List.class};
            setIconImages = JFrame.class.getMethod("setIconImages", signature);
        } catch (NoSuchMethodException e) {
            setIconImages = null;
        }
        if (Util.javaVersion >= 1.6 && setIconImages != null) {
            Image i16x16 = Resources.getImage("zipeg16x16");
            Image i32x32 = Resources.getImage("zipeg32x32");
            Image i64x64 = Resources.getImage("zipeg64x64");
            Image i128x128 = Resources.getImage("zipeg128x128");
            Image i256x256 = Resources.getImage("zipeg256x256");
            ArrayList il = new ArrayList();
            il.add(i16x16);
            il.add(i32x32);
            il.add(i64x64);
            il.add(i128x128);
            il.add(i256x256);
            calls.call(this, setIconImages, new Object[]{il});
        } else {
            setIconImage(Resources.getImage("zipeg64x64"));
        }
    }

    private static JEditorPane html(String body) {
        JEditorPane link = new JEditorPane();
        link.setContentType("text/html");
        link.setEditable(false);
        link.setOpaque(false);
        link.setText("<html><body>" + body + "</body></html>");
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        return link;
    }

    public static String getPassword(final String name) {
        final JTextField passwordField = Flags.getFlag(Flags.SHOW_PASSWORDS) ?
                new JTextField(20) {
                    public Dimension getMaximumSize() { return getPreferredSize(); }
                } :
                new JPasswordField(20) {
                    public Dimension getMaximumSize() { return getPreferredSize(); }
                };
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(html("File: " + name));
        panel.add(html("is encrypted and protected with a password."));
        panel.add(html("Please enter password for this file:"));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(passwordField);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JEditorPane link = html("Do not know or have forgotten the password?&nbsp;<br>" +
                "For more information click&nbsp;&nbsp;" +
                "<a href=\"http://www.zipeg.com/password.html\">" +
                "here</a>.");
        panel.add(link);
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4414164
        // toolkit.getLockingKeyState(KeyEvent.VK_CAPS_LOCK)
        boolean capsLock = false;
        try {
            capsLock = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
        } catch (Throwable t) {
            // not supported
        }
        if (capsLock) {
            panel.add(html("<br>Passwords are case sensitive.<br>" +
                    "Make sure <font color=red>CAPS LOCK</font> is <b>off</b>."));
        }
        link.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    Util.openUrl(e.getURL().toString());
                }
            }
        });
        Object[] ob = {panel};
        final Runnable[] r = new Runnable[1];
        passwordField.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                if (r[0] == null) {
                    r[0] = new Runnable(){
                            public void run() {
                                passwordField.requestFocusInWindow();
                                passwordField.requestFocus();
                            }
                    };
                    Util.invokeLater(100, r[0]);
                }
            }
        });
        int result = MessageBox.show(
                        ob,
                        "Zipeg: encrypted archive",
                        JOptionPane.DEFAULT_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            return passwordField.getText();
        } else {
            return null;
        }
    }

}
