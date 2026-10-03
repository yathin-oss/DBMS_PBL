-- =============================================================================
-- 07_reports.sql  --  the seven required reports as standalone SQL
--
-- Run against a database with sample data, e.g.
--     mysql -u bank_app -p -t bank_dbms < sql/07_reports.sql
-- Parameters are session variables; change them at the top.
-- The Java application runs the same queries through ReportDao.
-- =============================================================================

SET @account_number = (SELECT account_number FROM v_account_overview
                        WHERE primary_holder = 'Ananya Iyer' LIMIT 1);   -- any 12-digit account number
SET @from_date      = '2026-08-01';
SET @to_date        = '2026-09-30';

-- -----------------------------------------------------------------------------
-- R1  ACCOUNT STATEMENT (FR-31): opening balance, period lines, closing balance
-- -----------------------------------------------------------------------------
SELECT a.account_number,
       o.primary_holder,
       o.type_name,
       @from_date AS period_from,
       @to_date   AS period_to,
       COALESCE((SELECT le.balance_after FROM v_ledger_entry le
                  WHERE le.account_id = a.account_id AND le.posted_at < @from_date
                  ORDER BY le.entry_id DESC LIMIT 1), 0.00) AS opening_balance
FROM account a
JOIN v_account_overview o ON o.account_id = a.account_id
WHERE a.account_number = @account_number;

SELECT le.posted_at, le.reference_no, le.transaction_type, le.narration,
       le.debit_amount  AS debit,
       le.credit_amount AS credit,
       le.balance_after AS balance,
       CASE WHEN le.transaction_status = 'REVERSED' THEN 'reversed' ELSE '' END AS note
FROM v_ledger_entry le
WHERE le.account_number = @account_number
  AND le.posted_at >= @from_date
  AND le.posted_at <  DATE_ADD(@to_date, INTERVAL 1 DAY)
ORDER BY le.entry_id;

SELECT COALESCE(SUM(le.debit_amount), 0)  AS total_debits,
       COALESCE(SUM(le.credit_amount), 0) AS total_credits
FROM v_ledger_entry le
WHERE le.account_number = @account_number
  AND le.posted_at >= @from_date
  AND le.posted_at <  DATE_ADD(@to_date, INTERVAL 1 DAY);

-- -----------------------------------------------------------------------------
-- R2  ACCOUNT BALANCES (FR-32)
-- -----------------------------------------------------------------------------
SELECT account_number, primary_holder, holder_count, branch_name, type_code, status, balance, min_balance,
       CASE WHEN status <> 'CLOSED' AND balance < min_balance THEN 'BELOW MINIMUM' ELSE '' END AS flag
FROM v_account_overview
ORDER BY branch_name, account_number;

-- -----------------------------------------------------------------------------
-- R3  TRANSACTION AUDIT (FR-33): every transaction balanced, with reversal links
-- -----------------------------------------------------------------------------
SELECT reference_no, posted_at, transaction_type, status, total_debit, total_credit, entry_count,
       debit_accounts, credit_accounts, reverses_reference, reversed_by_reference
FROM v_transaction_audit
WHERE posted_at >= @from_date AND posted_at < DATE_ADD(@to_date, INTERVAL 1 DAY)
ORDER BY transaction_id;

-- -----------------------------------------------------------------------------
-- R4  TRANSFERS (FR-34)
-- -----------------------------------------------------------------------------
SELECT reference_no, posted_at, from_account, to_account, amount, transfer_kind, beneficiary_nickname,
       remarks, status
FROM v_transfer_report
WHERE posted_at >= @from_date AND posted_at < DATE_ADD(@to_date, INTERVAL 1 DAY)
ORDER BY transaction_id;

-- -----------------------------------------------------------------------------
-- R5  LOAN OUTSTANDING (FR-29, FR-35)
-- -----------------------------------------------------------------------------
SELECT loan_number, borrower, loan_type, principal, annual_interest_rate, tenure_months, emi_amount,
       installments_paid, installments_overdue, principal_repaid, interest_paid, outstanding_principal,
       next_due_date, loan_status
FROM v_loan_position
ORDER BY loan_status, outstanding_principal DESC;

-- Overdue instalments in detail
SELECT loan_number, installment_no, due_date, installment_amount, days_overdue
FROM v_loan_installment_status
WHERE installment_status = 'OVERDUE'
ORDER BY days_overdue DESC;

-- -----------------------------------------------------------------------------
-- R6  LOAN REPAYMENTS (FR-30, FR-36)
-- -----------------------------------------------------------------------------
SELECT reference_no, paid_at, loan_number, borrower, installment_no, due_date, amount_paid,
       principal_component, interest_component, timeliness, days_late
FROM v_loan_repayment_report
ORDER BY loan_id, installment_no;

-- -----------------------------------------------------------------------------
-- R7  BRANCH SUMMARY (FR-09, FR-37)
-- -----------------------------------------------------------------------------
SELECT branch_name, city, total_accounts, active_accounts, restricted_accounts, closed_accounts,
       customers_served, total_deposits, active_loans, loan_outstanding
FROM v_branch_summary
ORDER BY branch_id;
