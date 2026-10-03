package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.AccountType;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.Branch;
import com.bankdbms.model.ChargeType;
import com.bankdbms.model.Customer;
import com.bankdbms.report.ReportTable;
import com.bankdbms.ui.TablePrinter;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import java.util.ArrayList;
import java.util.List;

/** Drop-downs filled from the database, so operators pick records instead of typing ids. */
public final class Pickers {

    private Pickers() {
    }

    public static JComboBox<Choice> combo() {
        JComboBox<Choice> combo = new JComboBox<>();
        combo.setPrototypeDisplayValue(new Choice("0", "000000000000  Xxxxxxxxxxxx Xxxxxxxxxxx  (CURRENT, BLOCKED, 99,99,999.00)"));
        return combo;
    }

    /** Customer accounts (key = account number), optionally excluding CLOSED ones. */
    public static void loadAccounts(AppContext app, JComboBox<Choice> combo, boolean includeClosed) {
        ReportTable t = app.reports.accountBalances();
        List<Choice> items = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            String status = (String) t.value(i, "status");
            if (!includeClosed && "CLOSED".equals(status)) {
                continue;
            }
            items.add(new Choice((String) t.value(i, "account_number"), t.value(i, "account_number") + "  "
                    + t.value(i, "primary_holder") + "  (" + t.value(i, "type_code") + ", " + status + ", "
                    + TablePrinter.format(t.value(i, "balance")) + ")"));
        }
        fill(combo, items);
    }

    public static void loadCustomers(AppContext app, JComboBox<Choice> combo) {
        List<Choice> items = new ArrayList<>();
        for (Customer c : app.customers.list()) {
            items.add(new Choice(String.valueOf(c.customerId()), c.customerId() + " - " + c.fullName() + " (" + c.city() + ")"));
        }
        fill(combo, items);
    }

    public static void loadBranches(AppContext app, JComboBox<Choice> combo) {
        List<Choice> items = new ArrayList<>();
        for (Branch b : app.branches.list()) {
            items.add(new Choice(String.valueOf(b.branchId()), b.branchName() + ", " + b.city() + " (" + b.ifscCode() + ")"));
        }
        fill(combo, items);
    }

    public static void loadProducts(AppContext app, JComboBox<Choice> combo) {
        List<Choice> items = new ArrayList<>();
        for (AccountType t : app.accountTypes.listProducts()) {
            items.add(new Choice(String.valueOf(t.accountTypeId()), t.typeName() + " (min. opening "
                    + TablePrinter.format(t.minOpeningBalance()) + ", max " + t.maxHolders() + " holder(s))"));
        }
        fill(combo, items);
    }

    public static void loadChargeTypes(AppContext app, JComboBox<Choice> combo) {
        List<Choice> items = new ArrayList<>();
        for (ChargeType t : app.charges.chargeTypes()) {
            items.add(new Choice(String.valueOf(t.chargeTypeId()), t.chargeName() + " (" + TablePrinter.format(t.defaultAmount()) + ")"));
        }
        fill(combo, items);
    }

    /** ACTIVE beneficiaries of the customers who hold the given account. */
    public static void loadBeneficiariesOfAccount(AppContext app, String accountNumber, JComboBox<Choice> combo) {
        List<Choice> items = new ArrayList<>();
        int accountId = app.accounts.getByNumber(accountNumber).accountId();
        for (Customer holder : app.customers.holdersOf(accountId)) {
            for (Beneficiary b : app.beneficiaries.listForCustomer(holder.customerId())) {
                if (b.status().name().equals("ACTIVE")) {
                    String payee = app.accounts.get(b.beneficiaryAccountId()).accountNumber();
                    items.add(new Choice(String.valueOf(b.beneficiaryId()), b.nickname() + " -> " + payee
                            + " (saved by " + holder.fullName() + ")"));
                }
            }
        }
        fill(combo, items);
    }

    private static void fill(JComboBox<Choice> combo, List<Choice> items) {
        Object previous = combo.getSelectedItem();
        combo.setModel(new DefaultComboBoxModel<>(items.toArray(new Choice[0])));
        if (previous instanceof Choice p) {
            for (Choice c : items) {
                if (c.key().equals(p.key())) {
                    combo.setSelectedItem(c);
                    break;
                }
            }
        }
    }
}
