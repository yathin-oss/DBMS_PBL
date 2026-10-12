package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.AccountType;
import com.bankdbms.model.Branch;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.List;

/** Branches (FR-06, FR-07) and account types / products (FR-10). */
public class BranchesPanel extends JPanel implements Refreshable {

    private final AppContext app;
    private final RowTableModel branchModel = new RowTableModel();
    private final DataTable branchTable = new DataTable(branchModel);
    private final RowTableModel productModel = new RowTableModel();

    public BranchesPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        Gui.page(this);
        add(Gui.pageHeader("Branches and products", "Branch network and the account types (banking products and internal ledger categories)",
                null), BorderLayout.NORTH);
        branchTable.emptyText("No branches yet.");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                Gui.card("Branches", Gui.scroll(branchTable), Gui.primary("New branch...", this::newBranch),
                        Gui.button("Edit contact details...", this::editBranch)),
                Gui.card("Account types", Gui.scroll(new DataTable(productModel).emptyText("No account types defined.")),
                        Gui.primary("New product...", this::newProduct)));
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> {
            branchModel.setItems(List.of("Id", "IFSC", "Name", "Address", "City", "Phone"), app.branches.list(),
                    (Branch b) -> Arrays.asList(b.branchId(), b.ifscCode(), b.branchName(), b.address(), b.city(), b.phone()));
            productModel.setItems(List.of("Id", "Code", "Name", "Category", "Normal side", "Min. opening", "Min. balance",
                    "Max holders", "Rate %"), app.accountTypes.listAll(), (AccountType t) -> Arrays.asList(
                    t.accountTypeId(), t.typeCode(), t.typeName(), t.category().name(), t.normalSide().name(),
                    t.minOpeningBalance(), t.minBalance(), t.maxHolders(), t.interestRate()));
        });
    }

    private void newBranch() {
        JTextField ifsc = new JTextField(12);
        JTextField name = new JTextField(20);
        JTextField address = new JTextField(28);
        JTextField city = new JTextField(16);
        JTextField phone = new JTextField(14);
        FormPanel form = new FormPanel().add("IFSC code", ifsc).add("Branch name", name).add("Address", address)
                .add("City", city).add("Phone", phone).note("IFSC format: 4 letters, 0, then 6 letters/digits (e.g. BDBS0000405).");
        if (Gui.showForm(this, "New branch", form) && Gui.attempt(this, () -> app.branches.create(Gui.text(ifsc),
                Gui.text(name), Gui.text(address), Gui.text(city), Gui.text(phone)))) {
            refresh();
        }
    }

    private void editBranch() {
        int row = branchTable.selectedModelRow();
        if (row < 0) {
            Gui.info(this, "Branches", "Select a branch first.");
            return;
        }
        int id = (Integer) branchModel.value(row, "Id");
        JTextField address = new JTextField((String) branchModel.value(row, "Address"), 28);
        JTextField city = new JTextField((String) branchModel.value(row, "City"), 16);
        JTextField phone = new JTextField((String) branchModel.value(row, "Phone"), 14);
        FormPanel form = new FormPanel().add("Address", address).add("City", city).add("Phone", phone);
        if (Gui.showForm(this, "Edit branch " + branchModel.value(row, "Name"), form)
                && Gui.attempt(this, () -> app.branches.updateContact(id, Gui.text(address), Gui.text(city), Gui.text(phone)))) {
            refresh();
        }
    }

    private void newProduct() {
        JTextField code = new JTextField(14);
        JTextField name = new JTextField(20);
        JTextField minOpening = new JTextField("0", 10);
        JTextField minBalance = new JTextField("0", 10);
        JTextField maxHolders = new JTextField("1", 4);
        JTextField rate = new JTextField("0", 6);
        JTextField description = new JTextField(28);
        FormPanel form = new FormPanel().add("Code (A-Z, _)", code).add("Name", name).add("Minimum opening balance", minOpening)
                .add("Minimum balance", minBalance).add("Maximum holders (1-4)", maxHolders)
                .add("Interest rate % (informational)", rate).add("Description", description);
        if (Gui.showForm(this, "New product", form) && Gui.attempt(this, () -> app.accountTypes.createProduct(
                Gui.text(code), Gui.text(name), Gui.money(minOpening, "Minimum opening balance"),
                Gui.money(minBalance, "Minimum balance"), Gui.integer(maxHolders, "Maximum holders"),
                Gui.money(rate, "Interest rate"), Gui.text(description)))) {
            refresh();
        }
    }
}
