package com.zipeg;

import javax.swing.*;
import java.awt.*;

public class ShadowLabel extends JLabel {

    private int width;

    public ShadowLabel(String text) {
        super(text);
    }

    public ShadowLabel(String text, int horizontalAlignment) {
        super(text, horizontalAlignment);
    }

    public String getText() {
        return super.getText();
    }

    public void setText(String s) {
        super.setText(s);
    }

    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return width == 0 ? d : new Dimension(Math.max(width, d.width), d.height);
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public Insets getInsets() {
        return new Insets(0, 4, 0, 4);
    }

    protected void paintComponent(Graphics g) {
        if (Util.isMac) {
            Font f = g.getFont();
            Color c = g.getColor();
            try {
                Font bf = UIManager.getFont("Button.font");
                if (bf != null) {
                    g.setFont(bf);
                }
                int h = g.getFontMetrics().getHeight();
                int y = getHeight() - ((getHeight() - h) / 2) - g.getFontMetrics().getDescent();
                String s = super.getText();
                int w = g.getFontMetrics().stringWidth(s);
                width = w + 8;
                Color shadow = new Color(192,192,192);
                g.setColor(shadow);
                int x;
                if (getHorizontalAlignment() == JLabel.LEADING) {
                    x = 0;
                } else if (getHorizontalAlignment() == JLabel.TRAILING) {
                    x = getWidth() - w;
                } else {
                    x = (getWidth() - w) / 2;
                }
                g.drawString(s, x, y + 1);
                g.setColor(c);
                if (isEnabled()) {
                    g.drawString(s, x, y);
                }
            } finally {
                g.setColor(c);
                g.setFont(f);
            }
        } else {
            super.paintComponent(g);
        }
    }

}