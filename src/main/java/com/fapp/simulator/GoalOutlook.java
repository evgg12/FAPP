package com.fapp.simulator;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a scenario would do to a savings goal.
 *
 * <p>The two "months to target" figures are {@code null} when the target is not reachable
 * at all — which is what saving nothing, or spending more than you receive, actually
 * means. A zero or negative monthly surplus has no answer to "how many months", and
 * reporting a very large number or a zero would both be wrong.
 *
 * @param remaining                 still to find, from the goal's own progress
 * @param baselineMonthsToTarget    months at the current rate, or {@code null} if never
 * @param scenarioMonthsToTarget    months under the scenario, or {@code null} if never
 * @param baselineProjectedDate     when it would be met at the current rate
 * @param scenarioProjectedDate     when it would be met under the scenario
 * @param onTrackBefore             whether the current rate meets the goal's own date
 * @param onTrackAfter              whether the scenario still does
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalOutlook(
        UUID goalId,
        String goalName,
        BigDecimal remaining,
        Integer baselineMonthsToTarget,
        Integer scenarioMonthsToTarget,
        LocalDate baselineProjectedDate,
        LocalDate scenarioProjectedDate,
        boolean onTrackBefore,
        boolean onTrackAfter) {
}
