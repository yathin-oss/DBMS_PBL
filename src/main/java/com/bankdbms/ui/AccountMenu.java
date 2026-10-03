package com.bankdbms.ui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountHolder;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Customer;
import com.bankdbms.model.StatusChange;

import java.util.ArrayList;
import java.util.List;

/** Account screens: open, view, joint holders, lifecycle, close, search (FR-11 to FR-14). */
public class AccountMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public AccountMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void show() {
        new Menu("Accounts", io)
                .add("Open account", this::open)
                .add("View account (holders and status history)", this::view)
                .add("Add joint holder", this::addJoint)
                .add("Change status (ACTIVE / FROZEN / BLOCKED)", this::changeStatus)
                .add("Close account (pays out remaining balance)", this::close)
                .add("Search accounts", () -> TablePrinter.print(io, app.reports.searchAccounts(
                        io.required("Account number or holder name contains"))))
                .add("All account balances", () -> TablePrinter.print(io, app.reports.accountBalances()))
                .run();
    }

    /** Reads an account number and resolves it (shared by other menus). */
    static Account readAccount(AppContext app, ConsoleIO io, String prompt) {
        return app.accounts.getByNumber(io.required(prompt + " (12-digit account number)"));
    }

    private void open() {
        int customerId = io.integer("Primary holder customer id");
        int branchId = io.integer("Branch id");
        int typeId = io.integer("Account type id (1 SAVINGS, 2 CURRENT, 3 SALARY, or a new product)");
        Account a = app.accounts.open(customerId, branchId, typeId, io.money("Opening deposit"));
        io.success("Account " + a.accountNumber() + " opened. Balance " + TablePrinter.format(a.balance()) + ".");
    }

    private void view() {
        Account a = readAccount(app, io, "Account");
        io.println("  Account " + a.accountNumber() + " | " + app.accounts.productOf(a).typeName() + " | status "
                + a.status() + " | balance " + TablePrinter.format(a.balance()) + " | opened " + TablePrinter.format(a.openedAt()));
        List<List<Object>> holders = new ArrayList<>();
        for (AccountHolder h : app.accounts.holders(a.accountId())) {
            Customer c = app.customers.get(h.customerId());
            holders.add(List.of(h.role().name(), c.customerId(), c.fullName(), h.addedAt()));
        }
        TablePrinter.print(io, List.of("role", "customer_id", "name", "added_at"), holders);
        List<List<Object>> history = new ArrayList<>();
        for (StatusChange s : app.accounts.statusHistory(a.accountId())) {
            history.add(java.util.Arrays.asList(s.changedAt(), s.oldStatus() == null ? "-" : s.oldStatus().name(),
                    s.newStatus().name(), s.reason()));
        }
        TablePrinter.print(io, List.of("changed_at", "from", "to", "reason"), history);
    }

    private void addJoint() {
        Account a = readAccount(app, io, "Account");
        app.accounts.addJointHolder(a.accountId(), io.integer("Customer id of joint holder"));
        io.success("Joint holder added to " + a.accountNumber() + ".");
    }

    private void changeStatus() {
        Account a = readAccount(app, io, "Account");
        io.println("  Current status: " + a.status() + "; allowed: " + a.status().allowedTransitions());
        Account updated = app.accounts.changeStatus(a.accountId(), io.choice("New status", AccountStatus.class),
                io.required("Reason"));
        io.success("Account " + updated.accountNumber() + " is now " + updated.status() + ".");
    }

    private void close() {
        Account a = readAccount(app, io, "Account");
        io.println("  Balance to be paid out: " + TablePrinter.format(a.balance()));
        if (!io.confirm("Close account " + a.accountNumber() + "?")) {
            io.println("  Cancelled.");
            return;
        }
        Account closed = app.accounts.close(a.accountId(), io.required("Reason"));
        io.success("Account " + closed.accountNumber() + " closed at " + TablePrinter.format(closed.closedAt()) + ".");
    }
}
