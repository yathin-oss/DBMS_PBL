package com.bankdbms.exception;

/**
 * Base class for every error the application reports to the operator.
 * Messages are always safe to display: they never contain SQL, stack traces or credentials.
 */
public class BankException extends RuntimeException {
    public BankException(String message) {
        super(message);
    }

    public BankException(String message, Throwable cause) {
        super(message, cause);
    }
}
