package com.bankdbms.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes the project's SQL script files through JDBC (used by the database setup command and
 * the test suite, so both install exactly the scripts in {@code /sql}).
 *
 * <p>Understands the subset of mysql-client syntax the scripts use: full-line and trailing
 * {@code --} comments, statements ending with the current delimiter, and {@code DELIMITER}
 * lines (needed for trigger bodies).
 */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    /** Runs a script from the classpath, e.g. {@code sql/01_schema.sql}. */
    public static void runClasspathScript(Connection conn, String resource) throws SQLException {
        try (InputStream in = SqlScriptRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("SQL script not found on classpath: " + resource);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            run(conn, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void run(Connection conn, String script) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String sql : split(script)) {
                st.execute(sql);
            }
        }
    }

    /** Splits a script into executable statements (exposed for unit testing). */
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        String delimiter = ";";
        StringBuilder current = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(script))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String code = stripTrailingComment(line).strip();
                if (code.isEmpty()) {
                    continue;
                }
                if (code.toUpperCase().startsWith("DELIMITER ")) {
                    delimiter = code.substring("DELIMITER ".length()).strip();
                    continue;
                }
                if (code.endsWith(delimiter)) {
                    current.append(code, 0, code.length() - delimiter.length());
                    String sql = current.toString().strip();
                    if (!sql.isEmpty()) {
                        statements.add(sql);
                    }
                    current.setLength(0);
                } else {
                    current.append(code).append('\n');
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().strip());
        }
        return statements;
    }

    /** Removes a {@code -- comment} that is not inside a quoted string. */
    static String stripTrailingComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '-' && !inSingle && !inDouble && i + 1 < line.length() && line.charAt(i + 1) == '-'
                    && (i + 2 == line.length() || Character.isWhitespace(line.charAt(i + 2)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
