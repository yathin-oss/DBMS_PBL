package com.bankdbms.db;

import com.bankdbms.exception.BankException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns JDBC transaction boundaries for the service layer.
 *
 * <p>{@link #inTransaction} gives the work one {@link Connection} with auto-commit off.
 * Every statement in the work (locks, balance updates, ledger inserts) therefore belongs to one
 * database transaction: it is committed only if the work returns normally, and rolled back on
 * <em>any</em> exception. This is what makes a transfer all-or-nothing (NFR-02).
 */
public class TransactionRunner {

    private static final Logger LOG = Logger.getLogger(TransactionRunner.class.getName());

    private final ConnectionFactory connections;

    public TransactionRunner(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** Runs read-write work atomically: COMMIT on success, ROLLBACK on any failure. */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(conn);
                throw translate(e);
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e);
        }
    }

    /** Runs read-only work with auto-commit (each query sees committed data). */
    public <T> T readOnly(SqlWork<T> work) {
        try (Connection conn = connections.open()) {
            conn.setReadOnly(true);
            return work.run(conn);
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e);
        }
    }

    private static RuntimeException translate(Exception e) {
        if (e instanceof SQLException sql) {
            return SqlErrorTranslator.translate(sql);
        }
        if (e instanceof BankException bank) {
            return bank;
        }
        LOG.log(Level.SEVERE, "Unexpected error inside a database transaction (rolled back)", e);
        return (RuntimeException) e;
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            LOG.log(Level.SEVERE, "Rollback failed", rollbackFailure);
        }
    }
}
