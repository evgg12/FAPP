package com.fapp.analytics;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One account's income, expenditure and net position over a period.
 *
 * <p>Every account the user holds is reported, including any with no activity in the
 * period, which come back as zeros. The provider slug is included so a caller can group
 * by bank without a second request; no bank identifiers exist to expose.
 */
public record AccountSummary(
        UUID accountId,
        String provider,
        String accountName,
        BigDecimal income,
        BigDecimal expenditure,
        BigDecimal netSavings,
        long transactionCount) {

    static AccountSummary of(UUID accountId,
                             String provider,
                             String accountName,
                             BigDecimal income,
                             BigDecimal expenditure,
                             long transactionCount) {
        return new AccountSummary(accountId, provider, accountName, income, expenditure,
                income.subtract(expenditure), transactionCount);
    }
}
