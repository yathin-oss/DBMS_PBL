package com.bankdbms.model;

import java.util.EnumSet;
import java.util.Set;

public enum TransactionType {
    DEPOSIT, WITHDRAWAL, TRANSFER, CHARGE, LOAN_DISBURSEMENT, LOAN_REPAYMENT, REVERSAL, CAPITAL_INJECTION;

    /** BR-19: only these may be corrected by reversal (loan corrections are out of scope). */
    public static final Set<TransactionType> REVERSIBLE = EnumSet.of(DEPOSIT, WITHDRAWAL, TRANSFER, CHARGE);
}
