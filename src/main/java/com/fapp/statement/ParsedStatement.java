package com.fapp.statement;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Everything a provider adapter read out of one statement: its data rows, and the
 * period the statement covers.
 *
 * <p>The period is carried rather than inferred later because only the adapter can
 * know it. A statement that declares its own range in a header is more accurate than
 * the span of its rows — a month whose last week had no activity really does cover
 * that week — and the period is what scopes deduplication, so understating it would
 * let a later overlapping statement fail to import a genuinely new transaction.
 * Adapters whose format declares no range use {@link #of(List)}, which derives it.
 *
 * <p>{@code transactions.size()} is the row count a {@link StatementImport} records:
 * every parsed data row is either imported or recognised as a duplicate, so the
 * deduplication step splits this total in two and never loses a row.
 *
 * @param transactions the statement's data rows, in the order the statement listed
 *                     them. Held as an immutable copy. May be empty for a statement
 *                     covering a period with no activity.
 * @param period       the inclusive range the statement covers. Every row's booking
 *                     date must fall inside it.
 */
public record ParsedStatement(List<RawTransaction> transactions, StatementPeriod period) {

    public ParsedStatement {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(period, "period must not be null");
        transactions = List.copyOf(transactions);

        for (RawTransaction transaction : transactions) {
            LocalDate bookingDate = transaction.bookingDate();
            if (bookingDate.isBefore(period.start()) || bookingDate.isAfter(period.end())) {
                throw new IllegalArgumentException(
                        "row booked on " + bookingDate + " falls outside the statement period " + period);
            }
        }
    }

    /**
     * For statement formats that declare no period of their own: derives it from the
     * earliest and latest booking dates present.
     *
     * @throws IllegalArgumentException if there are no rows, because a period cannot
     *                                 be derived from nothing. An adapter for a format
     *                                 that can produce an empty statement must supply
     *                                 the period explicitly.
     */
    public static ParsedStatement of(List<RawTransaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        if (transactions.isEmpty()) {
            throw new IllegalArgumentException(
                    "cannot derive a statement period from an empty statement; supply the period explicitly");
        }
        LocalDate earliest = transactions.stream()
                .map(RawTransaction::bookingDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDate latest = transactions.stream()
                .map(RawTransaction::bookingDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return new ParsedStatement(transactions, StatementPeriod.of(earliest, latest));
    }

    /** The number of data rows parsed, which is what {@link StatementImport} records. */
    public int rowCount() {
        return transactions.size();
    }
}
