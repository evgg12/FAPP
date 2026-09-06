package com.fapp.analytics;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * One month's income, expenditure and net position.
 *
 * <p>Every month the requested period touches is reported, including months with no
 * transactions at all, which come back as zeros. A gap in the series would be
 * indistinguishable from a month of no spending to anything drawing a chart from this.
 */
public record MonthlySummary(
        YearMonth month,
        BigDecimal income,
        BigDecimal expenditure,
        BigDecimal netSavings,
        long transactionCount) {

    static MonthlySummary of(YearMonth month,
                             BigDecimal income,
                             BigDecimal expenditure,
                             long transactionCount) {
        return new MonthlySummary(month, income, expenditure,
                income.subtract(expenditure), transactionCount);
    }
}
