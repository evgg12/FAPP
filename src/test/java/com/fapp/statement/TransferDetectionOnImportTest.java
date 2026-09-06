package com.fapp.statement;

import com.fapp.account.Account;
import com.fapp.account.AccountType;
import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.AnalyticsScope;
import com.fapp.analytics.AnalyticsService;
import com.fapp.analytics.FinancialSummary;
import com.fapp.persistence.AbstractPostgresTest;
import com.fapp.user.User;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Transfer detection reached through the real import flow, which is the only way it is
 * triggered in production.
 *
 * <p>The point being proved is the awkward part of the timing: one statement covers one
 * account, so the two legs of a move between accounts can never arrive together. The
 * first import has nothing to match and the second completes the link, however long
 * afterwards it happens.
 */
class TransferDetectionOnImportTest extends AbstractPostgresTest {

    private static final String MONZO_HEADER =
            "Transaction ID,Date,Time,Type,Name,Emoji,Category,Amount,Currency,Local amount,"
                    + "Local currency,Notes and #tags,Address,Receipt,Description,Category split,"
                    + "Money Out,Money In";

    private static final AnalyticsPeriod AUGUST =
            new AnalyticsPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01"));

    @Autowired
    private StatementImportService imports;

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private Account current;
    private Account pot;

    @BeforeEach
    void twoMonzoAccounts() {
        jdbc.execute("TRUNCATE users CASCADE");
        owner = User.of("importer@example.com", "Importer");
        current = Account.of(owner, "monzo", "Monzo Current", AccountType.CURRENT,
                Currency.getInstance("GBP"));
        pot = Account.of(owner, "monzo", "Holiday Pot", AccountType.SAVINGS,
                Currency.getInstance("GBP"));
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(owner);
            entityManager.persist(current);
            entityManager.persist(pot);
            entityManager.flush();
        });
    }

    @Test
    void recognisesTheTransferWhenTheSecondAccountIsImported() {
        // First statement: the money leaves the current account and there is nothing yet
        // to match it against, so it counts as expenditure.
        imports.importStatement(current, monzo(
                "tx_out_1,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,-250.00,GBP,-250.00,GBP,,,,Holiday Pot,,250.00,"));

        assertThat(transferCount()).isZero();
        FinancialSummary afterFirst = analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST);
        assertThat(afterFirst.expenditure()).isEqualByComparingTo("250.00");

        // Second statement completes the pair, and both legs leave the totals.
        imports.importStatement(pot, monzo(
                "tx_in_1,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,250.00,GBP,250.00,GBP,,,,Holiday Pot,,,250.00"));

        assertThat(transferCount()).isEqualTo(1);
        FinancialSummary afterSecond = analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST);
        assertThat(afterSecond.income()).isEqualByComparingTo("0");
        assertThat(afterSecond.expenditure()).isEqualByComparingTo("0");
        assertThat(afterSecond.transactionCount()).isZero();
    }

    @Test
    void keepsOrdinarySpendingInTheTotalsAlongsideADetectedTransfer() {
        imports.importStatement(current, monzo(
                "tx_out_2,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,-250.00,GBP,-250.00,GBP,,,,Holiday Pot,,250.00,",
                "tx_shop,12/08/2026,10:00:00,Card payment,Sample Grocer,,Groceries,-24.15,GBP,-24.15,GBP,,,,SAMPLE GROCER,,24.15,",
                "tx_pay,05/08/2026,03:00:00,Bacs (Direct Credit),Sample Employer,,Income,1500.00,GBP,1500.00,GBP,,,,SALARY,,,1500.00"));
        imports.importStatement(pot, monzo(
                "tx_in_2,11/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,250.00,GBP,250.00,GBP,,,,Holiday Pot,,,250.00"));

        assertThat(transferCount()).isEqualTo(1);

        // The salary and the groceries are untouched; only the transfer legs are gone.
        FinancialSummary summary = analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST);
        assertThat(summary.income()).isEqualByComparingTo("1500.00");
        assertThat(summary.expenditure()).isEqualByComparingTo("24.15");
        assertThat(summary.netSavings()).isEqualByComparingTo("1475.85");
        assertThat(summary.transactionCount()).isEqualTo(2);
    }

    @Test
    void leavesAPaymentToSomebodyElseAsExpenditure() {
        // Monzo files a payment to a friend under the same transfer category as a move to
        // a pot. Without a matching leg on another of the user's accounts, it is spending.
        imports.importStatement(current, monzo(
                "tx_friend,10/08/2026,09:00:00,Faster payment,A Counterparty,,Transfers,-45.00,GBP,-45.00,GBP,,,,DINNER SPLIT,,45.00,"));

        assertThat(transferCount()).isZero();
        assertThat(analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST).expenditure())
                .isEqualByComparingTo("45.00");
    }

    @Test
    void addsNoFurtherTransferWhenAnOverlappingStatementIsImportedAgain() {
        imports.importStatement(current, monzo(
                "tx_out_3,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,-250.00,GBP,-250.00,GBP,,,,Holiday Pot,,250.00,"));
        imports.importStatement(pot, monzo(
                "tx_in_3,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,250.00,GBP,250.00,GBP,,,,Holiday Pot,,,250.00"));
        assertThat(transferCount()).isEqualTo(1);

        // A later download of the same account, different bytes, same transaction: the
        // row is recognised as already held and no second link appears.
        StatementImport again = imports.importStatement(pot, monzo(
                "tx_in_3,10/08/2026,09:30:00,Pot transfer,Holiday Pot,,Transfers,250.00,GBP,250.00,GBP,,,,Holiday Pot,,,250.00"));

        assertThat(again.duplicateCount()).isEqualTo(1);
        assertThat(again.importedCount()).isZero();
        assertThat(transferCount()).isEqualTo(1);
    }

    @Test
    void detectsNothingForAUserWithASingleAccount() {
        User solo = User.of("solo@example.com", "Solo");
        Account only = Account.of(solo, "monzo", "Monzo Current", AccountType.CURRENT,
                Currency.getInstance("GBP"));
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(solo);
            entityManager.persist(only);
            entityManager.flush();
        });

        imports.importStatement(only, monzo(
                "tx_solo,10/08/2026,09:00:00,Pot transfer,Somewhere,,Transfers,-250.00,GBP,-250.00,GBP,,,,SOMEWHERE,,250.00,"));

        assertThat(transferCount()).isZero();
        assertThat(analytics.summarise(AnalyticsScope.ofUser(solo.id()), AUGUST).expenditure())
                .isEqualByComparingTo("250.00");
    }

    @Test
    void leavesAMalformedStatementWithNeitherTransactionsNorTransfers() {
        imports.importStatement(current, monzo(
                "tx_out_4,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,-250.00,GBP,-250.00,GBP,,,,Holiday Pot,,250.00,"));

        assertThatExceptionOfType(StatementParseException.class)
                .isThrownBy(() -> imports.importStatement(pot, monzo(
                        "tx_in_4,10/08/2026,09:00:00,Pot transfer,Holiday Pot,,Transfers,250.00,GBP,250.00,GBP,,,,Holiday Pot,,,250.00",
                        "tx_bad,11/08/2026,09:00:00,Card payment,X,,General,not-a-number,GBP,-1.00,GBP,,,,BROKEN,,1.00,")));

        // The whole import rolled back, so the leg that would have completed the transfer
        // is not there and neither is a half-recorded link.
        assertThat(transferCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class)).isEqualTo(1);
    }

    private static byte[] monzo(String... rows) {
        return (MONZO_HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private int transferCount() {
        return jdbc.queryForObject("SELECT count(*) FROM transfers", Integer.class);
    }
}
