package com.bankdbms.model;

import java.math.BigDecimal;

/** Stored loan facts only; status, EMI and outstanding amount are derived (see LoanPosition). */
public record Loan(int loanId, String loanNumber, int accountId, int customerId, LoanType loanType,
                   BigDecimal principal, BigDecimal annualInterestRate, int tenureMonths, long disbursementTxnId) {
}
