package com.fapp.transaction;

/**
 * The coarse nature of a money movement, normalised by each bank adapter from its own
 * statement codes.
 *
 * <p>Deliberately kept small and provider-independent. Income and expenditure figures
 * are derived from the signed amount and from {@link Category}, never from this field —
 * it exists to support secondary analysis such as recurring-payment detection, where
 * {@link #DIRECT_DEBIT} and {@link #STANDING_ORDER} are strong signals.
 *
 * <p>Note that {@link #BANK_TRANSFER} does not imply the money stayed with the user.
 * Only a matched {@code transfers} row proves that.
 */
public enum TransactionType {
    CARD_PAYMENT,
    DIRECT_DEBIT,
    STANDING_ORDER,
    BANK_TRANSFER,
    CASH_WITHDRAWAL,
    FEE,
    INTEREST,
    REFUND,
    OTHER
}
