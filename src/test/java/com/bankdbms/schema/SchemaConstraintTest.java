package com.bankdbms.schema;

import com.bankdbms.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the database itself is the final integrity boundary: each statement below bypasses the
 * Java services entirely and must still be rejected by a PK, FK, UNIQUE, NOT NULL, CHECK,
 * generated-column or trigger rule.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaConstraintTest {

    private static final int DUPLICATE = 1062;
    private static final int FK_MISSING = 1452;
    private static final int CHECK = 3819;
    private static final int NOT_NULL = 1048;
    private static final int SIGNAL = 1644;

    @BeforeAll
    void setUp() throws SQLException {
        TestDatabase.rebuild();
        // A minimal valid customer account to violate against.
        exec("INSERT INTO branch (branch_id, ifsc_code, branch_name, address, city, phone) "
                + "VALUES (2, 'TEST0000002', 'Constraint Branch', 'Road', 'Pune', '02000000002')");
        exec("INSERT INTO customer (customer_id, first_name, last_name, date_of_birth, gender, email, phone, address, "
                + "city, id_proof_type, id_proof_number) VALUES (1, 'Asha', 'Rao', '1990-01-01', 'FEMALE', "
                + "'asha@example.com', '9800000001', 'Street', 'Pune', 'PAN', 'ABCDE1234F')");
        exec("INSERT INTO customer (customer_id, first_name, last_name, date_of_birth, gender, email, phone, address, "
                + "city, id_proof_type, id_proof_number) VALUES (2, 'Ravi', 'Rao', '1991-01-01', 'MALE', "
                + "'ravi@example.com', '9800000002', 'Street', 'Pune', 'PAN', 'ABCDE1234G')");
        exec("INSERT INTO account (account_id, account_number, branch_id, account_type_id) VALUES (10, '100000000010', 2, 1)");
        exec("INSERT INTO account_holder (account_id, customer_id, holder_role) VALUES (10, 1, 'PRIMARY')");
    }

    @Test
    @DisplayName("TC-DB-01 DEFAULT: new account is ACTIVE with balance 0.00 and an opening timestamp")
    void defaults() throws SQLException {
        try (Connection c = TestDatabase.app(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT status, balance, opened_at FROM account WHERE account_id = 10")) {
            rs.next();
            assertEquals("ACTIVE", rs.getString(1));
            assertEquals("0.00", rs.getBigDecimal(2).toPlainString());
            assertTrue(rs.getTimestamp(3) != null);
        }
    }

    @Test
    @DisplayName("TC-DB-02 UNIQUE: duplicate customer email is rejected")
    void uniqueEmail() {
        assertRejected(DUPLICATE, "INSERT INTO customer (first_name, last_name, date_of_birth, gender, email, phone, "
                + "address, city, id_proof_type, id_proof_number) VALUES ('X', 'Y', '1990-01-01', 'MALE', "
                + "'asha@example.com', '9800000009', 'S', 'C', 'PAN', 'ZZZZZ9999Z')");
    }

    @Test
    @DisplayName("TC-DB-03 UNIQUE: duplicate (ID-proof type, number) is rejected")
    void uniqueIdProof() {
        assertRejected(DUPLICATE, "INSERT INTO customer (first_name, last_name, date_of_birth, gender, email, phone, "
                + "address, city, id_proof_type, id_proof_number) VALUES ('X', 'Y', '1990-01-01', 'MALE', "
                + "'other@example.com', '9800000009', 'S', 'C', 'PAN', 'ABCDE1234F')");
    }

    @Test
    @DisplayName("TC-DB-04 PRIMARY KEY: same customer cannot hold the same account twice")
    void compositePrimaryKey() {
        assertRejected(DUPLICATE, "INSERT INTO account_holder (account_id, customer_id, holder_role) VALUES (10, 1, 'JOINT')");
    }

    @Test
    @DisplayName("TC-DB-05 generated column + UNIQUE: a second PRIMARY holder is rejected")
    void onePrimaryHolder() {
        assertRejected(DUPLICATE, "INSERT INTO account_holder (account_id, customer_id, holder_role) VALUES (10, 2, 'PRIMARY')");
    }

    @Test
    @DisplayName("TC-DB-06 FOREIGN KEY: account at a non-existent branch is rejected")
    void foreignKeyBranch() {
        assertRejected(FK_MISSING, "INSERT INTO account (account_number, branch_id, account_type_id) "
                + "VALUES ('100000000099', 999, 1)");
    }

    @Test
    @DisplayName("TC-DB-07 composite FK: loan borrower who does not hold the account is rejected")
    void loanBorrowerMustHoldAccount() throws SQLException {
        exec("INSERT INTO bank_transaction (transaction_id, reference_no, transaction_type) "
                + "VALUES (500, 'TX20260101LOAN01', 'LOAN_DISBURSEMENT')");
        assertRejected(FK_MISSING, "INSERT INTO loan (loan_number, account_id, customer_id, loan_type, principal, "
                + "annual_interest_rate, tenure_months, disbursement_txn_id) VALUES "
                + "('LN202601010001', 10, 2, 'PERSONAL', 1000.00, 10.00, 12, 500)");
    }

    @Test
    @DisplayName("TC-DB-08 composite FK: a transfer row cannot point at a CHARGE transaction")
    void specialisationMustMatchType() throws SQLException {
        exec("INSERT INTO bank_transaction (transaction_id, reference_no, transaction_type) "
                + "VALUES (501, 'TX20260101CHG001', 'CHARGE')");
        assertRejected(FK_MISSING, "INSERT INTO transfer (transaction_id) VALUES (501)");
    }

    @Test
    @DisplayName("TC-DB-09 CHECK: balance cannot become negative")
    void checkBalance() {
        assertRejected(CHECK, "UPDATE account SET balance = -0.01 WHERE account_id = 10");
    }

    @Test
    @DisplayName("TC-DB-10 CHECK: ledger entry amount must be positive")
    void checkEntryAmount() {
        assertRejected(CHECK, "INSERT INTO transaction_entry (transaction_id, account_id, entry_side, amount, "
                + "balance_after) VALUES (1, 10, 'CREDIT', 0.00, 0.00)");
    }

    @Test
    @DisplayName("TC-DB-11 CHECK: account number must be 12 digits")
    void checkAccountNumber() {
        assertRejected(CHECK, "INSERT INTO account (account_number, branch_id, account_type_id) VALUES ('12AB', 2, 1)");
    }

    @Test
    @DisplayName("TC-DB-12 CHECK: CLOSED status requires closed_at (and vice versa)")
    void checkClosedConsistency() {
        assertRejected(CHECK, "UPDATE account SET status = 'CLOSED' WHERE account_id = 10");
    }

    @Test
    @DisplayName("TC-DB-13 CHECK: customer products must be credit-normal")
    void checkProductNormalSide() {
        assertRejected(CHECK, "INSERT INTO account_type (type_code, type_name, category, normal_side, max_holders) "
                + "VALUES ('BAD_PRODUCT', 'Bad', 'CUSTOMER', 'DEBIT', 1)");
    }

    @Test
    @DisplayName("TC-DB-14 CHECK: loan interest rate above 36% is rejected")
    void checkLoanRate() throws SQLException {
        exec("INSERT INTO bank_transaction (transaction_id, reference_no, transaction_type) "
                + "VALUES (502, 'TX20260101LOAN02', 'LOAN_DISBURSEMENT')");
        assertRejected(CHECK, "INSERT INTO loan (loan_number, account_id, customer_id, loan_type, principal, "
                + "annual_interest_rate, tenure_months, disbursement_txn_id) VALUES "
                + "('LN202601010002', 10, 1, 'PERSONAL', 1000.00, 40.00, 12, 502)");
    }

    @Test
    @DisplayName("TC-DB-15 CHECK: a REVERSAL must reference the original transaction")
    void checkReversalLink() {
        assertRejected(CHECK, "INSERT INTO bank_transaction (reference_no, transaction_type) "
                + "VALUES ('TX20260101REV001', 'REVERSAL')");
    }

    @Test
    @DisplayName("TC-DB-16 NOT NULL: customer without a first name is rejected")
    void notNull() {
        assertRejected(NOT_NULL, "INSERT INTO customer (first_name, last_name, date_of_birth, gender, email, phone, "
                + "address, city, id_proof_type, id_proof_number) VALUES (NULL, 'Y', '1990-01-01', 'MALE', "
                + "'n@example.com', '9800000009', 'S', 'C', 'PAN', 'NNNNN1111N')");
    }

    @Test
    @DisplayName("TC-DB-17 TRIGGER: ledger entries cannot be updated")
    void entriesImmutable() {
        assertRejected(SIGNAL, "UPDATE transaction_entry SET amount = 1.00 WHERE transaction_id = 1");
    }

    @Test
    @DisplayName("TC-DB-18 TRIGGER: transactions cannot be deleted, even by the administrator")
    void transactionsCannotBeDeleted() throws SQLException {
        try (Connection admin = TestDatabase.admin(); Statement st = admin.createStatement()) {
            SQLException e = assertThrows(SQLException.class,
                    () -> st.executeUpdate("DELETE FROM bank_transaction WHERE transaction_id = 1"));
            assertEquals(SIGNAL, e.getErrorCode());
        }
    }

    @Test
    @DisplayName("TC-DB-19 TRIGGER: only POSTED -> REVERSED is allowed on a transaction")
    void onlyStatusReversalAllowed() {
        assertRejected(SIGNAL, "UPDATE bank_transaction SET narration = 'edited' WHERE transaction_id = 1");
    }

    @Test
    @DisplayName("TC-DB-20 TRIGGER: account status history is append-only")
    void historyAppendOnly() {
        assertRejected(SIGNAL, "UPDATE account_status_history SET reason = 'edited' WHERE history_id = 1");
    }

    private static void exec(String sql) throws SQLException {
        try (Connection c = TestDatabase.app(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void assertRejected(int expectedErrorCode, String sql) {
        SQLException e = assertThrows(SQLException.class, () -> exec(sql), "statement was accepted: " + sql);
        assertEquals(expectedErrorCode, e.getErrorCode(), "unexpected error: " + e.getMessage());
    }
}
