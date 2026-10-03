# 04 — SQL, Views and Reports (Gate 5)

All SQL lives in [`/sql`](../sql). The scripts are also packaged on the Java classpath, so the application and the tests use exactly the same files.

| Script | Purpose | Run as |
|---|---|---|
| `00_create_database.sql` | Databases `bank_dbms` and `bank_dbms_test`; least-privilege user `bank_app` (SELECT, INSERT, UPDATE only) | administrator, once |
| `01_schema.sql` | 15 tables, PK / FK / UNIQUE / CHECK / DEFAULT / NOT NULL, 7 secondary indexes | administrator |
| `02_triggers.sql` | 6 immutability triggers on the ledger and status history | administrator |
| `03_views.sql` | 11 reporting / derived-data views | administrator |
| `04_reference_data.sql` | Products, charge catalogue, head office, internal accounts, opening capital | administrator |
| `05_sample_data.sql` | Optional demo data, generated through the real services | administrator |
| `06_verification_queries.sql` | 13 integrity invariants; every result must be 0 | any |
| `07_reports.sql` | The 7 required reports as standalone, parameterised SQL | `bank_app` |
| `08_sql_feature_demo.sql` | One labelled example of every required SQL feature, plus transaction and EXPLAIN demos | administrator |

`scripts/setup_database.sh` runs 01 to 04 (and optionally 05), then 06. Real output of 07 and 08 against the sample data is in [`docs/evidence/`](evidence).

## 1. Views

| View | What it derives | Used by |
|---|---|---|
| `v_ledger_entry` | Entry + transaction header + account number, debit/credit columns | Statements, transaction detail |
| `v_account_overview` | Customer accounts with branch, product, primary holder, holder count (correlated subquery) | FR-32 balances, search |
| `v_transaction_audit` | One row per transaction: total debit/credit, entry count, accounts debited/credited (`GROUP_CONCAT`), reversal links both ways (self-joins) | FR-33 |
| `v_transfer_report` | Source/destination/amount derived from the DEBIT and CREDIT entries (self-join of `transaction_entry`), own-account vs beneficiary | FR-34 |
| `v_loan_installment_status` | PAID / OVERDUE / PENDING and days overdue, derived from repayments and `CURRENT_DATE` | Schedules, FR-35 |
| `v_loan_position` | EMI, instalments paid/overdue, principal repaid, interest paid, outstanding principal, next due date, ACTIVE/CLOSED (aggregate over a derived table) | FR-29, FR-35 |
| `v_loan_repayment_report` | Amount actually paid (from the ledger), principal/interest split, ON_TIME/LATE | FR-30, FR-36 |
| `v_branch_summary` | Accounts by status, deposits held, customers served, active loans, loan outstanding per branch | FR-09, FR-37 |
| `v_trial_balance` | Internal accounts plus customer deposits, by normal side (`UNION ALL`) | Invariant I3 |
| `v_charge_summary` | Charges applied and total charged per charge type (OPTIONAL ENHANCEMENT) | Charge report |
| `v_daily_transaction_summary` | Count and value per day and type (OPTIONAL ENHANCEMENT) | Daily report |

Views contain no business data of their own, so they can never disagree with the tables.

## 2. Required reports

| Report | SQL (07_reports.sql) | Java | Test |
|---|---|---|---|
| Account statement | R1: opening balance subquery + period lines + totals | `ReportService.statement` | TC-RPT-01..03, TC-REG-03 |
| Account balances | R2 | `ReportService.accountBalances` | TC-RPT-04 |
| Transaction audit | R3 | `ReportService.transactionAudit` | TC-RPT-05 |
| Transfers | R4 | `ReportService.transfers` | TC-RPT-06 |
| Loan outstanding | R5 + overdue detail | `ReportService.loanOutstanding` | TC-RPT-07, TC-LOAN-08 |
| Loan repayments | R6 | `ReportService.loanRepayments` | TC-RPT-07, TC-LOAN-07 |
| Branch summary | R7 | `ReportService.branchSummary` | TC-RPT-08 |
| Trial balance (extra) | `v_trial_balance` | `ReportService.trialBalance` | TC-RPT-09 |
| Charge / daily summary (optional) | views | `ReportService.chargeSummary` / `dailySummary` | TC-RPT-10 |

