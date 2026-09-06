package com.fapp.statement;

import com.fapp.money.Money;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.TransactionType;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One statement row after a provider adapter has read it, and before FAPP has
 * normalised, validated against an account, deduplicated or stored anything.
 *
 * <p>This is the seam that keeps the platform provider-independent. Everything
 * bank-specific — column order, date and decimal formats, character encoding, the
 * bank's own type codes and category vocabulary, which sign a debit is written with —
 * is resolved by the adapter that produced this. Every step after it works only with the values here, so
 * adding a bank means adding an adapter and nothing else.
 *
 * <p>Deliberately in-memory only: no JPA annotations, never persisted, and no
 * reference to the raw statement text it came from. Storing raw statement rows is
 * exactly the personal data FAPP is careful not to keep.
 *
 * <p>Several components are nullable because provider statements legitimately omit
 * them: not every bank supplies a transaction id, an identifiable merchant, a
 * separate date for when the movement actually happened, or a foreign-currency leg.
 * Text components are trimmed on construction, and a blank one is rejected rather
 * than quietly stored — absent data must be {@code null}, not empty.
 *
 * @param bookingDate     the date the bank applied the movement; becomes the canonical
 *                        analytical date. Required.
 * @param occurredOn      when the movement actually happened, if the statement says.
 *                        Informational, never used in arithmetic. Nullable.
 * @param amount          the signed amount in the statement's currency, already in
 *                        FAPP's convention: negative left the user's position,
 *                        positive entered it. Required, never zero.
 * @param originalAmount  the foreign-currency amount behind an FX movement. Must be a
 *                        different currency from {@code amount} and point the same
 *                        way. Nullable.
 * @param description     the statement's description of the movement. Required.
 * @param merchant        the merchant, where the adapter can identify one. Nullable.
 * @param externalId      the bank's own transaction id, where the bank provides one.
 *                        Authoritative for deduplication when present. Nullable.
 * @param transactionType the coarse nature of the movement, already mapped out of the
 *                        bank's own code list by the adapter. Required.
 * @param category        the FAPP category, mapped out of the provider's own category
 *                        vocabulary by the adapter. {@link Category#UNCATEGORISED}
 *                        where the statement says nothing usable. Required.
 * @param categorySource  how {@code category} was decided. An adapter that read it
 *                        from the statement reports {@link CategorySource#ADAPTER};
 *                        one whose format carries no categories reports
 *                        {@link CategorySource#DEFAULT}. Required.
 */
public record RawTransaction(
        LocalDate bookingDate,
        LocalDate occurredOn,
        Money amount,
        Money originalAmount,
        String description,
        String merchant,
        String externalId,
        TransactionType transactionType,
        Category category,
        CategorySource categorySource) {

    public RawTransaction {
        Objects.requireNonNull(bookingDate, "bookingDate must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(transactionType, "transactionType must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(categorySource, "categorySource must not be null");

        if (amount.isZero()) {
            throw new IllegalArgumentException("a statement row of zero is malformed input, not a movement");
        }
        if (originalAmount != null) {
            if (originalAmount.currency().equals(amount.currency())) {
                throw new IllegalArgumentException(
                        "originalAmount is only recorded when it is genuinely a different currency");
            }
            if (originalAmount.isNegative() != amount.isNegative()) {
                throw new IllegalArgumentException(
                        "originalAmount must point the same way as amount: " + originalAmount + " vs " + amount);
            }
        }

        description = requireText(description, "description");
        merchant = optionalText(merchant, "merchant");
        externalId = optionalText(externalId, "externalId");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static String optionalText(String value, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must be null rather than blank when absent");
        }
        return trimmed;
    }
}
