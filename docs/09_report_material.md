# 09 — Material for the PBL Report

Draft text for the chapters of the university report template (`PBL Report Template.docx`). Every figure below comes from this repository; adapt the wording to the template's headings.

## Abstract

This project designs and implements a relational database management system for bank account and transaction management, together with a layered Java/JDBC desktop application.

**Database.** The MySQL database models customers, branches, banking products, accounts with joint holders, a double-entry transaction ledger, transfers, beneficiaries, service charges, loans, loan schedules and loan repayments.
- **Normalization:** derived step by step from unnormalized documents to Third Normal Form (in fact BCNF).
- **Integrity:** enforced by 19 foreign keys, 39 CHECK constraints, unique and generated-column rules, and immutability triggers.

**Application.** Every money-moving operation runs as an atomic JDBC transaction through a single posting engine.
- **Posting rules:** the engine locks accounts in a fixed order, enforces account-lifecycle and minimum-balance rules, and records balanced debit and credit entries.
- **Reports:** statements, balances, transaction audit, transfers, loan outstanding, loan repayments and branch summaries, all derived from views.

**Evaluation.** 152 automated tests pass. They cover database constraints, business rules, rollback under injected failures, concurrent transfers, report correctness and the desktop screens. Thirteen ledger invariants (for example, total debits equal total credits) hold after every test and on the delivered sample data.

## 1. Introduction
- **Problem:** manual or spreadsheet tracking cannot stop overdrafts, transfers to non-existent accounts, duplicate account numbers or untraceable balance changes (Review 1).
- **Goal:** "bank-grade thinking at academic-project scale". Realistic banking concepts (double entry, lifecycle, reversal, auditability), implemented completely and tested, rather than a CRUD prototype.

## 2. Objectives
1. A normalized (3NF) relational schema with database-enforced integrity.
2. A layered Java/JDBC application covering every required banking operation.
3. Atomicity of every money-moving operation.
4. A reporting layer using joins, aggregates, subqueries and views.
5. Systematic testing with recorded evidence, and documentation that matches the implementation.

## 3. Scope
See [01 §4](01_requirements.md#4-scope).
- **In scope:** all 37 functional requirements.
- **Out of scope:** GUI, authentication, multi-currency, deposit interest, fixed deposits, external transfers, partial loan repayments.

## 4. Methodology
- **Database-first process:**
  1. requirements;
  2. ER model;
  3. relational schema;
  4. functional dependencies and normalization;
  5. constraints and indexes;
  6. DDL, sample data, SQL and views;
  7. JDBC;
  8. application.
- **Gates:** each phase was a gate with planned, implemented, run, tested and documented steps. Design probes on the real MySQL server checked the riskiest constructs before the DDL was written.

## 5. System design
- **ER model:** [02](02_er_model.md); diagram in `docs/diagrams/er_diagram.png`.
- **Relational schema and normalization:** [03](03_relational_design.md) §1–§3.
- **Data dictionary and constraints:** [03](03_relational_design.md) §4, §7.
- **Ledger and posting rules:** [01 §5.6](01_requirements.md#56-posting-rules); invariants in [03 §5](03_relational_design.md#5-controlled-redundancy-and-the-balance-invariant).
- **Architecture:** [05](05_architecture.md), including the transfer sequence diagram.

## 6. Implementation
- **Size:** 9 SQL scripts and 99 Java source files (about 5,400 lines) in 12 packages, plus 19 test classes (about 2,100 lines). Key classes: `PostingEngine`, `AccountLocker`, `TransactionRunner`, `SqlErrorTranslator`, and the services.
- **Technologies:** Java 17, JDBC (Connector/J 8.4.0), MySQL 8.0.16+, Maven, JUnit 5.
- **Screenshots:** the desktop screens are in [screenshots/](screenshots) (rendered by the GUI test with the sample data). The console session is in [evidence/console_session.txt](evidence/console_session.txt).

## 7. Results
- **Tests:** 152/152 pass ([06](06_testing.md), [catalogue](evidence/test_catalogue_generated.md)).
- **Sample data:** generated through the services. 96 transactions and 199 entries; all 13 invariants report 0 violations.
- **Reports:** output in [evidence/07_reports_output.txt](evidence/07_reports_output.txt).
- **Indexes:** confirmed with EXPLAIN ([04 §4](04_sql_and_reports.md#4-index-evidence)).

## 8. Conclusion
The system meets every mandatory requirement with database-enforced integrity, atomic and auditable financial operations, and reports derived from a balanced ledger. Its design decisions are documented with their functional-dependency and business justification.

## 9. Future work
- Authentication with maker-checker roles.
- Interest accrual and term deposits.
- External payments through a clearing account.
- Per-branch cash tills.
- Partial/advance loan payments and penalties.
- A GUI or web front end on top of the same service layer.
