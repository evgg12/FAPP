package com.fapp.goal;

import com.fapp.money.Money;
import com.fapp.user.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Something a user is saving towards: an amount, a date, and how far along they are.
 *
 * <p>Progress is a figure the user maintains. FAPP does not guess at it from their
 * accounts — it has no way to know which of someone's savings is earmarked for a car and
 * which is a buffer, and inventing an answer would make the one number they actually care
 * about unreliable. What FAPP does calculate is the projection: whether the trajectory
 * their history implies reaches this target by this date.
 *
 * <p>The target and the current amount share one currency by construction, so they cannot
 * drift apart. The currency is fixed for the goal's life, as an account's is: changing it
 * would silently reinterpret every figure already recorded.
 */
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /* Read from the foreign key column the association writes; see Account.userId. */
    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "target_amount", nullable = false, precision = 19,
                            scale = Money.SCALE)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "currency", nullable = false, length = 3, updatable = false))
    })
    private Money target;

    /*
     * Held as a plain column and exposed as Money built from the target's currency, so
     * there is one currency for the goal rather than two columns that could disagree.
     */
    @Column(name = "current_amount", nullable = false, precision = 19, scale = Money.SCALE)
    private BigDecimal currentAmount;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavingsGoal() {
        // for Hibernate
    }

    private SavingsGoal(User user, String name, Money target, LocalDate targetDate) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.userId = user.id();
        this.name = name;
        this.target = target;
        this.currentAmount = BigDecimal.ZERO.setScale(Money.SCALE);
        this.targetDate = targetDate;
    }

    /**
     * Starts a goal with nothing saved towards it yet.
     *
     * @param target     what to save; must be more than nothing
     * @param targetDate when to have it by; must not already be in the past
     */
    public static SavingsGoal of(User user, String name, Money target, LocalDate targetDate, LocalDate today) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(targetDate, "targetDate must not be null");
        Objects.requireNonNull(today, "today must not be null");

        if (!target.isPositive()) {
            throw new IllegalArgumentException("a savings goal must be for more than nothing, was " + target);
        }
        if (targetDate.isBefore(today)) {
            throw new IllegalArgumentException(
                    "a new savings goal cannot be due before it is created: " + targetDate + " is before " + today);
        }
        return new SavingsGoal(user, requireName(name), target, targetDate);
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("a savings goal must be named");
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("a savings goal name must be at most 100 characters");
        }
        return trimmed;
    }

    /** Renames the goal. Nothing about its money changes. */
    public void rename(String name) {
        this.name = requireName(name);
    }

    /**
     * Changes what is being saved for and by when.
     *
     * <p>The amount may move in either direction — a target can turn out to have been
     * optimistic — but not to zero, and not into a different currency.
     */
    public void retarget(Money target, LocalDate targetDate) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(targetDate, "targetDate must not be null");
        if (!target.isPositive()) {
            throw new IllegalArgumentException("a savings goal must be for more than nothing, was " + target);
        }
        if (!target.currency().equals(this.target.currency())) {
            throw new IllegalArgumentException(
                    "a goal's currency is fixed at " + this.target.currency().getCurrencyCode()
                            + " and cannot become " + target.currency().getCurrencyCode());
        }
        this.target = target;
        this.targetDate = targetDate;
    }

    /** Records what is actually put aside now, replacing whatever was recorded before. */
    public void recordCurrentAmount(Money current) {
        Objects.requireNonNull(current, "current must not be null");
        requireGoalCurrency(current);
        if (current.isNegative()) {
            throw new IllegalArgumentException("a savings goal cannot hold less than nothing, was " + current);
        }
        this.currentAmount = current.amount();
    }

    /** Adds to what is put aside. Negative contributions are a withdrawal and allowed, to zero. */
    public void contribute(Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        requireGoalCurrency(amount);
        BigDecimal updated = currentAmount.add(amount.amount());
        if (updated.signum() < 0) {
            throw new IllegalArgumentException(
                    "withdrawing " + amount + " would leave the goal below nothing");
        }
        this.currentAmount = updated;
    }

    /** Marks this as the user's featured goal. Idempotent: featuring it again changes nothing. */
    public void feature() {
        this.featured = true;
    }

    /** Clears the featured flag. Idempotent: unfeaturing an unfeatured goal changes nothing. */
    public void unfeature() {
        this.featured = false;
    }

    private void requireGoalCurrency(Money amount) {
        if (!amount.currency().equals(target.currency())) {
            throw new IllegalArgumentException(
                    "this goal is in " + target.currency().getCurrencyCode()
                            + " but the amount is in " + amount.currency().getCurrencyCode());
        }
    }

    /** How far along this goal is: remaining, percentage, and whether it is met. */
    public GoalProgress progress() {
        return GoalProgress.of(target, currentAmount());
    }

    public UUID id() {
        return id;
    }

    public User user() {
        return user;
    }

    /** The owner's id. Safe on a detached goal: it is a column, not a hop. */
    public UUID userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public Money target() {
        return target;
    }

    public Money currentAmount() {
        return Money.of(currentAmount, target.currency());
    }

    public LocalDate targetDate() {
        return targetDate;
    }

    public boolean featured() {
        return featured;
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
