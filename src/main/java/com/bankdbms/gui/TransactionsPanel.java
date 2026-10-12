package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.ui.TablePrinter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Supplier;

/** Money movement (FR-15 to FR-19, FR-25): deposit, withdraw, transfer, charge, reverse, lookup. */
public class TransactionsPanel extends JPanel implements Refreshable {

    private final AppContext app;
    private final RowTableModel resultModel = new RowTableModel();
    private final JLabel resultLabel = new JLabel("Ledger lines of the last posted transaction appear here.");

    private final JComboBox<Choice> depositAccount = Pickers.combo();
    private final JComboBox<Choice> withdrawAccount = Pickers.combo();
    private final JComboBox<Choice> fromAccount = Pickers.combo();
    private final JComboBox<Choice> toAccount = Pickers.combo();
    private final JComboBox<Choice> beneficiary = Pickers.combo();
    private final JComboBox<Choice> chargeAccount = Pickers.combo();
    private final JComboBox<Choice> chargeType = Pickers.combo();
    private final JRadioButton ownAccount = new JRadioButton("Own account (shared holder)", true);
    private final JRadioButton toBeneficiary = new JRadioButton("Saved beneficiary");

    public TransactionsPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        Gui.page(this);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Deposit", depositTab());
        tabs.addTab("Withdraw", withdrawTab());
        tabs.addTab("Transfer", transferTab());
        tabs.addTab("Service charge", chargeTab());
        tabs.addTab("Reverse / look up", reverseTab());
        JPanel top = new JPanel(new BorderLayout(0, Theme.SPACE_L));
        top.setOpaque(false);
        top.add(Gui.pageHeader("Transactions", "Post deposits, withdrawals, transfers and charges to the double-entry ledger",
                null), BorderLayout.NORTH);
        top.add(Gui.card(null, tabs), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        JPanel result = new JPanel(new BorderLayout(0, Theme.SPACE_M));
        result.setOpaque(false);
        Gui.status(resultLabel, Gui.Tone.NEUTRAL, resultLabel.getText());
        result.add(resultLabel, BorderLayout.NORTH);
        result.add(Gui.scroll(new DataTable(resultModel).emptyText("No transaction posted yet in this session.")),
                BorderLayout.CENTER);
        add(Gui.card("Result: double-entry ledger lines", result), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> {
            for (JComboBox<Choice> c : List.of(depositAccount, withdrawAccount, fromAccount, toAccount, chargeAccount)) {
                Pickers.loadAccounts(app, c, false);
            }
            Pickers.loadChargeTypes(app, chargeType);
            reloadBeneficiaries();
        });
    }

    private JPanel depositTab() {
        JTextField amount = new JTextField(12);
        JTextField narration = new JTextField(28);
        FormPanel form = new FormPanel().add("Account", depositAccount).add("Amount", amount).add("Narration", narration)
                .note("Posts DR CASH / CR customer account. Allowed on ACTIVE and FROZEN accounts.");
        return withAction(form, "Post deposit", () -> app.cash.deposit(accountId(depositAccount),
                Gui.money(amount, "Amount"), Gui.text(narration)), amount, narration);
    }

    private JPanel withdrawTab() {
        JTextField amount = new JTextField(12);
        JTextField narration = new JTextField(28);
        FormPanel form = new FormPanel().add("Account", withdrawAccount).add("Amount", amount).add("Narration", narration)
                .note("Posts DR customer account / CR CASH. The product's minimum balance must remain.");
        return withAction(form, "Post withdrawal", () -> app.cash.withdraw(accountId(withdrawAccount),
                Gui.money(amount, "Amount"), Gui.text(narration)), amount, narration);
    }

