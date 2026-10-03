# 03 — Relational Design, Normalization and Data Dictionary (Gate 3)

Derived from the ER model in [02_er_model.md](02_er_model.md). Target: MySQL 8.0.16+ (InnoDB, `utf8mb4`), strict SQL mode.
This document is the specification for `sql/01_schema.sql`. The DDL must match it column for column.

## 1. Relational schema (summary)

Notation: **underline = PK** shown as `PK(...)`, `CK(...)` other candidate key, `FK(...) → table`.

| # | Relation | Keys |
|---|---|---|
| 1 | branch(branch_id, ifsc_code, branch_name, address, city, phone, created_at) | PK(branch_id) · CK(ifsc_code) |
| 2 | account_type(account_type_id, type_code, type_name, category, normal_side, min_opening_balance, min_balance, max_holders, interest_rate, description) | PK(account_type_id) · CK(type_code) |
| 3 | customer(customer_id, first_name, last_name, date_of_birth, gender, email, phone, address, city, id_proof_type, id_proof_number, created_at, updated_at) | PK(customer_id) · CK(email) · CK(id_proof_type, id_proof_number) |
| 4 | account(account_id, account_number, branch_id, account_type_id, balance, status, opened_at, closed_at) | PK(account_id) · CK(account_number) · FK(branch_id) → branch · FK(account_type_id) → account_type |
| 5 | account_holder(account_id, customer_id, holder_role, added_at) | PK(account_id, customer_id) · FK(account_id) → account · FK(customer_id) → customer |
| 6 | account_status_history(history_id, account_id, old_status, new_status, reason, changed_at) | PK(history_id) · FK(account_id) → account |
| 7 | bank_transaction(transaction_id, reference_no, transaction_type, status, narration, reversal_of_id, posted_at) | PK(transaction_id) · CK(reference_no) · CK(reversal_of_id) · FK(reversal_of_id) → bank_transaction |
| 8 | transaction_entry(entry_id, transaction_id, account_id, entry_side, amount, balance_after) | PK(entry_id) · CK(transaction_id, account_id, entry_side) · FK(transaction_id) → bank_transaction · FK(account_id) → account |
| 9 | transfer(transaction_id, beneficiary_id, remarks) | PK(transaction_id) · FK(transaction_id) → bank_transaction · FK(beneficiary_id) → beneficiary |
| 10 | beneficiary(beneficiary_id, customer_id, beneficiary_account_id, nickname, status, added_at, deactivated_at) | PK(beneficiary_id) · CK(customer_id, beneficiary_account_id) · CK(customer_id, nickname) · FK(customer_id) → customer · FK(beneficiary_account_id) → account |
| 11 | charge_type(charge_type_id, charge_code, charge_name, default_amount) | PK(charge_type_id) · CK(charge_code) |
| 12 | charge(transaction_id, charge_type_id) | PK(transaction_id) · FK(transaction_id) → bank_transaction · FK(charge_type_id) → charge_type |
| 13 | loan(loan_id, loan_number, account_id, customer_id, loan_type, principal, annual_interest_rate, tenure_months, disbursement_txn_id) | PK(loan_id) · CK(loan_number) · CK(disbursement_txn_id) · FK(account_id, customer_id) → account_holder · FK(disbursement_txn_id) → bank_transaction |
| 14 | loan_schedule(loan_id, installment_no, due_date, principal_component, interest_component) | PK(loan_id, installment_no) · CK(loan_id, due_date) · FK(loan_id) → loan |
| 15 | loan_repayment(transaction_id, loan_id, installment_no) | PK(transaction_id) · CK(loan_id, installment_no) · FK(transaction_id) → bank_transaction · FK(loan_id, installment_no) → loan_schedule |

**Implementation-only columns** (not part of the logical relations; they exist only so the database can enforce a rule):

