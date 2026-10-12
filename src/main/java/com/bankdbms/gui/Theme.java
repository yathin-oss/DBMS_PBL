package com.bankdbms.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.util.Set;

/**
 * Design tokens (colours, type, spacing, radii) and the application look and feel. Presentation
 * only: it changes how components are painted, never what they do.
 */
public final class Theme {

    // ---- colour tokens ----
    public static final Color NAVY = new Color(0x16324F);
    public static final Color NAVY_DEEP = new Color(0x10253D);
    public static final Color BLUE = new Color(0x2563EB);
    public static final Color BLUE_HOVER = new Color(0x1D4ED8);
    public static final Color BLUE_PRESSED = new Color(0x1E40AF);
    public static final Color TEAL = new Color(0x0D9488);
    public static final Color PAGE = new Color(0xF4F7FB);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x172B4D);
    public static final Color TEXT_MUTED = new Color(0x64748B);
    public static final Color BORDER = new Color(0xE2E8F0);
    /** Input outlines: at least 3:1 against white so fields are identifiable (WCAG 1.4.11). */
    public static final Color BORDER_STRONG = new Color(0x8494A9);
    public static final Color SUCCESS = new Color(0x15803D);
    public static final Color WARNING = new Color(0xB45309);
    public static final Color ERROR = new Color(0xB91C1C);

    public static final Color SUBTLE = new Color(0xF8FAFC);
    public static final Color HOVER = new Color(0xF1F5F9);
    public static final Color SELECTION = new Color(0xDBEAFE);
    public static final Color DISABLED_TEXT = new Color(0x94A3B8);
    public static final Color SIDEBAR_TEXT = new Color(0xCBD5E1);

    // ---- shape and spacing tokens ----
    public static final int RADIUS = 8;
    public static final int RADIUS_CONTROL = 6;
    public static final int SPACE_XS = 4;
    public static final int SPACE_S = 8;
    public static final int SPACE_M = 12;
    public static final int SPACE_L = 16;
    public static final int SPACE_XL = 24;

    // ---- type scale ----
    private static final String FAMILY = pickFamily();
    public static final Font BODY = new Font(FAMILY, Font.PLAIN, 13);
    public static final Font SMALL = new Font(FAMILY, Font.PLAIN, 12);
    public static final Font LABEL = new Font(FAMILY, Font.BOLD, 12);
    public static final Font BUTTON = new Font(FAMILY, Font.BOLD, 13);
    public static final Font SECTION = new Font(FAMILY, Font.BOLD, 15);
    public static final Font PAGE_TITLE = new Font(FAMILY, Font.BOLD, 22);
    public static final Font KPI = new Font(FAMILY, Font.BOLD, 24);

    private static boolean installed;

    private Theme() {
    }

    /** Inter if installed, otherwise the platform's UI sans-serif. */
    private static String pickFamily() {
        Set<String> available;
        try {
            available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        } catch (RuntimeException e) {
            return Font.SANS_SERIF;
        }
        for (String family : new String[]{"Inter", "Segoe UI", "Helvetica Neue", "Helvetica", "Arial"}) {
            if (available.contains(family)) {
                return family;
            }
        }
        return Font.SANS_SERIF;
    }

    /** Installs the look and feel once; safe to call repeatedly. Must run on the event thread. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            MetalLookAndFeel.setCurrentTheme(new BankMetalTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            return;     // keep whatever look and feel is active
        }
        String pkg = Theme.class.getPackageName() + ".";
        UIManager.put("ButtonUI", pkg + "FlatButtonUI");
        UIManager.put("ComboBoxUI", pkg + "FlatComboBoxUI");
        UIManager.put("ScrollBarUI", pkg + "FlatScrollBarUI");
        UIManager.put("TabbedPaneUI", pkg + "UnderlineTabbedPaneUI");
        UIManager.put("SplitPaneUI", pkg + "FlatSplitPaneUI");

        put("Panel.background", PAGE);
        put("OptionPane.background", PAGE);
        put("Label.foreground", TEXT);
        put("Viewport.background", CARD);
        put("ScrollPane.background", CARD);
        UIManager.put("ScrollPane.border", new BorderUIResource(BorderFactory.createLineBorder(BORDER)));
        UIManager.put("SplitPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        UIManager.put("SplitPaneDivider.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        UIManager.put("SplitPane.dividerSize", 12);
        put("SplitPane.background", PAGE);

        for (String field : new String[]{"TextField", "PasswordField", "FormattedTextField"}) {
            put(field + ".background", CARD);
            put(field + ".foreground", TEXT);
            put(field + ".inactiveForeground", DISABLED_TEXT);
            put(field + ".inactiveBackground", HOVER);
            put(field + ".selectionBackground", SELECTION);
            put(field + ".selectionForeground", TEXT);
            put(field + ".caretForeground", TEXT);
            UIManager.put(field + ".border", new FieldBorder());
            UIManager.put(field + ".margin", new InsetsUIResource(0, 0, 0, 0));
        }
        put("ComboBox.background", CARD);
        put("ComboBox.foreground", TEXT);
        put("ComboBox.selectionBackground", SELECTION);
        put("ComboBox.selectionForeground", TEXT);
        put("ComboBox.disabledBackground", HOVER);
        put("ComboBox.disabledForeground", DISABLED_TEXT);
        put("List.selectionBackground", SELECTION);
        put("List.selectionForeground", TEXT);

        put("Table.background", CARD);
        put("Table.foreground", TEXT);
        put("Table.gridColor", HOVER);
        put("Table.selectionBackground", SELECTION);
        put("Table.selectionForeground", TEXT);
        put("Table.focusCellHighlightBorder", BORDER);
        UIManager.put("Table.focusCellHighlightBorder", new BorderUIResource(BorderFactory.createEmptyBorder()));
        put("TableHeader.background", SUBTLE);
        put("TableHeader.foreground", TEXT_MUTED);

        put("RadioButton.background", PAGE);
        put("RadioButton.foreground", TEXT);
        put("CheckBox.foreground", TEXT);
        put("RadioButton.focus", BLUE);
        put("ToolTip.background", NAVY_DEEP);
        put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", new BorderUIResource(BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        UIManager.put("OptionPane.errorIcon", new StatusIcon(StatusIcon.Kind.ERROR));
        UIManager.put("OptionPane.warningIcon", new StatusIcon(StatusIcon.Kind.WARNING));
        UIManager.put("OptionPane.informationIcon", new StatusIcon(StatusIcon.Kind.INFO));
        UIManager.put("OptionPane.questionIcon", new StatusIcon(StatusIcon.Kind.QUESTION));
        put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.buttonPadding", 8);
        UIManager.put("OptionPane.sameSizeButtons", Boolean.TRUE);

        // Field outlines change on focus; repaint the old and new focus owner so the ring follows.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", e -> {
            for (Object c : new Object[]{e.getOldValue(), e.getNewValue()}) {
                if (c instanceof JComponent jc) {
                    jc.repaint();
                    if (jc.getParent() instanceof JComponent parent) {
                        parent.repaint();   // combo box editors and similar composites
                    }
                }
            }
        });
    }

    private static void put(String key, Color colour) {
        UIManager.put(key, new ColorUIResource(colour));
    }

    /** Graphics with anti-aliasing for shapes and text. Caller disposes. */
    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** A symbol if the UI font can draw it, otherwise a plain-text fallback. */
    public static String symbol(String wanted, String fallback) {
        return BODY.canDisplayUpTo(wanted) == -1 ? wanted : fallback;
    }

    /** The nearest component that actually paints its background (what shows behind c). */
    public static Component opaqueAncestor(Component c) {
        Component p = c;
        while (p != null && !p.isOpaque() && !(p instanceof Card) && p.getParent() != null) {
            p = p.getParent();
        }
        return p == null ? c : p;
    }

    /** Padding used inside cards and pages. */
    public static Border padding(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    /** Metal theme carrying the palette and type scale into components the app does not style itself. */
    private static final class BankMetalTheme extends DefaultMetalTheme {
        private final FontUIResource body = new FontUIResource(BODY);
        private final FontUIResource small = new FontUIResource(SMALL);

        @Override
        public String getName() {
            return "Bank";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return new ColorUIResource(BLUE);
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return new ColorUIResource(SELECTION);
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return new ColorUIResource(SELECTION);
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return new ColorUIResource(BORDER_STRONG);
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return new ColorUIResource(BORDER);
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return new ColorUIResource(PAGE);
        }

        @Override
        public ColorUIResource getSystemTextColor() {
            return new ColorUIResource(TEXT);
        }

        @Override
        public ColorUIResource getUserTextColor() {
            return new ColorUIResource(TEXT);
        }

        @Override
        public ColorUIResource getControlTextColor() {
            return new ColorUIResource(TEXT);
        }

        @Override
        public FontUIResource getControlTextFont() {
            return body;
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return body;
        }

        @Override
        public FontUIResource getUserTextFont() {
            return body;
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return body;
        }

        @Override
        public FontUIResource getWindowTitleFont() {
            return body;
        }

        @Override
        public FontUIResource getSubTextFont() {
            return small;
        }
    }

    /** Rounded input outline: 1px slate, 2px blue while the field has keyboard focus. */
    public static final class FieldBorder extends AbstractBorder implements UIResource {
        private final Insets padding;

        public FieldBorder() {
            this(new Insets(7, 10, 7, 10));
        }

        public FieldBorder(Insets padding) {
            this.padding = padding;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = smooth(g);
            boolean focused = c.hasFocus() && c.isEnabled();
            // The field paints a square background; cover its corners with the parent's colour.
            Component parent = c.getParent();
            if (parent != null) {
                parent = opaqueAncestor(parent);
                java.awt.geom.Area corners = new java.awt.geom.Area(new java.awt.Rectangle(x, y, width, height));
                corners.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Float(x, y, width, height,
                        RADIUS_CONTROL * 2, RADIUS_CONTROL * 2)));
                g2.setColor(parent.getBackground());
                g2.fill(corners);
            }
            g2.setColor(focused ? BLUE : c.isEnabled() ? BORDER_STRONG : BORDER);
            float stroke = focused ? 2f : 1f;
            float inset = stroke / 2f;
            g2.setStroke(new BasicStroke(stroke));
            g2.draw(new java.awt.geom.RoundRectangle2D.Float(x + inset, y + inset, width - stroke, height - stroke,
                    RADIUS_CONTROL * 2, RADIUS_CONTROL * 2));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(padding.top, padding.left, padding.bottom, padding.right);
            return insets;
        }
    }
}
