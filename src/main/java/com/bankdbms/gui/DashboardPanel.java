package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.report.ReportTable;
import com.bankdbms.ui.TablePrinter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
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
    private final JLabel overdue = new JLabel();
    private final JLabel integrity = new JLabel();
    private final RowTableModel branchModel = new RowTableModel();

    public DashboardPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        JPanel cards = new JPanel(new GridLayout(2, 3, 10, 10));
        cards.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        cards.add(card("Customers", customers));
        cards.add(card("Customer accounts", accounts));
        cards.add(card("Total deposits", deposits));
        cards.add(card("Loans outstanding", loans));
        cards.add(card("Overdue instalments", overdue));
        cards.add(card("Ledger integrity (13 checks)", integrity));
        JPanel top = new JPanel(new BorderLayout());
        top.add(Gui.heading("Dashboard"), BorderLayout.NORTH);
        top.add(cards, BorderLayout.CENTER);
        top.add(Gui.toolbar(Gui.button("Refresh", this::refresh)), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        JPanel branches = new JPanel(new BorderLayout());
        branches.setBorder(BorderFactory.createTitledBorder("Branch summary"));
        branches.add(new JScrollPane(new DataTable(branchModel)), BorderLayout.CENTER);
        add(branches, BorderLayout.CENTER);
    }

    private static JPanel card(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xD0D7E2)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel t = new JLabel(title);
        t.setForeground(Color.DARK_GRAY);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 22f));
        value.setForeground(Gui.ACCENT);
        p.add(t, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
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
            loans.setText(TablePrinter.format(outstanding.setScale(2)) + "  (" + active + " active)");
            int overdueCount = positions.stream().mapToInt(LoanPosition::installmentsOverdue).sum();
            overdue.setText(String.valueOf(overdueCount));
            overdue.setForeground(overdueCount > 0 ? Gui.ERROR_RED : Gui.OK_GREEN);
            List<InvariantResult> checks = app.reports.verifyLedger();
            long failed = checks.stream().filter(r -> !r.passed()).count();
            integrity.setText(failed == 0 ? "All " + checks.size() + " pass" : failed + " FAILED");
            integrity.setForeground(failed == 0 ? Gui.OK_GREEN : Gui.ERROR_RED);
            branchModel.setReport(app.reports.branchSummary());
        });
    }
}
