package com.fapp.analytics;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.money.Money;
import com.fapp.persistence.AbstractPostgresTest;
import com.fapp.statement.StatementImport;
import com.fapp.statement.StatementPeriod;
import com.fapp.transaction.Category;
import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransactionType;
import com.fapp.transaction.Transfer;
import com.fapp.transaction.TransferDetectionSource;
import com.fapp.user.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds exact transactions so every expected figure in an analytics test can be worked
 * out by hand.
 *
 * <p>Deliberately does not go through the import pipeline. Analytics is being tested
 * here, not parsing, and a test whose inputs come from a CSV makes the arithmetic
 * harder to check than the code it is checking.
 */
abstract class AnalyticsTestSupport extends AbstractPostgresTest {

    protected static final Currency GBP = Currency.getInstance("GBP");

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private int sequence;

    @BeforeEach
    void emptyTheDatabase() {
        jdbc.execute("TRUNCATE users CASCADE");
        sequence = 0;
    }

    protected User user(String email) {
        User user = User.of(email, "Test User");
        inTransaction(em -> em.persist(user));
        return user;
    }

    protected Account account(User owner, String provider, String displayName) {
        Account account = Account.of(owner, provider, displayName, AccountType.CURRENT, GBP);
        inTransaction(em -> em.persist(account));
        return account;
    }

    /**
     * Persists one statement import for the account and a transaction for each row.
     *
     * @return the persisted transactions, in the order given
     */
    protected List<Transaction> seed(Account account, Row... rows) {
        List<Transaction> persisted = new ArrayList<>();
        inTransaction(em -> {
            StatementImport statementImport = StatementImport.of(
                    account,
                    uniqueHash(),
                    StatementPeriod.of(earliest(rows), latest(rows)),
                    rows.length, rows.length, 0);
            em.persist(statementImport);

            for (Row row : rows) {
                Transaction transaction = Transaction.builder()
                        .account(account)
                        .statementImport(statementImport)
                        .bookingDate(row.date())
                        .amount(Money.of(row.amount(), "GBP"))
                        .description(row.description())
                        .merchant(row.description())
                        .category(row.category(), row.categorySource())
                        .transactionType(TransactionType.OTHER)
                        .fingerprint(uniqueHash())
                        .build();
                em.persist(transaction);
                persisted.add(transaction);
            }
        });
        return persisted;
    }

    /** Records the two transactions as the legs of one internal transfer. */
    protected void linkAsTransfer(Transaction outgoing, Transaction incoming) {
        inTransaction(em -> em.persist(
                Transfer.of(outgoing, incoming, TransferDetectionSource.RULE)));
    }

    /** One transaction to seed. Amount is signed, in FAPP's convention. */
    protected record Row(LocalDate date, String amount, Category category,
                         com.fapp.transaction.CategorySource categorySource, String description) {

        static Row of(String date, String amount, Category category, String description) {
            return new Row(LocalDate.parse(date), amount, category,
                    com.fapp.transaction.CategorySource.ADAPTER, description);
        }
    }

    protected static Row row(String date, String amount, Category category, String description) {
        return Row.of(date, amount, category, description);
    }

    private static LocalDate earliest(Row... rows) {
        return List.of(rows).stream().map(Row::date).min(LocalDate::compareTo).orElseThrow();
    }

    private static LocalDate latest(Row... rows) {
        return List.of(rows).stream().map(Row::date).max(LocalDate::compareTo).orElseThrow();
    }

    /** A well-formed, unique 64-character lowercase hex value. */
    private String uniqueHash() {
        return String.format("%064x", ++sequence);
    }

    private void inTransaction(java.util.function.Consumer<EntityManager> work) {
        transactionTemplate.executeWithoutResult(status -> {
            work.accept(entityManager);
            entityManager.flush();
        });
    }
}
