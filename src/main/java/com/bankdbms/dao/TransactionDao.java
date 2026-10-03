package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.BankTransaction;
import com.bankdbms.model.EntrySide;
import com.bankdbms.model.LedgerEntry;
import com.bankdbms.model.TransactionStatus;
import com.bankdbms.model.TransactionType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Ledger header (bank_transaction) and line (transaction_entry) access. */
public class TransactionDao {

    private static final String TXN_COLUMNS =
            "transaction_id, reference_no, transaction_type, status, narration, reversal_of_id, posted_at";
    private static final String ENTRY_COLUMNS = "entry_id, transaction_id, account_id, entry_side, amount, balance_after";

    public long insertHeader(Connection conn, String referenceNo, TransactionType type, String narration,
                             Long reversalOfId, LocalDateTime postedAt) throws SQLException {
        String sql = "INSERT INTO bank_transaction (reference_no, transaction_type, status, narration, reversal_of_id, "
                + "posted_at) VALUES (?, ?, 'POSTED', ?, ?, ?)";
        return JdbcSupport.insertReturningKey(conn, sql, ps -> {
            ps.setString(1, referenceNo);
            ps.setString(2, type.name());
            JdbcSupport.setNullableString(ps, 3, narration);
            JdbcSupport.setNullableLong(ps, 4, reversalOfId);
            ps.setTimestamp(5, Timestamp.valueOf(postedAt));
        });
    }

    public long insertEntry(Connection conn, long transactionId, int accountId, EntrySide side, BigDecimal amount,
                            BigDecimal balanceAfter) throws SQLException {
        String sql = "INSERT INTO transaction_entry (transaction_id, account_id, entry_side, amount, balance_after) "
                + "VALUES (?, ?, ?, ?, ?)";
        return JdbcSupport.insertReturningKey(conn, sql, ps -> {
            ps.setLong(1, transactionId);
            ps.setInt(2, accountId);
            ps.setString(3, side.name());
            ps.setBigDecimal(4, amount);
            ps.setBigDecimal(5, balanceAfter);
        });
    }

    /** The only permitted change to a posted transaction (also enforced by trigger trg_txn_guard_update). */
    public int markReversed(Connection conn, long transactionId) throws SQLException {
        return JdbcSupport.update(conn,
                "UPDATE bank_transaction SET status = 'REVERSED' WHERE transaction_id = ? AND status = 'POSTED'",
                ps -> ps.setLong(1, transactionId));
    }

    public Optional<BankTransaction> findById(Connection conn, long transactionId) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + TXN_COLUMNS + " FROM bank_transaction WHERE transaction_id = ?",
                ps -> ps.setLong(1, transactionId), TransactionDao::mapTxn);
    }

    /** Locks the transaction header so two operators cannot reverse it at the same time. */
    public Optional<BankTransaction> findByIdForUpdate(Connection conn, long transactionId) throws SQLException {
        return JdbcSupport.queryOne(conn,
                "SELECT " + TXN_COLUMNS + " FROM bank_transaction WHERE transaction_id = ? FOR UPDATE",
                ps -> ps.setLong(1, transactionId), TransactionDao::mapTxn);
    }

    public Optional<BankTransaction> findByReference(Connection conn, String referenceNo) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + TXN_COLUMNS + " FROM bank_transaction WHERE reference_no = ?",
                ps -> ps.setString(1, referenceNo), TransactionDao::mapTxn);
    }

    public List<LedgerEntry> findEntries(Connection conn, long transactionId) throws SQLException {
        return JdbcSupport.queryList(conn,
                "SELECT " + ENTRY_COLUMNS + " FROM transaction_entry WHERE transaction_id = ? ORDER BY entry_id",
                ps -> ps.setLong(1, transactionId), TransactionDao::mapEntry);
    }

    private static BankTransaction mapTxn(ResultSet rs) throws SQLException {
        return new BankTransaction(rs.getLong("transaction_id"), rs.getString("reference_no"),
                TransactionType.valueOf(rs.getString("transaction_type")),
                TransactionStatus.valueOf(rs.getString("status")), rs.getString("narration"),
                JdbcSupport.getNullableLong(rs, "reversal_of_id"), JdbcSupport.getDateTime(rs, "posted_at"));
    }

    private static LedgerEntry mapEntry(ResultSet rs) throws SQLException {
        return new LedgerEntry(rs.getLong("entry_id"), rs.getLong("transaction_id"), rs.getInt("account_id"),
                EntrySide.valueOf(rs.getString("entry_side")), rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_after"));
    }
}
