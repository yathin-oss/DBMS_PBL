package com.bankdbms.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One line of an account statement; debit = money out, credit = money in. */
public record StatementLine(LocalDateTime postedAt, String referenceNo, String transactionType,
                            String transactionStatus, String narration, BigDecimal debit, BigDecimal credit,
                            BigDecimal balanceAfter) {
}
