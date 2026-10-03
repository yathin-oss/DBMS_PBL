package com.bankdbms.ledger;

import com.bankdbms.model.Account;
import com.bankdbms.model.AccountType;

/** An account row read under an exclusive lock, together with its product. */
public record LockedAccount(Account account, AccountType type) {

    public int id() {
        return account.accountId();
    }

    public boolean isCustomerAccount() {
        return type.isCustomerProduct();
    }
}
