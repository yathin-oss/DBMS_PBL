package com.bankdbms.report;

import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Account;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.Customer;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.model.LoanType;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a known history on fixed past dates, then checks that every report is derived from it.
 */
class ReportServiceTest extends IntegrationTestBase {

    private static final LocalDate D1 = LocalDate.of(2026, 5, 4);
    private static final LocalDate D2 = LocalDate.of(2026, 5, 12);
    private static final LocalDate D3 = LocalDate.of(2026, 6, 2);

    private Customer owner;
    private Account savings;
    private Account current;
    private Account payee;
    private PostedTransaction transfer;
    private PostedTransaction beneficiaryTransfer;
    private LoanPosition loan;

    @BeforeAll
    void buildHistory() {
        clock.set(D1.atTime(10, 0));
        owner = newCustomer();
        savings = newAccount(owner, SAVINGS, "10000.00");                 // D1 opening deposit
        current = newAccount(owner, CURRENT, "20000.00");                 // D1
        payee = newAccount(SAVINGS, "1000.00");                           // D1
        clock.set(D2.atTime(11, 30));
        app.cash.deposit(savings.accountId(), money("2500.00"), "Cheque clearing");      // D2
        app.cash.withdraw(savings.accountId(), money("1500.00"), "ATM");                 // D2
        transfer = app.transfers.transfer(current.accountId(), savings.accountId(), money("4000.00"), null, "Own");
        Beneficiary b = app.beneficiaries.add(owner.customerId(), payee.accountNumber(), "Supplier");
        beneficiaryTransfer = app.transfers.transferToBeneficiary(current.accountId(), b.beneficiaryId(),
                money("3000.00"), "Invoice 17");
        clock.set(D3.atTime(9, 15));
        app.charges.apply(savings.accountId(), 1, null);                                 // D3: -25.00
        loan = app.loans.disburse(owner.customerId(), current.accountId(), LoanType.VEHICLE, money("60000.00"),
                money("9"), 24);
        app.loans.repayNextInstallment(loan.loanId(), loan.emiAmount());
        clock.set(LocalDateTime.now());
    }

    @Test
    @DisplayName("TC-RPT-01 statement: opening balance, period lines with running balance, totals, closing")
    void statement() {
        AccountStatement s = app.reports.statement(savings.accountId(), D2, D3);
        assertEquals(money("10000.00"), s.openingBalance());              // balance before D2
        assertEquals(4, s.lines().size());                                // deposit, withdrawal, transfer in, charge
        assertEquals(money("4000.00"), s.lines().get(2).credit());
        assertEquals(money("1525.00"), s.totalDebits());                  // 1500 + 25
        assertEquals(money("6500.00"), s.totalCredits());                 // 2500 + 4000
        assertEquals(money("14975.00"), s.closingBalance());
        assertEquals(balance(savings.accountId()), s.closingBalance());
        assertTrue(s.isConsistent());
        assertTrue(s.holders().contains(owner.firstName()));
    }

    @Test
    @DisplayName("TC-RPT-02 statement for a period without activity: opening = closing, no lines")
    void emptyStatement() {
        AccountStatement s = app.reports.statement(savings.accountId(), D1.plusDays(1), D1.plusDays(3));
        assertEquals(0, s.lines().size());
        assertEquals(money("10000.00"), s.openingBalance());
        assertEquals(s.openingBalance(), s.closingBalance());
    }

    @Test
    @DisplayName("TC-RPT-03 statement input validation and unknown account")
    void statementValidation() {
        assertThrows(ValidationException.class, () -> app.reports.statement(savings.accountId(), D3, D2));
        assertThrows(NotFoundException.class, () -> app.reports.statement(999_999, D1, D3));
    }

    @Test
    @DisplayName("TC-RPT-04 account balances report matches stored balances and holder counts")
    void accountBalances() {
        ReportTable t = app.reports.accountBalances();
        int row = findRow(t, "account_number", savings.accountNumber());
        assertEquals(balance(savings.accountId()), t.value(row, "balance"));
        assertEquals(1L, ((Number) t.value(row, "holder_count")).longValue());
    }

