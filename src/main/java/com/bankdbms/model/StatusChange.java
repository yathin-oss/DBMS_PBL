package com.bankdbms.model;

import java.time.LocalDateTime;

/** One row of account_status_history; oldStatus is null for the account-opening record. */
public record StatusChange(long historyId, int accountId, AccountStatus oldStatus, AccountStatus newStatus,
                           String reason, LocalDateTime changedAt) {
}
