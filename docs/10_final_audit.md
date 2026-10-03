# 10 — Final Audit (Gate 14)

Audit date: 2026-10-03.

- **Method:** every item below was checked against the running system, the executed tests or a recorded command. It was not judged from memory.
- **Labels:**
  - **PASS:** verified with the evidence named.
  - **NOTE:** acceptable, with a documented limitation.
  - **NOT VERIFIED:** could not be checked in this environment.

## Database
| Check | Result | Evidence |
|---|---|---|
| 3NF (in fact BCNF) | PASS | 03 §2 derivation, §3 FD table; every determinant is a candidate key |
| Documentation matches schema | PASS | Scripted comparison of the 03 §4 data dictionary with `information_schema.columns`: 15/15 tables, identical column lists |
| Constraints | PASS | 39 CHECK, 19 FK (all RESTRICT), UNIQUE, NOT NULL, DEFAULT, 2 generated columns, 6 triggers; each kind rejected in TC-DB-01..20 |
| Indexes | PASS | 7 secondary indexes, each tied to a query (03 §10); EXPLAIN confirms use (04 §4) |
| Integrity of data | PASS | 13 invariants (06_verification_queries.sql) report 0 violations on reference data, on sample data, and after every integration test |
| Sample data | PASS | Generated through the real services; fresh install with `--with-sample-data` passes all invariants (TC-REG-01..04) |

## Application
| Check | Result | Evidence |
|---|---|---|
| Layered architecture, no frameworks | PASS | Packages ui → service → ledger → dao → db; pom has only connector-j and junit |
| DAO layer | PASS | One DAO per table plus ReportDao; `JdbcSupport` gives try-with-resources everywhere |
| Services | PASS | Transaction boundaries in services via `TransactionRunner` |
| Validation | PASS | `Validator` / `Money` before any transaction; database constraints as backstop (TC-VAL, TC-CUS-03, TC-DEP-02...) |
| Error handling | PASS | Typed exceptions; `SqlErrorTranslator` friendly messages (TC-JDBC-05); console never shows stack traces (console_session.txt) |
| Dead code / quality | PASS | Unused methods removed during the audit; compiler lint (`-Xlint:all,-serial`) reports no warnings |

## Banking
| Operation | Result | Evidence |
|---|---|---|
| Deposits | PASS | TC-DEP-01..05 |
| Withdrawals | PASS | TC-WDR-01..05 |
| Transfers | PASS | TC-TRF-01..11 |
| Charges | PASS | TC-CHG-01..04 |
| Reversals | PASS | TC-REV-01..05 |
| Loans | PASS | TC-LOAN-01..10, TC-LC-01..06 |
| Repayments | PASS | TC-LOAN-04..08 |
| Statements | PASS | TC-RPT-01..03, TC-REG-03 (all 11 sample accounts reconcile) |
| Account lifecycle | PASS | TC-ACC-07..10, TC-LIFE-01/02 |

## Transactions
| Check | Result | Evidence |
|---|---|---|
| Atomicity | PASS | Injected failures leave no trace: TC-DEP-05, TC-TRF-09, TC-TRF-10, TC-JDBC-04 |
| Commit / rollback | PASS | `TransactionRunner`; SQL demo §10 (SAVEPOINT, ROLLBACK) |
| Balance consistency | PASS | Invariants I2/I2b/I3 after every test |
| Concurrency | PASS | TC-TRF-11: 80 parallel opposite-direction transfers, no deadlock, no lost update |

## SQL
| Check | Result | Evidence |
|---|---|---|
| Joins (inner, left, multi-table, self) | PASS | 08 §2–3, `v_transfer_report`, `v_transaction_audit` |
| Aggregation, GROUP BY, HAVING | PASS | 08 §4–5, invariant I1 |
| Subqueries (nested, correlated, EXISTS / IN) | PASS | 08 §6–8c |
| CASE, UNION, window function | PASS | 08 §8d–8f |
| Views | PASS | 11 views (04 §1) |
| Reports | PASS | 07_reports.sql output in evidence; Java reports tested (TC-RPT) |

## Testing
| Kind | Result | Evidence |
|---|---|---|
| Positive | PASS | e.g. TC-ACC-01, TC-TRF-01, TC-LOAN-01 |
| Negative | PASS | e.g. TC-DEP-02, TC-TRF-03..07, TC-LOAN-02/05 |
| Integrity | PASS | TC-DB-01..20, invariant hook |
| Transaction | PASS | TC-TRF-09..11, TC-DEP-05 |
| Regression | PASS | TC-REG-01..04 |
| Totals | PASS | 147/147 ([catalogue](evidence/test_catalogue_generated.md), [build log](evidence/maven_build_and_test_output.txt)) |

## Security
| Check | Result | Evidence |
|---|---|---|
| Secrets | PASS | Both `db.properties` gitignored (`git check-ignore`); not in the jar (`unzip -l`); password found 0 times in docs/ and logs/ |
| SQL injection | PASS | All user input bound via PreparedStatement; the only `createStatement` is in `SqlScriptRunner` for trusted scripts; LIKE escaping (TC-CUS-06) |
| Least privilege | PASS | `bank_app`: SELECT/INSERT/UPDATE only (`SHOW GRANTS`); DELETE refused (TC-JDBC-03) |
| Sensitive information | PASS | ID numbers masked in the UI; logs hold SQL state and code only |
| Local MySQL root without password | NOTE | Homebrew default, localhost only; the application never uses root. Recommended: `mysql_secure_installation` |

## Documentation
| Check | Result |
|---|---|
| Accuracy | PASS: figures (table, constraint and test counts, class counts) re-measured during the audit; one wrong count in 06 corrected; minimum MySQL version corrected to 8.0.16 |
| Completeness | PASS: README, requirements, ER, relational/3NF/data dictionary, SQL/reports, architecture, testing, user guide, viva, report material, traceability, state, audit |
| Consistency | PASS: design deviations (time-zone setting) are recorded where they happened (03 §8, 06 §5) |

## Unnecessary complexity review
| Item | Kept? | Reason |
|---|---|---|
| Five internal accounts | Yes | Required to make "debits = credits" checkable; five seed rows |
| Immutability triggers | Yes | Six short triggers enforce "history is never edited", even against direct SQL |
| Composite FKs for specialisation types | Yes | Database-level guarantee; one extra constant column per specialisation |
| Connection pool, ORM, DI framework | No | Not needed at single-operator scale; would hide the JDBC/SQL the course assesses |

## Not verified
- Running on MySQL 8.0.16–8.4 and on Windows (only MySQL 26.7.0 on macOS was available).
- The PBL report document itself (material supplied in 09; formatting into the university template is the student's task).

## Definition of Done
| Criterion | Met |
|---|---|
| All mandatory requirements covered | Yes: FR-01..37, NFR-01..10 VERIFIED (traceability matrix) |
| Database design documented; 3NF justified | Yes |
| DDL verified | Yes: installed by script and by the test suite each run |
| Sample data valid | Yes |
| SQL works; reports work | Yes |
| JDBC works; core Java functionality works | Yes |
| Financial transactions atomic | Yes |
| Validation and error handling work | Yes |
| Tests have actual evidence | Yes |
| Documentation matches implementation | Yes |
| No critical defect | Yes: none known |
| No secrets exposed | Yes |

**Status: COMPLETE** for the defined scope. The NOT VERIFIED items above are environment-compatibility checks, not missing functionality.
