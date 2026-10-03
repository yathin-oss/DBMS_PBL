# Bank Account and Transaction Management System

**DBMS Project 55: Design and Implementation of a Database Management System for Bank Account and Transaction Management.**

A MySQL database and a layered Java/JDBC console application that simulate the core of a retail bank:
- customers, branches and banking products;
- accounts with joint holders and a controlled lifecycle;
- deposits, withdrawals, atomic transfers and beneficiaries;
- service charges and reversals;
- loans with EMI schedules and repayments;
- statements and reports.

Design approach: *bank-grade thinking at academic-project scale*. Every financial event is a double-entry ledger transaction (debits = credits), history is never edited or deleted, and the database enforces integrity itself.

| | |
|---|---|
| Database | MySQL 8.0.16+ (developed and tested on 26.7.0) — 15 tables in 3NF/BCNF, 39 CHECK constraints, 19 foreign keys, 7 secondary indexes, 6 triggers, 11 views |
| Application | Java 17+ (plain JDBC, no frameworks), Maven, Connector/J 8.4.0 |
| Tests | 147 JUnit 5 tests (unit, database-constraint, integration, rollback, concurrency, regression): **147 pass** |

## 1. Features

- **Customers, branches, products:** create, read, update, search, with validation (email, phone, IFSC, age ≥ 18 for holders/borrowers, masked ID numbers).
- **Accounts:**
  - opening with a product-specific minimum deposit, posted to the ledger in the same transaction;
  - joint holders up to the product limit;
  - lifecycle ACTIVE / FROZEN / BLOCKED / CLOSED, with a status history;
  - closure pays out the remaining balance.
- **Money movement:** deposit, withdrawal, transfer (own accounts, or to an ACTIVE beneficiary), service charges. All of it goes through one posting engine that locks accounts, enforces lifecycle and minimum-balance rules, and commits or rolls back as a unit.
- **Corrections:** reversal transactions; the original is marked REVERSED and never edited.
- **Loans:** disbursement with a reducing-balance EMI schedule; repayment of the exact next instalment, split into principal and interest; derived outstanding amount, overdue instalments and loan status.
- **Reports:** account statement, account balances, transaction audit, transfers, loan outstanding, loan repayments, branch summary, trial balance, charge summary, daily summary, and a one-click ledger integrity check (13 invariants).

## 2. Project structure

```
sql/                 00 create DB + app user · 01 schema · 02 triggers · 03 views · 04 reference data
                     05 sample data · 06 integrity checks · 07 report queries · 08 SQL feature demo
scripts/             setup_database.sh · export_sample_data.sh
src/main/java/com/bankdbms/
  app/               Main (console entry point), AppContext (wiring)
  ui/                console menus
  service/           business rules and transaction boundaries
  ledger/            PostingEngine, AccountLocker (double-entry posting, locking)
  dao/               JDBC data access (PreparedStatement only)
  db/                connections, TransactionRunner, error translation, script runner
  model/ report/ util/ exception/ config/ tools/
src/test/java/...    147 tests
docs/                design, SQL, architecture, testing, user guide, viva, report material, audit
docs/evidence/       real outputs: test catalogue, build log, report/demo output, console session
```

## 3. Setup

### 3.1 Prerequisites
- **MySQL 8.0.16 or newer (CHECK constraints are only enforced from 8.0.16)**, running locally.
- **JDK 17 or newer, and Maven 3.8+.** The first build downloads the dependencies from Maven Central.

### 3.2 Create the databases and the application user (once, as a MySQL administrator)
1. Edit `sql/00_create_database.sql` and replace `CHANGE_ME` with a password.
2. Run it:
   ```bash
   mysql -u root -p < sql/00_create_database.sql
   ```
The application user `bank_app` gets only `SELECT, INSERT, UPDATE` (no DELETE, no DDL).

