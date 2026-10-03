# 01 — Requirements, Scope and Business Rules

Project: **Design and Implementation of a Database Management System for Bank Account and Transaction Management System** (Project 55).
Stack: Java · JDBC · MySQL. Single actor: bank staff / operator (console application).

## 1. Sources and labels

| Priority | Source | Where |
|---|---|---|
| 1 | Official requirements | No separate university document was supplied. The project owner directed that the functional requirements in the project master prompt (§5) are the baseline. Each requirement below is labelled **OFFICIAL REQUIREMENT**. |
| 2 | Project owner decisions | Review 1 (submitted) and design decisions approved on 2026-10-03 (see §6). |
| 3 | Design proposals | Labelled **PROPOSED ASSUMPTION** (adopted, may be revised) or **OPTIONAL ENHANCEMENT** (only if time permits). |

## 2. Functional requirements (OFFICIAL REQUIREMENT unless labelled)

| ID | Requirement |
|---|---|
| FR-01 | Create a customer |
| FR-02 | Read customer details |
| FR-03 | Update customer details |
| FR-04 | Search customers |
| FR-05 | Validate customer data |
| FR-06 | Create a branch |
| FR-07 | View branch details (and update contact details — PROPOSED ASSUMPTION) |
| FR-08 | Associate every account with exactly one branch |
| FR-09 | Branch-level reporting |
| FR-10 | Manage account types (banking products) |
| FR-11 | Open an account (customer, branch, account type, opening deposit) |
| FR-12 | Look up accounts (by number, by customer, by search) |
| FR-13 | Manage account status / lifecycle; only valid accounts may transact |
| FR-14 | Model account holders, including joint ownership |
| FR-15 | Deposit |
| FR-16 | Withdrawal |
| FR-17 | Atomic transfer between accounts (no partial transfer, rollback on any failure) |
| FR-18 | Every financial transaction is traceable (reference, timestamp, type, status, accounts) |
| FR-19 | Correct a posted transaction by reversal, never by edit/delete — PROPOSED ASSUMPTION |
| FR-20 | Add a beneficiary |
| FR-21 | Update a beneficiary |
| FR-22 | Deactivate (and reactivate) a beneficiary |
| FR-23 | Search beneficiaries |
| FR-24 | Validate the beneficiary relationship |
| FR-25 | Apply service charges as real financial records |
| FR-26 | Create (sanction and disburse) a loan |
| FR-27 | Generate the loan repayment schedule |
| FR-28 | Record loan repayments |
| FR-29 | Calculate the outstanding loan amount |
| FR-30 | Loan repayment reporting |
| FR-31 | Account statement for a period |
| FR-32 | Report: account balances |
| FR-33 | Report: transaction audit |
| FR-34 | Report: transfers |
| FR-35 | Report: loan outstanding |
| FR-36 | Report: loan repayments |
| FR-37 | Report: branch summary |

## 3. Non-functional requirements (OFFICIAL REQUIREMENT)

| ID | Requirement |
|---|---|
| NFR-01 | Monetary values: `DECIMAL` in MySQL, `BigDecimal` in Java; never `float`/`double` |
| NFR-02 | Multi-step financial operations are atomic, on one JDBC `Connection`, with commit/rollback |
| NFR-03 | Schema normalized to 3NF, with documented functional dependencies |
| NFR-04 | Integrity enforced by the database (PK, FK, UNIQUE, NOT NULL, CHECK, DEFAULT) **and** by application validation |
| NFR-05 | All SQL uses `PreparedStatement`; no user input concatenated into SQL |
| NFR-06 | No credentials in source control; configuration separated from code |
| NFR-07 | User-facing errors are friendly and never expose SQL, stack traces or credentials |
| NFR-08 | Layered architecture: Presentation → Service → DAO → JDBC → MySQL; no frameworks/ORM |
| NFR-09 | Financial history is never deleted or overwritten |
| NFR-10 | Positive and negative tests with recorded evidence |

## 4. Scope

**In scope:** everything in §2–§3.

