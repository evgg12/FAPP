package com.fapp.pinned;

import com.fapp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A named group a user keeps to track selected transactions, with a free-form note --
 * for example, money a family member owes them.
 *
 * <p>Purely organisational. Nothing here changes a transaction's category, amount or
 * analytics behaviour; a group is a label a user attaches on top of their own history,
 * not a second ledger.
 */
@Entity
@Table(name = "pinned_groups")
public class PinnedGroup {

    private static final int NAME_MAX = 120;
    private static final int NOTES_MAX = 2000;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /* Read from the foreign key column the association writes; see Account.userId. */
    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "notes", length = NOTES_MAX)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PinnedGroup() {
        // for Hibernate
    }

    private PinnedGroup(User user, String name, String notes) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.userId = user.id();
        this.name = requireName(name);
        this.notes = optionalNotes(notes);
    }

    public static PinnedGroup of(User user, String name, String notes) {
        Objects.requireNonNull(user, "user must not be null");
        return new PinnedGroup(user, name, notes);
    }

    /** Renames the group and replaces its notes. */
    public void update(String name, String notes) {
        this.name = requireName(name);
        this.notes = optionalNotes(notes);
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("a pinned group must be named");
        }
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("a pinned group name must be at most " + NAME_MAX + " characters");
        }
        return trimmed;
    }

    private static String optionalNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > NOTES_MAX) {
            throw new IllegalArgumentException("notes must be at most " + NOTES_MAX + " characters");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public Optional<String> notes() {
        return Optional.ofNullable(notes);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
