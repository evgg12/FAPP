package com.fapp.statement.bankofscotland;

import com.fapp.money.Money;
import com.fapp.statement.CsvReader;
import com.fapp.statement.ParsedStatement;
import com.fapp.statement.RawTransaction;
import com.fapp.statement.StatementAdapter;
import com.fapp.statement.StatementParseException;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.TransactionType;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads Bank of Scotland's CSV statement export.
 *
 * <p>A plainer format than Monzo's, and the differences are the interesting part. It
 * splits the amount across a debit column and a credit column instead of signing one,
 * gives no transaction id, no time and no category, and carries a running balance that
 * is a property of the statement rather than of any transaction. All of that is
 * reconciled here so that what leaves this class is indistinguishable in shape from any
 * other bank's rows.
 *
 * <p>Two columns are deliberately never read. The export includes the account's sort
 * code and account number on every row, and FAPP has no use for either: nothing
 * downstream needs to identify the account beyond the account it is being imported into.
 * They are not required in the header check and their values are never touched, so they
 * cannot reach a transaction, a log line or a fingerprint.
 *
 * <p>Some decisions worth knowing about:
 *
 * <ul>
 *   <li>The sign comes from which column holds the amount, not from the type code. A
 *       debit is money out and a credit is money in whatever the bank filed the row as,
 *       which keeps a refund or reversal readable instead of contradictory.
 *   <li>Exactly one of the two amount columns must be filled. Neither means the row
 *       describes no movement; both means the row is not the format this reads.
 *   <li>Balance is validated as a number when present, purely to catch a shifted or
 *       corrupt row, and then discarded. It is not used to reconstruct amounts and never
 *       reaches {@link RawTransaction}.
 *   <li>Every row is {@link Category#UNCATEGORISED} from
 *       {@link CategorySource#DEFAULT}: the format carries no category, so there is
 *       nothing for an adapter to derive and categorising by merchant name belongs to a
 *       later layer.
 *   <li>Having no transaction id, this provider relies entirely on the content
 *       fingerprint for deduplication, which is why {@code externalId} is always absent.
 * </ul>
 */
@Component
public class BankOfScotlandStatementAdapter implements StatementAdapter {

    /** Matches the value stored in {@code accounts.provider} for Bank of Scotland accounts. */
    public static final String PROVIDER = "bank_of_scotland";

    /** The only currency this export is issued in. */
    private static final Currency GBP = Currency.getInstance("GBP");

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.UK).withResolverStyle(ResolverStyle.STRICT);

    /**
     * A plain decimal, optionally with the thousands separators a sterling statement
     * writes on larger figures. Grouped strictly in threes, so a comma used as a decimal
     * separator fails to match rather than being silently multiplied by a hundred.
     */
    private static final Pattern AMOUNT = Pattern.compile("^-?\\d{1,3}(,\\d{3})*(\\.\\d+)?$|^-?\\d+(\\.\\d+)?$");

    private static final String DATE_COLUMN = "Transaction Date";
    private static final String TYPE = "Transaction Type";
    private static final String DESCRIPTION = "Transaction Description";
    private static final String DEBIT = "Debit Amount";
    private static final String CREDIT = "Credit Amount";
    private static final String BALANCE = "Balance";

    /**
     * The columns this adapter reads. Sort Code and Account Number are absent by
     * intent, not oversight.
     */
    private static final List<String> REQUIRED_COLUMNS =
            List.of(DATE_COLUMN, TYPE, DESCRIPTION, DEBIT, CREDIT);

    /**
     * Bank of Scotland's type codes.
     *
     * <p>Only a debit card payment has a direct equivalent. The three payment codes are
     * mapped to {@code OTHER} rather than {@code BANK_TRANSFER} so that the same
     * movement carries the same type whichever bank reported it — the equivalent Monzo
     * types map to {@code OTHER} too, and a type that depended on the provider would
     * make analytics grouped by type depend on it as well.
     */
    private static final Map<String, TransactionType> TRANSACTION_TYPES = Map.of(
            "DEB", TransactionType.CARD_PAYMENT,
            "FPO", TransactionType.OTHER,
            "FPI", TransactionType.OTHER,
            "BGC", TransactionType.OTHER);

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ParsedStatement parse(byte[] statement) {
        List<List<String>> records = CsvReader.read(decode(statement));
        if (records.isEmpty()) {
            throw new StatementParseException("the uploaded file is empty");
        }

        Map<String, Integer> columns = locateColumns(records.get(0));
        List<RawTransaction> transactions = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            // Row numbers are the ones a person sees in a spreadsheet, header included.
            transactions.add(readRow(records.get(index), columns, index + 1));
        }

        if (transactions.isEmpty()) {
            throw new StatementParseException("the statement contains no transactions to import");
        }
        // The export declares no period of its own, so it spans the rows it contains.
        return ParsedStatement.of(transactions);
    }

    private RawTransaction readRow(List<String> record, Map<String, Integer> columns, int rowNumber) {
        String description = value(record, columns, DESCRIPTION);
        if (description == null) {
            throw new StatementParseException("row " + rowNumber + ": the Transaction Description column is empty");
        }
        validateBalance(record, columns, rowNumber);

        try {
            return new RawTransaction(
                    readBookingDate(record, columns, rowNumber),
                    // The export carries no time of day.
                    null,
                    readAmount(record, columns, rowNumber),
                    // Sterling throughout; the format has no foreign-currency leg.
                    null,
                    description,
                    // The only text the row offers, so it is the best merchant available.
                    description,
                    // The export gives no transaction id, so identity rests on the fingerprint.
                    null,
                    transactionType(record, columns, rowNumber),
                    Category.UNCATEGORISED,
                    CategorySource.DEFAULT);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new StatementParseException("row " + rowNumber + ": " + e.getMessage(), e);
        }
    }

    private static LocalDate readBookingDate(List<String> record, Map<String, Integer> columns, int rowNumber) {
        String text = value(record, columns, DATE_COLUMN);
        if (text == null) {
            throw new StatementParseException("row " + rowNumber + ": the Transaction Date column is empty");
        }
        try {
            return LocalDate.parse(text, DATE);
        } catch (DateTimeParseException e) {
            throw new StatementParseException(
                    "row " + rowNumber + ": '" + text + "' is not a date in dd/MM/yyyy form", e);
        }
    }

    /**
     * The signed amount, taken from whichever of the two columns holds it. A debit left
     * the account and a credit arrived in it, which is the whole of FAPP's sign
     * convention.
     */
    private static Money readAmount(List<String> record, Map<String, Integer> columns, int rowNumber) {
        String debit = value(record, columns, DEBIT);
        String credit = value(record, columns, CREDIT);

        if (debit != null && credit != null) {
            throw new StatementParseException("row " + rowNumber
                    + ": a row cannot be both a debit and a credit, but both amounts are filled in");
        }
        if (debit == null && credit == null) {
            throw new StatementParseException("row " + rowNumber
                    + ": neither Debit Amount nor Credit Amount holds an amount");
        }

        boolean outgoing = debit != null;
        BigDecimal magnitude = parseAmount(outgoing ? debit : credit, outgoing ? DEBIT : CREDIT, rowNumber);
        try {
            return Money.of(outgoing ? magnitude.negate() : magnitude, GBP);
        } catch (IllegalArgumentException e) {
            throw new StatementParseException("row " + rowNumber + ": " + e.getMessage(), e);
        }
    }

    private static BigDecimal parseAmount(String text, String column, int rowNumber) {
        if (!AMOUNT.matcher(text).matches()) {
            throw new StatementParseException(
                    "row " + rowNumber + ": '" + text + "' in " + column + " is not an amount");
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new StatementParseException(
                    "row " + rowNumber + ": '" + text + "' in " + column + " is not an amount", e);
        }
    }

    /**
     * Rejects a balance that is not a number. A shifted delimiter can leave the amount
     * columns readable while putting text where the balance belongs, and this is the
     * cheapest place to notice. The value is then discarded: a running balance describes
     * the statement, not a transaction, and reconstructing amounts from it would mean
     * assuming the rows are ordered and the history is complete.
     */
    private static void validateBalance(List<String> record, Map<String, Integer> columns, int rowNumber) {
        Integer index = columns.get(BALANCE);
        if (index == null) {
            return;
        }
        String balance = value(record, columns, BALANCE);
        if (balance != null && !AMOUNT.matcher(balance).matches()) {
            throw new StatementParseException(
                    "row " + rowNumber + ": '" + balance + "' in " + BALANCE + " is not an amount");
        }
    }

    private static TransactionType transactionType(List<String> record, Map<String, Integer> columns, int rowNumber) {
        String code = value(record, columns, TYPE);
        if (code == null) {
            throw new StatementParseException("row " + rowNumber + ": the Transaction Type column is empty");
        }
        TransactionType type = TRANSACTION_TYPES.get(code.toUpperCase(Locale.ROOT));
        if (type == null) {
            throw new StatementParseException("row " + rowNumber + ": '" + code
                    + "' is not a transaction type this adapter knows; known types are "
                    + TRANSACTION_TYPES.keySet().stream().sorted().toList());
        }
        return type;
    }

    private Map<String, Integer> locateColumns(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            columns.put(header.get(index).trim(), index);
        }
        List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !columns.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new StatementParseException(
                    "this does not look like a Bank of Scotland export; it is missing the column(s) " + missing);
        }
        return columns;
    }

    /**
     * @return the trimmed cell, or {@code null} when the column is absent or the cell is
     *         empty. Statement exports pad fields with spaces freely.
     */
    private static String value(List<String> record, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= record.size()) {
            return null;
        }
        String trimmed = record.get(index).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Decoded strictly as UTF-8. Replacing an undecodable byte with a question mark
     * would quietly alter a description, and a description that changes is a fingerprint
     * that changes — which for this provider is the whole of a transaction's identity.
     */
    private static String decode(byte[] statement) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return stripByteOrderMark(decoder.decode(ByteBuffer.wrap(statement)).toString());
        } catch (CharacterCodingException e) {
            throw new StatementParseException("the uploaded file is not valid UTF-8 text", e);
        }
    }

    private static String stripByteOrderMark(String content) {
        return content.startsWith("﻿") ? content.substring(1) : content;
    }
}
