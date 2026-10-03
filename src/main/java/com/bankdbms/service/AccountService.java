package com.bankdbms.service;

import com.bankdbms.dao.AccountDao;
import com.bankdbms.dao.AccountHolderDao;
import com.bankdbms.dao.AccountTypeDao;
import com.bankdbms.dao.BranchDao;
import com.bankdbms.dao.CustomerDao;
import com.bankdbms.dao.LoanDao;
import com.bankdbms.dao.StatusHistoryDao;
import com.bankdbms.db.SqlErrorTranslator;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.ledger.AccountLocker;
import com.bankdbms.ledger.BalanceRule;
import com.bankdbms.ledger.LockedAccount;
import com.bankdbms.ledger.PostingEngine;
import com.bankdbms.ledger.PostingLine;
import com.bankdbms.ledger.PostingRequest;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountHolder;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.AccountType;
import com.bankdbms.model.Customer;
import com.bankdbms.model.HolderRole;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.StatusChange;
import com.bankdbms.model.TransactionType;
import com.bankdbms.util.BankClock;
import com.bankdbms.util.Money;
import com.bankdbms.util.ReferenceGenerator;
import com.bankdbms.util.Validator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Account opening, holders, lookup and lifecycle (FR-11 to FR-14, BR-07 to BR-15).
 */
public class AccountService {

    private static final int ACCOUNT_NUMBER_ATTEMPTS = 5;

    private final TransactionRunner tx;
    private final AccountDao accountDao;
    private final AccountHolderDao holderDao;
    private final StatusHistoryDao historyDao;
    private final CustomerDao customerDao;
    private final BranchDao branchDao;
    private final AccountTypeDao accountTypeDao;
    private final LoanDao loanDao;
    private final AccountLocker locker;
    private final PostingEngine engine;
    private final InternalAccounts internal;
    private final ReferenceGenerator references;
    private final BankClock clock;

    public AccountService(TransactionRunner tx, AccountDao accountDao, AccountHolderDao holderDao,
                          StatusHistoryDao historyDao, CustomerDao customerDao, BranchDao branchDao,
                          AccountTypeDao accountTypeDao, LoanDao loanDao, AccountLocker locker, PostingEngine engine,
                          InternalAccounts internal, ReferenceGenerator references, BankClock clock) {
        this.tx = tx;
        this.accountDao = accountDao;
        this.holderDao = holderDao;
        this.historyDao = historyDao;
        this.customerDao = customerDao;
        this.branchDao = branchDao;
        this.accountTypeDao = accountTypeDao;
        this.loanDao = loanDao;
        this.locker = locker;
        this.engine = engine;
        this.internal = internal;
        this.references = references;
        this.clock = clock;
    }

    /**
     * Opens an account in one database transaction: account row, PRIMARY holder, opening status
     * history, and the opening deposit posted to the ledger (BR-09). Nothing is saved if any step fails.
     */
    public Account open(int customerId, int branchId, int accountTypeId, BigDecimal openingDeposit) {
        BigDecimal deposit = Money.requireNonNegative(openingDeposit, "Opening deposit");
        return tx.inTransaction(conn -> {
            requireAdultCustomer(conn, customerId);
            branchDao.findById(conn, branchId)
                    .orElseThrow(() -> new NotFoundException("Branch " + branchId + " not found."));
            AccountType product = accountTypeDao.findById(conn, accountTypeId)
                    .orElseThrow(() -> new NotFoundException("Account type " + accountTypeId + " not found."));
            if (!product.isCustomerProduct()) {
                throw new BusinessRuleException("Account type " + product.typeCode() + " is internal to the bank.");
            }
            if (deposit.compareTo(product.minOpeningBalance()) < 0) {
                throw new ValidationException(product.typeName() + " requires an opening deposit of at least "
                        + Money.format(product.minOpeningBalance()) + ".");
            }
            LocalDateTime now = clock.now();
            int accountId = insertWithUniqueNumber(conn, branchId, accountTypeId, now);
            holderDao.insert(conn, accountId, customerId, HolderRole.PRIMARY, now);
            historyDao.insert(conn, accountId, null, AccountStatus.ACTIVE, "Account opened", now);
            if (deposit.signum() > 0) {
                int cash = internal.id(conn, InternalAccount.CASH);
                engine.post(conn, PostingRequest.of(TransactionType.DEPOSIT, "Opening deposit",
                        PostingLine.debit(cash, deposit, BalanceRule.NON_NEGATIVE),
                        PostingLine.credit(accountId, deposit)));
            }
            return accountDao.findById(conn, accountId).orElseThrow();
        });
    }

    /** Adds a JOINT holder, respecting the product's maximum number of holders (BR-08). */
    public AccountHolder addJointHolder(int accountId, int customerId) {
        return tx.inTransaction(conn -> {
            LockedAccount acc = lockCustomerAccount(conn, accountId);
            if (acc.account().status() == AccountStatus.CLOSED) {
                throw new AccountStateException("Account " + acc.account().accountNumber() + " is closed.");
            }
            requireAdultCustomer(conn, customerId);
            if (holderDao.isHolder(conn, accountId, customerId)) {
                throw new BusinessRuleException("This customer is already a holder of the account.");
            }
            int holders = holderDao.findByAccount(conn, accountId).size();
            if (holders >= acc.type().maxHolders()) {
                throw new BusinessRuleException(acc.type().typeName() + " allows at most "
                        + acc.type().maxHolders() + " holder(s).");
            }
            LocalDateTime now = clock.now();
            holderDao.insert(conn, accountId, customerId, HolderRole.JOINT, now);
            return new AccountHolder(accountId, customerId, HolderRole.JOINT, now);
        });
    }

