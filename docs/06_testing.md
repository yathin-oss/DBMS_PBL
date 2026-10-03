# 06 — Testing (Gate 12)

## 1. Result

**147 automated tests, 147 PASS, 0 FAIL, 0 NOT VERIFIED.** This is the final run of `mvn clean package` on 2026-10-03.

| Evidence | File |
|---|---|
| Every test with ID, description and status, generated from the Surefire XML reports (not typed by hand) | [evidence/test_catalogue_generated.md](evidence/test_catalogue_generated.md) |
| Full Maven build and test console output | [evidence/maven_build_and_test_output.txt](evidence/maven_build_and_test_output.txt) |
| Output of the 7 report queries | [evidence/07_reports_output.txt](evidence/07_reports_output.txt) |
| Output of the SQL feature demo, including EXPLAIN and the transaction/savepoint demo | [evidence/08_sql_feature_demo_output.txt](evidence/08_sql_feature_demo_output.txt) |
| Scripted end-to-end session of the real console application | [evidence/console_session.txt](evidence/console_session.txt) |

**Environment:** macOS 26.5.2, MySQL 26.7.0 (Homebrew), Connector/J 8.4.0. Maven ran on Java 26.0.2; the bytecode target is Java 17.

**NOT VERIFIED:** behaviour on MySQL 8.0/8.4 and on Windows. The SQL avoids post-8.0 features, but it has only been executed on 26.7.0.

## 2. Strategy

| Level | What | How |
|---|---|---|
| Unit | EMI maths, money/validation rules, lifecycle matrix, script splitting, posting balance check | Plain JUnit 5, no database |
| Database | Every constraint type rejects bad data with no Java involved | `SchemaConstraintTest`: direct SQL as `bank_app` or admin, expecting the exact MySQL error code |
| Integration | Every service against real MySQL as the least-privilege user | `IntegrationTestBase`: rebuilds `bank_dbms_test` from `/sql` per class |
| Transaction | Atomicity under injected failures; concurrency | `PostingFaultInjector` throws a `SQLException` mid-posting; an 8-thread transfer test |
| Invariant | Ledger correctness after **every** integration test | `@AfterEach` runs all 13 checks in `06_verification_queries.sql` |
| Regression | The shipped sample data installs cleanly and the app works on top of it | `SampleDataRegressionTest` |
| Manual / E2E | The real console jar with scripted input | `evidence/console_session.txt` |

**How to run:** see the README. In short, set up `src/test/resources/db.properties` from the example file, then run `mvn test`. The tests DROP and re-create `bank_dbms_test` (guarded: the name must end in `_test`).

## 3. Test areas

| Prefix | Area | Tests |
|---|---|---|
| TC-DB | Schema constraints (PK, FK, UNIQUE, NOT NULL, CHECK, DEFAULT, generated column, triggers) | 20 |
| TC-JDBC | Connection, least privilege, time zone, rollback, error translation | 5 |
| TC-SCR | SQL script runner | 2 |
| TC-LC | Loan calculator | 11 |
| TC-VAL | Money and validation | 17 |
| TC-LIFE / TC-LED | Lifecycle matrix, posting balance rules | 5 |
| TC-CUS, TC-BR, TC-AT | Customers, branches, products | 11 |
| TC-ACC | Accounts, holders, lifecycle, closure | 11 |
| TC-BEN | Beneficiaries | 4 |
| TC-DEP, TC-WDR | Deposits, withdrawals | 15 |
| TC-TRF | Transfers, rollback, concurrency | 13 |
| TC-CHG, TC-REV | Charges, reversals | 9 |
| TC-LOAN | Loans, schedules, repayments, overdue | 10 |
| TC-RPT | Statements and reports | 10 |
| TC-REG | Sample-data regression | 4 |
| **Total** | | **147** (counting each parameterised case separately) |

## 4. Key scenarios in detail

"Actual" is what the executed test observed. Each row is backed by the test named in the ID column.

