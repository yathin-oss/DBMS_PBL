-- =============================================================================
-- 04_reference_data.sql  --  data the application needs in every installation
--
-- Products, charge catalogue, the head-office branch, the bank's five internal
-- accounts, and the opening capital transaction that funds the CASH account.
-- Run after 01-03. Explicit IDs are used so the Java code and documentation can
-- rely on them; AUTO_INCREMENT continues after the highest ID.
-- Demo customers/accounts/transactions are in 05_sample_data.sql (optional).
-- =============================================================================

-- Products (customer) and internal account categories. Customer products are
-- credit-normal (BR-06). FIXED_DEPOSIT is intentionally absent (docs/01 section 4).
INSERT INTO account_type
    (account_type_id, type_code, type_name, category, normal_side,
     min_opening_balance, min_balance, max_holders, interest_rate, description)
VALUES
    (1, 'SAVINGS',   'Savings Account',            'CUSTOMER', 'CREDIT', 1000.00, 500.00, 2, 3.50,
        'Individual or joint savings account (max 2 holders)'),
    (2, 'CURRENT',   'Current Account',            'CUSTOMER', 'CREDIT', 5000.00, 2000.00, 4, 0.00,
        'Transactional account for frequent use, no interest (max 4 holders)'),
    (3, 'SALARY',    'Salary Account',             'CUSTOMER', 'CREDIT', 0.00, 0.00, 1, 2.50,
        'Zero-balance single-holder salary account'),
    (4, 'GL_ASSET',  'Internal Asset Account',     'INTERNAL', 'DEBIT',  0.00, 0.00, 0, 0.00,
        'Bank-owned asset (cash, loans receivable)'),
    (5, 'GL_INCOME', 'Internal Income Account',    'INTERNAL', 'CREDIT', 0.00, 0.00, 0, 0.00,
        'Bank income (fees, interest)'),
    (6, 'GL_EQUITY', 'Internal Equity Account',    'INTERNAL', 'CREDIT', 0.00, 0.00, 0, 0.00,
        'Bank capital');

INSERT INTO charge_type (charge_type_id, charge_code, charge_name, default_amount) VALUES
    (1, 'SMS_ALERT_FEE',        'Quarterly SMS alert fee',        25.00),
    (2, 'CHEQUE_BOOK_FEE',      'Cheque book issue fee',          150.00),
    (3, 'STATEMENT_COPY_FEE',   'Duplicate statement fee',        100.00),
    (4, 'ANNUAL_MAINTENANCE_FEE','Annual account maintenance fee', 500.00);

-- Head office hosts the internal accounts.
INSERT INTO branch (branch_id, ifsc_code, branch_name, address, city, phone, created_at) VALUES
    (1, 'BDBS0000001', 'Head Office', '1 Bank Street, Fort', 'Mumbai', '02222000001', '2026-01-01 09:00:00');

-- The five internal accounts (account numbers 9000000000xx are reserved for them).
INSERT INTO account (account_id, account_number, branch_id, account_type_id, balance, status, opened_at) VALUES
    (1, '900000000001', 1, 4, 0.00, 'ACTIVE', '2026-01-01 09:00:00'),   -- CASH            (asset)
    (2, '900000000002', 1, 4, 0.00, 'ACTIVE', '2026-01-01 09:00:00'),   -- LOAN_RECEIVABLE (asset)
    (3, '900000000003', 1, 5, 0.00, 'ACTIVE', '2026-01-01 09:00:00'),   -- FEE_INCOME      (income)
    (4, '900000000004', 1, 5, 0.00, 'ACTIVE', '2026-01-01 09:00:00'),   -- INTEREST_INCOME (income)
    (5, '900000000005', 1, 6, 0.00, 'ACTIVE', '2026-01-01 09:00:00');   -- CAPITAL         (equity)

INSERT INTO account_status_history (account_id, old_status, new_status, reason, changed_at) VALUES
    (1, NULL, 'ACTIVE', 'Internal account created: CASH',            '2026-01-01 09:00:00'),
    (2, NULL, 'ACTIVE', 'Internal account created: LOAN_RECEIVABLE', '2026-01-01 09:00:00'),
    (3, NULL, 'ACTIVE', 'Internal account created: FEE_INCOME',      '2026-01-01 09:00:00'),
    (4, NULL, 'ACTIVE', 'Internal account created: INTEREST_INCOME', '2026-01-01 09:00:00'),
    (5, NULL, 'ACTIVE', 'Internal account created: CAPITAL',         '2026-01-01 09:00:00');

-- Opening capital: DR CASH 50,000,000 / CR CAPITAL 50,000,000, posted exactly
-- as the Java posting engine would (header + balanced entries + balances).
INSERT INTO bank_transaction (transaction_id, reference_no, transaction_type, status, narration, posted_at) VALUES
    (1, 'TX20260101CAP001', 'CAPITAL_INJECTION', 'POSTED', 'Opening share capital', '2026-01-01 09:00:00');

INSERT INTO transaction_entry (transaction_id, account_id, entry_side, amount, balance_after) VALUES
    (1, 1, 'DEBIT',  50000000.00, 50000000.00),
    (1, 5, 'CREDIT', 50000000.00, 50000000.00);

UPDATE account SET balance = 50000000.00 WHERE account_id IN (1, 5);
