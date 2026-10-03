package com.bankdbms.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates human-readable identifiers. Uniqueness is guaranteed by the database's UNIQUE
 * constraints; callers retry with a fresh value in the (very unlikely) event of a collision.
 */
public class ReferenceGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final SecureRandom random = new SecureRandom();

    /** {@code TX} + yyyyMMdd + 6 random characters, e.g. {@code TX20260915K7M2QD}. */
    public String transactionReference(LocalDate businessDate) {
        StringBuilder sb = new StringBuilder("TX").append(businessDate.format(DATE));
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /** 12 digits; the first digit is 1-8 because numbers starting with 9 are reserved for internal accounts. */
    public String accountNumber() {
        StringBuilder sb = new StringBuilder().append(1 + random.nextInt(8));
        for (int i = 0; i < 11; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** {@code LN} + yyyyMMdd + 4 random digits, e.g. {@code LN202604060417}. */
    public String loanNumber(LocalDate businessDate) {
        return "LN" + businessDate.format(DATE) + String.format("%04d", random.nextInt(10_000));
    }
}
