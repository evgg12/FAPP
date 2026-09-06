package com.fapp.statement;

import com.fapp.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One import of one statement into one account: the provenance of every transaction it
 * produced, and the record that makes an import reversible.
 *
 * <p>Stores no statement content and no filename. Bank statement filenames routinely
 * embed the account number, so keeping one would reintroduce exactly the personal data
 * the transaction model is careful to leave out. {@code contentHash} identifies an
 * upload without revealing anything about it, and rejects a byte-identical re-upload
 * before any parsing happens.
 *
 * <p>The counts are an invariant, not a report: import is atomic, so a malformed
 * statement is rejected whole and every parsed data row is either imported or
 * recognised as a duplicate. The database enforces the arithmetic.
 */
@Entity
@Table(name = "statement_imports")
public class StatementImport {

    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    /** Denormalised from the account so the composite foreign key can pin ownership. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 40, updatable = false)
    private String provider;

    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Embedded
    private StatementPeriod period;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    protected StatementImport() {
        // for Hibernate
    }

    private StatementImport(Account account,
                            String contentHash,
                            StatementPeriod period,
                            int rowCount,
                            int importedCount,
                            int duplicateCount) {
        this.id = UUID.randomUUID();
        this.account = account;
        this.userId = account.userId();
        this.provider = account.provider();
        this.contentHash = contentHash;
        this.period = period;
        this.rowCount = rowCount;
        this.importedCount = importedCount;
        this.duplicateCount = duplicateCount;
    }

    /**
     * @param contentHash   lowercase hex SHA-256 of the uploaded statement bytes
     * @param importedCount rows stored as new transactions
     * @param duplicateCount rows recognised as already present
     */
    public static StatementImport of(Account account,
                                     String contentHash,
                                     StatementPeriod period,
                                     int rowCount,
                                     int importedCount,
                                     int duplicateCount) {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(period, "period must not be null");
        requireSha256Hex(contentHash);
        requireConsistentCounts(rowCount, importedCount, duplicateCount);
        return new StatementImport(account, contentHash, period, rowCount, importedCount, duplicateCount);
    }

    private static void requireSha256Hex(String contentHash) {
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        if (!SHA_256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("contentHash must be lowercase hex SHA-256, was: " + contentHash);
        }
    }

    private static void requireConsistentCounts(int rowCount, int importedCount, int duplicateCount) {
        if (rowCount < 0 || importedCount < 0 || duplicateCount < 0) {
            throw new IllegalArgumentException(
                    "import counts must not be negative: rowCount=" + rowCount
                            + ", importedCount=" + importedCount + ", duplicateCount=" + duplicateCount);
        }
        if (importedCount + duplicateCount != rowCount) {
            throw new IllegalArgumentException(
                    "every parsed row must be imported or a duplicate: rowCount=" + rowCount
                            + " but importedCount=" + importedCount + " + duplicateCount=" + duplicateCount);
        }
    }

    public UUID id() {
        return id;
    }

    public Account account() {
        return account;
    }

    public UUID userId() {
        return userId;
    }

    public String provider() {
        return provider;
    }

    public String contentHash() {
        return contentHash;
    }

    public StatementPeriod period() {
        return period;
    }

    public int rowCount() {
        return rowCount;
    }

    public int importedCount() {
        return importedCount;
    }

    public int duplicateCount() {
        return duplicateCount;
    }

    public Instant importedAt() {
        return importedAt;
    }

    @PrePersist
    void onPersist() {
        this.importedAt = Instant.now();
    }
}