| Column | Table | Purpose |
|---|---|---|
| `transaction_type` (constant per table) | transfer, charge, loan_repayment, loan (as `disbursement_txn_type`) | Composite FK `(transaction_id, transaction_type)` → `bank_transaction(transaction_id, transaction_type)` plus a CHECK fixing the value. A transfer row can then only point at a TRANSFER transaction, and so on. |
| `primary_account_id` (stored generated) | account_holder | `= account_id` when `holder_role = 'PRIMARY'`, else NULL; UNIQUE. Enforces exactly ≤ 1 PRIMARY holder per account (BR-08). |
| `installment_amount` (stored generated) | loan_schedule | `= principal_component + interest_component`. A derived attribute maintained by MySQL, never written by the application. |

These columns cannot be updated independently (constant, or computed by the DBMS), so they cannot cause update anomalies. They are excluded from the FD analysis in §3.

## 2. Unnormalized starting point and derivation

The design starts from the two "documents" a bank clerk would naturally keep. It decomposes them, so the normal forms are derived rather than reverse-engineered.

### 2.1 Account ledger sheet (UNF)

```
ACCOUNT_SHEET( account_number, branch_name, ifsc_code, branch_address, branch_city, branch_phone,
               type_code, type_name, min_opening_balance, min_balance, max_holders, ...,
               balance, status, opened_at, closed_at,
               { customer_id, first_name, last_name, dob, email, phone, ..., holder_role },     -- repeating group: holders
               { reference_no, type, posted_at, narration, side, amount, balance_after,
                 counterparty_account, beneficiary, charge_code, charge_name, default_amount } -- repeating group: movements
             )
```

**→ 1NF.** Remove the repeating groups. Every attribute is atomic (one value per cell) and each relation has a key:
- `ACCOUNT_1(account_number, branch…, type…, balance, status, opened_at, closed_at)`, key `account_number`
- `HOLDING_1(account_number, customer_id, first_name, …, holder_role)`, key `(account_number, customer_id)`
- `MOVEMENT_1(account_number, reference_no, type, posted_at, narration, side, amount, balance_after, charge_code, charge_name, default_amount, beneficiary…)`, key `(reference_no, account_number, side)`

Addresses are kept as one `address` line plus a separate `city`. We never query the parts of the address line, so it is atomic for this system.

**→ 2NF.** Remove partial dependencies on composite keys:
- In `HOLDING_1`, `customer_id → first_name, last_name, dob, email, …` depends on only part of the key. Customer attributes are split out to **customer**, leaving **account_holder(account_id, customer_id, holder_role, added_at)**.
- In `MOVEMENT_1`, `reference_no → type, posted_at, narration` depends on only part of the key. These are split out to the **bank_transaction** header, leaving **transaction_entry(transaction, account, side, amount, balance_after)**. Charge and transfer details also depend on `reference_no` alone, so they move to header-level relations (**charge**, **transfer**).

**→ 3NF.** Remove transitive dependencies (non-key → non-key):
- In `ACCOUNT_1`: `account_number → ifsc_code → branch_name, address, city, phone`. Branch attributes are split out to **branch**, and the account keeps `branch_id`.
- In `ACCOUNT_1`: `account_number → type_code → type_name, min_balance, max_holders, …`. These are split out to **account_type**, and the account keeps `account_type_id`.
- In the charge details: `reference_no → charge_code → charge_name, default_amount`. These are split out to **charge_type**, and **charge** keeps `charge_type_id`.
- Status history: `account_number → status` describes only the current value. Past values are a separate fact type keyed by an event, giving **account_status_history**.

### 2.2 Loan card (UNF)

```
LOAN_CARD( loan_number, customer…, account_number, loan_type, principal, rate, tenure, emi, status, disbursed_on,
           { installment_no, due_date, principal_part, interest_part, emi, paid_flag, paid_on, payment_reference } )
```

**→ 1NF.** `LOAN_1(loan_number, …)` and `INSTALMENT_1(loan_number, installment_no, due_date, principal_part, interest_part, emi, paid_flag, paid_on, payment_reference)`, key `(loan_number, installment_no)`.

**→ 2NF.** Customer and account attributes in `LOAN_1` depend on the customer/account, not the loan. Replace them with FKs. `INSTALMENT_1` has no attribute that depends on `loan_number` alone.

