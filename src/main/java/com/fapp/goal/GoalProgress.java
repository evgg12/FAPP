package com.fapp.goal;

import com.fapp.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How far along a savings goal is.
 *
 * <p>Every figure is exact except the percentage, which is a ratio and has no exact
 * decimal form, so it is given to two places, half-up, and that is stated rather than
 * left to be discovered. There is never a zero denominator: a goal's target is required
 * to be positive.
 *
 * <p>Saving past the target is reported honestly rather than clamped. {@code percentage}
 * goes above 100 and {@code achieved} is true, while {@code remaining} stops at zero —
 * there is no such thing as negative money still to find.
 *
 * @param target     what the user is saving towards
 * @param current    what they have put aside so far
 * @param remaining  what is still to find, never below zero
 * @param percentage {@code current} as a percentage of {@code target}, two decimal places
 * @param achieved   whether the target has been reached or passed
 */
public record GoalProgress(
        Money target,
        Money current,
        Money remaining,
        BigDecimal percentage,
        boolean achieved) {

    /** Decimal places on {@link #percentage}. */
    public static final int PERCENTAGE_SCALE = 2;

    static GoalProgress of(Money target, Money current) {
        BigDecimal shortfall = target.amount().subtract(current.amount());
        Money remaining = shortfall.signum() > 0
                ? Money.of(shortfall, target.currency())
                : Money.of(BigDecimal.ZERO, target.currency());
        BigDecimal percentage = current.amount()
                .multiply(BigDecimal.valueOf(100))
                .divide(target.amount(), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        return new GoalProgress(target, current, remaining, percentage, shortfall.signum() <= 0);
    }
}
