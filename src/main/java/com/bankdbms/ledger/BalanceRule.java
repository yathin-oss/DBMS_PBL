package com.bankdbms.ledger;

/**
 * How far a debit may reduce a customer account (business rule BR-11).
 * <ul>
 *   <li>{@link #MIN_BALANCE}: customer-initiated debits (withdrawal, transfer, EMI) must leave at
 *       least the product's minimum balance;</li>
 *   <li>{@link #NON_NEGATIVE}: bank-initiated debits (charges, reversals, closure payout) may go
 *       below the minimum but never below zero.</li>
 * </ul>
 */
public enum BalanceRule {
    MIN_BALANCE, NON_NEGATIVE
}
