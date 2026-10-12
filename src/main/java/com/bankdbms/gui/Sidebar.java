package com.bankdbms.gui;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Left navigation for the main window. It only selects tabs of the existing tabbed pane, so each
 * screen still reloads its data through the tab-change listener exactly as before.
 */
public class Sidebar extends JPanel {

    private static final int SIDEBAR_WIDTH = 236;
    private final List<JToggleButton> items = new ArrayList<>();

    public Sidebar(JTabbedPane tabs, String connectionInfo) {
        super(new BorderLayout());
        setBackground(Theme.NAVY);
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(Theme.padding(Theme.SPACE_XL - 4, Theme.SPACE_M));
        top.add(brand());
        top.add(Box.createVerticalStrut(Theme.SPACE_XL));
        JLabel menu = new JLabel("MENU");
        menu.setFont(new Font(Theme.LABEL.getFamily(), Font.BOLD, 11));
        menu.setForeground(new Color(0x94A3B8));
        menu.setBorder(Theme.padding(0, Theme.SPACE_M));
        menu.setAlignmentX(0f);
        top.add(menu);
        top.add(Box.createVerticalStrut(Theme.SPACE_S));

        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            int index = i;
            String title = tabs.getTitleAt(i);
            JToggleButton item = new NavItem(title, new NavIcon(i));
            item.setToolTipText(title + "  (" + shortcutText() + (i + 1) + ")");
            item.addActionListener(e -> tabs.setSelectedIndex(index));
            group.add(item);
            items.add(item);
            top.add(item);
            top.add(Box.createVerticalStrut(2));
        }
        tabs.addChangeListener(e -> select(tabs.getSelectedIndex()));
        select(tabs.getSelectedIndex());
        installArrowKeys();
        add(top, BorderLayout.NORTH);
        add(connection(connectionInfo), BorderLayout.SOUTH);
    }

    private void select(int index) {
        if (index >= 0 && index < items.size()) {
            items.get(index).setSelected(true);
        }
    }

    /** Up/Down move between items while one has keyboard focus. */
    private void installArrowKeys() {
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            JToggleButton item = items.get(i);
            item.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "next");
            item.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("UP"), "previous");
            item.getActionMap().put("next", focusAction(() -> items.get((index + 1) % items.size())));
            item.getActionMap().put("previous", focusAction(() -> items.get((index - 1 + items.size()) % items.size())));
        }
    }

    private static AbstractAction focusAction(java.util.function.Supplier<JToggleButton> target) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                target.get().requestFocusInWindow();
            }
        };
    }

    static String shortcutText() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac") ? "⌘" : "Ctrl+";
    }

    private static JComponent brand() {
        JPanel brand = new JPanel(new BorderLayout(Theme.SPACE_M, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(0f);
        brand.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 48));
        brand.setBorder(Theme.padding(0, Theme.SPACE_S));
        JComponent mark = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.smooth(g);
                g2.setColor(Theme.TEAL);
                g2.fillRoundRect(0, 0, 36, 36, 10, 10);
                g2.setColor(Color.WHITE);
                // a simple bank: pediment, three columns, base
                g2.fillPolygon(new int[]{8, 18, 28}, new int[]{14, 8, 14}, 3);
                for (int x : new int[]{10, 16, 22}) {
                    g2.fillRect(x, 16, 4, 9);
                }
                g2.fillRect(8, 26, 20, 3);
                g2.dispose();
            }
        };
        mark.setPreferredSize(new Dimension(36, 36));
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        JLabel name = new JLabel("Bank DBMS");
        name.setFont(new Font(Theme.LABEL.getFamily(), Font.BOLD, 16));
        name.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Accounts & transactions");
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.SIDEBAR_TEXT);
        text.add(name, BorderLayout.NORTH);
        text.add(sub, BorderLayout.SOUTH);
        brand.add(mark, BorderLayout.WEST);
        brand.add(text, BorderLayout.CENTER);
        return brand;
    }

    private static JComponent connection(String info) {
        JPanel panel = new JPanel(new BorderLayout(Theme.SPACE_S, 2));
        panel.setBackground(Theme.NAVY_DEEP);
        panel.setBorder(Theme.padding(Theme.SPACE_M + 2, Theme.SPACE_L + 4));
        JLabel state = new JLabel("Connected");
        state.setIcon(new DotIcon(new Color(0x4ADE80)));
        state.setIconTextGap(Theme.SPACE_S);
        state.setFont(Theme.LABEL);
        state.setForeground(Color.WHITE);
        String shown = info.startsWith("Connected to ") ? info.substring("Connected to ".length()) : info;
        JLabel detail = new JLabel("<html><div style='width:160px'>" + shown + "</div></html>");
        detail.setFont(Theme.SMALL);
        detail.setForeground(Theme.SIDEBAR_TEXT);
        panel.add(state, BorderLayout.NORTH);
        panel.add(detail, BorderLayout.CENTER);
        panel.getAccessibleContext().setAccessibleName("Database connection: " + info);
        return panel;
    }

    /** A navigation entry: icon and title, teal bar and lighter fill when selected, ring on keyboard focus. */
    private static final class NavItem extends JToggleButton {
        NavItem(String title, Icon icon) {
            super(title, icon);
            setFont(Theme.BUTTON);
            setHorizontalAlignment(LEFT);
            setIconTextGap(Theme.SPACE_M);
            setBorder(Theme.padding(0, Theme.SPACE_M + 2));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setAlignmentX(0f);
            setMaximumSize(new Dimension(SIDEBAR_WIDTH, 40));
            setPreferredSize(new Dimension(SIDEBAR_WIDTH - 2 * Theme.SPACE_M, 40));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.smooth(g);
            if (isSelected()) {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Theme.TEAL);
                g2.fillRoundRect(0, 9, 4, getHeight() - 18, 4, 4);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            if (isFocusOwner()) {
                g2.setColor(new Color(0x93C5FD));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
            }
            Color fg = isSelected() ? Color.WHITE : Theme.SIDEBAR_TEXT;
            java.awt.Insets in = getInsets();
            Icon icon = getIcon();
            int x = in.left;
            if (icon instanceof NavIcon nav) {
                nav.colour = fg;
                icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
                x += icon.getIconWidth() + getIconTextGap();
            }
            g2.setFont(getFont());
            g2.setColor(fg);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private record DotIcon(Color colour) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Theme.smooth(g);
            g2.setColor(colour);
            g2.fillOval(x, y + 1, 8, 8);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 8;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }

    /** 18px line icons, one per screen in tab order. */
    private static final class NavIcon implements Icon {
        private final int kind;
        Color colour = Color.WHITE;

        NavIcon(int kind) {
            this.kind = kind;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.setColor(colour);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case 0 -> {        // dashboard: four tiles
                    g2.drawRoundRect(1, 1, 7, 7, 2, 2);
                    g2.drawRoundRect(10, 1, 7, 7, 2, 2);
                    g2.drawRoundRect(1, 10, 7, 7, 2, 2);
                    g2.drawRoundRect(10, 10, 7, 7, 2, 2);
                }
                case 1 -> {        // customers: person
                    g2.drawOval(5, 1, 8, 8);
                    g2.drawArc(1, 11, 16, 12, 0, 180);
                }
                case 2 -> {        // accounts: card
                    g2.drawRoundRect(1, 3, 16, 12, 3, 3);
                    g2.drawLine(1, 7, 17, 7);
                    g2.drawLine(4, 11, 8, 11);
                }
                case 3 -> {        // transactions: opposing arrows
                    g2.drawLine(2, 6, 16, 6);
                    g2.drawPolyline(new int[]{12, 16, 12}, new int[]{2, 6, 10}, 3);
                    g2.drawLine(2, 12, 16, 12);
                    g2.drawPolyline(new int[]{6, 2, 6}, new int[]{8, 12, 16}, 3);
                }
                case 4 -> {        // beneficiaries: bookmark
                    g2.drawPolygon(new int[]{4, 14, 14, 9, 4}, new int[]{1, 1, 17, 12, 17}, 5);
                }
                case 5 -> {        // loans: percent
                    g2.drawLine(3, 15, 15, 3);
                    g2.drawOval(2, 2, 4, 4);
                    g2.drawOval(12, 12, 4, 4);
                }
                case 6 -> {        // branches: building
                    g2.drawRect(3, 4, 12, 13);
                    g2.drawLine(1, 17, 17, 17);
                    g2.drawLine(7, 8, 7, 8);
                    g2.drawLine(11, 8, 11, 8);
                    g2.drawLine(7, 12, 7, 12);
                    g2.drawLine(11, 12, 11, 12);
                }
                default -> {       // reports: bar chart
                    g2.drawLine(1, 17, 17, 17);
                    g2.drawLine(4, 14, 4, 9);
                    g2.drawLine(9, 14, 9, 3);
                    g2.drawLine(14, 14, 14, 7);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }
}