**→ 3NF.**
- `LOAN_1`: `(principal, rate, tenure) → emi` is a transitive dependency (`loan_number → principal, rate, tenure → emi`). `emi` is removed from the loan. Each instalment's amount is kept instead, because the final instalment legitimately differs (BR-33).
- `LOAN_1`: `status` is determined by whether any instalment is unpaid, so it is removed and derived (BR-35). `disbursed_on` is determined by the disbursement transaction, so it is replaced by `disbursement_txn_id`.
- `INSTALMENT_1`: `payment_reference → paid_on, amount`. The payment facts are split out to **loan_repayment** (keyed by its transaction). `paid_flag` becomes the existence of a repayment row.
- `INSTALMENT_1`: `emi = principal_part + interest_part` is a dependency between non-key attributes. It is not stored as an independent column. It is a DBMS-generated column (see §1).

## 3. Functional dependencies of the final relations

For each relation, **every** non-trivial FD whose left side is not a superkey would be a 3NF violation. None remain. Candidate keys are marked ⟨CK⟩.

| Relation | Functional dependencies | Normal form |
|---|---|---|
| branch | branch_id → all ⟨CK⟩; ifsc_code → all ⟨CK⟩ | BCNF |
| account_type | account_type_id → all ⟨CK⟩; type_code → all ⟨CK⟩ | BCNF |
| customer | customer_id → all ⟨CK⟩; email → all ⟨CK⟩; (id_proof_type, id_proof_number) → all ⟨CK⟩ | BCNF |
| account | account_id → all ⟨CK⟩; account_number → all ⟨CK⟩ | BCNF |
| account_holder | (account_id, customer_id) → holder_role, added_at ⟨CK⟩ | BCNF |
| account_status_history | history_id → account_id, old_status, new_status, reason, changed_at | BCNF |
| bank_transaction | transaction_id → all ⟨CK⟩; reference_no → all ⟨CK⟩; reversal_of_id → all ⟨CK, nullable unique⟩ | BCNF |
| transaction_entry | entry_id → all ⟨CK⟩; (transaction_id, account_id, entry_side) → amount, balance_after ⟨CK⟩ | BCNF |
| transfer | transaction_id → beneficiary_id, remarks | BCNF |
| beneficiary | beneficiary_id → all ⟨CK⟩; (customer_id, beneficiary_account_id) → all ⟨CK⟩; (customer_id, nickname) → all ⟨CK⟩ | BCNF |
| charge_type | charge_type_id → all ⟨CK⟩; charge_code → all ⟨CK⟩ | BCNF |
| charge | transaction_id → charge_type_id | BCNF |
| loan | loan_id → all ⟨CK⟩; loan_number → all ⟨CK⟩; disbursement_txn_id → all ⟨CK⟩ | BCNF |
| loan_schedule | (loan_id, installment_no) → due_date, principal_component, interest_component ⟨CK⟩; (loan_id, due_date) → installment_no, … ⟨CK⟩ | BCNF |
| loan_repayment | transaction_id → loan_id, installment_no ⟨CK⟩; (loan_id, installment_no) → transaction_id ⟨CK⟩ | BCNF |

**Dependencies deliberately not treated as FDs:**
- `customer.address → city` does not hold. The same street line can exist in different cities. No PIN code is stored, which avoids the classic `PIN → city` transitive dependency.
- `customer.phone` is not a key. Shared family phones are allowed.
- `transaction_entry.balance_after` is not determined by any other attribute of the entry. It depends on the account's history up to that entry (see §5).

**Result:** every relation is in 3NF (the requirement), and in fact BCNF, because every determinant is a candidate key.

## 4. Data dictionary

Conventions:
- money is `DECIMAL(15,2)`, which holds up to 9,999,999,999,999.99;
- timestamps are `DATETIME` written in the server session time zone (see §8);
- NN = NOT NULL;
- every FK is `ON DELETE RESTRICT ON UPDATE RESTRICT`, so no cascading deletes of financial data.

