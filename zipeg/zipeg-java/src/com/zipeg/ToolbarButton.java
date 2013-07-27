package com.zipeg;

import javax.swing.*;
import java.awt.*;

public class ToolbarButton extends JButton {

    private static int maxHeight;

    public ToolbarButton(AbstractAction aa) {
        super(aa);
    }

    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        Font font = getFont();
        int h = Math.round(font.getSize2D()) + 8;
        if (d.height + h > maxHeight) {
            maxHeight = d.height + h;
        }
        return new Dimension(d.width + h, maxHeight);
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    public String getText() {
        return Util.isMac ? " " : super.getText();
    }

    protected void paintComponent(Graphics g) {
        if (Util.isMac) {
            Color c = g.getColor();
            try {
                Insets i = getInsets();
                int y = getIconTextGap() + getIcon().getIconHeight() + i.top + g.getFontMetrics().getHeight();
                String s = super.getText();
                int w = g.getFontMetrics().stringWidth(s);
                Color disabledTextColor = isEnabled() ? new Color(192,192,192) :
                        UIManager.getColor("Button.disabledText");
                g.setColor(disabledTextColor);
                int x = (getWidth() - w) / 2;
                g.drawString(super.getText(), x, y + (isEnabled() ? 1 : 0));
                g.setColor(c);
                if (isEnabled()) {
                    g.drawString(super.getText(), x, y);
                }
            } finally {
                g.setColor(c);
            }
        }
        super.paintComponent(g);
    }

}
