package com.fapp.analytics;

import com.fapp.transaction.Category;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One of a period's biggest outgoings, reported as a positive amount.
 *
 * <p>Only money genuinely leaving the user's position appears: incoming movements are
 * not expenses, and a leg of an internal transfer is not either.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LargestExpense(
        UUID transactionId,
        LocalDate bookingDate,
        String description,
        String merchant,
        BigDecimal amount,
        Category category,
        UUID accountId,
        String accountName) {
}
