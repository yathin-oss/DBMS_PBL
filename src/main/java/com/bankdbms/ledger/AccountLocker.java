package com.bankdbms.ledger;

import com.bankdbms.dao.AccountDao;
import com.bankdbms.dao.AccountTypeDao;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Locks account rows with SELECT ... FOR UPDATE in ascending account_id order.
 *
 * <p>Every operation that touches several accounts locks them in the same global order, so two
 * concurrent operations can never each hold one lock while waiting for the other (no deadlock
 * cycle). Locks are held until the caller's transaction commits or rolls back.
 */
public class AccountLocker {

    private final AccountDao accountDao;
    private final AccountTypeDao accountTypeDao;

    public AccountLocker(AccountDao accountDao, AccountTypeDao accountTypeDao) {
        this.accountDao = accountDao;
        this.accountTypeDao = accountTypeDao;
    }

    public Map<Integer, LockedAccount> lock(Connection conn, Collection<Integer> accountIds) throws SQLException {
        Map<Integer, LockedAccount> locked = new LinkedHashMap<>();
        for (int id : new TreeSet<>(accountIds)) {
            Account account = accountDao.findByIdForUpdate(conn, id)
                    .orElseThrow(() -> new NotFoundException("Account not found (id " + id + ")."));
            AccountType type = accountTypeDao.findById(conn, account.accountTypeId())
                    .orElseThrow(() -> new IllegalStateException("Account type missing for account " + id));
            locked.put(id, new LockedAccount(account, type));
        }
        return locked;
    }
}
