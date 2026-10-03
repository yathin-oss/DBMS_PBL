-- =============================================================================
-- 01_schema.sql  --  tables, keys, constraints, indexes (MySQL 8.0.16+, InnoDB)
--
-- Specification: docs/03_relational_design.md (data dictionary section 4).
-- Run as administrator against an EMPTY database, for example:
--     mysql -u root bank_dbms < sql/01_schema.sql
-- The script deliberately contains no USE and no DROP statements.
--
-- Conventions: money DECIMAL(15,2); every FK is RESTRICT (no cascading deletes
-- of financial data); constraint names are referenced by the Java error
-- translator, so rename them only together with SqlErrorTranslator.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. branch
-- -----------------------------------------------------------------------------
CREATE TABLE branch (
    branch_id    INT          NOT NULL AUTO_INCREMENT,
    ifsc_code    CHAR(11)     NOT NULL,
    branch_name  VARCHAR(100) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    city         VARCHAR(60)  NOT NULL,
    phone        VARCHAR(15)  NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_branch PRIMARY KEY (branch_id),
    CONSTRAINT uq_branch_ifsc UNIQUE (ifsc_code),
    CONSTRAINT chk_branch_ifsc CHECK (REGEXP_LIKE(ifsc_code, '^[A-Z]{4}0[A-Z0-9]{6}$', 'c')),
    CONSTRAINT chk_branch_phone CHECK (REGEXP_LIKE(phone, '^[+]?[0-9]{10,14}$')),
    CONSTRAINT chk_branch_name CHECK (TRIM(branch_name) <> '')
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 2. account_type  (banking product, or category of the bank's own accounts)
-- -----------------------------------------------------------------------------
CREATE TABLE account_type (
    account_type_id     INT           NOT NULL AUTO_INCREMENT,
    type_code           VARCHAR(20)   NOT NULL,
    type_name           VARCHAR(60)   NOT NULL,
    category            ENUM('CUSTOMER','INTERNAL') NOT NULL,
    normal_side         ENUM('DEBIT','CREDIT')      NOT NULL,
    min_opening_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    min_balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    max_holders         TINYINT       NOT NULL DEFAULT 1,
    interest_rate       DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    description         VARCHAR(255)  NULL,
    CONSTRAINT pk_account_type PRIMARY KEY (account_type_id),
    CONSTRAINT uq_account_type_code UNIQUE (type_code),
    CONSTRAINT chk_account_type_code CHECK (REGEXP_LIKE(type_code, '^[A-Z_]+$', 'c')),
    CONSTRAINT chk_account_type_min_opening CHECK (min_opening_balance >= 0),
    CONSTRAINT chk_account_type_min_balance CHECK (min_balance >= 0 AND min_balance <= min_opening_balance),
    CONSTRAINT chk_account_type_interest CHECK (interest_rate BETWEEN 0 AND 100),
    CONSTRAINT chk_account_type_category CHECK (
        (category = 'CUSTOMER' AND normal_side = 'CREDIT' AND max_holders BETWEEN 1 AND 4)
     OR (category = 'INTERNAL' AND max_holders = 0 AND min_opening_balance = 0 AND min_balance = 0))
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 3. customer
-- -----------------------------------------------------------------------------
CREATE TABLE customer (
    customer_id     INT          NOT NULL AUTO_INCREMENT,
    first_name      VARCHAR(50)  NOT NULL,
    last_name       VARCHAR(50)  NOT NULL,
    date_of_birth   DATE         NOT NULL,
    gender          ENUM('MALE','FEMALE','OTHER') NOT NULL,
    email           VARCHAR(100) NOT NULL,
    phone           VARCHAR(15)  NOT NULL,
    address         VARCHAR(255) NOT NULL,
    city            VARCHAR(60)  NOT NULL,
    id_proof_type   ENUM('PAN','AADHAAR','PASSPORT','VOTER_ID','DRIVING_LICENCE') NOT NULL,
    id_proof_number VARCHAR(20)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer PRIMARY KEY (customer_id),
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT uq_customer_id_proof UNIQUE (id_proof_type, id_proof_number),
    CONSTRAINT chk_customer_first_name CHECK (TRIM(first_name) <> ''),
    CONSTRAINT chk_customer_last_name CHECK (TRIM(last_name) <> ''),
    -- "not in the future" and "age >= 18" need CURRENT_DATE, which MySQL forbids in
    -- CHECK constraints; they are enforced in Java (BR-02, BR-03).
    CONSTRAINT chk_customer_dob CHECK (date_of_birth >= '1900-01-01'),
    CONSTRAINT chk_customer_email CHECK (REGEXP_LIKE(email, '^[^@ ]+@[^@ ]+[.][^@ ]+$')),
    CONSTRAINT chk_customer_phone CHECK (REGEXP_LIKE(phone, '^[+]?[0-9]{10,14}$'))
) ENGINE = InnoDB;

CREATE INDEX idx_customer_name ON customer (last_name, first_name);

-- -----------------------------------------------------------------------------
-- 4. account
-- -----------------------------------------------------------------------------
CREATE TABLE account (
    account_id      INT           NOT NULL AUTO_INCREMENT,
    account_number  CHAR(12)      NOT NULL,
    branch_id       INT           NOT NULL,
    account_type_id INT           NOT NULL,
    balance         DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status          ENUM('ACTIVE','FROZEN','BLOCKED','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    opened_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at       DATETIME      NULL,
    CONSTRAINT pk_account PRIMARY KEY (account_id),
    CONSTRAINT uq_account_number UNIQUE (account_number),
    CONSTRAINT fk_account_branch FOREIGN KEY (branch_id) REFERENCES branch (branch_id),
    CONSTRAINT fk_account_type FOREIGN KEY (account_type_id) REFERENCES account_type (account_type_id),
    CONSTRAINT chk_account_number CHECK (REGEXP_LIKE(account_number, '^[0-9]{12}$')),
    CONSTRAINT chk_account_balance CHECK (balance >= 0),
    CONSTRAINT chk_account_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT chk_account_dates CHECK (closed_at IS NULL OR closed_at >= opened_at)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 5. account_holder  (Customer M:N Account)
-- -----------------------------------------------------------------------------
CREATE TABLE account_holder (
    account_id         INT      NOT NULL,
    customer_id        INT      NOT NULL,
    holder_role        ENUM('PRIMARY','JOINT') NOT NULL,
    added_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Implementation-only: equals account_id for the PRIMARY holder, NULL otherwise.
    -- UNIQUE allows many NULLs, so this enforces "at most one PRIMARY per account".
    primary_account_id INT GENERATED ALWAYS AS (CASE WHEN holder_role = 'PRIMARY' THEN account_id END) STORED,
    CONSTRAINT pk_account_holder PRIMARY KEY (account_id, customer_id),
    CONSTRAINT uq_account_holder_primary UNIQUE (primary_account_id),
    CONSTRAINT fk_holder_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_holder_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 6. account_status_history  (append-only, see 02_triggers.sql)
-- -----------------------------------------------------------------------------
CREATE TABLE account_status_history (
    history_id  BIGINT       NOT NULL AUTO_INCREMENT,
    account_id  INT          NOT NULL,
    old_status  ENUM('ACTIVE','FROZEN','BLOCKED','CLOSED') NULL,
    new_status  ENUM('ACTIVE','FROZEN','BLOCKED','CLOSED') NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    changed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_account_status_history PRIMARY KEY (history_id),
    CONSTRAINT fk_history_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT chk_history_change CHECK (old_status IS NULL OR old_status <> new_status),
    CONSTRAINT chk_history_reason CHECK (TRIM(reason) <> '')
) ENGINE = InnoDB;

CREATE INDEX idx_history_account_time ON account_status_history (account_id, changed_at);

-- -----------------------------------------------------------------------------
-- 7. bank_transaction  (ledger header; immutable except POSTED -> REVERSED)
-- -----------------------------------------------------------------------------
CREATE TABLE bank_transaction (
    transaction_id   BIGINT       NOT NULL AUTO_INCREMENT,
    reference_no     CHAR(16)     NOT NULL,
    transaction_type ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','CHARGE','LOAN_DISBURSEMENT',
                          'LOAN_REPAYMENT','REVERSAL','CAPITAL_INJECTION') NOT NULL,
    status           ENUM('POSTED','REVERSED') NOT NULL DEFAULT 'POSTED',
    narration        VARCHAR(255) NULL,
    reversal_of_id   BIGINT       NULL,
    posted_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_bank_transaction PRIMARY KEY (transaction_id),
    CONSTRAINT uq_txn_reference UNIQUE (reference_no),
    CONSTRAINT uq_txn_reversal_of UNIQUE (reversal_of_id),
    -- Target of the composite FKs that tie each specialisation to its transaction type.
    CONSTRAINT uq_txn_id_type UNIQUE (transaction_id, transaction_type),
    CONSTRAINT fk_txn_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES bank_transaction (transaction_id),
    CONSTRAINT chk_txn_reference CHECK (REGEXP_LIKE(reference_no, '^TX[0-9]{8}[A-Z0-9]{6}$', 'c')),
    CONSTRAINT chk_txn_reversal_link CHECK ((transaction_type = 'REVERSAL') = (reversal_of_id IS NOT NULL)),
    CONSTRAINT chk_txn_reversal_status CHECK (NOT (transaction_type = 'REVERSAL' AND status = 'REVERSED'))
) ENGINE = InnoDB;

CREATE INDEX idx_txn_posted_at ON bank_transaction (posted_at);
CREATE INDEX idx_txn_type_posted ON bank_transaction (transaction_type, posted_at);

-- -----------------------------------------------------------------------------
-- 8. transaction_entry  (ledger line; immutable)
-- -----------------------------------------------------------------------------
CREATE TABLE transaction_entry (
    entry_id       BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT        NOT NULL,
    account_id     INT           NOT NULL,
    entry_side     ENUM('DEBIT','CREDIT') NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    balance_after  DECIMAL(15,2) NOT NULL,
    CONSTRAINT pk_transaction_entry PRIMARY KEY (entry_id),
    CONSTRAINT uq_entry_txn_account_side UNIQUE (transaction_id, account_id, entry_side),
    CONSTRAINT fk_entry_transaction FOREIGN KEY (transaction_id) REFERENCES bank_transaction (transaction_id),
    CONSTRAINT fk_entry_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT chk_entry_amount CHECK (amount > 0),
    CONSTRAINT chk_entry_balance_after CHECK (balance_after >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_entry_account_txn ON transaction_entry (account_id, transaction_id);

-- -----------------------------------------------------------------------------
-- 10. beneficiary  (created before transfer, which references it)
-- -----------------------------------------------------------------------------
CREATE TABLE beneficiary (
    beneficiary_id         INT         NOT NULL AUTO_INCREMENT,
    customer_id            INT         NOT NULL,
    beneficiary_account_id INT         NOT NULL,
    nickname               VARCHAR(50) NOT NULL,
    status                 ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    added_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at         DATETIME    NULL,
    CONSTRAINT pk_beneficiary PRIMARY KEY (beneficiary_id),
    CONSTRAINT uq_beneficiary_payee UNIQUE (customer_id, beneficiary_account_id),
    CONSTRAINT uq_beneficiary_nickname UNIQUE (customer_id, nickname),
    CONSTRAINT fk_beneficiary_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT fk_beneficiary_account FOREIGN KEY (beneficiary_account_id) REFERENCES account (account_id),
    CONSTRAINT chk_beneficiary_nickname CHECK (TRIM(nickname) <> ''),
    CONSTRAINT chk_beneficiary_status CHECK ((status = 'INACTIVE') = (deactivated_at IS NOT NULL))
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 9. transfer  (specialisation of bank_transaction)
-- -----------------------------------------------------------------------------
CREATE TABLE transfer (
    transaction_id   BIGINT       NOT NULL,
    transaction_type ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','CHARGE','LOAN_DISBURSEMENT',
                          'LOAN_REPAYMENT','REVERSAL','CAPITAL_INJECTION') NOT NULL DEFAULT 'TRANSFER',
    beneficiary_id   INT          NULL,
    remarks          VARCHAR(140) NULL,
    CONSTRAINT pk_transfer PRIMARY KEY (transaction_id),
    CONSTRAINT fk_transfer_transaction FOREIGN KEY (transaction_id, transaction_type)
        REFERENCES bank_transaction (transaction_id, transaction_type),
    CONSTRAINT fk_transfer_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiary (beneficiary_id),
    CONSTRAINT chk_transfer_type CHECK (transaction_type = 'TRANSFER')
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 11. charge_type
-- -----------------------------------------------------------------------------
CREATE TABLE charge_type (
    charge_type_id INT           NOT NULL AUTO_INCREMENT,
    charge_code    VARCHAR(30)   NOT NULL,
    charge_name    VARCHAR(80)   NOT NULL,
    default_amount DECIMAL(15,2) NOT NULL,
    CONSTRAINT pk_charge_type PRIMARY KEY (charge_type_id),
    CONSTRAINT uq_charge_type_code UNIQUE (charge_code),
    CONSTRAINT chk_charge_type_code CHECK (REGEXP_LIKE(charge_code, '^[A-Z_]+$', 'c')),
    CONSTRAINT chk_charge_type_amount CHECK (default_amount > 0)
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 12. charge  (specialisation of bank_transaction)
-- -----------------------------------------------------------------------------
CREATE TABLE charge (
    transaction_id   BIGINT NOT NULL,
    transaction_type ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','CHARGE','LOAN_DISBURSEMENT',
                          'LOAN_REPAYMENT','REVERSAL','CAPITAL_INJECTION') NOT NULL DEFAULT 'CHARGE',
    charge_type_id   INT    NOT NULL,
    CONSTRAINT pk_charge PRIMARY KEY (transaction_id),
    CONSTRAINT fk_charge_transaction FOREIGN KEY (transaction_id, transaction_type)
        REFERENCES bank_transaction (transaction_id, transaction_type),
    CONSTRAINT fk_charge_type FOREIGN KEY (charge_type_id) REFERENCES charge_type (charge_type_id),
    CONSTRAINT chk_charge_type CHECK (transaction_type = 'CHARGE')
) ENGINE = InnoDB;

-- -----------------------------------------------------------------------------
-- 13. loan
-- -----------------------------------------------------------------------------
CREATE TABLE loan (
    loan_id               INT           NOT NULL AUTO_INCREMENT,
    loan_number           CHAR(14)      NOT NULL,
    account_id            INT           NOT NULL,
    customer_id           INT           NOT NULL,
    loan_type             ENUM('PERSONAL','HOME','VEHICLE','EDUCATION') NOT NULL,
    principal             DECIMAL(15,2) NOT NULL,
    annual_interest_rate  DECIMAL(5,2)  NOT NULL,
    tenure_months         SMALLINT      NOT NULL,
    disbursement_txn_id   BIGINT        NOT NULL,
    disbursement_txn_type ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','CHARGE','LOAN_DISBURSEMENT',
                               'LOAN_REPAYMENT','REVERSAL','CAPITAL_INJECTION') NOT NULL DEFAULT 'LOAN_DISBURSEMENT',
    CONSTRAINT pk_loan PRIMARY KEY (loan_id),
    CONSTRAINT uq_loan_number UNIQUE (loan_number),
    CONSTRAINT uq_loan_disbursement UNIQUE (disbursement_txn_id),
    -- The borrower must hold the loan's account (BR-31): FK to the holding itself.
    CONSTRAINT fk_loan_holding FOREIGN KEY (account_id, customer_id)
        REFERENCES account_holder (account_id, customer_id),
    CONSTRAINT fk_loan_disbursement FOREIGN KEY (disbursement_txn_id, disbursement_txn_type)
        REFERENCES bank_transaction (transaction_id, transaction_type),
    CONSTRAINT chk_loan_number CHECK (REGEXP_LIKE(loan_number, '^LN[0-9]{12}$', 'c')),
    CONSTRAINT chk_loan_principal CHECK (principal > 0),
    CONSTRAINT chk_loan_rate CHECK (annual_interest_rate > 0 AND annual_interest_rate <= 36),
    CONSTRAINT chk_loan_tenure CHECK (tenure_months BETWEEN 1 AND 360),
    CONSTRAINT chk_loan_disbursement_type CHECK (disbursement_txn_type = 'LOAN_DISBURSEMENT')
) ENGINE = InnoDB;

CREATE INDEX idx_loan_customer ON loan (customer_id);

-- -----------------------------------------------------------------------------
-- 14. loan_schedule  (weak entity of loan)
-- -----------------------------------------------------------------------------
CREATE TABLE loan_schedule (
    loan_id             INT           NOT NULL,
    installment_no      SMALLINT      NOT NULL,
    due_date            DATE          NOT NULL,
    principal_component DECIMAL(15,2) NOT NULL,
    interest_component  DECIMAL(15,2) NOT NULL,
    -- Derived attribute maintained by MySQL; never written by the application.
    installment_amount  DECIMAL(15,2) GENERATED ALWAYS AS (principal_component + interest_component) STORED,
    CONSTRAINT pk_loan_schedule PRIMARY KEY (loan_id, installment_no),
    CONSTRAINT uq_schedule_due_date UNIQUE (loan_id, due_date),
    CONSTRAINT fk_schedule_loan FOREIGN KEY (loan_id) REFERENCES loan (loan_id),
    CONSTRAINT chk_schedule_installment_no CHECK (installment_no >= 1),
    CONSTRAINT chk_schedule_principal CHECK (principal_component > 0),
    CONSTRAINT chk_schedule_interest CHECK (interest_component >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_schedule_due_date ON loan_schedule (due_date);

-- -----------------------------------------------------------------------------
-- 15. loan_repayment  (specialisation of bank_transaction; settles one instalment)
-- -----------------------------------------------------------------------------
CREATE TABLE loan_repayment (
    transaction_id   BIGINT   NOT NULL,
    transaction_type ENUM('DEPOSIT','WITHDRAWAL','TRANSFER','CHARGE','LOAN_DISBURSEMENT',
                          'LOAN_REPAYMENT','REVERSAL','CAPITAL_INJECTION') NOT NULL DEFAULT 'LOAN_REPAYMENT',
    loan_id          INT      NOT NULL,
    installment_no   SMALLINT NOT NULL,
    CONSTRAINT pk_loan_repayment PRIMARY KEY (transaction_id),
    CONSTRAINT uq_repayment_installment UNIQUE (loan_id, installment_no),
    CONSTRAINT fk_repayment_transaction FOREIGN KEY (transaction_id, transaction_type)
        REFERENCES bank_transaction (transaction_id, transaction_type),
    CONSTRAINT fk_repayment_installment FOREIGN KEY (loan_id, installment_no)
        REFERENCES loan_schedule (loan_id, installment_no),
    CONSTRAINT chk_repayment_type CHECK (transaction_type = 'LOAN_REPAYMENT')
) ENGINE = InnoDB;
