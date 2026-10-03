package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.Loan;
import com.bankdbms.model.LoanType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class LoanDao {

    private static final String COLUMNS = "loan_id, loan_number, account_id, customer_id, loan_type, principal, "
            + "annual_interest_rate, tenure_months, disbursement_txn_id";

    public int insert(Connection conn, Loan loan) throws SQLException {
        String sql = "INSERT INTO loan (loan_number, account_id, customer_id, loan_type, principal, annual_interest_rate, "
                + "tenure_months, disbursement_txn_id, disbursement_txn_type) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'LOAN_DISBURSEMENT')";
        return (int) JdbcSupport.insertReturningKey(conn, sql, ps -> {
            ps.setString(1, loan.loanNumber());
            ps.setInt(2, loan.accountId());
            ps.setInt(3, loan.customerId());
            ps.setString(4, loan.loanType().name());
            ps.setBigDecimal(5, loan.principal());
            ps.setBigDecimal(6, loan.annualInterestRate());
            ps.setInt(7, loan.tenureMonths());
            ps.setLong(8, loan.disbursementTxnId());
        });
    }

    public Optional<Loan> findById(Connection conn, int loanId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM loan WHERE loan_id = ?",
                ps -> ps.setInt(1, loanId), LoanDao::map);
    }

    /** Locks the loan row so repayments of the same loan are processed one at a time. */
    public Optional<Loan> findByIdForUpdate(Connection conn, int loanId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM loan WHERE loan_id = ? FOR UPDATE",
                ps -> ps.setInt(1, loanId), LoanDao::map);
    }

    public Optional<Loan> findByNumber(Connection conn, String loanNumber) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM loan WHERE loan_number = ?",
                ps -> ps.setString(1, loanNumber), LoanDao::map);
    }

    /** True if any loan repayable from this account still has an unpaid instalment (blocks closure, BR-13). */
    public boolean hasUnpaidInstallments(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT 1 FROM loan l JOIN loan_schedule s ON s.loan_id = l.loan_id "
                + "WHERE l.account_id = ? AND NOT EXISTS (SELECT 1 FROM loan_repayment r "
                + "WHERE r.loan_id = s.loan_id AND r.installment_no = s.installment_no) LIMIT 1";
        return JdbcSupport.queryOne(conn, sql, ps -> ps.setInt(1, accountId), rs -> true).isPresent();
    }

    private static Loan map(ResultSet rs) throws SQLException {
        return new Loan(rs.getInt("loan_id"), rs.getString("loan_number"), rs.getInt("account_id"),
                rs.getInt("customer_id"), LoanType.valueOf(rs.getString("loan_type")), rs.getBigDecimal("principal"),
                rs.getBigDecimal("annual_interest_rate"), rs.getInt("tenure_months"),
                rs.getLong("disbursement_txn_id"));
    }
}
