package com.fapp.api;

import com.fapp.pinned.PinnedGroup;
import com.fapp.pinned.PinnedGroupService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's pinned groups: named collections of transactions kept for reference, with
 * optional notes. Organisational only -- see {@link PinnedGroupService} for the
 * ownership guarantees this thin layer relies on.
 */
@RestController
@RequestMapping("/api/users/{userId}/pinned-groups")
class PinnedGroupController {

    private final PinnedGroupService groups;

    PinnedGroupController(PinnedGroupService groups) {
        this.groups = groups;
    }

    @PostMapping
    ResponseEntity<PinnedGroupResponse> create(@PathVariable UUID userId,
                                               @Valid @RequestBody CreatePinnedGroupRequest request) {
        PinnedGroup created = groups.create(userId, request.name(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(PinnedGroupResponse.of(created, List.of()));
    }

    @GetMapping
    List<PinnedGroupResponse> list(@PathVariable UUID userId) {
        return groups.findAll(userId).stream()
                .map(group -> PinnedGroupResponse.of(group, groups.transactionsIn(userId, group.id())))
                .toList();
    }

    @GetMapping("/{groupId}")
    PinnedGroupResponse get(@PathVariable UUID userId, @PathVariable UUID groupId) {
        PinnedGroup group = groups.find(userId, groupId);
        return PinnedGroupResponse.of(group, groups.transactionsIn(userId, groupId));
    }

    @PutMapping("/{groupId}")
    PinnedGroupResponse update(@PathVariable UUID userId,
                               @PathVariable UUID groupId,
                               @Valid @RequestBody UpdatePinnedGroupRequest request) {
        PinnedGroup updated = groups.update(userId, groupId, request.name(), request.notes());
        return PinnedGroupResponse.of(updated, groups.transactionsIn(userId, groupId));
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID userId, @PathVariable UUID groupId) {
        groups.delete(userId, groupId);
    }

    @PostMapping("/{groupId}/transactions")
    PinnedGroupResponse addTransactions(@PathVariable UUID userId,
                                        @PathVariable UUID groupId,
                                        @Valid @RequestBody AddPinnedTransactionsRequest request) {
        groups.addTransactions(userId, groupId, request.transactionIds());
        PinnedGroup group = groups.find(userId, groupId);
        return PinnedGroupResponse.of(group, groups.transactionsIn(userId, groupId));
    }

    @DeleteMapping("/{groupId}/transactions/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeTransaction(@PathVariable UUID userId,
                           @PathVariable UUID groupId,
                           @PathVariable UUID transactionId) {
        groups.removeTransaction(userId, groupId, transactionId);
    }
}
