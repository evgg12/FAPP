package com.fapp.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The owner of all financial data and the boundary every query is scoped to.
 *
 * <p>Intentionally minimal. Authentication mechanics — credentials, tokens, roles,
 * verification — are a separate concern and are not modelled here; this entity exists
 * so that accounts and transactions have an owner.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /**
     * The encoded password, never the password. Null for a user who has no credential and
     * therefore cannot sign in.
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // for Hibernate
    }

    private User(String email, String displayName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.displayName = displayName;
    }

    public static User of(String email, String displayName) {
        String normalisedEmail = requireText(email, "email").toLowerCase();
        if (normalisedEmail.indexOf('@') < 1) {
            throw new IllegalArgumentException("email must contain a local part and a domain: " + email);
        }
        return new User(normalisedEmail, requireText(displayName, "displayName"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    /**
     * Sets the credential this user signs in with.
     *
     * @param passwordHash output of a password encoder. Passing a plaintext password here
     *                     would store a plaintext password, so callers hash first and this
     *                     accepts nothing that looks like it was forgotten.
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "passwordHash");
    }

    /** The encoded password, or empty for a user who cannot sign in. */
    public java.util.Optional<String> passwordHash() {
        return java.util.Optional.ofNullable(passwordHash);
    }

    public void rename(String displayName) {
        this.displayName = requireText(displayName, "displayName");
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
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
