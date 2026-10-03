package com.bankdbms.report;

/** Result of one ledger/data-integrity check from sql/06_verification_queries.sql. */
public record InvariantResult(String invariant, long violations) {

    public boolean passed() {
        return violations == 0;
    }
}