### 4.1 branch
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| branch_id | INT | NN | AUTO_INCREMENT | PK | Surrogate key |
| ifsc_code | CHAR(11) | NN | | UNIQUE; CHECK `^[A-Z]{4}0[A-Z0-9]{6}$` | Branch code (IFSC format) |
| branch_name | VARCHAR(100) | NN | | | |
| address | VARCHAR(255) | NN | | | Street address line |
| city | VARCHAR(60) | NN | | | |
| phone | VARCHAR(15) | NN | | CHECK `^\+?[0-9]{10,14}$` | |
| created_at | DATETIME | NN | CURRENT_TIMESTAMP | | |

### 4.2 account_type
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| account_type_id | INT | NN | AUTO_INCREMENT | PK | |
| type_code | VARCHAR(20) | NN | | UNIQUE; CHECK `^[A-Z_]+$` | e.g. SAVINGS |
| type_name | VARCHAR(60) | NN | | | |
| category | ENUM('CUSTOMER','INTERNAL') | NN | | | Customer product or bank's own account |
| normal_side | ENUM('DEBIT','CREDIT') | NN | | | Side that increases the balance |
| min_opening_balance | DECIMAL(15,2) | NN | 0.00 | CHECK ≥ 0 | BR-09 |
| min_balance | DECIMAL(15,2) | NN | 0.00 | CHECK 0 ≤ min_balance ≤ min_opening_balance | BR-11 |
| max_holders | TINYINT | NN | 1 | see table CHECK | BR-08 |
| interest_rate | DECIMAL(5,2) | NN | 0.00 | CHECK 0–100 | Informational only |
| description | VARCHAR(255) | NULL | | | |

Table CHECK `chk_account_type_category`: `(category='CUSTOMER' AND normal_side='CREDIT' AND max_holders BETWEEN 1 AND 4) OR (category='INTERNAL' AND max_holders=0 AND min_opening_balance=0 AND min_balance=0)`.

### 4.3 customer
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| customer_id | INT | NN | AUTO_INCREMENT | PK | |
| first_name | VARCHAR(50) | NN | | CHECK not blank | |
| last_name | VARCHAR(50) | NN | | CHECK not blank | |
| date_of_birth | DATE | NN | | CHECK ≥ '1900-01-01' | Not-in-future and ≥ 18 checked in Java (BR-02/03) |
| gender | ENUM('MALE','FEMALE','OTHER') | NN | | | |
| email | VARCHAR(100) | NN | | UNIQUE; CHECK `^[^@ ]+@[^@ ]+\.[^@ ]+$` | |
| phone | VARCHAR(15) | NN | | CHECK `^\+?[0-9]{10,14}$` | |
| address | VARCHAR(255) | NN | | | |
| city | VARCHAR(60) | NN | | | |
| id_proof_type | ENUM('PAN','AADHAAR','PASSPORT','VOTER_ID','DRIVING_LICENCE') | NN | | UNIQUE with id_proof_number | |
| id_proof_number | VARCHAR(20) | NN | | | Displayed masked in the UI |
| created_at | DATETIME | NN | CURRENT_TIMESTAMP | | |
| updated_at | DATETIME | NN | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | | |

### 4.4 account
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| account_id | INT | NN | AUTO_INCREMENT | PK | |
| account_number | CHAR(12) | NN | | UNIQUE; CHECK `^[0-9]{12}$` | Generated by the application |
| branch_id | INT | NN | | FK → branch | |
| account_type_id | INT | NN | | FK → account_type | |
| balance | DECIMAL(15,2) | NN | 0.00 | CHECK ≥ 0 | Current balance in the normal-side sign (§5) |
| status | ENUM('ACTIVE','FROZEN','BLOCKED','CLOSED') | NN | 'ACTIVE' | | BR-12 |
| opened_at | DATETIME | NN | CURRENT_TIMESTAMP | | |
| closed_at | DATETIME | NULL | | CHECK `(status='CLOSED') = (closed_at IS NOT NULL)`; CHECK closed_at ≥ opened_at | |

