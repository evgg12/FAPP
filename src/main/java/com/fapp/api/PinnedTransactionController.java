package com.fapp.api;

import com.fapp.pinned.PinnedGroupService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's transactions pinned on their own, with no group -- the star on a transaction
 * row pins and unpins through here. See {@link PinnedGroupController} for pinning into a
 * named group instead.
 */
@RestController
@RequestMapping("/api/users/{userId}/pinned-transactions")
class PinnedTransactionController {

    private final PinnedGroupService groups;

    PinnedTransactionController(PinnedGroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    List<PinnedTransactionResponse> list(@PathVariable UUID userId) {
        return groups.individualPins(userId).stream().map(PinnedTransactionResponse::of).toList();
    }

    /** Every transaction id pinned in any way -- individually or into a group -- for the stars. */
    @GetMapping("/ids")
    List<UUID> ids(@PathVariable UUID userId) {
        return groups.pinnedTransactionIds(userId).stream().toList();
    }

    @PostMapping
    PinnedTransactionResponse pin(@PathVariable UUID userId, @Valid @RequestBody CreateIndividualPinRequest request) {
        return PinnedTransactionResponse.of(groups.pinIndividually(userId, request.transactionId(), request.note()));
    }

    @DeleteMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unpin(@PathVariable UUID userId, @PathVariable UUID transactionId) {
        groups.unpinIndividually(userId, transactionId);
    }
}
