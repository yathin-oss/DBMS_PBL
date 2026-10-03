package com.bankdbms.db;

import com.bankdbms.exception.BankException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.DataAccessException;
import com.bankdbms.exception.DuplicateRecordException;
import com.bankdbms.exception.ValidationException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts {@link SQLException}s into operator-friendly {@link BankException}s.
 *
 * <p>The database is the final integrity boundary (NFR-04): if a rule slips past Java
 * validation, MySQL rejects the row and this class explains the rejection in plain language,
 * using the constraint names defined in {@code sql/01_schema.sql}. Raw server messages, SQL text
 * and stack traces are written to the log only and never shown to the operator (NFR-07).
 */
public final class SqlErrorTranslator {

    private static final Logger LOG = Logger.getLogger(SqlErrorTranslator.class.getName());

    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_BAD_NULL = 1048;
    private static final int ER_NO_REFERENCED_ROW = 1452;
    private static final int ER_ROW_IS_REFERENCED = 1451;
    private static final int ER_DATA_TOO_LONG = 1406;
    private static final int ER_CHECK_VIOLATED = 3819;
    private static final int ER_SIGNAL = 1644;
    private static final int ER_LOCK_DEADLOCK = 1213;
    private static final int ER_LOCK_WAIT_TIMEOUT = 1205;

    private static final Map<String, String> MESSAGES = new LinkedHashMap<>();

    static {
        // UNIQUE constraints
        MESSAGES.put("uq_branch_ifsc", "A branch with this IFSC code already exists.");
        MESSAGES.put("uq_account_type_code", "An account type with this code already exists.");
        MESSAGES.put("uq_customer_email", "A customer with this email address already exists.");
        MESSAGES.put("uq_customer_id_proof", "A customer with this ID proof already exists.");
        MESSAGES.put("uq_account_number", "This account number is already in use.");
        MESSAGES.put("pk_account_holder", "This customer is already a holder of the account.");
        MESSAGES.put("uq_account_holder_primary", "The account already has a primary holder.");
        MESSAGES.put("uq_txn_reversal_of", "This transaction has already been reversed.");
        MESSAGES.put("uq_beneficiary_payee", "This account is already saved as a beneficiary of the customer.");
        MESSAGES.put("uq_beneficiary_nickname", "The customer already has a beneficiary with this nickname.");
        MESSAGES.put("uq_charge_type_code", "A charge type with this code already exists.");
        MESSAGES.put("uq_repayment_installment", "This instalment has already been repaid.");
        MESSAGES.put("uq_loan_number", "This loan number is already in use.");
        // FOREIGN KEY constraints
        MESSAGES.put("fk_account_branch", "The selected branch does not exist.");
        MESSAGES.put("fk_account_type", "The selected account type does not exist.");
        MESSAGES.put("fk_holder_customer", "The selected customer does not exist.");
        MESSAGES.put("fk_holder_account", "The selected account does not exist.");
        MESSAGES.put("fk_loan_holding", "The borrower must be a holder of the selected account.");
        MESSAGES.put("fk_beneficiary_customer", "The selected customer does not exist.");
        MESSAGES.put("fk_beneficiary_account", "The beneficiary account does not exist.");
        MESSAGES.put("fk_charge_type", "The selected charge type does not exist.");
        // CHECK constraints
        MESSAGES.put("chk_account_balance", "Insufficient funds: the balance cannot become negative.");
        MESSAGES.put("chk_entry_balance_after", "Insufficient funds: the balance cannot become negative.");
        MESSAGES.put("chk_entry_amount", "Amounts must be greater than zero.");
        MESSAGES.put("chk_branch_ifsc", "IFSC code must be 4 letters, a zero, then 6 letters or digits (e.g. BDBS0000102).");
        MESSAGES.put("chk_branch_phone", "Phone number must contain 10 to 14 digits.");
        MESSAGES.put("chk_customer_phone", "Phone number must contain 10 to 14 digits.");
        MESSAGES.put("chk_customer_email", "Email address is not valid.");
        MESSAGES.put("chk_customer_dob", "Date of birth is not valid.");
        MESSAGES.put("chk_loan_rate", "Interest rate must be above 0% and at most 36%.");
        MESSAGES.put("chk_loan_tenure", "Tenure must be between 1 and 360 months.");
        MESSAGES.put("chk_loan_principal", "Loan principal must be greater than zero.");
        MESSAGES.put("chk_account_type_category", "Account type settings are inconsistent with its category.");
        MESSAGES.put("chk_account_type_min_balance", "Minimum balance must be between 0 and the minimum opening balance.");
    }

    private SqlErrorTranslator() {
    }

    public static BankException translate(SQLException e) {
        String serverMessage = e.getMessage() == null ? "" : e.getMessage();
        switch (e.getErrorCode()) {
            case ER_DUP_ENTRY:
                return expected(e, new DuplicateRecordException(friendly(serverMessage, "This record already exists.")));
            case ER_NO_REFERENCED_ROW:
                return expected(e, new BusinessRuleException(friendly(serverMessage, "A referenced record does not exist.")));
            case ER_ROW_IS_REFERENCED:
                return expected(e, new BusinessRuleException("The record is in use and cannot be removed."));
            case ER_CHECK_VIOLATED:
                return expected(e, new ValidationException(friendly(serverMessage, "A value breaks a data rule.")));
            case ER_BAD_NULL:
                return expected(e, new ValidationException("A required value is missing."));
            case ER_DATA_TOO_LONG:
                return expected(e, new ValidationException("A value is too long."));
            case ER_SIGNAL:
                // Raised by our own triggers; their messages are written for operators.
                return expected(e, new BusinessRuleException(serverMessage));
            case ER_LOCK_DEADLOCK:
            case ER_LOCK_WAIT_TIMEOUT:
                return expected(e, new BusinessRuleException("The account is busy with another operation. Please try again."));
            default:
                LOG.log(Level.SEVERE, "Unexpected database error: SQLState=" + e.getSQLState()
                        + " code=" + e.getErrorCode() + " message=" + serverMessage, e);
                return new DataAccessException("A database error occurred. The operation was not completed.", e);
        }
    }

    /** True when the exception is a duplicate-key error on the named UNIQUE constraint. */
    public static boolean isDuplicateOn(SQLException e, String constraintName) {
        return e.getErrorCode() == ER_DUP_ENTRY && e.getMessage() != null && e.getMessage().contains(constraintName);
    }

    private static String friendly(String serverMessage, String fallback) {
        for (Map.Entry<String, String> entry : MESSAGES.entrySet()) {
            if (serverMessage.contains("'" + entry.getKey() + "'") || serverMessage.contains("." + entry.getKey() + "'")
                    || serverMessage.contains("`" + entry.getKey() + "`")) {
                return entry.getValue();
            }
        }
        return fallback;
    }

    private static BankException expected(SQLException e, BankException translated) {
        LOG.log(Level.FINE, "Database rejected operation: code=" + e.getErrorCode() + " message=" + e.getMessage());
        return translated;
    }
}