**Statement algorithm (BR-37):**
- **Opening balance:** the `balance_after` of the account's last entry posted before the start date, or 0 if there is none.
- **Lines:** every entry with `posted_at` in [from, to+1 day), in posting order.
- **Closing balance:** the last line's `balance_after`.
- **Check:** the statement verifies `opening + credits − debits = closing`; the console prints the result.

## 3. Required SQL features: where each is demonstrated

| Feature | Example (08_sql_feature_demo.sql section, unless stated) |
|---|---|
| SELECT, WHERE, ORDER BY, LIMIT | §1 top balances |
| INNER JOIN, multi-table join | §2 customer → holder → account → branch |
| LEFT JOIN | §3 customers with loan counts (0 for none); `v_transaction_audit`, `v_branch_summary` |
| COUNT, SUM, AVG, MIN, MAX | §4 per product |
| GROUP BY + HAVING | §5 accounts with more than 10 entries; invariant I1 |
| Nested subquery | §6 balances above average |
| Correlated subquery | §7 largest credit per account; `v_account_overview.holder_count` |
| EXISTS / NOT EXISTS | §8a, §8b; invariants I5, I7b |
| IN (subquery) | §8c borrowers |
| CASE | §8d balance bands; every report view |
| UNION ALL | §8e, `v_trial_balance` |
| Window function | §8f running balance recomputed and compared with `balance_after` |
| Views | §8g, `03_views.sql` |
| INSERT / UPDATE / DELETE | §9, on a temporary copy inside a rolled-back transaction (financial tables are never deleted from) |
| Transactions, COMMIT / ROLLBACK / SAVEPOINT, FOR UPDATE | §10 |
| Constraints rejecting bad data | §11; `SchemaConstraintTest` (20 cases) |
| Indexes and EXPLAIN | §12 (see below) |

## 4. Index evidence

`EXPLAIN` output from [`evidence/08_sql_feature_demo_output.txt`](evidence/08_sql_feature_demo_output.txt), MySQL 26.7.0 with the sample data:

```
-> Index lookup on transaction_entry using idx_entry_account_txn (account_id = (@src))  (cost=1.95 rows=12)
-> Index range scan on bank_transaction using idx_txn_posted_at over
   ('2026-08-01 00:00:00' <= posted_at < '2026-09-01 00:00:00') ...  (cost=5.66 rows=12)
```

The statement query (all entries of one account in posting order) uses `idx_entry_account_txn`. The audit date-range query uses `idx_txn_posted_at`. The rationale for every index is in [03 §10](03_relational_design.md#10-index-strategy).

## 5. Integrity invariants (06_verification_queries.sql)

| ID | Check |
|---|---|
| I1 | Every transaction: Σ debits = Σ credits, at least 2 entries |
| I2 | Every account: balance = signed net of its entries (by normal side) |
| I2b | Latest `balance_after` = account balance |
| I3 | Trial balance: debit-normal balances = credit-normal balances |
| I5 | REVERSED ⇔ exactly one REVERSAL points at it |
| I6a | Loan schedule principal sums to the loan principal |
| I6b | Total outstanding principal = LOAN_RECEIVABLE balance |
| I7a | Each transfer = 1 debit + 1 credit on 2 customer accounts |
| I7b | Each charge debits a customer account and credits FEE_INCOME |
| I8 | Repayment amount = scheduled instalment amount |
| I9 | Every customer account has exactly one PRIMARY holder |
| I10 | Holders within the product's `max_holders` |
| I11 | Current status = latest status-history entry |

These checks run:
- in `scripts/setup_database.sh`;
- in the console (Reports → Ledger integrity check);
- **after every integration test** (`IntegrationTestBase.ledgerInvariantsStillHold`).
