package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.db.SqlScriptRunner;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.model.LoanType;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.report.ReportTable;
import com.bankdbms.report.StatementLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Read-only queries over the reporting views in sql/03_views.sql. */
public class ReportDao {

    private static final String INVARIANTS_SCRIPT = "sql/06_verification_queries.sql";

    public ReportTable accountBalances(Connection conn) throws SQLException {
        return table(conn, "Account Balances",
                "SELECT account_number, primary_holder, holder_count, branch_name, type_code, status, balance, "
                        + "min_balance, opened_at FROM v_account_overview ORDER BY branch_name, account_number",
                JdbcSupport.NO_PARAMS);
    }

    public ReportTable accountsOfCustomer(Connection conn, int customerId) throws SQLException {
        return table(conn, "Accounts of customer " + customerId,
                "SELECT o.account_number, o.type_code, o.branch_name, h.holder_role, o.status, o.balance "
                        + "FROM v_account_overview o JOIN account_holder h ON h.account_id = o.account_id "
                        + "WHERE h.customer_id = ? ORDER BY o.account_number",
                ps -> ps.setInt(1, customerId));
    }

    public ReportTable searchAccounts(Connection conn, String keyword) throws SQLException {
        String pattern = JdbcSupport.containsPattern(keyword);
        return table(conn, "Account search: " + keyword,
                "SELECT account_number, primary_holder, holder_count, branch_name, type_code, status, balance "
                        + "FROM v_account_overview WHERE account_number LIKE ? OR primary_holder LIKE ? "
                        + "OR account_id IN (SELECT h.account_id FROM account_holder h JOIN customer c "
                        + "ON c.customer_id = h.customer_id WHERE CONCAT(c.first_name, ' ', c.last_name) LIKE ?) "
                        + "ORDER BY account_number",
                ps -> {
                    ps.setString(1, pattern);
                    ps.setString(2, pattern);
                    ps.setString(3, pattern);
                });
    }

    public ReportTable transactionAudit(Connection conn, LocalDateTime from, LocalDateTime toExclusive)
            throws SQLException {
        return table(conn, "Transaction Audit",
                "SELECT reference_no, posted_at, transaction_type, status, total_debit, total_credit, entry_count, "
                        + "debit_accounts, credit_accounts, reverses_reference, reversed_by_reference, narration "
                        + "FROM v_transaction_audit WHERE posted_at >= ? AND posted_at < ? ORDER BY transaction_id",
                ps -> bindRange(ps, from, toExclusive));
    }

    public ReportTable transactionDetail(Connection conn, String referenceNo) throws SQLException {
        return table(conn, "Transaction " + referenceNo,
                "SELECT reference_no, posted_at, transaction_type, transaction_status, account_number, entry_side, "
                        + "amount, balance_after, narration FROM v_ledger_entry WHERE reference_no = ? ORDER BY entry_id",
                ps -> ps.setString(1, referenceNo));
    }

    public ReportTable transfers(Connection conn, LocalDateTime from, LocalDateTime toExclusive) throws SQLException {
        return table(conn, "Transfers",
                "SELECT reference_no, posted_at, status, from_account, to_account, amount, transfer_kind, "
                        + "beneficiary_nickname, remarks FROM v_transfer_report "
                        + "WHERE posted_at >= ? AND posted_at < ? ORDER BY transaction_id",
                ps -> bindRange(ps, from, toExclusive));
    }

    public List<LoanPosition> loanPositions(Connection conn) throws SQLException {
        return JdbcSupport.queryList(conn, LOAN_POSITION_SQL + " ORDER BY loan_id", JdbcSupport.NO_PARAMS,
                ReportDao::mapLoanPosition);
    }

    public List<LoanPosition> loanPosition(Connection conn, int loanId) throws SQLException {
        return JdbcSupport.queryList(conn, LOAN_POSITION_SQL + " WHERE loan_id = ?", ps -> ps.setInt(1, loanId),
                ReportDao::mapLoanPosition);
    }

    public List<LoanPosition> loanPositionsForCustomer(Connection conn, int customerId) throws SQLException {
        return JdbcSupport.queryList(conn, LOAN_POSITION_SQL + " WHERE customer_id = ? ORDER BY loan_id",
                ps -> ps.setInt(1, customerId), ReportDao::mapLoanPosition);
    }

    public ReportTable loanOutstanding(Connection conn) throws SQLException {
        return table(conn, "Loan Outstanding",
                "SELECT loan_number, borrower, loan_type, principal, annual_interest_rate, tenure_months, emi_amount, "
                        + "installments_paid, installments_overdue, principal_repaid, outstanding_principal, "
                        + "next_due_date, loan_status FROM v_loan_position ORDER BY loan_status, loan_number",
                JdbcSupport.NO_PARAMS);
    }

    public ReportTable loanRepayments(Connection conn, Integer loanId) throws SQLException {
        String where = loanId == null ? "" : " WHERE loan_id = ?";
        return table(conn, "Loan Repayments",
                "SELECT reference_no, paid_at, loan_number, borrower, installment_no, due_date, amount_paid, "
                        + "principal_component, interest_component, timeliness, days_late FROM v_loan_repayment_report"
                        + where + " ORDER BY loan_id, installment_no",
                ps -> {
                    if (loanId != null) {
                        ps.setInt(1, loanId);
                    }
                });
    }

    public ReportTable branchSummary(Connection conn) throws SQLException {
        return table(conn, "Branch Summary",
                "SELECT branch_name, city, total_accounts, active_accounts, restricted_accounts, closed_accounts, "
                        + "customers_served, total_deposits, active_loans, loan_outstanding FROM v_branch_summary "
                        + "ORDER BY branch_id", JdbcSupport.NO_PARAMS);
    }

