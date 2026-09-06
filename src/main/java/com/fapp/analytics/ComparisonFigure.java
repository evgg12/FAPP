package com.fapp.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One figure in two periods, and how it moved.
 *
 * <p>{@code change} is {@code current - previous} and is exact. {@code changePercent}
 * is {@code null} whenever a percentage would be meaningless — which is exactly when
 * the previous period was zero, because there is no such thing as a percentage of
 * nothing. Callers get an absent field rather than a zero, an infinity or a divide by
 * zero.
 *
 * <p>The percentage is taken against the previous value's magnitude, so that an
 * improvement reads positive even when the previous figure was negative: net savings
 * moving from -100 to -50 is a 50% improvement, not -50%.
 *
 * <p>It is also the one number here that is rounded. Money is never rounded, but a
 * ratio has no exact decimal form, so it is given to two decimal places, half-up, and
 * that is stated rather than left to the caller to discover.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComparisonFigure(
        BigDecimal current,
        BigDecimal previous,
        BigDecimal change,
        BigDecimal changePercent) {

    /** Decimal places on {@link #changePercent}. */
    public static final int PERCENT_SCALE = 2;

    static ComparisonFigure of(BigDecimal current, BigDecimal previous) {
        BigDecimal change = current.subtract(previous);
        return new ComparisonFigure(current, previous, change, percentChange(change, previous));
    }

    private static BigDecimal percentChange(BigDecimal change, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return change.multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
