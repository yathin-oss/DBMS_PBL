-- =============================================================================
-- 08_sql_feature_demo.sql  --  one meaningful example of each required SQL feature
--
--     mysql -u root -t bank_dbms < sql/08_sql_feature_demo.sql
-- (Run as an administrator: section 9 creates a temporary table. Every change in
-- sections 9-10 is rolled back, so the data is left exactly as it was.)
-- =============================================================================

-- 1. SELECT with WHERE, ORDER BY, LIMIT: five largest customer balances
SELECT account_number, primary_holder, balance
FROM v_account_overview
WHERE status <> 'CLOSED'
ORDER BY balance DESC
LIMIT 5;

-- 2. INNER JOIN across four tables: who holds which account, at which branch
SELECT c.first_name, c.last_name, h.holder_role, a.account_number, b.branch_name
FROM customer c
INNER JOIN account_holder h ON h.customer_id = c.customer_id
INNER JOIN account a        ON a.account_id = h.account_id
INNER JOIN branch b         ON b.branch_id = a.branch_id
ORDER BY c.last_name, c.first_name;

-- 3. LEFT JOIN: every customer with the number of loans (customers without loans show 0)
SELECT c.customer_id, CONCAT(c.first_name, ' ', c.last_name) AS customer, COUNT(l.loan_id) AS loans
FROM customer c
LEFT JOIN loan l ON l.customer_id = c.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name
ORDER BY loans DESC, customer;

-- 4. Aggregation: COUNT, SUM, AVG, MIN, MAX of customer balances per product
SELECT at.type_code,
       COUNT(*)              AS accounts,
       SUM(a.balance)        AS total_balance,
       ROUND(AVG(a.balance), 2) AS average_balance,
       MIN(a.balance)        AS smallest,
       MAX(a.balance)        AS largest
FROM account a
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE at.category = 'CUSTOMER'
GROUP BY at.type_code;

-- 5. GROUP BY + HAVING: accounts with more than 10 ledger entries
SELECT a.account_number, COUNT(*) AS entries, SUM(e.amount) AS turnover
FROM transaction_entry e
JOIN account a ON a.account_id = e.account_id
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE at.category = 'CUSTOMER'
GROUP BY a.account_number
HAVING COUNT(*) > 10
ORDER BY entries DESC;

-- 6. Nested (non-correlated) subquery: accounts holding more than the average balance
SELECT account_number, primary_holder, balance
FROM v_account_overview
WHERE balance > (SELECT AVG(balance) FROM v_account_overview WHERE status <> 'CLOSED')
ORDER BY balance DESC;

-- 7. Correlated subquery: each account's largest single credit
SELECT a.account_number,
       (SELECT MAX(e.amount) FROM transaction_entry e
         WHERE e.account_id = a.account_id AND e.entry_side = 'CREDIT') AS largest_credit
FROM account a
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE at.category = 'CUSTOMER'
ORDER BY largest_credit DESC;

-- 8a. EXISTS: customers who have saved at least one ACTIVE beneficiary
SELECT c.customer_id, c.first_name, c.last_name
FROM customer c
WHERE EXISTS (SELECT 1 FROM beneficiary b WHERE b.customer_id = c.customer_id AND b.status = 'ACTIVE');

-- 8b. NOT EXISTS: customer accounts that have never been debited
SELECT a.account_number
FROM account a
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE at.category = 'CUSTOMER'
  AND NOT EXISTS (SELECT 1 FROM transaction_entry e WHERE e.account_id = a.account_id AND e.entry_side = 'DEBIT');

-- 8c. IN with a subquery: customers who are borrowers
SELECT first_name, last_name FROM customer
WHERE customer_id IN (SELECT customer_id FROM loan);

-- 8d. CASE: classify accounts by balance band
SELECT account_number, balance,
       CASE WHEN balance = 0          THEN 'ZERO'
            WHEN balance < 10000      THEN 'LOW'
            WHEN balance < 100000     THEN 'MEDIUM'
            ELSE 'HIGH' END AS balance_band
