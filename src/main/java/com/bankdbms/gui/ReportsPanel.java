package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.report.AccountStatement;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.report.ReportTable;
import com.bankdbms.report.StatementLine;
import com.bankdbms.ui.TablePrinter;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Statements and reports (FR-31 to FR-37), all read from the database views. */
public class ReportsPanel extends JPanel implements Refreshable {

    private static final String STATEMENT = "Account statement";
    private static final String BALANCES = "Account balances";
    private static final String AUDIT = "Transaction audit";
    private static final String TRANSFERS = "Transfers";
    private static final String LOAN_OUTSTANDING = "Loan outstanding";
    private static final String LOAN_REPAYMENTS = "Loan repayments";
    private static final String BRANCH = "Branch summary";
    private static final String TRIAL = "Trial balance (bank's books)";
    private static final String CHARGES = "Charge summary";
    private static final String DAILY = "Daily transaction summary";
    private static final String INTEGRITY = "Ledger integrity check";

    private final AppContext app;
    private final JComboBox<String> report = new JComboBox<>(new String[]{STATEMENT, BALANCES, AUDIT, TRANSFERS,
            LOAN_OUTSTANDING, LOAN_REPAYMENTS, BRANCH, TRIAL, CHARGES, DAILY, INTEGRITY});
    private final JComboBox<Choice> account = Pickers.combo();
    private final JTextField from = new JTextField(LocalDate.now().withDayOfMonth(1).minusMonths(1).toString(), 10);
    private final JTextField to = new JTextField(LocalDate.now().toString(), 10);
    private final RowTableModel model = new RowTableModel();
    private final JLabel header = new JLabel(" ");
    private final JLabel footer = new JLabel(" ");
    private String title = "report";

