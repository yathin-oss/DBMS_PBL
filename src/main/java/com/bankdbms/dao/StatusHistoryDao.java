package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.StatusChange;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public class StatusHistoryDao {

    public void insert(Connection conn, int accountId, AccountStatus oldStatus, AccountStatus newStatus,
                       String reason, LocalDateTime changedAt) throws SQLException {
        String sql = "INSERT INTO account_status_history (account_id, old_status, new_status, reason, changed_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        JdbcSupport.update(conn, sql, ps -> {
            ps.setInt(1, accountId);
            JdbcSupport.setNullableString(ps, 2, oldStatus == null ? null : oldStatus.name());
            ps.setString(3, newStatus.name());
            ps.setString(4, reason);
            ps.setTimestamp(5, Timestamp.valueOf(changedAt));
        });
    }

    public List<StatusChange> findByAccount(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT history_id, account_id, old_status, new_status, reason, changed_at "
                + "FROM account_status_history WHERE account_id = ? ORDER BY history_id";
        return JdbcSupport.queryList(conn, sql, ps -> ps.setInt(1, accountId), StatusHistoryDao::map);
    }

    private static StatusChange map(ResultSet rs) throws SQLException {
        String old = rs.getString("old_status");
        return new StatusChange(rs.getLong("history_id"), rs.getInt("account_id"),
                old == null ? null : AccountStatus.valueOf(old), AccountStatus.valueOf(rs.getString("new_status")),
                rs.getString("reason"), JdbcSupport.getDateTime(rs, "changed_at"));
    }
}
