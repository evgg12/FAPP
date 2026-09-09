package com.fapp.pinned;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinnedGroupRepository extends JpaRepository<PinnedGroup, UUID> {

    List<PinnedGroup> findByUser_IdOrderByCreatedAtAsc(UUID userId);

    /**
     * Resolved by group <em>and</em> owner in one query, so a group belonging to
     * somebody else is simply not found rather than found and then rejected.
     */
    Optional<PinnedGroup> findByIdAndUser_Id(UUID id, UUID userId);
}
