package com.fapp.analytics;

import java.math.BigDecimal;

/**
 * What the user's savings pot did over a period.
 *
 * <p>{@code balance} is {@code paidIn - withdrawn}: how much of the period's money ended
 * up set aside. It is a movement over the period, not a bank balance — FAPP holds
 * statements, not balances, so a figure that claimed to be the pot's current total would
 * be wrong the moment a statement was missing.
 */
public record SavingsPot(
        AnalyticsPeriod period,
        BigDecimal paidIn,
        BigDecimal withdrawn,
        BigDecimal balance,
        long transactionCount) {

    static SavingsPot of(AnalyticsPeriod period,
                         BigDecimal paidIn,
                         BigDecimal withdrawn,
                         long transactionCount) {
        return new SavingsPot(period, paidIn, withdrawn, paidIn.subtract(withdrawn), transactionCount);
    }
}
