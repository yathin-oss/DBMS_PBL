package com.bankdbms.service;

import com.bankdbms.dao.AccountDao;
import com.bankdbms.model.InternalAccount;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

/** Resolves the bank's internal accounts (CASH, FEE_INCOME, ...) to account ids, by reserved number. */
public class InternalAccounts {

    private final AccountDao accountDao;
    private final Map<InternalAccount, Integer> cache = new EnumMap<>(InternalAccount.class);

    public InternalAccounts(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    public synchronized int id(Connection conn, InternalAccount account) throws SQLException {
        Integer cached = cache.get(account);
        if (cached != null) {
            return cached;
        }
        int id = accountDao.findByNumber(conn, account.accountNumber())
                .orElseThrow(() -> new IllegalStateException("Internal account " + account
                        + " is missing; run sql/04_reference_data.sql"))
                .accountId();
        cache.put(account, id);
        return id;
    }
}
