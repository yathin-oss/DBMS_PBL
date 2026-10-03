package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.Customer;
import com.bankdbms.model.CustomerForm;
import com.bankdbms.model.Gender;
import com.bankdbms.model.IdProofType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class CustomerDao {

    private static final String COLUMNS = "customer_id, first_name, last_name, date_of_birth, gender, email, phone, "
            + "address, city, id_proof_type, id_proof_number, created_at, updated_at";

    public int insert(Connection conn, CustomerForm f, LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO customer (first_name, last_name, date_of_birth, gender, email, phone, address, city, "
                + "id_proof_type, id_proof_number, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return (int) JdbcSupport.insertReturningKey(conn, sql, ps -> {
            bindForm(ps, f);
            ps.setTimestamp(11, Timestamp.valueOf(createdAt));
            ps.setTimestamp(12, Timestamp.valueOf(createdAt));
        });
    }

    public int update(Connection conn, int customerId, CustomerForm f, LocalDateTime updatedAt) throws SQLException {
        String sql = "UPDATE customer SET first_name = ?, last_name = ?, date_of_birth = ?, gender = ?, email = ?, "
                + "phone = ?, address = ?, city = ?, id_proof_type = ?, id_proof_number = ?, updated_at = ? "
                + "WHERE customer_id = ?";
        return JdbcSupport.update(conn, sql, ps -> {
            bindForm(ps, f);
            ps.setTimestamp(11, Timestamp.valueOf(updatedAt));
            ps.setInt(12, customerId);
        });
    }

    public Optional<Customer> findById(Connection conn, int customerId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM customer WHERE customer_id = ?",
                ps -> ps.setInt(1, customerId), CustomerDao::map);
    }

    public List<Customer> findAll(Connection conn) throws SQLException {
        return JdbcSupport.queryList(conn, "SELECT " + COLUMNS + " FROM customer ORDER BY customer_id",
                JdbcSupport.NO_PARAMS, CustomerDao::map);
    }

    /** Search by name, email, phone or ID-proof number (partial match). */
    public List<Customer> search(Connection conn, String keyword) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM customer "
                + "WHERE CONCAT(first_name, ' ', last_name) LIKE ? OR email LIKE ? OR phone LIKE ? "
                + "OR id_proof_number LIKE ? ORDER BY last_name, first_name";
        String pattern = JdbcSupport.containsPattern(keyword);
        return JdbcSupport.queryList(conn, sql, ps -> {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, pattern);
            }
        }, CustomerDao::map);
    }

    /** Customers who hold the given account, primary holder first. */
    public List<Customer> findHoldersOfAccount(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT c." + COLUMNS.replace(", ", ", c.") + " FROM customer c "
                + "JOIN account_holder h ON h.customer_id = c.customer_id WHERE h.account_id = ? "
                + "ORDER BY h.holder_role, c.customer_id";
        return JdbcSupport.queryList(conn, sql, ps -> ps.setInt(1, accountId), CustomerDao::map);
    }

    private static void bindForm(PreparedStatement ps, CustomerForm f) throws SQLException {
        ps.setString(1, f.firstName());
        ps.setString(2, f.lastName());
        ps.setDate(3, Date.valueOf(f.dateOfBirth()));
        ps.setString(4, f.gender().name());
        ps.setString(5, f.email());
        ps.setString(6, f.phone());
        ps.setString(7, f.address());
        ps.setString(8, f.city());
        ps.setString(9, f.idProofType().name());
        ps.setString(10, f.idProofNumber());
    }

    private static Customer map(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("customer_id"), rs.getString("first_name"), rs.getString("last_name"),
                JdbcSupport.getDate(rs, "date_of_birth"), Gender.valueOf(rs.getString("gender")),
                rs.getString("email"), rs.getString("phone"), rs.getString("address"), rs.getString("city"),
                IdProofType.valueOf(rs.getString("id_proof_type")), rs.getString("id_proof_number"),
                JdbcSupport.getDateTime(rs, "created_at"), JdbcSupport.getDateTime(rs, "updated_at"));
    }
}
