package com.bankdbms.exception;

/** Input that is malformed or out of range (bad amount, blank name, invalid email...). */
public class ValidationException extends BankException {
    public ValidationException(String message) {
        super(message);
    }
}
