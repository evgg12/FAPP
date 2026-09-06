package com.fapp.statement.bankofscotland;

import com.fapp.money.Money;
import com.fapp.statement.ParsedStatement;
import com.fapp.statement.RawTransaction;
import com.fapp.statement.StatementParseException;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.TransactionType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Drives the adapter with a sanitised export keeping the structure of a real Bank of
 * Scotland download: all four transaction types, the split debit/credit columns, a
 * running balance, a quoted description containing a comma, padded fields, and two rows
 * identical in everything the fingerprint covers.
 */
class BankOfScotlandStatementAdapterTest {

    private static final String HEADER = "Transaction Date,Transaction Type,Sort Code,Account Number,"
            + "Transaction Description,Debit Amount,Credit Amount,Balance";

    private static byte[] export;

    private final BankOfScotlandStatementAdapter adapter = new BankOfScotlandStatementAdapter();

    @BeforeAll
    static void loadExport() {
        try (InputStream stream = BankOfScotlandStatementAdapterTest.class
                .getResourceAsStream("/bankofscotland/statement.csv")) {
            export = stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void identifiesItselfAsBankOfScotlandInTheFormAccountsStore() {
        assertThat(adapter.provider()).isEqualTo("bank_of_scotland").matches("^[a-z][a-z0-9_]*$");
    }

    @Test
    void parsesEveryTransactionRow() {
        assertThat(parse().rowCount()).isEqualTo(10);
    }

    @Test
    void derivesThePeriodFromTheEarliestAndLatestTransactionDate() {
        ParsedStatement statement = parse();

        assertThat(statement.period().start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(statement.period().end()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(statement.transactions()).allSatisfy(row -> {
            assertThat(row.bookingDate()).isAfterOrEqualTo(statement.period().start());
            assertThat(row.bookingDate()).isBeforeOrEqualTo(statement.period().end());
        });
    }

    @Test
    void takesTheBookingDateFromTheTransactionDateAndCarriesNoTime() {
        assertThat(parse().transactions().get(0).bookingDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(parse().transactions()).allSatisfy(row -> assertThat(row.occurredOn()).isNull());
    }

    @Test
    void makesADebitNegativeAndACreditPositiveInSterling() {
        // Two rows share the description "A COUNTERPARTY": the FPO debit and the FPI
        // credit. Which column held the amount is what decides the sign.
        assertThat(rowDescribed("SAMPLE GROCER 1234").amount()).isEqualTo(Money.of("-24.15", "GBP"));
        assertThat(rowDescribed("SAMPLE EMPLOYER LTD").amount()).isEqualTo(Money.of("1842.55", "GBP"));
        assertThat(parse().transactions())
                .filteredOn(row -> "A COUNTERPARTY".equals(row.description()))
                .extracting(RawTransaction::amount)
                .containsExactly(Money.of("-45.00", "GBP"), Money.of("20.00", "GBP"));
        assertThat(parse().transactions())
                .allSatisfy(row -> assertThat(row.amount().currency().getCurrencyCode()).isEqualTo("GBP"));
    }

    @Test
    void mapsEachTransactionTypeCodeOntoTheFappVocabulary() {
        assertThat(typeOf("DEB")).isEqualTo(TransactionType.CARD_PAYMENT);
        assertThat(typeOf("FPO")).isEqualTo(TransactionType.OTHER);
        assertThat(typeOf("FPI")).isEqualTo(TransactionType.OTHER);
        assertThat(typeOf("BGC")).isEqualTo(TransactionType.OTHER);

        assertThat(parse().transactions())
                .extracting(RawTransaction::transactionType)
                .containsOnly(TransactionType.CARD_PAYMENT, TransactionType.OTHER);
    }

    @Test
    void leavesEveryRowUncategorisedBecauseTheFormatSaysNothingAboutCategories() {
        assertThat(parse().transactions()).allSatisfy(row -> {
            assertThat(row.category()).isEqualTo(Category.UNCATEGORISED);
            assertThat(row.categorySource()).isEqualTo(CategorySource.DEFAULT);
        });
    }

    @Test
    void leavesTheExternalIdAbsentBecauseTheExportHasNoTransactionId() {
        assertThat(parse().transactions()).allSatisfy(row -> assertThat(row.externalId()).isNull());
    }

    @Test
    void usesTheDescriptionForBothDescriptionAndMerchant() {
        RawTransaction row = rowDescribed("SAMPLE GROCER 1234");

        assertThat(row.description()).isEqualTo("SAMPLE GROCER 1234");
        assertThat(row.merchant()).isEqualTo("SAMPLE GROCER 1234");
    }

    @Test
    void readsADescriptionContainingACommaThroughCsvQuoting() {
        assertThat(rowDescribed("SAMPLE CAFE, SAMPLETON").amount()).isEqualTo(Money.of("-8.40", "GBP"));
    }

    @Test
    void trimsThePaddingRealExportsLeaveAroundFields() {
        RawTransaction padded = rowDescribed("SAMPLE SAVINGS TRANSFER");

        assertThat(padded.amount()).isEqualTo(Money.of("-300.00", "GBP"));
        assertThat(padded.transactionType()).isEqualTo(TransactionType.OTHER);
        assertThat(padded.bookingDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void keepsTwoRowsIdenticalInEveryFingerprintedFieldAsSeparateTransactions() {
        List<RawTransaction> coffees = parse().transactions().stream()
                .filter(row -> "SAMPLE CAFE 2".equals(row.description()))
                .toList();

        assertThat(coffees).hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.amount()).isEqualTo(Money.of("-2.50", "GBP"));
                    assertThat(row.bookingDate()).isEqualTo(LocalDate.of(2026, 8, 6));
                });
    }

    @Test
    void neverReadsTheSortCodeOrAccountNumber() {
        // Both columns are present in the export and deliberately absent from the
        // required set, so no value from either can reach a transaction.
        assertThat(parse().transactions()).allSatisfy(row -> {
            assertThat(row.description()).doesNotContain("00-00-00", "00000000");
            assertThat(row.merchant()).doesNotContain("00-00-00", "00000000");
        });

        ParsedStatement withoutIdentifiers = adapter.parse((
                "Transaction Date,Transaction Type,Transaction Description,Debit Amount,Credit Amount\r\n"
                        + "03/08/2026,DEB,SOMETHING,1.00,\r\n").getBytes(StandardCharsets.UTF_8));

        assertThat(withoutIdentifiers.rowCount()).isEqualTo(1);
    }

    @Test
    void acceptsLargerSterlingFiguresWrittenWithThousandsSeparators() {
        ParsedStatement statement = adapter.parse(export(
                "17/08/2026,BGC,00-00-00,00000000,SAMPLE EMPLOYER LTD,,\"1,842.55\",\"3,780.00\""));

        assertThat(statement.transactions().get(0).amount()).isEqualTo(Money.of("1842.55", "GBP"));
    }

    // --- malformed input ---

    @Test
    void rejectsAFileThatIsNotABankOfScotlandExport() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(
                        "Date,Description,Amount\r\n03/08/2026,X,-1.00\r\n".getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("does not look like a Bank of Scotland export")
                .withMessageContaining("Transaction Date");
    }

    @Test
    void rejectsAnEmptyUploadAndAHeaderWithNoRows() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(new byte[0]))
                .withMessageContaining("empty");

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(HEADER.getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("no transactions");
    }

    @Test
    void rejectsAMalformedDate() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "2026-08-03,DEB,00-00-00,00000000,SOMETHING,1.00,,1.00")))
                .withMessageContaining("row 2")
                .withMessageContaining("dd/MM/yyyy");

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "31/02/2026,DEB,00-00-00,00000000,SOMETHING,1.00,,1.00")))
                .withMessageContaining("row 2");
    }

    @Test
    void rejectsAMalformedAmount() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,SOMETHING,ninety pounds,,1.00")))
                .withMessageContaining("row 2")
                .withMessageContaining("Debit Amount");
    }

    @Test
    void rejectsACommaUsedAsADecimalSeparatorRatherThanMultiplyingItByAHundred() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,SOMETHING,\"1234,56\",,1.00")))
                .withMessageContaining("is not an amount");
    }

    @Test
    void rejectsARowWithNoAmountAtAll() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,SOMETHING,,,1.00")))
                .withMessageContaining("row 2")
                .withMessageContaining("neither Debit Amount nor Credit Amount");
    }

    @Test
    void rejectsARowThatIsBothADebitAndACredit() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,SOMETHING,1.00,2.00,1.00")))
                .withMessageContaining("row 2")
                .withMessageContaining("cannot be both a debit and a credit");
    }

    @Test
    void rejectsAZeroValueRow() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,SOMETHING,0.00,,1.00")))
                .withMessageContaining("row 2")
                .withMessageContaining("zero");
    }

    @Test
    void rejectsATransactionTypeItDoesNotKnow() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,XYZ,00-00-00,00000000,SOMETHING,1.00,,1.00")))
                .withMessageContaining("row 2")
                .withMessageContaining("'XYZ' is not a transaction type")
                .withMessageContaining("DEB");
    }

    @Test
    void rejectsAMissingTypeOrDescription() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,,00-00-00,00000000,SOMETHING,1.00,,1.00")))
                .withMessageContaining("Transaction Type column is empty");

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,,1.00,,1.00")))
                .withMessageContaining("Transaction Description column is empty");
    }

    @Test
    void rejectsABalanceThatIsNotANumberBecauseTheRowIsProbablyShifted() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,SOMETHING,1.00,,not-a-balance")))
                .withMessageContaining("row 2")
                .withMessageContaining("Balance");
    }

    @Test
    void refusesTheWholeStatementWhenASingleRowIsCorrupt() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(export(
                        "03/08/2026,DEB,00-00-00,00000000,FINE,1.00,,1.00",
                        "04/08/2026,DEB,00-00-00,00000000,BROKEN,nope,,1.00",
                        "05/08/2026,DEB,00-00-00,00000000,ALSO FINE,2.00,,1.00")))
                .withMessageContaining("row 3");
    }

    @Test
    void rejectsBytesThatAreNotUtf8() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> adapter.parse(new byte[] {(byte) 0xC3, (byte) 0x28}))
                .withMessageContaining("not valid UTF-8");
    }

    // --- helpers ---

    private ParsedStatement parse() {
        return adapter.parse(export);
    }

    private RawTransaction rowDescribed(String description) {
        return parse().transactions().stream()
                .filter(row -> description.equals(row.description()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row described as " + description));
    }

    private TransactionType typeOf(String code) {
        return adapter.parse(export("03/08/2026," + code + ",00-00-00,00000000,SOMETHING,1.00,,1.00"))
                .transactions().get(0).transactionType();
    }

    private static byte[] export(String... rows) {
        return (HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