    /** Changes status between ACTIVE, FROZEN and BLOCKED (BR-12, BR-14). Use {@link #close} to close. */
    public Account changeStatus(int accountId, AccountStatus target, String reason) {
        String why = Validator.requireText(reason, "Reason", 255);
        if (target == null) {
            throw new ValidationException("New status is required.");
        }
        if (target == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Use the close-account operation to close an account.");
        }
        return tx.inTransaction(conn -> {
            LockedAccount acc = lockCustomerAccount(conn, accountId);
            AccountStatus current = acc.account().status();
            if (!current.canChangeTo(target)) {
                throw new AccountStateException("Cannot change account " + acc.account().accountNumber()
                        + " from " + current + " to " + target + ".");
            }
            accountDao.updateStatus(conn, accountId, target, null);
            historyDao.insert(conn, accountId, current, target, why, clock.now());
            return accountDao.findById(conn, accountId).orElseThrow();
        });
    }

    /**
     * Closes an ACTIVE account with no unpaid loan instalments (BR-13). Any remaining balance is paid
     * out as a WITHDRAWAL in the same transaction, so money is never silently discarded.
     */
    public Account close(int accountId, String reason) {
        String why = Validator.requireText(reason, "Reason", 255);
        return tx.inTransaction(conn -> {
            int cash = internal.id(conn, InternalAccount.CASH);
            Map<Integer, LockedAccount> locked = locker.lock(conn, List.of(accountId, cash));
            LockedAccount acc = requireCustomer(locked.get(accountId));
            AccountStatus current = acc.account().status();
            if (current != AccountStatus.ACTIVE) {
                throw new AccountStateException("Only an ACTIVE account can be closed; account "
                        + acc.account().accountNumber() + " is " + current + ".");
            }
            if (loanDao.hasUnpaidInstallments(conn, accountId)) {
                throw new BusinessRuleException("Account " + acc.account().accountNumber()
                        + " is the repayment account of a loan that is not fully repaid.");
            }
            BigDecimal balance = acc.account().balance();
            if (balance.signum() > 0) {
                engine.post(conn, PostingRequest.of(TransactionType.WITHDRAWAL, "Closure payout",
                        PostingLine.debit(accountId, balance, BalanceRule.NON_NEGATIVE),
                        PostingLine.credit(cash, balance)));
            }
            LocalDateTime now = clock.now();
            accountDao.updateStatus(conn, accountId, AccountStatus.CLOSED, now);
            historyDao.insert(conn, accountId, current, AccountStatus.CLOSED, why, now);
            return accountDao.findById(conn, accountId).orElseThrow();
        });
    }

    public Account get(int accountId) {
        return tx.readOnly(conn -> accountDao.findById(conn, accountId))
                .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found."));
    }

    public Account getByNumber(String accountNumber) {
        String number = Validator.requireAccountNumber(accountNumber);
        return tx.readOnly(conn -> accountDao.findByNumber(conn, number))
                .orElseThrow(() -> new NotFoundException("Account " + number + " not found."));
    }

    public List<Account> listByCustomer(int customerId) {
        return tx.readOnly(conn -> accountDao.findByCustomer(conn, customerId));
    }

    public List<AccountHolder> holders(int accountId) {
        return tx.readOnly(conn -> holderDao.findByAccount(conn, accountId));
    }

    public List<StatusChange> statusHistory(int accountId) {
        return tx.readOnly(conn -> historyDao.findByAccount(conn, accountId));
    }

    public AccountType productOf(Account account) {
        return tx.readOnly(conn -> accountTypeDao.findById(conn, account.accountTypeId())).orElseThrow();
    }

    private LockedAccount lockCustomerAccount(Connection conn, int accountId) throws SQLException {
        return requireCustomer(locker.lock(conn, List.of(accountId)).get(accountId));
    }

    private static LockedAccount requireCustomer(LockedAccount acc) {
        if (!acc.isCustomerAccount()) {
            throw new BusinessRuleException("Account " + acc.account().accountNumber()
                    + " is an internal bank account and cannot be changed.");
        }
        return acc;
    }

    private void requireAdultCustomer(Connection conn, int customerId) throws SQLException {
        Customer customer = customerDao.findById(conn, customerId)
                .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found."));
        if (!Validator.isAdult(customer.dateOfBirth(), clock.today())) {
            throw new BusinessRuleException(customer.fullName() + " is under " + Validator.ADULT_AGE
                    + " and cannot hold an account.");
        }
    }

    private int insertWithUniqueNumber(Connection conn, int branchId, int accountTypeId, LocalDateTime now)
            throws SQLException {
        for (int attempt = 1; ; attempt++) {
            try {
                return accountDao.insert(conn, references.accountNumber(), branchId, accountTypeId, now);
            } catch (SQLException e) {
                if (!SqlErrorTranslator.isDuplicateOn(e, "uq_account_number") || attempt == ACCOUNT_NUMBER_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
