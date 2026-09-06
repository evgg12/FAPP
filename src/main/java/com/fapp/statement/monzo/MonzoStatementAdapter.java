package com.fapp.statement.monzo;

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
import java.nio.CharBuffer;
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
import org.springframework.stereotype.Component;

/**
 * Reads Monzo's CSV statement export.
 *
 * <p>Everything Monzo-specific stops here. The export's column set, its
 * {@code dd/MM/yyyy} dates, its own transaction types and category vocabulary, and its
 * habit of restating the transaction amount in the "local" columns for domestic
 * spending are all resolved into {@link RawTransaction} values that say nothing about
 * which bank produced them.
 *
 * <p>Columns are located by header name rather than position, so Monzo adding or
 * reordering a column does not break the import, while a missing column it depends on
 * fails loudly rather than silently losing data.
 *
 * <p>Some export decisions worth knowing about:
 *
 * <ul>
 *   <li>The signed {@code Amount} column is canonical. {@code Money Out} and
 *       {@code Money In} restate the same figure unsigned and are ignored.
 *   <li>{@code Date} becomes the booking date and {@code Time} is discarded: FAPP's
 *       analytical date is a calendar day, and half of a two-bank aggregate has no
 *       time to offer anyway.
 *   <li>{@code Local amount}/{@code Local currency} only describe a foreign leg when
 *       the local currency actually differs. Monzo repeats the settled figure there
 *       for domestic spending, which is not an FX transaction.
 *   <li>A zero {@code Amount} is a card authorisation hold, not a movement of money.
 *       Those rows are skipped rather than turned into a transaction FAPP would have
 *       to reject.
 *   <li>Rows whose description says a payment is pending are ordinary transactions.
 *       The money has left the account, so it counts.
 * </ul>
 */
@Component
public class MonzoStatementAdapter implements StatementAdapter {

    /** Matches the value stored in {@code accounts.provider} for Monzo accounts. */
    public static final String PROVIDER = "monzo";

