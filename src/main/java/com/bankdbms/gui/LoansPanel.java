package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.LoanInstallment;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.model.LoanType;
import com.bankdbms.service.LoanService;
import com.bankdbms.ui.TablePrinter;
import com.bankdbms.util.LoanCalculator;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Loans (FR-26 to FR-30): portfolio, schedule with derived status, disbursement, repayment. */
public class LoansPanel extends JPanel implements Refreshable {

    private static final List<String> HEADERS = List.of("Loan id", "Loan number", "Borrower", "Type", "Account",
            "Principal", "Rate %", "EMI", "Paid", "Overdue", "Outstanding", "Next due", "Status");

    private final AppContext app;
    private final RowTableModel model = new RowTableModel();
    private final DataTable table = new DataTable(model);
    private final RowTableModel scheduleModel = new RowTableModel();
    private final JLabel summary = new JLabel(" ");

    public LoansPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        Gui.page(this);
        add(Gui.pageHeader("Loans", "Loan portfolio with derived outstanding amounts, overdue instalments and schedules",
                Gui.toolbar(Gui.button("Refresh", this::refresh), Gui.primary("Disburse loan...", this::disburse),
                Gui.button("Repay next instalment...", this::repay))), BorderLayout.NORTH);
        table.emptyText("No loans have been disbursed.");
        JPanel lower = new JPanel(new BorderLayout(0, Theme.SPACE_M));
        lower.setOpaque(false);
        summary.setFont(Theme.BODY);
        lower.add(summary, BorderLayout.NORTH);
        lower.add(Gui.scroll(new DataTable(scheduleModel).emptyText("Select a loan above to see its repayment schedule.")),
                BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Gui.card("Loan portfolio", Gui.scroll(table)),
                Gui.card("Repayment schedule of the selected loan", lower));
        split.setResizeWeight(0.4);
        add(split, BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSchedule();
            }
        });
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> {
            model.setItems(HEADERS, app.loans.allPositions(), (LoanPosition p) -> Arrays.asList(p.loanId(),
                    p.loanNumber(), p.borrower(), p.loanType().name(), p.accountNumber(), p.principal(),
                    p.annualInterestRate(), p.emiAmount(), p.installmentsPaid() + "/" + p.tenureMonths(),
                    p.installmentsOverdue(), p.outstandingPrincipal(), p.nextDueDate(), p.loanStatus()));
            scheduleModel.set(List.of(), List.of());
            summary.setText(" ");
        });
    }

    private Integer selectedLoanId() {
        int row = table.selectedModelRow();
        return row < 0 ? null : (Integer) model.value(row, "Loan id");
    }

    private void showSchedule() {
        Integer id = selectedLoanId();
        if (id == null) {
            return;
        }
        Gui.attempt(this, () -> {
            LoanPosition p = app.loans.position(id);
            String sep = "&nbsp;&nbsp;&nbsp;<span style='color:#CBD5E1'>|</span>&nbsp;&nbsp;&nbsp;";
            summary.setText("<html><b style='font-size:13pt'>" + p.loanNumber() + "</b>" + sep + "Disbursed " + p.disbursedOn()
                    + sep + "Total interest <b>" + TablePrinter.format(p.totalInterest()) + "</b>" + sep
                    + "Principal repaid <b>" + TablePrinter.format(p.principalRepaid()) + "</b>" + sep
                    + "Interest paid <b>" + TablePrinter.format(p.interestPaid()) + "</b></html>");
            scheduleModel.setItems(List.of("No", "Due date", "Principal", "Interest", "Amount", "Status", "Paid at",
                    "Reference", "Days overdue"), app.loans.schedule(id), (LoanInstallment i) -> Arrays.asList(
                    i.installmentNo(), i.dueDate(), i.principalComponent(), i.interestComponent(), i.installmentAmount(),
                    i.status(), i.paidAt(), i.repaymentReference(), i.daysOverdue()));
        });
    }

    private void disburse() {
        JComboBox<Choice> borrower = Pickers.combo();
        JComboBox<Choice> account = Pickers.combo();
        JComboBox<LoanType> type = new JComboBox<>(LoanType.values());
        JTextField principal = new JTextField(12);
        JTextField rate = new JTextField(6);
        JTextField tenure = new JTextField(5);
        JLabel emiPreview = new JLabel("-");
        if (!Gui.attempt(this, () -> {
            Pickers.loadCustomers(app, borrower);
            Pickers.loadAccounts(app, account, false);
        })) {
            return;
        }
        Runnable preview = () -> {
            try {
                BigDecimal emi = LoanCalculator.emi(new BigDecimal(Gui.text(principal)), new BigDecimal(Gui.text(rate)),
                        Integer.parseInt(Gui.text(tenure)));
                emiPreview.setText(TablePrinter.format(emi) + " per month");
            } catch (RuntimeException e) {
                emiPreview.setText("-");
            }
        };
        for (JTextField f : List.of(principal, rate, tenure)) {
            f.addCaretListener(e -> preview.run());
        }
        FormPanel form = new FormPanel().add("Borrower", borrower).add("Disbursement / repayment account", account)
                .add("Loan type", type).add("Principal", principal).add("Annual interest rate %", rate)
                .add("Tenure (months)", tenure).add("EMI (preview)", emiPreview)
                .note("The borrower must hold the account. Reducing-balance EMI; schedule generated on disbursement.");
        if (Gui.showForm(this, "Disburse loan", form) && Gui.attempt(this, () -> {
            String number = Gui.selected(account, "an account").key();
            LoanPosition p = app.loans.disburse(Gui.selected(borrower, "a borrower").id(),
                    app.accounts.getByNumber(number).accountId(), (LoanType) type.getSelectedItem(),
                    Gui.money(principal, "Principal"), parseRate(rate), Gui.integer(tenure, "Tenure"));
            Gui.info(this, "Loan disbursed", "Loan " + p.loanNumber() + " disbursed. EMI " + TablePrinter.format(p.emiAmount())
                    + ", first instalment due " + p.nextDueDate() + ".");
        })) {
            refresh();
        }
    }

    private void repay() {
        Integer id = selectedLoanId();
        if (id == null) {
            Gui.info(this, "Repay", "Select a loan in the table first.");
            return;
        }
        LoanInstallment[] found = new LoanInstallment[1];
        if (!Gui.attempt(this, () -> found[0] = app.loans.schedule(id).stream()
                .filter(i -> !"PAID".equals(i.status())).findFirst().orElse(null))) {
            return;
        }
        LoanInstallment next = found[0];
        if (next == null) {
            Gui.info(this, "Repay", "This loan is fully repaid.");
            return;
        }
        JTextField amount = new JTextField(TablePrinter.format(next.installmentAmount()).replace(",", ""), 12);
        FormPanel form = new FormPanel().add("Instalment", new JLabel(next.installmentNo() + " due " + next.dueDate()
                + " (" + next.status() + ")")).add("Amount due", new JLabel(TablePrinter.format(next.installmentAmount())))
                .add("Amount to pay", amount).note("Exactly the instalment amount; partial payments are not supported.");
        if (Gui.showForm(this, "Repay next instalment", form) && Gui.attempt(this, () -> {
            LoanService.Repayment r = app.loans.repayNextInstallment(id, Gui.money(amount, "Amount"));
            Gui.info(this, "Repayment posted", "Instalment " + r.installmentNo() + " repaid. Reference "
                    + r.posted().transaction().referenceNo() + ".");
        })) {
            refresh();
        }
    }

    private static BigDecimal parseRate(JTextField field) {
        try {
            return new BigDecimal(Gui.text(field));
        } catch (NumberFormatException e) {
            throw new ValidationException("Interest rate must be a number such as 9.5.");
        }
    }
}
