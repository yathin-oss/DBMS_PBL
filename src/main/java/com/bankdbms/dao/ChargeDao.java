package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;

import java.sql.Connection;
import java.sql.SQLException;

public class ChargeDao {

    public void insert(Connection conn, long transactionId, int chargeTypeId) throws SQLException {
        String sql = "INSERT INTO charge (transaction_id, transaction_type, charge_type_id) VALUES (?, 'CHARGE', ?)";
        JdbcSupport.update(conn, sql, ps -> {
            ps.setLong(1, transactionId);
            ps.setInt(2, chargeTypeId);
        });
    }
}
