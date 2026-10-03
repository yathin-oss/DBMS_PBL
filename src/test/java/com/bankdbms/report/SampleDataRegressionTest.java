package com.bankdbms.report;

import com.bankdbms.app.AppContext;
import com.bankdbms.ledger.PostingFaultInjector;
import com.bankdbms.model.Account;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.support.TestDatabase;
import com.bankdbms.util.BankClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Installs schema + reference data + sql/05_sample_data.sql exactly as an evaluator would, then checks it. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleDataRegressionTest {

    private AppContext app;

    @BeforeAll
    void install() {
        TestDatabase.rebuild("sql/05_sample_data.sql");
        app = new AppContext(TestDatabase.config(), BankClock.system(), PostingFaultInjector.NONE);
    }

    @Test
    @DisplayName("TC-REG-01 sample data loads and satisfies every ledger invariant (I1-I11)")
    void invariantsHold() {
        List<InvariantResult> results = app.reports.verifyLedger();
        assertEquals(13, results.size());
        results.forEach(r -> assertTrue(r.passed(), r.invariant()));
    }

    @Test
    @DisplayName("TC-REG-02 sample data tells the documented story (loans CLOSED / overdue / pending, lifecycle states)")
    void documentedStory() {
        Map<String, LoanPosition> loans = app.loans.allPositions().stream()
                .collect(Collectors.toMap(p -> p.loanType().name(), Function.identity()));
        assertEquals("CLOSED", loans.get("VEHICLE").loanStatus());
        assertEquals(new BigDecimal("0.00"), loans.get("VEHICLE").outstandingPrincipal());
        assertEquals(4, loans.get("PERSONAL").installmentsPaid());
        assertTrue(loans.get("PERSONAL").installmentsOverdue() >= 1);
        assertEquals(0, loans.get("HOME").installmentsPaid());
        ReportTable balances = app.reports.accountBalances();
        assertEquals(11, balances.size());
        List<Object> statuses = balances.rows().stream().map(r -> r.get(balances.columns().indexOf("status"))).toList();
        assertTrue(statuses.containsAll(List.of("ACTIVE", "FROZEN", "BLOCKED", "CLOSED")));
        assertTrue(app.reports.loanRepayments(null).rows().stream().anyMatch(r -> r.contains("LATE")));
    }

    @Test
    @DisplayName("TC-REG-03 every sample account statement reconciles (opening + credits - debits = closing)")
    void statementsReconcile() {
        ReportTable balances = app.reports.accountBalances();
        for (int i = 0; i < balances.size(); i++) {
            Account a = app.accounts.getByNumber((String) balances.value(i, "account_number"));
            AccountStatement s = app.reports.statement(a.accountId(), LocalDate.of(2026, 1, 1), LocalDate.now());
            assertTrue(s.isConsistent(), "statement of " + a.accountNumber());
            assertEquals(0, a.balance().compareTo(s.closingBalance()), "closing balance of " + a.accountNumber());
        }
    }

    @Test
    @DisplayName("TC-REG-04 the application keeps working on top of the sample data")
    void operationsStillWork() {
        Account ananya = app.accounts.getByNumber((String) app.reports.searchAccounts("Ananya").value(0, "account_number"));
        BigDecimal before = ananya.balance();
        app.cash.deposit(ananya.accountId(), new BigDecimal("100.00"), "Regression check");
        assertEquals(before.add(new BigDecimal("100.00")), app.accounts.get(ananya.accountId()).balance());
        app.reports.verifyLedger().forEach(r -> assertTrue(r.passed(), r.invariant()));
    }
}
