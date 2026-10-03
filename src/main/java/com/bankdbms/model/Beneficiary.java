package com.bankdbms.model;

import java.time.LocalDateTime;

public record Beneficiary(int beneficiaryId, int customerId, int beneficiaryAccountId, String nickname,
                          BeneficiaryStatus status, LocalDateTime addedAt, LocalDateTime deactivatedAt) {
}
