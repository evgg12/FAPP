package com.fapp.money;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact monetary value: a decimal amount together with the currency it is
 * denominated in. Neither half is meaningful alone, so they travel together and no
 * bare {@link BigDecimal} is passed around the financial domain.
 *
 * <p><strong>Sign convention.</strong> Negative means money left the user's financial
 * position; positive means it entered. This holds for every account type. On a credit
 * card a purchase is negative even though it increases the balance owed — adapters
 * normalise each bank's representation into this convention, so no calculation ever
 * has to ask which bank or account type a figure came from.
 *
 * <p><strong>Scale and rounding.</strong> Amounts are held at scale {@value #SCALE},
 * which covers every ISO 4217 minor unit. Construction never rounds: an amount with
 * more decimal places than that is rejected rather than silently adjusted, because a
 * rounding decision taken during import can never be undone afterwards.
 */
@Embeddable
public class Money implements Serializable {

    /** Stored scale for all monetary amounts. 4 covers every ISO 4217 minor unit. */
    public static final int SCALE = 4;

    @Column(name = "amount", nullable = false, precision = 19, scale = SCALE)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    protected Money() {
        // for Hibernate
    }

    private Money(BigDecimal amount, Currency currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        return new Money(atStoredScale(amount), currency);
    }

    /**
     * @param amount       decimal text, e.g. {@code "-12.34"}
     * @param currencyCode ISO 4217 alphabetic code, e.g. {@code "GBP"}
     */
    public static Money of(String amount, String currencyCode) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        return of(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    private static BigDecimal atStoredScale(BigDecimal amount) {
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "amount " + amount.toPlainString() + " needs more than " + SCALE
                            + " decimal places; FAPP never rounds a monetary value implicitly", e);
        }
    }

    public BigDecimal amount() {
        return amount;
    }

    public Currency currency() {
        return currency;
    }

    /** The same magnitude in the same currency, pointing the other way. */
    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    /**
     * Value equality. Because construction pins the scale, {@code "1.5"} and
     * {@code "1.5000"} in the same currency are equal, as they should be.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Money that)) {
            return false;
        }
        return amount.equals(that.amount) && currency.equals(that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
