package com.fapp.transaction;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads the state deduplication needs before an import decides what is new.
 *
 * <p>Both queries are shaped by the uniqueness the database actually enforces —
 * {@code ux_transactions_external_id} on {@code (account_id, external_id)} and
 * {@code uq_transactions_dedup} on {@code (account_id, fingerprint, occurrence)} — and
 * both answer for a whole statement in one round trip rather than per row.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * A user's transactions in one category, used to reapply merchant rules to rows
     * imported before a rule existed. Scoped by user so a backfill can never reach
     * across owners.
     */
    List<Transaction> findByUserIdAndCategory(UUID userId, Category category);

    /**
     * A user's transactions sharing one description, whatever account they sit in.
     *
     * <p>Case-insensitive because the same payee can be written differently by two
     * banks. Used when a category set by hand is applied to every other movement with
     * the same name, which is what saves categorising a recurring payee row by row.
     */
    @Query("select t from Transaction t where t.userId = :userId"
            + " and lower(t.description) = lower(:description)")
    List<Transaction> findByUserIdAndDescription(@Param("userId") UUID userId,
                                                 @Param("description") String description);

    /**
     * An account's transactions, newest first, for reading back what was imported.
     * Ordered by the analytical date and then by insertion so that repeats of the same
     * day come back in a stable order.
     */
    List<Transaction> findByAccount_IdOrderByBookingDateDescCreatedAtDesc(UUID accountId);

    /**
     * Which of a statement's transaction ids this account already holds. The bank's own
     * id is authoritative, so anything returned here is a duplicate whatever else the
     * row says.
     */
    @Query("select t.externalId from Transaction t "
            + "where t.account.id = :accountId and t.externalId in :externalIds")
    List<String> findExistingExternalIds(@Param("accountId") UUID accountId,
                                         @Param("externalIds") Collection<String> externalIds);

    /**
     * How many transactions this account already holds for each of a statement's
     * fingerprints, and the highest occurrence in use.
     *
     * <p>Both numbers are needed and they are not interchangeable. The count says how
     * many of the statement's identical rows are already present, and so how many are
     * duplicates. The maximum says where to continue numbering: reverting an earlier
     * import can leave a gap, and numbering from the count would then collide with a
     * surviving row.
     *
     * <p>Deliberately not filtered by {@code fingerprint_version}, because
     * {@code uq_transactions_dedup} does not include it. Numbering has to avoid every
     * occurrence the constraint can see, whichever version produced it.
     *
     * @return one row per fingerprint: {@code [fingerprint, count, maxOccurrence]}
     */
    @Query("select t.fingerprint, count(t), max(t.occurrence) from Transaction t "
            + "where t.account.id = :accountId and t.fingerprint in :fingerprints "
            + "group by t.fingerprint")
    List<Object[]> tallyFingerprints(@Param("accountId") UUID accountId,
                                     @Param("fingerprints") Collection<String> fingerprints);
}
