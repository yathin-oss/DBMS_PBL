-- =============================================================================
-- 03_views.sql  --  reporting and derived-data views
--
-- Every report reads stored facts and derives the rest at query time (loan
-- status, overdue instalments, outstanding principal, transaction totals), so
-- reports can never disagree with the ledger.
-- Run as administrator after 01_schema.sql.
--
-- Sign convention on customer statements: DEBIT = money out of the account,
-- CREDIT = money into the account (the bank's books, which is what a printed
-- bank statement also shows).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Ledger line with its transaction header (base for statements and audit).
-- -----------------------------------------------------------------------------
CREATE VIEW v_ledger_entry AS
SELECT e.entry_id,
       e.transaction_id,
       t.reference_no,
       t.transaction_type,
       t.status                                                   AS transaction_status,
       t.posted_at,
       t.narration,
       e.account_id,
       a.account_number,
       e.entry_side,
       CASE WHEN e.entry_side = 'DEBIT'  THEN e.amount END        AS debit_amount,
       CASE WHEN e.entry_side = 'CREDIT' THEN e.amount END        AS credit_amount,
       e.amount,
       e.balance_after
FROM transaction_entry e
JOIN bank_transaction t ON t.transaction_id = e.transaction_id
JOIN account a          ON a.account_id = e.account_id;

-- -----------------------------------------------------------------------------
-- FR-32  Account balances: every customer account with branch, product and
-- primary holder. Holder count via a correlated subquery.
-- -----------------------------------------------------------------------------
CREATE VIEW v_account_overview AS
SELECT a.account_id,
       a.account_number,
       b.branch_id,
       b.branch_name,
       at.type_code,
       at.type_name,
       a.status,
       a.balance,
       at.min_balance,
       a.opened_at,
       a.closed_at,
       c.customer_id                                              AS primary_customer_id,
       CONCAT(c.first_name, ' ', c.last_name)                     AS primary_holder,
       (SELECT COUNT(*) FROM account_holder h2
         WHERE h2.account_id = a.account_id)                      AS holder_count
FROM account a
JOIN account_type at  ON at.account_type_id = a.account_type_id AND at.category = 'CUSTOMER'
JOIN branch b         ON b.branch_id = a.branch_id
JOIN account_holder h ON h.account_id = a.account_id AND h.holder_role = 'PRIMARY'
JOIN customer c       ON c.customer_id = h.customer_id;

-- -----------------------------------------------------------------------------
-- FR-33  Transaction audit: one row per transaction with balanced totals,
-- accounts debited/credited, and reversal links in both directions.
-- -----------------------------------------------------------------------------
CREATE VIEW v_transaction_audit AS
SELECT t.transaction_id,
       t.reference_no,
       t.transaction_type,
       t.status,
       t.posted_at,
       t.narration,
       SUM(CASE WHEN e.entry_side = 'DEBIT'  THEN e.amount ELSE 0 END) AS total_debit,
       SUM(CASE WHEN e.entry_side = 'CREDIT' THEN e.amount ELSE 0 END) AS total_credit,
       COUNT(*)                                                      AS entry_count,
       GROUP_CONCAT(CASE WHEN e.entry_side = 'DEBIT'  THEN a.account_number END
                    ORDER BY e.entry_id SEPARATOR ', ')              AS debit_accounts,
       GROUP_CONCAT(CASE WHEN e.entry_side = 'CREDIT' THEN a.account_number END
                    ORDER BY e.entry_id SEPARATOR ', ')              AS credit_accounts,
       orig.reference_no                                             AS reverses_reference,
       rev.reference_no                                              AS reversed_by_reference
FROM bank_transaction t
JOIN transaction_entry e       ON e.transaction_id = t.transaction_id
JOIN account a                 ON a.account_id = e.account_id
LEFT JOIN bank_transaction orig ON orig.transaction_id = t.reversal_of_id
LEFT JOIN bank_transaction rev  ON rev.reversal_of_id = t.transaction_id
GROUP BY t.transaction_id, t.reference_no, t.transaction_type, t.status, t.posted_at, t.narration,
         orig.reference_no, rev.reference_no;

-- -----------------------------------------------------------------------------
-- FR-34  Transfers: source/destination/amount are derived from the entries
-- (self-join of transaction_entry on the DEBIT and CREDIT sides).
-- -----------------------------------------------------------------------------
CREATE VIEW v_transfer_report AS
SELECT t.transaction_id,
       t.reference_no,
       t.posted_at,
       t.status,
       d.account_id                                               AS from_account_id,
       fa.account_number                                          AS from_account,
       cr.account_id                                              AS to_account_id,
       ta.account_number                                          AS to_account,
       d.amount,
       tr.beneficiary_id,
       bn.nickname                                                AS beneficiary_nickname,
       CASE WHEN tr.beneficiary_id IS NULL THEN 'OWN_ACCOUNT' ELSE 'BENEFICIARY' END AS transfer_kind,
       tr.remarks
