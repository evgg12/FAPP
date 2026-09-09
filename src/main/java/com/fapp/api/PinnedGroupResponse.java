package com.fapp.api;

import com.fapp.pinned.PinnedGroup;
import com.fapp.pinned.PinnedGroupTransaction;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A pinned group and the transactions currently pinned into it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PinnedGroupResponse(
        UUID id,
        UUID userId,
        String name,
        String notes,
        List<PinnedTransactionResponse> transactions,
        Instant createdAt,
        Instant updatedAt) {

    public static PinnedGroupResponse of(PinnedGroup group, List<PinnedGroupTransaction> memberships) {
        return new PinnedGroupResponse(
                group.id(),
                group.userId(),
                group.name(),
                group.notes().orElse(null),
                memberships.stream().map(PinnedTransactionResponse::of).toList(),
                group.createdAt(),
                group.updatedAt());
    }
}
