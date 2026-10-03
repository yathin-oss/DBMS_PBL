package com.bankdbms.service;

import com.bankdbms.dao.AccountHolderDao;
import com.bankdbms.dao.BeneficiaryDao;
import com.bankdbms.dao.TransferDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.ledger.AccountLocker;
import com.bankdbms.ledger.BalanceRule;
import com.bankdbms.ledger.LockedAccount;
import com.bankdbms.ledger.PostingEngine;
import com.bankdbms.ledger.PostingLine;
import com.bankdbms.ledger.PostingRequest;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.BeneficiaryStatus;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.model.TransactionType;
import com.bankdbms.util.Money;
import com.bankdbms.util.Validator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Atomic fund transfer between two customer accounts (FR-17, BR-24, BR-25).
 *
 * <p>Sequence, all inside one JDBC transaction: validate input, lock both accounts (ascending
 * id), check both are customer accounts, check the beneficiary authorisation, then post
 * DR source / CR destination (the engine checks statuses and the minimum balance) and record the
 * transfer row. Any failure rolls back everything, so a partial transfer cannot exist.
 */
public class TransferService {

    private final TransactionRunner tx;
    private final AccountLocker locker;
    private final PostingEngine engine;
    private final AccountHolderDao holderDao;
    private final BeneficiaryDao beneficiaryDao;
    private final TransferDao transferDao;

    public TransferService(TransactionRunner tx, AccountLocker locker, PostingEngine engine,
                           AccountHolderDao holderDao, BeneficiaryDao beneficiaryDao, TransferDao transferDao) {
        this.tx = tx;
        this.locker = locker;
        this.engine = engine;
        this.holderDao = holderDao;
        this.beneficiaryDao = beneficiaryDao;
        this.transferDao = transferDao;
    }

    /**
     * @param beneficiaryId required unless source and destination share a holder (own-account transfer)
     */
    public PostedTransaction transfer(int fromAccountId, int toAccountId, BigDecimal amount, Integer beneficiaryId,
                                      String remarks) {
        BigDecimal value = Money.requirePositive(amount, "Transfer amount");
        String note = Validator.optionalText(remarks, "Remarks", 140);
        if (fromAccountId == toAccountId) {
            throw new ValidationException("Source and destination accounts must be different.");
        }
        return tx.inTransaction(conn -> {
            Map<Integer, LockedAccount> locked = locker.lock(conn, List.of(fromAccountId, toAccountId));
            LockedAccount from = locked.get(fromAccountId);
            LockedAccount to = locked.get(toAccountId);
            if (!from.isCustomerAccount() || !to.isCustomerAccount()) {
                throw new BusinessRuleException("Transfers are only allowed between customer accounts.");
            }
            authorise(conn, from, to, beneficiaryId);
            String narration = "Transfer " + from.account().accountNumber() + " -> " + to.account().accountNumber();
            PostedTransaction posted = engine.post(conn, PostingRequest.of(TransactionType.TRANSFER, narration,
                    PostingLine.debit(fromAccountId, value, BalanceRule.MIN_BALANCE),
                    PostingLine.credit(toAccountId, value)));
            transferDao.insert(conn, posted.transaction().transactionId(), beneficiaryId, note);
            return posted;
        });
    }

    /** Transfer to a saved beneficiary: the destination is the beneficiary's account. */
    public PostedTransaction transferToBeneficiary(int fromAccountId, int beneficiaryId, BigDecimal amount,
                                                   String remarks) {
        Beneficiary b = tx.readOnly(conn -> beneficiaryDao.findById(conn, beneficiaryId))
                .orElseThrow(() -> new NotFoundException("Beneficiary " + beneficiaryId + " not found."));
        return transfer(fromAccountId, b.beneficiaryAccountId(), amount, beneficiaryId, remarks);
    }

    /** BR-25: own-account transfers need no beneficiary; all others need a valid ACTIVE one. */
    private void authorise(Connection conn, LockedAccount from, LockedAccount to, Integer beneficiaryId)
            throws SQLException {
        boolean ownAccounts = holderDao.shareAHolder(conn, from.id(), to.id());
        if (beneficiaryId == null) {
            if (!ownAccounts) {
                throw new BusinessRuleException("Account " + to.account().accountNumber()
                        + " belongs to a different customer: add it as a beneficiary first.");
            }
            return;
        }
        Beneficiary b = beneficiaryDao.findById(conn, beneficiaryId)
                .orElseThrow(() -> new NotFoundException("Beneficiary " + beneficiaryId + " not found."));
        if (b.status() != BeneficiaryStatus.ACTIVE) {
            throw new BusinessRuleException("Beneficiary '" + b.nickname() + "' is inactive.");
        }
        if (b.beneficiaryAccountId() != to.id()) {
            throw new BusinessRuleException("Beneficiary '" + b.nickname() + "' does not point to account "
                    + to.account().accountNumber() + ".");
        }
        if (!holderDao.isHolder(conn, from.id(), b.customerId())) {
            throw new BusinessRuleException("Beneficiary '" + b.nickname()
                    + "' does not belong to a holder of the source account.");
        }
    }
}
