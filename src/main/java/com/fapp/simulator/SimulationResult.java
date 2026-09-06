package com.fapp.simulator;

import com.fapp.analytics.AnalyticsPeriod;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * A scenario answered: what the user's money does now, what it would do instead, and the
 * difference.
 *
 * <p>{@code baseline} is real: averages of transactions actually imported over
 * {@code baselinePeriod}. Everything named {@code scenario} is hypothetical and exists
 * only in this response. The two are kept as separate fields rather than merged so that
 * nothing reading this can mistake one for the other.
 *
 * @param baselinePeriod    the real history the averages came from
 * @param monthsOfHistory   how many months that period covers
 * @param horizonMonths     how far ahead the scenario was projected
 * @param baseline          real average income, expenditure and net per month
 * @param scenario          the same figures with the adjustment applied
 * @param monthlyNetChange  {@code scenario.net - baseline.net}
 * @param baselineHorizonNet what would accumulate over the horizon unchanged
 * @param scenarioHorizonNet what would accumulate under the scenario, one-off included
 * @param horizonNetChange  the difference over the whole horizon
 * @param goalOutlook       the effect on one savings goal, if one was named
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationResult(
        AnalyticsPeriod baselinePeriod,
        int monthsOfHistory,
        int horizonMonths,
        MonthlyFigures baseline,
        MonthlyFigures scenario,
        BigDecimal monthlyNetChange,
        BigDecimal baselineHorizonNet,
        BigDecimal scenarioHorizonNet,
        BigDecimal horizonNetChange,
        GoalOutlook goalOutlook) {
}
