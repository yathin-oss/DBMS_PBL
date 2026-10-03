package com.bankdbms.util;

import com.bankdbms.exception.ValidationException;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/**
 * Application-level input validation. These rules mirror the CHECK constraints in
 * {@code sql/01_schema.sql} so the operator gets an immediate, specific message; the database
 * still enforces them as the final boundary (defence in depth, NFR-04).
 */
public final class Validator {

    public static final int ADULT_AGE = 18;

    private static final Pattern EMAIL = Pattern.compile("^[^@ ]+@[^@ ]+\\.[^@ ]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,14}$");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z_]+$");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{12}$");
    private static final LocalDate EARLIEST_DOB = LocalDate.of(1900, 1, 1);

    private Validator() {
    }

    /** Trims and checks a required text field against its column length. */
    public static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required.");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new ValidationException(field + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    /** Optional text: blank becomes null; otherwise trimmed and length-checked. */
    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    public static String requireEmail(String value) {
        String email = requireText(value, "Email", 100).toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw new ValidationException("Email address is not valid.");
        }
        return email;
    }

    public static String requirePhone(String value) {
        String phone = requireText(value, "Phone", 15).replace(" ", "").replace("-", "");
        if (!PHONE.matcher(phone).matches()) {
            throw new ValidationException("Phone number must contain 10 to 14 digits (optionally starting with +).");
        }
        return phone;
    }

    public static String requireIfsc(String value) {
        String ifsc = requireText(value, "IFSC code", 11).toUpperCase();
        if (!IFSC.matcher(ifsc).matches()) {
            throw new ValidationException("IFSC code must be 4 letters, a zero, then 6 letters or digits (e.g. BDBS0000102).");
        }
        return ifsc;
    }

    public static String requireCode(String value, String field, int maxLength) {
        String code = requireText(value, field, maxLength).toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw new ValidationException(field + " may contain only letters A-Z and underscores.");
        }
        return code;
    }

    public static String requireAccountNumber(String value) {
        String number = requireText(value, "Account number", 12);
        if (!ACCOUNT_NUMBER.matcher(number).matches()) {
            throw new ValidationException("Account number must be exactly 12 digits.");
        }
        return number;
    }

    /** BR-03: date of birth is present, not in the future, and not before 1900. */
    public static LocalDate requireDateOfBirth(LocalDate dob, LocalDate today) {
        if (dob == null) {
            throw new ValidationException("Date of birth is required.");
        }
        if (dob.isAfter(today)) {
            throw new ValidationException("Date of birth cannot be in the future.");
        }
        if (dob.isBefore(EARLIEST_DOB)) {
            throw new ValidationException("Date of birth is not valid.");
        }
        return dob;
    }

    public static boolean isAdult(LocalDate dob, LocalDate today) {
        return Period.between(dob, today).getYears() >= ADULT_AGE;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new ValidationException(message);
        }
    }

    /** Masks an identity document number for display, keeping the last 4 characters. */
    public static String mask(String idNumber) {
        if (idNumber == null || idNumber.length() <= 4) {
            return idNumber;
        }
        return "*".repeat(idNumber.length() - 4) + idNumber.substring(idNumber.length() - 4);
    }
}
