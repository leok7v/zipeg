package com.zipeg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

public final class InputBlocker extends JLabel {

    private float transparency = 0.1f;
    private static final int tick = 50;
    private static int inputDisabled;
    private static InputBlocker instance;
    private ComponentListener resized = new ComponentAdapter(){
        public void componentResized(ComponentEvent e) { adjustSize(); }
        public void componentMoved(ComponentEvent e) { adjustSize(); }
    };
    private static final MouseAdapter muteMouse = new MouseAdapter() {
        public void mousePressed(MouseEvent me) {}
    };
/*  // TODO: eats JDialog keyboard input.
    private static final KeyEventDispatcher muteKeyboard = new KeyEventDispatcher() {
        public boolean dispatchKeyEvent(KeyEvent e) { return true; }
    };
*/

    private InputBlocker(String message) {
        super(message, CENTER);
        setOpaque(false);
        setFocusable(true);
        setRequestFocusEnabled(true);
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        repaint();
    }

    public static void setInputDisabled(boolean b) {
        inputDisabled += b ? + 1 : -1;
        assert 0 <= inputDisabled && inputDisabled <= 1 : "inputDisabled=" + inputDisabled;
        JComponent gp = getGlassPane();
        if (gp != null) {
            if (inputDisabled == 1) {
                if (instance == null) {
                    instance = new InputBlocker(
                            "<html><body><font size=5 color=green>" +
                            "Working... Please stand by..."+
                            "</font></body></html>");
                }
                gp.addMouseListener(muteMouse);
                gp.add(instance);
                gp.revalidate();
                gp.setVisible(true);
                instance.adjustSize();
//              KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(muteKeyboard);
            } else if (inputDisabled == 0) {
                gp.setVisible(false);
                gp.remove(instance);
                gp.revalidate();
                gp.removeMouseListener(muteMouse);
//              KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(muteKeyboard);
            }
        }
    }

    private static MainFrame getMainFrame() {
        return MainFrame.getInstance();
    }

    private static ContentPane getContentPane() {
        MainFrame mf = MainFrame.getInstance();
        return mf != null ? (ContentPane)mf.getContentPane() : null;
    }

    private static JComponent getGlassPane() {
        return getMainFrame() != null ? (JComponent)getMainFrame().getGlassPane() : null;
    }

    private void adjustSize() {
        setBounds(getContentPane().getViewBounds());
    }

    public void paint(Graphics g) {
        float a = Math.abs(transparency);
        Graphics2D g2d = (Graphics2D)g;
        Composite c = g2d.getComposite();
        Shape r = new Rectangle(0, 0, getWidth(), getHeight());
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.abs(0.25f) * a));
        g2d.setColor(Color.BLACK);
        g2d.fill(r);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        int h = Math.min(120, getHeight() / 4);
        int y = (getHeight() - h) / 2;
        Shape e = new RoundRectangle2D.Double(15, y, getWidth() - 30, h, 45, 45);
        g2d.setColor(Color.WHITE);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f * a));
        g2d.fill(e);
        super.paint(g);
        g2d.setComposite(c);
        if (a < 1.0) {
            int delay = Math.max(5, tick - (int)(25.f * (1.f - a)));
            if (a > 0.8) {
                delay = delay * 2;
            }
            Util.invokeLater(delay, new Runnable(){
                public void run() {
                    if (transparency > 0) {
                        transparency = Math.min(1.0f, transparency + 0.02f);
                        if (transparency > 0.80f) {
                            transparency = 0.80f;
                        } else {
                            repaint();
                        }
                    }
                }
            });
        }
    }

    public void addNotify() {
        super.addNotify();
        MainFrame mf = getMainFrame();
        if (mf != null) {
            getMainFrame().addComponentListener(resized);
            requestFocusInWindow();
        }
    }

    public void removeNotify() {
        MainFrame mf = getMainFrame();
        if (mf != null) {
            removeComponentListener(resized);
        }
        super.removeNotify();
    }

}