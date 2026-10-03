package com.bankdbms.model;

/** Side of a ledger entry; also the "normal side" of an account type (the side that increases it). */
public enum EntrySide {
    DEBIT, CREDIT;

    public EntrySide opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
