package com.fapp.statement;

import com.fapp.money.Money;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.TransactionType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The invariants a provider adapter must satisfy before its rows are allowed any
 * further into the platform. They mirror the constraints on the transaction the row
 * eventually becomes, so a malformed statement fails at the adapter with a readable
 * message rather than as a constraint violation part-way through an import.
 */
class RawTransactionTest {

    private static final LocalDate BOOKED = LocalDate.of(2026, 3, 14);

    @Test
    void carriesADomesticRowWithEverythingTheProviderOmittedLeftNull() {
        RawTransaction row = new RawTransaction(
                BOOKED, null, Money.of("-42.00", "GBP"), null,
                "TESCO STORES 3421", null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT);

        assertThat(row.bookingDate()).isEqualTo(BOOKED);
        assertThat(row.occurredOn()).isNull();
        assertThat(row.amount()).isEqualTo(Money.of("-42.00", "GBP"));
        assertThat(row.originalAmount()).isNull();
        assertThat(row.description()).isEqualTo("TESCO STORES 3421");
        assertThat(row.merchant()).isNull();
        assertThat(row.externalId()).isNull();
        assertThat(row.transactionType()).isEqualTo(TransactionType.CARD_PAYMENT);
    }

    @Test
    void carriesAForeignCurrencyRowWithBothLegs() {
        RawTransaction row = new RawTransaction(
                BOOKED, LocalDate.of(2026, 3, 12), Money.of("-8.50", "GBP"), Money.of("-10.00", "EUR"),
                "SNCF PARIS", "SNCF", "tx_00009fZ", TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT);

        assertThat(row.amount()).isEqualTo(Money.of("-8.50", "GBP"));
        assertThat(row.originalAmount()).isEqualTo(Money.of("-10.00", "EUR"));
        assertThat(row.occurredOn()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(row.merchant()).isEqualTo("SNCF");
        assertThat(row.externalId()).isEqualTo("tx_00009fZ");
    }

    @Test
    void rejectsAZeroAmount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> row(Money.of("0.00", "GBP")))
                .withMessageContaining("zero");
    }

    @Test
    void keepsBothDirectionsOfTheFappSignConvention() {
        assertThat(row(Money.of("-42.00", "GBP")).amount().isNegative()).isTrue();
        assertThat(row(Money.of("1500.00", "GBP")).amount().isPositive()).isTrue();
    }

    @Test
    void rejectsAForeignLegInTheSameCurrencyAsTheAmount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-8.50", "GBP"), Money.of("-8.50", "GBP"),
                        "SOMEWHERE", null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("different currency");
    }

    @Test
    void rejectsAForeignLegPointingTheOppositeWay() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-8.50", "GBP"), Money.of("10.00", "EUR"),
                        "SOMEWHERE", null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("same way");
    }

    @Test
    void trimsTheSurroundingWhitespaceProviderExportsAreFullOf() {
        RawTransaction row = new RawTransaction(
                BOOKED, null, Money.of("-42.00", "GBP"), null,
                "  TESCO STORES 3421  ", "  Tesco  ", "  tx_1  ", TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT);

        assertThat(row.description()).isEqualTo("TESCO STORES 3421");
        assertThat(row.merchant()).isEqualTo("Tesco");
        assertThat(row.externalId()).isEqualTo("tx_1");
    }

    @Test
    void rejectsABlankDescription() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-1.00", "GBP"), null,
                        "   ", null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("description must not be blank");
    }

    @Test
    void insistsAbsentOptionalTextIsNullRatherThanAnEmptyCell() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-1.00", "GBP"), null,
                        "SOMEWHERE", "", null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("merchant must be null rather than blank");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-1.00", "GBP"), null,
                        "SOMEWHERE", null, "  ", TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("externalId must be null rather than blank");
    }

    @Test
    void rejectsTheRequiredComponentsBeingMissing() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RawTransaction(
                        null, null, Money.of("-1.00", "GBP"), null,
                        "SOMEWHERE", null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("bookingDate");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, null, null,
                        "SOMEWHERE", null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("amount");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-1.00", "GBP"), null,
                        "SOMEWHERE", null, null, null,
                        Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("transactionType");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-1.00", "GBP"), null,
                        null, null, null, TransactionType.CARD_PAYMENT,
                        Category.UNCATEGORISED, CategorySource.DEFAULT))
                .withMessageContaining("description");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-1.00", "GBP"), null,
                        "SOMEWHERE", null, null, TransactionType.CARD_PAYMENT,
                        null, CategorySource.DEFAULT))
                .withMessageContaining("category");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new RawTransaction(
                        BOOKED, null, Money.of("-1.00", "GBP"), null,
                        "SOMEWHERE", null, null, TransactionType.CARD_PAYMENT,
                        Category.UNCATEGORISED, null))
                .withMessageContaining("categorySource");
    }

    @Test
    void comparesByValueSoDuplicateDetectionCanRelyOnIt() {
        RawTransaction one = row(Money.of("-42.00", "GBP"));
        RawTransaction other = row(Money.of("-42.0000", "GBP"));

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(one).isNotEqualTo(row(Money.of("-42.01", "GBP")));
    }

    @Test
    void carriesTheCategoryAnAdapterMappedOutOfTheProvidersOwnVocabulary() {
        RawTransaction row = new RawTransaction(
                BOOKED, null, Money.of("-24.15", "GBP"), null,
                "GREENFIELD GROCERS", "Greenfield Grocers", "tx_1",
                TransactionType.CARD_PAYMENT, Category.GROCERIES, CategorySource.ADAPTER);

        assertThat(row.category()).isEqualTo(Category.GROCERIES);
        assertThat(row.categorySource()).isEqualTo(CategorySource.ADAPTER);
    }

    private static RawTransaction row(Money amount) {
        return new RawTransaction(
                BOOKED, null, amount, null, "TESCO STORES 3421", null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT);
    }
}
