package com.fapp.statement;

import com.fapp.money.Money;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.TransactionType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ParsedStatementTest {

    private static final StatementPeriod MARCH =
            StatementPeriod.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    @Test
    void derivesThePeriodFromTheEarliestAndLatestRowsForFormatsThatDeclareNone() {
        ParsedStatement statement = ParsedStatement.of(List.of(
                row(LocalDate.of(2026, 3, 14)),
                row(LocalDate.of(2026, 3, 2)),
                row(LocalDate.of(2026, 3, 28))));

        assertThat(statement.period().start()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(statement.period().end()).isEqualTo(LocalDate.of(2026, 3, 28));
    }

    @Test
    void derivesASingleDayPeriodFromASingleRow() {
        ParsedStatement statement = ParsedStatement.of(List.of(row(LocalDate.of(2026, 3, 14))));

        assertThat(statement.period())
                .isEqualTo(StatementPeriod.of(LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 14)));
    }

    @Test
    void refusesToInventAPeriodForAStatementWithNoRows() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ParsedStatement.of(List.of()))
                .withMessageContaining("supply the period explicitly");
    }

    @Test
    void acceptsAnEmptyStatementWhenThePeriodIsDeclared() {
        ParsedStatement statement = new ParsedStatement(List.of(), MARCH);

        assertThat(statement.transactions()).isEmpty();
        assertThat(statement.rowCount()).isZero();
        assertThat(statement.period()).isEqualTo(MARCH);
    }

    @Test
    void keepsADeclaredPeriodWiderThanItsRows() {
        // A quiet end to the month is real coverage, and deduplication is scoped by it.
        ParsedStatement statement = new ParsedStatement(List.of(row(LocalDate.of(2026, 3, 4))), MARCH);

        assertThat(statement.period()).isEqualTo(MARCH);
    }

    @Test
    void rejectsARowBookedOutsideTheDeclaredPeriod() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ParsedStatement(List.of(row(LocalDate.of(2026, 4, 1))), MARCH))
                .withMessageContaining("outside the statement period");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ParsedStatement(List.of(row(LocalDate.of(2026, 2, 28))), MARCH))
                .withMessageContaining("outside the statement period");
    }

    @Test
    void acceptsRowsOnTheInclusiveBoundaries() {
        ParsedStatement statement = new ParsedStatement(
                List.of(row(LocalDate.of(2026, 3, 1)), row(LocalDate.of(2026, 3, 31))), MARCH);

        assertThat(statement.rowCount()).isEqualTo(2);
    }

    @Test
    void reportsTheRowCountAStatementImportRecords() {
        ParsedStatement statement = new ParsedStatement(
                List.of(row(LocalDate.of(2026, 3, 1)), row(LocalDate.of(2026, 3, 2)),
                        row(LocalDate.of(2026, 3, 3))), MARCH);

        assertThat(statement.rowCount()).isEqualTo(3).isEqualTo(statement.transactions().size());
    }

    @Test
    void preservesTheOrderTheStatementListedItsRowsIn() {
        RawTransaction first = row(LocalDate.of(2026, 3, 20));
        RawTransaction second = row(LocalDate.of(2026, 3, 5));

        assertThat(new ParsedStatement(List.of(first, second), MARCH).transactions())
                .containsExactly(first, second);
    }

    @Test
    void takesADefensiveCopySoAnAdapterCannotChangeItAfterwards() {
        List<RawTransaction> mutable = new ArrayList<>(List.of(row(LocalDate.of(2026, 3, 1))));
        ParsedStatement statement = new ParsedStatement(mutable, MARCH);

        mutable.add(row(LocalDate.of(2026, 3, 2)));

        assertThat(statement.rowCount()).isEqualTo(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> statement.transactions().add(row(LocalDate.of(2026, 3, 3))));
    }

    @Test
    void rejectsMissingArguments() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ParsedStatement(null, MARCH))
                .withMessageContaining("transactions");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new ParsedStatement(List.of(), null))
                .withMessageContaining("period");

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ParsedStatement.of(null))
                .withMessageContaining("transactions");
    }

    private static RawTransaction row(LocalDate bookingDate) {
        return new RawTransaction(
                bookingDate, null, Money.of("-12.34", "GBP"), null,
                "ROW " + bookingDate, null, null, TransactionType.CARD_PAYMENT,
                Category.UNCATEGORISED, CategorySource.DEFAULT);
    }
}