FROM transfer tr
JOIN bank_transaction t   ON t.transaction_id = tr.transaction_id
JOIN transaction_entry d  ON d.transaction_id = t.transaction_id AND d.entry_side = 'DEBIT'
JOIN transaction_entry cr ON cr.transaction_id = t.transaction_id AND cr.entry_side = 'CREDIT'
JOIN account fa           ON fa.account_id = d.account_id
JOIN account ta           ON ta.account_id = cr.account_id
LEFT JOIN beneficiary bn  ON bn.beneficiary_id = tr.beneficiary_id;

-- -----------------------------------------------------------------------------
-- Instalment status, derived (BR-35): PAID if a repayment exists, OVERDUE if
-- unpaid and past due, otherwise PENDING.
-- -----------------------------------------------------------------------------
CREATE VIEW v_loan_installment_status AS
SELECT s.loan_id,
       l.loan_number,
       s.installment_no,
       s.due_date,
       s.principal_component,
       s.interest_component,
       s.installment_amount,
       r.transaction_id                                           AS repayment_txn_id,
       t.reference_no                                             AS repayment_reference,
       t.posted_at                                                AS paid_at,
       CASE WHEN r.transaction_id IS NOT NULL THEN 'PAID'
            WHEN s.due_date < CURRENT_DATE    THEN 'OVERDUE'
            ELSE 'PENDING' END                                    AS installment_status,
       CASE WHEN r.transaction_id IS NULL AND s.due_date < CURRENT_DATE
            THEN DATEDIFF(CURRENT_DATE, s.due_date) ELSE 0 END    AS days_overdue
FROM loan_schedule s
JOIN loan l                ON l.loan_id = s.loan_id
LEFT JOIN loan_repayment r ON r.loan_id = s.loan_id AND r.installment_no = s.installment_no
LEFT JOIN bank_transaction t ON t.transaction_id = r.transaction_id;

-- -----------------------------------------------------------------------------
-- FR-29 / FR-35  Loan position: outstanding principal and loan status derived
-- from the schedule (aggregate over a derived table).
-- -----------------------------------------------------------------------------
CREATE VIEW v_loan_position AS
SELECT l.loan_id,
       l.loan_number,
       l.loan_type,
       l.customer_id,
       CONCAT(c.first_name, ' ', c.last_name)                     AS borrower,
       l.account_id,
       a.account_number,
       a.branch_id,
       l.principal,
       l.annual_interest_rate,
       l.tenure_months,
       DATE(dt.posted_at)                                         AS disbursed_on,
       (SELECT s1.installment_amount FROM loan_schedule s1
         WHERE s1.loan_id = l.loan_id AND s1.installment_no = 1)  AS emi_amount,
       ls.installment_count,
       ls.installments_paid,
       ls.installments_overdue,
       ls.total_interest,
       ls.principal_repaid,
       ls.interest_paid,
       l.principal - ls.principal_repaid                          AS outstanding_principal,
       ls.next_due_date,
       CASE WHEN ls.installments_paid = ls.installment_count THEN 'CLOSED' ELSE 'ACTIVE' END AS loan_status
FROM loan l
JOIN customer c          ON c.customer_id = l.customer_id
JOIN account a           ON a.account_id = l.account_id
JOIN bank_transaction dt ON dt.transaction_id = l.disbursement_txn_id
JOIN (SELECT loan_id,
             COUNT(*)                                                           AS installment_count,
             SUM(installment_status = 'PAID')                                   AS installments_paid,
             SUM(installment_status = 'OVERDUE')                                AS installments_overdue,
             SUM(interest_component)                                            AS total_interest,
             SUM(CASE WHEN installment_status = 'PAID' THEN principal_component ELSE 0 END) AS principal_repaid,
             SUM(CASE WHEN installment_status = 'PAID' THEN interest_component  ELSE 0 END) AS interest_paid,
             MIN(CASE WHEN installment_status <> 'PAID' THEN due_date END)      AS next_due_date
      FROM v_loan_installment_status
      GROUP BY loan_id) ls ON ls.loan_id = l.loan_id;

-- -----------------------------------------------------------------------------
-- FR-36  Loan repayments: the amount actually paid is read from the ledger
-- (the DEBIT entry on the customer account), not copied from the schedule.
-- -----------------------------------------------------------------------------
CREATE VIEW v_loan_repayment_report AS
SELECT r.transaction_id,
       t.reference_no,
       t.posted_at                                                AS paid_at,
       l.loan_id,
       l.loan_number,
       CONCAT(c.first_name, ' ', c.last_name)                     AS borrower,
       r.installment_no,
       s.due_date,
       d.amount                                                   AS amount_paid,
       s.principal_component,
       s.interest_component,
       CASE WHEN DATE(t.posted_at) > s.due_date THEN 'LATE' ELSE 'ON_TIME' END AS timeliness,
       GREATEST(DATEDIFF(DATE(t.posted_at), s.due_date), 0)       AS days_late
