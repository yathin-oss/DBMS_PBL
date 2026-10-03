package com.bankdbms.exception;

/** A debit would take a balance below zero or below the product's minimum balance. */
public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
