package com.bankdbms.exception;

/** A referenced record (customer, account, loan...) does not exist. */
public class NotFoundException extends BankException {
    public NotFoundException(String message) {
        super(message);
    }
}
