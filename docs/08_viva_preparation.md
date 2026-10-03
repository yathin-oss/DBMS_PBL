# 08 — Viva Preparation

Every answer refers to something that exists in this repository, so it can be shown live.

## A. Database design

**Q1. Why is a transaction stored as a header plus entries instead of one row per movement?**
- **What was wrong with one row:** in the Review 1 design, the direction was implied by the transaction type, so "debits = credits" could not be checked.
- **What we do now:** `bank_transaction` (header: reference, type, status, time) owns two or more `transaction_entry` rows. Each entry is an explicit DEBIT or CREDIT on one account.
- **What it gives us:** invariant I1 (Σ debits = Σ credits per transaction) becomes a simple GROUP BY/HAVING query (`06_verification_queries.sql`).
- **Normalization:** this is also the 2NF decomposition. In the "movement" relation, `reference_no → type, posted_at, narration` was a partial dependency on the key (reference, account, side). See 03 §2.1.

**Q2. Why are there "internal accounts"?**
- **The problem:** a cash deposit only touches one customer account, so on its own it can't balance.
- **The solution:** the bank's books are five accounts: CASH and LOAN_RECEIVABLE (assets, debit-normal); FEE_INCOME and INTEREST_INCOME (income, credit-normal); CAPITAL (equity, credit-normal). A deposit is DR CASH / CR customer.
- **What it buys:** the trial balance (`v_trial_balance`) must balance, which is invariant I3.
- **Why it isn't over-engineering:** it is five seed rows and one `normal_side` column on `account_type`. There is no general ledger subsystem.

**Q3. Prove the schema is in 3NF.**
- **Derivation:** we started from two unnormalized documents, the account ledger sheet and the loan card, and normalized them step by step (03 §2). For example:
  - repeating groups of holders and movements were removed (1NF);
  - customer attributes depend only on customer_id, so they left account_holder (2NF);
  - `account → type_code → min_balance` was a transitive dependency, so the product moved to `account_type` (3NF).
- **Final check (03 §3):** every non-trivial FD's left side is a candidate key. That makes every relation BCNF, which is stronger than the required 3NF.

**Q4. `account.balance` can be computed from the entries. Isn't that redundant?**
- **Yes, deliberately.** It is redundancy across relations, not an FD violation inside a relation.
- **Why we keep it:**
  - it is the row we lock (`SELECT … FOR UPDATE`) to serialise postings;
  - it is the target of `CHECK (balance >= 0)`, a constraint that cannot be written on a SUM.
- **How it stays correct:** only the posting engine changes it, in the same transaction as the entry. Invariant I2 checks balance = signed net of entries after every integration test (03 §5).

**Q5. Why does `loan` not store the EMI or a status?**
- **EMI:** it depends on (principal, rate, tenure), which is a transitive dependency. The final instalment also differs from the EMI because of rounding. Each instalment's amount is stored instead.
- **Status:** loan ACTIVE/CLOSED and instalment PAID/OVERDUE are derived in views (`v_loan_installment_status`, `v_loan_position`).
- **Why OVERDUE must be derived:** it depends on today's date, so a stored value goes stale. The earlier implementation had exactly this bug (audit defect D-03).

**Q6. How does the database itself enforce "the borrower must hold the account"?**
- `loan(account_id, customer_id)` is a **composite foreign key** to `account_holder(account_id, customer_id)`, the primary key of the holding.
- A non-holder borrower is rejected with error 1452 (test TC-DB-07).

**Q7. How do you guarantee exactly one PRIMARY holder?**
- `account_holder.primary_account_id` is a stored generated column: it equals `account_id` when the role is PRIMARY, otherwise NULL.
- It has a UNIQUE constraint. UNIQUE allows many NULLs but only one non-NULL value per account (TC-DB-05).

**Q8. How can a `transfer` row only reference a TRANSFER transaction?**
- `bank_transaction` has UNIQUE(transaction_id, transaction_type).
- `transfer` carries `transaction_type` fixed by CHECK to 'TRANSFER', with a composite FK to that pair (TC-DB-08).
- The same pattern is used for charge, loan_repayment and the loan's disbursement.

**Q9. Why isn't Statement a table?**
- It would duplicate ledger rows and could go out of sync (Review 1 decision).
- A statement is derived from the ledger:
  - **opening balance:** a subquery for the last `balance_after` before the period;
  - **lines and running balance:** `v_ledger_entry`;
  - **closing balance:** the last line.
- It must satisfy opening + credits − debits = closing (TC-RPT-01, TC-REG-03).

**Q10. Which constraints did you use, and why are some rules in Java instead?**
- **In the database:** PK, FK (19, all RESTRICT), UNIQUE, NOT NULL, CHECK (39, including REGEXP formats and cross-column rules such as `status='CLOSED' ⇔ closed_at IS NOT NULL`), DEFAULT, generated columns, triggers.
- **In Java, with reasons:**
  - **Age ≥ 18 and "date of birth not in the future":** these need `CURRENT_DATE`, which MySQL forbids in CHECK.
  - **Debits = credits:** a cross-row rule, and MySQL has no deferred constraints.
  - **Holder limits:** a cross-row count.
- The full map is in 03 §7.

**Q11. Why these indexes?**
- **What InnoDB already gives us:** indexes on the PK, every UNIQUE constraint and every FK.
- **The 7 we added, each tied to a query:**
  - `idx_entry_account_txn`: statements;
  - `idx_txn_posted_at`: audit by date;
  - `idx_txn_type_posted`: audit filtered by type;
  - `idx_customer_name`: customer search;
  - `idx_schedule_due_date`: overdue instalments;
  - `idx_history_account_time`: lifecycle history;
  - `idx_loan_customer`: loans of a customer.
