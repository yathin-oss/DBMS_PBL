# Project State

Last updated: 2026-10-03 — **all gates (1–14) completed.**

## Current phase
Final audit complete ([10_final_audit.md](10_final_audit.md)). Waiting for the project owner's review.

**Git:**
- Commit `3c627e4` (console version) exists locally.
- The GUI work is **not committed yet**.
- No remote is configured.

## Completed
| Gate | Result |
|---|---|
| 1 Repository audit | Audit of the earlier implementation (`~/Downloads/bank-dbms`, not copied in) |
| 2 Domain + ER | [01](01_requirements.md), [02](02_er_model.md), rendered ER diagram |
| 3 Relational + 3NF | [03](03_relational_design.md); data dictionary cross-checked against the live schema (15/15 tables) |
| 4 Database | `sql/00–06`; 15 tables, 39 CHECK, 19 FK, 7 indexes, 6 triggers, 11 views; TC-DB-01..20 |
| 5 SQL + reports | `sql/07–08`, [04](04_sql_and_reports.md), EXPLAIN evidence |
| 6 JDBC foundation | Real connection as `bank_app` verified (TC-JDBC-01..05) |
| 7 Core modules | Customers, branches, products, accounts, holders, beneficiaries |
| 8 Financial operations | Posting engine, deposit, withdrawal, transfer (rollback + concurrency tests), charges, reversals |
| 9 Loans | EMI schedule, repayments, derived outstanding/overdue |
| 10 Statements + reports | 7 required + trial balance, charge and daily summaries |
| 11 Validation + errors | `Validator`, `SqlErrorTranslator`, safe logging |
| 12 Testing | 152/152 PASS, catalogue generated from Surefire XML |
| 13 Documentation | README and docs 01–10, evidence folder |
| 14 Final audit | [10](10_final_audit.md) |
| GUI (owner request) | Swing desktop application as the main interface (`java -jar`); console kept (`--console`); TC-GUI-01..05; screenshots in docs/screenshots |

## Approved decisions
See [01 §6](01_requirements.md#6-approved-decisions-log).

## Proposed assumptions adopted without explicit review
The owner said "complete the whole project on your own". These are documented and easy to revisit:
- no fixed-deposit product;
- internal beneficiaries only;
- loan transactions are not reversible;
- minimum-balance rule for customer-initiated debits only;
- loan status, overdue, EMI and outstanding amount are derived;
- ledger immutability triggers;
- age ≥ 18 for holders and borrowers;
- least-privilege app user without DELETE.

## Known bugs
None known. All 152 tests pass, and all 13 invariants hold on the sample data.

## Known risks
| Risk | Mitigation / status |
|---|---|
| Evaluation machine runs MySQL 8.0/8.4 or Windows (only 26.7.0 on macOS tested) | SQL avoids post-8.0 syntax (CHECK and REGEXP_LIKE need 8.0.16+). **NOT VERIFIED** on other versions. |
| MySQL root has no password on this machine (Homebrew default, localhost only) | The application never uses root. Set a root password before any shared use (`mysql_secure_installation`). |
| Scratch database `bank_design_probe` (Gate 3 design probe) still exists on the local server | Harmless, holds no project data. Remove with `DROP DATABASE bank_design_probe;` if desired (not done automatically: destructive). |
| Overdue counts in the sample data grow as real time passes (derived from CURRENT_DATE) | Intended behaviour, documented in `05_sample_data.sql` and the user guide |
| CASH account lock serialises all cash operations | Documented limitation (05 §4); fine for a single operator |

## Test status
152 automated tests: **152 PASS, 0 FAIL, 0 NOT VERIFIED** (final run, `mvn clean package`). Scripted console session recorded.

## Documentation status
README and docs 01–10, traceability matrix and evidence are all current with the final code.

## Security status
- No credentials in tracked files (`db.properties` files gitignored, not packaged in the jar).
- The app user has least privilege (no DELETE/DDL).
- No secrets in logs or evidence (checked).
- PreparedStatement for all user input.
