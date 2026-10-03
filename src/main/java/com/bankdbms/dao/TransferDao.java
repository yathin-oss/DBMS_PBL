package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;

import java.sql.Connection;
import java.sql.SQLException;

public class TransferDao {

    public void insert(Connection conn, long transactionId, Integer beneficiaryId, String remarks) throws SQLException {
        String sql = "INSERT INTO transfer (transaction_id, transaction_type, beneficiary_id, remarks) "
                + "VALUES (?, 'TRANSFER', ?, ?)";
        JdbcSupport.update(conn, sql, ps -> {
            ps.setLong(1, transactionId);
            JdbcSupport.setNullableInt(ps, 2, beneficiaryId);
            JdbcSupport.setNullableString(ps, 3, remarks);
        });
    }
}
