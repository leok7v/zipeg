package com.zipeg;

import java.util.*;
import java.util.List;
import java.awt.event.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.swing.*;

public final class Actions extends HashMap {

    private static List listeners = new LinkedList();
    private static Actions instance = new Actions();
    private static boolean enabled = true;

    private Actions() {
        addListener(this);
        IdlingEventQueue.addIdler(new Runnable() {
            public void run() { updateCommandState(); }
        });
    }

    public static void addAction(String method, AbstractAction aa) {
        assert IdlingEventQueue.isDispatchThread();
        assert getAction(method) == null;
        instance.put(method, aa);
    }

    public static AbstractAction getAction(String method) {
        assert IdlingEventQueue.isDispatchThread();
        return (AbstractAction)instance.get(method);
    }

    public static void addListener(Object listener) {
        assert IdlingEventQueue.isDispatchThread();
        assert !listeners.contains(listener) : "can only be added once";
        listeners.add(listener);
    }

    public static void removeListener(Object listener) {
        assert IdlingEventQueue.isDispatchThread();
        if (Debug.isDebug()) {
            // happened 12 times in history of Zipeg so far. Different JDKs, platforms, releases...
            // No repro.
            assert listeners.contains(listener) : "not added or already removed";
        }
        listeners.remove(listener);
    }

    /** Thread safe
     * @param method to invoke later from all listeners on empty stack
     */
    public static void postEvent(final String method) {
        EventQueue.invokeLater(new Runnable(){
            public void run() {
                invokeMethod(method);
            }
        });
    }

    /** Thread safe
     * @param method to invoke later from all listeners on empty stack
     * @param param parameter to pass to the method
     */
    public static void postEvent(final String method, final Object param) {
        EventQueue.invokeLater(new Runnable(){
            public void run() {
                invokeMethod(method, Util.OBJECT, new Object[]{param});
            }
        });
    }

    /** Posts error on the queue and returns immediately. Thread safe.
     *  @param text error message to report
     */
    public static void reportError(String text) {
        postEvent("showError", text);
    }

    public static void reportFatalError(final String text) {
        // noinspection finally
        try {
            Memory.releaseSafetyPool();
            if (IdlingEventQueue.isDispatchThread()) {
                Dialogs.dispose();
                MainFrame.showError(text);
            } else {
                IdlingEventQueue.invokeAndWait(new Runnable() {
                    public void run() {
                        Dialogs.dispose();
                        MainFrame.showError(text);
                    }
                });
            }
        } catch (Throwable x) {
            // ignore
        } finally {
            System.err.println("Zipeg Fatal Error:" + text);
            System.exit(1);
        }
    }

    public interface MenuItemListener {
        void run(String method);
    }

    public static void showContextMenu(Component c, Point pt, String[] spec, MenuItemListener listener) {
        final MainFrame mf = MainFrame.getInstance();
        if (mf == null || spec == null || spec.length == 0) {
            return;
        }
        final JPopupMenu pm = new JPopupMenu();
        final WindowListener windowAdapter = new WindowAdapter(){
            public void windowDeactivated(WindowEvent windowEvent) {
                mf.removeWindowListener(this);
                pm.setVisible(false);
            }
        };
        mf.addWindowListener(windowAdapter);
        for (int i = 0; i < spec.length; i++) {
            if ("---".equals(spec[i])) {
                pm.add(new JSeparator());
            } else {
                pm.add(parseItem(spec[i], null, listener));
            }
        }
        pm.show(c, pt.x, pt.y);
    }

    static JMenuBar createMenuBar() {
        Map menus = new HashMap();
        JMenuBar mb = new JMenuBar();
        if (Util.isMac) {
            addMenu(mb, menus, "File|Open Meta+O", "commandFileOpen");
            addMenu(mb, menus, "File|Close Meta+W", "commandFileClose");
            addMenu(mb, menus, "Actions|E&xtract Meta+E", "commandActionsExtract");
            addMenu(mb, menus, "Actions|---", null);
            addMenu(mb, menus, "Actions|Preview Meta+R", "commandActionsPreview");
            addMenu(mb, menus, "Go|Enclosing_Folder Meta+UP", "commandGoEnclosingFolder");
            addMenu(mb, menus, "Go|Back Meta+[", "commandGoBack");
            addMenu(mb, menus, "Window|Minimize Meta+M", "commandWindowMinimize");
            addMenu(mb, menus, "Window|Zoom", "commandWindowZoom");
            addMenu(mb, menus, "Help|Zipeg_Help Meta+?", "commandHelpIndex");
        } else {
            addMenu(mb, menus, "&File|&Open Ctrl+O", "commandFileOpen");
            addMenu(mb, menus, "&File|&Close", "commandFileClose");
            addMenu(mb, menus, "&File|E&xit Alt+F4", "commandFileExit");
            addMenu(mb, menus, "&Actions|E&xtract", "commandActionsExtract");
            addMenu(mb, menus, "&Actions|---", null);
            addMenu(mb, menus, "&Actions|&Preview F5", "commandActionsPreview");
            addMenu(mb, menus, "&Tools|&Options Alt+F7", "commandToolsOptions");
            addMenu(mb, menus, "&Help|&About", "commandHelpAbout");
            addMenu(mb, menus, "&Help|&Index F1", "commandHelpIndex");
        }
        if (!mac.isFromAppStore()) {
            addMenu(mb, menus, "&Help|Check_For_&Update", "commandHelpCheckForUpdate");
        }
        addMenu(mb, menus, "&Help|&Support", "commandHelpSupport");
        addMenu(mb, menus, "&Help|---", null);
        addMenu(mb, menus, "&Help|&License", "commandHelpLicense");
        addMenu(mb, menus, "&Help|&Donate", "commandHelpDonate");
        mb.setBorder(null);
        return mb;
    }

