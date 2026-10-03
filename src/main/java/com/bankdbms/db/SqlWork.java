package com.bankdbms.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that runs on a connection supplied by {@link TransactionRunner}. */
@FunctionalInterface
public interface SqlWork<T> {
    T run(Connection connection) throws SQLException;
}
