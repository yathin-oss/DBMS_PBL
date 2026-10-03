package com.bankdbms.service;

import com.bankdbms.dao.ChargeDao;
import com.bankdbms.dao.ChargeTypeDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.ledger.AccountLocker;
import com.bankdbms.ledger.BalanceRule;
import com.bankdbms.ledger.PostingEngine;
import com.bankdbms.ledger.PostingLine;
import com.bankdbms.ledger.PostingRequest;
import com.bankdbms.model.ChargeType;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.model.TransactionType;
import com.bankdbms.util.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service charges as real ledger postings (FR-25, BR-29, BR-30): DR customer / CR FEE_INCOME,
 * plus a charge row recording the charge type. A charge may take the balance below the product
 * minimum but never below zero.
 */
public class ChargeService {

    private final TransactionRunner tx;
    private final AccountLocker locker;
    private final PostingEngine engine;
    private final InternalAccounts internal;
    private final ChargeTypeDao chargeTypeDao;
    private final ChargeDao chargeDao;

    public ChargeService(TransactionRunner tx, AccountLocker locker, PostingEngine engine, InternalAccounts internal,
                         ChargeTypeDao chargeTypeDao, ChargeDao chargeDao) {
        this.tx = tx;
        this.locker = locker;
        this.engine = engine;
        this.internal = internal;
        this.chargeTypeDao = chargeTypeDao;
        this.chargeDao = chargeDao;
    }

    public List<ChargeType> chargeTypes() {
        return tx.readOnly(chargeTypeDao::findAll);
    }

    /** @param amountOverride null to use the charge type's default amount */
    public PostedTransaction apply(int accountId, int chargeTypeId, BigDecimal amountOverride) {
        BigDecimal override = amountOverride == null ? null : Money.requirePositive(amountOverride, "Charge amount");
        return tx.inTransaction(conn -> {
            ChargeType type = chargeTypeDao.findById(conn, chargeTypeId)
                    .orElseThrow(() -> new NotFoundException("Charge type " + chargeTypeId + " not found."));
            BigDecimal amount = override == null ? type.defaultAmount() : override;
            int feeIncome = internal.id(conn, InternalAccount.FEE_INCOME);
            CashService.requireCustomerAccount(locker.lock(conn, List.of(accountId, feeIncome)), accountId);
            PostedTransaction posted = engine.post(conn, PostingRequest.of(TransactionType.CHARGE, type.chargeName(),
                    PostingLine.debit(accountId, amount, BalanceRule.NON_NEGATIVE),
                    PostingLine.credit(feeIncome, amount)));
            chargeDao.insert(conn, posted.transaction().transactionId(), chargeTypeId);
            return posted;
        });
    }
}
