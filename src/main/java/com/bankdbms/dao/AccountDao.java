package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class AccountDao {

    private static final String COLUMNS =
            "account_id, account_number, branch_id, account_type_id, balance, status, opened_at, closed_at";

    /** Inserts a new ACTIVE account with zero balance; money arrives only through ledger postings. */
    public int insert(Connection conn, String accountNumber, int branchId, int accountTypeId, LocalDateTime openedAt)
            throws SQLException {
        String sql = "INSERT INTO account (account_number, branch_id, account_type_id, balance, status, opened_at) "
                + "VALUES (?, ?, ?, 0.00, 'ACTIVE', ?)";
        return (int) JdbcSupport.insertReturningKey(conn, sql, ps -> {
            ps.setString(1, accountNumber);
            ps.setInt(2, branchId);
            ps.setInt(3, accountTypeId);
            ps.setTimestamp(4, Timestamp.valueOf(openedAt));
        });
    }

    public Optional<Account> findById(Connection conn, int accountId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM account WHERE account_id = ?",
                ps -> ps.setInt(1, accountId), AccountDao::map);
    }

    /**
     * Reads the account and takes an exclusive row lock (SELECT ... FOR UPDATE) held until the
     * enclosing transaction ends. Concurrent postings to the same account therefore run one after
     * another, so no update is lost.
     */
    public Optional<Account> findByIdForUpdate(Connection conn, int accountId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM account WHERE account_id = ? FOR UPDATE",
                ps -> ps.setInt(1, accountId), AccountDao::map);
    }

    public Optional<Account> findByNumber(Connection conn, String accountNumber) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM account WHERE account_number = ?",
                ps -> ps.setString(1, accountNumber), AccountDao::map);
    }

    public List<Account> findByCustomer(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT a." + COLUMNS.replace(", ", ", a.") + " FROM account a "
                + "JOIN account_holder h ON h.account_id = a.account_id WHERE h.customer_id = ? ORDER BY a.account_id";
        return JdbcSupport.queryList(conn, sql, ps -> ps.setInt(1, customerId), AccountDao::map);
    }

    public int updateBalance(Connection conn, int accountId, BigDecimal newBalance) throws SQLException {
        return JdbcSupport.update(conn, "UPDATE account SET balance = ? WHERE account_id = ?", ps -> {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, accountId);
        });
    }

    public int updateStatus(Connection conn, int accountId, AccountStatus status, LocalDateTime closedAt)
            throws SQLException {
        return JdbcSupport.update(conn, "UPDATE account SET status = ?, closed_at = ? WHERE account_id = ?", ps -> {
            ps.setString(1, status.name());
            JdbcSupport.setNullableTimestamp(ps, 2, closedAt);
            ps.setInt(3, accountId);
        });
    }

    private static Account map(ResultSet rs) throws SQLException {
        return new Account(rs.getInt("account_id"), rs.getString("account_number"), rs.getInt("branch_id"),
                rs.getInt("account_type_id"), rs.getBigDecimal("balance"), AccountStatus.valueOf(rs.getString("status")),
                JdbcSupport.getDateTime(rs, "opened_at"), JdbcSupport.getDateTime(rs, "closed_at"));
    }
}
