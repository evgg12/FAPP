package com.fapp.statement;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The delimited-text cases a real bank export actually contains. Each one would
 * silently shift every column after it if handled wrongly, which is why they are
 * pinned here rather than only through the adapter.
 */
class CsvReaderTest {

    @Test
    void splitsPlainFields() {
        assertThat(CsvReader.read("a,b,c")).containsExactly(List.of("a", "b", "c"));
    }

    @Test
    void keepsEmptyFieldsSoColumnPositionsHold() {
        assertThat(CsvReader.read("a,,c,")).containsExactly(List.of("a", "", "c", ""));
    }

    @Test
    void keepsACommaInsideAQuotedField() {
        assertThat(CsvReader.read("a,\"12 High Street, Sampleton\",c"))
                .containsExactly(List.of("a", "12 High Street, Sampleton", "c"));
    }

    @Test
    void resolvesADoubledQuoteToOneLiteralQuote() {
        assertThat(CsvReader.read("a,\"said \"\"next day\"\" delivery\",c"))
                .containsExactly(List.of("a", "said \"next day\" delivery", "c"));
    }

    @Test
    void keepsANewlineInsideAQuotedFieldWithoutStartingANewRecord() {
        List<List<String>> records = CsvReader.read("a,\"first\nsecond\",c\nd,e,f");

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsExactly("a", "first\nsecond", "c");
        assertThat(records.get(1)).containsExactly("d", "e", "f");
    }

    @Test
    void readsBothWindowsAndUnixLineEndings() {
        assertThat(CsvReader.read("a,b\r\nc,d\ne,f"))
                .containsExactly(List.of("a", "b"), List.of("c", "d"), List.of("e", "f"));
    }

    @Test
    void ignoresATrailingNewlineRatherThanReportingAPhantomRecord() {
        assertThat(CsvReader.read("a,b\r\n")).containsExactly(List.of("a", "b"));
        assertThat(CsvReader.read("a,b\n\n\n")).containsExactly(List.of("a", "b"));
    }

    @Test
    void treatsAQuoteInTheMiddleOfABareFieldAsData() {
        // Banks write merchant names such as GOOGLE *TEMPORARY HOLD unquoted; a stray
        // quote in one must not be read as a delimiter.
        assertThat(CsvReader.read("a,12\" pizza,c")).containsExactly(List.of("a", "12\" pizza", "c"));
    }

    @Test
    void returnsNothingForEmptyInput() {
        assertThat(CsvReader.read("")).isEmpty();
    }

    @Test
    void rejectsAQuotedFieldThatIsNeverClosed() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> CsvReader.read("a,\"unclosed,c"))
                .withMessageContaining("not valid CSV");
    }
}
