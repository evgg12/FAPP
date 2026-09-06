package com.fapp.analytics;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AnalyticsPeriodTest {

    @Test
    void isHalfOpenSoConsecutiveMonthsTileWithoutOverlapping() {
        AnalyticsPeriod august = period("2026-08-01", "2026-09-01");
        AnalyticsPeriod september = period("2026-09-01", "2026-10-01");

        assertThat(august.to()).isEqualTo(september.from());
        assertThat(august.months()).containsExactly(YearMonth.of(2026, 8));
        assertThat(september.months()).containsExactly(YearMonth.of(2026, 9));
    }

    @Test
    void rejectsAPeriodThatEndsBeforeOrWhenItStarts() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> period("2026-09-01", "2026-08-01"))
                .withMessageContaining("must start before it ends");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> period("2026-08-01", "2026-08-01"))
                .withMessageContaining("must start before it ends");
    }

    @Test
    void rejectsMissingDates() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new AnalyticsPeriod(null, LocalDate.of(2026, 8, 1)));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new AnalyticsPeriod(LocalDate.of(2026, 8, 1), null));
    }

    @Test
    void stepsAWholeMonthWindowBackByMonthsRatherThanDays() {
        // The month before March is February, whatever their lengths. Counting the 31
        // days of March backwards would land in late January.
        assertThat(period("2026-03-01", "2026-04-01").previous())
                .isEqualTo(period("2026-02-01", "2026-03-01"));
        assertThat(period("2026-08-01", "2026-09-01").previous())
                .isEqualTo(period("2026-07-01", "2026-08-01"));
    }

    @Test
    void stepsAMultiMonthWindowBackByTheSameNumberOfMonths() {
        assertThat(period("2026-07-01", "2026-10-01").previous())
                .isEqualTo(period("2026-04-01", "2026-07-01"));
    }

    @Test
    void stepsAWindowThatIsNotMonthAlignedBackByItsOwnLengthInDays() {
        assertThat(period("2026-08-10", "2026-08-17").previous())
                .isEqualTo(period("2026-08-03", "2026-08-10"));
    }

    @Test
    void handlesAWholeMonthWindowSpanningAYearEnd() {
        assertThat(period("2027-01-01", "2027-02-01").previous())
                .isEqualTo(period("2026-12-01", "2027-01-01"));
    }

    @Test
    void listsEveryMonthItTouchesIncludingPartialOnes() {
        assertThat(period("2026-07-01", "2026-10-01").months())
                .containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));

        // A window ending on the first of a month does not include that month.
        assertThat(period("2026-08-15", "2026-09-01").months())
                .containsExactly(YearMonth.of(2026, 8));

        assertThat(period("2026-08-20", "2026-09-10").months())
                .containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9));
    }

    private static AnalyticsPeriod period(String from, String to) {
        return new AnalyticsPeriod(LocalDate.parse(from), LocalDate.parse(to));
    }
}