### 4.5 account_holder
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| account_id | INT | NN | | PK, FK → account | |
| customer_id | INT | NN | | PK, FK → customer | |
| holder_role | ENUM('PRIMARY','JOINT') | NN | | | |
| added_at | DATETIME | NN | CURRENT_TIMESTAMP | | |
| primary_account_id | INT | NULL | GENERATED (STORED) | UNIQUE | Implementation-only (§1) |

### 4.6 account_status_history
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| history_id | BIGINT | NN | AUTO_INCREMENT | PK | |
| account_id | INT | NN | | FK → account | |
| old_status | ENUM(as account.status) | NULL | | CHECK `old_status IS NULL OR old_status <> new_status` | NULL = account opened |
| new_status | ENUM(as account.status) | NN | | | |
| reason | VARCHAR(255) | NN | | CHECK not blank | |
| changed_at | DATETIME | NN | CURRENT_TIMESTAMP | | |

### 4.7 bank_transaction
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| transaction_id | BIGINT | NN | AUTO_INCREMENT | PK; UNIQUE(transaction_id, transaction_type) | |
| reference_no | CHAR(16) | NN | | UNIQUE; CHECK `^TX[0-9]{8}[A-Z0-9]{6}$` | `TX` + yyyyMMdd + 6 random characters, generated in Java with retry on collision |
| transaction_type | ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','CHARGE','LOAN_DISBURSEMENT','LOAN_REPAYMENT','REVERSAL','CAPITAL_INJECTION') | NN | | | |
| status | ENUM('POSTED','REVERSED') | NN | 'POSTED' | CHECK NOT (type='REVERSAL' AND status='REVERSED') | |
| narration | VARCHAR(255) | NULL | | | |
| reversal_of_id | BIGINT | NULL | | UNIQUE; FK → bank_transaction; CHECK `(transaction_type='REVERSAL') = (reversal_of_id IS NOT NULL)` | Original transaction this one reverses |
| posted_at | DATETIME | NN | CURRENT_TIMESTAMP | | |

### 4.8 transaction_entry
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| entry_id | BIGINT | NN | AUTO_INCREMENT | PK | Monotonic: gives posting order |
| transaction_id | BIGINT | NN | | FK → bank_transaction; UNIQUE(transaction_id, account_id, entry_side) | |
| account_id | INT | NN | | FK → account | |
| entry_side | ENUM('DEBIT','CREDIT') | NN | | | |
| amount | DECIMAL(15,2) | NN | | CHECK > 0 | |
| balance_after | DECIMAL(15,2) | NN | | CHECK ≥ 0 | Account balance immediately after this entry |

### 4.9 transfer
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| transaction_id | BIGINT | NN | | PK; FK (transaction_id, transaction_type) → bank_transaction | |
| transaction_type | ENUM(as bank_transaction) | NN | 'TRANSFER' | CHECK = 'TRANSFER' | Implementation-only |
| beneficiary_id | INT | NULL | | FK → beneficiary | NULL = own-account transfer (BR-25) |
| remarks | VARCHAR(140) | NULL | | | |

### 4.10 beneficiary
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| beneficiary_id | INT | NN | AUTO_INCREMENT | PK | |
| customer_id | INT | NN | | FK → customer | Owner of the payee list |
| beneficiary_account_id | INT | NN | | FK → account; UNIQUE(customer_id, beneficiary_account_id) | Payee account |
| nickname | VARCHAR(50) | NN | | UNIQUE(customer_id, nickname); CHECK not blank | |
| status | ENUM('ACTIVE','INACTIVE') | NN | 'ACTIVE' | | |
| added_at | DATETIME | NN | CURRENT_TIMESTAMP | | |
| deactivated_at | DATETIME | NULL | | CHECK `(status='INACTIVE') = (deactivated_at IS NOT NULL)` | |

### 4.11 charge_type
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| charge_type_id | INT | NN | AUTO_INCREMENT | PK | |
| charge_code | VARCHAR(30) | NN | | UNIQUE; CHECK `^[A-Z_]+$` | |
| charge_name | VARCHAR(80) | NN | | | |
| default_amount | DECIMAL(15,2) | NN | | CHECK > 0 | |

