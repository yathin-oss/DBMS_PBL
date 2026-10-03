package com.bankdbms.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * An account statement for a period (FR-31, BR-37), derived entirely from the ledger.
 * {@link #isConsistent()} checks opening + credits - debits = closing.
 */
public record AccountStatement(String accountNumber, String productName, String branchName, String holders,
                               String accountStatus, LocalDate fromDate, LocalDate toDate,
                               BigDecimal openingBalance, List<StatementLine> lines, BigDecimal totalDebits,
                               BigDecimal totalCredits, BigDecimal closingBalance) {

    public AccountStatement {
        lines = List.copyOf(lines);
    }

    public boolean isConsistent() {
        return openingBalance.add(totalCredits).subtract(totalDebits).compareTo(closingBalance) == 0;
    }
}
