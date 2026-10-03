package com.bankdbms.exception;

/** The account's lifecycle status does not allow the requested operation. */
public class AccountStateException extends BusinessRuleException {
    public AccountStateException(String message) {
        super(message);
    }
}