    private JPanel transferTab() {
        JTextField amount = new JTextField(12);
        JTextField remarks = new JTextField(28);
        ButtonGroup group = new ButtonGroup();
        group.add(ownAccount);
        group.add(toBeneficiary);
        JPanel kind = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        kind.setOpaque(false);
        ownAccount.setOpaque(false);
        toBeneficiary.setOpaque(false);
        kind.add(ownAccount);
        kind.add(toBeneficiary);
        ownAccount.addActionListener(e -> updateTransferMode());
        toBeneficiary.addActionListener(e -> updateTransferMode());
        fromAccount.addActionListener(e -> reloadBeneficiaries());
        FormPanel form = new FormPanel().add("From account", fromAccount).add("Transfer to", kind)
                .add("To account", toAccount).add("Beneficiary", beneficiary).add("Amount", amount).add("Remarks", remarks)
                .note("Atomic: debit and credit commit together or not at all. Third-party payments need an ACTIVE beneficiary.");
        updateTransferMode();
        return withAction(form, "Post transfer", () -> {
            int from = accountId(fromAccount);
            if (ownAccount.isSelected()) {
                return app.transfers.transfer(from, accountId(toAccount), Gui.money(amount, "Amount"), null, Gui.text(remarks));
            }
            return app.transfers.transferToBeneficiary(from, Gui.selected(beneficiary, "a beneficiary").id(),
                    Gui.money(amount, "Amount"), Gui.text(remarks));
        }, amount, remarks);
    }

    private JPanel chargeTab() {
        JTextField amount = new JTextField(12);
        FormPanel form = new FormPanel().add("Account", chargeAccount).add("Charge type", chargeType)
                .add("Amount (blank = default)", amount)
                .note("Posts DR customer account / CR FEE_INCOME and records the charge type.");
        return withAction(form, "Apply charge", () -> app.charges.apply(accountId(chargeAccount),
                Gui.selected(chargeType, "a charge type").id(), Gui.optionalMoney(amount)), amount);
    }

    private JPanel reverseTab() {
        JTextField reference = new JTextField(18);
        JTextField reason = new JTextField(28);
        FormPanel form = new FormPanel().add("Transaction reference (TX...)", reference).add("Reason", reason)
                .note("A reversal posts mirror-image entries and marks the original REVERSED. History is never edited.");
        JButton lookup = Gui.button("Look up", () -> Gui.attempt(this, () -> {
            resultModel.setReport(app.reports.transactionDetail(Gui.text(reference)));
            Gui.status(resultLabel, Gui.Tone.NEUTRAL, "Transaction " + Gui.text(reference).toUpperCase());
        }));
        JButton reverse = Gui.danger("Post reversal", () -> {
            if (Gui.confirm(this, "Reverse transaction", "Post a reversal of " + Gui.text(reference).toUpperCase() + "?")) {
                post(() -> app.reversals.reverse(Gui.text(reference), Gui.text(reason)), reason);
            }
        });
        return tabBody(form, lookup, reverse);
    }

    private JPanel withAction(FormPanel form, String label, Supplier<PostedTransaction> action, JTextField... clearAfter) {
        return tabBody(form, Gui.primary(label, () -> post(action, clearAfter)));
    }

    /** A sub-tab: the form, then its action buttons aligned under the fields. */
    private static JPanel tabBody(FormPanel form, JButton... actions) {
        JPanel stack = new JPanel(new BorderLayout(0, Theme.SPACE_M));
        stack.setOpaque(false);
        stack.add(form, BorderLayout.CENTER);
        stack.add(Gui.toolbar(actions), BorderLayout.SOUTH);
        JPanel panel = new JPanel(new BorderLayout());     // keeps the form at the top of a taller tab
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_L, 0, 0, 0));
        panel.add(stack, BorderLayout.NORTH);
        return panel;
    }

    private void post(Supplier<PostedTransaction> action, JTextField... clearAfter) {
        Gui.attempt(this, () -> {
            PostedTransaction t = action.get();
            resultModel.setReport(app.reports.transactionDetail(t.transaction().referenceNo()));
            Gui.status(resultLabel, Gui.Tone.SUCCESS, "Posted " + t.transaction().type() + "  " + t.transaction().referenceNo()
                    + "  amount " + TablePrinter.format(t.amount()));
            for (JTextField f : clearAfter) {
                f.setText("");
            }
            refresh();
        });
    }

    private void updateTransferMode() {
        toAccount.setEnabled(ownAccount.isSelected());
        beneficiary.setEnabled(toBeneficiary.isSelected());
    }

    private void reloadBeneficiaries() {
        Object from = fromAccount.getSelectedItem();
        if (from instanceof Choice c) {
            Pickers.loadBeneficiariesOfAccount(app, c.key(), beneficiary);
        }
    }

    private int accountId(JComboBox<Choice> combo) {
        return app.accounts.getByNumber(Gui.selected(combo, "an account").key()).accountId();
    }
}
