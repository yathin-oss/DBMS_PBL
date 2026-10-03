package com.bankdbms.model;

import java.math.BigDecimal;
import java.util.List;

/** A transaction header together with its entries, as returned after posting. */
public record PostedTransaction(BankTransaction transaction, List<LedgerEntry> entries) {

    public PostedTransaction {
        entries = List.copyOf(entries);
    }

    /** Total of the debit side (equal to the credit side by invariant I1). */
    public BigDecimal amount() {
        return entries.stream().filter(e -> e.side() == EntrySide.DEBIT)
                .map(LedgerEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public LedgerEntry entryFor(int accountId, EntrySide side) {
        return entries.stream().filter(e -> e.accountId() == accountId && e.side() == side).findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + side + " entry for account " + accountId));
    }
}
