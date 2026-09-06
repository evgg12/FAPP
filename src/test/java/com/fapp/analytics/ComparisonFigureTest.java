package com.fapp.analytics;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonFigureTest {

    @Test
    void reportsAnExactAbsoluteChange() {
        ComparisonFigure figure = ComparisonFigure.of(money("1234.5600"), money("1000.0000"));

        assertThat(figure.change()).isEqualByComparingTo("234.56");
        assertThat(figure.changePercent()).isEqualByComparingTo("23.46");
    }

    @Test
    void leavesThePercentageAbsentWhenThereIsNothingToCompareAgainst() {
        ComparisonFigure figure = ComparisonFigure.of(money("500.00"), BigDecimal.ZERO);

        assertThat(figure.change()).isEqualByComparingTo("500.00");
        // A percentage of nothing does not exist; no zero, no infinity, no exception.
        assertThat(figure.changePercent()).isNull();
    }

    @Test
    void reportsZeroChangeForIdenticalPeriods() {
        ComparisonFigure figure = ComparisonFigure.of(money("995.00"), money("995.00"));

        assertThat(figure.change()).isEqualByComparingTo("0");
        assertThat(figure.changePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void reportsADecreaseAsNegative() {
        ComparisonFigure figure = ComparisonFigure.of(money("100.00"), money("995.00"));

        assertThat(figure.change()).isEqualByComparingTo("-895.00");
        assertThat(figure.changePercent()).isEqualByComparingTo("-89.95");
    }

    @Test
    void treatsAnImprovementFromANegativeBaseAsPositive() {
        // Net savings moving from -100 to -50 is a 50% improvement. Dividing by the
        // signed previous value would report it as -50%, which reads as getting worse.
        ComparisonFigure figure = ComparisonFigure.of(money("-50.00"), money("-100.00"));

        assertThat(figure.change()).isEqualByComparingTo("50.00");
        assertThat(figure.changePercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void treatsADeteriorationFromANegativeBaseAsNegative() {
        ComparisonFigure figure = ComparisonFigure.of(money("-150.00"), money("-100.00"));

        assertThat(figure.change()).isEqualByComparingTo("-50.00");
        assertThat(figure.changePercent()).isEqualByComparingTo("-50.00");
    }

    @Test
    void roundsThePercentageToTwoPlacesHalfUpAndNothingElse() {
        // 1/3 has no exact decimal form, so the ratio is rounded; the money is not.
        ComparisonFigure figure = ComparisonFigure.of(money("400.0000"), money("300.0000"));

        assertThat(figure.change()).isEqualByComparingTo("100.0000");
        assertThat(figure.changePercent()).isEqualByComparingTo("33.33");
        assertThat(figure.changePercent().scale()).isEqualTo(ComparisonFigure.PERCENT_SCALE);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
