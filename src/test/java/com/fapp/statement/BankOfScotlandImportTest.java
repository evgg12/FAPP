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
import com.fapp.user.User;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Imports Bank of Scotland statements end to end.
 *
 * <p>Worth its own class because this provider takes the other deduplication path.
 * Monzo hands over a transaction id that settles identity outright; Bank of Scotland
 * gives none, so identity rests entirely on the content fingerprint and on occurrence
 * to tell genuine repeats apart. That is the harder half of the algorithm and the one
 * every future bank without transaction ids will use.
 */
class BankOfScotlandImportTest extends AbstractPostgresTest {

    private static final String HEADER = "Transaction Date,Transaction Type,Sort Code,Account Number,"
            + "Transaction Description,Debit Amount,Credit Amount,Balance";

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
    private Account account;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE users CASCADE");
        user = User.of("bos@example.com", "BoS Customer");
        account = Account.of(user, "bank_of_scotland", "BoS Current", AccountType.CURRENT, GBP);
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(user);
            entityManager.persist(account);
            entityManager.flush();
        });
    }

    @Test
    void storesEveryValidTransactionInTheStatement() {
        StatementImport result = service.importStatement(account, fixture());

        assertThat(result.rowCount()).isEqualTo(10);
        assertThat(result.importedCount()).isEqualTo(10);
        assertThat(result.duplicateCount()).isZero();
        assertThat(result.provider()).isEqualTo("bank_of_scotland");
        assertThat(result.period().start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(result.period().end()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(transactions.count()).isEqualTo(10);
    }

    @Test
    void storesNoTransactionIdAndLeavesEveryRowUncategorised() {
        service.importStatement(account, fixture());

        assertThat(reload()).allSatisfy(transaction -> {
            assertThat(transaction.externalId()).isEmpty();
            assertThat(transaction.category()).isEqualTo(Category.UNCATEGORISED);
            assertThat(transaction.categorySource()).isEqualTo(CategorySource.DEFAULT);
            assertThat(transaction.originalAmount()).isEmpty();
            assertThat(transaction.occurredOn()).isEmpty();
        });
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE external_id IS NULL", Integer.class)).isEqualTo(10);
    }

    @Test
    void keepsTheSignsTheDebitAndCreditColumnsImplied() {
        service.importStatement(account, fixture());

        assertThat(reload()).filteredOn(t -> t.description().equals("SAMPLE EMPLOYER LTD"))
                .singleElement()
                .extracting(Transaction::amount)
                .isEqualTo(Money.of("1842.55", "GBP"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE amount < 0", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE amount > 0", Integer.class)).isEqualTo(2);
    }

    @Test
    void distinguishesGenuineSameDayRepeatsByOccurrence() {
        service.importStatement(account, fixture());

        String coffee = TransactionFingerprint.of(
                LocalDate.of(2026, 8, 6), Money.of("-2.50", "GBP"), "SAMPLE CAFE 2");

        assertThat(reload()).filteredOn(t -> t.fingerprint().equals(coffee))
                .hasSize(2)
                .extracting(Transaction::occurrence)
                .containsExactlyInAnyOrder((short) 1, (short) 2);
    }

    @Test
    void refusesAByteIdenticalReUpload() {
        service.importStatement(account, fixture());

        assertThatExceptionOfType(StatementImportException.class)
                .isThrownBy(() -> service.importStatement(account, fixture()))
                .withMessageContaining("already been imported");

        assertThat(transactions.count()).isEqualTo(10);
        assertThat(statementImports.count()).isEqualTo(1);
    }

    @Test
    void deduplicatesOverlappingStatementsByFingerprintAlone() {
        service.importStatement(account, fixture());

        // A September download that still carries the last few August rows, plus two
        // genuinely new ones. Different bytes, so the content hash does not catch it.
        StatementImport second = service.importStatement(account, export(
                "24/08/2026,DEB,00-00-00,00000000,SAMPLE POWER CO,88.40,,3391.60",
                "28/08/2026,DEB,00-00-00,00000000,SAMPLE HARDWARE 22,64.00,,3327.60",
                "01/09/2026,DEB,00-00-00,00000000,SAMPLE GROCER 1234,31.05,,3296.55",
                "02/09/2026,FPI,00-00-00,00000000,A COUNTERPARTY,,15.00,3311.55"));

        assertThat(second.rowCount()).isEqualTo(4);
        assertThat(second.duplicateCount()).isEqualTo(2);
        assertThat(second.importedCount()).isEqualTo(2);
        assertThat(transactions.count()).isEqualTo(12);
    }

    @Test
    void bringsInALaterGenuineRepeatWithoutDuplicatingTheEarlierOnes() {
        service.importStatement(account, fixture());

        // The same two coffees again, and a third bought the same day.
        StatementImport second = service.importStatement(account, export(
                "06/08/2026,DEB,00-00-00,00000000,SAMPLE CAFE 2,2.50,,1964.95",
                "06/08/2026,DEB,00-00-00,00000000,SAMPLE CAFE 2,2.50,,1962.45",
                "06/08/2026,DEB,00-00-00,00000000,SAMPLE CAFE 2,2.50,,1959.95"));

        assertThat(second.rowCount()).isEqualTo(3);
        assertThat(second.duplicateCount()).isEqualTo(2);
        assertThat(second.importedCount()).isEqualTo(1);

        String coffee = TransactionFingerprint.of(
                LocalDate.of(2026, 8, 6), Money.of("-2.50", "GBP"), "SAMPLE CAFE 2");
        assertThat(reload()).filteredOn(t -> t.fingerprint().equals(coffee))
                .extracting(Transaction::occurrence)
                .containsExactlyInAnyOrder((short) 1, (short) 2, (short) 3);
        assertThat(transactions.count()).isEqualTo(11);
    }

    @Test
    void reImportingTheSameTransactionsInADifferentFileAddsNothing() {
        service.importStatement(account, fixture());

        // Same transactions, different bytes: only the ignored balance column moved.
        StatementImport second = service.importStatement(account, export(
                "03/08/2026,DEB,00-00-00,00000000,SAMPLE GROCER 1234,24.15,,999.99",
                "07/08/2026,FPO,00-00-00,00000000,A COUNTERPARTY,45.00,,888.88"));

        assertThat(second.rowCount()).isEqualTo(2);
        assertThat(second.importedCount()).isZero();
        assertThat(second.duplicateCount()).isEqualTo(2);
        assertThat(transactions.count()).isEqualTo(10);
    }

    @Test
    void keepsEachProvidersTransactionsToItsOwnAccount() {
        Account monzo = Account.of(user, "monzo", "Monzo Current", AccountType.CURRENT, GBP);
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(monzo);
            entityManager.flush();
        });

        service.importStatement(account, fixture());
        service.importStatement(monzo, monzoFixture());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_id = ?",
                Integer.class, account.id())).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_id = ?",
                Integer.class, monzo.id())).isEqualTo(18);
        assertThat(statementImports.count()).isEqualTo(2);
    }

    @Test
    void aMalformedStatementPersistsNothing() {
        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> service.importStatement(account, export(
                        "03/08/2026,DEB,00-00-00,00000000,FINE,1.00,,1.00",
                        "04/08/2026,DEB,00-00-00,00000000,BROKEN,,,1.00")))
                .withMessageContaining("row 3");

        assertThat(transactions.count()).isZero();
        assertThat(statementImports.count()).isZero();
    }

    // --- helpers ---

    private static byte[] fixture() {
        return read("/bankofscotland/statement.csv");
    }

    private static byte[] monzoFixture() {
        return read("/monzo/statement.csv");
    }

    private static byte[] read(String resource) {
        try (InputStream stream = BankOfScotlandImportTest.class.getResourceAsStream(resource)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] export(String... rows) {
        return (HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private List<Transaction> reload() {
        return transactionTemplate.execute(status -> transactions.findAll());
    }
}