### 3.3 Install the schema (as administrator)
```bash
# schema + triggers + views + reference data, then the integrity check
scripts/setup_database.sh                       # empty bank: internal accounts only
scripts/setup_database.sh --with-sample-data    # plus 7 months of demo activity (recommended)
```
`MYSQL_ADMIN_USER` (default `root`) and `MYSQL_PWD` select the admin account. The script refuses to touch a database that already has tables unless you pass `--reset`.

Without bash, run the scripts manually in this order:
```bash
mysql -u root -p bank_dbms < sql/01_schema.sql
mysql -u root -p bank_dbms < sql/02_triggers.sql
mysql -u root -p bank_dbms < sql/03_views.sql
mysql -u root -p bank_dbms < sql/04_reference_data.sql
mysql -u root -p bank_dbms < sql/05_sample_data.sql      # optional
mysql -u root -p -t bank_dbms < sql/06_verification_queries.sql
```

### 3.4 Configure the application
```bash
cp db.properties.example db.properties            # then set db.password (gitignored)
```
Alternatively, set `DB_URL`, `DB_USER` and `DB_PASSWORD` as environment variables.

### 3.5 Build and run
```bash
mvn clean package            # compiles, runs all 147 tests, builds target/bank-dbms.jar
java -jar target/bank-dbms.jar
```
To build without running the tests: `mvn -DskipTests package`.

## 4. Running the tests

The integration tests rebuild a dedicated test database from `/sql`.

1. Create the test settings file:
   ```bash
   cp src/test/resources/db.properties.example src/test/resources/db.properties
   ```
   Set `db.password` (the `bank_app` password) and the administrator account (`db.admin.user` / `db.admin.password`). The admin account is used only to rebuild `bank_dbms_test`.
2. Run the tests:
   ```bash
   mvn test
   ```

The test database name must end in `_test`, and the suite refuses to rebuild anything else. Results: `target/surefire-reports/`. The documented run is in `docs/06_testing.md` and `docs/evidence/`.

## 5. Documentation

| Document | Contents |
|---|---|
| [docs/01_requirements.md](docs/01_requirements.md) | Requirements (FR/NFR), scope, 37 business rules, posting rules, decisions log |
| [docs/02_er_model.md](docs/02_er_model.md) | Entities, keys, relationships, cardinalities, ER diagram |
| [docs/03_relational_design.md](docs/03_relational_design.md) | Relational schema, UNF→1NF→2NF→3NF, functional dependencies, data dictionary, constraints, indexes, invariants |
| [docs/04_sql_and_reports.md](docs/04_sql_and_reports.md) | Views, the 7 reports, SQL feature map, EXPLAIN evidence |
| [docs/05_architecture.md](docs/05_architecture.md) | Layers, transaction flow, locking, error handling, security |
| [docs/06_testing.md](docs/06_testing.md) | Strategy, results, key scenarios, defects found |
| [docs/07_user_guide.md](docs/07_user_guide.md) | Console walkthrough with the sample data |
| [docs/08_viva_preparation.md](docs/08_viva_preparation.md) | Questions and answers grounded in this implementation |
| [docs/09_report_material.md](docs/09_report_material.md) | Material for the PBL report chapters |
| [docs/10_final_audit.md](docs/10_final_audit.md) | Final audit and Definition of Done |
| [docs/traceability_matrix.md](docs/traceability_matrix.md) | Requirement → design → code → test → status |
| [docs/PROJECT_STATE.md](docs/PROJECT_STATE.md) | Current state, risks, decisions |

## 6. Limitations (by design)

- **Interface:** console only, a single operator role, no login (Review 1 scope).
- **Products:** single currency; no interest accrual on deposit accounts; no fixed deposits.
- **Payments:** no transfers to other banks.
- **Loans:** exact-instalment repayment only (no partial or advance payment); loan transactions cannot be reversed.
- **Concurrency:** all cash operations lock one CASH account, so they are serialised. That's fine for one operator; a real bank would use branch tills.
- **Platform:** developed and tested on MySQL 26.7.0 on macOS. The SQL targets MySQL 8.0.16+, but older versions and Windows are not verified.