FROM loan_repayment r
JOIN bank_transaction t  ON t.transaction_id = r.transaction_id
JOIN loan_schedule s     ON s.loan_id = r.loan_id AND s.installment_no = r.installment_no
JOIN loan l              ON l.loan_id = r.loan_id
JOIN customer c          ON c.customer_id = l.customer_id
JOIN transaction_entry d ON d.transaction_id = r.transaction_id
                        AND d.entry_side = 'DEBIT' AND d.account_id = l.account_id;

-- -----------------------------------------------------------------------------
-- FR-37  Branch summary: customer accounts by status, deposits held, customers
-- served, active loans and outstanding loan principal per branch.
-- -----------------------------------------------------------------------------
CREATE VIEW v_branch_summary AS
SELECT b.branch_id,
       b.branch_name,
       b.city,
       COUNT(ca.account_id)                                       AS total_accounts,
       COALESCE(SUM(ca.status = 'ACTIVE'), 0)                     AS active_accounts,
       COALESCE(SUM(ca.status IN ('FROZEN','BLOCKED')), 0)        AS restricted_accounts,
       COALESCE(SUM(ca.status = 'CLOSED'), 0)                     AS closed_accounts,
       COALESCE(SUM(ca.balance), 0)                               AS total_deposits,
       (SELECT COUNT(DISTINCT h.customer_id)
          FROM account_holder h JOIN account a2 ON a2.account_id = h.account_id
         WHERE a2.branch_id = b.branch_id)                        AS customers_served,
       (SELECT COUNT(*) FROM v_loan_position lp
         WHERE lp.branch_id = b.branch_id AND lp.loan_status = 'ACTIVE') AS active_loans,
       (SELECT COALESCE(SUM(lp.outstanding_principal), 0) FROM v_loan_position lp
         WHERE lp.branch_id = b.branch_id)                        AS loan_outstanding
FROM branch b
LEFT JOIN (SELECT a.account_id, a.branch_id, a.status, a.balance
             FROM account a
             JOIN account_type at ON at.account_type_id = a.account_type_id
            WHERE at.category = 'CUSTOMER') ca ON ca.branch_id = b.branch_id
GROUP BY b.branch_id, b.branch_name, b.city;

-- -----------------------------------------------------------------------------
-- Trial balance (invariant I3): internal accounts individually, customer
-- deposits as one liability line. Total debit column must equal total credit.
-- Internal account roles follow the reserved numbers in 04_reference_data.sql.
-- -----------------------------------------------------------------------------
CREATE VIEW v_trial_balance AS
SELECT CASE a.account_number
            WHEN '900000000001' THEN 'CASH'
            WHEN '900000000002' THEN 'LOAN_RECEIVABLE'
            WHEN '900000000003' THEN 'FEE_INCOME'
            WHEN '900000000004' THEN 'INTEREST_INCOME'
            WHEN '900000000005' THEN 'CAPITAL'
            ELSE a.account_number END                             AS ledger_account,
       at.type_name                                               AS ledger_group,
       at.normal_side,
       CASE WHEN at.normal_side = 'DEBIT'  THEN a.balance ELSE 0 END AS debit_balance,
       CASE WHEN at.normal_side = 'CREDIT' THEN a.balance ELSE 0 END AS credit_balance
FROM account a
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE at.category = 'INTERNAL'
UNION ALL
SELECT 'CUSTOMER_DEPOSITS', 'Customer accounts (liability)', 'CREDIT', 0, COALESCE(SUM(a.balance), 0)
FROM account a
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE at.category = 'CUSTOMER';

-- -----------------------------------------------------------------------------
-- OPTIONAL ENHANCEMENT  Charge summary by type (posted, not reversed).
-- -----------------------------------------------------------------------------
CREATE VIEW v_charge_summary AS
SELECT ct.charge_code,
       ct.charge_name,
       ct.default_amount,
       COUNT(t.transaction_id)                                    AS charges_applied,
       COALESCE(SUM(d.amount), 0)                                 AS total_charged
FROM charge_type ct
LEFT JOIN charge ch            ON ch.charge_type_id = ct.charge_type_id
LEFT JOIN bank_transaction t   ON t.transaction_id = ch.transaction_id AND t.status = 'POSTED'
LEFT JOIN transaction_entry d  ON d.transaction_id = t.transaction_id AND d.entry_side = 'DEBIT'
GROUP BY ct.charge_type_id, ct.charge_code, ct.charge_name, ct.default_amount;

-- -----------------------------------------------------------------------------
-- OPTIONAL ENHANCEMENT  Daily transaction summary by type.
-- -----------------------------------------------------------------------------
CREATE VIEW v_daily_transaction_summary AS
SELECT DATE(ta.posted_at)  AS business_date,
       ta.transaction_type,
       COUNT(*)            AS transaction_count,
       SUM(ta.total_debit) AS total_amount
FROM v_transaction_audit ta
GROUP BY DATE(ta.posted_at), ta.transaction_type;
