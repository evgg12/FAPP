package com.fapp.statement;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The inclusive date range a statement covers.
 *
 * <p>More than documentation: deduplication counts existing transactions within the
 * incoming statement's own period, which is what lets a later statement covering the
 * same dates still import a genuinely new transaction.
 */
@Embeddable
public class StatementPeriod implements Serializable {

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate start;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate end;

    protected StatementPeriod() {
        // for Hibernate
    }

    private StatementPeriod(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    public static StatementPeriod of(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("statement period ends before it starts: " + start + " to " + end);
        }
        return new StatementPeriod(start, end);
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate end() {
        return end;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StatementPeriod that)) {
            return false;
        }
        return start.equals(that.start) && end.equals(that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return start + " to " + end;
    }
}
