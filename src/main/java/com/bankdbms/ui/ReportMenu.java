package com.bankdbms.ui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.Account;
import com.bankdbms.report.AccountStatement;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.report.StatementLine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Statements, the seven required reports, optional reports and the ledger integrity check. */
public class ReportMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public ReportMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void show() {
        new Menu("Reports", io)
                .add("Account statement", this::statement)
                .add("Account balances", () -> TablePrinter.print(io, app.reports.accountBalances()))
                .add("Transaction audit", () -> {
                    LocalDate[] r = range();
                    TablePrinter.print(io, app.reports.transactionAudit(r[0], r[1]));
                })
                .add("Transfers", () -> {
                    LocalDate[] r = range();
                    TablePrinter.print(io, app.reports.transfers(r[0], r[1]));
                })
                .add("Loan outstanding", () -> TablePrinter.print(io, app.reports.loanOutstanding()))
                .add("Loan repayments", () -> TablePrinter.print(io, app.reports.loanRepayments(null)))
                .add("Branch summary", () -> TablePrinter.print(io, app.reports.branchSummary()))
                .add("Trial balance (bank's books)", () -> TablePrinter.print(io, app.reports.trialBalance()))
                .add("Charge summary", () -> TablePrinter.print(io, app.reports.chargeSummary()))
                .add("Daily transaction summary", () -> {
                    LocalDate[] r = range();
                    TablePrinter.print(io, app.reports.dailySummary(r[0], r[1]));
                })
                .add("Ledger integrity check (invariants I1-I11)", this::integrity)
                .run();
    }

    private LocalDate[] range() {
        LocalDate today = LocalDate.now();
        LocalDate from = io.date("From", today.withDayOfMonth(1));
        LocalDate to = io.date("To", today);
        return new LocalDate[]{from, to};
    }

    private void statement() {
        Account a = AccountMenu.readAccount(app, io, "Account");
        LocalDate[] r = range();
        AccountStatement s = app.reports.statement(a.accountId(), r[0], r[1]);
        io.println();
        io.println("ACCOUNT STATEMENT  " + s.accountNumber() + "  (" + s.productName() + ", " + s.branchName() + ")");
        io.println("Holder(s): " + s.holders() + "   Status: " + s.accountStatus());
        io.println("Period: " + s.fromDate() + " to " + s.toDate() + "   Opening balance: "
                + TablePrinter.format(s.openingBalance()));
        List<List<Object>> rows = new ArrayList<>();
        for (StatementLine l : s.lines()) {
            rows.add(Arrays.asList(l.postedAt(), l.referenceNo(), l.transactionType(), l.narration(), l.debit(),
                    l.credit(), l.balanceAfter(), "REVERSED".equals(l.transactionStatus()) ? "reversed" : ""));
        }
        TablePrinter.print(io, List.of("date", "reference", "type", "narration", "debit", "credit", "balance", "note"), rows);
        io.println("Total debits " + TablePrinter.format(s.totalDebits()) + " | total credits "
                + TablePrinter.format(s.totalCredits()) + " | closing balance " + TablePrinter.format(s.closingBalance())
                + (s.isConsistent() ? "  [opening + credits - debits = closing]" : "  [INCONSISTENT]"));
    }

    private void integrity() {
        List<List<Object>> rows = new ArrayList<>();
        boolean allPassed = true;
        for (InvariantResult r : app.reports.verifyLedger()) {
            rows.add(List.of(r.invariant(), r.violations(), r.passed() ? "PASS" : "FAIL"));
            allPassed &= r.passed();
        }
        TablePrinter.print(io, List.of("invariant", "violations", "result"), rows);
        io.println(allPassed ? "  All ledger invariants hold." : "  WARNING: integrity violations found.");
    }
}
