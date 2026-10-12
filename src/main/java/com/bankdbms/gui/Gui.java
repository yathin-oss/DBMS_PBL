package com.bankdbms.gui;

import com.bankdbms.exception.BankException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.util.Money;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared Swing helpers: error boundary, buttons, layout building blocks, input parsing, dialogs. */
public final class Gui {

    private static final Logger LOG = Logger.getLogger(Gui.class.getName());

    public static final Color ACCENT = Theme.NAVY;
    public static final Color OK_GREEN = Theme.SUCCESS;
    public static final Color ERROR_RED = Theme.ERROR;

    /** Tone of a status message; each has its own colour and a text prefix, so colour is not the only cue. */
    public enum Tone { NEUTRAL, SUCCESS, ERROR }

    private Gui() {
    }

    /**
     * Runs an operator action with a wait cursor. Banking errors are shown in a dialog with their
     * safe message; anything unexpected is logged and reported generically. Returns true on success.
     */
    public static boolean attempt(Component parent, Runnable action) {
        Window window = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        Component cursorTarget = window != null ? window : parent;
        if (cursorTarget != null) {
            cursorTarget.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        try {
            action.run();
            return true;
        } catch (BankException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Cannot complete the operation",
                    e instanceof ValidationException ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);
            return false;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error in GUI action", e);
            JOptionPane.showMessageDialog(parent, "An unexpected error occurred. Details were written to logs/bank-dbms.log.",
                    "Unexpected error", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            if (cursorTarget != null) {
                cursorTarget.setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /** Shows a form in an OK/Cancel dialog; returns true if the operator pressed OK. */
    public static boolean showForm(Component parent, String title, JComponent form) {
        return JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /** A secondary (outlined) button. */
    public static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** The main action of a screen or section (filled blue). */
    public static JButton primary(String text, Runnable action) {
        JButton b = button(text, action);
        b.putClientProperty(FlatButtonUI.VARIANT, FlatButtonUI.PRIMARY);
        return b;
    }

    /** An action that cannot be undone or takes something out of service (red outline). */
    public static JButton danger(String text, Runnable action) {
        JButton b = button(text, action);
        b.putClientProperty(FlatButtonUI.VARIANT, FlatButtonUI.DANGER);
        return b;
    }

    /** A row of controls that wraps onto a new line when the window is narrow. */
    public static JPanel toolbar(Component... components) {
        JPanel bar = new JPanel(new WrapLayout(FlowLayout.LEFT, Theme.SPACE_S, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, -Theme.SPACE_S, 0, 0));
        for (Component c : components) {
            bar.add(c);
        }
        return bar;
    }

    /** Page title on its own (kept for callers that only need the label). */
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.PAGE_TITLE);
        label.setForeground(Theme.TEXT);
        return label;
    }

    /** Applies the page background and outer spacing to a screen. */
    public static void page(JPanel screen) {
        screen.setBackground(Theme.PAGE);
        screen.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_XL - 4, Theme.SPACE_XL + 4, Theme.SPACE_XL, Theme.SPACE_XL + 4));
        if (screen.getLayout() instanceof BorderLayout layout) {
            layout.setVgap(Theme.SPACE_L);
        }
    }

    /** Page title, a one-line description and (below) the screen's toolbar. */
    public static JPanel pageHeader(String title, String subtitle, JComponent toolbar) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel t = heading(title);
        t.setAlignmentX(0f);
        header.add(t);
        if (subtitle != null) {
            JLabel sub = new JLabel(subtitle);
            sub.setFont(Theme.BODY);
            sub.setForeground(Theme.TEXT_MUTED);
            sub.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            sub.setAlignmentX(0f);
            header.add(sub);
        }
        if (toolbar != null) {
            header.add(Box.createVerticalStrut(Theme.SPACE_L));
            toolbar.setAlignmentX(0f);
            header.add(toolbar);
        }
        return header;
    }

    /** A titled card around a body (usually a table), with optional actions on the right of the title. */
    public static Card card(String title, JComponent body, JComponent... actions) {
        Card card = new Card(new BorderLayout(0, Theme.SPACE_M));
        if (title != null || actions.length > 0) {
            JPanel head = new JPanel(new BorderLayout(Theme.SPACE_M, 0));
            head.setOpaque(false);
            if (title != null) {
                JLabel t = new JLabel(title);
                t.setFont(Theme.SECTION);
                t.setForeground(Theme.TEXT);
                head.add(t, BorderLayout.WEST);
                body.getAccessibleContext().setAccessibleName(title);
                if (body instanceof JScrollPane sp && sp.getViewport().getView() instanceof JComponent view) {
                    view.getAccessibleContext().setAccessibleName(title);
                }
            }
            if (actions.length > 0) {
                JPanel bar = new JPanel(new WrapLayout(FlowLayout.RIGHT, Theme.SPACE_S, 0));
                bar.setOpaque(false);
                for (JComponent a : actions) {
                    bar.add(a);
                }
                head.add(bar, BorderLayout.CENTER);
            }
            card.add(head, BorderLayout.NORTH);
        }
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** A borderless scroll pane for a table inside a card. */
    public static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        pane.getViewport().setBackground(Theme.CARD);
        return pane;
    }

    /** A label for secondary information (captions, detail lines). */
    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.BODY);
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    /** Shows a status message in the label, styled by tone (a tinted strip with a text prefix). */
    public static void status(JLabel label, Tone tone, String text) {
        Color fg;
        Color bg;
        String prefix;
        switch (tone) {
            case SUCCESS -> {
                fg = Theme.SUCCESS;
                bg = new Color(0xF0FDF4);
                prefix = Theme.symbol("\u2713", "OK:") + "  ";
            }
            case ERROR -> {
                fg = Theme.ERROR;
                bg = new Color(0xFEF2F2);
                prefix = "!  ";
            }
            default -> {
                fg = Theme.TEXT;
                bg = Theme.SUBTLE;
                prefix = "";
            }
        }
        label.setOpaque(true);
        label.setBackground(bg);
        label.setForeground(fg);
        label.setFont(Theme.LABEL);
        label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(tone == Tone.NEUTRAL ? Theme.BORDER
                : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 70)), Theme.padding(Theme.SPACE_S, Theme.SPACE_M)));
        label.setText(prefix + text);
    }

    public static String text(JTextField field) {
        return field.getText().strip();
    }

    public static BigDecimal money(JTextField field, String name) {
        String value = text(field);
        if (value.isEmpty()) {
            throw new ValidationException(name + " is required.");
        }
        return Money.parse(value);
    }

    public static BigDecimal optionalMoney(JTextField field) {
        String value = text(field);
        return value.isEmpty() ? null : Money.parse(value);
    }

    public static int integer(JTextField field, String name) {
        String value = text(field);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(name + " must be a whole number.");
        }
    }

    public static LocalDate date(JTextField field, String name) {
        String value = text(field);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(name + " must be a date in the form yyyy-mm-dd.");
        }
    }

    /** The selected drop-down item, or a validation error naming the field. */
    public static Choice selected(JComboBox<Choice> combo, String name) {
        Object item = combo.getSelectedItem();
        if (!(item instanceof Choice choice)) {
            throw new ValidationException("Please select " + name + ".");
        }
        return choice;
    }
}
