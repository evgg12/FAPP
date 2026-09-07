package com.fapp.statement.monzo;

import com.fapp.money.Money;
import com.fapp.statement.ParsedStatement;
import com.fapp.statement.RawTransaction;
import com.fapp.statement.StatementParseException;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.TransactionType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Drives the adapter with a sanitised export that keeps the structural edge cases of a
 * real Monzo download: a zero-value authorisation hold, a pending card payment, pot
 * transfers in and out, Bacs credit, faster and bank payments, an interest credit, a
 * genuine foreign-currency purchase alongside domestic rows whose "local" columns
 * merely repeat the settled figure, blank descriptions, quoted commas, escaped quotes,
 * a newline inside a note, and repeated merchants and amounts.
 */
class MonzoStatementAdapterTest {

    private static final String HEADER =
            "Transaction ID,Date,Time,Type,Name,Emoji,Category,Amount,Currency,Local amount,"
                    + "Local currency,Notes and #tags,Address,Receipt,Description,Category split,"
                    + "Money Out,Money In";

    private static byte[] export;

    private final MonzoStatementAdapter adapter = new MonzoStatementAdapter();

    @BeforeAll
    static void loadExport() {
        try (InputStream stream = MonzoStatementAdapterTest.class.getResourceAsStream("/monzo/statement.csv")) {
            export = stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void identifiesItselfAsMonzoInTheFormAccountsStore() {
        assertThat(adapter.provider()).isEqualTo("monzo").matches("^[a-z][a-z0-9_]*$");
    }

    @Test
    void parsesEveryRowThatMovedMoney() {
        // 19 data rows in the export, one of which is a zero-value hold.
        assertThat(parse().rowCount()).isEqualTo(18);
    }

    @Test
    void skipsTheZeroValueTemporaryHoldRatherThanBuildingATransactionFappWouldReject() {
        ParsedStatement statement = parse();

        assertThat(statement.transactions())
                .noneMatch(row -> row.externalId().equals("tx_sample000000000000001"));
        assertThat(statement.transactions()).noneMatch(row -> row.amount().isZero());
        assertThat(statement.transactions())
                .noneMatch(row -> row.description().contains("TEMPORARY HOLD"));
    }

    @Test
    void derivesTheStatementPeriodFromImportedRowsOnly() {
        // The skipped hold is dated 01/08, earlier than every real transaction.
        assertThat(parse().period().start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(parse().period().end()).isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    void coversEveryImportedBookingDateWithThePeriod() {
        ParsedStatement statement = parse();

        assertThat(statement.transactions()).allSatisfy(row -> {
            assertThat(row.bookingDate()).isAfterOrEqualTo(statement.period().start());
            assertThat(row.bookingDate()).isBeforeOrEqualTo(statement.period().end());
        });
    }

    @Test
    void takesTheBookingDateFromTheDateColumnAndDiscardsTheTime() {
        assertThat(row("tx_sample000000000000002").bookingDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(row("tx_sample000000000000018").bookingDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        // Nothing carries a time: occurredOn stays absent for every row.
        assertThat(parse().transactions()).allSatisfy(r -> assertThat(r.occurredOn()).isNull());
    }

    @Test
    void usesTheSignedAmountColumnInFappsConvention() {
        assertThat(row("tx_sample000000000000002").amount()).isEqualTo(Money.of("-24.15", "GBP"));
        assertThat(row("tx_sample000000000000008").amount()).isEqualTo(Money.of("1842.55", "GBP"));
        assertThat(row("tx_sample000000000000002").amount().isNegative()).isTrue();
        assertThat(row("tx_sample000000000000008").amount().isPositive()).isTrue();
    }

    @Test
    void takesTheTransactionCurrencyFromTheCurrencyColumn() {
        assertThat(parse().transactions())
                .allSatisfy(r -> assertThat(r.amount().currency().getCurrencyCode()).isEqualTo("GBP"));
    }

    @Test
    void keepsMonzosOwnTransactionIdAsTheExternalId() {
        assertThat(row("tx_sample000000000000005").externalId()).isEqualTo("tx_sample000000000000005");
        assertThat(parse().transactions()).allSatisfy(r -> assertThat(r.externalId()).isNotNull());
    }

    @Test
    void mapsNameToMerchantAndDescriptionToDescription() {
        RawTransaction groceries = row("tx_sample000000000000002");

        assertThat(groceries.merchant()).isEqualTo("Greenfield Grocers");
        assertThat(groceries.description()).isEqualTo("GREENFIELD GROCERS 4821");
    }

    @Test
    void fallsBackToTheCounterpartyWhenMonzoLeavesTheDescriptionBlank() {
        RawTransaction cinema = row("tx_sample000000000000016");

        assertThat(cinema.description()).isEqualTo("Sampleplex Cinema");
        assertThat(cinema.merchant()).isEqualTo("Sampleplex Cinema");
    }

    @Test
    void fallsBackToTheProviderTypeWhenBothDescriptionAndCounterpartyAreBlank() {
        RawTransaction anonymous = row("tx_sample000000000000017");

        assertThat(anonymous.description()).isEqualTo("Faster payment");
        assertThat(anonymous.merchant()).isNull();
    }

    @Test
    void leavesAbsentOptionalTextNullRatherThanEmpty() {
        assertThat(row("tx_sample000000000000017").merchant()).isNull();
        assertThat(parse().transactions()).allSatisfy(r -> {
            assertThat(r.merchant()).isNotEqualTo("");
            assertThat(r.externalId()).isNotEqualTo("");
        });
    }

    @Test
    void mapsEveryMonzoTypeInTheExportOntoTheFappEnum() {
        assertThat(row("tx_sample000000000000002").transactionType()).isEqualTo(TransactionType.CARD_PAYMENT);
        assertThat(row("tx_sample000000000000008").transactionType()).isEqualTo(TransactionType.OTHER);
        assertThat(row("tx_sample000000000000009").transactionType()).isEqualTo(TransactionType.OTHER);
        assertThat(row("tx_sample000000000000010").transactionType()).isEqualTo(TransactionType.OTHER);
        assertThat(row("tx_sample000000000000011").transactionType()).isEqualTo(TransactionType.OTHER);
        assertThat(row("tx_sample000000000000012").transactionType()).isEqualTo(TransactionType.OTHER);

        assertThat(parse().transactions())
                .extracting(RawTransaction::transactionType)
                .containsOnly(TransactionType.CARD_PAYMENT, TransactionType.OTHER);
    }

    @Test
    void mapsAnUnrecognisedMonzoTypeToOther() {
        ParsedStatement statement = adapter.parse(export(
                "tx_x,03/08/2026,08:00:00,Some Future Monzo Type,Somebody,,General,-1.00,GBP,-1.00,GBP,,,,SOMETHING,,1.00,"));

        assertThat(statement.transactions().get(0).transactionType()).isEqualTo(TransactionType.OTHER);
    }

    @Test
    void mapsMonzosCategoryVocabularyOntoFappsCategories() {
        assertThat(row("tx_sample000000000000002").category()).isEqualTo(Category.GROCERIES);
        assertThat(row("tx_sample000000000000003").category()).isEqualTo(Category.RESTAURANTS);
        assertThat(row("tx_sample000000000000006").category()).isEqualTo(Category.TRANSPORT);
        assertThat(row("tx_sample000000000000016").category()).isEqualTo(Category.ENTERTAINMENT);
        assertThat(row("tx_sample000000000000014").category()).isEqualTo(Category.BILLS);
        assertThat(row("tx_sample000000000000005").category()).isEqualTo(Category.SHOPPING);
        assertThat(row("tx_sample000000000000008").category()).isEqualTo(Category.INCOME);
        assertThat(row("tx_sample000000000000011").category()).isEqualTo(Category.TRANSFER);
        assertThat(row("tx_sample000000000000009").category()).isEqualTo(Category.UNCATEGORISED);
    }

    @Test
    void mapsAnUnknownMonzoCategoryToUncategorisedRatherThanInventingOne() {
        // "Personal care" is a real Monzo category with no FAPP equivalent.
        assertThat(row("tx_sample000000000000013").category()).isEqualTo(Category.UNCATEGORISED);
    }

    @Test
    void marksEveryCategoryAsDerivedByTheAdapter() {
        assertThat(parse().transactions())
                .extracting(RawTransaction::categorySource)
                .containsOnly(CategorySource.ADAPTER);
    }

    @Test
    void letsNoMonzoVocabularyReachTheCanonicalModel() {
        List<String> monzoOnlyTerms = List.of("eating out", "personal care", "general", "monzo_paid",
                "bacs (direct credit)", "faster payment", "pot transfer");

        assertThat(parse().transactions()).allSatisfy(row -> {
            assertThat(Category.values()).contains(row.category());
            assertThat(TransactionType.values()).contains(row.transactionType());
            assertThat(monzoOnlyTerms).doesNotContain(row.category().name().toLowerCase(Locale.ROOT));
        });
    }

    @Test
    void recordsTheForeignLegOnlyWhenTheLocalCurrencyGenuinelyDiffers() {
        RawTransaction abroad = row("tx_sample000000000000005");

        assertThat(abroad.amount()).isEqualTo(Money.of("-18.62", "GBP"));
        assertThat(abroad.originalAmount()).isEqualTo(Money.of("-21.90", "EUR"));
    }

    @Test
    void treatsALocalAmountInTheSameCurrencyAsNotAnFxTransaction() {
        // Monzo restates the settled figure in the local columns for domestic spending.
        assertThat(row("tx_sample000000000000002").originalAmount()).isNull();
        assertThat(row("tx_sample000000000000014").originalAmount()).isNull();
        assertThat(parse().transactions())
                .filteredOn(r -> r.originalAmount() != null)
                .hasSize(1);
    }

    @Test
    void keepsPotTransfersAsOrdinaryRowsCategorisedAsSavings() {
        RawTransaction out = row("tx_sample000000000000010");
        RawTransaction in = row("tx_sample000000000000019");

        assertThat(out.amount()).isEqualTo(Money.of("-200.00", "GBP"));
        // Savings, not Transfer: a pot movement is reported as a pot balance and kept out
        // of the spending breakdown.
        assertThat(out.category()).isEqualTo(Category.SAVINGS);
        assertThat(out.transactionType()).isEqualTo(TransactionType.OTHER);

        // Monzo filed this one under "General"; being a pot transfer decides it.
        assertThat(in.amount()).isEqualTo(Money.of("75.00", "GBP"));
        assertThat(in.category()).isEqualTo(Category.SAVINGS);
        assertThat(in.transactionType()).isEqualTo(TransactionType.OTHER);
    }

    @Test
    void importsAPendingCardPaymentAsAnOrdinaryTransaction() {
        RawTransaction pending = row("tx_sample000000000000007");

        assertThat(pending.amount()).isEqualTo(Money.of("-14.75", "GBP"));
        assertThat(pending.transactionType()).isEqualTo(TransactionType.CARD_PAYMENT);
        assertThat(pending.category()).isEqualTo(Category.TRANSPORT);
        assertThat(pending.description()).contains("PENDING");
    }

    @Test
    void keepsRepeatedMerchantsAndRepeatedAmountsAsSeparateRows() {
        assertThat(parse().transactions())
                .filteredOn(r -> "Greenfield Grocers".equals(r.merchant()))
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.amount()).isEqualTo(Money.of("-24.15", "GBP")))
                .extracting(RawTransaction::externalId)
                .doesNotHaveDuplicates();

        assertThat(parse().transactions())
                .filteredOn(r -> r.amount().equals(Money.of("-8.40", "GBP")))
                .hasSize(2);
    }

    @Test
    void readsQuotedCommasEscapedQuotesAndNewlinesWithoutLettingThemLeakIntoTheDescription() {
        RawTransaction hardware = row("tx_sample000000000000018");

        assertThat(hardware.description()).isEqualTo("SAMPLE HARDWARE 22");
        assertThat(hardware.merchant()).isEqualTo("Sample Hardware");
        // Address, notes, receipt and emoji stay out of the canonical fields.
        assertThat(hardware.description()).doesNotContain("Dock Road", "next day", "\n");
        assertThat(row("tx_sample000000000000003").description()).doesNotContain("Mill Lane", "lunch");
    }

    @Test
    void preservesTheOrderTheExportListedTheRowsIn() {
        assertThat(parse().transactions())
                .extracting(RawTransaction::bookingDate)
                .isSorted();
    }

    // --- malformed input ---

    @Test
    void rejectsAFileThatIsNotAMonzoExport() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse("Date,Description,Amount\n01/08/2026,X,-1.00\n"
                        .getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("does not look like a Monzo export");
    }

    @Test
    void namesTheColumnsItNeededWhenTheyAreMissing() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(
                        HEADER.replace(",Local currency", ",Something Else").getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("Local currency");
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(new byte[0]))
                .withMessageContaining("empty");
    }

    @Test
    void rejectsAnExportWithAHeaderAndNoTransactions() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(HEADER.getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("no transactions");
    }

    @Test
    void rejectsAnUnreadableDateAndSaysWhichRow() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "tx_x,2026-08-03,08:00:00,Card payment,Somebody,,General,-1.00,GBP,-1.00,GBP,,,,SOMETHING,,1.00,")))
                .withMessageContaining("row 2")
                .withMessageContaining("dd/MM/yyyy");
    }

    @Test
    void rejectsADateThatDoesNotExist() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "tx_x,31/02/2026,08:00:00,Card payment,Somebody,,General,-1.00,GBP,-1.00,GBP,,,,SOMETHING,,1.00,")))
                .withMessageContaining("row 2");
    }

    @Test
    void rejectsAnUnreadableAmount() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "tx_x,03/08/2026,08:00:00,Card payment,Somebody,,General,ninety pounds,GBP,-1.00,GBP,,,,SOMETHING,,1.00,")))
                .withMessageContaining("row 2")
                .withMessageContaining("is not an amount");
    }

    @Test
    void rejectsAnUnknownCurrencyCode() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "tx_x,03/08/2026,08:00:00,Card payment,Somebody,,General,-1.00,XYZ,-1.00,XYZ,,,,SOMETHING,,1.00,")))
                .withMessageContaining("row 2")
                .withMessageContaining("is not a currency code");
    }

    @Test
    void rejectsAMissingRequiredValue() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "tx_x,03/08/2026,08:00:00,Card payment,Somebody,,General,,GBP,-1.00,GBP,,,,SOMETHING,,1.00,")))
                .withMessageContaining("row 2")
                .withMessageContaining("Amount");
    }

    @Test
    void rejectsAnAmountNeedingMorePrecisionThanFappStores() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "tx_x,03/08/2026,08:00:00,Card payment,Somebody,,General,-1.234567,GBP,-1.234567,GBP,,,,SOMETHING,,1.23,")))
                .withMessageContaining("row 2");
    }

    @Test
    void rejectsATruncatedRow() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export("tx_x,03/08/2026,08:00:00,Card payment")))
                .withMessageContaining("row 2");
    }

    @Test
    void rejectsAFileThatEndsInsideAQuotedValue() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "tx_x,03/08/2026,08:00:00,Card payment,Somebody,,General,-1.00,GBP,-1.00,GBP,\"unclosed")))
                .withMessageContaining("not valid CSV");
    }

    @Test
    void rejectsBytesThatAreNotUtf8RatherThanCorruptingTheText() {
        byte[] invalid = {(byte) 0xC3, (byte) 0x28};

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(invalid))
                .withMessageContaining("not valid UTF-8");
    }

    @Test
    void refusesTheWholeStatementWhenASingleRowIsCorrupt() {
        byte[] mostlyGood = export(
                "tx_ok,03/08/2026,08:00:00,Card payment,Somebody,,Groceries,-1.00,GBP,-1.00,GBP,,,,FINE,,1.00,",
                "tx_bad,03/08/2026,08:00:00,Card payment,Somebody,,Groceries,not-a-number,GBP,-1.00,GBP,,,,BROKEN,,1.00,",
                "tx_ok2,04/08/2026,08:00:00,Card payment,Somebody,,Groceries,-2.00,GBP,-2.00,GBP,,,,ALSO FINE,,2.00,");

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(mostlyGood))
                .withMessageContaining("row 3");
    }

    @Test
    void toleratesAByteOrderMarkOnTheHeader() {
        byte[] withBom = ("﻿" + HEADER + "\r\n"
                + "tx_x,03/08/2026,08:00:00,Card payment,Somebody,,Groceries,-1.00,GBP,-1.00,GBP,,,,SOMETHING,,1.00,\r\n")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(adapter.parse(withBom).rowCount()).isEqualTo(1);
    }

    // --- helpers ---

    private ParsedStatement parse() {
        return adapter.parse(export);
    }

    private RawTransaction row(String externalId) {
        return parse().transactions().stream()
                .filter(transaction -> externalId.equals(transaction.externalId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row with external id " + externalId));
    }

    private static byte[] export(String... rows) {
        return (HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
