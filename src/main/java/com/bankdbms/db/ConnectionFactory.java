package com.bankdbms.db;

import com.bankdbms.config.DbConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens JDBC connections as the least-privilege application account.
 * A new physical connection per unit of work keeps the code easy to follow; at the scale of
 * a single-operator console application a connection pool would add complexity without benefit.
 */
public class ConnectionFactory {

    private final DbConfig config;

    public ConnectionFactory(DbConfig config) {
        this.config = config;
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    public DbConfig config() {
        return config;
    }
}
