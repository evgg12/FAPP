package com.fapp.pinned;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PinnedGroupTransactionRepository extends JpaRepository<PinnedGroupTransaction, UUID> {

    /**
     * Fetches the transaction eagerly rather than leaving it lazy: the response
     * mapping reads its fields after this method's own transaction has closed, so a
     * lazy proxy would fail to initialise.
     */
    @Query("select pgt from PinnedGroupTransaction pgt join fetch pgt.transaction"
            + " where pgt.groupId = :groupId order by pgt.pinnedAt asc")
    List<PinnedGroupTransaction> findByGroup_IdOrderByPinnedAtAsc(@Param("groupId") UUID groupId);

    boolean existsByGroup_IdAndTransaction_Id(UUID groupId, UUID transactionId);

    long deleteByGroup_IdAndTransaction_Id(UUID groupId, UUID transactionId);

    /** A user's transactions pinned on their own, most recently pinned first. */
    @Query("select pgt from PinnedGroupTransaction pgt join fetch pgt.transaction"
            + " where pgt.userId = :userId and pgt.group is null order by pgt.pinnedAt desc")
    List<PinnedGroupTransaction> findIndividualByUserId(@Param("userId") UUID userId);

    @Query("select pgt from PinnedGroupTransaction pgt join fetch pgt.transaction"
            + " where pgt.userId = :userId and pgt.transaction.id = :transactionId and pgt.group is null")
    Optional<PinnedGroupTransaction> findIndividualByUserIdAndTransactionId(
            @Param("userId") UUID userId, @Param("transactionId") UUID transactionId);

    @Modifying
    @Query("delete from PinnedGroupTransaction pgt"
            + " where pgt.userId = :userId and pgt.transaction.id = :transactionId and pgt.group is null")
    void deleteIndividualByUserIdAndTransactionId(
            @Param("userId") UUID userId, @Param("transactionId") UUID transactionId);

    /** Every transaction id this user has pinned, individually or into any group. */
    @Query("select distinct pgt.transactionId from PinnedGroupTransaction pgt where pgt.userId = :userId")
    List<UUID> findPinnedTransactionIdsByUserId(@Param("userId") UUID userId);
}
