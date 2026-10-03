package com.bankdbms.service;

import com.bankdbms.dao.AccountDao;
import com.bankdbms.dao.AccountHolderDao;
import com.bankdbms.dao.AccountTypeDao;
import com.bankdbms.dao.BeneficiaryDao;
import com.bankdbms.dao.CustomerDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.BeneficiaryStatus;
import com.bankdbms.util.BankClock;
import com.bankdbms.util.Validator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Beneficiary (saved payee) management (FR-20 to FR-24, BR-26 to BR-28). */
public class BeneficiaryService {

    private final TransactionRunner tx;
    private final BeneficiaryDao beneficiaryDao;
    private final CustomerDao customerDao;
    private final AccountDao accountDao;
    private final AccountTypeDao accountTypeDao;
    private final AccountHolderDao holderDao;
    private final BankClock clock;

    public BeneficiaryService(TransactionRunner tx, BeneficiaryDao beneficiaryDao, CustomerDao customerDao,
                              AccountDao accountDao, AccountTypeDao accountTypeDao, AccountHolderDao holderDao,
                              BankClock clock) {
        this.tx = tx;
        this.beneficiaryDao = beneficiaryDao;
        this.customerDao = customerDao;
        this.accountDao = accountDao;
        this.accountTypeDao = accountTypeDao;
        this.holderDao = holderDao;
        this.clock = clock;
    }

    /** Saves an account of this bank as a payee of the customer (FR-20, FR-24). */
    public Beneficiary add(int customerId, String payeeAccountNumber, String nickname) {
        String number = Validator.requireAccountNumber(payeeAccountNumber);
        String nick = Validator.requireText(nickname, "Nickname", 50);
        return tx.inTransaction(conn -> {
            customerDao.findById(conn, customerId)
                    .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found."));
            Account payee = accountDao.findByNumber(conn, number)
                    .orElseThrow(() -> new NotFoundException("Account " + number + " not found."));
            requireValidPayee(conn, payee);
            if (holderDao.isHolder(conn, payee.accountId(), customerId)) {
                throw new BusinessRuleException("A customer cannot add their own account as a beneficiary.");
            }
            int id = beneficiaryDao.insert(conn, customerId, payee.accountId(), nick, clock.now());
            return beneficiaryDao.findById(conn, id).orElseThrow();
        });
    }

    /** Only the nickname is updatable; the payee account identifies the beneficiary (FR-21, BR-28). */
    public Beneficiary rename(int beneficiaryId, String nickname) {
        String nick = Validator.requireText(nickname, "Nickname", 50);
        return tx.inTransaction(conn -> {
            requireExisting(conn, beneficiaryId);
            beneficiaryDao.updateNickname(conn, beneficiaryId, nick);
            return beneficiaryDao.findById(conn, beneficiaryId).orElseThrow();
        });
    }

    /** Deactivates instead of deleting, so past transfers keep their beneficiary reference (FR-22). */
    public Beneficiary deactivate(int beneficiaryId) {
        return tx.inTransaction(conn -> {
            Beneficiary b = requireExisting(conn, beneficiaryId);
            if (b.status() == BeneficiaryStatus.INACTIVE) {
                throw new BusinessRuleException("Beneficiary '" + b.nickname() + "' is already inactive.");
            }
            beneficiaryDao.updateStatus(conn, beneficiaryId, BeneficiaryStatus.INACTIVE, clock.now());
            return beneficiaryDao.findById(conn, beneficiaryId).orElseThrow();
        });
    }

    public Beneficiary reactivate(int beneficiaryId) {
        return tx.inTransaction(conn -> {
            Beneficiary b = requireExisting(conn, beneficiaryId);
            if (b.status() == BeneficiaryStatus.ACTIVE) {
                throw new BusinessRuleException("Beneficiary '" + b.nickname() + "' is already active.");
            }
            Account payee = accountDao.findById(conn, b.beneficiaryAccountId()).orElseThrow();
            requireValidPayee(conn, payee);
            beneficiaryDao.updateStatus(conn, beneficiaryId, BeneficiaryStatus.ACTIVE, null);
            return beneficiaryDao.findById(conn, beneficiaryId).orElseThrow();
        });
    }

    public Beneficiary get(int beneficiaryId) {
        return tx.readOnly(conn -> beneficiaryDao.findById(conn, beneficiaryId))
                .orElseThrow(() -> new NotFoundException("Beneficiary " + beneficiaryId + " not found."));
    }

    public List<Beneficiary> listForCustomer(int customerId) {
        return tx.readOnly(conn -> beneficiaryDao.findByCustomer(conn, customerId));
    }

    public List<Beneficiary> search(String keyword) {
        String kw = Validator.requireText(keyword, "Search text", 100);
        return tx.readOnly(conn -> beneficiaryDao.search(conn, kw));
    }

    private Beneficiary requireExisting(Connection conn, int beneficiaryId) throws SQLException {
        return beneficiaryDao.findById(conn, beneficiaryId)
                .orElseThrow(() -> new NotFoundException("Beneficiary " + beneficiaryId + " not found."));
    }

    private void requireValidPayee(Connection conn, Account payee) throws SQLException {
        boolean customerProduct = accountTypeDao.findById(conn, payee.accountTypeId()).orElseThrow().isCustomerProduct();
        if (!customerProduct) {
            throw new BusinessRuleException("Internal bank accounts cannot be beneficiaries.");
        }
        if (payee.status() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Account " + payee.accountNumber() + " is closed.");
        }
    }
}
