package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.Branch;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class BranchDao {

    private static final String COLUMNS = "branch_id, ifsc_code, branch_name, address, city, phone, created_at";

    public int insert(Connection conn, String ifsc, String name, String address, String city, String phone,
                      LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO branch (ifsc_code, branch_name, address, city, phone, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        return (int) JdbcSupport.insertReturningKey(conn, sql, ps -> {
            ps.setString(1, ifsc);
            ps.setString(2, name);
            ps.setString(3, address);
            ps.setString(4, city);
            ps.setString(5, phone);
            ps.setTimestamp(6, Timestamp.valueOf(createdAt));
        });
    }

    public Optional<Branch> findById(Connection conn, int branchId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM branch WHERE branch_id = ?",
                ps -> ps.setInt(1, branchId), BranchDao::map);
    }

    public List<Branch> findAll(Connection conn) throws SQLException {
        return JdbcSupport.queryList(conn, "SELECT " + COLUMNS + " FROM branch ORDER BY branch_id",
                JdbcSupport.NO_PARAMS, BranchDao::map);
    }

    public List<Branch> search(Connection conn, String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM branch "
                + "WHERE branch_name LIKE ? OR city LIKE ? OR ifsc_code LIKE ? ORDER BY branch_name";
        String pattern = JdbcSupport.containsPattern(keyword);
        return JdbcSupport.queryList(conn, sql, ps -> {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
        }, BranchDao::map);
    }

    public int updateContact(Connection conn, int branchId, String address, String city, String phone)
            throws SQLException {
        String sql = "UPDATE branch SET address = ?, city = ?, phone = ? WHERE branch_id = ?";
        return JdbcSupport.update(conn, sql, ps -> {
            ps.setString(1, address);
            ps.setString(2, city);
            ps.setString(3, phone);
            ps.setInt(4, branchId);
        });
    }

    private static Branch map(ResultSet rs) throws SQLException {
        return new Branch(rs.getInt("branch_id"), rs.getString("ifsc_code"), rs.getString("branch_name"),
                rs.getString("address"), rs.getString("city"), rs.getString("phone"),
                JdbcSupport.getDateTime(rs, "created_at"));
    }
}
