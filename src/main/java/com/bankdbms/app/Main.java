package com.bankdbms.app;

import com.bankdbms.config.DbConfig;
import com.bankdbms.db.ConnectionFactory;
import com.bankdbms.exception.BankException;
import com.bankdbms.ui.ConsoleIO;
import com.bankdbms.ui.MainMenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Entry point of the operator console.
 *
 * <p>Technical details (SQL states, stack traces) go only to {@code logs/bank-dbms.log}; the console
 * shows operator-friendly messages. Credentials are never logged.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        configureLogging();
        ConsoleIO io = new ConsoleIO();
        AppContext app;
        try {
            DbConfig config = DbConfig.load();
            checkConnection(config);
            app = new AppContext(config);
        } catch (BankException e) {
            io.error(e.getMessage());
            System.exit(1);
            return;
        }
        try {
            new MainMenu(app, io).run();
        } catch (ConsoleIO.InputClosedException e) {
            io.println();
            io.println("Input closed. Goodbye.");
        }
    }

    /** Verifies the database is reachable; throws a friendly BankException otherwise. */
    public static void checkConnection(DbConfig config) {
        try (Connection conn = new ConnectionFactory(config).open()) {
            if (!conn.isValid(5)) {
                throw new SQLException("Connection is not valid");
            }
            Logger.getLogger(Main.class.getName()).info("Connected to " + config.databaseName() + " as " + config.user());
        } catch (SQLException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Database connection failed: SQLState="
                    + e.getSQLState() + " code=" + e.getErrorCode(), e);
            throw new BankException("Cannot connect to the database '" + config.databaseName()
                    + "'. Check that MySQL is running and that db.properties is correct (details in logs/bank-dbms.log).");
        }
    }

    /** Sends technical logs to logs/bank-dbms.log only (never to the operator's screen). */
    public static void configureLogging() {
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
        try {
            Files.createDirectories(Path.of("logs"));
            FileHandler file = new FileHandler("logs/bank-dbms.log", true);
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.INFO);
            root.addHandler(file);
            root.setLevel(Level.INFO);
        } catch (IOException e) {
            root.setLevel(Level.OFF);
        }
    }
}
