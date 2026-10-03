package com.bankdbms.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Academic reducing-balance EMI model (business rule BR-33).
 *
 * <pre>
 *   r   = annual rate / 12 / 100                     (monthly rate)
 *   EMI = P * r * (1+r)^n / ((1+r)^n - 1)            rounded HALF_EVEN to 2 dp
 *   for instalment k = 1..n:
 *       interest_k  = round(outstanding * r)
 *       principal_k = EMI - interest_k               (last instalment: principal = outstanding)
 *       outstanding = outstanding - principal_k
 *   due date of instalment k = disbursement date + k months
 * </pre>
 *
 * The final instalment absorbs the rounding difference, so the scheduled principal always sums
 * exactly to P (invariant I6a). Intermediate maths uses 20 significant digits.
 */
public final class LoanCalculator {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    /** One row of a generated repayment schedule. */
    public record ScheduledInstallment(int installmentNo, LocalDate dueDate,
                                       BigDecimal principalComponent, BigDecimal interestComponent) {
        public BigDecimal amount() {
            return principalComponent.add(interestComponent);
        }
    }

    private LoanCalculator() {
    }

    public static BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent.divide(TWELVE_HUNDRED, MC);
    }

    public static BigDecimal emi(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal growth = BigDecimal.ONE.add(r, MC).pow(tenureMonths, MC);
        BigDecimal emi = principal.multiply(r, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE, MC), MC);
        return Money.round(emi);
    }

    public static List<ScheduledInstallment> schedule(BigDecimal principal, BigDecimal annualRatePercent,
                                                      int tenureMonths, LocalDate disbursementDate) {
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal emi = emi(principal, annualRatePercent, tenureMonths);
        BigDecimal outstanding = Money.round(principal);
        List<ScheduledInstallment> rows = new ArrayList<>(tenureMonths);
        for (int k = 1; k <= tenureMonths; k++) {
            BigDecimal interest = Money.round(outstanding.multiply(r, MC));
            BigDecimal principalPart = (k == tenureMonths) ? outstanding : emi.subtract(interest);
            outstanding = outstanding.subtract(principalPart);
            rows.add(new ScheduledInstallment(k, disbursementDate.plusMonths(k), principalPart, interest));
        }
        return rows;
    }
}
