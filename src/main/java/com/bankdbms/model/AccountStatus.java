package com.bankdbms.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Account lifecycle (business rules BR-12 to BR-15).
 *
 * <pre>
 *   status   credits  debits
 *   ACTIVE   yes      yes
 *   FROZEN   yes      no     (debit freeze, e.g. legal hold)
 *   BLOCKED  no       no     (all activity stopped, e.g. KYC issue)
 *   CLOSED   no       no     (terminal)
 * </pre>
 * Transitions: any of ACTIVE/FROZEN/BLOCKED to another of those three; only ACTIVE to CLOSED.
 */
public enum AccountStatus {
    ACTIVE(true, true),
    FROZEN(true, false),
    BLOCKED(false, false),
    CLOSED(false, false);

    private final boolean creditsAllowed;
    private final boolean debitsAllowed;

    AccountStatus(boolean creditsAllowed, boolean debitsAllowed) {
        this.creditsAllowed = creditsAllowed;
        this.debitsAllowed = debitsAllowed;
    }

    public boolean allowsCredits() {
        return creditsAllowed;
    }

    public boolean allowsDebits() {
        return debitsAllowed;
    }

    public boolean allows(EntrySide side) {
        return side == EntrySide.CREDIT ? creditsAllowed : debitsAllowed;
    }

    public Set<AccountStatus> allowedTransitions() {
        switch (this) {
            case ACTIVE:
                return EnumSet.of(FROZEN, BLOCKED, CLOSED);
            case FROZEN:
                return EnumSet.of(ACTIVE, BLOCKED);
            case BLOCKED:
                return EnumSet.of(ACTIVE, FROZEN);
            default:
                return EnumSet.noneOf(AccountStatus.class);
        }
    }

    public boolean canChangeTo(AccountStatus target) {
        return allowedTransitions().contains(target);
    }
}
