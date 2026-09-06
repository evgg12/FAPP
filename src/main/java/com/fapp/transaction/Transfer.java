package com.fapp.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A link proving that two transactions are the two sides of one move between accounts
 * the same user owns.
 *
 * <p>A transfer appears as a separate line in each bank's statement, so it is stored as
 * two faithful transactions rather than collapsed into one row. That keeps import
 * atomic and provider-blind: neither adapter has to know the other exists.
 *
 * <p>The link matters arithmetically. Without it a 500.00 move from a current account
 * into a savings account counts as 500.00 of expenditure <em>and</em> 500.00 of income,
 * inflating both totals and corrupting net savings — the figure savings-goal
 * projections are built on. Analytics excludes linked legs from income and expenditure
 * and reports them as internal movement instead.
 *
 * <p>Note that a {@link TransactionType#BANK_TRANSFER} on its own proves nothing: 50.00
 * sent to a friend is genuinely expenditure. Only a link recorded here shows the money
 * stayed with the user.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    /**
     * Carried into both foreign keys, which is how the database refuses a transfer
     * that mixes two different users' transactions.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outgoing_transaction_id", nullable = false, unique = true, updatable = false)
    private Transaction outgoing;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incoming_transaction_id", nullable = false, unique = true, updatable = false)
    private Transaction incoming;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_source", nullable = false, length = 20)
    private TransferDetectionSource detectionSource;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected Transfer() {
        // for Hibernate
    }

    private Transfer(Transaction outgoing, Transaction incoming, TransferDetectionSource detectionSource) {
        this.id = UUID.randomUUID();
        this.userId = outgoing.userId();
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.detectionSource = detectionSource;
    }

    /**
     * Links two legs into an internal transfer.
     *
     * <p>Enforces the three invariants PostgreSQL cannot express, because all three
     * compare two rows: the legs must sit in different accounts, and they must carry
     * opposite signs with equal magnitude in the same currency. Same-user ownership is
     * checked here too, for a clear message, but the database guarantees it regardless.
     *
     * <p>Cross-currency internal transfers are deliberately out of scope: verifying one
     * needs an exchange rate, and neither initial bank makes them common.
     */
    public static Transfer of(Transaction outgoing,
                              Transaction incoming,
                              TransferDetectionSource detectionSource) {
        Objects.requireNonNull(outgoing, "outgoing must not be null");
        Objects.requireNonNull(incoming, "incoming must not be null");
        Objects.requireNonNull(detectionSource, "detectionSource must not be null");

        if (outgoing == incoming) {
            throw new IllegalArgumentException("a transaction cannot be both legs of a transfer");
        }
        if (!outgoing.userId().equals(incoming.userId())) {
            throw new IllegalArgumentException("both legs of a transfer must belong to the same user");
        }
        if (outgoing.accountId().equals(incoming.accountId())) {
            throw new IllegalArgumentException("a transfer must move money between two different accounts");
        }
        if (!outgoing.amount().isNegative()) {
            throw new IllegalArgumentException("the outgoing leg must be negative, was " + outgoing.amount());
        }
        if (!incoming.amount().isPositive()) {
            throw new IllegalArgumentException("the incoming leg must be positive, was " + incoming.amount());
        }
        // One comparison covers equal magnitude, opposite sign and matching currency.
        if (!outgoing.amount().negated().equals(incoming.amount())) {
            throw new IllegalArgumentException(
                    "transfer legs must be equal and opposite in the same currency, were "
                            + outgoing.amount() + " and " + incoming.amount());
        }
        return new Transfer(outgoing, incoming, detectionSource);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Transaction outgoing() {
        return outgoing;
    }

    public Transaction incoming() {
        return incoming;
    }

    public TransferDetectionSource detectionSource() {
        return detectionSource;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    @PrePersist
    void onPersist() {
        this.detectedAt = Instant.now();
    }
}
