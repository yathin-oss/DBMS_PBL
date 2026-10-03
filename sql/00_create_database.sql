-- =============================================================================
-- 00_create_database.sql  --  run ONCE as a MySQL administrator (e.g. root)
--
-- Creates the application database, the test database, and a least-privilege
-- application account. Schema objects (tables, triggers, views) are installed by
-- the administrator; the application account can only read and write rows.
--
-- Before running, replace CHANGE_ME with a strong password and put the same
-- password in src/main/resources/db.properties (gitignored, never committed).
-- =============================================================================

CREATE DATABASE IF NOT EXISTS bank_dbms
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS bank_dbms_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'bank_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';

-- Least privilege: no DELETE (financial rows are never deleted; beneficiaries are
-- deactivated), no DDL, no TRIGGER. UPDATE is needed for balances, statuses and
-- SELECT ... FOR UPDATE row locking.
GRANT SELECT, INSERT, UPDATE ON bank_dbms.*      TO 'bank_app'@'localhost';
GRANT SELECT, INSERT, UPDATE ON bank_dbms_test.* TO 'bank_app'@'localhost';
