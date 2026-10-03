package com.bankdbms.util;

import com.bankdbms.util.LoanCalculator.ScheduledInstallment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanCalculatorTest {

    @Test
    @DisplayName("TC-LC-01 EMI matches the standard formula for 1,00,000 at 12% over 12 months (8,884.88)")
    void emiKnownValue() {
        assertEquals(new BigDecimal("8884.88"), LoanCalculator.emi(new BigDecimal("100000.00"), new BigDecimal("12"), 12));
    }

    @Test
    @DisplayName("TC-LC-02 first instalment interest = outstanding x monthly rate (1,000.00 on 1,00,000 at 12%)")
    void firstInterest() {
        List<ScheduledInstallment> rows = LoanCalculator.schedule(new BigDecimal("100000.00"), new BigDecimal("12"), 12,
                LocalDate.of(2026, 1, 15));
        assertEquals(new BigDecimal("1000.00"), rows.get(0).interestComponent());
        assertEquals(new BigDecimal("7884.88"), rows.get(0).principalComponent());
    }

    @ParameterizedTest(name = "TC-LC-03 principal sums exactly to P: P={0} rate={1} n={2}")
    @CsvSource({"100000.00,12,12", "250000.00,8.5,36", "5000.00,24,7", "1000000.00,9.25,240", "999.99,36,1",
            "1234567.89,10.75,360"})
    void principalSumsExactly(String principal, String rate, int tenure) {
        BigDecimal p = new BigDecimal(principal);
        List<ScheduledInstallment> rows = LoanCalculator.schedule(p, new BigDecimal(rate), tenure, LocalDate.of(2026, 1, 31));
        BigDecimal sum = rows.stream().map(ScheduledInstallment::principalComponent).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, p.compareTo(sum));
        assertEquals(tenure, rows.size());
        rows.forEach(r -> assertTrue(r.principalComponent().signum() > 0 && r.interestComponent().signum() >= 0));
    }

    @Test
    @DisplayName("TC-LC-04 every instalment except the last equals the EMI; amounts have 2 decimals")
    void instalmentsEqualEmi() {
        BigDecimal emi = LoanCalculator.emi(new BigDecimal("250000.00"), new BigDecimal("8.5"), 36);
        List<ScheduledInstallment> rows = LoanCalculator.schedule(new BigDecimal("250000.00"), new BigDecimal("8.5"), 36,
                LocalDate.of(2026, 3, 1));
        for (int i = 0; i < rows.size() - 1; i++) {
            assertEquals(emi, rows.get(i).amount());
            assertEquals(2, rows.get(i).amount().scale());
        }
    }

    @Test
    @DisplayName("TC-LC-05 due dates are disbursement + k months, month-end clamped and distinct")
    void dueDates() {
        List<ScheduledInstallment> rows = LoanCalculator.schedule(new BigDecimal("12000.00"), new BigDecimal("10"), 3,
                LocalDate.of(2026, 1, 31));
        assertEquals(LocalDate.of(2026, 2, 28), rows.get(0).dueDate());
        assertEquals(LocalDate.of(2026, 3, 31), rows.get(1).dueDate());
        assertEquals(LocalDate.of(2026, 4, 30), rows.get(2).dueDate());
    }

    @Test
    @DisplayName("TC-LC-06 single-month loan: one instalment of principal plus one month's interest")
    void singleInstalment() {
        List<ScheduledInstallment> rows = LoanCalculator.schedule(new BigDecimal("12000.00"), new BigDecimal("12"), 1,
                LocalDate.of(2026, 6, 1));
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("12000.00"), rows.get(0).principalComponent());
        assertEquals(new BigDecimal("120.00"), rows.get(0).interestComponent());
    }
}
