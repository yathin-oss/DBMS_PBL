package com.bankdbms.service;

import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.ledger.AccountLocker;
import com.bankdbms.ledger.BalanceRule;
import com.bankdbms.ledger.LockedAccount;
import com.bankdbms.ledger.PostingEngine;
import com.bankdbms.ledger.PostingLine;
import com.bankdbms.ledger.PostingRequest;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.model.TransactionType;
import com.bankdbms.util.Money;
import com.bankdbms.util.Validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Cash deposits and withdrawals at the counter (FR-15, FR-16).
 * Deposit: DR CASH / CR customer. Withdrawal: DR customer / CR CASH.
 */
public class CashService {

    private final TransactionRunner tx;
    private final AccountLocker locker;
    private final PostingEngine engine;
    private final InternalAccounts internal;

    public CashService(TransactionRunner tx, AccountLocker locker, PostingEngine engine, InternalAccounts internal) {
        this.tx = tx;
        this.locker = locker;
        this.engine = engine;
        this.internal = internal;
    }

    /** BR-22: the account must be a customer account that accepts credits (ACTIVE or FROZEN). */
    public PostedTransaction deposit(int accountId, BigDecimal amount, String narration) {
        BigDecimal value = Money.requirePositive(amount, "Deposit amount");
        String note = narrationOrDefault(narration, "Cash deposit");
        return tx.inTransaction(conn -> {
            int cash = internal.id(conn, InternalAccount.CASH);
            requireCustomerAccount(locker.lock(conn, List.of(accountId, cash)), accountId);
            return engine.post(conn, PostingRequest.of(TransactionType.DEPOSIT, note,
                    PostingLine.debit(cash, value, BalanceRule.NON_NEGATIVE),
                    PostingLine.credit(accountId, value)));
        });
    }

    /** BR-23: ACTIVE account; balance after must stay at or above the product minimum. */
    public PostedTransaction withdraw(int accountId, BigDecimal amount, String narration) {
        BigDecimal value = Money.requirePositive(amount, "Withdrawal amount");
        String note = narrationOrDefault(narration, "Cash withdrawal");
        return tx.inTransaction(conn -> {
            int cash = internal.id(conn, InternalAccount.CASH);
            requireCustomerAccount(locker.lock(conn, List.of(accountId, cash)), accountId);
            return engine.post(conn, PostingRequest.of(TransactionType.WITHDRAWAL, note,
                    PostingLine.debit(accountId, value, BalanceRule.MIN_BALANCE),
                    PostingLine.credit(cash, value)));
        });
    }

    static void requireCustomerAccount(Map<Integer, LockedAccount> locked, int accountId) {
        if (!locked.get(accountId).isCustomerAccount()) {
            throw new BusinessRuleException("Internal bank accounts cannot be used for customer operations.");
        }
    }

    static String narrationOrDefault(String narration, String fallback) {
        String text = Validator.optionalText(narration, "Narration", 255);
        return text == null ? fallback : text;
    }
}
