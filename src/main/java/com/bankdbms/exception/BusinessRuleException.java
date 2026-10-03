package com.bankdbms.exception;

/** A well-formed request that a banking rule forbids (lifecycle, beneficiary, loan rules...). */
public class BusinessRuleException extends BankException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
