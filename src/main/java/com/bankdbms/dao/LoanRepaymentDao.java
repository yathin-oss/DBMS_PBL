package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;

import java.sql.Connection;
import java.sql.SQLException;

public class LoanRepaymentDao {

    public void insert(Connection conn, long transactionId, int loanId, int installmentNo) throws SQLException {
        String sql = "INSERT INTO loan_repayment (transaction_id, transaction_type, loan_id, installment_no) "
                + "VALUES (?, 'LOAN_REPAYMENT', ?, ?)";
        JdbcSupport.update(conn, sql, ps -> {
            ps.setLong(1, transactionId);
            ps.setInt(2, loanId);
            ps.setInt(3, installmentNo);
        });
    }
}