**Out of scope** (Review 1 decisions unless labelled):
- GUI / web front end (console only)
- Authentication / multiple user roles
- Multi-currency
- Interest accrual on deposit accounts (interest rate on account types is informational)
- Partial, advance or foreclosure loan repayments (exactly one scheduled instalment per repayment)
- Term / fixed deposits — PROPOSED ASSUMPTION: a term deposit needs maturity, premature-closure and interest-payout rules that cannot be done properly at this scale, so no FIXED_DEPOSIT product is offered
- Transfers to other banks / external payees — PROPOSED ASSUMPTION: there is no payment network or clearing account, so beneficiaries are accounts in this bank
- Reversal of loan transactions — PROPOSED ASSUMPTION: loan corrections need a loan-restructuring process

## 5. Business rules

All are PROPOSED ASSUMPTIONS adopted for the design unless marked OFFICIAL.

### 5.1 Customers
- **BR-01** Email is unique per customer. The pair (ID-proof type, ID-proof number) is unique.
- **BR-02** A customer must be at least 18 years old to be an account holder or borrower. (Enforced in Java: MySQL CHECK cannot use `CURRENT_DATE`.)
- **BR-03** Date of birth cannot be in the future.

### 5.2 Account types (products)
- **BR-04** An account type is a product with: category (CUSTOMER or INTERNAL), normal balance side, minimum opening balance, minimum balance, maximum number of holders and an informational interest rate.
- **BR-05** Customer products are SAVINGS, CURRENT and SALARY (seed data; operators may add more).
- **BR-06** Customer accounts are credit-normal (the bank owes the customer the balance).

### 5.3 Accounts and holders
- **BR-07** An account belongs to exactly one branch and one account type.
- **BR-08** An account has exactly one PRIMARY holder and up to `max_holders − 1` JOINT holders.
- **BR-09** The opening deposit must be ≥ the type's minimum opening balance and is posted as a DEPOSIT transaction in the same database transaction as account creation.
- **BR-10** A customer account balance can never be negative (OFFICIAL — insufficient funds are rejected).
- **BR-11** Customer-initiated debits (withdrawal, transfer out, loan repayment) may not take the balance below the type's minimum balance. Bank-initiated debits (charges) may take it below the minimum but never below zero.

### 5.4 Account lifecycle

| Status | Credits allowed | Debits allowed | Meaning |
|---|---|---|---|
| ACTIVE | yes | yes | Normal operation |
| FROZEN | yes | no | Debit freeze (e.g. legal hold) |
| BLOCKED | no | no | All financial activity stopped (e.g. KYC issue) |
| CLOSED | no | no | Terminal |

- **BR-12** Allowed transitions: any of ACTIVE/FROZEN/BLOCKED ↔ any other of those three; ACTIVE → CLOSED. CLOSED is terminal.
- **BR-13** Closing requires: status ACTIVE and no unpaid loan instalment repayable from the account. Any remaining balance is paid out as a closure WITHDRAWAL in the same database transaction.
- **BR-14** Every status change (including the initial ACTIVE at opening) is recorded with old status, new status, reason and timestamp.
- **BR-15** Internal (bank) accounts are always ACTIVE and cannot be changed or closed.

### 5.5 Ledger
- **BR-16** Every financial event is one transaction (header) with two or more entries. Each entry debits or credits exactly one account.
- **BR-17** Per transaction, total debits = total credits.
- **BR-18** Each transaction has a unique reference number, a type, a status (POSTED / REVERSED) and a posting timestamp.
- **BR-19** Entries are never updated or deleted. A posted DEPOSIT, WITHDRAWAL, TRANSFER or CHARGE may be reversed once, by a new REVERSAL transaction that mirrors its entries. The original is marked REVERSED. The reversal is subject to the normal balance and status rules.
- **BR-20** An account's stored balance always equals the net of its entries, signed by its normal side.

### 5.6 Posting rules

