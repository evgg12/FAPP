package com.fapp.api;

import com.fapp.pinned.PinnedGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Pinned Transactions", description = "Manage individually pinned transactions")
class PinnedTransactionController {

    private final PinnedGroupService groups;

    PinnedTransactionController(PinnedGroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    @Operation(summary = "List individually pinned transactions",
            description = "Returns all transactions pinned individually by the user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of pinned transactions"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials")
    })
    @SecurityRequirement(name = "basicAuth")
    List<PinnedTransactionResponse> list(@PathVariable UUID userId) {
        return groups.individualPins(userId).stream().map(PinnedTransactionResponse::of).toList();
    }

    /** Every transaction id pinned in any way -- individually or into a group -- for the stars. */
    @GetMapping("/ids")
    @Operation(summary = "Get all pinned transaction IDs",
            description = "Returns all transaction IDs pinned in any way, either individually or in a group")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of pinned transaction IDs (array of UUIDs)"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials")
    })
    @SecurityRequirement(name = "basicAuth")
    List<UUID> ids(@PathVariable UUID userId) {
        return groups.pinnedTransactionIds(userId).stream().toList();
    }

    @PostMapping
    @Operation(summary = "Pin a transaction individually",
            description = "Pins a transaction individually with an optional note")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction pinned successfully",
                    content = @Content(schema = @Schema(implementation = PinnedTransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials")
    })
    @SecurityRequirement(name = "basicAuth")
    PinnedTransactionResponse pin(@PathVariable UUID userId, @Valid @RequestBody CreateIndividualPinRequest request) {
        return PinnedTransactionResponse.of(groups.pinIndividually(userId, request.transactionId(), request.note()));
    }

    @DeleteMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unpin a transaction",
            description = "Removes a transaction from individual pins")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Transaction unpinned successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials")
    })
    @SecurityRequirement(name = "basicAuth")
    void unpin(@PathVariable UUID userId, @PathVariable UUID transactionId) {
        groups.unpinIndividually(userId, transactionId);
    }
}
