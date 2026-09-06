package com.fapp.persistence;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.money.Money;
import com.fapp.statement.StatementImport;
import com.fapp.statement.StatementPeriod;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransactionType;
import com.fapp.transaction.Transfer;
import com.fapp.transaction.TransferDetectionSource;
import com.fapp.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the entities and the migration describe the same schema, which nothing else
 * can: Flyway owns the DDL and {@code ddl-auto} is {@code none}, so Hibernate never
 * validates the mapping. Each test writes through JPA and reads back in a fresh
 * persistence context, so a wrong column name, type or enum length fails here.
 */
class DomainMappingTest extends AbstractPostgresTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE users CASCADE");
    }

    @Test
    void roundTripsTheWholeGraphThroughRealSql() {
        User user = User.of("Owner@Example.com", "  Owner  ");
        Account current = Account.of(user, "monzo", "Monzo Current", AccountType.CURRENT, GBP);
        StatementImport statementImport = StatementImport.of(current, "a".repeat(64),
                StatementPeriod.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), 3, 2, 1);
        Transaction transaction = Transaction.builder()
                .account(current)
                .statementImport(statementImport)
                .bookingDate(LocalDate.of(2026, 3, 14))
                .occurredOn(LocalDate.of(2026, 3, 12))
                .amount(Money.of("-8.50", "GBP"))
                .originalAmount(Money.of("-10.00", "EUR"))
                .description("SNCF PARIS")
                .merchant("SNCF")
                .category(Category.TRANSPORT, CategorySource.RULE)
                .transactionType(TransactionType.CARD_PAYMENT)
                .externalId("tx_00009fZ")
                .occurrence(2)
                .fingerprint("b".repeat(64))
                .build();

        inTransaction(em -> {
            em.persist(user);
            em.persist(current);
            em.persist(statementImport);
            em.persist(transaction);
        });

        UUID transactionId = transaction.id();
        inTransaction(em -> {
            Transaction reloaded = em.find(Transaction.class, transactionId);

            assertThat(reloaded).isNotNull();
            assertThat(reloaded.amount()).isEqualTo(Money.of("-8.50", "GBP"));
            assertThat(reloaded.amount().amount()).isEqualTo(new BigDecimal("-8.5000"));
            assertThat(reloaded.amount().currency()).isEqualTo(GBP);
            assertThat(reloaded.originalAmount()).contains(Money.of("-10.00", "EUR"));
            assertThat(reloaded.bookingDate()).isEqualTo(LocalDate.of(2026, 3, 14));
            assertThat(reloaded.occurredOn()).contains(LocalDate.of(2026, 3, 12));
            assertThat(reloaded.description()).isEqualTo("SNCF PARIS");
            assertThat(reloaded.merchant()).contains("SNCF");
            assertThat(reloaded.category()).isEqualTo(Category.TRANSPORT);
            assertThat(reloaded.categorySource()).isEqualTo(CategorySource.RULE);
            assertThat(reloaded.transactionType()).isEqualTo(TransactionType.CARD_PAYMENT);
            assertThat(reloaded.externalId()).contains("tx_00009fZ");
            assertThat(reloaded.fingerprint()).isEqualTo("b".repeat(64));
            assertThat(reloaded.fingerprintVersion()).isEqualTo(Transaction.CURRENT_FINGERPRINT_VERSION);
            assertThat(reloaded.occurrence()).isEqualTo((short) 2);
            assertThat(reloaded.createdAt()).isNotNull();
            assertThat(reloaded.userId()).isEqualTo(user.id());
            assertThat(reloaded.accountId()).isEqualTo(current.id());

            // Navigating the associations proves the join columns are right too.
            assertThat(reloaded.account().provider()).isEqualTo("monzo");
            assertThat(reloaded.account().currency()).isEqualTo(GBP);
            assertThat(reloaded.account().user().email()).isEqualTo("owner@example.com");
            assertThat(reloaded.account().user().displayName()).isEqualTo("Owner");
            assertThat(reloaded.statementImport().contentHash()).isEqualTo("a".repeat(64));
            assertThat(reloaded.statementImport().period().start()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(reloaded.statementImport().period().end()).isEqualTo(LocalDate.of(2026, 3, 31));
            assertThat(reloaded.statementImport().rowCount()).isEqualTo(3);
            assertThat(reloaded.statementImport().importedCount()).isEqualTo(2);
            assertThat(reloaded.statementImport().duplicateCount()).isEqualTo(1);
        });
    }

    @Test
    void leavesTheForeignCurrencyPairAbsentRatherThanHalfPopulated() {
        User user = User.of("domestic@example.com", "Domestic");
        Account account = Account.of(user, "bank_of_scotland", "BoS Current", AccountType.CURRENT, GBP);
        StatementImport statementImport = StatementImport.of(account, "c".repeat(64),
                StatementPeriod.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), 1, 1, 0);
        Transaction transaction = Transaction.builder()
                .account(account)
                .statementImport(statementImport)
                .bookingDate(LocalDate.of(2026, 3, 2))
                .amount(Money.of("-42.00", "GBP"))
                .description("TESCO STORES 3421")
                .transactionType(TransactionType.CARD_PAYMENT)
                .fingerprint("d".repeat(64))
                .build();

        inTransaction(em -> {
            em.persist(user);
            em.persist(account);
            em.persist(statementImport);
            em.persist(transaction);
        });

        UUID id = transaction.id();
        inTransaction(em -> {
            Transaction reloaded = em.find(Transaction.class, id);

            // The trap this guards: an embeddable whose columns are all null can read
            // back as a non-null instance full of nulls.
            assertThat(reloaded.originalAmount()).isEmpty();
            assertThat(reloaded.occurredOn()).isEmpty();
            assertThat(reloaded.externalId()).isEmpty();
            assertThat(reloaded.merchant()).isEmpty();
        });

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE original_amount IS NULL AND original_currency IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(EUR.getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void persistsAnInternalTransferAcrossTwoBanks() {
        User user = User.of("multibank@example.com", "Multi Bank");
        Account bos = Account.of(user, "bank_of_scotland", "BoS Current", AccountType.CURRENT, GBP);
        Account pot = Account.of(user, "monzo", "Holiday Pot", AccountType.SAVINGS, GBP);
        StatementImport bosImport = StatementImport.of(bos, "e".repeat(64), march(), 1, 1, 0);
        StatementImport potImport = StatementImport.of(pot, "f".repeat(64), march(), 1, 1, 0);

        Transaction outgoing = leg(bos, bosImport, "-500.00", "TRANSFER TO MONZO", "1".repeat(64));
        Transaction incoming = leg(pot, potImport, "500.00", "TRANSFER FROM BOS", "2".repeat(64));
        Transfer transfer = Transfer.of(outgoing, incoming, TransferDetectionSource.RULE);

        inTransaction(em -> {
            em.persist(user);
            em.persist(bos);
            em.persist(pot);
            em.persist(bosImport);
            em.persist(potImport);
            em.persist(outgoing);
            em.persist(incoming);
            em.persist(transfer);
        });

        UUID transferId = transfer.id();
        inTransaction(em -> {
            Transfer reloaded = em.find(Transfer.class, transferId);

            assertThat(reloaded).isNotNull();
            assertThat(reloaded.userId()).isEqualTo(user.id());
            assertThat(reloaded.detectionSource()).isEqualTo(TransferDetectionSource.RULE);
            assertThat(reloaded.detectedAt()).isNotNull();
            assertThat(reloaded.outgoing().amount()).isEqualTo(Money.of("-500.00", "GBP"));
            assertThat(reloaded.incoming().amount()).isEqualTo(Money.of("500.00", "GBP"));
            // Two banks, one user, and the legs sit in different accounts.
            assertThat(reloaded.outgoing().account().provider()).isEqualTo("bank_of_scotland");
            assertThat(reloaded.incoming().account().provider()).isEqualTo("monzo");
            assertThat(reloaded.outgoing().accountId()).isNotEqualTo(reloaded.incoming().accountId());
        });
    }

    @Test
    void updatesTheCategoryWithoutTouchingTheMoney() {
        User user = User.of("recategorise@example.com", "Recategoriser");
        Account account = Account.of(user, "monzo", "Monzo Current", AccountType.CURRENT, GBP);
        StatementImport statementImport = StatementImport.of(account, "3".repeat(64), march(), 1, 1, 0);
        Transaction transaction = leg(account, statementImport, "-19.99", "SPOTIFY", "4".repeat(64));

        inTransaction(em -> {
            em.persist(user);
            em.persist(account);
            em.persist(statementImport);
            em.persist(transaction);
        });

        UUID id = transaction.id();
        inTransaction(em -> em.find(Transaction.class, id)
                .recategorise(Category.SUBSCRIPTIONS, CategorySource.USER));

        inTransaction(em -> {
            Transaction reloaded = em.find(Transaction.class, id);

            assertThat(reloaded.category()).isEqualTo(Category.SUBSCRIPTIONS);
            assertThat(reloaded.categorySource()).isEqualTo(CategorySource.USER);
            assertThat(reloaded.amount()).isEqualTo(Money.of("-19.99", "GBP"));
            assertThat(reloaded.updatedAt()).isAfterOrEqualTo(reloaded.createdAt());
        });
    }

    @Test
    void storesEveryEnumValueTheCheckConstraintsAllow() {
        User user = User.of("enums@example.com", "Enums");
        Account account = Account.of(user, "monzo", "Monzo Current", AccountType.CURRENT, GBP);
        StatementImport statementImport = StatementImport.of(account, "5".repeat(64), march(),
                Category.values().length, Category.values().length, 0);

        inTransaction(em -> {
            em.persist(user);
            em.persist(account);
            em.persist(statementImport);
            int index = 0;
            for (Category category : Category.values()) {
                TransactionType type = TransactionType.values()[index % TransactionType.values().length];
                CategorySource source = CategorySource.values()[index % CategorySource.values().length];
                em.persist(Transaction.builder()
                        .account(account)
                        .statementImport(statementImport)
                        .bookingDate(LocalDate.of(2026, 3, 1))
                        .amount(Money.of("-1.00", "GBP"))
                        .description("ENUM " + category)
                        .category(category, source)
                        .transactionType(type)
                        .fingerprint(String.format("%064x", index))
                        .build());
                index++;
            }
        });

        assertThat(jdbc.queryForObject("SELECT count(DISTINCT category) FROM transactions", Integer.class))
                .isEqualTo(Category.values().length);
    }

    @Test
    void storesEveryAccountTypeIncludingMonzoPotsAsSavings() {
        User user = User.of("types@example.com", "Types");

        inTransaction(em -> {
            em.persist(user);
            for (AccountType type : AccountType.values()) {
                em.persist(Account.of(user, "monzo", "Monzo " + type, type, GBP));
            }
        });

        assertThat(jdbc.queryForList("SELECT account_type FROM accounts ORDER BY account_type", String.class))
                .containsExactly("CREDIT_CARD", "CURRENT", "OTHER", "SAVINGS");
    }

    private static StatementPeriod march() {
        return StatementPeriod.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    }

    private static Transaction leg(Account account, StatementImport statementImport,
                                   String amount, String description, String fingerprint) {
        return Transaction.builder()
                .account(account)
                .statementImport(statementImport)
                .bookingDate(LocalDate.of(2026, 3, 10))
                .amount(Money.of(amount, account.currency().getCurrencyCode()))
                .description(description)
                .transactionType(TransactionType.BANK_TRANSFER)
                .fingerprint(fingerprint)
                .build();
    }

    /**
     * Runs work in its own transaction. Each one gets a fresh persistence context, so
     * reading back afterwards really does go to the database rather than to a cache.
     */
    private void inTransaction(Consumer<EntityManager> work) {
        transactionTemplate.executeWithoutResult(status -> {
            work.accept(entityManager);
            entityManager.flush();
        });
    }
}
