package com.fapp.simulator;

import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.AnalyticsScope;
import com.fapp.analytics.AnalyticsService;
import com.fapp.goal.SavingsGoal;
import com.fapp.goal.SavingsGoalService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers what-if questions about a user's money.
 *
 * <p>Reads real history through {@link AnalyticsService} and hands it to
 * {@link ScenarioCalculator}, which does the arithmetic. Deliberately has no way to write
 * anything: a hypothetical purchase is never stored as a transaction, and a projected
 * goal balance is never written to the goal. The read-only transaction is not a hint —
 * it is the guarantee.
 */
@Service
@Transactional(readOnly = true)
public class SimulatorService {

    private final AnalyticsService analytics;
    private final SavingsGoalService goals;
    private final Clock clock;

    SimulatorService(AnalyticsService analytics, SavingsGoalService goals, Clock clock) {
        this.analytics = analytics;
        this.goals = goals;
        this.clock = clock;
    }

    /**
     * @param scenario  the hypothetical change
     * @param goalId    a goal to report the effect on, or {@code null}
     */
    public SimulationResult simulate(AnalyticsScope scope,
                                     AnalyticsPeriod baselinePeriod,
                                     ScenarioAdjustment scenario,
                                     int horizonMonths,
                                     UUID goalId) {
        // Scope and ownership are checked by the analytics service, and the goal is
        // resolved by owner, so a scenario cannot read across users.
        SavingsGoal goal = goalId == null ? null : goals.find(scope.userId(), goalId);
        return ScenarioCalculator.simulate(
                baselinePeriod,
                analytics.summarise(scope, baselinePeriod),
                scenario,
                horizonMonths,
                goal,
                LocalDate.now(clock));
    }
}
