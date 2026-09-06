package com.fapp.account;

import com.fapp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One financial account at one provider, belonging to one user.
 *
 * <p>The provider is a stable slug — {@code bank_of_scotland}, {@code monzo} — declared
 * by the adapter that reads that bank's statements. It is only ever data: nothing in
 * the transaction model, persistence or analytics branches on it, and registering a new
 * bank needs an adapter rather than a schema change. A user holding accounts at several
 * banks is simply several rows here, as are two accounts at the same bank.
 *
 * <p>Owner and currency are fixed for the account's life. The database enforces this:
 * transactions carry both columns in a composite foreign key with {@code ON UPDATE
 * RESTRICT}, so neither can change while the account holds any transaction.
 */
@Entity
@Table(name = "accounts")
public class Account {

    private static final Pattern PROVIDER_SLUG = Pattern.compile("^[a-z][a-z0-9_]*$");

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /*
     * The owner's id, read straight from the foreign key column the association already
     * writes. Without it, userId() would have to call id() on a lazy proxy -- and because
     * id() is not a JavaBean getter, Hibernate cannot recognise it as the identifier
     * accessor and initialises the proxy instead, which fails outside a session.
     */
    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 40, updatable = false)
    private String provider;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // for Hibernate
    }

    private Account(User user, String provider, String displayName, AccountType accountType, Currency currency) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.userId = user.id();
        this.provider = provider;
        this.displayName = displayName;
        this.accountType = accountType;
        this.currency = currency;
    }

    public static Account of(User user,
                             String provider,
                             String displayName,
                             AccountType accountType,
                             Currency currency) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(accountType, "accountType must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        return new Account(user, requireProviderSlug(provider), requireText(displayName), accountType, currency);
    }

    private static String requireProviderSlug(String provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        if (!PROVIDER_SLUG.matcher(provider).matches()) {
            throw new IllegalArgumentException(
                    "provider must be a lowercase slug such as 'bank_of_scotland', was: " + provider);
        }
        return provider;
    }

    private static String requireText(String displayName) {
        Objects.requireNonNull(displayName, "displayName must not be null");
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return trimmed;
    }

    public void rename(String displayName) {
        this.displayName = requireText(displayName);
    }

    public UUID id() {
        return id;
    }

    public User user() {
        return user;
    }

    /** The owner's id. Safe to read on a detached account: it is a column, not a hop. */
    public UUID userId() {
        return userId;
    }

    public String provider() {
        return provider;
    }

    public String displayName() {
        return displayName;
    }

    public AccountType accountType() {
        return accountType;
    }

    public Currency currency() {
        return currency;
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
