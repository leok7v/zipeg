package com.zipeg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Map;

public final class StatusBar extends JToolBar {

    private JLabel status;
    private JLabel info;
    private SubtleMessage message;
    private JPanel right;
    private JPanel left;
    private JProgressBar progress;
    private final int height;

    StatusBar() {
        setLayout(new BorderLayout());
        if (!Util.isMac) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLoweredBevelBorder(), this.getBorder()));
        } else {
            setBorder(null);
            setOpaque(false);
        }
        setFocusable(false);
        setFloatable(false);
        height = (int)(getFontMetrics(getFont()).getHeight() * (Util.isMac ? 1.5 : 1.7));
        left = new JPanel(new BorderLayout()) {
            public Insets getInsets() {
                Insets i = super.getInsets();
                i.left = 4;
                return i;
            }
        };
        add(left, BorderLayout.WEST);
        add(new JPanel(), BorderLayout.CENTER);
        status = new ShadowLabel("", SwingConstants.LEADING) {
            public Dimension getPreferredSize() {
                return new Dimension((StatusBar.this.getWidth() - 72) / 2, height);
            }
        };
        left.add(status, BorderLayout.WEST);
        status.setAlignmentY(BOTTOM_ALIGNMENT);
        left.add(createSpacer(), BorderLayout.CENTER);
        info = new ShadowLabel("", SwingConstants.TRAILING);
        left.add(info, BorderLayout.EAST);
        info.setAlignmentY(BOTTOM_ALIGNMENT);
        left.add(createSpacer(), BorderLayout.CENTER);
        right = new JPanel(new BorderLayout());
        GrowBox box = new GrowBox();
        box.setAlignmentY(BOTTOM_ALIGNMENT);
        right.add(box, BorderLayout.EAST);
        add(right, BorderLayout.EAST);
        WindowDragger.makeDraggableChildren(this);
        addComponentListener(new ComponentAdapter(){
            public void componentResized(ComponentEvent e) {
                left.revalidate();
                right.revalidate();
                repaint();
            }
        });
    }

    private JComponent createSpacer() {
        JComponent spacer;
        if (Util.isMac) {
            JToolBar tb = new JToolBar();
            tb.setFloatable(false);
            spacer = tb;
        } else {
            spacer = new JPanel();
        }
        spacer.setOpaque(false);
        spacer.setBorder(BorderFactory.createEmptyBorder());
        return spacer;
    }

    public void addNotify() {
        super.addNotify();
        Actions.addListener(this);
    }

    public void removeNotify() {
        Actions.removeListener(this);
        super.removeNotify();
    }

    public Insets getInsets() {
        return Util.isMac ? new Insets(0, 0, 0, 0) : super.getInsets();
    }

    public Dimension getPreferredSize() {
        return new Dimension(Integer.MAX_VALUE, height);
    }

    /** method to enable commands state
     * @param map command ids (like "commandFileOpen" to Boolean.TRUE/FALSE
     * @noinspection UnusedDeclaration
     */
    public static void updateCommandState(Map map) {
    }

    private void createProgress() {
        // create and remove progress bar "on-demand" because otherwise
        // apple.laf.AquaProgressBarUI.Animator will keep posting messages.
        // BUG in 1.4.2 Animator is always posting messages even when there is no
        // progress bar in sight
        assert progress == null;
        progress = new JProgressBar();
        right.add(progress, BorderLayout.WEST);
        progress.setAlignmentY(BOTTOM_ALIGNMENT);
        progress.setPreferredSize(new Dimension(100, height));
        progress.setVisible(false);
        progress.setMinimum(0);
        progress.setMaximum(99);
        progress.setValue(0);
        progress.setVisible(true);
        right.revalidate();
        left.revalidate();
        revalidate();
        repaint();
        InputBlocker.setInputDisabled(true);
    }

    private void removeProgress() {
        assert progress != null;
        right.remove(progress);
        right.revalidate();
        left.revalidate();
        revalidate();
        repaint();
        progress = null;
        InputBlocker.setInputDisabled(false);
    }

    /** set the text into status panel of status bar
     * Do not call directly from background threads.
     * Use Actions.postEvent("setInfo", "text") instead.
     * @param param message. plain text or html
     */

    public void setStatus(Object param) {
        assert IdlingEventQueue.isDispatchThread();
        String s = (String)param;
        if (!Util.equals(s, status.getText())) {
            status.setText(s);
        }
    }

    /** set the text into info panel of status bar
     * Do not call directly from background threads.
     * Use Actions.postEvent("setInfo", "text") instead.
     * @param param message. plain text or html
     */

    public void setInfo(Object param) {
        assert IdlingEventQueue.isDispatchThread();
        String s = (String)param;
        if (!Util.equals(s, info.getText())) {
            info.setText(s);
        }
    }

    /** shows done or error message to the user
     * Do not call directly from background threads.
     * Use Actions.postEvent("showMessage", "message text") instead.
     * Message will be wraped into < html > tags and colored Red or Blue
     * depending on "error: " prefix.
     * @param param message. Error messages must start with "error: "
     */

    public void setMessage(Object param) {
        assert IdlingEventQueue.isDispatchThread();
        String s = (String)param;
        if (message != null) {
            message.dismiss();
            message = null;
        }
        if (s.length() > 0 && MainFrame.getInstance() != null) {
            message = new SubtleMessage(s);
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void commandFileClose() {
        setMessage("");
        setStatus("");
        setInfo("");
    }

    public void setProgress(Object p) {
        if (MainFrame.getInstance() != null) {
            assert IdlingEventQueue.isDispatchThread();
            assert p instanceof Float;
            float f = ((Float)p).floatValue();
            assert 0 <= f && f <= 1.0;
            if (f > 0) {
                setMessage("");
                if (progress == null) {
                    createProgress();
                    Cursor wait = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
                    MainFrame.getInstance().pushCursor(wait);
                    // because of:
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5023342
                    //   MainFrame.getInstance().setEnabled(false);
                    // cannot be used and even when used does not disable menus on Mac OS X
                    Actions.setEnabled(false);
                }
                progress.setValue(Math.round(100 * f));
            } else if (progress != null) {
                removeProgress();
                Actions.setEnabled(true);
                MainFrame.getInstance().popCursor();
            }
        }
    }

    public boolean inProgress() {
        return progress != null;
    }

    private class GrowBox extends JComponent {

        GrowBox() {
            setOpaque(false);
            setFocusable(false);
            setRequestFocusEnabled(false);
        }

        private final Color b = new JLabel().getBackground();
        private final Color[] lines = Util.isWindows ? new Color[]{b.brighter(), b.darker()} :
                new Color[]{Color.DARK_GRAY, Color.WHITE, Color.LIGHT_GRAY, Color.GRAY};

        public void paint(Graphics g) {
            Graphics2D g2d = (Graphics2D)g;
            int x = 0;
            Color s = g2d.getColor();
            if (Util.isWindows) {
                int n = getWidth() - 1;
                int y = 0;
                for (int dy = 0; dy < n; dy += 4) {
                    for (int dx = dy; dx > 0; dx -= 4) {
                        g2d.setColor(lines[0]);
                        // InvalidPipeException in sun.awt.windows.Win32Renderer.doShape()
                        g2d.fill(new Rectangle(n - dx + 1, y + dy + 1, 2, 2));
                        g2d.setColor(lines[1]);
                        g2d.fill(new Rectangle(n - dx, y + dy, 2, 2));
                    }
                }
            } else if (Util.isMac) {
                int y = getHeight() - 19;
                for (int i = 0; i < 15; i++) {
                    Color c = lines[i % 4];
                    g2d.setColor(c);
                    g2d.drawLine(x + i, y + 14, x + 14, y + i);
                }
            }
            g2d.setColor(s);
        }

        public Dimension getPreferredSize() {
            return new Dimension(18, height);
        }

        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    private static class SubtleMessage extends JLabel {

        private float transparency = 0.1f;
        private MainFrame mf = MainFrame.getInstance();
        private JComponent gp = (JComponent)mf.getGlassPane();
        private Sound sound;
        private KeyListener keyListener;
        private MouseListener mouseListener;
        private ComponentListener resized;
        private int tick = 50;

        SubtleMessage(String message) {
            super(wrapMessage(message), CENTER);
            setOpaque(false);
            setFocusable(true);
            setRequestFocusEnabled(true);
            adjustSize();
            gp.add(this);
            gp.setVisible(true);
            setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            repaint();
            if (Flags.getFlag(Flags.PLAY_SOUNDS)) {
                String r = isError(message) ? "resources/error.wav" : "resources/done.wav";
                sound = new Sound(Resources.getResourceAsStream(r), false);
            }
        }

        private void adjustSize() {
            setBounds(((ContentPane)mf.getContentPane()).getViewBounds());
        }

        private boolean isError(String s) {
            return s.startsWith("error:");
        }

        private static String wrapMessage(String s) {
            if (s.startsWith("error:")) {
                return "<html><body><font size=5 color=red>" + s + "</font></body></html>";
            } else {
                return "<html><body><font size=5 color=blue>" + s + "</font></body></html>";
            }
        }

        public void paint(Graphics g) {
            float a = Math.abs(transparency);
            Graphics2D g2d = (Graphics2D)g;
            Composite c = g2d.getComposite();
            Shape r = new Rectangle(0, 0, getWidth(), getHeight());
            g2d.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, Math.abs(0.25f) * a));
            g2d.setColor(Color.BLACK);
            g2d.fill(r);
            g2d.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, a));
            int h = Math.min(120, getHeight() / 4);
            int y = (getHeight() - h) / 2;
            Shape e = new RoundRectangle2D.Double(15, y, getWidth() - 30, h, 45, 45);
            g2d.setColor(Color.WHITE);
            g2d.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 0.8f * a));
            g2d.fill(e);
            super.paint(g);
            g2d.setComposite(c);
            if (transparency == 0) {
                IdlingEventQueue.invokeLater(new Runnable(){
                    public void run() { close(); }
                });
            } else if (a < 1.0) {
                int delay = Math.max(5, tick - (int)(25.f * (1.f - a)));
                if (a > 0.8) {
                    delay = delay * 2;
                }
//              System.err.println(System.currentTimeMillis() + " " + delay + " " + transparency);
                Util.invokeLater(delay, new Runnable(){
                    public void run() {
                        if (transparency > 0) {
                            transparency = Math.min(1.0f, transparency + 0.02f);
                            repaint();
                            if (transparency > 0.99f) {
                                transparency = -0.99f;
                            }
                        } else if (transparency < 0) {
                            transparency = Math.min(0, transparency + 0.02f);
                            repaint();
                        }
                    }
                });
            }
        }

        public void addNotify() {
            super.addNotify();
            keyListener = new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    dismiss();
                }
            };
            mouseListener = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dismiss();
                }
            };
            resized = new ComponentAdapter(){
                public void componentResized(ComponentEvent e) { adjustSize(); }
                public void componentMoved(ComponentEvent e) { adjustSize(); }
            };
            addKeyListener(keyListener);
            addMouseListener(mouseListener);
            mf.addComponentListener(resized);
            requestFocusInWindow(); // so ESC will work
        }

        public void removeNotify() {
            removeKeyListener(keyListener);
            removeMouseListener(mouseListener);
            mf.removeComponentListener(resized);
            super.removeNotify();
        }

        private void dismiss() {
            if (transparency > 0) {
                transparency = -0.99f;
                tick = 25; // dismiss faster
                repaint();
            }
        }

        private void close() {
            if (gp != null) {
                gp.remove(SubtleMessage.this);
                gp.revalidate();
                gp.setVisible(false);
                gp = null;
            }
            if (sound != null) {
                sound.stop();
                sound = null;
            }
        }
    }

}
