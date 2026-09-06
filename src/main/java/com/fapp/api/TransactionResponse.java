package com.fapp.api;

import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A transaction as the API describes it.
 *
 * <p>{@code amount} is signed in FAPP's convention — negative left the account, positive
 * arrived — and is reported at the scale it is stored at rather than rounded for
 * display, because rounding a figure a client may total is the client's decision to
 * make, not this layer's. {@code originalAmount} appears only for a foreign-currency
 * movement.
 *
 * <p>{@code fingerprint} and {@code occurrence} are deliberately absent: they exist to
 * recognise a transaction already held and mean nothing outside the import pipeline.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        UUID id,
        LocalDate bookingDate,
        LocalDate occurredOn,
        BigDecimal amount,
        String currency,
        BigDecimal originalAmount,
        String originalCurrency,
        String description,
        String merchant,
        Category category,
        CategorySource categorySource,
        TransactionType transactionType,
        String externalId) {

    public static TransactionResponse of(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.bookingDate(),
                transaction.occurredOn().orElse(null),
                transaction.amount().amount(),
                transaction.amount().currency().getCurrencyCode(),
                transaction.originalAmount().map(money -> money.amount()).orElse(null),
                transaction.originalAmount().map(money -> money.currency().getCurrencyCode()).orElse(null),
                transaction.description(),
                transaction.merchant().orElse(null),
                transaction.category(),
                transaction.categorySource(),
                transaction.transactionType(),
                transaction.externalId().orElse(null));
    }
}
