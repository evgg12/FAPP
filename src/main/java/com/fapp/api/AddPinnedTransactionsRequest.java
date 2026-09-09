package com.fapp.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** One or more transactions to pin into a group in a single request. */
public record AddPinnedTransactionsRequest(@NotEmpty List<UUID> transactionIds) {
}