FROM v_account_overview
ORDER BY balance;

-- 8e. UNION ALL: the trial balance (view) totals, proving debits = credits
SELECT 'TOTAL' AS ledger_account, SUM(debit_balance) AS debits, SUM(credit_balance) AS credits
FROM v_trial_balance
UNION ALL
SELECT ledger_account, debit_balance, credit_balance FROM v_trial_balance;

-- 8f. Window function (MySQL 8): running balance recomputed from entries matches balance_after
SELECT le.account_number, le.posted_at, le.reference_no,
       SUM(CASE WHEN le.entry_side = 'CREDIT' THEN le.amount ELSE -le.amount END)
           OVER (PARTITION BY le.account_id ORDER BY le.entry_id) AS recomputed_balance,
       le.balance_after
FROM v_ledger_entry le
WHERE le.account_number = (SELECT account_number FROM v_account_overview WHERE primary_holder = 'Arjun Nair')
ORDER BY le.entry_id;

-- 8g. Views: the reporting layer (list of views defined in 03_views.sql)
SELECT table_name AS view_name FROM information_schema.views
WHERE table_schema = DATABASE() ORDER BY table_name;

-- 9. INSERT / UPDATE / DELETE (on a scratch copy; financial tables are never deleted from)
START TRANSACTION;
CREATE TEMPORARY TABLE demo_branch_contacts AS SELECT branch_id, branch_name, phone FROM branch;
INSERT INTO demo_branch_contacts (branch_id, branch_name, phone) VALUES (99, 'Demo Branch', '02000000099');
UPDATE demo_branch_contacts SET phone = '02000000100' WHERE branch_id = 99;
DELETE FROM demo_branch_contacts WHERE branch_id = 99;
SELECT COUNT(*) AS rows_after_insert_update_delete FROM demo_branch_contacts;
DROP TEMPORARY TABLE demo_branch_contacts;
ROLLBACK;

-- 10. Transactions: atomic transfer in plain SQL with COMMIT/ROLLBACK and a SAVEPOINT.
--     (The application does the same through JDBC; here we roll everything back.)
START TRANSACTION;
SET @src = (SELECT account_id FROM v_account_overview WHERE primary_holder = 'Vikram Singh' AND type_code = 'SAVINGS');
SET @dst = (SELECT account_id FROM v_account_overview WHERE primary_holder = 'Ananya Iyer');
SELECT account_id, balance FROM account WHERE account_id IN (@src, @dst) ORDER BY account_id FOR UPDATE;
UPDATE account SET balance = balance - 1000.00 WHERE account_id = @src;   -- debit
SAVEPOINT after_debit;
UPDATE account SET balance = balance + 1000.00 WHERE account_id = @dst;   -- credit
ROLLBACK TO SAVEPOINT after_debit;                                         -- undo only the credit
SELECT account_id, balance AS inside_transaction FROM account WHERE account_id IN (@src, @dst);
ROLLBACK;                                                                  -- undo everything
SELECT account_id, balance AS after_rollback FROM account WHERE account_id IN (@src, @dst);

-- 11. Constraints at work: the database itself rejects a negative balance
--     (expected error 3819 "Check constraint 'chk_account_balance' is violated"; uncomment to try)
-- UPDATE account SET balance = -1 WHERE account_id = @src;

-- 12. Indexes: EXPLAIN shows the statement query using idx_entry_account_txn
EXPLAIN SELECT entry_id, amount, balance_after
FROM transaction_entry
WHERE account_id = @src
ORDER BY transaction_id;

-- ...and the audit date-range query using idx_txn_posted_at
EXPLAIN SELECT transaction_id, reference_no
FROM bank_transaction
WHERE posted_at >= '2026-08-01' AND posted_at < '2026-09-01';
