package com.fapp.analytics;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The window every figure is calculated over: {@code from} inclusive, {@code to}
 * exclusive.
 *
 * <p>Half-open on purpose. A month is {@code 2026-08-01} to {@code 2026-09-01}, so
 * consecutive months tile without overlapping and without a caller having to know how
 * long February is. There is no time of day and no zone: imported statements record a
 * calendar date and nothing finer, so introducing an instant here would let a zone
 * conversion move a transaction between months.
 *
 * @param from first day included
 * @param to   first day excluded; must be after {@code from}
 */
public record AnalyticsPeriod(LocalDate from, LocalDate to) {

    public AnalyticsPeriod {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "the period must start before it ends, but from=" + from + " and to=" + to);
        }
    }

    /**
     * The equivalent window immediately before this one.
     *
     * <p>Whole-month windows step back by months, so the period before August is July
     * whatever their lengths. Anything else steps back by its own length in days.
     * Counting days for a month-aligned window would put the period before March in
     * late January, which is not what anyone comparing months means.
     */
    public AnalyticsPeriod previous() {
        if (startsAndEndsOnAFirstOfTheMonth()) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to));
            return new AnalyticsPeriod(from.minusMonths(months), from);
        }
        return new AnalyticsPeriod(from.minusDays(ChronoUnit.DAYS.between(from, to)), from);
    }

    /** Every month this period touches, in order, whether or not anything happened in it. */
    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        YearMonth last = YearMonth.from(to.minusDays(1));
        for (YearMonth month = YearMonth.from(from); !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return List.copyOf(months);
    }

    private boolean startsAndEndsOnAFirstOfTheMonth() {
        return from.getDayOfMonth() == 1 && to.getDayOfMonth() == 1;
    }
}
