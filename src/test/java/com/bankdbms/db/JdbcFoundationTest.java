package com.bankdbms.db;

import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.DuplicateRecordException;
import com.bankdbms.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcFoundationTest {

    private TransactionRunner tx;

    @BeforeAll
    void setUp() {
        TestDatabase.rebuild();
        tx = new TransactionRunner(new ConnectionFactory(TestDatabase.config()));
    }

    @Test
    @DisplayName("TC-JDBC-01 connects to MySQL as the least-privilege application user")
    void connectsAsApplicationUser() {
        String[] info = tx.readOnly(conn -> {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT CURRENT_USER(), DATABASE(), VERSION()")) {
                rs.next();
                return new String[]{rs.getString(1), rs.getString(2), rs.getString(3)};
            }
        });
        assertTrue(info[0].startsWith("bank_app@"), "connected as " + info[0]);
        assertEquals("bank_dbms_test", info[1]);
        System.out.println("TC-JDBC-01 evidence: user=" + info[0] + " db=" + info[1] + " server=" + info[2]);
    }

    @Test
    @DisplayName("TC-JDBC-02 session time zone matches the JVM (fixes audit defect D-02)")
    void sessionTimeZoneMatchesJvm() {
        LocalDateTime dbNow = tx.readOnly(conn -> {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT NOW()")) {
                rs.next();
                return rs.getTimestamp(1).toLocalDateTime();
            }
        });
        long drift = Math.abs(Duration.between(dbNow, LocalDateTime.now()).getSeconds());
        assertTrue(drift < 5, "DB NOW() differs from JVM time by " + drift + "s");
    }

    @Test
    @DisplayName("TC-JDBC-03 application user has no DELETE privilege (financial rows cannot be deleted)")
    void applicationUserCannotDelete() throws SQLException {
        try (Connection conn = TestDatabase.app(); Statement st = conn.createStatement()) {
            SQLException e = assertThrows(SQLException.class, () -> st.executeUpdate("DELETE FROM bank_transaction"));
            assertEquals(1142, e.getErrorCode(), "expected 'command denied' but got " + e.getMessage());
        }
    }

    @Test
    @DisplayName("TC-JDBC-04 TransactionRunner rolls back every statement when the work fails")
    void rollsBackOnFailure() {
        assertThrows(IllegalStateException.class, () -> tx.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO charge_type (charge_code, charge_name, default_amount) "
                        + "VALUES ('ROLLBACK_PROBE', 'Probe', 1.00)");
            }
            throw new IllegalStateException("simulated failure after the insert");
        }));
        long rows = tx.readOnly(conn -> {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM charge_type WHERE charge_code = 'ROLLBACK_PROBE'")) {
                rs.next();
                return rs.getLong(1);
            }
        });
        assertEquals(0, rows);
    }

    @Test
    @DisplayName("TC-JDBC-05 database errors are translated to friendly messages without SQL text")
    void translatesErrors() {
        DuplicateRecordException dup = assertThrows(DuplicateRecordException.class, () -> tx.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO charge_type (charge_code, charge_name, default_amount) "
                        + "VALUES ('SMS_ALERT_FEE', 'Dup', 1.00)");
            }
            return null;
        }));
        assertEquals("A charge type with this code already exists.", dup.getMessage());
        BusinessRuleException trigger = assertThrows(BusinessRuleException.class, () -> tx.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("UPDATE transaction_entry SET amount = amount + 1");
            }
            return null;
        }));
        assertFalse(trigger.getMessage().toUpperCase().contains("UPDATE "), trigger.getMessage());
        assertTrue(trigger.getMessage().contains("immutable"));
    }
}
