package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.exception.AccountStateException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountHolder;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Customer;
import com.bankdbms.model.StatusChange;
import com.bankdbms.ui.TablePrinter;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Account management (FR-11 to FR-14): open, holders, lifecycle, close, search. */
public class AccountsPanel extends JPanel implements Refreshable {

    private final AppContext app;
    private final JTextField search = new JTextField(18);
    private final RowTableModel model = new RowTableModel();
    private final DataTable table = new DataTable(model);
    private final RowTableModel holdersModel = new RowTableModel();
    private final RowTableModel historyModel = new RowTableModel();
    private final JLabel detail = new JLabel(" ");

    public AccountsPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        search.addActionListener(e -> refresh());
        JPanel top = new JPanel(new BorderLayout());
        top.add(Gui.heading("Accounts"), BorderLayout.NORTH);
        top.add(Gui.toolbar(new JLabel("Search number / holder:"), search, Gui.button("Search", this::refresh),
                Gui.button("Show all", () -> {
                    search.setText("");
                    refresh();
                }), Gui.button("Open account...", this::open), Gui.button("Add joint holder...", this::addJoint),
                Gui.button("Change status...", this::changeStatus), Gui.button("Close account...", this::close)),
                BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel details = new JPanel(new BorderLayout());
        details.setBorder(BorderFactory.createTitledBorder("Selected account"));
        detail.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        details.add(detail, BorderLayout.NORTH);
        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
        JScrollPane holders = new JScrollPane(new DataTable(holdersModel));
        holders.setBorder(BorderFactory.createTitledBorder("Holders"));
        JScrollPane history = new JScrollPane(new DataTable(historyModel));
        history.setBorder(BorderFactory.createTitledBorder("Status history"));
        lists.add(holders);
        lists.add(history);
        details.add(lists, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), details);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetails();
            }
        });
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> {
            String kw = Gui.text(search);
            model.setReport(kw.isEmpty() ? app.reports.accountBalances() : app.reports.searchAccounts(kw));
            holdersModel.set(List.of(), List.of());
            historyModel.set(List.of(), List.of());
            detail.setText(" ");
        });
    }

    private Account selected() {
        int row = table.selectedModelRow();
        if (row < 0) {
            Gui.info(this, "Accounts", "Select an account in the table first.");
            return null;
        }
        return app.accounts.getByNumber((String) model.value(row, "Account number"));
    }

    private void showDetails() {
        int row = table.selectedModelRow();
        if (row < 0) {
            return;
        }
        Gui.attempt(this, () -> {
            Account a = app.accounts.getByNumber((String) model.value(row, "Account number"));
            detail.setText("Account " + a.accountNumber() + "   |   " + app.accounts.productOf(a).typeName()
                    + "   |   status " + a.status() + "   |   balance " + TablePrinter.format(a.balance())
                    + "   |   opened " + TablePrinter.format(a.openedAt())
                    + (a.closedAt() == null ? "" : "   |   closed " + TablePrinter.format(a.closedAt())));
            List<List<Object>> holders = new ArrayList<>();
            for (AccountHolder h : app.accounts.holders(a.accountId())) {
                Customer c = app.customers.get(h.customerId());
                holders.add(Arrays.asList(h.role().name(), c.customerId(), c.fullName(), h.addedAt()));
            }
            holdersModel.set(List.of("Role", "Customer id", "Name", "Added"), holders);
            historyModel.setItems(List.of("Changed at", "From", "To", "Reason"), app.accounts.statusHistory(a.accountId()),
                    (StatusChange s) -> Arrays.asList(s.changedAt(), s.oldStatus() == null ? "-" : s.oldStatus().name(),
                            s.newStatus().name(), s.reason()));
        });
    }

    private void open() {
        JComboBox<Choice> customer = Pickers.combo();
        JComboBox<Choice> branch = Pickers.combo();
        JComboBox<Choice> product = Pickers.combo();
        JTextField deposit = new JTextField(12);
        if (!Gui.attempt(this, () -> {
            Pickers.loadCustomers(app, customer);
            Pickers.loadBranches(app, branch);
            Pickers.loadProducts(app, product);
        })) {
            return;
        }
        FormPanel form = new FormPanel().add("Primary holder", customer).add("Branch", branch).add("Product", product)
                .add("Opening deposit", deposit)
                .note("The opening deposit is posted to the ledger in the same database transaction.");
        if (Gui.showForm(this, "Open account", form) && Gui.attempt(this, () -> {
            Account a = app.accounts.open(Gui.selected(customer, "a customer").id(), Gui.selected(branch, "a branch").id(),
                    Gui.selected(product, "a product").id(), Gui.money(deposit, "Opening deposit"));
            Gui.info(this, "Account opened", "Account " + a.accountNumber() + " opened with balance "
                    + TablePrinter.format(a.balance()) + ".");
        })) {
            refresh();
        }
    }

    private void addJoint() {
        Account a = selected();
        if (a == null) {
            return;
        }
        JComboBox<Choice> customer = Pickers.combo();
        if (!Gui.attempt(this, () -> Pickers.loadCustomers(app, customer))) {
            return;
        }
        FormPanel form = new FormPanel().add("Account", new JLabel(a.accountNumber())).add("Joint holder", customer);
        if (Gui.showForm(this, "Add joint holder", form)
                && Gui.attempt(this, () -> app.accounts.addJointHolder(a.accountId(), Gui.selected(customer, "a customer").id()))) {
            refresh();
        }
    }

    private void changeStatus() {
        Account a = selected();
        if (a == null) {
            return;
        }
        JComboBox<AccountStatus> status = new JComboBox<>(a.status().allowedTransitions().stream()
                .filter(s -> s != AccountStatus.CLOSED).toArray(AccountStatus[]::new));
        JTextField reason = new JTextField(28);
        FormPanel form = new FormPanel().add("Account", new JLabel(a.accountNumber() + " (currently " + a.status() + ")"))
                .add("New status", status).add("Reason", reason)
                .note("FROZEN: deposits allowed, no debits. BLOCKED: no transactions. Use 'Close account' to close.");
        if (Gui.showForm(this, "Change account status", form) && Gui.attempt(this, () -> {
            if (status.getSelectedItem() == null) {
                throw new AccountStateException("No status change is possible from " + a.status() + ".");
            }
            app.accounts.changeStatus(a.accountId(), (AccountStatus) status.getSelectedItem(), Gui.text(reason));
        })) {
            refresh();
        }
    }

    private void close() {
        Account a = selected();
        if (a == null) {
            return;
        }
        JTextField reason = new JTextField(28);
        FormPanel form = new FormPanel().add("Account", new JLabel(a.accountNumber()))
                .add("Balance to pay out", new JLabel(TablePrinter.format(a.balance()))).add("Reason", reason)
                .note("Closing is permanent. The remaining balance is paid out as a withdrawal.");
        if (Gui.showForm(this, "Close account", form)
                && Gui.attempt(this, () -> app.accounts.close(a.accountId(), Gui.text(reason)))) {
            refresh();
        }
    }
}
