package com.bankdbms.model;

import java.math.BigDecimal;

public record LedgerEntry(long entryId, long transactionId, int accountId, EntrySide side, BigDecimal amount,
                          BigDecimal balanceAfter) {
}
