package com.bankdbms.gui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Path2D;

/** Drop-down styled like the text fields: rounded outline, focus ring, chevron arrow. */
public class FlatComboBoxUI extends BasicComboBoxUI {

    public static ComponentUI createUI(JComponent c) {
        return new FlatComboBoxUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        comboBox.setBorder(new Theme.FieldBorder(new java.awt.Insets(4, 6, 4, 4)));
        comboBox.setOpaque(true);
    }

    @Override
    protected JButton createArrowButton() {
        JButton arrow = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.smooth(g);
                g2.setColor(comboBox.isEnabled() ? Theme.TEXT_MUTED : Theme.DISABLED_TEXT);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                Path2D chevron = new Path2D.Float();
                chevron.moveTo(cx - 4, cy - 2);
                chevron.lineTo(cx, cy + 2);
                chevron.lineTo(cx + 4, cy - 2);
                g2.draw(chevron);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(24, 20);
            }
        };
        arrow.setName("ComboBox.arrowButton");
        arrow.setFocusable(false);
        arrow.setBorder(null);
        arrow.setOpaque(false);
        arrow.setContentAreaFilled(false);
        return arrow;
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        g.setColor(comboBox.isEnabled() ? Theme.CARD : Theme.HOVER);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /** The current value is never drawn "selected"; keyboard focus is shown by the outline instead. */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
        ListCellRenderer renderer = comboBox.getRenderer();
        Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);
        c.setFont(comboBox.getFont());
        c.setForeground(comboBox.isEnabled() ? Theme.TEXT : Theme.DISABLED_TEXT);
        c.setBackground(comboBox.isEnabled() ? Theme.CARD : Theme.HOVER);
        if (c instanceof JComponent jc) {
            jc.setOpaque(false);
        }
        currentValuePane.paintComponent(g, c, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, false);
    }
}
