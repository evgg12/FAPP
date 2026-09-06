package com.fapp.statement;

import com.fapp.money.Money;
import com.fapp.transaction.TransactionType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Verifies the adapter contract holds up, using throwaway stubs rather than a real
 * bank. What matters is that the contract is enough to add a provider without any
 * other type learning about it: two unrelated stubs are selected purely by
 * {@link StatementAdapter#provider()}, and a malformed upload is refused whole.
 */
class StatementAdapterTest {

    private final StatementAdapter first = new StubAdapter("first_bank", LocalDate.of(2026, 3, 4));
    private final StatementAdapter second = new StubAdapter("second_bank", LocalDate.of(2026, 3, 9));
    private final List<StatementAdapter> adapters = List.of(first, second);

    @Test
    void picksAnAdapterByProviderSlugAloneWithoutKnowingWhichExist() {
        assertThat(adapterFor("second_bank")).isSameAs(second);
        assertThat(adapterFor("first_bank")).isSameAs(first);
        assertThat(adapters.stream().filter(a -> a.provider().equals("unregistered_bank")).findFirst()).isEmpty();
    }

    @Test
    void reportsAProviderSlugInTheFormTheDatabaseStores() {
        // accounts.provider and statement_imports.provider both enforce this shape, so
        // an adapter whose slug does not match it could never be selected for an account.
        assertThat(adapters).allSatisfy(adapter ->
                assertThat(adapter.provider()).matches("^[a-z][a-z0-9_]*$"));
    }

    @Test
    void producesProviderIndependentRowsFromProviderSpecificBytes() {
        ParsedStatement statement = second.parse("ok".getBytes(StandardCharsets.UTF_8));

        assertThat(statement.rowCount()).isEqualTo(1);
        RawTransaction row = statement.transactions().get(0);
        assertThat(row.bookingDate()).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(row.amount()).isEqualTo(Money.of("-25.00", "GBP"));
        assertThat(row.transactionType()).isEqualTo(TransactionType.CARD_PAYMENT);
        // Nothing on the row says which bank produced it.
        assertThat(RawTransaction.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase(Locale.ROOT).contains("provider"));
    }

    @Test
    void suppliesThePeriodTheApplicationLayerNeedsForAStatementImport() {
        ParsedStatement statement = first.parse("ok".getBytes(StandardCharsets.UTF_8));

        assertThat(statement.period().start()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(statement.period().end()).isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    void rejectsAMalformedStatementWholeRatherThanReturningWhatItCouldRead() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> first.parse("broken".getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("row 2");
    }

    @Test
    void carriesTheUnderlyingCauseWhenParsingFailsOnSomethingElse() {
        NumberFormatException cause = new NumberFormatException("ninety pounds");

        StatementParseException exception = new StatementParseException("row 12: not an amount", cause);

        assertThat(exception).hasMessageContaining("row 12").hasCause(cause);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    private StatementAdapter adapterFor(String provider) {
        return adapters.stream()
                .filter(adapter -> adapter.provider().equals(provider))
                .findFirst()
                .orElseThrow();
    }

    /** A stand-in for a real bank adapter. Reads a marker, not a statement format. */
    private record StubAdapter(String provider, LocalDate bookingDate) implements StatementAdapter {

        @Override
        public ParsedStatement parse(byte[] statement) {
            if (!"ok".equals(new String(statement, StandardCharsets.UTF_8))) {
                throw new StatementParseException("row 2: unreadable statement for " + provider);
            }
            return ParsedStatement.of(List.of(new RawTransaction(
                    bookingDate, null, Money.of("-25.00", "GBP"), null,
                    "STUB ROW", null, null, TransactionType.CARD_PAYMENT)));
        }
    }
}
