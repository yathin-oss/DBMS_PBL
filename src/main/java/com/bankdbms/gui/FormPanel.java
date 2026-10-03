package com.bankdbms.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** A two-column label/field form. */
public class FormPanel extends JPanel {

    private int row;

    public FormPanel() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    public FormPanel add(String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.LINE_END;
        add(new JLabel(label), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.weightx = 1;
        add(field, c);
        row++;
        return this;
    }

    public FormPanel note(String text) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(2, 4, 6, 4);
        c.anchor = GridBagConstraints.LINE_START;
        JLabel label = new JLabel("<html><i>" + text + "</i></html>");
        add(label, c);
        return this;
    }
}