    public ReportTable trialBalance(Connection conn) throws SQLException {
        return table(conn, "Trial Balance",
                "SELECT ledger_account, ledger_group, normal_side, debit_balance, credit_balance FROM v_trial_balance",
                JdbcSupport.NO_PARAMS);
    }

    public ReportTable chargeSummary(Connection conn) throws SQLException {
        return table(conn, "Charge Summary",
                "SELECT charge_code, charge_name, default_amount, charges_applied, total_charged FROM v_charge_summary "
                        + "ORDER BY charge_code", JdbcSupport.NO_PARAMS);
    }

    public ReportTable dailySummary(Connection conn, LocalDateTime from, LocalDateTime toExclusive)
            throws SQLException {
        return table(conn, "Daily Transaction Summary",
                "SELECT business_date, transaction_type, transaction_count, total_amount "
                        + "FROM v_daily_transaction_summary WHERE business_date >= DATE(?) AND business_date < DATE(?) "
                        + "ORDER BY business_date, transaction_type",
                ps -> bindRange(ps, from, toExclusive));
    }

    /** Balance immediately before the period: balance_after of the last earlier entry, or zero. */
    public BigDecimal balanceBefore(Connection conn, int accountId, LocalDateTime from) throws SQLException {
        return JdbcSupport.queryOne(conn,
                "SELECT balance_after FROM v_ledger_entry WHERE account_id = ? AND posted_at < ? "
                        + "ORDER BY entry_id DESC LIMIT 1",
                ps -> {
                    ps.setInt(1, accountId);
                    ps.setTimestamp(2, Timestamp.valueOf(from));
                }, rs -> rs.getBigDecimal(1)).orElse(BigDecimal.ZERO.setScale(2));
    }

    public List<StatementLine> statementLines(Connection conn, int accountId, LocalDateTime from,
                                              LocalDateTime toExclusive) throws SQLException {
        return JdbcSupport.queryList(conn,
                "SELECT posted_at, reference_no, transaction_type, transaction_status, narration, debit_amount, "
                        + "credit_amount, balance_after FROM v_ledger_entry "
                        + "WHERE account_id = ? AND posted_at >= ? AND posted_at < ? ORDER BY entry_id",
                ps -> {
                    ps.setInt(1, accountId);
                    bindRange(ps, 2, from, toExclusive);
                },
                rs -> new StatementLine(JdbcSupport.getDateTime(rs, "posted_at"), rs.getString("reference_no"),
                        rs.getString("transaction_type"), rs.getString("transaction_status"),
                        rs.getString("narration"), rs.getBigDecimal("debit_amount"),
                        rs.getBigDecimal("credit_amount"), rs.getBigDecimal("balance_after")));
    }

    /** Runs every integrity check in sql/06_verification_queries.sql. */
    public List<InvariantResult> verifyInvariants(Connection conn) throws SQLException {
        List<String> statements = SqlScriptRunner.split(readClasspath(INVARIANTS_SCRIPT));
        List<InvariantResult> results = new ArrayList<>();
        for (String sql : statements) {
            results.addAll(JdbcSupport.queryList(conn, sql, JdbcSupport.NO_PARAMS,
                    rs -> new InvariantResult(rs.getString(1), rs.getLong(2))));
        }
        return results;
    }

    private static final String LOAN_POSITION_SQL = "SELECT loan_id, loan_number, loan_type, customer_id, borrower, "
            + "account_number, principal, annual_interest_rate, tenure_months, disbursed_on, emi_amount, "
            + "installments_paid, installments_overdue, total_interest, principal_repaid, interest_paid, "
            + "outstanding_principal, next_due_date, loan_status FROM v_loan_position";

    private static LoanPosition mapLoanPosition(ResultSet rs) throws SQLException {
        return new LoanPosition(rs.getInt("loan_id"), rs.getString("loan_number"),
                LoanType.valueOf(rs.getString("loan_type")), rs.getInt("customer_id"), rs.getString("borrower"),
                rs.getString("account_number"), rs.getBigDecimal("principal"),
                rs.getBigDecimal("annual_interest_rate"), rs.getInt("tenure_months"),
                JdbcSupport.getDate(rs, "disbursed_on"), rs.getBigDecimal("emi_amount"),
                rs.getInt("installments_paid"), rs.getInt("installments_overdue"), rs.getBigDecimal("total_interest"),
                rs.getBigDecimal("principal_repaid"), rs.getBigDecimal("interest_paid"),
                rs.getBigDecimal("outstanding_principal"), JdbcSupport.getDate(rs, "next_due_date"),
                rs.getString("loan_status"));
    }

    private static ReportTable table(Connection conn, String title, String sql, JdbcSupport.Binder binder)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) {
                        Object value = rs.getObject(i);
                        row.add(value instanceof Timestamp ts ? ts.toLocalDateTime() : value);
                    }
                    rows.add(row);
                }
                return new ReportTable(title, columns, rows);
            }
        }
    }

    private static void bindRange(PreparedStatement ps, LocalDateTime from, LocalDateTime toExclusive)
            throws SQLException {
        bindRange(ps, 1, from, toExclusive);
    }

    private static void bindRange(PreparedStatement ps, int firstIndex, LocalDateTime from, LocalDateTime toExclusive)
            throws SQLException {
        ps.setTimestamp(firstIndex, Timestamp.valueOf(from));
        ps.setTimestamp(firstIndex + 1, Timestamp.valueOf(toExclusive));
    }

    private static String readClasspath(String resource) {
        try (InputStream in = ReportDao.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
