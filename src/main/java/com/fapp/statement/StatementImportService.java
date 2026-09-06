package com.fapp.statement;

import com.fapp.account.Account;
import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransferDetectionService;
import com.fapp.transaction.TransactionFingerprint;
import com.fapp.transaction.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a bank statement into an existing account.
 *
 * <p>The whole import is one database transaction: either every new transaction and the
 * record of the import land together, or nothing does. A statement is never partly
 * imported.
 *
 * <p>Order matters here, and it is forced by the domain rather than chosen. A
 * {@link StatementImport} is built with its final counts, and a {@link Transaction}
 * cannot exist without the import that produced it — so deduplication has to be decided
 * before either object exists:
 *
 * <ol>
 *   <li>hash the upload and reject a file this account has already imported;
 *   <li>parse it with the adapter that reads the account's bank;
 *   <li>work out which rows are new, which are already held, and what occurrence each
 *       new row takes;
 *   <li>build the import with the counts that fall out of that;
 *   <li>build a transaction for each new row against that import, and save both.
 * </ol>
 *
 * <p>Application checks decide what to write; they do not guarantee it. Two imports
 * racing on the same account can both believe a transaction is new, and the database is
 * what settles it — so a constraint violation is expected and translated, not treated as
 * impossible.
 */
@Service
public class StatementImportService {

    private final Map<String, StatementAdapter> adaptersByProvider;
    private final StatementImportRepository statementImports;
    private final TransactionRepository transactions;
    private final TransferDetectionService transferDetection;
    private final EntityManager entityManager;

    public StatementImportService(List<StatementAdapter> adapters,
                                  StatementImportRepository statementImports,
                                  TransactionRepository transactions,
                                  TransferDetectionService transferDetection,
                                  EntityManager entityManager) {
        this.adaptersByProvider = index(adapters);
        this.statementImports = statementImports;
        this.transactions = transactions;
        this.transferDetection = transferDetection;
        this.entityManager = entityManager;
    }

    private static Map<String, StatementAdapter> index(List<StatementAdapter> adapters) {
        Map<String, StatementAdapter> byProvider = new HashMap<>();
        for (StatementAdapter adapter : adapters) {
            StatementAdapter clash = byProvider.put(adapter.provider(), adapter);
            if (clash != null) {
                throw new IllegalStateException("two adapters both claim provider '" + adapter.provider()
                        + "': " + clash.getClass().getName() + " and " + adapter.getClass().getName());
            }
        }
        return Map.copyOf(byProvider);
    }

    /**
     * @param account   an existing account, already persisted, whose provider decides
     *                  which adapter reads the file and whose currency every
     *                  transaction must be in
     * @param statement the raw bytes of the upload
     * @return the record of what was imported, including how many rows were already held
     * @throws UnsupportedProviderException  if no adapter reads the account's bank
     * @throws DuplicateStatementException   if this exact file is already imported
     * @throws StatementImportException      if a concurrent import stored these
     *                                       transactions first
     * @throws StatementParseException  if the file itself cannot be read
     */
    @Transactional
    public StatementImport importStatement(Account account, byte[] statement) {
        Objects.requireNonNull(account, "account must not be null");
        Objects.requireNonNull(statement, "statement must not be null");

        String contentHash = sha256Hex(statement);
        StatementAdapter adapter = adapterFor(account);

        if (statementImports.existsByAccount_IdAndContentHash(account.id(), contentHash)) {
            throw new DuplicateStatementException(
                    "this statement has already been imported into " + account.displayName());
        }

        ParsedStatement parsed = adapter.parse(statement);
        List<FingerprintedRow> rows = fingerprint(parsed.transactions());
        List<NewRow> newRows = selectNewRows(account, rows);

        StatementImport statementImport = StatementImport.of(
                account,
                contentHash,
                parsed.period(),
                parsed.rowCount(),
                newRows.size(),
                parsed.rowCount() - newRows.size());

        return persist(account, statementImport, newRows);
    }

    private StatementAdapter adapterFor(Account account) {
        StatementAdapter adapter = adaptersByProvider.get(account.provider());
        if (adapter == null) {
            throw new UnsupportedProviderException(
                    "no statement adapter is registered for provider '" + account.provider()
                            + "'; known providers are " + adaptersByProvider.keySet().stream().sorted().toList());
        }
        return adapter;
    }

    private static List<FingerprintedRow> fingerprint(List<RawTransaction> parsed) {
        List<FingerprintedRow> rows = new ArrayList<>(parsed.size());
        for (RawTransaction raw : parsed) {
            rows.add(new FingerprintedRow(raw,
                    TransactionFingerprint.of(raw.bookingDate(), raw.amount(), raw.description())));
        }
        return rows;
    }

