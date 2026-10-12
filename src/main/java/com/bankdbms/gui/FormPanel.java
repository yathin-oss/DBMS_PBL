package com.bankdbms.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * A two-column label/field form. Fields share one column width so their edges line up; each label
 * is linked to its field for screen readers. Read-only values (JLabels) are shown in bold.
 */
public class FormPanel extends JPanel {

    private static final int MIN_FIELD_WIDTH = 300;

    private int row;

    public FormPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0));
    }

    public FormPanel add(String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(5, 0, 5, Theme.SPACE_L);
        c.anchor = GridBagConstraints.LINE_START;
        JLabel name = new JLabel(label);
        name.setFont(Theme.LABEL);
        name.setForeground(Theme.TEXT);
        name.setLabelFor(field);
        add(name, c);

        c.gridx = 1;
        c.insets = new Insets(5, 0, 5, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        if (field instanceof JLabel value) {
            value.setFont(Theme.BUTTON);
            value.setForeground(Theme.TEXT);
            c.fill = GridBagConstraints.NONE;
        } else if (field.getAccessibleContext().getAccessibleName() == null) {
            field.getAccessibleContext().setAccessibleName(label);
        }
        if (field.getPreferredSize().width < MIN_FIELD_WIDTH && c.fill == GridBagConstraints.HORIZONTAL) {
            c.ipadx = MIN_FIELD_WIDTH - field.getPreferredSize().width;
        }
        add(field, c);

        // Empty third column absorbs extra width so wide windows do not stretch the fields.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 2;
        filler.gridy = row;
        filler.weightx = 1;
        add(new JLabel(), filler);
        row++;
        return this;
    }

    /** A hint below the fields, shown as a tinted callout that wraps long text. */
    public FormPanel note(String text) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(Theme.SPACE_S, 0, Theme.SPACE_XS, 0);
        c.anchor = GridBagConstraints.LINE_START;
        JLabel label = new JLabel("<html><div style='width:420px'>" + text + "</div></html>");
        label.setFont(Theme.SMALL);
        label.setForeground(new Color(0x334155));
        JPanel callout = new JPanel(new BorderLayout());
        callout.setBackground(new Color(0xEFF6FF));
        callout.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.BLUE),
                Theme.padding(Theme.SPACE_S, Theme.SPACE_M)));
        callout.add(label, BorderLayout.CENTER);
        add(callout, c);
        return this;
    }
}
