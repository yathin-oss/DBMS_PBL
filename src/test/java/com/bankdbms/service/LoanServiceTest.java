package com.bankdbms.service;

import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.InsufficientFundsException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Customer;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.LoanInstallment;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.model.LoanType;
import com.bankdbms.report.ReportTable;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanServiceTest extends IntegrationTestBase {

    private Customer borrower;
    private Account account;

    private LoanPosition disburse(String principal, String rate, int tenure) {
        borrower = newCustomer();
        account = newAccount(borrower, SAVINGS, "50000.00");
        return app.loans.disburse(borrower.customerId(), account.accountId(), LoanType.PERSONAL, money(principal),
                money(rate), tenure);
    }

    @Test
    @DisplayName("TC-LOAN-01 disburse: account credited, LOAN_RECEIVABLE debited, full schedule generated")
    void disburseLoan() {
        BigDecimal receivableBefore = internalBalance(InternalAccount.LOAN_RECEIVABLE);
        LoanPosition p = disburse("100000.00", "12", 12);
        assertEquals(money("150000.00"), balance(account.accountId()));
        assertEquals(receivableBefore.add(money("100000.00")), internalBalance(InternalAccount.LOAN_RECEIVABLE));
        assertEquals(money("8884.88"), p.emiAmount());
        assertEquals(money("100000.00"), p.outstandingPrincipal());
        assertEquals("ACTIVE", p.loanStatus());
        assertTrue(p.loanNumber().matches("LN[0-9]{12}"));
        List<LoanInstallment> schedule = app.loans.schedule(p.loanId());
        assertEquals(12, schedule.size());
        assertEquals(clock.today().plusMonths(1), schedule.get(0).dueDate());
        schedule.forEach(i -> assertEquals("PENDING", i.status()));
    }

    @Test
    @DisplayName("TC-LOAN-02 invalid amount, rate or tenure is rejected")
    void invalidParameters() {
        Customer c = newCustomer();
        Account a = newAccount(c, SAVINGS, "5000.00");
        assertThrows(ValidationException.class,
                () -> app.loans.disburse(c.customerId(), a.accountId(), LoanType.HOME, money("0"), money("9"), 12));
        assertThrows(ValidationException.class,
                () -> app.loans.disburse(c.customerId(), a.accountId(), LoanType.HOME, money("-1000"), money("9"), 12));
        assertThrows(ValidationException.class,
                () -> app.loans.disburse(c.customerId(), a.accountId(), LoanType.HOME, money("1000"), money("36.01"), 12));
        assertThrows(ValidationException.class,
                () -> app.loans.disburse(c.customerId(), a.accountId(), LoanType.HOME, money("1000"), money("0"), 12));
        assertThrows(ValidationException.class,
                () -> app.loans.disburse(c.customerId(), a.accountId(), LoanType.HOME, money("1000"), money("9"), 361));
        assertThrows(ValidationException.class,
                () -> app.loans.disburse(c.customerId(), a.accountId(), null, money("1000"), money("9"), 12));
    }

    @Test
    @DisplayName("TC-LOAN-03 borrower must hold the account, which must be ACTIVE")
    void borrowerRules() {
        Customer c = newCustomer();
        Account someoneElses = newAccount(SAVINGS, "5000.00");
        assertThrows(BusinessRuleException.class, () -> app.loans.disburse(c.customerId(), someoneElses.accountId(),
                LoanType.PERSONAL, money("1000"), money("10"), 6));
        Account own = newAccount(c, SAVINGS, "5000.00");
        app.accounts.changeStatus(own.accountId(), AccountStatus.FROZEN, "Hold");
        assertThrows(AccountStateException.class, () -> app.loans.disburse(c.customerId(), own.accountId(),
                LoanType.PERSONAL, money("1000"), money("10"), 6));
    }

    @Test
    @DisplayName("TC-LOAN-04 repayment: principal reduces outstanding, interest credited to INTEREST_INCOME")
    void repayOne() {
        LoanPosition p = disburse("100000.00", "12", 12);
        BigDecimal interestBefore = internalBalance(InternalAccount.INTEREST_INCOME);
        LoanService.Repayment r = app.loans.repayNextInstallment(p.loanId(), money("8884.88"));
        assertEquals(1, r.installmentNo());
        assertEquals(3, r.posted().entries().size());
        LoanPosition after = app.loans.position(p.loanId());
        assertEquals(money("92115.12"), after.outstandingPrincipal());         // 100000 - 7884.88
        assertEquals(1, after.installmentsPaid());
        assertEquals(interestBefore.add(money("1000.00")), internalBalance(InternalAccount.INTEREST_INCOME));
        assertEquals("PAID", app.loans.schedule(p.loanId()).get(0).status());
    }

    @Test
    @DisplayName("TC-LOAN-05 invalid repayment amount is rejected and nothing changes")
    void wrongAmount() {
        LoanPosition p = disburse("100000.00", "12", 12);
        BigDecimal before = balance(account.accountId());
        ValidationException e = assertThrows(ValidationException.class,
                () -> app.loans.repayNextInstallment(p.loanId(), money("8000.00")));
        assertTrue(e.getMessage().contains("8,884.88"));
        assertThrows(ValidationException.class, () -> app.loans.repayNextInstallment(p.loanId(), money("0")));
        assertEquals(before, balance(account.accountId()));
        assertEquals(0, app.loans.position(p.loanId()).installmentsPaid());
    }

    @Test
    @DisplayName("TC-LOAN-06 repayment needs sufficient balance above the minimum")
    void insufficientForEmi() {
        Customer c = newCustomer();
        Account a = newAccount(c, SAVINGS, "1000.00");
        LoanPosition p = app.loans.disburse(c.customerId(), a.accountId(), LoanType.PERSONAL, money("12000.00"),
                money("12"), 12);
        app.cash.withdraw(a.accountId(), money("12000.00"), null);   // leaves 1000.00, minimum 500.00
        assertThrows(InsufficientFundsException.class, () -> app.loans.repayNextInstallment(p.loanId(), p.emiAmount()));
    }

    @Test
    @DisplayName("TC-LOAN-07 full repayment closes the loan; outstanding becomes zero; further repayment rejected")
    void fullRepayment() {
        LoanPosition p = disburse("30000.00", "10.5", 3);
        for (LoanInstallment i : app.loans.schedule(p.loanId())) {
            app.loans.repayNextInstallment(p.loanId(), i.installmentAmount());
        }
        LoanPosition closed = app.loans.position(p.loanId());
        assertEquals("CLOSED", closed.loanStatus());
        assertEquals(money("0.00"), closed.outstandingPrincipal());
        assertNull(closed.nextDueDate());
        assertThrows(BusinessRuleException.class, () -> app.loans.repayNextInstallment(p.loanId(), money("1.00")));
        ReportTable repayments = app.reports.loanRepayments(p.loanId());
        assertEquals(3, repayments.size());
    }

    @Test
    @DisplayName("TC-LOAN-08 overdue instalments are derived from due dates; a late payment is reported as LATE")
    void overdueDerivation() {
        LocalDateTime realNow = clock.now();
        clock.set(realNow.minusMonths(4).minusDays(3));               // disburse about 4 months ago
        LoanPosition p = disburse("60000.00", "12", 6);
        clock.set(realNow);
        LoanPosition position = app.loans.position(p.loanId());
        assertEquals(4, position.installmentsOverdue());
        List<LoanInstallment> schedule = app.loans.schedule(p.loanId());
        assertEquals("OVERDUE", schedule.get(0).status());
        assertTrue(schedule.get(0).daysOverdue() > 90);
        assertEquals("PENDING", schedule.get(5).status());
        app.loans.repayNextInstallment(p.loanId(), schedule.get(0).installmentAmount());
        ReportTable report = app.reports.loanRepayments(p.loanId());
        assertEquals("LATE", report.value(0, "timeliness"));
        assertEquals(3, app.loans.position(p.loanId()).installmentsOverdue());
    }

    @Test
    @DisplayName("TC-LOAN-09 an account repaying an unfinished loan cannot be closed")
    void cannotCloseWithLoan() {
        disburse("10000.00", "10", 6);
        assertThrows(BusinessRuleException.class, () -> app.accounts.close(account.accountId(), "Leaving"));
    }

    @Test
    @DisplayName("TC-LOAN-10 a joint holder may borrow on the joint account")
    void jointHolderBorrows() {
        Customer primary = newCustomer();
        Account joint = newAccount(primary, SAVINGS, "5000.00");
        Customer second = newCustomer();
        app.accounts.addJointHolder(joint.accountId(), second.customerId());
        LoanPosition p = app.loans.disburse(second.customerId(), joint.accountId(), LoanType.EDUCATION,
                money("20000.00"), money("9.5"), 10);
        assertEquals(second.customerId(), p.customerId());
    }
}