### 4.12 charge
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| transaction_id | BIGINT | NN | | PK; FK (transaction_id, transaction_type) → bank_transaction | |
| transaction_type | ENUM(as bank_transaction) | NN | 'CHARGE' | CHECK = 'CHARGE' | Implementation-only |
| charge_type_id | INT | NN | | FK → charge_type | |

### 4.13 loan
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| loan_id | INT | NN | AUTO_INCREMENT | PK | |
| loan_number | CHAR(14) | NN | | UNIQUE; CHECK `^LN[0-9]{12}$` | `LN` + yyyyMMdd + 4 digits |
| account_id | INT | NN | | FK (account_id, customer_id) → account_holder | Disbursement and repayment account |
| customer_id | INT | NN | | (part of the FK above) | Borrower |
| loan_type | ENUM('PERSONAL','HOME','VEHICLE','EDUCATION') | NN | | | |
| principal | DECIMAL(15,2) | NN | | CHECK > 0 | |
| annual_interest_rate | DECIMAL(5,2) | NN | | CHECK > 0 AND ≤ 36 | Percent per year |
| tenure_months | SMALLINT | NN | | CHECK 1–360 | |
| disbursement_txn_id | BIGINT | NN | | UNIQUE; FK (disbursement_txn_id, disbursement_txn_type) → bank_transaction | |
| disbursement_txn_type | ENUM(as bank_transaction) | NN | 'LOAN_DISBURSEMENT' | CHECK = 'LOAN_DISBURSEMENT' | Implementation-only |

### 4.14 loan_schedule
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| loan_id | INT | NN | | PK, FK → loan | |
| installment_no | SMALLINT | NN | | PK; CHECK ≥ 1 | |
| due_date | DATE | NN | | UNIQUE(loan_id, due_date) | |
| principal_component | DECIMAL(15,2) | NN | | CHECK > 0 | |
| interest_component | DECIMAL(15,2) | NN | | CHECK ≥ 0 | |
| installment_amount | DECIMAL(15,2) | NN | GENERATED (STORED) | | Implementation-only (§1) |

### 4.15 loan_repayment
| Column | Type | Null | Default | Constraints | Meaning |
|---|---|---|---|---|---|
| transaction_id | BIGINT | NN | | PK; FK (transaction_id, transaction_type) → bank_transaction | |
| transaction_type | ENUM(as bank_transaction) | NN | 'LOAN_REPAYMENT' | CHECK = 'LOAN_REPAYMENT' | Implementation-only |
| loan_id | INT | NN | | FK (loan_id, installment_no) → loan_schedule; UNIQUE(loan_id, installment_no) | |
| installment_no | SMALLINT | NN | | | |

## 5. Controlled redundancy and the balance invariant

Two stored values can be computed from other rows. Both are deliberate, documented and verified:

| Value | Why stored | How kept correct |
|---|---|---|
| `account.balance` | It is the row locked (`SELECT … FOR UPDATE`) to serialise concurrent postings, and the target of the `CHECK (balance >= 0)` integrity boundary. Recomputing from all entries on every operation would be slow, and could not be protected by a CHECK. | Changed only by the posting engine, in the same DB transaction as the entry. Invariant **I2**: `balance = Σ(entries on the normal side) − Σ(entries on the other side)`, checked by a verification query and tests. |
| `transaction_entry.balance_after` | A point-in-time fact used for statements (running balance). Entries are immutable, so it can never go stale. | Written once by the posting engine; invariant **I2b**: the latest entry's balance_after = `account.balance`. |

This is redundancy *across* relations. It is not a 3NF violation, because no FD inside a relation is broken. We still name and test it, because it is the main correctness risk in the design.

