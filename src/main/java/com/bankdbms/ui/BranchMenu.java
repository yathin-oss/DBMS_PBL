package com.bankdbms.ui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.AccountType;
import com.bankdbms.model.Branch;

import java.util.ArrayList;
import java.util.List;

/** Branch and product (account type) screens (FR-06, FR-07, FR-10). */
public class BranchMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public BranchMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void show() {
        new Menu("Branches and Products", io)
                .add("List branches", () -> printBranches(app.branches.list()))
                .add("Create branch", this::create)
                .add("Update branch contact details", this::update)
                .add("Search branches", () -> printBranches(app.branches.search(io.required("Name, city or IFSC contains"))))
                .add("List account types (products)", this::listProducts)
                .add("Create account type (product)", this::createProduct)
                .run();
    }

    private void create() {
        Branch b = app.branches.create(io.required("IFSC code"), io.required("Branch name"), io.required("Address"),
                io.required("City"), io.required("Phone"));
        io.success("Branch created with id " + b.branchId() + ".");
    }

    private void update() {
        Branch b = app.branches.get(io.integer("Branch id"));
        Branch updated = app.branches.updateContact(b.branchId(), io.required("Address [" + b.address() + "]"),
                io.required("City [" + b.city() + "]"), io.required("Phone [" + b.phone() + "]"));
        io.success("Branch " + updated.branchName() + " updated.");
    }

    private void listProducts() {
        List<List<Object>> rows = new ArrayList<>();
        for (AccountType t : app.accountTypes.listAll()) {
            rows.add(List.of(t.accountTypeId(), t.typeCode(), t.typeName(), t.category().name(), t.normalSide().name(),
                    t.minOpeningBalance(), t.minBalance(), t.maxHolders(), t.interestRate()));
        }
        TablePrinter.print(io, List.of("id", "code", "name", "category", "normal_side", "min_opening", "min_balance",
                "max_holders", "rate_%"), rows);
    }

    private void createProduct() {
        AccountType t = app.accountTypes.createProduct(io.required("Type code (A-Z, _)"), io.required("Type name"),
                io.money("Minimum opening balance"), io.money("Minimum balance"), io.integer("Maximum holders (1-4)"),
                io.money("Interest rate % (informational)"), io.line("Description"));
        io.success("Account type " + t.typeCode() + " created with id " + t.accountTypeId() + ".");
    }

    private void printBranches(List<Branch> branches) {
        List<List<Object>> rows = new ArrayList<>();
        for (Branch b : branches) {
            rows.add(List.of(b.branchId(), b.ifscCode(), b.branchName(), b.city(), b.phone()));
        }
        TablePrinter.print(io, List.of("id", "ifsc", "name", "city", "phone"), rows);
    }
}
