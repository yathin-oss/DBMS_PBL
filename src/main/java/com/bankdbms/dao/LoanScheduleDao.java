package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.LoanInstallment;
import com.bankdbms.util.LoanCalculator.ScheduledInstallment;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class LoanScheduleDao {

    private static final String STATUS_COLUMNS = "loan_id, installment_no, due_date, principal_component, "
            + "interest_component, installment_amount, installment_status, repayment_reference, paid_at, days_overdue";

    /** Batch-inserts the generated schedule (installment_amount is computed by MySQL). */
    public void insertAll(Connection conn, int loanId, List<ScheduledInstallment> rows) throws SQLException {
        String sql = "INSERT INTO loan_schedule (loan_id, installment_no, due_date, principal_component, "
                + "interest_component) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ScheduledInstallment row : rows) {
                ps.setInt(1, loanId);
                ps.setInt(2, row.installmentNo());
                ps.setDate(3, Date.valueOf(row.dueDate()));
                ps.setBigDecimal(4, row.principalComponent());
                ps.setBigDecimal(5, row.interestComponent());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Full schedule with derived status, from view v_loan_installment_status. */
    public List<LoanInstallment> findByLoan(Connection conn, int loanId) throws SQLException {
        return JdbcSupport.queryList(conn, "SELECT " + STATUS_COLUMNS
                        + " FROM v_loan_installment_status WHERE loan_id = ? ORDER BY installment_no",
                ps -> ps.setInt(1, loanId), LoanScheduleDao::map);
    }

    /** Earliest instalment that has no repayment (BR-34). Call after locking the loan row. */
    public Optional<LoanInstallment> findNextUnpaid(Connection conn, int loanId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + STATUS_COLUMNS + " FROM v_loan_installment_status "
                        + "WHERE loan_id = ? AND installment_status <> 'PAID' ORDER BY installment_no LIMIT 1",
                ps -> ps.setInt(1, loanId), LoanScheduleDao::map);
    }

    private static LoanInstallment map(ResultSet rs) throws SQLException {
        return new LoanInstallment(rs.getInt("loan_id"), rs.getInt("installment_no"),
                JdbcSupport.getDate(rs, "due_date"), rs.getBigDecimal("principal_component"),
                rs.getBigDecimal("interest_component"), rs.getBigDecimal("installment_amount"),
                rs.getString("installment_status"), rs.getString("repayment_reference"),
                JdbcSupport.getDateTime(rs, "paid_at"), rs.getInt("days_overdue"));
    }
}
