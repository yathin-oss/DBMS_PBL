package com.bankdbms.gui;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.geom.RoundRectangle2D;

/** A white surface with a hairline border and rounded corners; the basic container of every screen. */
public class Card extends JPanel {

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBackground(Theme.CARD);
        setBorder(Theme.padding(Theme.SPACE_L, Theme.SPACE_L + 2));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f,
                Theme.RADIUS * 2f, Theme.RADIUS * 2f);
        g2.setColor(getBackground());
        g2.fill(shape);
        g2.setColor(Theme.BORDER);
        g2.draw(shape);
        g2.dispose();
    }
}
