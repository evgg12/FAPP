package com.fapp.simulator;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The hypothetical change being tested.
 *
 * <p>None of this is ever stored. A scenario is a question about money that has not
 * moved, and answering it must not leave a trace in the user's transaction history —
 * which is why nothing in this package touches persistence.
 *
 * @param oneOffPurchase           a single hypothetical outgoing, deducted once over the
 *                                 whole horizon. Zero or more; a negative purchase is a
 *                                 windfall and belongs in {@code monthlyIncomeChange}
 *                                 or nowhere.
 * @param monthlyExpenditureChange how much more to spend each month. Signed: negative
 *                                 models spending less.
 * @param monthlyIncomeChange      how much more to receive each month. Signed.
 */
public record ScenarioAdjustment(
        BigDecimal oneOffPurchase,
        BigDecimal monthlyExpenditureChange,
        BigDecimal monthlyIncomeChange) {

    public ScenarioAdjustment {
        oneOffPurchase = orZero(oneOffPurchase);
        monthlyExpenditureChange = orZero(monthlyExpenditureChange);
        monthlyIncomeChange = orZero(monthlyIncomeChange);

        if (oneOffPurchase.signum() < 0) {
            throw new IllegalArgumentException(
                    "a one-off purchase cannot be negative, was " + oneOffPurchase.toPlainString());
        }
    }

    /** Changing nothing, which is a valid question: it is the baseline. */
    public static ScenarioAdjustment none() {
        return new ScenarioAdjustment(null, null, null);
    }

    public boolean changesNothing() {
        return oneOffPurchase.signum() == 0
                && monthlyExpenditureChange.signum() == 0
                && monthlyIncomeChange.signum() == 0;
    }

    private static BigDecimal orZero(BigDecimal amount) {
        return Objects.requireNonNullElse(amount, BigDecimal.ZERO);
    }
}
