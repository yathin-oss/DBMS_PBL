package com.bankdbms.gui;

import javax.swing.JTextField;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

/** A text field that shows a muted placeholder while it is empty; the hint is also its accessible name. */
public class HintTextField extends JTextField {

    private final String hint;

    public HintTextField(String hint, int columns) {
        super(columns);
        this.hint = hint;
        getAccessibleContext().setAccessibleName(hint);
        setToolTipText(hint);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty()) {
            Graphics2D g2 = Theme.smooth(g);
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(getFont());
            Insets in = getInsets();
            int baseline = in.top + (getHeight() - in.top - in.bottom - g2.getFontMetrics().getHeight()) / 2
                    + g2.getFontMetrics().getAscent();
            g2.drawString(hint, in.left, baseline);
            g2.dispose();
        }
    }
}
