package com.fapp.api;

import com.fapp.pinned.PinnedGroupTransaction;
import com.fapp.transaction.Category;
import com.fapp.transaction.Transaction;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A transaction as it appears inside a pinned group: enough to display it without a
 * second lookup, and nothing that would let a caller mistake this for the transaction's
 * own record. The fields it carries are read-only copies of facts the transaction
 * already holds; pinning changes none of them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PinnedTransactionResponse(
        UUID transactionId,
        UUID accountId,
        LocalDate bookingDate,
        BigDecimal amount,
        String currency,
        String description,
        String merchant,
        Category category,
        String customCategory,
        String note,
        Instant pinnedAt) {

    public static PinnedTransactionResponse of(PinnedGroupTransaction membership) {
        Transaction transaction = membership.transaction();
        return new PinnedTransactionResponse(
                transaction.id(),
                transaction.accountId(),
                transaction.bookingDate(),
                transaction.amount().amount(),
                transaction.amount().currency().getCurrencyCode(),
                transaction.description(),
                transaction.merchant().orElse(null),
                transaction.category(),
                transaction.customCategory().orElse(null),
                membership.note().orElse(null),
                membership.pinnedAt());
    }
}
