package com.bankdbms.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptRunnerTest {

    @Test
    @DisplayName("TC-SCR-01 splits statements, strips comments, honours DELIMITER blocks")
    void splitsScript() {
        String script = "-- header comment\n"
                + "CREATE TABLE t (a INT); -- trailing\n"
                + "INSERT INTO t VALUES (1),\n  (2);   -- end of statement\n"
                + "DELIMITER //\n"
                + "CREATE TRIGGER x BEFORE DELETE ON t FOR EACH ROW\nBEGIN\n  SIGNAL SQLSTATE '45000';\nEND//\n"
                + "DELIMITER ;\n"
                + "SELECT '--not a comment';\n";
        List<String> statements = SqlScriptRunner.split(script);
        assertEquals(4, statements.size());
        assertEquals("CREATE TABLE t (a INT)", statements.get(0));
        assertTrue(statements.get(2).contains("SIGNAL SQLSTATE '45000';"));
        assertEquals("SELECT '--not a comment'", statements.get(3));
    }

    @Test
    @DisplayName("TC-SCR-02 the real project scripts split into the expected statement counts")
    void realScripts() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("sql/02_triggers.sql")) {
            assertEquals(6, SqlScriptRunner.split(new String(in.readAllBytes())).size());
        }
    }
}
