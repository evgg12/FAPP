package com.fapp.simulator;

import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.FinancialSummary;
import com.fapp.goal.GoalProgress;
import com.fapp.goal.SavingsGoal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The whole of the what-if arithmetic, and nothing else.
 *
 * <p>No Spring, no repositories, no clock of its own: given real figures and a
 * hypothetical change it returns the answer, which makes every rule here testable
 * without a database and guarantees a scenario cannot touch stored data. Running it
 * twice over the same inputs gives the same answer to the penny.
 *
 * <p>Averaging is the one place a figure is rounded. A month's share of a period's total
 * has no exact decimal form once the period is not a whole number of anything, so
 * averages are taken at {@value com.fapp.money.Money#SCALE} places, half-up — the scale
 * money is stored at. Totals derived from them are exact multiplications of that.
 */
public final class ScenarioCalculator {

    /** Longest horizon a scenario may be projected over: ten years. */
    public static final int MAX_HORIZON_MONTHS = 120;

    private static final int MONEY_SCALE = com.fapp.money.Money.SCALE;

    private ScenarioCalculator() {
    }

    /**
     * Averages a period's real totals down to a month.
     *
     * @param months how many months the period covers; always at least one, because a
     *               period must start before it ends
     */
    public static MonthlyFigures monthlyAverage(FinancialSummary summary, int months) {
        Objects.requireNonNull(summary, "summary must not be null");
        if (months < 1) {
            throw new IllegalArgumentException("a period covers at least one month, was " + months);
        }
        BigDecimal divisor = BigDecimal.valueOf(months);
        return MonthlyFigures.of(
                summary.income().divide(divisor, MONEY_SCALE, RoundingMode.HALF_UP),
                summary.expenditure().divide(divisor, MONEY_SCALE, RoundingMode.HALF_UP));
    }

    /** The same figures with the hypothetical change applied. */
    public static MonthlyFigures apply(MonthlyFigures baseline, ScenarioAdjustment adjustment) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(adjustment, "adjustment must not be null");
        return MonthlyFigures.of(
                baseline.income().add(adjustment.monthlyIncomeChange()),
                baseline.expenditure().add(adjustment.monthlyExpenditureChange()));
    }

    /**
     * Answers the scenario.
     *
     * @param goal   the goal to report on, or {@code null} for none
     * @param today  the day projections count forward from
     */
    public static SimulationResult simulate(AnalyticsPeriod baselinePeriod,
                                            FinancialSummary summary,
                                            ScenarioAdjustment adjustment,
                                            int horizonMonths,
                                            SavingsGoal goal,
                                            LocalDate today) {
        requireHorizon(horizonMonths);
        int monthsOfHistory = baselinePeriod.months().size();
        MonthlyFigures baseline = monthlyAverage(summary, monthsOfHistory);
        MonthlyFigures scenario = apply(baseline, adjustment);

        BigDecimal horizon = BigDecimal.valueOf(horizonMonths);
        BigDecimal baselineHorizonNet = baseline.net().multiply(horizon);
        // The one-off comes out once over the whole horizon, not every month.
        BigDecimal scenarioHorizonNet =
                scenario.net().multiply(horizon).subtract(adjustment.oneOffPurchase());

        return new SimulationResult(
                baselinePeriod,
                monthsOfHistory,
                horizonMonths,
                baseline,
                scenario,
                scenario.net().subtract(baseline.net()),
                baselineHorizonNet,
                scenarioHorizonNet,
                scenarioHorizonNet.subtract(baselineHorizonNet),
                goal == null ? null : outlookFor(goal, baseline, scenario, adjustment, today));
    }

    /**
     * What the scenario does to a goal.
     *
     * <p>The one-off purchase is taken off what is already saved, not off the monthly
     * rate: buying a laptop today sets the goal back by its price once. Both the current
     * rate and the scenario rate are then projected forward.
     */
    private static GoalOutlook outlookFor(SavingsGoal goal,
                                          MonthlyFigures baseline,
                                          MonthlyFigures scenario,
                                          ScenarioAdjustment adjustment,
                                          LocalDate today) {
        GoalProgress progress = goal.progress();
        BigDecimal remaining = progress.remaining().amount();
        BigDecimal remainingAfterPurchase = remaining.add(adjustment.oneOffPurchase());

        Integer baselineMonths = monthsToSave(remaining, baseline.net());
        Integer scenarioMonths = monthsToSave(remainingAfterPurchase, scenario.net());
        LocalDate baselineDate = baselineMonths == null ? null : today.plusMonths(baselineMonths);
        LocalDate scenarioDate = scenarioMonths == null ? null : today.plusMonths(scenarioMonths);

        return new GoalOutlook(
                goal.id(),
                goal.name(),
                remaining,
                baselineMonths,
                scenarioMonths,
                baselineDate,
                scenarioDate,
                baselineDate != null && !baselineDate.isAfter(goal.targetDate()),
                scenarioDate != null && !scenarioDate.isAfter(goal.targetDate()));
    }

    /**
     * Whole months of saving at this rate to cover this much.
     *
     * @return the number of months, or {@code null} when the target is unreachable —
     *         which is what a monthly surplus of zero or less means. There is no honest
     *         number of months in that case.
     */
    static Integer monthsToSave(BigDecimal remaining, BigDecimal monthlySurplus) {
        if (remaining.signum() <= 0) {
            return 0;
        }
        if (monthlySurplus.signum() <= 0) {
            return null;
        }
        // Rounded up: a part-month of saving does not reach the target.
        return remaining.divide(monthlySurplus, 0, RoundingMode.CEILING).intValueExact();
    }

    private static void requireHorizon(int horizonMonths) {
        if (horizonMonths < 1 || horizonMonths > MAX_HORIZON_MONTHS) {
            throw new IllegalArgumentException(
                    "the horizon must be between 1 and " + MAX_HORIZON_MONTHS
                            + " months, was " + horizonMonths);
        }
    }
}
