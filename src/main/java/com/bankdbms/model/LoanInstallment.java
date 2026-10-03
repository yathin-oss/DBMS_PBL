package com.bankdbms.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A schedule row with its derived status (PAID / OVERDUE / PENDING) from v_loan_installment_status. */
public record LoanInstallment(int loanId, int installmentNo, LocalDate dueDate, BigDecimal principalComponent,
                              BigDecimal interestComponent, BigDecimal installmentAmount, String status,
                              String repaymentReference, LocalDateTime paidAt, int daysOverdue) {
}
