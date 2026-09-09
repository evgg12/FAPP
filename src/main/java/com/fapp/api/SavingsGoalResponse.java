package com.fapp.api;

import com.fapp.goal.GoalProgress;
import com.fapp.goal.SavingsGoal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal and how far along it is.
 *
 * <p>Progress is included rather than left for the caller to work out: it is calculated
 * by the domain, and a client recomputing it in floating point is exactly how a
 * financial figure goes wrong. {@code percentageComplete} may exceed 100 where the target
 * has been passed, while {@code remainingAmount} stops at zero.
 */
public record SavingsGoalResponse(
        UUID id,
        UUID userId,
        String name,
        String currency,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal remainingAmount,
        BigDecimal percentageComplete,
        boolean achieved,
        LocalDate targetDate,
        boolean featured,
        Instant createdAt,
        Instant updatedAt) {

    public static SavingsGoalResponse of(SavingsGoal goal) {
        GoalProgress progress = goal.progress();
        return new SavingsGoalResponse(
                goal.id(),
                goal.userId(),
                goal.name(),
                goal.target().currency().getCurrencyCode(),
                progress.target().amount(),
                progress.current().amount(),
                progress.remaining().amount(),
                progress.percentage(),
                progress.achieved(),
                goal.targetDate(),
                goal.featured(),
                goal.createdAt(),
                goal.updatedAt());
    }
}
