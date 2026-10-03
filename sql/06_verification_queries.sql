-- =============================================================================
-- 06_verification_queries.sql  --  ledger and data-integrity invariants
--
-- Each check returns the NUMBER OF VIOLATIONS; every value must be 0.
-- Invariant IDs refer to docs/03_relational_design.md section 5.
-- The same query is run by the Java test suite (LedgerInvariantsTest).
-- =============================================================================

SELECT 'I1 transaction debits = credits, >= 2 entries' AS invariant, COUNT(*) AS violations
FROM (SELECT t.transaction_id
        FROM bank_transaction t
        LEFT JOIN transaction_entry e ON e.transaction_id = t.transaction_id
       GROUP BY t.transaction_id
      HAVING COUNT(e.entry_id) < 2
          OR SUM(CASE WHEN e.entry_side = 'DEBIT' THEN e.amount ELSE 0 END)
          <> SUM(CASE WHEN e.entry_side = 'CREDIT' THEN e.amount ELSE 0 END)) x
UNION ALL
SELECT 'I2 account balance = signed net of entries', COUNT(*)
FROM (SELECT a.account_id
        FROM account a
        JOIN account_type at ON at.account_type_id = a.account_type_id
        LEFT JOIN transaction_entry e ON e.account_id = a.account_id
       GROUP BY a.account_id, a.balance, at.normal_side
      HAVING a.balance <> COALESCE(SUM(CASE WHEN e.entry_side = at.normal_side THEN e.amount
                                            ELSE -e.amount END), 0)) x
UNION ALL
SELECT 'I2b latest balance_after = account balance', COUNT(*)
FROM account a
JOIN transaction_entry e ON e.account_id = a.account_id
WHERE e.entry_id = (SELECT MAX(e2.entry_id) FROM transaction_entry e2 WHERE e2.account_id = a.account_id)
  AND e.balance_after <> a.balance
UNION ALL
SELECT 'I3 trial balance: debit-normal = credit-normal', COUNT(*)
FROM (SELECT SUM(debit_balance) AS dr, SUM(credit_balance) AS cr FROM v_trial_balance) tb
WHERE tb.dr <> tb.cr
UNION ALL
SELECT 'I5 reversed <-> exactly one reversal', COUNT(*)
FROM bank_transaction t
WHERE (t.status = 'REVERSED')
   <> EXISTS (SELECT 1 FROM bank_transaction r WHERE r.reversal_of_id = t.transaction_id)
UNION ALL
SELECT 'I6a loan schedule principal sums to loan principal', COUNT(*)
FROM loan l
WHERE l.principal <> (SELECT SUM(s.principal_component) FROM loan_schedule s WHERE s.loan_id = l.loan_id)
UNION ALL
SELECT 'I6b total outstanding principal = LOAN_RECEIVABLE balance', COUNT(*)
FROM (SELECT COALESCE(SUM(outstanding_principal), 0) AS outstanding FROM v_loan_position) lp
JOIN account a ON a.account_number = '900000000002'
WHERE lp.outstanding <> a.balance
UNION ALL
SELECT 'I7a transfer = 1 debit + 1 credit on 2 customer accounts', COUNT(*)
FROM transfer tr
WHERE (SELECT COUNT(*) FROM transaction_entry e
         JOIN account a ON a.account_id = e.account_id
         JOIN account_type at ON at.account_type_id = a.account_type_id
        WHERE e.transaction_id = tr.transaction_id AND at.category = 'CUSTOMER') <> 2
   OR (SELECT COUNT(DISTINCT e.entry_side) FROM transaction_entry e
        WHERE e.transaction_id = tr.transaction_id) <> 2
   OR (SELECT COUNT(*) FROM transaction_entry e WHERE e.transaction_id = tr.transaction_id) <> 2
UNION ALL
SELECT 'I7b charge debits a customer account and credits FEE_INCOME', COUNT(*)
FROM charge ch
WHERE NOT EXISTS (SELECT 1 FROM transaction_entry e JOIN account a ON a.account_id = e.account_id
                   WHERE e.transaction_id = ch.transaction_id AND e.entry_side = 'CREDIT'
                     AND a.account_number = '900000000003')
   OR NOT EXISTS (SELECT 1 FROM transaction_entry e
                    JOIN account a ON a.account_id = e.account_id
                    JOIN account_type at ON at.account_type_id = a.account_type_id
                   WHERE e.transaction_id = ch.transaction_id AND e.entry_side = 'DEBIT'
                     AND at.category = 'CUSTOMER')
UNION ALL
SELECT 'I8 repayment amount = scheduled instalment amount', COUNT(*)
FROM v_loan_repayment_report rr
JOIN loan_schedule s ON s.loan_id = rr.loan_id AND s.installment_no = rr.installment_no
WHERE rr.amount_paid <> s.installment_amount
UNION ALL
SELECT 'I9 every customer account has exactly one PRIMARY holder', COUNT(*)
FROM account a
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE at.category = 'CUSTOMER'
  AND (SELECT COUNT(*) FROM account_holder h
        WHERE h.account_id = a.account_id AND h.holder_role = 'PRIMARY') <> 1
UNION ALL
SELECT 'I10 holders within product max_holders', COUNT(*)
FROM account a
JOIN account_type at ON at.account_type_id = a.account_type_id
WHERE (SELECT COUNT(*) FROM account_holder h WHERE h.account_id = a.account_id) > at.max_holders
UNION ALL
SELECT 'I11 current status = latest status history entry', COUNT(*)
FROM account a
WHERE a.status <> (SELECT h.new_status FROM account_status_history h
                    WHERE h.account_id = a.account_id
                    ORDER BY h.history_id DESC LIMIT 1)
   OR NOT EXISTS (SELECT 1 FROM account_status_history h WHERE h.account_id = a.account_id);
