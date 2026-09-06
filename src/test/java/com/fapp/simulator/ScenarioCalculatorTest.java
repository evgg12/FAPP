package com.fapp.simulator;

import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.FinancialSummary;
import com.fapp.goal.SavingsGoal;
import com.fapp.money.Money;
import com.fapp.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The what-if arithmetic, with every expected figure worked out by hand.
 *
 * <p>The baseline throughout is three months of real history — 6000.00 in and 3600.00 out
 * — which averages to 2000.00 in, 1200.00 out and 800.00 left over per month.
 */
class ScenarioCalculatorTest {

    private static final AnalyticsPeriod THREE_MONTHS =
            new AnalyticsPeriod(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-09-01"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    private final User owner = User.of("saver@example.com", "Saver");

    @Test
    void averagesRealHistoryDownToAMonth() {
        MonthlyFigures monthly = ScenarioCalculator.monthlyAverage(summary("6000.00", "3600.00"), 3);

        assertThat(monthly.income()).isEqualByComparingTo("2000.00");
        assertThat(monthly.expenditure()).isEqualByComparingTo("1200.00");
        assertThat(monthly.net()).isEqualByComparingTo("800.00");
    }

    @Test
    void reportsTheBaselineUnchangedWhenNothingIsAltered() {
        SimulationResult result = simulate(ScenarioAdjustment.none(), 12, null);

        assertThat(result.baseline().net()).isEqualByComparingTo("800.00");
        assertThat(result.scenario().net()).isEqualByComparingTo("800.00");
        assertThat(result.monthlyNetChange()).isEqualByComparingTo("0");
        assertThat(result.horizonNetChange()).isEqualByComparingTo("0");
        assertThat(result.monthsOfHistory()).isEqualTo(3);
        assertThat(result.horizonMonths()).isEqualTo(12);
        // 800.00 a month for a year.
        assertThat(result.baselineHorizonNet()).isEqualByComparingTo("9600.00");
    }

    @Test
    void takesAOneOffPurchaseOffOnceRatherThanEveryMonth() {
        // "What if I buy a 1200 laptop?" over a year.
        SimulationResult result = simulate(purchase("1200.00"), 12, null);

        assertThat(result.scenario().net()).isEqualByComparingTo("800.00");
        assertThat(result.monthlyNetChange()).isEqualByComparingTo("0");
        // 9600.00 less 1200.00, not less 14400.00.
        assertThat(result.scenarioHorizonNet()).isEqualByComparingTo("8400.00");
        assertThat(result.horizonNetChange()).isEqualByComparingTo("-1200.00");
    }

    @Test
    void appliesARecurringSpendingIncrease() {
        SimulationResult result = simulate(
                new ScenarioAdjustment(null, new BigDecimal("100.00"), null), 6, null);

        assertThat(result.scenario().expenditure()).isEqualByComparingTo("1300.00");
        assertThat(result.scenario().net()).isEqualByComparingTo("700.00");
        assertThat(result.monthlyNetChange()).isEqualByComparingTo("-100.00");
        // 700 x 6 = 4200 against 800 x 6 = 4800.
        assertThat(result.scenarioHorizonNet()).isEqualByComparingTo("4200.00");
        assertThat(result.horizonNetChange()).isEqualByComparingTo("-600.00");
    }

    @Test
    void appliesARecurringSpendingReduction() {
        SimulationResult result = simulate(
                new ScenarioAdjustment(null, new BigDecimal("-150.00"), null), 12, null);

        assertThat(result.scenario().expenditure()).isEqualByComparingTo("1050.00");
        assertThat(result.monthlyNetChange()).isEqualByComparingTo("150.00");
        assertThat(result.horizonNetChange()).isEqualByComparingTo("1800.00");
    }

    @Test
    void appliesAnIncomeChange() {
        SimulationResult result = simulate(
                new ScenarioAdjustment(null, null, new BigDecimal("250.00")), 12, null);

        assertThat(result.scenario().income()).isEqualByComparingTo("2250.00");
        assertThat(result.scenario().net()).isEqualByComparingTo("1050.00");
        assertThat(result.horizonNetChange()).isEqualByComparingTo("3000.00");
    }

    @Test
    void combinesAOneOffWithRecurringChanges() {
        SimulationResult result = simulate(
                new ScenarioAdjustment(new BigDecimal("1200.00"), new BigDecimal("100.00"),
                        new BigDecimal("50.00")),
                12, null);

        // 2050 in, 1300 out, 750 left; 9000 over the year, less the 1200 purchase.
        assertThat(result.scenario().net()).isEqualByComparingTo("750.00");
        assertThat(result.scenarioHorizonNet()).isEqualByComparingTo("7800.00");
        assertThat(result.horizonNetChange()).isEqualByComparingTo("-1800.00");
    }

    @Test
    void handlesAHorizonOfASingleMonth() {
        SimulationResult result = simulate(purchase("1200.00"), 1, null);

        assertThat(result.baselineHorizonNet()).isEqualByComparingTo("800.00");
        assertThat(result.scenarioHorizonNet()).isEqualByComparingTo("-400.00");
    }

    @Test
    void handlesAUserWhoSpendsMoreThanTheyReceive() {
        SimulationResult result = ScenarioCalculator.simulate(
                THREE_MONTHS, summary("3000.00", "4500.00"), ScenarioAdjustment.none(), 12, null, TODAY);

        assertThat(result.baseline().net()).isEqualByComparingTo("-500.00");
        assertThat(result.baselineHorizonNet()).isEqualByComparingTo("-6000.00");
    }

    @Test
    void handlesAPeriodWithNoTransactionsAtAll() {
        SimulationResult result = ScenarioCalculator.simulate(
                THREE_MONTHS, summary("0", "0"), purchase("500.00"), 6, null, TODAY);

        assertThat(result.baseline().net()).isEqualByComparingTo("0");
        assertThat(result.scenarioHorizonNet()).isEqualByComparingTo("-500.00");
    }

    @Test
    void refusesAHorizonOutsideWhatItWillProject() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> simulate(ScenarioAdjustment.none(), 0, null))
                .withMessageContaining("between 1 and 120");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> simulate(ScenarioAdjustment.none(), 121, null))
                .withMessageContaining("between 1 and 120");
    }

    @Test
    void refusesANegativeOneOffPurchase() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ScenarioAdjustment(new BigDecimal("-1.00"), null, null))
                .withMessageContaining("cannot be negative");
    }

    @Test
    void treatsOmittedFiguresAsChangingNothing() {
        ScenarioAdjustment nothing = new ScenarioAdjustment(null, null, null);

        assertThat(nothing.oneOffPurchase()).isEqualByComparingTo("0");
        assertThat(nothing.monthlyExpenditureChange()).isEqualByComparingTo("0");
        assertThat(nothing.monthlyIncomeChange()).isEqualByComparingTo("0");
        assertThat(nothing.changesNothing()).isTrue();
    }

    // --- savings goals ---

    @Test
    void projectsWhenAGoalWouldBeReachedAtTheCurrentRate() {
        // 5650.00 still to find at 800.00 a month: 7.06 months, so 8 whole months.
        SimulationResult result = simulate(ScenarioAdjustment.none(), 12, carFundAt("2350.00"));

        GoalOutlook outlook = result.goalOutlook();
        assertThat(outlook.remaining()).isEqualByComparingTo("5650.00");
        assertThat(outlook.baselineMonthsToTarget()).isEqualTo(8);
        assertThat(outlook.baselineProjectedDate()).isEqualTo(LocalDate.of(2027, 5, 1));
        // Due June 2027, projected May 2027.
        assertThat(outlook.onTrackBefore()).isTrue();
    }

    @Test
    void showsAPurchasePushingAGoalPastItsDate() {
        // The laptop adds 1200 to what is still to find: 6850 at 800 a month is 9 months.
        SimulationResult result = simulate(purchase("1200.00"), 12, carFundAt("2350.00"));

        GoalOutlook outlook = result.goalOutlook();
        assertThat(outlook.baselineMonthsToTarget()).isEqualTo(8);
        assertThat(outlook.scenarioMonthsToTarget()).isEqualTo(9);
        assertThat(outlook.onTrackBefore()).isTrue();
        assertThat(outlook.scenarioProjectedDate()).isEqualTo(LocalDate.of(2027, 6, 1));
        // Exactly on the target date still counts as on track.
        assertThat(outlook.onTrackAfter()).isTrue();
    }

    @Test
    void showsSavingMoreBringingAGoalForward() {
        // "What if I save an extra 100 a month?" 5650 at 900 is 6.28 months, so 7.
        SimulationResult result = simulate(
                new ScenarioAdjustment(null, new BigDecimal("-100.00"), null), 12, carFundAt("2350.00"));

        assertThat(result.goalOutlook().baselineMonthsToTarget()).isEqualTo(8);
        assertThat(result.goalOutlook().scenarioMonthsToTarget()).isEqualTo(7);
        assertThat(result.goalOutlook().scenarioProjectedDate()).isEqualTo(LocalDate.of(2027, 4, 1));
    }

    @Test
    void reportsAGoalAsUnreachableRatherThanDividingByZero() {
        // Spending everything that comes in leaves nothing to save, so there is no honest
        // number of months. Null, not zero and not a huge number.
        SimulationResult result = ScenarioCalculator.simulate(
                THREE_MONTHS, summary("6000.00", "6000.00"), ScenarioAdjustment.none(), 12,
                carFundAt("2350.00"), TODAY);

        assertThat(result.goalOutlook().baselineMonthsToTarget()).isNull();
        assertThat(result.goalOutlook().baselineProjectedDate()).isNull();
        assertThat(result.goalOutlook().onTrackBefore()).isFalse();
    }

    @Test
    void reportsAGoalAsUnreachableWhenTheScenarioWipesOutTheSurplus() {
        SimulationResult result = simulate(
                new ScenarioAdjustment(null, new BigDecimal("800.00"), null), 12, carFundAt("2350.00"));

        assertThat(result.goalOutlook().baselineMonthsToTarget()).isEqualTo(8);
        assertThat(result.goalOutlook().scenarioMonthsToTarget()).isNull();
        assertThat(result.goalOutlook().onTrackAfter()).isFalse();
    }

    @Test
    void reportsAnAlreadyMetGoalAsNeedingNoMoreMonths() {
        SimulationResult result = simulate(ScenarioAdjustment.none(), 12, carFundAt("8000.00"));

        assertThat(result.goalOutlook().remaining()).isEqualByComparingTo("0");
        assertThat(result.goalOutlook().baselineMonthsToTarget()).isZero();
        assertThat(result.goalOutlook().baselineProjectedDate()).isEqualTo(TODAY);
        assertThat(result.goalOutlook().onTrackBefore()).isTrue();
    }

    @Test
    void omitsTheGoalOutlookWhenNoGoalWasNamed() {
        assertThat(simulate(ScenarioAdjustment.none(), 12, null).goalOutlook()).isNull();
    }

    @Test
    void roundsMonthsUpBecauseAPartMonthDoesNotReachTheTarget() {
        assertThat(ScenarioCalculator.monthsToSave(new BigDecimal("100.00"), new BigDecimal("100.00")))
                .isEqualTo(1);
        assertThat(ScenarioCalculator.monthsToSave(new BigDecimal("100.01"), new BigDecimal("100.00")))
                .isEqualTo(2);
        assertThat(ScenarioCalculator.monthsToSave(new BigDecimal("0"), new BigDecimal("100.00")))
                .isZero();
        assertThat(ScenarioCalculator.monthsToSave(new BigDecimal("100.00"), new BigDecimal("0")))
                .isNull();
    }

    // --- helpers ---

    private SimulationResult simulate(ScenarioAdjustment adjustment, int horizon, SavingsGoal goal) {
        return ScenarioCalculator.simulate(
                THREE_MONTHS, summary("6000.00", "3600.00"), adjustment, horizon, goal, TODAY);
    }

    private static ScenarioAdjustment purchase(String amount) {
        return new ScenarioAdjustment(new BigDecimal(amount), null, null);
    }

    private static FinancialSummary summary(String income, String expenditure) {
        BigDecimal in = new BigDecimal(income);
        BigDecimal out = new BigDecimal(expenditure);
        return new FinancialSummary(THREE_MONTHS, in, out, in.subtract(out), 42);
    }

    private SavingsGoal carFundAt(String saved) {
        SavingsGoal goal = SavingsGoal.of(owner, "Car Fund", Money.of("8000.00", "GBP"),
                LocalDate.of(2027, 6, 1), TODAY);
        goal.recordCurrentAmount(Money.of(saved, "GBP"));
        return goal;
    }
}
