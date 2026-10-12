package com.bankdbms.gui;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;

/** Tabs drawn as text with a blue underline under the selected one (used for sub-sections). */
public class UnderlineTabbedPaneUI extends BasicTabbedPaneUI {

    public static ComponentUI createUI(JComponent c) {
        return new UnderlineTabbedPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabPane.setOpaque(false);
        tabPane.setFont(Theme.BUTTON);
        tabInsets = new Insets(10, 14, 10, 14);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(0, 0, 0, 0);
        contentBorderInsets = new Insets(1, 0, 0, 0);
    }

    @Override
    protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
        if (!selected && index == getRolloverTab()) {
            g.setColor(Theme.HOVER);
            g.fillRect(x, y, w, h - 1);
        }
    }

    @Override
    protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
        if (selected) {
            g.setColor(Theme.BLUE);
            g.fillRect(x, y + h - 3, w, 3);
        }
    }

    @Override
    protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics, int index, String title,
                             Rectangle textRect, boolean selected) {
        Graphics2D g2 = Theme.smooth(g);
        g2.setFont(font);
        g2.setColor(!tabPane.isEnabledAt(index) ? Theme.DISABLED_TEXT : selected ? Theme.BLUE_HOVER : Theme.TEXT_MUTED);
        g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
        g2.dispose();
    }

    @Override
    protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index, Rectangle iconRect,
                                       Rectangle textRect, boolean selected) {
        if (tabPane.hasFocus() && selected) {
            Graphics2D g2 = Theme.smooth(g);
            Rectangle r = rects[index];
            g2.setColor(Theme.BLUE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 6, 6, 6);
            g2.dispose();
        }
    }

    @Override
    protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
        g.setColor(Theme.BORDER);
        g.fillRect(0, calculateTabAreaHeight(placement, runCount, maxTabHeight), tabPane.getWidth(), 1);
    }
}
