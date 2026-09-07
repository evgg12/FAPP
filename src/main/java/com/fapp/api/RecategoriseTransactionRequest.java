package com.fapp.api;

import com.fapp.transaction.Category;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A category the user chose for one transaction.
 *
 * <p>{@code customCategory} is required when, and only meaningful when, the category is
 * {@link Category#CUSTOM}. The controller checks that pairing so the caller gets a clear
 * message instead of a constraint violation from the database.
 */
record RecategoriseTransactionRequest(
        @NotNull Category category,
        @Size(max = 40) String customCategory) {
}
