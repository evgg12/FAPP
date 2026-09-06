package com.fapp.persistence;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Drives the schema with raw SQL, deliberately bypassing the entities, so that each
 * guarantee is proved to live in the database rather than in application code. The
 * assertions name the constraint that must fire: a rename or a silently dropped
 * constraint fails the test rather than passing for the wrong reason.
 */
class SchemaConstraintTest extends AbstractPostgresTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userOne;
    private UUID userTwo;
    private UUID currentAccount;
    private UUID savingsAccount;
    private UUID foreignAccount;
    private UUID currentImport;
    private UUID savingsImport;

    @BeforeEach
    void resetAndSeed() {
        jdbc.execute("TRUNCATE users CASCADE");

        userOne = insertUser("one@example.com");
        userTwo = insertUser("two@example.com");
        currentAccount = insertAccount(userOne, "monzo", "Monzo Current", "CURRENT", "GBP");
        savingsAccount = insertAccount(userOne, "monzo", "Monzo Pot", "SAVINGS", "GBP");
        foreignAccount = insertAccount(userTwo, "bank_of_scotland", "BoS Euro", "CURRENT", "EUR");
        currentImport = insertImport(currentAccount, userOne, "monzo", HASH_A);
        savingsImport = insertImport(savingsAccount, userOne, "monzo", HASH_B);
    }

    // --- ownership and currency, both carried by fk_transactions_account ---

    @Test
    void refusesATransactionClaimingAUserWhoDoesNotOwnTheAccount() {
        assertViolates("fk_transactions_account",
                () -> insertTransaction(currentAccount, userTwo, currentImport, "-10.0000", "GBP", "c".repeat(64), 1));
    }

    @Test
    void refusesATransactionInADifferentCurrencyFromItsAccount() {
        assertViolates("fk_transactions_account",
                () -> insertTransaction(currentAccount, userOne, currentImport, "-10.0000", "EUR", "d".repeat(64), 1));
    }

    @Test
    void refusesToChangeAnAccountsCurrencyWhileItHoldsTransactions() {
        insertTransaction(currentAccount, userOne, currentImport, "-10.0000", "GBP", "e".repeat(64), 1);

        assertViolates("fk_transactions_account",
                () -> jdbc.update("UPDATE accounts SET currency = 'EUR' WHERE id = ?", currentAccount));
    }

    @Test
    void refusesATransactionCitingAnImportForAnotherAccount() {
        assertViolates("fk_transactions_import",
                () -> insertTransaction(currentAccount, userOne, savingsImport, "-10.0000", "GBP", "f".repeat(64), 1));
    }

    // --- monetary correctness ---

    @Test
    void refusesAZeroAmount() {
        assertViolates("ck_transactions_amount_nonzero",
                () -> insertTransaction(currentAccount, userOne, currentImport, "0.0000", "GBP", "1".repeat(64), 1));
    }

    @Test
    void refusesHalfOfAForeignCurrencyPair() {
        assertViolates("ck_transactions_original_pair", () -> jdbc.update(
                insertTransactionSql("original_amount"),
                UUID.randomUUID(), currentAccount, userOne, currentImport, "-8.5000", "GBP", "2".repeat(64), 1,
                "-10.0000"));
    }

    @Test
    void refusesAForeignLegPointingTheOppositeWay() {
        assertViolates("ck_transactions_original_sign", () -> jdbc.update(
                insertTransactionSql("original_amount", "original_currency"),
                UUID.randomUUID(), currentAccount, userOne, currentImport, "-8.5000", "GBP", "3".repeat(64), 1,
                "10.0000", "EUR"));
    }

    @Test
    void refusesAForeignLegInTheAccountsOwnCurrency() {
        assertViolates("ck_transactions_original_currency", () -> jdbc.update(
                insertTransactionSql("original_amount", "original_currency"),
                UUID.randomUUID(), currentAccount, userOne, currentImport, "-8.5000", "GBP", "4".repeat(64), 1,
                "-8.5000", "GBP"));
    }

    @Test
    void acceptsAWellFormedForeignCurrencyTransaction() {
        assertThatCode(() -> jdbc.update(
                insertTransactionSql("original_amount", "original_currency"),
                UUID.randomUUID(), currentAccount, userOne, currentImport, "-8.5000", "GBP", "5".repeat(64), 1,
                "-10.0000", "EUR"))
                .doesNotThrowAnyException();
    }

    // --- deduplication ---

    @Test
    void refusesTheSameFingerprintAndOccurrenceTwiceOnOneAccount() {
        insertTransaction(currentAccount, userOne, currentImport, "-2.5000", "GBP", "6".repeat(64), 1);

        assertViolates("uq_transactions_dedup",
                () -> insertTransaction(currentAccount, userOne, currentImport, "-2.5000", "GBP", "6".repeat(64), 1));
    }

    @Test
    void acceptsTwoGenuinelyIdenticalSameDayTransactions() {
        String fingerprint = "7".repeat(64);
        insertTransaction(currentAccount, userOne, currentImport, "-2.5000", "GBP", fingerprint, 1);

        assertThatCode(() ->
                insertTransaction(currentAccount, userOne, currentImport, "-2.5000", "GBP", fingerprint, 2))
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE fingerprint = ?", Integer.class, fingerprint))
                .isEqualTo(2);
    }

    @Test
    void allowsTheSameFingerprintOnADifferentAccount() {
        String fingerprint = "8".repeat(64);
        insertTransaction(currentAccount, userOne, currentImport, "-2.5000", "GBP", fingerprint, 1);

        assertThatCode(() ->
                insertTransaction(savingsAccount, userOne, savingsImport, "-2.5000", "GBP", fingerprint, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesTheSameBankTransactionIdTwiceOnOneAccount() {
        insertTransactionWithExternalId(currentAccount, userOne, currentImport, "9".repeat(64), "tx_0001");

        assertViolates("ux_transactions_external_id",
                () -> insertTransactionWithExternalId(currentAccount, userOne, currentImport, "0".repeat(64),
                        "tx_0001"));
    }

    @Test
    void allowsManyTransactionsWithNoBankTransactionIdAtAll() {
        insertTransaction(currentAccount, userOne, currentImport, "-1.0000", "GBP", "aa".repeat(32), 1);

        assertThatCode(() ->
                insertTransaction(currentAccount, userOne, currentImport, "-2.0000", "GBP", "bb".repeat(32), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAByteIdenticalStatementReupload() {
        assertViolates("uq_statement_imports_content",
                () -> insertImport(currentAccount, userOne, "monzo", HASH_A));
    }

    // --- import atomicity invariant ---

    @Test
    void refusesImportCountsThatDoNotAccountForEveryRow() {
        assertViolates("ck_statement_imports_counts", () -> jdbc.update(
                "INSERT INTO statement_imports (id, user_id, account_id, provider, content_hash, "
                        + "period_start, period_end, row_count, imported_count, duplicate_count) "
                        + "VALUES (?, ?, ?, 'monzo', ?, DATE '2026-03-01', DATE '2026-03-31', 10, 4, 3)",
                UUID.randomUUID(), userOne, currentAccount, "c".repeat(64)));
    }

    @Test
    void refusesAStatementPeriodThatEndsBeforeItStarts() {
        assertViolates("ck_statement_imports_period", () -> jdbc.update(
                "INSERT INTO statement_imports (id, user_id, account_id, provider, content_hash, "
                        + "period_start, period_end, row_count, imported_count, duplicate_count) "
                        + "VALUES (?, ?, ?, 'monzo', ?, DATE '2026-03-31', DATE '2026-03-01', 0, 0, 0)",
                UUID.randomUUID(), userOne, currentAccount, "d".repeat(64)));
    }

    // --- transfer integrity ---

    @Test
    void refusesATransferMixingTwoUsersTransactions() {
        UUID foreignImport = insertImport(foreignAccount, userTwo, "bank_of_scotland", "e".repeat(64));
        UUID mine = insertTransaction(currentAccount, userOne, currentImport, "-500.0000", "GBP", "cc".repeat(32), 1);
        UUID theirs = insertTransaction(foreignAccount, userTwo, foreignImport, "500.0000", "EUR", "dd".repeat(32), 1);

        assertViolates("fk_transfers_incoming", () -> insertTransfer(userOne, mine, theirs));
    }

    @Test
    void refusesReusingATransactionAsALegOfASecondTransfer() {
        UUID outgoing = insertTransaction(currentAccount, userOne, currentImport, "-500.0000", "GBP", "ee".repeat(32), 1);
        UUID incoming = insertTransaction(savingsAccount, userOne, savingsImport, "500.0000", "GBP", "ff".repeat(32), 1);
        UUID another = insertTransaction(savingsAccount, userOne, savingsImport, "500.0000", "GBP", "11".repeat(32), 1);
        insertTransfer(userOne, outgoing, incoming);

        assertViolates("uq_transfers_outgoing", () -> insertTransfer(userOne, outgoing, another));
    }

    @Test
    void refusesATransactionBeingBothLegs() {
        UUID leg = insertTransaction(currentAccount, userOne, currentImport, "-500.0000", "GBP", "22".repeat(32), 1);

        assertViolates("ck_transfers_distinct_legs", () -> insertTransfer(userOne, leg, leg));
    }

    @Test
    void acceptsATransferBetweenTwoOfOneUsersAccounts() {
        UUID outgoing = insertTransaction(currentAccount, userOne, currentImport, "-500.0000", "GBP", "33".repeat(32), 1);
        UUID incoming = insertTransaction(savingsAccount, userOne, savingsImport, "500.0000", "GBP", "44".repeat(32), 1);

        assertThatCode(() -> insertTransfer(userOne, outgoing, incoming)).doesNotThrowAnyException();
    }

    // --- vocabularies and formats ---

    @Test
    void refusesACategoryOutsideTheAgreedTaxonomy() {
        assertViolates("ck_transactions_category", () -> jdbc.update(
                "INSERT INTO transactions (id, account_id, user_id, statement_import_id, booking_date, amount, "
                        + "currency, description, category, category_source, transaction_type, fingerprint, "
                        + "fingerprint_version, occurrence) VALUES (?, ?, ?, ?, DATE '2026-03-01', -1.0000, 'GBP', "
                        + "'X', 'CRYPTO', 'RULE', 'CARD_PAYMENT', ?, 1, 1)",
                UUID.randomUUID(), currentAccount, userOne, currentImport, "55".repeat(32)));
    }

    @Test
    void refusesAFingerprintThatIsNotLowercaseHexSha256() {
        assertViolates("ck_transactions_fingerprint",
                () -> insertTransaction(currentAccount, userOne, currentImport, "-1.0000", "GBP", "Z".repeat(64), 1));
    }

    @Test
    void refusesAnOccurrenceBelowOne() {
        assertViolates("ck_transactions_occurrence",
                () -> insertTransaction(currentAccount, userOne, currentImport, "-1.0000", "GBP", "66".repeat(32), 0));
    }

    @Test
    void refusesAProviderThatIsNotALowercaseSlug() {
        assertViolates("ck_accounts_provider",
                () -> insertAccount(userOne, "Bank Of Scotland", "Bad", "CURRENT", "GBP"));
    }

    @Test
    void acceptsAnyNewProviderSlugWithoutASchemaChange() {
        assertThatCode(() -> insertAccount(userOne, "starling", "Starling Current", "CURRENT", "GBP"))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesTwoUsersSharingAnEmailRegardlessOfCase() {
        assertViolates("ux_users_email", () -> jdbc.update(
                "INSERT INTO users (id, email, display_name) VALUES (?, 'one@example.com', 'Duplicate')",
                UUID.randomUUID()));
    }

    // --- cascade behaviour ---

    @Test
    void revertingAnImportRemovesOnlyItsOwnTransactions() {
        insertTransaction(currentAccount, userOne, currentImport, "-1.0000", "GBP", "77".repeat(32), 1);
        insertTransaction(savingsAccount, userOne, savingsImport, "-2.0000", "GBP", "88".repeat(32), 1);

        jdbc.update("DELETE FROM statement_imports WHERE id = ?", currentImport);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE account_id = ?", Integer.class, savingsAccount))
                .isEqualTo(1);
    }

    @Test
    void deletingAUserRemovesEveryTraceOfTheirFinancialData() {
        UUID outgoing = insertTransaction(currentAccount, userOne, currentImport, "-500.0000", "GBP", "99".repeat(32), 1);
        UUID incoming = insertTransaction(savingsAccount, userOne, savingsImport, "500.0000", "GBP", "ab".repeat(32), 1);
        insertTransfer(userOne, outgoing, incoming);

        jdbc.update("DELETE FROM users WHERE id = ?", userOne);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts WHERE user_id = ?", Integer.class, userOne))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM statement_imports", Integer.class)).isZero();
        // The other user is untouched.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts WHERE user_id = ?", Integer.class, userTwo))
                .isEqualTo(1);
    }

    // --- helpers ---

    private void assertViolates(String constraintName, Runnable attempt) {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(attempt::run)
                .withMessageContaining(constraintName);
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, display_name) VALUES (?, ?, 'Test User')", id, email);
        return id;
    }

    private UUID insertAccount(UUID userId, String provider, String name, String type, String currency) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, user_id, provider, display_name, account_type, currency) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, userId, provider, name, type, currency);
        return id;
    }

    private UUID insertImport(UUID accountId, UUID userId, String provider, String contentHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO statement_imports (id, user_id, account_id, provider, content_hash, "
                + "period_start, period_end, row_count, imported_count, duplicate_count) "
                + "VALUES (?, ?, ?, ?, ?, DATE '2026-03-01', DATE '2026-03-31', 0, 0, 0)",
                id, userId, accountId, provider, contentHash);
        return id;
    }

    private UUID insertTransaction(UUID accountId, UUID userId, UUID importId,
                                   String amount, String currency, String fingerprint, int occurrence) {
        UUID id = UUID.randomUUID();
        jdbc.update(insertTransactionSql(), id, accountId, userId, importId, amount, currency, fingerprint,
                occurrence);
        return id;
    }

    private UUID insertTransactionWithExternalId(UUID accountId, UUID userId, UUID importId,
                                                 String fingerprint, String externalId) {
        UUID id = UUID.randomUUID();
        jdbc.update(insertTransactionSql("external_id"), id, accountId, userId, importId, "-1.0000", "GBP",
                fingerprint, 1, externalId);
        return id;
    }

    /** The mandatory columns, plus any extra columns appended as trailing parameters. */
    private String insertTransactionSql(String... extraColumns) {
        StringBuilder columns = new StringBuilder(
                "id, account_id, user_id, statement_import_id, booking_date, amount, currency, description, "
                        + "category, category_source, transaction_type, fingerprint, fingerprint_version, occurrence");
        StringBuilder values = new StringBuilder(
                "?, ?, ?, ?, DATE '2026-03-01', CAST(? AS numeric), ?, 'TEST PAYMENT', "
                        + "'UNCATEGORISED', 'DEFAULT', 'CARD_PAYMENT', ?, 1, CAST(? AS smallint)");
        for (String extra : extraColumns) {
            columns.append(", ").append(extra);
            values.append(extra.endsWith("_amount") ? ", CAST(? AS numeric)" : ", ?");
        }
        return "INSERT INTO transactions (" + columns + ") VALUES (" + values + ")";
    }

    private void insertTransfer(UUID userId, UUID outgoing, UUID incoming) {
        jdbc.update("INSERT INTO transfers (id, user_id, outgoing_transaction_id, incoming_transaction_id, "
                + "detection_source) VALUES (?, ?, ?, ?, 'RULE')",
                UUID.randomUUID(), userId, outgoing, incoming);
    }
}
