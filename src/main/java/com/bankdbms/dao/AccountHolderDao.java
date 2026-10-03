package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.AccountHolder;
import com.bankdbms.model.HolderRole;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public class AccountHolderDao {

    public void insert(Connection conn, int accountId, int customerId, HolderRole role, LocalDateTime addedAt)
            throws SQLException {
        String sql = "INSERT INTO account_holder (account_id, customer_id, holder_role, added_at) VALUES (?, ?, ?, ?)";
        JdbcSupport.update(conn, sql, ps -> {
            ps.setInt(1, accountId);
            ps.setInt(2, customerId);
            ps.setString(3, role.name());
            ps.setTimestamp(4, Timestamp.valueOf(addedAt));
        });
    }

    public List<AccountHolder> findByAccount(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT account_id, customer_id, holder_role, added_at FROM account_holder "
                + "WHERE account_id = ? ORDER BY holder_role, customer_id";
        return JdbcSupport.queryList(conn, sql, ps -> ps.setInt(1, accountId), AccountHolderDao::map);
    }

    public boolean isHolder(Connection conn, int accountId, int customerId) throws SQLException {
        String sql = "SELECT 1 FROM account_holder WHERE account_id = ? AND customer_id = ?";
        return JdbcSupport.queryOne(conn, sql, ps -> {
            ps.setInt(1, accountId);
            ps.setInt(2, customerId);
        }, rs -> true).isPresent();
    }

    /** True if at least one customer holds both accounts (an own-account transfer, BR-25). */
    public boolean shareAHolder(Connection conn, int accountA, int accountB) throws SQLException {
        String sql = "SELECT 1 FROM account_holder a JOIN account_holder b ON b.customer_id = a.customer_id "
                + "WHERE a.account_id = ? AND b.account_id = ? LIMIT 1";
        return JdbcSupport.queryOne(conn, sql, ps -> {
            ps.setInt(1, accountA);
            ps.setInt(2, accountB);
        }, rs -> true).isPresent();
    }

    private static AccountHolder map(ResultSet rs) throws SQLException {
        return new AccountHolder(rs.getInt("account_id"), rs.getInt("customer_id"),
                HolderRole.valueOf(rs.getString("holder_role")), JdbcSupport.getDateTime(rs, "added_at"));
    }
}
