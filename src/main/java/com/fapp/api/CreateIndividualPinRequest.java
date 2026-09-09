package com.fapp.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** @param note free-form, optional; nothing here is interpreted */
public record CreateIndividualPinRequest(@NotNull UUID transactionId, @Size(max = 500) String note) {
}