    static JToolBar createToolBar() {
        JToolBar tb = new JToolBar() {
            public Insets getInsets() {
                if (Util.isMac && Util.osVersion >= 10.5) {
                    return new Insets(0, 0, 0, 0);
                } else {
                    return super.getInsets();
                }
            }
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                // System.err.println("getPreferredSize()=" + d);
                if (Util.isMac && Util.osVersion < 10.5 && TigerButton.maxHeight > 0) {
                    d.height = Math.min(d.height, TigerButton.maxHeight);
                }
                // System.err.println("getPreferredSize()=" + d);
                return d;
            }
        };
        tb.setRollover(true);
        tb.setFloatable(false);
        WindowDragger.makeDraggable(tb);
        // surround default LnF border with etched frame
        if (!Util.isMac) {
            tb.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), tb.getBorder()));
        }
        tb.setFocusable(true);
        tb.setRequestFocusEnabled(true);
        String prefix = Util.isMac ? "mac." : "win.";
        addButton(tb, prefix + "open", "commandFileOpen", "Open archive.", "Open");
        if (Util.isWindows) {
            tb.addSeparator(new Dimension(2, 1));
        }
        addButton(tb, prefix + "extract", "commandActionsExtract", "Extract selected items from the archive.", "Extract");
        if (Util.isWindows) {
            tb.addSeparator(new Dimension(2, 1));
        }
        addButton(tb, prefix + "preview", "commandActionsPreview", "Open selected items in Preview or associated application.", "Preview");
        if (Util.isWindows) {
            tb.addSeparator(new Dimension(10, 1));
        }
        addButton(tb, prefix + "preferences", "commandToolsOptions", Util.isMac ? "Preferences." : "Zipeg options.", Util.isMac ? "Preferences" : "Options");
        addSpacer(tb, 10);

        int extract_count = Presets.getInt("extract.count", 0);
        int donate_count = Presets.getInt("donate.count", 0);
        int like_count = Presets.getInt("like.count", 0);
        if (donate_count < 1 && (extract_count > 30 || like_count > 0)) {
            addButton(tb, "donate", "commandHelpDonate",
                    "<html><body>" +
                    "I've worked hard over the years to create&nbsp;<br>" +
                    "&nbsp;and bring Zipeg to you.&nbsp;<br><br>" +
                    "&nbsp;Please consider making a small donation&nbsp;<br>" +
                    "&nbsp;to support further Zipeg development.&nbsp;<br><br>" +
                    "&nbsp;<b>Thank you!</b>" +
                    "</body></html>", "");
            tb.addSeparator(new Dimension(2, 1));
        } else {
            if (extract_count > 10 && like_count < 1) {
                addSpacer(tb, 10);
                addButton(tb, "facebook", "commandLike", "Like Zipeg on Facebook", "Like");
            }
        }
        return tb;
    }

    private static void addSpacer(JToolBar tb, int width) {
        if (Util.isMac) {
            addSpacerMac(tb, width);
        } else {
            addSpacerWindows(tb, width);
        }
    }

    private static void addSpacerWindows(JToolBar tb, int width) {
        JComponent spacer = new JPanel();
        spacer.setPreferredSize(new Dimension(width, 40));
        spacer.setOpaque(false);
        WindowDragger.makeDraggable(spacer);
        tb.add(spacer);
    }

    private static void addSpacerMac(final JToolBar tb, final int width) {
        JComponent spacer;
        if (Util.osVersion < 10.6) {
            spacer = new JPanel(){
                public Insets getInsets() {
                    return new Insets(0, 0, 0, 0);
                }
                public Dimension getPreferredSize() {
                    return new Dimension(width, tb.getHeight());
                }
                public Dimension getMaximumSize() {
                    return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
                }
            };
        } else {
            spacer = new JButton() {
                public Insets getInsets() {
                    return new Insets(0, 0, 0, 0);
                }
                public Dimension getPreferredSize() {
                    return new Dimension(width, tb.getHeight());
                }
                public Dimension getMaximumSize() {
                    return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
                }
            };
        }
        spacer.setOpaque(false);
        spacer.setDoubleBuffered(false);
        spacer.setBackground(null);
        spacer.setBorder(null);
        WindowDragger.makeDraggable(spacer);
        tb.add(spacer);
    }

    private static class TigerButton extends JButton {

        private static int maxHeight;

        public TigerButton(AbstractAction aa) {
            super(aa);
        }

        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            Font font = getFont();
            int h = Math.round(font.getSize2D()) + 8;
            if (d.height + h > maxHeight) {
                maxHeight = d.height + h;
                // System.err.println("maxHeight=" + maxHeight);
            }
            return new Dimension(d.width + h, maxHeight);
        }

        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

    }

    private static void addButton(JToolBar tb, String iconname, final String method, String tooltip, String label) {
        AbstractAction aa = getAction(method);
        if (aa == null) {
            aa = new AbstractAction(){
                public void actionPerformed(ActionEvent actionEvent) {
                    invokeMethod(method);
                }
            };
            aa.setEnabled(false);
            addAction(method, aa);
        }
        boolean legacy = Util.isMac && Util.osVersion < 10.5; // Tiger
        JButton btn = legacy ? new TigerButton(aa) : new ToolbarButton(aa);
        btn.setBorderPainted(false);
        btn.setRolloverEnabled(true);
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setVerticalAlignment(SwingConstants.CENTER);
        if (label != null) {
            btn.setText(label);
            btn.setHorizontalTextPosition(SwingConstants.CENTER);
            btn.setVerticalTextPosition(SwingConstants.BOTTOM);
        }
        BufferedImage i = Resources.asBufferedImage(Resources.getImage(iconname));
        BufferedImage bi = Resources.asBufferedImage(i, +1, +2);

        btn.setIcon(new ImageIcon(bi));
        bi = Resources.asBufferedImage(i, +2, +3);
        btn.setPressedIcon(new ImageIcon(bi));

        bi = Resources.asBufferedImage(i, +1, 0);
        Resources.adjustHSB(bi, 1.2f, 2); // increase brightness by 20%
        btn.setRolloverIcon(new ImageIcon(bi));
        btn.setRolloverSelectedIcon(new ImageIcon(bi));

        btn.setPreferredSize(new Dimension(i.getWidth() + 8, i.getHeight() + 5));
        String tt = "<html><body>&nbsp;" + tooltip + "&nbsp;</body></html>";
        btn.setToolTipText(tt);
        btn.setAction(aa);
        btn.setBorderPainted(false);
        btn.setFocusable(false);
        tb.add(btn);
    }

    private static void addMenu(JMenuBar mb, Map menus, String command, String action) {
        StringTokenizer st = new StringTokenizer(command, "|");
        String top = st.nextToken();
        String s = top.replaceAll("&", "");
        JMenu menu = (JMenu)menus.get(s);
        if (menu == null) {
            menu = new JMenu(s);
            menus.put(s, menu);
            int ix = top.indexOf('&');
            if (ix >= 0 && ix < top.length() - 1) {
                menu.setMnemonic(top.charAt(ix + 1));
                menu.setDisplayedMnemonicIndex(ix);
            }
            mb.add(menu);
        }
        String cmd = st.nextToken();
        if ("---".equals(cmd)) {
            menu.add(new JSeparator());
            return;
        }
        JMenuItem item = parseItem(cmd, action, new MenuItemListener() {
            public void run(String method) {
                invokeMethod(method);
            }
        });
        menu.add(item);
    }

    private static JMenuItem parseItem(String cmd, String action, final MenuItemListener listener) {
        String a = null;
        int ix = cmd.lastIndexOf(' ');
        if (ix >= 0) {
            a = cmd.substring(ix + 1).trim();
            cmd = cmd.substring(0, ix);
        }
        String s = cmd.replaceAll("&", "");
        final String method = action != null ? action :
                "command" + s.replaceAll(" ", "").replaceAll("_", "");
        AbstractAction aa = new AbstractAction(){
            public void actionPerformed(ActionEvent actionEvent) {
                listener.run(method);
            }
        };
        JMenuItem item = new JMenuItem(aa);
        item.setText(s.replaceAll("_", " "));
        ix = cmd.indexOf('&');
        if (ix >= 0 && ix < cmd.length() - 1) {
            item.setMnemonic(cmd.charAt(ix + 1));
            item.setDisplayedMnemonicIndex(ix);
        }
        if (a != null) {
            parseAccelerator(item, a);
        }
        if (action != null) {
            addAction(action, aa);
        }
        return item;
    }

    private static void parseAccelerator(JMenuItem item, String a) {
        int mask = 0;
        while (a.length() > 0) {
            if (a.startsWith("Meta+")) {
                mask |= InputEvent.META_MASK;
                a = a.substring(5);
            } else if (a.startsWith("Ctrl+")) {
                mask |= InputEvent.CTRL_MASK;
                a = a.substring(5);
            } else if (a.startsWith("Alt+")) {
                mask |= InputEvent.ALT_MASK;
                a = a.substring(4);
            } else if (a.startsWith("Shift+")) {
                mask |= InputEvent.SHIFT_MASK;
                a = a.substring(6);
            } else {
                if (a.length() > 1) {
                    int kc = KeyStroke.getKeyStroke(a).getKeyCode();
                    item.setAccelerator(KeyStroke.getKeyStroke(kc, mask));
                } else if (a.charAt(0) == '?') {
                    // VK_QUESTIONMARK does not exist, however KeyEvent.VK_SLASH with
                    // SHIFT_MASK gives desired accelerator
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, mask|InputEvent.SHIFT_MASK));
                } else {
                    item.setAccelerator(KeyStroke.getKeyStroke(a.charAt(0), mask));
                }
                break;
            }
        }
        // Note: http://developer.apple.com/technotes/tn/tn2042.html
        // possibly better and more portable way:
        // item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C,
        // Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    }

    private static void invokeMethod(String method) {
        invokeMethod(method, Util.VOID, Util.NONE);
    }

    private static void invokeMethod(String method, Class[] signature, Object[] params) {
        assert signature != null;
        Object[] clone = listeners.toArray();
        int called = 0;
        for (int i = 0; i < clone.length; i++) {
            try {
                Method m = clone[i].getClass().getMethod(method, signature);
                m.invoke(clone[i], params);
                called++;
            } catch (NoSuchMethodException e) {
                /* method is optional */
            } catch (IllegalAccessException e) {
                throw new Error(method + " must be declared public in " + clone[i].getClass(), e);
            } catch (InvocationTargetException e) {
                throw new Error(e);
            }
        }
        if (called == 0) {
            // this is not an assert because it can happen on delayed quit...
            // see: crash 622f1311-5a19-4b8e-ba00-2ddadd35e26a
            Debug.traceln("Warning: nobody listens to " + method);
        }
    }

    public static void setEnabled(boolean b) {
        enabled = b;
        updateCommandState();
        mac.setEnabledAboutMenu(b);
        mac.setEnabledPreferencesMenu(b);
    }

    /** method to enable commands state
     * @param map command ids (like "commandFileOpen" to Boolean.TRUE/FALSE
     * @noinspection UnusedDeclaration
     */
    public static void updateCommandState(Map map) {
    }

    private static void updateCommandState() {
//      System.err.println("updateCommandState");
        long time = System.currentTimeMillis();
        Map state = new HashMap(instance.size() * 3 / 2);
        Object[] p = new Object[]{state};
        if (enabled) {
            Object[] clone = listeners.toArray();
            for (Object listener : clone) {
                try {
                    Method updateCommandState =
                            listener.getClass().getMethod("updateCommandState", Util.MAP);
                    updateCommandState.invoke(listener, p);
                } catch (NoSuchMethodException e) {
                    /* the updateCommandState is optional */
                    throw new Error(listener.getClass().getName() +
                                    " must implement method updateCommandState(Map)", e);
                  } catch (IllegalAccessException e) {
                    throw new Error(listener.getClass().getName() +
                                    " method updateCommandState(Map) is not public", e);
                } catch (InvocationTargetException e) {
                    throw new Error(e);
                }
            }
        }
        boolean changed = false;
        for (Iterator i = instance.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry)i.next();
            AbstractAction aa = (AbstractAction)e.getValue();
            if (!Boolean.TRUE.equals(state.get(e.getKey()))) {
                if (aa.isEnabled()) {
                    changed = true;
                    aa.setEnabled(false);
                }
            }
        }
        for (Iterator i = state.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry e = (Map.Entry)i.next();
            if (Boolean.TRUE.equals(e.getValue())) {
                AbstractAction aa = (AbstractAction)instance.get(e.getKey());
                if (aa != null && !aa.isEnabled()) {
                    changed = true;
                    aa.setEnabled(true);
                }
            }
        }
        if (changed && MainFrame.getMenu() != null) {
            MainFrame.getMenu().repaint();
        }
        time = System.currentTimeMillis() - time;
        if (time > IdlingEventQueue.DELAY) {
            Debug.traceln("updateCommandState: WARNING time = " + time + " milli");
        }
    }

}
