package com.bankdbms.service;

import com.bankdbms.dao.TransactionDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.ledger.AccountLocker;
import com.bankdbms.ledger.BalanceRule;
import com.bankdbms.ledger.PostingEngine;
import com.bankdbms.ledger.PostingLine;
import com.bankdbms.ledger.PostingRequest;
import com.bankdbms.model.BankTransaction;
import com.bankdbms.model.LedgerEntry;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.model.TransactionStatus;
import com.bankdbms.model.TransactionType;
import com.bankdbms.util.Validator;

import java.util.ArrayList;
import java.util.List;

/**
 * Corrects a posted transaction without editing history (FR-19, BR-19): a new REVERSAL transaction
 * mirrors every entry of the original (debits become credits and vice versa), and the original is
 * marked REVERSED. Both happen in one database transaction. Normal lifecycle and balance rules apply
 * to the reversal (e.g. a deposit cannot be reversed if the money has already been withdrawn).
 */
public class ReversalService {

    private final TransactionRunner tx;
    private final TransactionDao transactionDao;
    private final AccountLocker locker;
    private final PostingEngine engine;

    public ReversalService(TransactionRunner tx, TransactionDao transactionDao, AccountLocker locker,
                           PostingEngine engine) {
        this.tx = tx;
        this.transactionDao = transactionDao;
        this.locker = locker;
        this.engine = engine;
    }

    public PostedTransaction reverse(String referenceNo, String reason) {
        String ref = Validator.requireText(referenceNo, "Transaction reference", 16).toUpperCase();
        String why = Validator.requireText(reason, "Reason", 200);
        return tx.inTransaction(conn -> {
            long id = transactionDao.findByReference(conn, ref)
                    .orElseThrow(() -> new NotFoundException("Transaction " + ref + " not found."))
                    .transactionId();
            BankTransaction original = transactionDao.findByIdForUpdate(conn, id).orElseThrow();
            if (!TransactionType.REVERSIBLE.contains(original.type())) {
                throw new BusinessRuleException(original.type() + " transactions cannot be reversed.");
            }
            if (original.status() != TransactionStatus.POSTED) {
                throw new BusinessRuleException("Transaction " + ref + " has already been reversed.");
            }
            List<LedgerEntry> entries = transactionDao.findEntries(conn, id);
            List<Integer> accountIds = new ArrayList<>();
            List<PostingLine> mirrored = new ArrayList<>();
            for (LedgerEntry e : entries) {
                accountIds.add(e.accountId());
                mirrored.add(new PostingLine(e.accountId(), e.side().opposite(), e.amount(), BalanceRule.NON_NEGATIVE));
            }
            locker.lock(conn, accountIds);
            PostedTransaction reversal = engine.post(conn, new PostingRequest(TransactionType.REVERSAL,
                    "Reversal of " + ref + ": " + why, id, mirrored));
            transactionDao.markReversed(conn, id);
            return reversal;
        });
    }
}
