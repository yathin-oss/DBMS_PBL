package com.bankdbms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Account(int accountId, String accountNumber, int branchId, int accountTypeId, BigDecimal balance,
                      AccountStatus status, LocalDateTime openedAt, LocalDateTime closedAt) {
}
