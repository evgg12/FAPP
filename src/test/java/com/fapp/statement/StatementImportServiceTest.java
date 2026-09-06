package com.fapp.statement;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.money.Money;
import com.fapp.persistence.AbstractPostgresTest;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransactionFingerprint;
import com.fapp.transaction.TransactionRepository;
import com.fapp.transaction.TransactionType;
import com.fapp.user.User;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.function.Consumer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Drives the import end to end against real PostgreSQL, because most of what makes an
 * import correct is enforced by the database: the uniqueness that makes a re-upload a
 * no-op, the foreign keys that tie a transaction to its import and its owner, and the
 * rollback that keeps a failed import from leaving anything behind.
 */
class StatementImportServiceTest extends AbstractPostgresTest {

    private static final String HEADER =
            "Transaction ID,Date,Time,Type,Name,Emoji,Category,Amount,Currency,Local amount,"
                    + "Local currency,Notes and #tags,Address,Receipt,Description,Category split,"
                    + "Money Out,Money In";

    private static final Currency GBP = Currency.getInstance("GBP");

    @Autowired
    private StatementImportService service;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private StatementImportRepository statementImports;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private Account monzoAccount;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE users CASCADE");
        user = User.of("importer@example.com", "Importer");
        monzoAccount = Account.of(user, "monzo", "Monzo Current", AccountType.CURRENT, GBP);
        inTransaction(em -> {
            em.persist(user);
            em.persist(monzoAccount);
        });
    }

    // --- the happy path ---

    @Test
    void importsEveryMovementInTheMonzoExport() {
        StatementImport result = service.importStatement(monzoAccount, fixture());

        // 19 data rows in the export, one a zero-value authorisation hold.
        assertThat(result.rowCount()).isEqualTo(18);
        assertThat(result.importedCount()).isEqualTo(18);
        assertThat(result.duplicateCount()).isZero();
        assertThat(transactions.count()).isEqualTo(18);
        assertThat(statementImports.count()).isEqualTo(1);
    }

    @Test
    void recordsTheImportWithoutKeepingTheFileOrItsName() {
        byte[] statement = fixture();

        StatementImport result = service.importStatement(monzoAccount, statement);

        assertThat(result.contentHash()).matches("^[0-9a-f]{64}$");
        assertThat(result.provider()).isEqualTo("monzo");
        assertThat(result.period().start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(result.period().end()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(result.importedAt()).isNotNull();
        assertThat(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'statement_imports'",
                String.class)).doesNotContain("filename", "content");
    }

    @Test
    void attributesEveryTransactionToTheAccountAndItsOwner() {
        service.importStatement(monzoAccount, fixture());

        // accountId() walks the lazy account association, so it is read in a session.
        transactionTemplate.executeWithoutResult(status ->
                assertThat(transactions.findAll()).allSatisfy(transaction -> {
                    assertThat(transaction.accountId()).isEqualTo(monzoAccount.id());
                    assertThat(transaction.userId()).isEqualTo(user.id());
                }));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE account_id = ? AND user_id = ?",
                Integer.class, monzoAccount.id(), user.id())).isEqualTo(18);
    }

    @Test
    void tiesEveryTransactionToTheImportThatProducedIt() {
        StatementImport result = service.importStatement(monzoAccount, fixture());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE statement_import_id = ?",
                Integer.class, result.id())).isEqualTo(18);
    }

    @Test
    void carriesTheAdapterSuppliedCategoryAndItsSourceThrough() {
        service.importStatement(monzoAccount, fixture());

        assertThat(reload()).allSatisfy(transaction ->
                assertThat(transaction.categorySource()).isEqualTo(CategorySource.ADAPTER));
        assertThat(byExternalId("tx_sample000000000000002").category()).isEqualTo(Category.GROCERIES);
        assertThat(byExternalId("tx_sample000000000000008").category()).isEqualTo(Category.INCOME);
        assertThat(byExternalId("tx_sample000000000000010").category()).isEqualTo(Category.TRANSFER);
        assertThat(byExternalId("tx_sample000000000000013").category()).isEqualTo(Category.UNCATEGORISED);
    }

    @Test
    void carriesTheForeignCurrencyLegThrough() {
        service.importStatement(monzoAccount, fixture());

        Transaction abroad = byExternalId("tx_sample000000000000005");
        assertThat(abroad.amount()).isEqualTo(Money.of("-18.62", "GBP"));
        assertThat(abroad.originalAmount()).contains(Money.of("-21.90", "EUR"));

        assertThat(byExternalId("tx_sample000000000000002").originalAmount()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE original_amount IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void carriesTheRestOfTheRowThrough() {
        service.importStatement(monzoAccount, fixture());

        Transaction groceries = byExternalId("tx_sample000000000000002");
        assertThat(groceries.bookingDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(groceries.occurredOn()).isEmpty();
        assertThat(groceries.description()).isEqualTo("GREENFIELD GROCERS 4821");
        assertThat(groceries.merchant()).contains("Greenfield Grocers");
        assertThat(groceries.transactionType()).isEqualTo(TransactionType.CARD_PAYMENT);
        assertThat(groceries.amount()).isEqualTo(Money.of("-24.15", "GBP"));
        assertThat(byExternalId("tx_sample000000000000017").merchant()).isEmpty();
    }

    @Test
    void storesTheCanonicalFingerprintAndItsVersion() {
        service.importStatement(monzoAccount, fixture());

        Transaction groceries = byExternalId("tx_sample000000000000002");
        assertThat(groceries.fingerprint()).isEqualTo(TransactionFingerprint.of(
                LocalDate.of(2026, 8, 3), Money.of("-24.15", "GBP"), "GREENFIELD GROCERS 4821"));
        assertThat(groceries.fingerprintVersion()).isEqualTo(Transaction.CURRENT_FINGERPRINT_VERSION);
        assertThat(reload()).allSatisfy(transaction -> {
            assertThat(transaction.fingerprint()).matches("^[0-9a-f]{64}$");
            assertThat(transaction.occurrence()).isGreaterThanOrEqualTo((short) 1);
        });
    }

    // --- deduplication ---

    @Test
    void refusesAByteIdenticalReUploadBeforeTouchingAnything() {
        service.importStatement(monzoAccount, fixture());

        assertThatExceptionOfType(StatementImportException.class)
                .isThrownBy(() -> service.importStatement(monzoAccount, fixture()))
                .withMessageContaining("already been imported");

        assertThat(transactions.count()).isEqualTo(18);
        assertThat(statementImports.count()).isEqualTo(1);
    }

    @Test
    void letsTheSameFileIntoADifferentAccount() {
        Account second = Account.of(user, "monzo", "Monzo Joint", AccountType.CURRENT, GBP);
        inTransaction(em -> em.persist(second));

        service.importStatement(monzoAccount, fixture());
        StatementImport result = service.importStatement(second, fixture());

        assertThat(result.importedCount()).isEqualTo(18);
        assertThat(transactions.count()).isEqualTo(36);
    }

    @Test
    void recognisesATransactionItAlreadyHoldsByTheBanksOwnTransactionId() {
        service.importStatement(monzoAccount, fixture());

        // A later export overlapping the first: one row already held, one genuinely new.
        StatementImport second = service.importStatement(monzoAccount, export(
                "tx_sample000000000000002,03/08/2026,08:14:22,Card payment,Greenfield Grocers,,Groceries,"
                        + "-24.15,GBP,-24.15,GBP,,,,GREENFIELD GROCERS 4821,,24.15,",
                "tx_sample000000000000099,30/08/2026,10:00:00,Card payment,Greenfield Grocers,,Groceries,"
                        + "-31.05,GBP,-31.05,GBP,,,,GREENFIELD GROCERS 4821,,31.05,"));

        assertThat(second.rowCount()).isEqualTo(2);
        assertThat(second.importedCount()).isEqualTo(1);
        assertThat(second.duplicateCount()).isEqualTo(1);
        assertThat(transactions.count()).isEqualTo(19);
        assertThat(byExternalId("tx_sample000000000000099").amount()).isEqualTo(Money.of("-31.05", "GBP"));
    }

    @Test
    void importsTwoGenuinelyIdenticalRowsAtDifferentOccurrences() {
        // Two coffees of the same price at the same place on the same day: one
        // fingerprint, two transactions, distinguished only by occurrence.
        StatementImport result = service.importStatement(monzoAccount, twoIdenticalCoffees("tx_a", "tx_b"));

        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(reload()).extracting(Transaction::fingerprint).hasSize(2).containsOnly(coffeeFingerprint());
        assertThat(reload()).extracting(Transaction::occurrence)
                .containsExactlyInAnyOrder((short) 1, (short) 2);
    }

    @Test
    void reImportingIdenticalRowsAddsNothing() {
        service.importStatement(monzoAccount, twoIdenticalCoffees("tx_a", "tx_b"));

        // Same transactions, different bytes: only the ignored Time column moved.
        StatementImport second = service.importStatement(monzoAccount, reTimed("tx_a", "tx_b"));

        assertThat(second.rowCount()).isEqualTo(2);
        assertThat(second.importedCount()).isZero();
        assertThat(second.duplicateCount()).isEqualTo(2);
        assertThat(transactions.count()).isEqualTo(2);
    }

    @Test
    void countsIdenticalRowsWithNoTransactionIdAgainstWhatItAlreadyHolds() {
        // The path every bank without transaction ids takes, Bank of Scotland included:
        // identity rests entirely on the fingerprint, and occurrence separates repeats.
        StatementImport first = service.importStatement(monzoAccount, coffeesWithoutIds("09:00:00", 2));

        assertThat(first.importedCount()).isEqualTo(2);
        assertThat(reload()).allSatisfy(t -> assertThat(t.externalId()).isEmpty());
        assertThat(reload()).extracting(Transaction::occurrence)
                .containsExactlyInAnyOrder((short) 1, (short) 2);

        // Re-exported later with a third genuine repeat: only the third is new.
        StatementImport second = service.importStatement(monzoAccount, coffeesWithoutIds("09:30:00", 3));

        assertThat(second.rowCount()).isEqualTo(3);
        assertThat(second.importedCount()).isEqualTo(1);
        assertThat(second.duplicateCount()).isEqualTo(2);
        assertThat(transactions.count()).isEqualTo(3);
        assertThat(reload()).extracting(Transaction::occurrence)
                .containsExactlyInAnyOrder((short) 1, (short) 2, (short) 3);
    }

    @Test
    void reImportingRowsWithNoTransactionIdAddsNothing() {
        service.importStatement(monzoAccount, coffeesWithoutIds("09:00:00", 2));

        StatementImport second = service.importStatement(monzoAccount, coffeesWithoutIds("10:15:00", 2));

        assertThat(second.importedCount()).isZero();
        assertThat(second.duplicateCount()).isEqualTo(2);
        assertThat(transactions.count()).isEqualTo(2);
    }

    // --- failures leave nothing behind ---

    @Test
    void failsClearlyWhenNoAdapterReadsTheAccountsBank() {
        Account unsupported =
                Account.of(user, "bank_of_scotland", "BoS Current", AccountType.CURRENT, GBP);
        inTransaction(em -> em.persist(unsupported));

        assertThatExceptionOfType(StatementImportException.class)
                .isThrownBy(() -> service.importStatement(unsupported, fixture()))
                .withMessageContaining("no statement adapter is registered for provider 'bank_of_scotland'")
                .withMessageContaining("monzo");

        assertThat(transactions.count()).isZero();
        assertThat(statementImports.count()).isZero();
    }

    @Test
    void aMalformedStatementPersistsNothing() {
        byte[] broken = export(
                "tx_ok,03/08/2026,08:00:00,Card payment,Somebody,,Groceries,-1.00,GBP,-1.00,GBP,,,,FINE,,1.00,",
                "tx_bad,04/08/2026,08:00:00,Card payment,Somebody,,Groceries,not-a-number,GBP,-1.00,GBP,,,,BROKEN,,1.00,");

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> service.importStatement(monzoAccount, broken))
                .withMessageContaining("row 3");

        assertThat(transactions.count()).isZero();
        assertThat(statementImports.count()).isZero();
    }

    @Test
    void rollsBackTheImportWhenARowBreaksADomainRule() {
        // Parses cleanly, but the money is not in the account's currency, so
        // Transaction.Builder refuses it after the import row has already been written.
        byte[] wrongCurrency = export(
                "tx_ok,03/08/2026,08:00:00,Card payment,Somebody,,Groceries,-1.00,GBP,-1.00,GBP,,,,FINE,,1.00,",
                "tx_eur,04/08/2026,08:00:00,Card payment,Somebody,,Groceries,-9.99,EUR,-9.99,EUR,,,,EUROS,,9.99,");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> service.importStatement(monzoAccount, wrongCurrency))
                .withMessageContaining("the account is in GBP");

        assertThat(transactions.count()).isZero();
        assertThat(statementImports.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM statement_imports", Integer.class)).isZero();
    }

    @Test
    void leavesEarlierImportsIntactWhenALaterOneFails() {
        service.importStatement(monzoAccount, fixture());

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> service.importStatement(monzoAccount,
                        "not a statement".getBytes(StandardCharsets.UTF_8)));

        assertThat(transactions.count()).isEqualTo(18);
        assertThat(statementImports.count()).isEqualTo(1);
    }

    // --- helpers ---

    private static byte[] fixture() {
        try (InputStream stream =
                     StatementImportServiceTest.class.getResourceAsStream("/monzo/statement.csv")) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] export(String... rows) {
        return (HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] twoIdenticalCoffees(String firstId, String secondId) {
        return export(coffee(firstId, "08:00:00"), coffee(secondId, "13:30:00"));
    }

    private static byte[] reTimed(String firstId, String secondId) {
        return export(coffee(firstId, "08:00:01"), coffee(secondId, "13:30:01"));
    }

    /** {@code repeats} identical rows with no transaction id, as a bank without ids exports. */
    private static byte[] coffeesWithoutIds(String time, int repeats) {
        String[] rows = new String[repeats];
        java.util.Arrays.fill(rows, coffee("", time));
        return export(rows);
    }

    private static String coffee(String externalId, String time) {
        return externalId + ",06/08/2026," + time + ",Card payment,Blue Door Cafe,,Eating out,"
                + "-2.50,GBP,-2.50,GBP,,,,BLUE DOOR CAFE,,2.50,";
    }

    private static String coffeeFingerprint() {
        return TransactionFingerprint.of(
                LocalDate.of(2026, 8, 6), Money.of("-2.50", "GBP"), "BLUE DOOR CAFE");
    }

    private List<Transaction> reload() {
        return transactionTemplate.execute(status -> transactions.findAll());
    }

    private Transaction byExternalId(String externalId) {
        return reload().stream()
                .filter(transaction -> transaction.externalId().filter(externalId::equals).isPresent())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no transaction with external id " + externalId));
    }

    private void inTransaction(Consumer<EntityManager> work) {
        transactionTemplate.executeWithoutResult(status -> {
            work.accept(entityManager);
            entityManager.flush();
        });
    }
}