### Invariants (each gets a verification query in Gate 4 and an automated test)
- **I1** For every transaction: Σ DEBIT amounts = Σ CREDIT amounts, with at least 2 entries.
- **I2** For every account: balance = signed net of its entries. **I2b**: the last entry's balance_after = balance.
- **I3** Trial balance: Σ balances of debit-normal accounts = Σ balances of credit-normal accounts.
- **I4** Balances ≥ 0 (CHECK).
- **I5** Every REVERSED transaction has exactly one REVERSAL pointing at it, and vice versa.
- **I6** For every loan: Σ principal_component = principal. Σ outstanding principal over all loans = LOAN_RECEIVABLE balance.
- **I7** Every TRANSFER has exactly one DEBIT and one CREDIT entry, on two different customer accounts. Every CHARGE debits one customer account and credits FEE_INCOME.

## 6. Status columns kept vs derived

| Fact | Decision | Reason |
|---|---|---|
| account.status | Stored | Set by deliberate operator action; not computable |
| bank_transaction.status | Stored | Set when a reversal is posted. Also checkable via I5 |
| beneficiary.status | Stored | Operator action |
| loan status (ACTIVE/CLOSED) | Derived | = exists unpaid instalment |
| instalment paid / overdue | Derived | = repayment exists / unpaid and due_date < CURRENT_DATE |
| loan EMI, outstanding principal | Derived | Computed from loan_schedule and loan_repayment |

## 7. Constraint catalogue

Where each business rule is enforced. **DB** = schema constraint · **Java** = service validation · **Both** = defence in depth.

| Rule | Enforcement | Mechanism |
|---|---|---|
| BR-01 unique email / ID proof | Both | UNIQUE + duplicate check with friendly message |
| BR-02 age ≥ 18, BR-03 DOB not future | Java | CHECK cannot use CURRENT_DATE in MySQL; DB keeps a ≥ 1900 sanity CHECK |
| BR-06 customer products are credit-normal | DB | `chk_account_type_category` |
| BR-07 one branch, one type | DB | NN FKs |
| BR-08 one PRIMARY holder | DB | UNIQUE on generated `primary_account_id` |
| BR-08 holder limit | Java | Cross-row count against max_holders, under row lock on the account |
| BR-09 opening deposit ≥ minimum | Java | Same DB transaction as account creation |
| BR-10 balance never negative | Both | `CHECK (balance >= 0)` + insufficient-funds check |
| BR-11 minimum balance on customer debits | Java | Cross-table rule |
| BR-12 lifecycle transitions; BR-13 closing preconditions | Java | Transition table in service, under row lock |
| BR-12 closed_at consistency | DB | CHECK |
| BR-14 status history | Java + DB | Written in the same transaction; `old <> new` CHECK |
| BR-16/17 balanced transaction | Java + verification | Posting engine rejects unbalanced postings; I1 query |
| BR-18 unique reference | DB | UNIQUE + format CHECK |
| BR-19 reverse once; reversal points to original | DB | UNIQUE(reversal_of_id) + type/reference CHECK |
| BR-19 no edit/delete of ledger | DB | Triggers (§9) |
| BR-21 amount > 0 | Both | CHECK (amount > 0) + validation |
| BR-25 beneficiary authorisation | Java | Cross-table rule (holders, beneficiary status) |
| BR-26 unique payee per customer | DB | UNIQUE(customer_id, beneficiary_account_id) |
| BR-27 no self-beneficiary | Java | Cross-table rule |
| BR-28 deactivation consistency | DB | CHECK status ↔ deactivated_at |
| BR-31 borrower holds the account | DB | Composite FK → account_holder |
| BR-32 loan parameter ranges | Both | CHECKs + validation |
| BR-34 one repayment per instalment | DB | UNIQUE(loan_id, installment_no) in loan_repayment |
| BR-34 pay the earliest unpaid instalment, exact amount | Java | Under row lock |
| Specialisation matches transaction type | DB | Composite FK + constant CHECK |

## 8. Time handling

All business timestamps (`posted_at`, `opened_at`, `closed_at`, `changed_at`, `added_at`, customer and branch `created_at`) are written **explicitly by the application** from its business clock (`BankClock`). They are not left to `DEFAULT CURRENT_TIMESTAMP`. The defaults remain for rows inserted directly with SQL (reference data, administrators).

