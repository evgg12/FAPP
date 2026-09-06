package com.fapp.transaction;

import com.fapp.money.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Computes the content fingerprint that identifies a transaction when its bank
 * supplies no transaction id of its own.
 *
 * <p>The single documented function behind
 * {@link Transaction#CURRENT_FINGERPRINT_VERSION}. Every input is already
 * provider-independent, so two banks reporting the same movement the same way produce
 * the same fingerprint, and no bank-specific detail can reach it.
 *
 * <p>The canonical tuple, joined with {@code |} and hashed with SHA-256:
 *
 * <pre>
 *   booking date (ISO-8601) | amount at scale 4, plain string | currency | normalised description
 * </pre>
 *
 * <p>Two details decide whether this works at all. The amount is written at a pinned
 * scale as a plain string, because {@code 1.5} and {@code 1.50} are unequal
 * {@link java.math.BigDecimal}s and scientific notation would appear for some values —
 * either would invent phantom duplicates. And the description is normalised, because
 * banks vary the spacing of otherwise identical text.
 *
 * <p>Normalisation is deliberately conservative: case and whitespace only. Stripping
 * what looks like bank-added noise — trailing card suffixes, embedded dates — would
 * risk collapsing two genuinely different transactions into one, and would be a change
 * of algorithm requiring a new fingerprint version rather than a tweak here.
 */
public final class TransactionFingerprint {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TransactionFingerprint() {
    }

    /**
     * @param bookingDate the date the bank applied the movement
     * @param amount      the signed amount in the account's currency
     * @param description the statement's description, unnormalised
     * @return lowercase hex SHA-256 of the canonical tuple
     */
    public static String of(LocalDate bookingDate, Money amount, String description) {
        Objects.requireNonNull(bookingDate, "bookingDate must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(description, "description must not be null");

        String canonical = String.join("|",
                bookingDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                amount.amount().setScale(Money.SCALE).toPlainString(),
                amount.currency().getCurrencyCode(),
                normaliseDescription(description));
        return sha256Hex(canonical);
    }

    private static String normaliseDescription(String description) {
        return WHITESPACE.matcher(description.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    private static String sha256Hex(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