| Test ID | Input | Expected | Actual | Status |
|---|---|---|---|---|
| TC-TRF-09 | Transfer 3,000.00 between own accounts; injected `SQLException` after the DEBIT entry is inserted | Operator sees "operation was not completed"; both balances, header count, entry count and transfer count unchanged | Exception raised; snapshot before = after; a retry without the fault succeeds | PASS |
| TC-TRF-10 | Same, with the fault after both entries (before COMMIT) | Nothing persisted | Snapshot unchanged | PASS |
| TC-TRF-11 | 8 threads × 10 transfers of 100.00, half A→B and half B→A | No deadlock or lost update; both balances back to 1,00,000.00; 80 transfer rows; invariants hold | As expected | PASS |
| TC-TRF-03 | Transfer to another customer's account with no beneficiary | Rejected (BR-25); nothing changes | `BusinessRuleException`; snapshot unchanged | PASS |
| TC-TRF-05 | Savings balance 1,500.00 (minimum 500.00), transfer 1,000.01 | Insufficient funds; nothing changes | `InsufficientFundsException`; snapshot unchanged | PASS |
| TC-TRF-08 | FROZEN source / FROZEN destination / BLOCKED destination | Debit refused / credit accepted / credit refused | As expected | PASS |
| TC-DEP-02 | Deposit 0, 0.00, −100, 10.555 | `ValidationException`; no transaction rows | As expected (4 cases) | PASS |
| TC-DEP-05 | Deposit with an injected database failure | Balance and CASH unchanged, no entries | As expected | PASS |
| TC-WDR-02 | Balance 1,500.00, withdraw 1,000.01 then 1,000.00 | First refused (minimum balance 500.00), second leaves exactly 500.00 | As expected | PASS |
| TC-ACC-02 | Savings opening deposit 999.99 (minimum 1,000.00) | Rejected; account count unchanged | As expected | PASS |
| TC-ACC-09 | Close an ACTIVE account holding 3,500.00 | Payout WITHDRAWAL posted, status CLOSED, closed_at set; cannot reopen | As expected | PASS |
| TC-LOAN-01 | 1,00,000.00 at 12% for 12 months | EMI 8,884.88; 12 instalments; LOAN_RECEIVABLE +1,00,000 | As expected | PASS |
| TC-LOAN-04 | Repay instalment 1 with 8,884.88 | Outstanding 92,115.12; INTEREST_INCOME +1,000.00; 3 ledger entries | As expected | PASS |
| TC-LOAN-05 | Repay 8,000.00 instead of 8,884.88 | Rejected; nothing changes | As expected | PASS |
| TC-LOAN-08 | Loan disbursed about 4 months ago, nothing repaid | 4 instalments OVERDUE; late payment reported as LATE | As expected | PASS |
| TC-REV-04 | Reverse a deposit after 900 of 1,000 was withdrawn | Refused (cannot go negative); no REVERSAL row | As expected | PASS |
| TC-RPT-01 | Statement 12 May – 2 Jun for a known history | Opening 10,000.00; 4 lines; debits 1,525.00; credits 6,500.00; closing 14,975.00 = balance | As expected | PASS |
| TC-DB-07 | Direct INSERT of a loan whose borrower does not hold the account | MySQL error 1452 (composite FK to account_holder) | 1452 | PASS |
| TC-DB-18 | Administrator runs `DELETE FROM bank_transaction` | Trigger SIGNAL 1644 | 1644 | PASS |
| TC-JDBC-03 | `bank_app` runs `DELETE FROM bank_transaction` | Privilege error 1142 | 1142 | PASS |
| TC-REG-03 | Statement of every sample account from 2026-01-01 to today | opening + credits − debits = closing = stored balance | All 11 accounts reconcile | PASS |

## 5. Defects found during development (and fixed)

| # | Found by | Defect | Fix |
|---|---|---|---|
| 1 | TC-JDBC-01 (first run) | `forceConnectionTimeZoneToSession=true` makes the driver send `SET time_zone='Asia/Kolkata'`, which fails on MySQL without time-zone tables | Use `connectionTimeZone=LOCAL` only; all business timestamps are written from Java (03 §8) |
| 2 | MySQL error 1419 during setup | Binary logging stops non-SUPER users creating triggers | Split roles: the admin installs DDL/triggers; the app user has SELECT/INSERT/UPDATE only |
| 3 | First sample-data run | Event order bug (beneficiary added before its account existed); the loader also moved the clock backwards, which would make statement order disagree with time order | Loader rewritten as a sorted event timeline |
| 4 | Data review | `customer.created_at` and `branch.created_at` used the server clock, not the business clock | DAOs write `created_at` from `BankClock` |
| 5 | Test review | `db.properties` under `src/main/resources` would have been packaged into the jar with its password | Runtime config moved to the gitignored project root; tests load their own classpath file |
| 6 | Code review | Charge override validated twice; dead helper methods; an `IllegalArgumentException` from bad enum input in the UI would have shown as an "unexpected error" | Cleaned up; enum input now raises `ValidationException` |
| 7 | MoneyAndValidatorTest | Test expected `Money.parse` to normalise scale | Test corrected (parsing keeps the exact input; `requirePositive` normalises) |

## 6. Known limitations of the test suite

- Tests run on one machine and one MySQL version (26.7.0). Compatibility with MySQL 8.0/8.4 and Windows is **NOT VERIFIED**.
- The console UI is exercised by one scripted end-to-end session, not by automated UI tests. All the logic it calls is covered by service tests.
- The concurrency test proves no lost updates or deadlocks for two hot accounts. It is not a load test.
