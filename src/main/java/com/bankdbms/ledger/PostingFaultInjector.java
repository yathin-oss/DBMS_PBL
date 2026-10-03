package com.bankdbms.ledger;

import java.sql.SQLException;

/**
 * Test seam for proving atomicity: called after each ledger entry is inserted, before the
 * transaction commits. Production code uses {@link #NONE}. A test can throw from here to simulate
 * a database failure between the debit and the credit, then assert that nothing was persisted.
 */
@FunctionalInterface
public interface PostingFaultInjector {

    PostingFaultInjector NONE = entryIndex -> { };

    void afterEntryInserted(int entryIndex) throws SQLException;
}
