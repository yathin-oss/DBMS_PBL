package com.bankdbms.gui;

/** An item in a drop-down: a key (id or account number) and the text shown to the operator. */
public record Choice(String key, String label) {

    public int id() {
        return Integer.parseInt(key);
    }

    @Override
    public String toString() {
        return label;
    }
}
