package com.bankdbms.util;

import com.bankdbms.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyAndValidatorTest {

    @Test
    @DisplayName("TC-VAL-01 amounts are normalised to scale 2")
    void normalisesScale() {
        assertEquals(new BigDecimal("100.50"), Money.requirePositive(new BigDecimal("100.5"), "Amount"));
        assertEquals(new BigDecimal("1500.00"), Money.requirePositive(Money.parse("1,500"), "Amount"));
    }

    @ParameterizedTest(name = "TC-VAL-02 invalid amount rejected: {0}")
    @ValueSource(strings = {"0", "-1", "-0.01", "10.001", "10000000000000"})
    void rejectsInvalidAmounts(String value) {
        assertThrows(ValidationException.class, () -> Money.requirePositive(new BigDecimal(value), "Amount"));
    }

    @Test
    @DisplayName("TC-VAL-03 non-numeric amount text is rejected with a friendly message")
    void rejectsText() {
        ValidationException e = assertThrows(ValidationException.class, () -> Money.parse("ten rupees"));
        assertTrue(e.getMessage().contains("not a valid amount"));
    }

    @ParameterizedTest(name = "TC-VAL-04 invalid email rejected: {0}")
    @ValueSource(strings = {"plainaddress", "a@b", "a b@c.com", "@example.com"})
    void rejectsEmail(String email) {
        assertThrows(ValidationException.class, () -> Validator.requireEmail(email));
    }

    @ParameterizedTest(name = "TC-VAL-05 invalid phone rejected: {0}")
    @ValueSource(strings = {"12345", "98765abcde", "+1234567890123456"})
    void rejectsPhone(String phone) {
        assertThrows(ValidationException.class, () -> Validator.requirePhone(phone));
    }

    @Test
    @DisplayName("TC-VAL-06 IFSC must match AAAA0XXXXXX")
    void ifsc() {
        assertEquals("BDBS0000102", Validator.requireIfsc("bdbs0000102"));
        assertThrows(ValidationException.class, () -> Validator.requireIfsc("BDBS1000102"));
    }

    @Test
    @DisplayName("TC-VAL-07 date of birth cannot be in the future; age >= 18 rule")
    void dateOfBirth() {
        LocalDate today = LocalDate.of(2026, 10, 3);
        assertThrows(ValidationException.class, () -> Validator.requireDateOfBirth(today.plusDays(1), today));
        assertTrue(Validator.isAdult(LocalDate.of(2008, 10, 3), today));
        assertFalse(Validator.isAdult(LocalDate.of(2008, 10, 4), today));
    }

    @Test
    @DisplayName("TC-VAL-08 text length limits and masking of ID numbers")
    void textAndMask() {
        assertThrows(ValidationException.class, () -> Validator.requireText("x".repeat(51), "First name", 50));
        assertThrows(ValidationException.class, () -> Validator.requireText("   ", "First name", 50));
        assertEquals("******234F", Validator.mask("ABCDE1234F"));
    }
}
