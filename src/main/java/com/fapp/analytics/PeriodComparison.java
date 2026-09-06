package com.fapp.analytics;

/**
 * A period set against the equivalent one before it, which is how a question like "why
 * was this month more expensive than usual" gets a factual answer.
 *
 * <p>The comparison is arithmetic only. It reports what changed and by how much; it
 * does not say why, and it does not judge.
 */
public record PeriodComparison(
        AnalyticsPeriod current,
        AnalyticsPeriod previous,
        ComparisonFigure income,
        ComparisonFigure expenditure,
        ComparisonFigure netSavings) {

    static PeriodComparison of(FinancialSummary current, FinancialSummary previous) {
        return new PeriodComparison(
                current.period(),
                previous.period(),
                ComparisonFigure.of(current.income(), previous.income()),
                ComparisonFigure.of(current.expenditure(), previous.expenditure()),
                ComparisonFigure.of(current.netSavings(), previous.netSavings()));
    }
}
