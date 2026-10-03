package com.bankdbms.ui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.Account;
import com.bankdbms.model.LoanInstallment;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.model.LoanType;
import com.bankdbms.service.LoanService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Loan screens (FR-26 to FR-30). */
public class LoanMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public LoanMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void show() {
        new Menu("Loans", io)
                .add("Disburse loan", this::disburse)
                .add("View loan and schedule", this::view)
                .add("Repay next instalment", this::repay)
                .add("Loans of a customer", () -> printPositions(app.loans.positionsForCustomer(io.integer("Customer id"))))
                .add("Loan outstanding report", () -> TablePrinter.print(io, app.reports.loanOutstanding()))
                .add("Loan repayment report", () -> TablePrinter.print(io, app.reports.loanRepayments(null)))
                .run();
    }

    private void disburse() {
        int customerId = io.integer("Borrower customer id");
        Account a = AccountMenu.readAccount(app, io, "Disbursement / repayment account");
        LoanPosition p = app.loans.disburse(customerId, a.accountId(), io.choice("Loan type", LoanType.class),
                io.money("Principal"), io.money("Annual interest rate %"), io.integer("Tenure (months)"));
        io.success("Loan " + p.loanNumber() + " disbursed. EMI " + TablePrinter.format(p.emiAmount()) + " for "
                + p.tenureMonths() + " months; first due " + p.nextDueDate() + ".");
    }

    private void view() {
        LoanPosition p = app.loans.positionByNumber(io.required("Loan number (LN...)"));
        printPositions(List.of(p));
        List<List<Object>> rows = new ArrayList<>();
        for (LoanInstallment i : app.loans.schedule(p.loanId())) {
            rows.add(Arrays.asList(i.installmentNo(), i.dueDate(), i.principalComponent(), i.interestComponent(),
                    i.installmentAmount(), i.status(), i.repaymentReference(), i.daysOverdue()));
        }
        TablePrinter.print(io, List.of("no", "due_date", "principal", "interest", "amount", "status", "paid_ref",
                "days_overdue"), rows);
    }

    private void repay() {
        LoanPosition p = app.loans.positionByNumber(io.required("Loan number (LN...)"));
        LoanInstallment next = app.loans.schedule(p.loanId()).stream().filter(i -> !"PAID".equals(i.status()))
                .findFirst().orElse(null);
        if (next == null) {
            io.println("  Loan " + p.loanNumber() + " is fully repaid.");
            return;
        }
        io.println("  Next instalment " + next.installmentNo() + " due " + next.dueDate() + ": "
                + TablePrinter.format(next.installmentAmount()) + " (" + next.status() + ")");
        LoanService.Repayment r = app.loans.repayNextInstallment(p.loanId(), io.money("Amount to pay"));
        io.success("Instalment " + r.installmentNo() + " repaid, reference " + r.posted().transaction().referenceNo() + ".");
    }

    private void printPositions(List<LoanPosition> positions) {
        List<List<Object>> rows = new ArrayList<>();
        for (LoanPosition p : positions) {
            rows.add(Arrays.asList(p.loanNumber(), p.borrower(), p.loanType().name(), p.principal(),
                    p.annualInterestRate(), p.emiAmount(), p.installmentsPaid() + "/" + p.tenureMonths(),
                    p.installmentsOverdue(), p.outstandingPrincipal(), p.nextDueDate(), p.loanStatus()));
        }
        TablePrinter.print(io, List.of("loan", "borrower", "type", "principal", "rate_%", "emi", "paid", "overdue",
                "outstanding", "next_due", "status"), rows);
    }
}
