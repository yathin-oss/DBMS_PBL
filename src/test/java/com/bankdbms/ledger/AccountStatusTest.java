package com.bankdbms.ledger;

import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.EntrySide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStatusTest {

    @Test
    @DisplayName("TC-LIFE-01 lifecycle permission matrix (credits/debits per status)")
    void permissionMatrix() {
        assertTrue(AccountStatus.ACTIVE.allows(EntrySide.CREDIT) && AccountStatus.ACTIVE.allows(EntrySide.DEBIT));
        assertTrue(AccountStatus.FROZEN.allows(EntrySide.CREDIT));
        assertFalse(AccountStatus.FROZEN.allows(EntrySide.DEBIT));
        assertFalse(AccountStatus.BLOCKED.allows(EntrySide.CREDIT) || AccountStatus.BLOCKED.allows(EntrySide.DEBIT));
        assertFalse(AccountStatus.CLOSED.allows(EntrySide.CREDIT) || AccountStatus.CLOSED.allows(EntrySide.DEBIT));
    }

    @Test
    @DisplayName("TC-LIFE-02 transition table: CLOSED is terminal and only reachable from ACTIVE")
    void transitions() {
        assertTrue(AccountStatus.ACTIVE.canChangeTo(AccountStatus.CLOSED));
        assertFalse(AccountStatus.FROZEN.canChangeTo(AccountStatus.CLOSED));
        assertFalse(AccountStatus.BLOCKED.canChangeTo(AccountStatus.CLOSED));
        for (AccountStatus s : AccountStatus.values()) {
            assertFalse(AccountStatus.CLOSED.canChangeTo(s));
            assertFalse(s.canChangeTo(s), s + " -> " + s + " should not be a transition");
        }
        assertTrue(AccountStatus.FROZEN.canChangeTo(AccountStatus.BLOCKED));
        assertTrue(AccountStatus.BLOCKED.canChangeTo(AccountStatus.ACTIVE));
    }
}
