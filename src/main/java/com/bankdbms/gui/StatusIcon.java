package com.bankdbms.gui;

import javax.swing.Icon;
import javax.swing.plaf.UIResource;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Round dialog icon with a symbol, in the palette's status colours (replaces the dated Metal icons). */
public final class StatusIcon implements Icon, UIResource {

    public enum Kind { INFO, QUESTION, WARNING, ERROR, SUCCESS }

    private static final int SIZE = 36;
    private final Kind kind;

    public StatusIcon(Kind kind) {
        this.kind = kind;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Color fg;
        Color bg;
        String symbol;
        switch (kind) {
            case ERROR -> {
                fg = Theme.ERROR;
                bg = new Color(0xFEE2E2);
                symbol = "!";
            }
            case WARNING -> {
                fg = Theme.WARNING;
                bg = new Color(0xFEF3C7);
                symbol = "!";
            }
            case SUCCESS -> {
                fg = Theme.SUCCESS;
                bg = new Color(0xDCFCE7);
                symbol = null;
            }
            case QUESTION -> {
                fg = Theme.BLUE;
                bg = Theme.SELECTION;
                symbol = "?";
            }
            default -> {
                fg = Theme.BLUE;
                bg = Theme.SELECTION;
                symbol = "i";
            }
        }
        Graphics2D g2 = Theme.smooth(g);
        g2.setColor(bg);
        g2.fillOval(x, y, SIZE, SIZE);
        g2.setColor(fg);
        if (symbol == null) {
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolyline(new int[]{x + 11, x + 16, x + 25}, new int[]{y + 19, y + 24, y + 13}, 3);
        } else {
            g2.setFont(new Font(Theme.BODY.getFamily(), Font.BOLD, 20));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(symbol, x + (SIZE - fm.stringWidth(symbol)) / 2, y + (SIZE - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
