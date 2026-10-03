package com.bankdbms.gui;

import com.bankdbms.exception.BankException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared Swing helpers: error boundary, buttons, input parsing, dialogs. */
public final class Gui {

    private static final Logger LOG = Logger.getLogger(Gui.class.getName());

    public static final Color ACCENT = new Color(0x1F4E79);
    public static final Color OK_GREEN = new Color(0x2E7D32);
    public static final Color ERROR_RED = new Color(0xC62828);

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

    public static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    public static JPanel toolbar(Component... components) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (Component c : components) {
            bar.add(c);
        }
        return bar;
    }

    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 4f));
        label.setForeground(ACCENT);
        label.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        return label;
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