    public ReportsPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        report.addActionListener(e -> updateInputs());
        JPanel top = new JPanel(new BorderLayout());
        top.add(Gui.heading("Statements and reports"), BorderLayout.NORTH);
        FormPanel inputs = new FormPanel().add("Report", report).add("Account (statement only)", account)
                .add("From date (yyyy-mm-dd)", from).add("To date (yyyy-mm-dd)", to);
        top.add(inputs, BorderLayout.CENTER);
        top.add(Gui.toolbar(Gui.button("Run report", this::run), Gui.button("Export CSV...", this::export)),
                BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        JPanel result = new JPanel(new BorderLayout());
        result.setBorder(BorderFactory.createTitledBorder("Result"));
        header.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        footer.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        result.add(header, BorderLayout.NORTH);
        result.add(new JScrollPane(new DataTable(model)), BorderLayout.CENTER);
        result.add(footer, BorderLayout.SOUTH);
        add(result, BorderLayout.CENTER);
        updateInputs();
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> Pickers.loadAccounts(app, account, true));
    }

    private void updateInputs() {
        String r = (String) report.getSelectedItem();
        account.setEnabled(STATEMENT.equals(r));
        boolean dated = STATEMENT.equals(r) || AUDIT.equals(r) || TRANSFERS.equals(r) || DAILY.equals(r);
        from.setEnabled(dated);
        to.setEnabled(dated);
    }

    private void run() {
        Gui.attempt(this, () -> {
            String r = (String) report.getSelectedItem();
            title = r;
            footer.setText(" ");
            header.setText(r);
            switch (r) {
                case STATEMENT -> statement();
                case BALANCES -> model.setReport(app.reports.accountBalances());
                case AUDIT -> model.setReport(app.reports.transactionAudit(Gui.date(from, "From date"), Gui.date(to, "To date")));
                case TRANSFERS -> model.setReport(app.reports.transfers(Gui.date(from, "From date"), Gui.date(to, "To date")));
                case LOAN_OUTSTANDING -> model.setReport(app.reports.loanOutstanding());
                case LOAN_REPAYMENTS -> model.setReport(app.reports.loanRepayments(null));
                case BRANCH -> model.setReport(app.reports.branchSummary());
                case TRIAL -> trialBalance();
                case CHARGES -> model.setReport(app.reports.chargeSummary());
                case DAILY -> model.setReport(app.reports.dailySummary(Gui.date(from, "From date"), Gui.date(to, "To date")));
                case INTEGRITY -> integrity();
                default -> throw new IllegalStateException(r);
            }
            if (footer.getText().isBlank()) {
                footer.setText(model.getRowCount() + " row(s)");
            }
        });
    }

    private void statement() {
        int accountId = app.accounts.getByNumber(Gui.selected(account, "an account").key()).accountId();
        AccountStatement s = app.reports.statement(accountId, Gui.date(from, "From date"), Gui.date(to, "To date"));
        title = "statement_" + s.accountNumber();
        header.setText("<html><b>Statement of account " + s.accountNumber() + "</b> &nbsp; " + s.productName() + ", "
                + s.branchName() + " &nbsp; | &nbsp; Holder(s): " + s.holders() + " &nbsp; | &nbsp; Status: " + s.accountStatus()
                + "<br>Period " + s.fromDate() + " to " + s.toDate() + " &nbsp; | &nbsp; Opening balance <b>"
                + TablePrinter.format(s.openingBalance()) + "</b></html>");
        List<List<Object>> rows = new ArrayList<>();
        for (StatementLine l : s.lines()) {
            rows.add(Arrays.asList(l.postedAt(), l.referenceNo(), l.transactionType(), l.narration(), l.debit(),
                    l.credit(), l.balanceAfter(), "REVERSED".equals(l.transactionStatus()) ? "REVERSED" : ""));
        }
        model.set(List.of("Date", "Reference", "Type", "Narration", "Debit (out)", "Credit (in)", "Balance", "Note"), rows);
        footer.setText("<html>Total debits <b>" + TablePrinter.format(s.totalDebits()) + "</b> &nbsp; Total credits <b>"
                + TablePrinter.format(s.totalCredits()) + "</b> &nbsp; Closing balance <b>" + TablePrinter.format(s.closingBalance())
                + "</b> &nbsp; " + (s.isConsistent() ? "<font color='#2E7D32'>opening + credits - debits = closing</font>"
                : "<font color='#C62828'>INCONSISTENT</font>") + "</html>");
    }

    private void trialBalance() {
        ReportTable t = app.reports.trialBalance();
        model.setReport(t);
        BigDecimal dr = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (int i = 0; i < t.size(); i++) {
            dr = dr.add((BigDecimal) t.value(i, "debit_balance"));
            cr = cr.add((BigDecimal) t.value(i, "credit_balance"));
        }
        footer.setText("Total debit balances " + TablePrinter.format(dr) + "   |   Total credit balances "
                + TablePrinter.format(cr) + "   |   " + (dr.compareTo(cr) == 0 ? "BALANCED" : "NOT BALANCED"));
    }

    private void integrity() {
        List<InvariantResult> results = app.reports.verifyLedger();
        model.setItems(List.of("Invariant", "Violations", "Result"), results,
                (InvariantResult r) -> Arrays.asList(r.invariant(), r.violations(), r.passed() ? "PASS" : "FAIL"));
        long failed = results.stream().filter(r -> !r.passed()).count();
        footer.setText(failed == 0 ? "All " + results.size() + " ledger invariants hold." : failed + " invariant(s) FAILED.");
    }

    private void export() {
        if (model.getRowCount() == 0) {
            Gui.info(this, "Export", "Run a report first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(title.replaceAll("[^A-Za-z0-9_]+", "_").toLowerCase() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        Gui.attempt(this, () -> {
            List<String> lines = new ArrayList<>();
            lines.add(model.columns().stream().map(ReportsPanel::csv).collect(Collectors.joining(",")));
            for (List<Object> row : model.rows()) {
                lines.add(row.stream().map(v -> csv(v == null ? "" : String.valueOf(v))).collect(Collectors.joining(",")));
            }
            try {
                Files.write(path, lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Gui.info(this, "Export", "Saved " + model.getRowCount() + " rows to " + path.getFileName() + ".");
        });
    }

    private static String csv(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