The JDBC URL is completed with `connectionTimeZone=LOCAL`, so Connector/J does not shift `DATETIME` values between the JVM and the server. Timestamps written from Java and date ranges built in Java (statements, audit) therefore always compare in the same zone. This fixes audit defect D-02 (the earlier `serverTimezone=UTC` mismatch). Verified by test TC-JDBC-02.

*Implementation note:* the design originally also forced the session time zone (`forceConnectionTimeZoneToSession=true`). That makes the driver send `SET time_zone='Asia/Kolkata'`, which fails on MySQL installations without time-zone tables (the Homebrew and most Windows defaults). It was removed in Gate 6, so the only server-time-dependent logic left is the overdue derivation in `v_loan_installment_status` (`CURRENT_DATE`), which uses the server's local date.

## 9. Ledger immutability triggers (implemented in sql/02_triggers.sql)

Enforces NFR-09 / BR-19 in the database itself, so even direct SQL cannot rewrite history:

| Trigger | Rule |
|---|---|
| `trg_entry_block_update`, `trg_entry_block_delete` | transaction_entry rows can never be updated or deleted |
| `trg_txn_guard_update` | bank_transaction: only `status` may change, and only POSTED → REVERSED |
| `trg_txn_block_delete` | bank_transaction rows can never be deleted |
| `trg_history_block_update`, `trg_history_block_delete` | account_status_history is append-only |

Each trigger raises `SIGNAL SQLSTATE '45000'` with a clear message. Test databases are rebuilt from the DDL (drop and create), which is not affected by row triggers.

## 10. Index strategy

InnoDB clusters each table on its PK and automatically indexes every UNIQUE constraint. It also requires (and creates, if missing) an index for every FK. The extra indexes below are justified by a specific query:

| Index | Columns | Serves |
|---|---|---|
| idx_customer_name | customer(last_name, first_name) | Customer search by name (FR-04) |
| idx_entry_account_txn | transaction_entry(account_id, transaction_id) | Statements and balance checks: all entries of one account in posting order (FR-31, I2). Replaces the plain FK index on account_id |
| idx_txn_posted_at | bank_transaction(posted_at) | Transaction audit for a date range (FR-33), statement period lookup |
| idx_txn_type_posted | bank_transaction(transaction_type, posted_at) | Audit filtered by type; transfer report (FR-34) |
| idx_history_account_time | account_status_history(account_id, changed_at) | Account lifecycle history in time order |
| idx_schedule_due_date | loan_schedule(due_date) | Overdue instalments across all loans (FR-35) |
| idx_loan_customer | loan(customer_id) | Loans of a customer (the composite FK index leads with account_id) |

**Indexes deliberately not created:**
- `account(status)`: only 4 values, and the reports scan whole branches anyway, so low selectivity.
- `customer(city)`: no requirement queries by city.

Each extra index's use will be shown with `EXPLAIN` in Gate 5.

## 11. Reference and sample data (as implemented)

- **`04_reference_data.sql` (required):**
  - 6 account types (SAVINGS, CURRENT, SALARY, GL_ASSET, GL_INCOME, GL_EQUITY);
  - 4 charge types;
  - the head-office branch;
  - the 5 internal accounts;
  - one CAPITAL_INJECTION transaction (DR CASH 5,00,00,000 / CR CAPITAL), so that cash withdrawals can never drive CASH negative.
- **`05_sample_data.sql` (optional):** generated by `SampleDataLoader`, which runs the real services from March to September 2026, and exported by `scripts/export_sample_data.sh`. It contains:
  - 3 branches and 10 customers;
  - 11 customer accounts: one joint, one FROZEN, one BLOCKED, one CLOSED;
  - 6 beneficiaries, one inactive;
  - 96 transactions with 199 entries, including 28 transfers, charges (one waived by reversal) and a reversed deposit;
  - 3 loans: a vehicle loan fully repaid (CLOSED), a personal loan with one late and one overdue instalment, and a home loan not yet due.
  It passes all invariants I1–I11 (test TC-REG-01).
