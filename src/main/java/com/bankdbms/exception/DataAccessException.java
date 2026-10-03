package com.bankdbms.exception;

/**
 * An unexpected technical database failure. The message shown to the operator is generic;
 * the original SQLException is kept as the cause and written to the log file only.
 */
public class DataAccessException extends BankException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