    @Test
    @DisplayName("TC-RPT-05 transaction audit: every transaction balanced, reversal links shown")
    void transactionAudit() {
        ReportTable t = app.reports.transactionAudit(D1, D3);
        assertTrue(t.size() >= 9);
        for (int i = 0; i < t.size(); i++) {
            assertEquals(0, ((BigDecimal) t.value(i, "total_debit")).compareTo((BigDecimal) t.value(i, "total_credit")));
        }
        ReportTable detail = app.reports.transactionDetail(transfer.transaction().referenceNo());
        assertEquals(2, detail.size());
        assertThrows(NotFoundException.class, () -> app.reports.transactionDetail("TX20990101AAAAAA"));
    }

    @Test
    @DisplayName("TC-RPT-06 transfer report derives source, destination and kind from the ledger")
    void transferReport() {
        ReportTable t = app.reports.transfers(D2, D2);
        int own = findRow(t, "reference_no", transfer.transaction().referenceNo());
        assertEquals(current.accountNumber(), t.value(own, "from_account"));
        assertEquals(savings.accountNumber(), t.value(own, "to_account"));
        assertEquals("OWN_ACCOUNT", t.value(own, "transfer_kind"));
        int third = findRow(t, "reference_no", beneficiaryTransfer.transaction().referenceNo());
        assertEquals("BENEFICIARY", t.value(third, "transfer_kind"));
        assertEquals("Supplier", t.value(third, "beneficiary_nickname"));
    }

    @Test
    @DisplayName("TC-RPT-07 loan outstanding and loan repayment reports")
    void loanReports() {
        ReportTable outstanding = app.reports.loanOutstanding();
        int row = findRow(outstanding, "loan_number", loan.loanNumber());
        LoanPosition now = app.loans.position(loan.loanId());
        assertEquals(now.outstandingPrincipal(), outstanding.value(row, "outstanding_principal"));
        assertEquals(1L, ((Number) outstanding.value(row, "installments_paid")).longValue());
        ReportTable repayments = app.reports.loanRepayments(loan.loanId());
        assertEquals(1, repayments.size());
        assertEquals(loan.emiAmount(), repayments.value(0, "amount_paid"));
        assertEquals("ON_TIME", repayments.value(0, "timeliness"));
    }

    @Test
    @DisplayName("TC-RPT-08 branch summary: account count and deposits equal the sum of the branch's balances")
    void branchSummary() {
        ReportTable t = app.reports.branchSummary();
        int row = findRow(t, "branch_name", "Test Branch");
        BigDecimal expected = balance(savings.accountId()).add(balance(current.accountId())).add(balance(payee.accountId()));
        assertEquals(3L, ((Number) t.value(row, "total_accounts")).longValue());
        assertEquals(0, expected.compareTo((BigDecimal) t.value(row, "total_deposits")));
        assertEquals(1L, ((Number) t.value(row, "active_loans")).longValue());
    }

    @Test
    @DisplayName("TC-RPT-09 trial balance: total debit balances equal total credit balances")
    void trialBalance() {
        ReportTable t = app.reports.trialBalance();
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (int i = 0; i < t.size(); i++) {
            debit = debit.add((BigDecimal) t.value(i, "debit_balance"));
            credit = credit.add((BigDecimal) t.value(i, "credit_balance"));
        }
        assertEquals(0, debit.compareTo(credit));
        assertEquals(6, t.size());
    }

    @Test
    @DisplayName("TC-RPT-10 charge summary and daily summary are derived from posted transactions")
    void optionalReports() {
        ReportTable charges = app.reports.chargeSummary();
        int sms = findRow(charges, "charge_code", "SMS_ALERT_FEE");
        assertEquals(1L, ((Number) charges.value(sms, "charges_applied")).longValue());
        ReportTable daily = app.reports.dailySummary(D2, D2);
        assertTrue(daily.rows().stream().anyMatch(r -> "TRANSFER".equals(r.get(1))
                && ((Number) r.get(2)).longValue() == 2));
    }

    private static int findRow(ReportTable t, String column, Object value) {
        for (int i = 0; i < t.size(); i++) {
            if (value.equals(t.value(i, column))) {
                return i;
            }
        }
        throw new AssertionError("No row with " + column + " = " + value + " in " + t.title());
    }
}