    /**
     * Monzo writes dates as day/month/year. Resolved strictly, so an impossible date
     * such as 31/02 is rejected instead of being shifted to a real one.
     */
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.UK).withResolverStyle(ResolverStyle.STRICT);

    private static final String TRANSACTION_ID = "Transaction ID";
    private static final String DATE_COLUMN = "Date";
    private static final String TYPE = "Type";
    private static final String NAME = "Name";
    private static final String CATEGORY = "Category";
    private static final String AMOUNT = "Amount";
    private static final String CURRENCY = "Currency";
    private static final String LOCAL_AMOUNT = "Local amount";
    private static final String LOCAL_CURRENCY = "Local currency";
    private static final String DESCRIPTION = "Description";

    /** The columns this adapter reads. Anything else in the export is ignored. */
    private static final List<String> REQUIRED_COLUMNS = List.of(
            TRANSACTION_ID, DATE_COLUMN, TYPE, NAME, CATEGORY,
            AMOUNT, CURRENCY, LOCAL_AMOUNT, LOCAL_CURRENCY, DESCRIPTION);

    private static final String POT_TRANSFER = "pot transfer";

    /**
     * Monzo's transaction types. Only a card payment has a direct equivalent; the rest
     * are listed rather than left to the default so that a type Monzo adds later is
     * visibly unreviewed instead of looking deliberate.
     */
    private static final Map<String, TransactionType> TRANSACTION_TYPES = Map.of(
            "card payment", TransactionType.CARD_PAYMENT,
            "bacs (direct credit)", TransactionType.OTHER,
            "faster payment", TransactionType.OTHER,
            "bank transfer", TransactionType.OTHER,
            "monzo_paid", TransactionType.OTHER,
            POT_TRANSFER, TransactionType.OTHER);

    /** Monzo's category vocabulary, mapped onto FAPP's. None of Monzo's names survive. */
    private static final Map<String, Category> CATEGORIES = Map.of(
            "groceries", Category.GROCERIES,
            "eating out", Category.RESTAURANTS,
            "transport", Category.TRANSPORT,
            "entertainment", Category.ENTERTAINMENT,
            "bills", Category.BILLS,
            "shopping", Category.SHOPPING,
            "income", Category.INCOME,
            "transfers", Category.TRANSFER,
            "general", Category.UNCATEGORISED);

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
            int rowNumber = index + 1;
            RawTransaction transaction = readRow(records.get(index), columns, rowNumber);
            if (transaction != null) {
                transactions.add(transaction);
            }
        }

        if (transactions.isEmpty()) {
            throw new StatementParseException("the statement contains no transactions to import");
        }
        // Monzo's export carries no statement period of its own, so it is derived from
        // the rows that were actually imported.
        return ParsedStatement.of(transactions);
    }

    /**
     * @return the parsed row, or {@code null} if it is not a movement of money
     */
    private RawTransaction readRow(List<String> record, Map<String, Integer> columns, int rowNumber) {
        Money amount = readAmount(record, columns, rowNumber);
        if (amount.isZero()) {
            // A temporary authorisation hold. Not money moving, so not a transaction.
            return null;
        }

        String monzoType = value(record, columns, TYPE);
        String name = value(record, columns, NAME);

        try {
            return new RawTransaction(
                    readBookingDate(record, columns, rowNumber),
                    null,
                    amount,
                    readForeignLeg(record, columns, amount, rowNumber),
                    describe(value(record, columns, DESCRIPTION), name, monzoType, rowNumber),
                    name,
                    value(record, columns, TRANSACTION_ID),
                    transactionType(monzoType),
                    category(value(record, columns, CATEGORY), monzoType),
                    CategorySource.ADAPTER);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new StatementParseException("row " + rowNumber + ": " + e.getMessage(), e);
        }
    }

    private static LocalDate readBookingDate(List<String> record, Map<String, Integer> columns, int rowNumber) {
        String text = value(record, columns, DATE_COLUMN);
        if (text == null) {
            throw new StatementParseException("row " + rowNumber + ": the Date column is empty");
        }
        try {
            return LocalDate.parse(text, DATE);
        } catch (DateTimeParseException e) {
            throw new StatementParseException(
                    "row " + rowNumber + ": '" + text + "' is not a date in dd/MM/yyyy form", e);
        }
    }

    private static Money readAmount(List<String> record, Map<String, Integer> columns, int rowNumber) {
        return readMoney(record, columns, AMOUNT, CURRENCY, rowNumber);
    }

    /**
     * The foreign-currency amount behind an FX transaction, or {@code null}. Monzo
     * restates the settled amount in these columns for domestic spending, so they only
     * mean something when the currency actually differs.
     */
    private static Money readForeignLeg(List<String> record, Map<String, Integer> columns,
                                        Money amount, int rowNumber) {
        String localAmount = value(record, columns, LOCAL_AMOUNT);
        String localCurrency = value(record, columns, LOCAL_CURRENCY);
        if (localAmount == null || localCurrency == null) {
            return null;
        }
        Money local = readMoney(record, columns, LOCAL_AMOUNT, LOCAL_CURRENCY, rowNumber);
        return local.currency().equals(amount.currency()) ? null : local;
    }

    private static Money readMoney(List<String> record, Map<String, Integer> columns,
                                   String amountColumn, String currencyColumn, int rowNumber) {
        String amountText = value(record, columns, amountColumn);
        String currencyText = value(record, columns, currencyColumn);
        if (amountText == null) {
            throw new StatementParseException("row " + rowNumber + ": the " + amountColumn + " column is empty");
        }
        if (currencyText == null) {
            throw new StatementParseException("row " + rowNumber + ": the " + currencyColumn + " column is empty");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException e) {
            throw new StatementParseException(
                    "row " + rowNumber + ": '" + amountText + "' is not an amount", e);
        }
        Currency currency;
        try {
            currency = Currency.getInstance(currencyText);
        } catch (IllegalArgumentException e) {
            throw new StatementParseException(
                    "row " + rowNumber + ": '" + currencyText + "' is not a currency code", e);
        }
        try {
            return Money.of(amount, currency);
        } catch (IllegalArgumentException e) {
            throw new StatementParseException("row " + rowNumber + ": " + e.getMessage(), e);
        }
    }

    /**
     * Monzo's own description, falling back to the counterparty and then to its
     * transaction type. Nothing is invented: every fallback is a value the statement
     * itself supplied. Address, receipt, emoji and personal notes are deliberately
     * left out — none of them describe the movement, and notes are free text a user
     * would not expect to be kept.
     */
    private static String describe(String description, String name, String monzoType, int rowNumber) {
        if (description != null) {
            return description;
        }
        if (name != null) {
            return name;
        }
        if (monzoType != null) {
            return monzoType;
        }
        throw new StatementParseException(
                "row " + rowNumber + ": the row has no description, counterparty or type to identify it by");
    }

    /** Unrecognised types become {@code OTHER}: coarse, but never wrong. */
    private static TransactionType transactionType(String monzoType) {
        if (monzoType == null) {
            return TransactionType.OTHER;
        }
        return TRANSACTION_TYPES.getOrDefault(monzoType.toLowerCase(Locale.ROOT), TransactionType.OTHER);
    }

    /**
     * A pot transfer is money moving between the user's own accounts whatever Monzo
     * filed it under, so its type decides the category. Otherwise an unmapped Monzo
     * category becomes {@code UNCATEGORISED} rather than a guess.
     */
    private static Category category(String monzoCategory, String monzoType) {
        if (monzoType != null && POT_TRANSFER.equals(monzoType.toLowerCase(Locale.ROOT))) {
            return Category.TRANSFER;
        }
        if (monzoCategory == null) {
            return Category.UNCATEGORISED;
        }
        return CATEGORIES.getOrDefault(monzoCategory.toLowerCase(Locale.ROOT), Category.UNCATEGORISED);
    }

    private Map<String, Integer> locateColumns(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            columns.put(header.get(index).trim(), index);
        }
        List<String> missing = REQUIRED_COLUMNS.stream().filter(column -> !columns.containsKey(column)).toList();
        if (!missing.isEmpty()) {
            throw new StatementParseException(
                    "this does not look like a Monzo export; it is missing the column(s) " + missing);
        }
        return columns;
    }

    /**
     * @return the trimmed cell, or {@code null} when the provider left it out. An empty
     *         cell means absent, never an empty string.
     */
    private static String value(List<String> record, Map<String, Integer> columns, String column) {
        int index = columns.get(column);
        if (index >= record.size()) {
            return null;
        }
        String trimmed = record.get(index).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Monzo exports UTF-8 — the export has an emoji column, so it could not be
     * anything else. Decoded strictly: a byte sequence that is not UTF-8 means a
     * corrupt or wrongly-typed upload, and replacing it with question marks would
     * quietly corrupt a description or a merchant name.
     */
    private static String decode(byte[] statement) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(statement));
            return stripByteOrderMark(decoded.toString());
        } catch (CharacterCodingException e) {
            throw new StatementParseException("the uploaded file is not valid UTF-8 text", e);
        }
    }

    private static String stripByteOrderMark(String content) {
        return content.startsWith("﻿") ? content.substring(1) : content;
    }
}
