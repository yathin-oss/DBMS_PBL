package com.bankdbms.model;

/**
 * The bank's five internal accounts, created by {@code sql/04_reference_data.sql}.
 * Account numbers starting with 9 are reserved for them.
 */
public enum InternalAccount {
    CASH("900000000001"),
    LOAN_RECEIVABLE("900000000002"),
    FEE_INCOME("900000000003"),
    INTEREST_INCOME("900000000004"),
    CAPITAL("900000000005");

    private final String accountNumber;

    InternalAccount(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String accountNumber() {
        return accountNumber;
    }
}
