package com.bankdbms.ledger;

import com.bankdbms.model.EntrySide;

import java.math.BigDecimal;

/** One requested debit or credit to one account. */
public record PostingLine(int accountId, EntrySide side, BigDecimal amount, BalanceRule rule) {

    public static PostingLine debit(int accountId, BigDecimal amount, BalanceRule rule) {
        return new PostingLine(accountId, EntrySide.DEBIT, amount, rule);
    }

    public static PostingLine credit(int accountId, BigDecimal amount) {
        return new PostingLine(accountId, EntrySide.CREDIT, amount, BalanceRule.NON_NEGATIVE);
    }
}
