package com.fapp.statement.monzo;

import com.fapp.statement.StatementParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits RFC 4180 delimited text into records and fields.
 *
 * <p>Deliberately package-private and owned by the Monzo adapter. Bank exports are
 * comma-separated but not interchangeable, and until a second adapter genuinely needs
 * the same behaviour there is no shared CSV concern to extract — promoting this before
 * then would be guessing at what the next format requires.
 *
 * <p>Handles what Monzo exports actually contain: quoted fields holding commas,
 * doubled quotes standing for a literal quote, and newlines inside a quoted note.
 * Blank lines are ignored so a trailing newline does not produce a phantom record.
 */
final class CsvReader {

    private CsvReader() {
    }

    /**
     * @param content the decoded file, with or without a trailing newline
     * @return one list of fields per record, quotes removed and escapes resolved
     * @throws StatementParseException if a quoted field is never closed
     */
    static List<List<String>> read(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int index = 0;

        while (index < content.length()) {
            char character = content.charAt(index);

            if (inQuotes) {
                if (character == '"') {
                    boolean escapedQuote = index + 1 < content.length() && content.charAt(index + 1) == '"';
                    if (escapedQuote) {
                        field.append('"');
                        index += 2;
                    } else {
                        inQuotes = false;
                        index++;
                    }
                } else {
                    field.append(character);
                    index++;
                }
                continue;
            }

            switch (character) {
                case '"' -> {
                    if (field.isEmpty()) {
                        inQuotes = true;
                    } else {
                        // A quote in the middle of a bare field is data, not a delimiter.
                        field.append(character);
                    }
                    index++;
                }
                case ',' -> {
                    record.add(field.toString());
                    field.setLength(0);
                    index++;
                }
                case '\r', '\n' -> {
                    if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                        index++;
                    }
                    record.add(field.toString());
                    field.setLength(0);
                    addUnlessBlank(records, record);
                    record = new ArrayList<>();
                    index++;
                }
                default -> {
                    field.append(character);
                    index++;
                }
            }
        }

        if (inQuotes) {
            throw new StatementParseException("the file ends inside a quoted value; it is not valid CSV");
        }
        if (!field.isEmpty() || !record.isEmpty()) {
            record.add(field.toString());
            addUnlessBlank(records, record);
        }
        return records;
    }

    private static void addUnlessBlank(List<List<String>> records, List<String> record) {
        boolean blankLine = record.size() == 1 && record.get(0).isBlank();
        if (!blankLine) {
            records.add(record);
        }
    }
}
