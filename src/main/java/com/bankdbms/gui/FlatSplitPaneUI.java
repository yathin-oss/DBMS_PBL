package com.bankdbms.gui;

import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Split pane whose divider is a gap between cards with a small grip in the middle. */
public class FlatSplitPaneUI extends BasicSplitPaneUI {

    public static ComponentUI createUI(JComponent c) {
        return new FlatSplitPaneUI();
    }

    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        return new BasicSplitPaneDivider(this) {
            @Override
            public void paint(Graphics g) {
                Graphics2D g2 = Theme.smooth(g);
                g2.setColor(Theme.PAGE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new java.awt.Color(0xCBD5E1));
                if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
                    g2.fillRoundRect(getWidth() / 2 - 18, getHeight() / 2 - 2, 36, 4, 4, 4);
                } else {
                    g2.fillRoundRect(getWidth() / 2 - 2, getHeight() / 2 - 18, 4, 36, 4, 4);
                }
                g2.dispose();
            }
        };
    }
}
