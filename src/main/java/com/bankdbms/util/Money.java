package com.bankdbms.util;

import com.bankdbms.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Monetary helpers. All amounts are {@link BigDecimal} with scale 2 (matching DECIMAL(15,2));
 * rounding uses HALF_EVEN (banker's rounding). {@code float}/{@code double} are never used.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    /** Largest value DECIMAL(15,2) can hold. */
    public static final BigDecimal MAX = new BigDecimal("9999999999999.99");
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Parses operator input such as "1,500.50" into an exact amount. */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Amount is required.");
        }
        try {
            return new BigDecimal(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new ValidationException("'" + text.trim() + "' is not a valid amount.");
        }
    }

    /**
     * Validates a money amount for a financial operation: present, greater than zero, at most two
     * decimal places, within the column range. Returns it with scale 2.
     */
    public static BigDecimal requirePositive(BigDecimal amount, String field) {
        if (amount == null) {
            throw new ValidationException(field + " is required.");
        }
        if (amount.signum() <= 0) {
            throw new ValidationException(field + " must be greater than zero.");
        }
        return requireScaleAndRange(amount, field);
    }

    /** Like {@link #requirePositive} but allows zero (e.g. a zero opening deposit on a salary account). */
    public static BigDecimal requireNonNegative(BigDecimal amount, String field) {
        if (amount == null) {
            throw new ValidationException(field + " is required.");
        }
        if (amount.signum() < 0) {
            throw new ValidationException(field + " cannot be negative.");
        }
        return requireScaleAndRange(amount, field);
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(amount);
    }

    private static BigDecimal requireScaleAndRange(BigDecimal amount, String field) {
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > SCALE) {
            throw new ValidationException(field + " can have at most 2 decimal places.");
        }
        if (amount.compareTo(MAX) > 0) {
            throw new ValidationException(field + " is too large.");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
