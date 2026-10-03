package com.bankdbms.exception;

/** A UNIQUE rule was violated (duplicate email, IFSC code, beneficiary...). */
public class DuplicateRecordException extends BankException {
    public DuplicateRecordException(String message) {
        super(message);
    }
}
