package com.bankdbms.config;

import com.bankdbms.exception.BankException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Database connection settings. Credentials never live in source code.
 *
 * <p>Lookup order (first match wins for each value):
 * <ol>
 *   <li>environment variables {@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD};</li>
 *   <li>the file named by system property {@code bankdbms.config};</li>
 *   <li>{@code ./db.properties} in the working directory;</li>
 *   <li>{@code db.properties} on the classpath (used by the test suite).</li>
 * </ol>
 *
 * <p>The JDBC URL is completed with session time-zone settings so that timestamps written
 * by MySQL defaults and date ranges built in Java use the same zone (fixes the earlier
 * implementation's UTC/local mismatch, audit defect D-02).
 */
public record DbConfig(String url, String user, String password, String adminUser, String adminPassword) {

    private static final String TIME_ZONE_PARAMS = "connectionTimeZone=LOCAL";

    @Override
    public String toString() {
        return "DbConfig[url=" + url + ", user=" + user + ", password=***]";
    }

    public static DbConfig load() {
        return from(readProperties());
    }

    /** Loads settings from a classpath resource only (used by the test suite: src/test/resources). */
    public static DbConfig fromClasspath(String resource) {
        Properties props = new Properties();
        try (InputStream in = DbConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new BankException("Settings file " + resource + " not found on the classpath.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new BankException("Could not read " + resource + ".", e);
        }
        return from(props);
    }

    private static DbConfig from(Properties file) {
        String url = pick("DB_URL", file, "db.url");
        String user = pick("DB_USER", file, "db.user");
        String password = pick("DB_PASSWORD", file, "db.password");
        if (url == null || user == null || password == null) {
            throw new BankException("Database settings are missing. Copy db.properties.example to db.properties "
                    + "and fill in db.url, db.user and db.password (or set DB_URL / DB_USER / DB_PASSWORD).");
        }
        return new DbConfig(withTimeZoneParams(url), user, password,
                file.getProperty("db.admin.user"), file.getProperty("db.admin.password", ""));
    }

    /** Database (schema) name taken from the JDBC URL, e.g. bank_dbms_test. */
    public String databaseName() {
        String path = url.substring(url.indexOf("//") + 2);
        int slash = path.indexOf('/');
        if (slash < 0) {
            throw new BankException("The database URL does not name a database.");
        }
        String rest = path.substring(slash + 1);
        int query = rest.indexOf('?');
        return query < 0 ? rest : rest.substring(0, query);
    }

    /** JDBC URL of the server without a default database (used for CREATE/DROP DATABASE). */
    public String serverUrl() {
        String base = url.substring(0, url.indexOf('/', url.indexOf("//") + 2) + 1);
        int query = url.indexOf('?');
        return query < 0 ? base : base + url.substring(query);
    }

    static String withTimeZoneParams(String url) {
        if (url.contains("connectionTimeZone")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + TIME_ZONE_PARAMS;
    }

    private static String pick(String envName, Properties file, String key) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String value = file.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Properties readProperties() {
        Properties props = new Properties();
        String explicit = System.getProperty("bankdbms.config");
        Path path = explicit != null ? Path.of(explicit) : Path.of("db.properties");
        try {
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                return props;
            }
            try (InputStream in = DbConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (in != null) {
                    props.load(in);
                }
            }
        } catch (IOException e) {
            throw new BankException("Could not read the database settings file.", e);
        }
        return props;
    }
}
