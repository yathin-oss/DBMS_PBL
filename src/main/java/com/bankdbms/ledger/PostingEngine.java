package com.bankdbms.ledger;

import com.bankdbms.dao.AccountDao;
import com.bankdbms.dao.TransactionDao;
import com.bankdbms.db.SqlErrorTranslator;
import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.InsufficientFundsException;
import com.bankdbms.model.BankTransaction;
import com.bankdbms.model.EntrySide;
import com.bankdbms.model.LedgerEntry;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.util.BankClock;
import com.bankdbms.util.Money;
import com.bankdbms.util.ReferenceGenerator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single path through which money moves (double-entry posting, business rules BR-16 to BR-20).
 *
 * <p>{@link #post} must be called inside a transaction opened by the service layer. It:
 * <ol>
 *   <li>checks the request is balanced (total debits = total credits, at least two lines);</li>
 *   <li>locks every affected account in ascending id order;</li>
 *   <li>applies each line to the locked balance using the account's normal side, enforcing the
 *       lifecycle (which statuses accept debits/credits) and the balance rule;</li>
 *   <li>inserts the transaction header and its entries (each with balance_after) and updates the
 *       stored balances.</li>
 * </ol>
 * Any failure throws; the caller's transaction then rolls back, so a posting is all-or-nothing.
 */
public class PostingEngine {

    private static final int REFERENCE_ATTEMPTS = 5;

    private final AccountLocker locker;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final ReferenceGenerator references;
    private final BankClock clock;
    private final PostingFaultInjector faultInjector;

    public PostingEngine(AccountLocker locker, AccountDao accountDao, TransactionDao transactionDao,
                         ReferenceGenerator references, BankClock clock, PostingFaultInjector faultInjector) {
        this.locker = locker;
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.references = references;
        this.clock = clock;
        this.faultInjector = faultInjector;
    }

    public PostedTransaction post(Connection conn, PostingRequest request) throws SQLException {
        requireBalanced(request);
        Set<Integer> accountIds = new HashSet<>();
        request.lines().forEach(line -> accountIds.add(line.accountId()));
        Map<Integer, LockedAccount> locked = locker.lock(conn, accountIds);

        List<BigDecimal> balancesAfter = applyLines(request.lines(), locked);

        LocalDateTime postedAt = clock.now();
        long txnId = insertHeader(conn, request, postedAt);
        List<LedgerEntry> entries = new ArrayList<>();
        Map<Integer, BigDecimal> finalBalances = new HashMap<>();
        for (int i = 0; i < request.lines().size(); i++) {
            PostingLine line = request.lines().get(i);
            BigDecimal after = balancesAfter.get(i);
            long entryId = transactionDao.insertEntry(conn, txnId, line.accountId(), line.side(), line.amount(), after);
            entries.add(new LedgerEntry(entryId, txnId, line.accountId(), line.side(), line.amount(), after));
            finalBalances.put(line.accountId(), after);
            faultInjector.afterEntryInserted(i);
        }
        for (Map.Entry<Integer, BigDecimal> balance : finalBalances.entrySet()) {
            accountDao.updateBalance(conn, balance.getKey(), balance.getValue());
        }
        BankTransaction header = transactionDao.findById(conn, txnId)
                .orElseThrow(() -> new IllegalStateException("Posted transaction vanished: " + txnId));
        return new PostedTransaction(header, entries);
    }

    /** Validates amounts and the debit = credit rule before anything is locked or written. */
    static void requireBalanced(PostingRequest request) {
        if (request.lines().size() < 2) {
            throw new IllegalArgumentException("A posting needs at least one debit and one credit line");
        }
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();
        for (PostingLine line : request.lines()) {
            Money.requirePositive(line.amount(), "Amount");
            if (!seen.add(line.accountId() + ":" + line.side())) {
                throw new IllegalArgumentException("Duplicate " + line.side() + " line for account " + line.accountId());
            }
            if (line.side() == EntrySide.DEBIT) {
                debits = debits.add(line.amount());
            } else {
                credits = credits.add(line.amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException("Unbalanced posting: debits " + debits + " <> credits " + credits);
        }
    }

    private List<BigDecimal> applyLines(List<PostingLine> lines, Map<Integer, LockedAccount> locked) {
        Map<Integer, BigDecimal> running = new HashMap<>();
        locked.forEach((id, acc) -> running.put(id, acc.account().balance()));
        List<BigDecimal> after = new ArrayList<>(lines.size());
        for (PostingLine line : lines) {
            LockedAccount acc = locked.get(line.accountId());
            requireStatusAllows(acc, line.side());
            boolean increases = line.side() == acc.type().normalSide();
            BigDecimal next = increases ? running.get(acc.id()).add(line.amount())
                                        : running.get(acc.id()).subtract(line.amount());
            requireBalanceRule(acc, line, next);
            running.put(acc.id(), next);
            after.add(next);
        }
        return after;
    }

    private static void requireStatusAllows(LockedAccount acc, EntrySide side) {
        if (!acc.account().status().allows(side)) {
            String operation = side == EntrySide.DEBIT ? "debited" : "credited";
            throw new AccountStateException("Account " + acc.account().accountNumber() + " is "
                    + acc.account().status() + " and cannot be " + operation + ".");
        }
    }

    private static void requireBalanceRule(LockedAccount acc, PostingLine line, BigDecimal next) {
        if (next.signum() < 0) {
            if (acc.isCustomerAccount()) {
                throw new InsufficientFundsException("Insufficient funds in account "
                        + acc.account().accountNumber() + ": available balance is "
                        + Money.format(acc.account().balance()) + ".");
            }
            throw new BusinessRuleException("The bank's internal account " + acc.account().accountNumber()
                    + " cannot cover this operation.");
        }
        boolean reducesCustomerBalance = acc.isCustomerAccount() && line.side() != acc.type().normalSide();
        if (reducesCustomerBalance && line.rule() == BalanceRule.MIN_BALANCE
                && next.compareTo(acc.type().minBalance()) < 0) {
            throw new InsufficientFundsException("Account " + acc.account().accountNumber()
                    + " must keep a minimum balance of " + Money.format(acc.type().minBalance())
                    + "; at most " + Money.format(acc.account().balance().subtract(acc.type().minBalance()).max(BigDecimal.ZERO))
                    + " can be debited.");
        }
    }

    private long insertHeader(Connection conn, PostingRequest request, LocalDateTime postedAt) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            String reference = references.transactionReference(postedAt.toLocalDate());
            try {
                return transactionDao.insertHeader(conn, reference, request.type(), request.narration(),
                        request.reversalOfId(), postedAt);
            } catch (SQLException e) {
                if (!SqlErrorTranslator.isDuplicateOn(e, "uq_txn_reference") || attempt == REFERENCE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
