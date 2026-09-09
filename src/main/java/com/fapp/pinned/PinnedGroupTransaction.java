package com.fapp.pinned;

import com.fapp.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One transaction pinned either into one {@link PinnedGroup}, or individually with no
 * group at all -- {@code group} is null exactly when this is an individual pin.
 *
 * <p>A membership row, nothing more: it records that a transaction is pinned, optionally
 * with a short note, and removing it removes only that fact. It never touches the
 * transaction's category, amount, merchant or any other field, and analytics never joins
 * against this table.
 */
@Entity
@Table(name = "pinned_group_transactions")
public class PinnedGroupTransaction {

    private static final int NOTE_MAX = 500;

    @Id
    private UUID id;

    /** Null for an individual pin; present for a pin that belongs to a group. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "group_id", nullable = true, updatable = false)
    private PinnedGroup group;

    @Column(name = "group_id", insertable = false, updatable = false)
    private UUID groupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @Column(name = "transaction_id", insertable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Set only on an individual pin; a group's own notes live on {@link PinnedGroup}. */
    @Column(name = "note", length = NOTE_MAX)
    private String note;

    @Column(name = "pinned_at", nullable = false, updatable = false)
    private Instant pinnedAt;

    protected PinnedGroupTransaction() {
        // for Hibernate
    }

    private PinnedGroupTransaction(PinnedGroup group, Transaction transaction, UUID userId, String note) {
        this.id = UUID.randomUUID();
        this.group = group;
        this.groupId = group == null ? null : group.id();
        this.transaction = transaction;
        this.transactionId = transaction.id();
        this.userId = userId;
        this.note = optionalNote(note);
    }

    public static PinnedGroupTransaction of(PinnedGroup group, Transaction transaction) {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(transaction, "transaction must not be null");
        if (!transaction.userId().equals(group.userId())) {
            throw new IllegalArgumentException("a transaction may only be pinned into its owner's own group");
        }
        return new PinnedGroupTransaction(group, transaction, group.userId(), null);
    }

    /** Pins a transaction on its own, with no group, so it can carry its own short note. */
    public static PinnedGroupTransaction individual(Transaction transaction, String note) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        return new PinnedGroupTransaction(null, transaction, transaction.userId(), note);
    }

    private static String optionalNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > NOTE_MAX) {
            throw new IllegalArgumentException("a pin note must be at most " + NOTE_MAX + " characters");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public UUID groupId() {
        return groupId;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public Transaction transaction() {
        return transaction;
    }

    public UUID userId() {
        return userId;
    }

    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    /** True when this pin belongs to no group -- the transaction was pinned on its own. */
    public boolean isIndividual() {
        return groupId == null;
    }

    public Instant pinnedAt() {
        return pinnedAt;
    }

    @PrePersist
    void onPersist() {
        this.pinnedAt = Instant.now();
    }
}
