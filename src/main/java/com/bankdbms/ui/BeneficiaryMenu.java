package com.bankdbms.ui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.Account;
import com.bankdbms.model.Beneficiary;

import java.util.ArrayList;
import java.util.List;

/** Beneficiary screens (FR-20 to FR-24). */
public class BeneficiaryMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public BeneficiaryMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void show() {
        new Menu("Beneficiaries", io)
                .add("Add beneficiary", this::add)
                .add("List a customer's beneficiaries", () -> print(app.beneficiaries.listForCustomer(io.integer("Customer id"))))
                .add("Rename beneficiary", () -> {
                    Beneficiary b = app.beneficiaries.rename(io.integer("Beneficiary id"), io.required("New nickname"));
                    io.success("Renamed to '" + b.nickname() + "'.");
                })
                .add("Deactivate beneficiary", () -> {
                    Beneficiary b = app.beneficiaries.deactivate(io.integer("Beneficiary id"));
                    io.success("'" + b.nickname() + "' deactivated.");
                })
                .add("Reactivate beneficiary", () -> {
                    Beneficiary b = app.beneficiaries.reactivate(io.integer("Beneficiary id"));
                    io.success("'" + b.nickname() + "' reactivated.");
                })
                .add("Search beneficiaries", () -> print(app.beneficiaries.search(io.required("Nickname, account or customer contains"))))
                .run();
    }

    private void add() {
        Beneficiary b = app.beneficiaries.add(io.integer("Customer id (owner)"), io.required("Payee account number"),
                io.required("Nickname"));
        io.success("Beneficiary added with id " + b.beneficiaryId() + ".");
    }

    private void print(List<Beneficiary> list) {
        List<List<Object>> rows = new ArrayList<>();
        for (Beneficiary b : list) {
            Account payee = app.accounts.get(b.beneficiaryAccountId());
            String payeeName = app.customers.holdersOf(payee.accountId()).get(0).fullName();
            rows.add(List.of(b.beneficiaryId(), b.customerId(), b.nickname(), payee.accountNumber(), payeeName,
                    b.status().name()));
        }
        TablePrinter.print(io, List.of("id", "owner_customer", "nickname", "payee_account", "payee_name", "status"), rows);
    }
}