| Operation | Debit | Credit |
|---|---|---|
| Deposit (incl. opening deposit) | CASH | customer account |
| Withdrawal (incl. closure payout) | customer account | CASH |
| Transfer | source account | destination account |
| Service charge | customer account | FEE_INCOME |
| Loan disbursement | LOAN_RECEIVABLE | customer account |
| Loan repayment | customer account (instalment) | LOAN_RECEIVABLE (principal) + INTEREST_INCOME (interest) |
| Reversal | each original credit | each original debit |
| Capital injection (seed only) | CASH | CAPITAL |

Internal accounts: CASH and LOAN_RECEIVABLE are debit-normal (assets). FEE_INCOME and INTEREST_INCOME are credit-normal (income). CAPITAL is credit-normal (equity).

### 5.7 Deposits, withdrawals, transfers
- **BR-21** Amounts must be > 0 with at most 2 decimal places (OFFICIAL: zero/negative rejected).
- **BR-22** Deposit: account is a customer account in ACTIVE or FROZEN status.
- **BR-23** Withdrawal: account ACTIVE; balance after ≥ minimum balance.
- **BR-24** Transfer: source ≠ destination; both are customer accounts; source ACTIVE; destination ACTIVE or FROZEN; source balance after ≥ minimum balance (OFFICIAL: atomic, all-or-nothing).
- **BR-25** Transfer authorisation: if source and destination share at least one holder, it is an own-account transfer and needs no beneficiary. Otherwise the transfer must name an ACTIVE beneficiary that belongs to a holder of the source account and points to the destination account.

### 5.8 Beneficiaries
- **BR-26** A beneficiary is a customer's saved payee: an account in this bank. The (customer, payee account) pair is unique.
- **BR-27** A customer cannot add an account they hold as their own beneficiary. The payee account must not be CLOSED or internal.
- **BR-28** Beneficiaries are deactivated, not deleted. Only the nickname is updatable.

### 5.9 Charges
- **BR-29** Charges come from a catalogue of charge types, each with a default amount. The operator may override the amount (> 0).
- **BR-30** A charge is always a posted CHARGE transaction. Account must be ACTIVE (a debit).

### 5.10 Loans (academic calculation model)
- **BR-31** The borrower must be a holder of the disbursement/repayment account, and that account must be an ACTIVE customer account.
- **BR-32** Principal > 0; annual interest rate > 0 and ≤ 36%; tenure 1–360 months.
- **BR-33** Reducing-balance EMI: `EMI = P·r·(1+r)^n / ((1+r)^n − 1)`, with `r = annual rate / 12 / 100`, rounded HALF_EVEN to 2 dp. For instalment k: interest = round(outstanding × r); principal = EMI − interest. The final instalment's principal is the exact remaining outstanding, so scheduled principal sums exactly to P. Due date of instalment k = disbursement date + k months.
- **BR-34** A repayment pays exactly the earliest unpaid instalment, for exactly its amount, from the loan's account, subject to BR-11.
- **BR-35** Paid / unpaid / overdue status and loan ACTIVE / CLOSED are **derived**, not stored. Paid = a repayment exists. Overdue = unpaid and due date < today. Loan CLOSED = no unpaid instalment.
- **BR-36** Outstanding principal = P − Σ principal of paid instalments.

### 5.11 Statements
- **BR-37** A statement is generated on demand for an account and a date range: opening balance, every entry in the period (date, reference, type, narration, debit, credit, running balance) and closing balance. It is derived from the ledger, not stored (Review 1 decision).

## 6. Approved decisions log

| Date | Decision |
|---|---|
| Review 1 | Console UI, single operator, no auth, no multi-currency, no deposit interest accrual, no partial loan repayment, joint accounts via Account Holder, statements derived |
| 2026-10-03 | Rebuild design-first in this repository; port earlier code module by module after re-review |
| 2026-10-03 | Ledger = transaction header + debit/credit entries, with five internal accounts |
| 2026-10-03 | Lifecycle ACTIVE / FROZEN / BLOCKED / CLOSED with status history; third-party transfers only to ACTIVE beneficiaries |
| 2026-10-03 | No separate university requirements document; master prompt §5 is the baseline |
