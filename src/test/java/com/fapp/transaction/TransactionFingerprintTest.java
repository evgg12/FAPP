package com.fapp.transaction;

import com.fapp.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransactionFingerprintTest {

    private static final LocalDate BOOKED = LocalDate.of(2026, 3, 14);

    @Test
    void producesTheCanonicalFingerprintForAKnownTuple() {
        // Pins the algorithm behind Transaction.CURRENT_FINGERPRINT_VERSION. If this
        // value has to change, the version has to change with it, because every stored
        // fingerprint was produced by the old one.
        //   canonical text: 2026-03-14|-8.5000|GBP|SNCF PARIS
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-8.50", "GBP"), "SNCF  paris "))
                .isEqualTo("8dbe790333e4cc8f4aa36e5c018d54408e3a794cbb18abe9f76e969c346422d2");
    }

    @Test
    void isLowercaseHexSha256AsTheSchemaRequires() {
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "ANYTHING"))
                .matches("^[0-9a-f]{64}$");
    }

    @Test
    void isStableForTheSameInputs() {
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-24.15", "GBP"), "TESCO STORES"))
                .isEqualTo(TransactionFingerprint.of(BOOKED, Money.of("-24.15", "GBP"), "TESCO STORES"));
    }

    @Test
    void ignoresTheScaleTheAmountWasWrittenAt() {
        // 1.5 and 1.50 are unequal BigDecimals; they are the same money.
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.5", "GBP"), "X"))
                .isEqualTo(TransactionFingerprint.of(BOOKED, Money.of("-1.5000", "GBP"), "X"));
    }

    @Test
    void ignoresDescriptionCaseAndSpacing() {
        String canonical = TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "TESCO STORES 3421");

        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "tesco stores 3421"))
                .isEqualTo(canonical);
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "  TESCO   STORES\t3421  "))
                .isEqualTo(canonical);
    }

    @Test
    void separatesTransactionsThatDifferInAnyPartOfTheTuple() {
        String base = TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "X");

        assertThat(TransactionFingerprint.of(BOOKED.plusDays(1), Money.of("-1.00", "GBP"), "X"))
                .isNotEqualTo(base);
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.01", "GBP"), "X"))
                .isNotEqualTo(base);
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.00", "EUR"), "X"))
                .isNotEqualTo(base);
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "Y"))
                .isNotEqualTo(base);
    }

    @Test
    void distinguishesAnOutflowFromAnInflowOfTheSameSize() {
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-50.00", "GBP"), "X"))
                .isNotEqualTo(TransactionFingerprint.of(BOOKED, Money.of("50.00", "GBP"), "X"));
    }

    @Test
    void keepsDescriptionsApartThatOnlyDifferAroundTheDelimiter() {
        assertThat(TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "A|B"))
                .isNotEqualTo(TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), "A|B|C"));
    }

    @Test
    void rejectsMissingInputs() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TransactionFingerprint.of(null, Money.of("-1.00", "GBP"), "X"));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TransactionFingerprint.of(BOOKED, null, "X"));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> TransactionFingerprint.of(BOOKED, Money.of("-1.00", "GBP"), null));
    }
}
