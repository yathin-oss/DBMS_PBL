package com.bankdbms.model;

import java.time.LocalDateTime;

public record AccountHolder(int accountId, int customerId, HolderRole role, LocalDateTime addedAt) {
}
