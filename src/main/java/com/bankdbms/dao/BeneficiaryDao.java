package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.BeneficiaryStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class BeneficiaryDao {

    private static final String COLUMNS =
            "beneficiary_id, customer_id, beneficiary_account_id, nickname, status, added_at, deactivated_at";

    public int insert(Connection conn, int customerId, int accountId, String nickname, LocalDateTime addedAt)
            throws SQLException {
        String sql = "INSERT INTO beneficiary (customer_id, beneficiary_account_id, nickname, status, added_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?)";
        return (int) JdbcSupport.insertReturningKey(conn, sql, ps -> {
            ps.setInt(1, customerId);
            ps.setInt(2, accountId);
            ps.setString(3, nickname);
            ps.setTimestamp(4, Timestamp.valueOf(addedAt));
        });
    }

    public Optional<Beneficiary> findById(Connection conn, int beneficiaryId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM beneficiary WHERE beneficiary_id = ?",
                ps -> ps.setInt(1, beneficiaryId), BeneficiaryDao::map);
    }

    public List<Beneficiary> findByCustomer(Connection conn, int customerId) throws SQLException {
        return JdbcSupport.queryList(conn,
                "SELECT " + COLUMNS + " FROM beneficiary WHERE customer_id = ? ORDER BY status, nickname",
                ps -> ps.setInt(1, customerId), BeneficiaryDao::map);
    }

    /** Search by nickname, payee account number, or the owning customer's name. */
    public List<Beneficiary> search(Connection conn, String keyword) throws SQLException {
        String sql = "SELECT b." + COLUMNS.replace(", ", ", b.") + " FROM beneficiary b "
                + "JOIN account a ON a.account_id = b.beneficiary_account_id "
                + "JOIN customer c ON c.customer_id = b.customer_id "
                + "WHERE b.nickname LIKE ? OR a.account_number LIKE ? OR CONCAT(c.first_name, ' ', c.last_name) LIKE ? "
                + "ORDER BY b.customer_id, b.nickname";
        String pattern = JdbcSupport.containsPattern(keyword);
        return JdbcSupport.queryList(conn, sql, ps -> {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
        }, BeneficiaryDao::map);
    }

    public int updateNickname(Connection conn, int beneficiaryId, String nickname) throws SQLException {
        return JdbcSupport.update(conn, "UPDATE beneficiary SET nickname = ? WHERE beneficiary_id = ?", ps -> {
            ps.setString(1, nickname);
            ps.setInt(2, beneficiaryId);
        });
    }

    public int updateStatus(Connection conn, int beneficiaryId, BeneficiaryStatus status, LocalDateTime deactivatedAt)
            throws SQLException {
        return JdbcSupport.update(conn,
                "UPDATE beneficiary SET status = ?, deactivated_at = ? WHERE beneficiary_id = ?", ps -> {
                    ps.setString(1, status.name());
                    JdbcSupport.setNullableTimestamp(ps, 2, deactivatedAt);
                    ps.setInt(3, beneficiaryId);
                });
    }

    private static Beneficiary map(ResultSet rs) throws SQLException {
        return new Beneficiary(rs.getInt("beneficiary_id"), rs.getInt("customer_id"),
                rs.getInt("beneficiary_account_id"), rs.getString("nickname"),
                BeneficiaryStatus.valueOf(rs.getString("status")), JdbcSupport.getDateTime(rs, "added_at"),
                JdbcSupport.getDateTime(rs, "deactivated_at"));
    }
}
