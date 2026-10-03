package com.bankdbms.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Derived loan summary from v_loan_position (business rules BR-35, BR-36). */
public record LoanPosition(int loanId, String loanNumber, LoanType loanType, int customerId, String borrower,
                           String accountNumber, BigDecimal principal, BigDecimal annualInterestRate,
                           int tenureMonths, LocalDate disbursedOn, BigDecimal emiAmount, int installmentsPaid,
                           int installmentsOverdue, BigDecimal totalInterest, BigDecimal principalRepaid,
                           BigDecimal interestPaid, BigDecimal outstandingPrincipal, LocalDate nextDueDate,
                           String loanStatus) {
}
