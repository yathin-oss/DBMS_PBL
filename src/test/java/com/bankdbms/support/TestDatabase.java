package com.bankdbms.support;

import com.bankdbms.config.DbConfig;
import com.bankdbms.db.SqlScriptRunner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Rebuilds the dedicated test database from the project's real SQL scripts.
 *
 * <p>Destructive by design (DROP DATABASE), so it refuses any database whose name does not end in
 * {@code _test}. DDL and triggers are installed with the administrator account; the application
 * under test connects as the least-privilege account from the same settings file.
 */
public final class TestDatabase {

    public static final List<String> INSTALL_SCRIPTS = List.of(
            "sql/01_schema.sql", "sql/02_triggers.sql", "sql/03_views.sql", "sql/04_reference_data.sql");

    private static DbConfig config;

    private TestDatabase() {
    }

    public static synchronized DbConfig config() {
        if (config == null) {
            config = DbConfig.fromClasspath("db.properties");
        }
        return config;
    }

    /** Drops and re-creates the test database, then installs schema, triggers, views and reference data. */
    public static void rebuild(String... extraScripts) {
        DbConfig cfg = config();
        String db = cfg.databaseName();
        if (!db.endsWith("_test")) {
            throw new IllegalStateException("Refusing to rebuild '" + db + "': test database names must end in _test");
        }
        try (Connection admin = adminConnection(cfg.serverUrl());
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + db + "`");
            st.execute("CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not re-create test database " + db, e);
        }
        try (Connection admin = adminConnection(cfg.url())) {
            for (String script : INSTALL_SCRIPTS) {
                SqlScriptRunner.runClasspathScript(admin, script);
            }
            for (String script : extraScripts) {
                SqlScriptRunner.runClasspathScript(admin, script);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not install schema into " + db, e);
        }
    }

    /** Administrator connection to the test database (for tests that need direct SQL). */
    public static Connection admin() throws SQLException {
        return adminConnection(config().url());
    }

    /** Application-account connection (SELECT/INSERT/UPDATE only), for direct constraint tests. */
    public static Connection app() throws SQLException {
        DbConfig cfg = config();
        return DriverManager.getConnection(cfg.url(), cfg.user(), cfg.password());
    }

    private static Connection adminConnection(String url) throws SQLException {
        DbConfig cfg = config();
        if (cfg.adminUser() == null) {
            throw new IllegalStateException("db.admin.user is missing from src/test/resources/db.properties");
        }
        return DriverManager.getConnection(url, cfg.adminUser(), cfg.adminPassword());
    }
}