- **Evidence:** `EXPLAIN` shows the first two in use (04 §4).
- **Not indexed:** `account.status`, because only 4 values make it too low in selectivity.

## B. Transactions and concurrency

**Q12. Walk through a transfer. What makes it atomic?**
- `TransactionRunner.inTransaction` opens one connection with `setAutoCommit(false)`. Every statement then runs on that connection:
  1. lock both accounts (lower id first);
  2. check the beneficiary;
  3. check status and minimum balance;
  4. insert the header and both entries;
  5. update both balances;
  6. insert the transfer row.
- It COMMITs only if the work returns normally. Any exception means ROLLBACK.
- **Evidence:** TC-TRF-09 and TC-TRF-10 inject a database failure after the debit entry, and after both entries. Balances, headers, entries and transfer rows are all unchanged afterwards (05 §3).

**Q13. What stops two simultaneous withdrawals from overdrawing an account?**
- `SELECT … FOR UPDATE` takes an exclusive row lock that lasts until commit. The second withdrawal waits, then reads the already-reduced balance, so there is no lost update.
- `CHECK (balance >= 0)` is the last line of defence.

**Q14. Can transfers deadlock?**
- Not on account locks: every operation locks its accounts in ascending `account_id` order, so a wait cycle cannot form.
- TC-TRF-11 runs 80 opposite-direction transfers on 8 threads with no deadlock.
- If MySQL ever reports one (error 1213), the translator tells the operator to retry.

**Q15. What isolation level do you use?**
- InnoDB's default, REPEATABLE READ. Correctness comes from the explicit locking reads, not from the isolation level alone.
- `08_sql_feature_demo.sql` §10 demonstrates START TRANSACTION, FOR UPDATE, SAVEPOINT, ROLLBACK TO SAVEPOINT and ROLLBACK in plain SQL.

**Q16. How do you correct a wrong transaction if nothing can be edited?**
- A REVERSAL transaction mirrors every entry of the original, and the original's status changes POSTED → REVERSED, both in one transaction.
- **Database enforcement:**
  - triggers allow only that single change;
  - `reversal_of_id` is UNIQUE, so a transaction can be reversed only once;
  - a CHECK ties REVERSAL type to the presence of `reversal_of_id`.
- Even the administrator cannot DELETE ledger rows (TC-DB-18).

## C. Application

**Q17. Why plain JDBC and no Spring or Hibernate?**
- The course is about the database. Every SQL statement is visible in a DAO and every transaction boundary is visible in a service, which an ORM would hide.
- `AppContext` wires 32 objects by constructor in one readable file.

**Q18. How do you prevent SQL injection?**
- Every query that includes user input uses a `PreparedStatement` with bind parameters.
- LIKE search text has its `%` and `_` escaped (TC-CUS-06 searches for "%" and gets no rows).
- The only `Statement.execute` is in `SqlScriptRunner`, for the project's own script files.

**Q19. How are errors shown to the operator?**
- Services throw typed exceptions with safe messages.
- `SqlErrorTranslator` turns MySQL errors into the same types using constraint names, for example duplicate `uq_customer_email` → "A customer with this email address already exists."
- The console prints only the message. Stack traces go to `logs/bank-dbms.log`, which never contains the password.

**Q20. Why does the application user have no DELETE privilege?**
- Least privilege: financial data is never deleted, and beneficiaries are deactivated.
- `bank_app` has SELECT, INSERT and UPDATE only. Its DELETE attempt fails with error 1142 (TC-JDBC-03).
- An administrator installs schema objects, which also avoids MySQL's binary-logging restriction on trigger creation (06 §5, defect 2).

**Q21. How is money handled?**
- `DECIMAL(15,2)` in MySQL and `BigDecimal` (scale 2, HALF_EVEN) in Java. There is no `double` anywhere.
- Inputs with more than two decimals are rejected, not rounded (TC-VAL-02).

**Q22. Explain the EMI calculation.**
- **Formula:** EMI = P·r·(1+r)^n / ((1+r)^n − 1), with r = annual rate / 1200, rounded HALF_EVEN.
- **Each month:** interest = round(outstanding × r); principal = EMI − interest.
- **Final instalment:** its principal is whatever remains, so the principals sum exactly to P (TC-LC-03 checks 6 cases, invariant I6a checks the database).
- **Example:** 1,00,000 at 12% over 12 months gives EMI 8,884.88 and first-month interest 1,000.00 (TC-LC-01/02).

## D. Testing and honesty

**Q23. How do you know the tests aren't just testing mocks?**
- Integration tests run the real services against a real MySQL database, rebuilt from the same `/sql` scripts as production, connected as the restricted user.
- After every test, all 13 ledger invariants are re-checked.
- The test catalogue is generated from the Surefire XML reports (`docs/evidence`).

**Q24. What is not verified?**
- Running on MySQL 8.0/8.4 or on Windows (only tested on MySQL 26.7.0 on macOS).
- Automated UI tests (one scripted console session is recorded).
- Load beyond the 8-thread concurrency test.

**Q25. What would a real bank add?**
- Authentication and roles (maker-checker), interest accrual, term deposits, external payments through a clearing account, per-branch cash tills instead of one CASH account, partial loan payments and penalties.
- Replication, backups and audit logs of operator actions.