    /**
     * Decides which of a statement's rows this account does not already hold, and what
     * occurrence each new one takes.
     *
     * <p>A row the bank gave a transaction id to is settled by that id alone: present in
     * the account already means duplicate, whatever the rest of the row says.
     *
     * <p>A row without one is settled by counting. If the account already holds two
     * transactions with a row's fingerprint and the statement lists three, the first two
     * are the ones already held and only the third is new. That is what makes
     * re-importing a statement a no-op while still letting a later, overlapping
     * statement bring in a genuinely new third coffee of the same price on the same day.
     *
     * <p>Occurrence numbering is separate from that decision, because every new row needs
     * one — including rows identified by transaction id, since
     * {@code uq_transactions_dedup} applies to them too. Numbering continues above the
     * highest occurrence already stored rather than from the count, so a gap left by a
     * reverted import cannot cause a collision.
     */
    private List<NewRow> selectNewRows(Account account, List<FingerprintedRow> rows) {
        Set<String> alreadyHeldExternalIds = findAlreadyHeldExternalIds(account, rows);
        Map<String, Tally> tallies = tallyFingerprints(account, rows);

        Map<String, Integer> seenWithoutExternalId = new HashMap<>();
        Map<String, Integer> nextOccurrence = new HashMap<>();
        List<NewRow> newRows = new ArrayList<>();

        for (FingerprintedRow row : rows) {
            String fingerprint = row.fingerprint();
            Tally tally = tallies.getOrDefault(fingerprint, Tally.NONE);

            boolean alreadyHeld;
            if (row.raw().externalId() != null) {
                alreadyHeld = alreadyHeldExternalIds.contains(row.raw().externalId());
            } else {
                int seen = seenWithoutExternalId.merge(fingerprint, 1, Integer::sum);
                alreadyHeld = seen <= tally.count();
            }
            if (alreadyHeld) {
                continue;
            }

            int occurrence = nextOccurrence.merge(fingerprint, tally.maxOccurrence() + 1,
                    (existing, ignored) -> existing + 1);
            newRows.add(new NewRow(row.raw(), fingerprint, occurrence));
        }
        return newRows;
    }

    private Set<String> findAlreadyHeldExternalIds(Account account, List<FingerprintedRow> rows) {
        Set<String> externalIds = new HashSet<>();
        for (FingerprintedRow row : rows) {
            if (row.raw().externalId() != null) {
                externalIds.add(row.raw().externalId());
            }
        }
        if (externalIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(transactions.findExistingExternalIds(account.id(), externalIds));
    }

    private Map<String, Tally> tallyFingerprints(Account account, List<FingerprintedRow> rows) {
        Set<String> fingerprints = new HashSet<>();
        for (FingerprintedRow row : rows) {
            fingerprints.add(row.fingerprint());
        }
        if (fingerprints.isEmpty()) {
            return Map.of();
        }
        Map<String, Tally> tallies = new HashMap<>();
        for (Object[] tally : transactions.tallyFingerprints(account.id(), fingerprints)) {
            tallies.put((String) tally[0],
                    new Tally(((Number) tally[1]).intValue(), ((Number) tally[2]).intValue()));
        }
        return tallies;
    }

    /**
     * Writes the import and its transactions in the surrounding transaction, the import
     * first so the foreign key it is referenced by resolves.
     *
     * <p>{@link EntityManager#persist} rather than a repository {@code save}: ids are
     * assigned in the constructors, so Spring Data would read these as detached and
     * {@code merge} them — a select before every insert, and a managed copy that the
     * transactions being written do not reference.
     */
    private StatementImport persist(Account account, StatementImport statementImport, List<NewRow> newRows) {
        try {
            entityManager.persist(statementImport);
            List<Transaction> stored = new ArrayList<>(newRows.size());
            for (NewRow row : newRows) {
                Transaction transaction = transactionOf(account, statementImport, row);
                entityManager.persist(transaction);
                stored.add(transaction);
            }
            entityManager.flush();

            /*
             * Now that these rows exist, some of them may complete a movement between
             * two of the user's own accounts whose other half was imported earlier --
             * one statement covers one account, so the two legs can never arrive
             * together. Detected here, inside the same transaction, so an import either
             * lands with its transfers recognised or does not land at all.
             */
            transferDetection.detect(stored);
            entityManager.flush();

            return statementImport;
        } catch (DataIntegrityViolationException | PersistenceException e) {
            throw new StatementImportException(
                    "the database rejected this import; another import may have stored these"
                            + " transactions first", e);
        }
    }

    private static Transaction transactionOf(Account account, StatementImport statementImport, NewRow row) {
        RawTransaction raw = row.raw();
        return Transaction.builder()
                .account(account)
                .statementImport(statementImport)
                .bookingDate(raw.bookingDate())
                .occurredOn(raw.occurredOn())
                .amount(raw.amount())
                .originalAmount(raw.originalAmount())
                .description(raw.description())
                .merchant(raw.merchant())
                .category(raw.category(), raw.categorySource())
                .transactionType(raw.transactionType())
                .externalId(raw.externalId())
                .fingerprint(row.fingerprint())
                .fingerprintVersion(Transaction.CURRENT_FINGERPRINT_VERSION)
                .occurrence(row.occurrence())
                .build();
    }

    /**
     * Lowercase hex SHA-256 of the upload, which is all that is kept of it. The bytes
     * themselves are never stored, and neither is the filename: bank statement filenames
     * routinely contain the account number.
     */
    private static String sha256Hex(byte[] statement) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(statement));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }

    private record FingerprintedRow(RawTransaction raw, String fingerprint) {
    }

    private record NewRow(RawTransaction raw, String fingerprint, int occurrence) {
    }

    /** What the account already holds for one fingerprint. */
    private record Tally(int count, int maxOccurrence) {
        static final Tally NONE = new Tally(0, 0);
    }
}
