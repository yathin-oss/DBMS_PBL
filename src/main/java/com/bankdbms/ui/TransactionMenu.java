package com.bankdbms.ui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.Account;
import com.bankdbms.model.ChargeType;
import com.bankdbms.model.PostedTransaction;

import java.util.ArrayList;
import java.util.List;

/** Money movement screens: deposit, withdrawal, transfer, charge, reversal, lookup (FR-15 to FR-19, FR-25). */
public class TransactionMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public TransactionMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void show() {
        new Menu("Transactions", io)
                .add("Deposit", this::deposit)
                .add("Withdraw", this::withdraw)
                .add("Transfer to own account", this::ownTransfer)
                .add("Transfer to beneficiary", this::beneficiaryTransfer)
                .add("Apply service charge", this::charge)
                .add("Reverse a transaction", this::reverse)
                .add("Look up transaction by reference", () -> TablePrinter.print(io,
                        app.reports.transactionDetail(io.required("Reference (TX...)"))))
                .run();
    }

    private void deposit() {
        Account a = AccountMenu.readAccount(app, io, "Account");
        report(app.cash.deposit(a.accountId(), io.money("Amount"), io.line("Narration")));
    }

    private void withdraw() {
        Account a = AccountMenu.readAccount(app, io, "Account");
        report(app.cash.withdraw(a.accountId(), io.money("Amount"), io.line("Narration")));
    }

    private void ownTransfer() {
        Account from = AccountMenu.readAccount(app, io, "From account");
        Account to = AccountMenu.readAccount(app, io, "To account");
        report(app.transfers.transfer(from.accountId(), to.accountId(), io.money("Amount"), null, io.line("Remarks")));
    }

    private void beneficiaryTransfer() {
        Account from = AccountMenu.readAccount(app, io, "From account");
        report(app.transfers.transferToBeneficiary(from.accountId(), io.integer("Beneficiary id"), io.money("Amount"),
                io.line("Remarks")));
    }

    private void charge() {
        Account a = AccountMenu.readAccount(app, io, "Account");
        List<List<Object>> rows = new ArrayList<>();
        for (ChargeType t : app.charges.chargeTypes()) {
            rows.add(List.of(t.chargeTypeId(), t.chargeCode(), t.chargeName(), t.defaultAmount()));
        }
        TablePrinter.print(io, List.of("id", "code", "name", "default_amount"), rows);
        report(app.charges.apply(a.accountId(), io.integer("Charge type id"), io.optionalMoney("Amount")));
    }

    private void reverse() {
        String reference = io.required("Reference of the transaction to reverse");
        TablePrinter.print(io, app.reports.transactionDetail(reference));
        if (!io.confirm("Post a reversal of " + reference.toUpperCase() + "?")) {
            io.println("  Cancelled.");
            return;
        }
        report(app.reversals.reverse(reference, io.required("Reason")));
    }

    private void report(PostedTransaction t) {
        io.success(t.transaction().type() + " posted: reference " + t.transaction().referenceNo() + ", amount "
                + TablePrinter.format(t.amount()) + ".");
        TablePrinter.print(io, app.reports.transactionDetail(t.transaction().referenceNo()));
    }
}
