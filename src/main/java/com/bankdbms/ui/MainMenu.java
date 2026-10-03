package com.bankdbms.ui;

import com.bankdbms.app.AppContext;

/** Top-level console menu for the bank operator. */
public class MainMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public MainMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void run() {
        io.println("==============================================================");
        io.println(" Bank Account and Transaction Management System (operator console)");
        io.println("==============================================================");
        new Menu("Main Menu", io, "Exit")
                .add("Customers", () -> new CustomerMenu(app, io).show())
                .add("Branches and products", () -> new BranchMenu(app, io).show())
                .add("Accounts", () -> new AccountMenu(app, io).show())
                .add("Transactions (deposit, withdraw, transfer, charge, reverse)", () -> new TransactionMenu(app, io).show())
                .add("Beneficiaries", () -> new BeneficiaryMenu(app, io).show())
                .add("Loans", () -> new LoanMenu(app, io).show())
                .add("Statements and reports", () -> new ReportMenu(app, io).show())
                .run();
        io.println("Goodbye.");
    }
}
