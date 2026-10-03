# Requirements Traceability Matrix

Last updated: 2026-10-03 (final audit, Gate 14).

**Statuses:**
- **VERIFIED:** an executed test or a recorded command output proves it.
- **IMPLEMENTED:** built but proven only by inspection.

Test IDs refer to [evidence/test_catalogue_generated.md](evidence/test_catalogue_generated.md); all 152 tests PASS.

| Req | Description | Design | Implementation | Test / evidence | Status |
|---|---|---|---|---|---|
| FR-01 | Create customer | 02 §2.3, 03 §4.3 | `CustomerService.register`, `CustomerDao.insert` | TC-CUS-01 | VERIFIED |
| FR-02 | Read customer | | `CustomerService.get` | TC-CUS-01, TC-CUS-05 | VERIFIED |
| FR-03 | Update customer | | `CustomerService.update` | TC-CUS-04, TC-CUS-05 | VERIFIED |
| FR-04 | Search customers | 03 §10 idx_customer_name | `CustomerDao.search` (escaped LIKE) | TC-CUS-06 | VERIFIED |
| FR-05 | Validate customer data | BR-01..03 | `Validator`, CHECK constraints | TC-CUS-02, TC-CUS-03, TC-VAL-04..08, TC-DB-02/03/16 | VERIFIED |
| FR-06 | Create branch | 03 §4.1 | `BranchService.create` | TC-BR-01, TC-BR-02 | VERIFIED |
| FR-07 | Branch details / update contact | | `BranchService.get/updateContact/search` | TC-BR-03 | VERIFIED |
| FR-08 | Account belongs to one branch | 02 R1 | NOT NULL FK `fk_account_branch` | TC-DB-06, TC-ACC-03 | VERIFIED |
| FR-09 | Branch reporting | `v_branch_summary` | `ReportService.branchSummary` | TC-RPT-08 | VERIFIED |
| FR-10 | Account types as products | 02 E7, BR-04..06 | `AccountTypeService` | TC-AT-01, TC-AT-02, TC-DB-13 | VERIFIED |
| FR-11 | Account opening | BR-09 | `AccountService.open` (one transaction) | TC-ACC-01..05 | VERIFIED |
| FR-12 | Account lookup | | `getByNumber`, `listByCustomer`, `searchAccounts` | TC-ACC-11, TC-REG-04 | VERIFIED |
| FR-13 | Lifecycle; only valid accounts transact | BR-12..15 | `AccountStatus`, `AccountService.changeStatus/close`, `PostingEngine` | TC-LIFE-01/02, TC-ACC-07..10, TC-DEP-03, TC-WDR-05, TC-TRF-08 | VERIFIED |
| FR-14 | Account holders, joint ownership | BR-08 | `addJointHolder`, generated-column UNIQUE | TC-ACC-06, TC-DB-04/05 | VERIFIED |
| FR-15 | Deposit | BR-22 | `CashService.deposit` | TC-DEP-01..05 | VERIFIED |
| FR-16 | Withdrawal | BR-23 | `CashService.withdraw` | TC-WDR-01..05 | VERIFIED |
| FR-17 | Atomic transfer, rollback | BR-24/25 | `TransferService`, `TransactionRunner`, `PostingEngine` | TC-TRF-01..11 (rollback: 09/10, concurrency: 11) | VERIFIED |
| FR-18 | Traceability | BR-18 | reference_no, status, posted_at, entries | TC-DEP-01, TC-RPT-05 | VERIFIED |
| FR-19 | Correction by reversal | BR-19 | `ReversalService`, triggers | TC-REV-01..05, TC-DB-17..19 | VERIFIED |
| FR-20 | Add beneficiary | BR-26 | `BeneficiaryService.add` | TC-BEN-01 | VERIFIED |
| FR-21 | Update beneficiary | BR-28 | `rename` | TC-BEN-03 | VERIFIED |
| FR-22 | Deactivate / reactivate | BR-28 | `deactivate`, `reactivate` | TC-BEN-04 | VERIFIED |
| FR-23 | Search beneficiaries | | `BeneficiaryService.search` | TC-BEN-01 | VERIFIED |
| FR-24 | Validate beneficiary relationship | BR-25, BR-27 | `BeneficiaryService`, `TransferService.authorise` | TC-BEN-02, TC-TRF-03/04 | VERIFIED |
| FR-25 | Service charges as financial records | BR-29/30 | `ChargeService` | TC-CHG-01..04 | VERIFIED |
| FR-26 | Loan creation | BR-31/32 | `LoanService.disburse` | TC-LOAN-01..03, TC-LOAN-10, TC-DB-07/14 | VERIFIED |
| FR-27 | Loan schedule | BR-33 | `LoanCalculator`, `LoanScheduleDao` | TC-LC-01..06, TC-LOAN-01 | VERIFIED |
| FR-28 | Loan repayment | BR-34 | `LoanService.repayNextInstallment` | TC-LOAN-04..07 | VERIFIED |
| FR-29 | Outstanding amount | BR-36 | `v_loan_position` | TC-LOAN-04, TC-LOAN-07, invariant I6b | VERIFIED |
| FR-30 | Repayment reporting | | `v_loan_repayment_report` | TC-LOAN-07/08, TC-RPT-07 | VERIFIED |
| FR-31 | Statements | BR-37 | `ReportService.statement` | TC-RPT-01..03, TC-REG-03, console session | VERIFIED |
| FR-32 | Report: balances | `v_account_overview` | `accountBalances` | TC-RPT-04 | VERIFIED |
| FR-33 | Report: transaction audit | `v_transaction_audit` | `transactionAudit` | TC-RPT-05 | VERIFIED |
| FR-34 | Report: transfers | `v_transfer_report` | `transfers` | TC-RPT-06 | VERIFIED |
| FR-35 | Report: loan outstanding | `v_loan_position` | `loanOutstanding` | TC-RPT-07, TC-LOAN-08 | VERIFIED |
| FR-36 | Report: loan repayments | `v_loan_repayment_report` | `loanRepayments` | TC-RPT-07 | VERIFIED |
| FR-37 | Report: branch summary | `v_branch_summary` | `branchSummary` | TC-RPT-08 | VERIFIED |
| NFR-01 | DECIMAL / BigDecimal, no float/double | 03 §4 | `Money`, all DAOs | TC-VAL-01/02; `grep -rnw 'double\|float' src/main/java` finds only 2 comments, no code | VERIFIED |
| NFR-02 | Atomic operations on one connection | 05 §3 | `TransactionRunner.inTransaction` | TC-JDBC-04, TC-DEP-05, TC-TRF-09/10 | VERIFIED |
| NFR-03 | 3NF with FDs | 03 §2–3 | `01_schema.sql` | Doc ↔ schema column cross-check (15/15 tables match) | VERIFIED |
| NFR-04 | DB + application integrity | 03 §7 | constraints, triggers, `Validator` | TC-DB-01..20 | VERIFIED |
| NFR-05 | PreparedStatement only | 05 §6 | `JdbcSupport`, DAOs | `createStatement` appears only in `SqlScriptRunner` (trusted script files); TC-CUS-06 (LIKE escaping) | VERIFIED |
| NFR-06 | No secrets in VCS | 05 §6 | `.gitignore`, `*.example`, runtime config outside the jar | `git check-ignore` on both db.properties; jar contains no db.properties; 0 password hits in docs/logs | VERIFIED |
| NFR-07 | Friendly errors | 05 §5 | `SqlErrorTranslator`, `Menu` | TC-JDBC-05, console session | VERIFIED |
| NFR-08 | Layered architecture, no frameworks | 05 §1 | packages gui/ui → service → ledger → dao → db | pom.xml (only connector-j and junit) | VERIFIED |
| NFR-09 | History never deleted | 03 §9 | triggers; app user has no DELETE | TC-DB-17..20, TC-JDBC-03 | VERIFIED |
| NFR-10 | Tests with evidence | 06 | 152 tests | evidence/ | VERIFIED |
| UI-01 | Desktop Java application (owner request 2026-10-03) | 05 §2a | `com.bankdbms.gui` (8 screens) | TC-GUI-01..05, docs/screenshots | VERIFIED |
