package com.fapp.analytics;

import java.math.BigDecimal;

/**
 * What came in, what went out and what was left over a period.
 *
 * <p>Both totals are reported as positive figures — expenditure of 250.00 rather than
 * -250.00 — because they answer "how much did I spend", while the stored amounts keep
 * FAPP's signed convention. {@code netSavings} is {@code income - expenditure} and may
 * be negative.
 *
 * <p>{@code transactionCount} counts exactly the movements the totals were calculated
 * from, so it excludes the same internal transfers they do. A caller can therefore
 * reconcile the two.
 */
public record FinancialSummary(
        AnalyticsPeriod period,
        BigDecimal income,
        BigDecimal expenditure,
        BigDecimal netSavings,
        long transactionCount) {

    static FinancialSummary of(AnalyticsPeriod period,
                               BigDecimal income,
                               BigDecimal expenditure,
                               long transactionCount) {
        return new FinancialSummary(period, income, expenditure,
                income.subtract(expenditure), transactionCount);
    }
}
