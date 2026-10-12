package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.report.ReportTable;
import com.bankdbms.ui.TablePrinter;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.List;

/** Overview: headline figures, ledger integrity and the branch summary report. */
public class DashboardPanel extends JPanel implements Refreshable {

    private final AppContext app;
    private final JLabel customers = new JLabel();
    private final JLabel accounts = new JLabel();
    private final JLabel deposits = new JLabel();
    private final JLabel loans = new JLabel();
    private final JLabel loansCaption = Gui.muted(" ");
    private final JLabel overdue = new JLabel();
    private final JLabel overdueCaption = Gui.muted(" ");
    private final JLabel integrity = new JLabel();
    private final RowTableModel branchModel = new RowTableModel();

    public DashboardPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        Gui.page(this);
        JPanel cards = new JPanel(new GridLayout(2, 3, Theme.SPACE_L, Theme.SPACE_L));
        cards.setOpaque(false);
        cards.add(card("Customers", customers, Gui.muted("Registered customers")));
        cards.add(card("Customer accounts", accounts, Gui.muted("Across all branches and products")));
        cards.add(card("Total deposits", deposits, Gui.muted("Sum of customer account balances")));
        cards.add(card("Loans outstanding", loans, loansCaption));
        cards.add(card("Overdue instalments", overdue, overdueCaption));
        cards.add(card("Ledger integrity", integrity, Gui.muted("Double-entry invariants verified")));
        JPanel top = new JPanel(new BorderLayout(0, Theme.SPACE_L));
        top.setOpaque(false);
        top.add(Gui.pageHeader("Dashboard", "Live overview of the bank's customers, balances, loans and ledger health",
                Gui.toolbar(Gui.button("Refresh", this::refresh))), BorderLayout.NORTH);
        top.add(cards, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(Gui.card("Branch summary", Gui.scroll(new DataTable(branchModel).emptyText("No branches found."))),
                BorderLayout.CENTER);
    }

    private static JPanel card(String title, JLabel value, JLabel caption) {
        Card p = new Card(new BorderLayout(0, Theme.SPACE_XS));
        JLabel t = new JLabel(title);
        t.setFont(Theme.LABEL);
        t.setForeground(Theme.TEXT_MUTED);
        value.setFont(Theme.KPI);
        value.setForeground(Theme.TEXT);
        caption.setFont(Theme.SMALL);
        p.add(t, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        p.add(caption, BorderLayout.SOUTH);
        p.getAccessibleContext().setAccessibleName(title);
        return p;
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> {
            customers.setText(String.valueOf(app.customers.list().size()));
            ReportTable balances = app.reports.accountBalances();
            accounts.setText(String.valueOf(balances.size()));
            BigDecimal total = BigDecimal.ZERO;
            for (int i = 0; i < balances.size(); i++) {
                total = total.add((BigDecimal) balances.value(i, "balance"));
            }
            deposits.setText(TablePrinter.format(total.setScale(2)));
            List<LoanPosition> positions = app.loans.allPositions();
            BigDecimal outstanding = positions.stream().map(LoanPosition::outstandingPrincipal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long active = positions.stream().filter(p -> "ACTIVE".equals(p.loanStatus())).count();
            loans.setText(TablePrinter.format(outstanding.setScale(2)));
            loansCaption.setText(active + " active loan" + (active == 1 ? "" : "s"));
            int overdueCount = positions.stream().mapToInt(LoanPosition::installmentsOverdue).sum();
            overdue.setText(String.valueOf(overdueCount));
            overdue.setForeground(overdueCount > 0 ? Theme.ERROR : Theme.SUCCESS);
            overdueCaption.setText(overdueCount > 0 ? "Past due and unpaid - needs follow-up" : "All instalments are on schedule");
            List<InvariantResult> checks = app.reports.verifyLedger();
            long failed = checks.stream().filter(r -> !r.passed()).count();
            integrity.setText(failed == 0 ? "All " + checks.size() + " pass" : failed + " of " + checks.size() + " failed");
            integrity.setForeground(failed == 0 ? Theme.SUCCESS : Theme.ERROR);
            branchModel.setReport(app.reports.branchSummary());
        });
    }
}
