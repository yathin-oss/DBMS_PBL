package com.bankdbms.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small helpers shared by all DAOs so that every query uses a {@link PreparedStatement},
 * try-with-resources, and the same mapping style, without repeating JDBC boilerplate.
 */
public final class JdbcSupport {

    /** Sets the parameters of a prepared statement. */
    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    /** Maps the current row of a result set to an object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public static final Binder NO_PARAMS = ps -> { };

    private JdbcSupport() {
    }

    public static <T> List<T> queryList(Connection conn, String sql, Binder binder, RowMapper<T> mapper)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        }
    }

    public static <T> Optional<T> queryOne(Connection conn, String sql, Binder binder, RowMapper<T> mapper)
            throws SQLException {
        List<T> rows = queryList(conn, sql, binder, mapper);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public static int update(Connection conn, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        }
    }

    /** Executes an INSERT and returns the AUTO_INCREMENT key it generated. */
    public static long insertReturningKey(Connection conn, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("INSERT did not return a generated key");
                }
                return keys.getLong(1);
            }
        }
    }

    /** Escapes LIKE wildcards in user input and wraps it as a "contains" pattern. */
    public static String containsPattern(String keyword) {
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    public static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    public static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    public static void setNullableTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    public static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    public static LocalDate getDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    public static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static BigDecimal getMoney(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column);
    }
}
