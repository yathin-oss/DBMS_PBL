package com.bankdbms.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Source of the business date/time used for posting timestamps, loan due dates and age checks.
 *
 * <p>Production code uses {@link #system()}. Tests and the sample-data loader use
 * {@link AdjustableBankClock} to post transactions on chosen historical dates, so overdue loans
 * and dated statements can be produced through the real business logic.
 */
public interface BankClock {

    LocalDateTime now();

    default LocalDate today() {
        return now().toLocalDate();
    }

    static BankClock system() {
        return () -> LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
