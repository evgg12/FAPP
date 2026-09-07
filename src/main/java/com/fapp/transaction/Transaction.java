package com.fapp.transaction;

import com.fapp.account.Account;
import com.fapp.money.Money;
import com.fapp.statement.StatementImport;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One money movement on one account, in the single form the whole platform works with.
 *
 * <p>Nothing here records which bank the record came from. The provider lives on the
 * {@link Account}, so adding a bank means adding an adapter that produces these, and
 * persistence and analytics never learn that a new one exists.
 *
 * <p><strong>Immutability.</strong> A transaction is a historical fact. Once imported,
 * its money, dates, provenance and identity cannot change — only its category and
 * merchant, which are interpretations rather than facts. The mapping enforces this with
 * {@code updatable = false} and the entity exposes no way to alter the rest.
 *
 * <p><strong>Dates.</strong> {@code bookingDate} is the sole canonical date for every
 * calculation. It is a {@link LocalDate} rather than an instant on purpose: Bank of
 * Scotland statements carry no time, and inventing one would let a timezone conversion
 * quietly move a transaction into a different month. {@code occurredOn} is
 * informational and never used in arithmetic.
 *
 * <p><strong>Identity.</strong> {@code externalId} is the bank's own transaction id
 * where one exists — Monzo supplies one, Bank of Scotland does not — and is
 * authoritative when present. Otherwise identity rests on {@code fingerprint}, a hash
 * of the canonical content, with {@code occurrence} distinguishing genuinely identical
 * same-day transactions from a re-imported duplicate. {@code fingerprintVersion}
 * records which normalisation produced the hash, so changing that normalisation is
 * detectable rather than silently breaking deduplication.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    /**
     * Version of the description normalisation and hashing that produces
     * {@code fingerprint}. Increment whenever that logic changes.
     */
    public static final short CURRENT_FINGERPRINT_VERSION = 1;

    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final int DESCRIPTION_MAX = 500;
    private static final int CUSTOM_CATEGORY_MAX = 40;

    private static final int MERCHANT_MAX = 200;
    private static final int EXTERNAL_ID_MAX = 120;

    /*
     * Assigned at construction rather than generated on persist. The import pipeline
     * has to build a whole graph -- an import plus its transactions plus any transfer
     * links -- and flush it in one transaction, and the denormalised user_id below is
     * copied from the account, so ids must exist before anything is persisted.
     */
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    /**
     * Denormalised from the account so every analytical query can scope by user without
     * a join. Kept truthful by the composite foreign key, not by discipline: the
     * database rejects a transaction whose user does not own its account.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /*
     * Read straight from the foreign key column the association writes, for the same
     * reason userId is denormalised: accountId() must not have to initialise a lazy
     * proxy, which fails once the request's session has closed.
     */
    @Column(name = "account_id", insertable = false, updatable = false)
    private UUID accountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_import_id", nullable = false, updatable = false)
    private StatementImport statementImport;

    @Column(name = "booking_date", nullable = false, updatable = false)
    private LocalDate bookingDate;

    @Column(name = "occurred_on", updatable = false)
    private LocalDate occurredOn;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "amount", nullable = false, precision = 19, scale = Money.SCALE,
                            updatable = false)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "currency", nullable = false, length = 3, updatable = false))
    })
    private Money amount;

    /*
     * The foreign-currency leg is mapped as two plain columns rather than a second
     * embedded Money because it is optional, and an embeddable whose columns are all
     * null does not reliably read back as null. Optional<Money> is exposed instead, so
     * the domain still never sees a bare BigDecimal.
     */
    @Column(name = "original_amount", precision = 19, scale = Money.SCALE, updatable = false)
    private BigDecimal originalAmount;

    @Column(name = "original_currency", length = 3, updatable = false)
    private Currency originalCurrency;

    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX, updatable = false)
    private String description;

    @Column(name = "merchant", length = MERCHANT_MAX)
    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Category category;

    /** Set only when the category is {@link Category#CUSTOM}; the database enforces it. */
    @Column(name = "custom_category", length = CUSTOM_CATEGORY_MAX)
    private String customCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", nullable = false, length = 20)
    private CategorySource categorySource;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 25, updatable = false)
    private TransactionType transactionType;

    @Column(name = "external_id", length = EXTERNAL_ID_MAX, updatable = false)
    private String externalId;

    @Column(name = "fingerprint", nullable = false, length = 64, updatable = false)
    private String fingerprint;

    @Column(name = "fingerprint_version", nullable = false, updatable = false)
    private short fingerprintVersion;

    @Column(name = "occurrence", nullable = false, updatable = false)
    private short occurrence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
        // for Hibernate
    }

    private Transaction(Builder builder) {
        this.id = UUID.randomUUID();
        this.account = builder.account;
        this.accountId = builder.account.id();
        this.userId = builder.account.userId();
        this.statementImport = builder.statementImport;
        this.bookingDate = builder.bookingDate;
        this.occurredOn = builder.occurredOn;
        this.amount = builder.amount;
        this.originalAmount = builder.originalAmount == null ? null : builder.originalAmount.amount();
        this.originalCurrency = builder.originalAmount == null ? null : builder.originalAmount.currency();
        this.description = builder.description;
        this.merchant = builder.merchant;
        this.category = builder.category;
        this.categorySource = builder.categorySource;
        this.transactionType = builder.transactionType;
        this.externalId = builder.externalId;
        this.fingerprint = builder.fingerprint;
        this.fingerprintVersion = builder.fingerprintVersion;
        this.occurrence = builder.occurrence;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Reinterprets what this transaction was for. The only mutation a transaction
     * allows, alongside {@link #assignMerchant(String)}: everything else is a fact
     * reported by a bank.
     */
    public void recategorise(Category category, CategorySource categorySource) {
        Objects.requireNonNull(category, "category must not be null");
        if (category == Category.CUSTOM) {
            throw new IllegalArgumentException("CUSTOM needs a label; use recategoriseAs");
        }
        this.category = category;
        this.customCategory = null;
        this.categorySource = Objects.requireNonNull(categorySource, "categorySource must not be null");
    }

    /**
     * Files this transaction under a name the user chose. The label is theirs, so it is
     * trimmed and length-checked but not interpreted.
     */
    public void recategoriseAs(String customCategory, CategorySource categorySource) {
        String label = optionalText(customCategory, "customCategory", CUSTOM_CATEGORY_MAX);
        if (label == null) {
            throw new IllegalArgumentException("customCategory must not be blank");
        }
        this.category = Category.CUSTOM;
        this.customCategory = label;
        this.categorySource = Objects.requireNonNull(categorySource, "categorySource must not be null");
    }

    public void assignMerchant(String merchant) {
        this.merchant = optionalText(merchant, "merchant", MERCHANT_MAX);
    }

    public UUID id() {
        return id;
    }

    public Account account() {
        return account;
    }

    /** The owning account's id. Safe on a detached transaction: it is a column. */
    public UUID accountId() {
        return accountId;
    }

    public UUID userId() {
        return userId;
    }

    public StatementImport statementImport() {
        return statementImport;
    }

    /** The one date every calculation uses. */
    public LocalDate bookingDate() {
        return bookingDate;
    }

    /** When the movement actually happened, if the bank said. Informational only. */
    public Optional<LocalDate> occurredOn() {
        return Optional.ofNullable(occurredOn);
    }

    /** The amount that moved on the account, in the account's currency. */
    public Money amount() {
        return amount;
    }

    /** The foreign-currency amount behind this movement, for an FX transaction. */
    public Optional<Money> originalAmount() {
        return originalAmount == null ? Optional.empty() : Optional.of(Money.of(originalAmount, originalCurrency));
    }

    public String description() {
        return description;
    }

    public Optional<String> merchant() {
        return Optional.ofNullable(merchant);
    }

    public Category category() {
        return category;
    }

    /** The user's own label, present only when the category is {@link Category#CUSTOM}. */
    public java.util.Optional<String> customCategory() {
        return java.util.Optional.ofNullable(customCategory);
    }

    public CategorySource categorySource() {
        return categorySource;
    }

    public TransactionType transactionType() {
        return transactionType;
    }

    /** The bank's own transaction id, where the bank provides one. */
    public Optional<String> externalId() {
        return Optional.ofNullable(externalId);
    }

    public String fingerprint() {
        return fingerprint;
    }

    public short fingerprintVersion() {
        return fingerprintVersion;
    }

    public short occurrence() {
        return occurrence;
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

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must be null rather than blank");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be at most " + maxLength + " characters, was " + trimmed.length());
        }
        return trimmed;
    }

    /**
     * Builds a transaction, rejecting anything a bank adapter should never produce.
     * These checks mirror the database constraints deliberately, so malformed data
     * fails at the boundary with a readable message instead of as a constraint
     * violation halfway through an import.
     */
    public static final class Builder {

        private Account account;
        private StatementImport statementImport;
        private LocalDate bookingDate;
        private LocalDate occurredOn;
        private Money amount;
        private Money originalAmount;
        private String description;
        private String merchant;
        private Category category = Category.UNCATEGORISED;
        private CategorySource categorySource = CategorySource.DEFAULT;
        private TransactionType transactionType;
        private String externalId;
        private String fingerprint;
        private short fingerprintVersion = CURRENT_FINGERPRINT_VERSION;
        private short occurrence = 1;

        private Builder() {
        }

        public Builder account(Account account) {
            this.account = account;
            return this;
        }

        public Builder statementImport(StatementImport statementImport) {
            this.statementImport = statementImport;
            return this;
        }

        public Builder bookingDate(LocalDate bookingDate) {
            this.bookingDate = bookingDate;
            return this;
        }

        public Builder occurredOn(LocalDate occurredOn) {
            this.occurredOn = occurredOn;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder originalAmount(Money originalAmount) {
            this.originalAmount = originalAmount;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder merchant(String merchant) {
            this.merchant = merchant;
            return this;
        }

        public Builder category(Category category, CategorySource categorySource) {
            this.category = category;
            this.categorySource = categorySource;
            return this;
        }

        public Builder transactionType(TransactionType transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public Builder externalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public Builder fingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        public Builder fingerprintVersion(short fingerprintVersion) {
            this.fingerprintVersion = fingerprintVersion;
            return this;
        }

        public Builder occurrence(int occurrence) {
            this.occurrence = (short) occurrence;
            return this;
        }

        public Transaction build() {
            Objects.requireNonNull(account, "account must not be null");
            Objects.requireNonNull(statementImport, "statementImport must not be null");
            Objects.requireNonNull(bookingDate, "bookingDate must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
            Objects.requireNonNull(transactionType, "transactionType must not be null");
            Objects.requireNonNull(category, "category must not be null");
            Objects.requireNonNull(categorySource, "categorySource must not be null");

            if (!statementImport.account().id().equals(account.id())) {
                throw new IllegalArgumentException(
                        "statementImport belongs to a different account than the transaction");
            }
            if (amount.isZero()) {
                throw new IllegalArgumentException("a transaction of zero is malformed input, not a movement");
            }
            if (!amount.currency().equals(account.currency())) {
                throw new IllegalArgumentException(
                        "amount is in " + amount.currency().getCurrencyCode()
                                + " but the account is in " + account.currency().getCurrencyCode()
                                + "; the foreign leg belongs in originalAmount");
            }
            if (originalAmount != null) {
                if (originalAmount.currency().equals(amount.currency())) {
                    throw new IllegalArgumentException(
                            "originalAmount is only recorded when it is genuinely a different currency");
                }
                if (originalAmount.isNegative() != amount.isNegative()) {
                    throw new IllegalArgumentException(
                            "originalAmount must point the same way as amount: " + originalAmount + " vs " + amount);
                }
            }
            this.description = requireDescription(description);
            this.merchant = optionalText(merchant, "merchant", MERCHANT_MAX);
            this.externalId = optionalText(externalId, "externalId", EXTERNAL_ID_MAX);
            requireFingerprint(fingerprint);
            if (fingerprintVersion < 1) {
                throw new IllegalArgumentException("fingerprintVersion must be at least 1, was " + fingerprintVersion);
            }
            if (occurrence < 1) {
                throw new IllegalArgumentException("occurrence must be at least 1, was " + occurrence);
            }
            return new Transaction(this);
        }

        private static String requireDescription(String description) {
            Objects.requireNonNull(description, "description must not be null");
            String trimmed = description.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("description must not be blank");
            }
            if (trimmed.length() > DESCRIPTION_MAX) {
                throw new IllegalArgumentException(
                        "description must be at most " + DESCRIPTION_MAX + " characters, was " + trimmed.length());
            }
            return trimmed;
        }

        private static void requireFingerprint(String fingerprint) {
            Objects.requireNonNull(fingerprint, "fingerprint must not be null");
            if (!SHA_256_HEX.matcher(fingerprint).matches()) {
                throw new IllegalArgumentException("fingerprint must be lowercase hex SHA-256, was: " + fingerprint);
            }
        }
    }
}
