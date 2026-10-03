package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.Account;
import com.bankdbms.model.Beneficiary;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Beneficiary management (FR-20 to FR-24). */
public class BeneficiariesPanel extends JPanel implements Refreshable {

    private final AppContext app;
    private final JComboBox<Choice> customer = Pickers.combo();
    private final RowTableModel model = new RowTableModel();
    private final DataTable table = new DataTable(model);

    public BeneficiariesPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        customer.addActionListener(e -> load());
        JPanel top = new JPanel(new BorderLayout());
        top.add(Gui.heading("Beneficiaries (saved payees)"), BorderLayout.NORTH);
        top.add(Gui.toolbar(new JLabel("Customer:"), customer, Gui.button("Add beneficiary...", this::add),
                Gui.button("Rename...", this::rename), Gui.button("Deactivate", () -> setActive(false)),
                Gui.button("Reactivate", () -> setActive(true))), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> Pickers.loadCustomers(app, customer));
        load();
    }

    private void load() {
        Object item = customer.getSelectedItem();
        if (!(item instanceof Choice c)) {
            model.set(List.of(), List.of());
            return;
        }
        Gui.attempt(this, () -> {
            List<List<Object>> rows = new ArrayList<>();
            for (Beneficiary b : app.beneficiaries.listForCustomer(c.id())) {
                Account payee = app.accounts.get(b.beneficiaryAccountId());
                String payeeName = app.customers.holdersOf(payee.accountId()).get(0).fullName();
                rows.add(Arrays.asList(b.beneficiaryId(), b.nickname(), payee.accountNumber(), payeeName,
                        b.status().name(), b.addedAt(), b.deactivatedAt()));
            }
            model.set(List.of("Id", "Nickname", "Payee account", "Payee name", "Status", "Added", "Deactivated"), rows);
        });
    }

    private Integer selectedId() {
        int row = table.selectedModelRow();
        if (row < 0) {
            Gui.info(this, "Beneficiaries", "Select a beneficiary in the table first.");
            return null;
        }
        return (Integer) model.value(row, "Id");
    }

    private void add() {
        Choice owner = (Choice) customer.getSelectedItem();
        if (owner == null) {
            return;
        }
        JComboBox<Choice> payee = Pickers.combo();
        JTextField nickname = new JTextField(20);
        if (!Gui.attempt(this, () -> Pickers.loadAccounts(app, payee, false))) {
            return;
        }
        FormPanel form = new FormPanel().add("Customer", new JLabel(owner.label())).add("Payee account", payee)
                .add("Nickname", nickname).note("A customer cannot add their own account; internal accounts are not allowed.");
        if (Gui.showForm(this, "Add beneficiary", form) && Gui.attempt(this,
                () -> app.beneficiaries.add(owner.id(), Gui.selected(payee, "a payee account").key(), Gui.text(nickname)))) {
            load();
        }
    }

    private void rename() {
        Integer id = selectedId();
        if (id == null) {
            return;
        }
        JTextField nickname = new JTextField(20);
        if (Gui.showForm(this, "Rename beneficiary", new FormPanel().add("New nickname", nickname))
                && Gui.attempt(this, () -> app.beneficiaries.rename(id, Gui.text(nickname)))) {
            load();
        }
    }

    private void setActive(boolean active) {
        Integer id = selectedId();
        if (id != null && Gui.attempt(this, () -> {
            if (active) {
                app.beneficiaries.reactivate(id);
            } else {
                app.beneficiaries.deactivate(id);
            }
        })) {
            load();
        }
    }
}
