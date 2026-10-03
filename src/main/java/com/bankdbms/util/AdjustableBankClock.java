package com.bankdbms.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** A {@link BankClock} whose time is set explicitly (tests and sample-data generation only). */
public final class AdjustableBankClock implements BankClock {

    private volatile LocalDateTime current;

    public AdjustableBankClock(LocalDateTime start) {
        set(start);
    }

    @Override
    public LocalDateTime now() {
        return current;
    }

    public void set(LocalDateTime time) {
        this.current = time.truncatedTo(ChronoUnit.SECONDS);
    }
}
