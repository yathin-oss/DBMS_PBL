-- =============================================================================
-- 02_triggers.sql  --  ledger immutability (NFR-09, BR-19)
--
-- Financial history can never be rewritten, even with direct SQL:
--   * transaction_entry and account_status_history are append-only;
--   * bank_transaction rows are never deleted, and the only permitted change is
--     status POSTED -> REVERSED (done when a reversal is posted).
-- Run as administrator after 01_schema.sql. Uses the mysql client DELIMITER
-- command (also understood by the project's Java SqlScriptRunner).
-- =============================================================================

DELIMITER //

CREATE TRIGGER trg_entry_block_update BEFORE UPDATE ON transaction_entry
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ledger entries are immutable and cannot be updated';
END//

CREATE TRIGGER trg_entry_block_delete BEFORE DELETE ON transaction_entry
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ledger entries are immutable and cannot be deleted';
END//

CREATE TRIGGER trg_txn_guard_update BEFORE UPDATE ON bank_transaction
FOR EACH ROW
BEGIN
    IF NOT (OLD.status = 'POSTED' AND NEW.status = 'REVERSED')
       OR NEW.transaction_id <> OLD.transaction_id
       OR NEW.reference_no <> OLD.reference_no
       OR NEW.transaction_type <> OLD.transaction_type
       OR NOT (NEW.narration <=> OLD.narration)
       OR NOT (NEW.reversal_of_id <=> OLD.reversal_of_id)
       OR NEW.posted_at <> OLD.posted_at THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Transactions are immutable; only POSTED -> REVERSED is allowed';
    END IF;
END//

CREATE TRIGGER trg_txn_block_delete BEFORE DELETE ON bank_transaction
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Transactions are immutable and cannot be deleted';
END//

CREATE TRIGGER trg_history_block_update BEFORE UPDATE ON account_status_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Account status history is append-only';
END//

CREATE TRIGGER trg_history_block_delete BEFORE DELETE ON account_status_history
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Account status history is append-only';
END//

DELIMITER ;
