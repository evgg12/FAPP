package com.fapp.money;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class MoneyTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void pinsAmountsToFourDecimalPlaces() {
        assertThat(Money.of("12.5", "GBP").amount()).isEqualTo(new BigDecimal("12.5000"));
        assertThat(Money.of("-3", "GBP").amount()).isEqualTo(new BigDecimal("-3.0000"));
        assertThat(Money.of("0.1234", "GBP").amount()).isEqualTo(new BigDecimal("0.1234"));
    }

    @Test
    void treatsSameValueAtDifferentScalesAsEqual() {
        assertThat(Money.of("1.5", "GBP")).isEqualTo(Money.of("1.5000", "GBP"));
        assertThat(Money.of("1.5", "GBP")).hasSameHashCodeAs(Money.of("1.50", "GBP"));
    }

    @Test
    void distinguishesCurrencies() {
        assertThat(Money.of("10.00", "GBP")).isNotEqualTo(Money.of("10.00", "EUR"));
    }

    @Test
    void rejectsAnAmountThatWouldHaveToBeRounded() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Money.of("1.23456", "GBP"))
                .withMessageContaining("never rounds");
    }

    @Test
    void acceptsFourDecimalPlacesForCurrenciesThatNeedThem() {
        assertThatNoException().isThrownBy(() -> Money.of("1.2345", "CLF"));
        assertThatNoException().isThrownBy(() -> Money.of("1.234", "KWD"));
    }

    @Test
    void reportsSignAccordingToTheFappConvention() {
        Money spent = Money.of("-42.00", "GBP");
        Money received = Money.of("42.00", "GBP");

        assertThat(spent.isNegative()).isTrue();
        assertThat(spent.isPositive()).isFalse();
        assertThat(received.isPositive()).isTrue();
        assertThat(Money.of("0.00", "GBP").isZero()).isTrue();
    }

    @Test
    void negatesWithoutChangingMagnitudeOrCurrency() {
        Money outgoing = Money.of("-500.00", "GBP");

        assertThat(outgoing.negated()).isEqualTo(Money.of("500.00", "GBP"));
        assertThat(outgoing.negated().negated()).isEqualTo(outgoing);
        assertThat(outgoing.negated().currency()).isEqualTo(GBP);
    }

    @Test
    void rejectsNulls() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Money.of((BigDecimal) null, GBP));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Money.of(BigDecimal.ONE, null));
    }

    @Test
    void rejectsAnUnknownCurrencyCode() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Money.of("1.00", "XYZ"));
    }

    @Test
    void printsAmountAndCurrency() {
        assertThat(Money.of("-12.34", "EUR")).hasToString("-12.3400 EUR");
        assertThat(EUR.getCurrencyCode()).isEqualTo("EUR");
    }
}
