package com.bankdbms.gui;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;

/**
 * Flat, rounded buttons in three variants chosen with the {@link #VARIANT} client property:
 * primary (filled blue), secondary (outlined, the default) and danger (red outline). The default
 * button of a dialog is drawn as primary. A blue ring shows keyboard focus.
 */
public class FlatButtonUI extends BasicButtonUI {

    public static final String VARIANT = "bank.variant";
    public static final String PRIMARY = "primary";
    public static final String SECONDARY = "secondary";
    public static final String DANGER = "danger";

    private static final int RING = 2;

    public static ComponentUI createUI(JComponent c) {
        return new FlatButtonUI();
    }

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setFocusPainted(false);
        if (b.getBorder() == null || b.getBorder() instanceof UIResource) {
            b.setBorder(new BorderResource(7 + RING, 14 + RING, 7 + RING, 14 + RING));
        }
        if (b.getFont() == null || b.getFont() instanceof UIResource) {
            b.setFont(new javax.swing.plaf.FontUIResource(Theme.BUTTON));
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel m = b.getModel();
        String variant = variant(b);
        boolean enabled = b.isEnabled();
        boolean pressed = m.isArmed() && m.isPressed();
        boolean hover = m.isRollover();

        Color fill;
        Color outline;
        if (PRIMARY.equals(variant)) {
            fill = !enabled ? Theme.DISABLED_TEXT : pressed ? Theme.BLUE_PRESSED : hover ? Theme.BLUE_HOVER : Theme.BLUE;
            outline = null;
        } else if (DANGER.equals(variant)) {
            fill = pressed ? new Color(0xFEE2E2) : hover ? new Color(0xFEF2F2) : Theme.CARD;
            outline = enabled ? new Color(0xF19A9A) : Theme.BORDER;
        } else {
            fill = pressed ? Theme.BORDER : hover ? Theme.HOVER : Theme.CARD;
            outline = enabled ? new Color(0xCBD5E1) : Theme.BORDER;
        }

        Graphics2D g2 = Theme.smooth(g);
        float w = c.getWidth() - 2f * RING;
        float h = c.getHeight() - 2f * RING;
        RoundRectangle2D shape = new RoundRectangle2D.Float(RING, RING, w, h, Theme.RADIUS_CONTROL * 2, Theme.RADIUS_CONTROL * 2);
        g2.setColor(fill);
        g2.fill(shape);
        if (outline != null) {
            g2.setColor(outline);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(RING + 0.5f, RING + 0.5f, w - 1, h - 1,
                    Theme.RADIUS_CONTROL * 2, Theme.RADIUS_CONTROL * 2));
        }
        if (b.isFocusOwner()) {
            g2.setColor(Theme.BLUE);
            g2.setStroke(new BasicStroke(RING));
            g2.draw(new RoundRectangle2D.Float(1, 1, c.getWidth() - 2f, c.getHeight() - 2f,
                    (Theme.RADIUS_CONTROL + 2) * 2, (Theme.RADIUS_CONTROL + 2) * 2));
        }
        g2.dispose();
        super.paint(g, c);
    }

    @Override
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
        String variant = variant(b);
        Color colour;
        if (!b.isEnabled()) {
            colour = PRIMARY.equals(variant) ? Color.WHITE : Theme.DISABLED_TEXT;
        } else if (PRIMARY.equals(variant)) {
            colour = Color.WHITE;
        } else if (DANGER.equals(variant)) {
            colour = Theme.ERROR;
        } else {
            colour = Theme.TEXT;
        }
        Graphics2D g2 = Theme.smooth(g);
        g2.setColor(colour);
        FontMetrics fm = g2.getFontMetrics(b.getFont());
        BasicGraphicsUtils.drawStringUnderlineCharAt(b, g2, text, b.getDisplayedMnemonicIndex(),
                textRect.x, textRect.y + fm.getAscent());
        g2.dispose();
    }

    @Override
    protected void paintButtonPressed(Graphics g, AbstractButton b) {
        // pressed state is part of the fill colour
    }

    private static String variant(AbstractButton b) {
        if (b instanceof JButton jb && jb.isDefaultButton()) {
            return PRIMARY;
        }
        Object v = b.getClientProperty(VARIANT);
        return v instanceof String s ? s : SECONDARY;
    }

    private static final class BorderResource extends EmptyBorder implements UIResource {
        BorderResource(int top, int left, int bottom, int right) {
            super(top, left, bottom, right);
        }
    }
}
