package com.fapp.analytics;

import com.fapp.transaction.Category;
import java.math.BigDecimal;

/**
 * One category's contribution over a period.
 *
 * <p>Income and expenditure are reported separately rather than netted, because a
 * category can legitimately hold both — a refund lands under the category it reverses.
 * Summing every category's expenditure gives the period's total expenditure exactly.
 *
 * <p>{@link Category#UNCATEGORISED} appears like any other. It is where a bank told us
 * nothing usable, and hiding it would make the totals stop adding up.
 */
public record CategorySummary(
        Category category,
        BigDecimal income,
        BigDecimal expenditure,
        BigDecimal net,
        long transactionCount) {

    static CategorySummary of(Category category,
                              BigDecimal income,
                              BigDecimal expenditure,
                              long transactionCount) {
        return new CategorySummary(category, income, expenditure,
                income.subtract(expenditure), transactionCount);
    }
}
