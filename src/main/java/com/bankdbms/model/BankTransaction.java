package com.bankdbms.model;

import java.time.LocalDateTime;

/** Ledger header. Amounts and accounts live in its {@link LedgerEntry} rows. */
public record BankTransaction(long transactionId, String referenceNo, TransactionType type,
                              TransactionStatus status, String narration, Long reversalOfId,
                              LocalDateTime postedAt) {
}
