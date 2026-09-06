package com.fapp.statement;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.AnalyticsScope;
import com.fapp.analytics.AnalyticsService;
import com.fapp.analytics.CategorySummary;
import com.fapp.persistence.AbstractPostgresTest;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.Transaction;
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
 * Categorisation reached through the real import, which is the only way it runs.
 *
 * <p>The Bank of Scotland export has no category column, so before this every row
 * arrived uncategorised. These cases use merchants written the way that bank writes
 * them, and check both the category and where the decision is recorded as having come
 * from.
 */
class TransactionCategorisationOnImportTest extends AbstractPostgresTest {

    private static final String BOS_HEADER = "Transaction Date,Transaction Type,Sort Code,Account Number,"
            + "Transaction Description,Debit Amount,Credit Amount,Balance";

    private static final String MONZO_HEADER =
            "Transaction ID,Date,Time,Type,Name,Emoji,Category,Amount,Currency,Local amount,"
                    + "Local currency,Notes and #tags,Address,Receipt,Description,Category split,"
                    + "Money Out,Money In";

    private static final AnalyticsPeriod AUGUST =
            new AnalyticsPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01"));

    @Autowired
    private StatementImportService imports;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private Account bos;
    private Account monzo;

    @BeforeEach
    void anAccountAtEachBank() {
        jdbc.execute("TRUNCATE users CASCADE");
        owner = User.of("categorised@example.com", "Owner");
        bos = Account.of(owner, "bank_of_scotland", "BoS Current", AccountType.CURRENT,
                Currency.getInstance("GBP"));
        monzo = Account.of(owner, "monzo", "Monzo Current", AccountType.CURRENT,
                Currency.getInstance("GBP"));
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(owner);
            entityManager.persist(bos);
            entityManager.persist(monzo);
            entityManager.flush();
        });
    }

    @Test
    void categorisesBankOfScotlandSpendingByMerchant() {
        imports.importStatement(bos, bankOfScotland(
                "03/08/2026,DEB,00-00-00,00000000,LIDL GB SAMPLETON,24.15,,1975.85",
                "04/08/2026,DEB,00-00-00,00000000,WAGAMAMA SAMPLETON,32.40,,1943.45",
                "05/08/2026,DEB,00-00-00,00000000,DOMINO'S PIZZA 1234,18.99,,1924.46",
                "06/08/2026,DEB,00-00-00,00000000,SPORTS DIRECT 421,64.00,,1860.46",
                "07/08/2026,DEB,00-00-00,00000000,AMAZON.CO.UK*A12BC34D,12.99,,1847.47",
                "08/08/2026,DEB,00-00-00,00000000,APPLE.COM/BILL,8.99,,1838.48",
                "09/08/2026,DEB,00-00-00,00000000,MARKS&SPENCER PLC,45.50,,1792.98",
                "10/08/2026,DEB,00-00-00,00000000,MYPROTEIN,28.00,,1764.98"));

        assertThat(categoryOf("LIDL GB SAMPLETON")).isEqualTo(Category.GROCERIES);
        assertThat(categoryOf("WAGAMAMA SAMPLETON")).isEqualTo(Category.RESTAURANTS);
        assertThat(categoryOf("DOMINO'S PIZZA 1234")).isEqualTo(Category.RESTAURANTS);
        assertThat(categoryOf("SPORTS DIRECT 421")).isEqualTo(Category.SHOPPING);
        assertThat(categoryOf("AMAZON.CO.UK*A12BC34D")).isEqualTo(Category.SHOPPING);
        assertThat(categoryOf("APPLE.COM/BILL")).isEqualTo(Category.SUBSCRIPTIONS);
        assertThat(categoryOf("MARKS&SPENCER PLC")).isEqualTo(Category.SHOPPING);
        assertThat(categoryOf("MYPROTEIN")).isEqualTo(Category.SHOPPING);
    }

    @Test
    void recordsThatAMerchantRuleMadeTheDecision() {
        imports.importStatement(bos,
                bankOfScotland("03/08/2026,DEB,00-00-00,00000000,LIDL GB SAMPLETON,24.15,,1975.85"));

        Transaction stored = only();
        assertThat(stored.category()).isEqualTo(Category.GROCERIES);
        // RULE, not ADAPTER: the bank said nothing, a rule of ours decided.
        assertThat(stored.categorySource()).isEqualTo(CategorySource.RULE);
    }

    @Test
    void leavesAMerchantWithNoRuleUncategorisedAndVisiblySo() {
        imports.importStatement(bos, bankOfScotland(
                "03/08/2026,DEB,00-00-00,00000000,SAMPLE GROCER 1234,24.15,,1975.85",
                "04/08/2026,DEB,00-00-00,00000000,SOME SHOP NOBODY WROTE A RULE FOR,10.00,,1965.85"));

        assertThat(reload()).hasSize(2).allSatisfy(stored -> {
            assertThat(stored.category()).isEqualTo(Category.UNCATEGORISED);
            // Still the adapter's decision: no rule claimed it, so nothing was overridden.
            assertThat(stored.categorySource()).isEqualTo(CategorySource.DEFAULT);
        });
    }

    @Test
    void keepsTheCategoryMonzoItselfSupplied() {
        imports.importStatement(monzo, monzo(
                "tx_1,03/08/2026,08:00:00,Card payment,Greenfield Grocers,,Groceries,-24.15,GBP,-24.15,GBP,,,,GREENFIELD GROCERS,,24.15,"));

        Transaction stored = only();
        assertThat(stored.category()).isEqualTo(Category.GROCERIES);
        assertThat(stored.categorySource()).isEqualTo(CategorySource.ADAPTER);
    }

    @Test
    void doesNotLetAMerchantRuleOverrideACategoryTheBankSupplied() {
        // Monzo filed a Lidl shop under eating out. Wrong or not, the bank's own filing
        // is better evidence than a merchant name, so it stands.
        imports.importStatement(monzo, monzo(
                "tx_2,03/08/2026,08:00:00,Card payment,LIDL GB SAMPLETON,,Eating out,-24.15,GBP,-24.15,GBP,,,,LIDL GB SAMPLETON,,24.15,"));

        Transaction stored = only();
        assertThat(stored.category()).isEqualTo(Category.RESTAURANTS);
        assertThat(stored.categorySource()).isEqualTo(CategorySource.ADAPTER);
    }

    @Test
    void fillsInAMonzoRowWhoseOwnCategoryMappedToNothingUseful() {
        // Monzo's "General" maps to UNCATEGORISED, which is the bank saying nothing
        // usable rather than saying no category. Rules are bank-agnostic and fill it.
        imports.importStatement(monzo, monzo(
                "tx_3,03/08/2026,08:00:00,Card payment,Wagamama,,General,-32.40,GBP,-32.40,GBP,,,,WAGAMAMA SAMPLETON,,32.40,"));

        Transaction stored = only();
        assertThat(stored.category()).isEqualTo(Category.RESTAURANTS);
        assertThat(stored.categorySource()).isEqualTo(CategorySource.RULE);
    }

    @Test
    void feedsTheCategoryBreakdownAcrossBothBanks() {
        imports.importStatement(bos, bankOfScotland(
                "03/08/2026,DEB,00-00-00,00000000,LIDL GB SAMPLETON,24.15,,1975.85",
                "04/08/2026,DEB,00-00-00,00000000,WAGAMAMA SAMPLETON,32.40,,1943.45"));
        imports.importStatement(monzo, monzo(
                "tx_4,05/08/2026,08:00:00,Card payment,Greenfield Grocers,,Groceries,-10.00,GBP,-10.00,GBP,,,,GREENFIELD GROCERS,,10.00,"));

        List<CategorySummary> categories =
                analytics.summariseByCategory(AnalyticsScope.ofUser(owner.id()), AUGUST);

        // Groceries totals the Lidl shop from one bank and the Monzo-categorised one from
        // the other: 24.15 + 10.00.
        assertThat(categories).extracting(CategorySummary::category)
                .containsExactly(Category.GROCERIES, Category.RESTAURANTS);
        assertThat(categories.get(0).expenditure()).isEqualByComparingTo("34.15");
        assertThat(categories.get(0).transactionCount()).isEqualTo(2);
        assertThat(categories.get(1).expenditure()).isEqualByComparingTo("32.40");
        assertThat(categories).extracting(CategorySummary::category)
                .doesNotContain(Category.UNCATEGORISED);
    }

    @Test
    void changesNothingAboutHowDuplicatesAreRecognised() {
        byte[] statement = bankOfScotland(
                "03/08/2026,DEB,00-00-00,00000000,LIDL GB SAMPLETON,24.15,,1975.85");
        imports.importStatement(bos, statement);

        assertThatExceptionOfType(DuplicateStatementException.class)
                .isThrownBy(() -> imports.importStatement(bos, statement));

        // The same transaction in a different file is still recognised by fingerprint,
        // which never included the category.
        StatementImport again = imports.importStatement(bos,
                bankOfScotland("03/08/2026,DEB,00-00-00,00000000,LIDL GB SAMPLETON,24.15,,9999.99"));

        assertThat(again.duplicateCount()).isEqualTo(1);
        assertThat(again.importedCount()).isZero();
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(only().category()).isEqualTo(Category.GROCERIES);
    }

    @Test
    void changesNothingAboutTransferDetection() {
        imports.importStatement(bos,
                bankOfScotland("10/08/2026,FPO,00-00-00,00000000,SAMPLE SAVINGS TRANSFER,250.00,,1750.00"));
        imports.importStatement(monzo, monzo(
                "tx_5,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,250.00,GBP,250.00,GBP,,,,Holiday Pot,,,250.00"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers", Integer.class)).isEqualTo(1);
        // Both legs still drop out of income and expenditure.
        assertThat(analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST).transactionCount())
                .isZero();
    }

    @Test
    void categorisesTheSanitisedBankOfScotlandFixtureNoDifferently() {
        // The existing fixture's merchants are all fictional, so nothing matches a rule
        // and the import behaves exactly as it did before categorisation existed.
        StatementImport result = imports.importStatement(bos, fixture("/bankofscotland/statement.csv"));

        assertThat(result.importedCount()).isEqualTo(10);
        assertThat(reload()).allSatisfy(stored -> {
            assertThat(stored.category()).isEqualTo(Category.UNCATEGORISED);
            assertThat(stored.categorySource()).isEqualTo(CategorySource.DEFAULT);
        });
    }

    // --- helpers ---

    private Category categoryOf(String description) {
        return reload().stream()
                .filter(stored -> stored.description().equals(description))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no transaction described as " + description))
                .category();
    }

    private Transaction only() {
        List<Transaction> stored = reload();
        assertThat(stored).hasSize(1);
        return stored.get(0);
    }

    private List<Transaction> reload() {
        return transactionTemplate.execute(status -> transactions.findAll());
    }

    private static byte[] bankOfScotland(String... rows) {
        return (BOS_HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] monzo(String... rows) {
        return (MONZO_HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] fixture(String resource) {
        try (InputStream stream = TransactionCategorisationOnImportTest.class.getResourceAsStream(resource)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
