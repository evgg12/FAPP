package com.fapp.transaction;

import com.fapp.account.Account;
import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.AnalyticsScope;
import com.fapp.analytics.AnalyticsService;
import com.fapp.analytics.FinancialSummary;
import com.fapp.persistence.SeededDomainTest;
import com.fapp.user.User;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every case is hand-built: exact transactions in, an exact expected link out.
 *
 * <p>Detection is deliberately hard to satisfy, so most of these assert that a pair is
 * <em>not</em> linked. A missed transfer overstates a total slightly; a wrong one erases
 * a real payment from the user's spending, so the tests weigh accordingly.
 */
class TransferDetectionServiceTest extends SeededDomainTest {

    private static final AnalyticsPeriod AUGUST =
            new AnalyticsPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01"));

    @Autowired
    private TransferDetectionService detection;

    @Autowired
    private AnalyticsService analytics;

    private User owner;
    private Account current;
    private Account savings;

    @BeforeEach
    void twoAccountsForOneUser() {
        owner = user("owner@example.com");
        current = account(owner, "monzo", "Monzo Current");
        savings = account(owner, "bank_of_scotland", "BoS Savings");
    }

    // --- pairs that are transfers ---

    @Test
    void linksAnEqualAndOppositePairBookedOnTheSameDay() {
        Transaction out = only(seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "TO SAVINGS")));
        Transaction in = only(seed(savings, row("2026-08-10", "500.00", Category.TRANSFER, "FROM CURRENT")));

        List<Transfer> recorded = detection.detect(List.of(in));

        assertThat(recorded).singleElement().satisfies(transfer -> {
            assertThat(transfer.outgoing().id()).isEqualTo(out.id());
            assertThat(transfer.incoming().id()).isEqualTo(in.id());
            assertThat(transfer.userId()).isEqualTo(owner.id());
            assertThat(transfer.detectionSource()).isEqualTo(TransferDetectionSource.RULE);
        });
        assertThat(transferCount()).isEqualTo(1);
    }

    @Test
    void linksAPairBookedADayApartInEitherDirection() {
        // Sent late one day, credited the next: the same movement, two settlement dates.
        Transaction out = only(seed(current, row("2026-08-10", "-250.00", Category.BILLS, "MOVE OUT")));
        Transaction in = only(seed(savings, row("2026-08-11", "250.00", Category.BILLS, "MOVE IN")));

        assertThat(detection.detect(List.of(in))).hasSize(1);
        assertThat(transferCount()).isEqualTo(1);

        User other = user("other@example.com");
        Account a = account(other, "monzo", "A");
        Account b = account(other, "bank_of_scotland", "B");
        Transaction laterOut = only(seed(a, row("2026-08-12", "-99.00", Category.BILLS, "OUT")));
        Transaction earlierIn = only(seed(b, row("2026-08-11", "99.00", Category.BILLS, "IN")));

        assertThat(detection.detect(List.of(laterOut))).hasSize(1);
        assertThat(out.id()).isNotEqualTo(laterOut.id());
        assertThat(earlierIn.id()).isNotNull();
    }

    @Test
    void findsThePartnerHoweverMuchLaterItsAccountIsImported() {
        // The first statement arrives with nothing to match; the second completes it.
        Transaction out = only(seed(current, row("2026-08-10", "-320.00", Category.SHOPPING, "OUT")));
        assertThat(detection.detect(List.of(out))).isEmpty();
        assertThat(transferCount()).isZero();

        Transaction in = only(seed(savings, row("2026-08-10", "320.00", Category.SHOPPING, "IN")));
        assertThat(detection.detect(List.of(in))).hasSize(1);
        assertThat(transferCount()).isEqualTo(1);
    }

    // --- pairs that are not transfers ---

    @Test
    void leavesAPairWithDifferentAmountsAlone() {
        seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "OUT"));
        Transaction in = only(seed(savings, row("2026-08-10", "499.99", Category.TRANSFER, "IN")));

        assertThat(detection.detect(List.of(in))).isEmpty();
        assertThat(transferCount()).isZero();
    }

    @Test
    void leavesTwoPaymentsInTheSameDirectionAlone() {
        seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "OUT"));
        Transaction alsoOut = only(seed(savings, row("2026-08-10", "-500.00", Category.TRANSFER, "ALSO OUT")));

        assertThat(detection.detect(List.of(alsoOut))).isEmpty();
        assertThat(transferCount()).isZero();
    }

    @Test
    void leavesAPairInDifferentCurrenciesAlone() {
        // Verifying a cross-currency transfer needs an exchange rate, so it is out of
        // scope and must not be guessed at.
        Account euros = account(owner, "monzo", "Euro Pot", Currency.getInstance("EUR"));
        seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "OUT"));
        Transaction in = only(seed(euros, row("2026-08-10", "500.00", Category.TRANSFER, "IN")));

        assertThat(detection.detect(List.of(in))).isEmpty();
        assertThat(transferCount()).isZero();
    }

    @Test
    void leavesAnEqualAndOppositePairOnOneAccountAlone() {
        // A payment and its refund on the same account is not money moving between
        // accounts, whatever it looks like arithmetically.
        List<Transaction> both = seed(current,
                row("2026-08-10", "-500.00", Category.SHOPPING, "PAYMENT"),
                row("2026-08-10", "500.00", Category.SHOPPING, "REFUND"));

        assertThat(detection.detect(both)).isEmpty();
        assertThat(transferCount()).isZero();
    }

    @Test
    void leavesAPairBelongingToTwoDifferentUsersAlone() {
        User stranger = user("stranger@example.com");
        Account theirs = account(stranger, "monzo", "Their Monzo");
        seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "OUT"));
        Transaction theirIn = only(seed(theirs, row("2026-08-10", "500.00", Category.TRANSFER, "IN")));

        assertThat(detection.detect(List.of(theirIn))).isEmpty();
        assertThat(transferCount()).isZero();
    }

    @Test
    void leavesAPairBookedTwoDaysApartAlone() {
        seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "OUT"));
        Transaction in = only(seed(savings, row("2026-08-12", "500.00", Category.TRANSFER, "IN")));

        assertThat(detection.detect(List.of(in))).isEmpty();
        assertThat(transferCount()).isZero();
    }

    @Test
    void doesNotTreatTheTransferCategoryAsProofOnItsOwn() {
        // 50.00 sent to a friend is filed exactly like a move to savings. Without an
        // equal and opposite counterpart it stays what it is: money gone.
        Transaction toAFriend =
                only(seed(current, row("2026-08-10", "-50.00", Category.TRANSFER, "A FRIEND")));

        assertThat(detection.detect(List.of(toAFriend))).isEmpty();
        assertThat(transferCount()).isZero();

        // And it is still counted as expenditure, because it was.
        assertThat(analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST).expenditure())
                .isEqualByComparingTo("50.00");
    }

    @Test
    void leavesOrdinaryPaymentsAsIncomeAndExpenditure() {
        seed(current,
                row("2026-08-03", "-24.15", Category.GROCERIES, "GROCER"),
                row("2026-08-05", "2000.00", Category.INCOME, "SALARY"));
        seed(savings, row("2026-08-06", "-88.40", Category.BILLS, "POWER"));

        assertThat(detection.detect(List.of())).isEmpty();
        assertThat(transferCount()).isZero();

        FinancialSummary summary = analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST);
        assertThat(summary.income()).isEqualByComparingTo("2000.00");
        assertThat(summary.expenditure()).isEqualByComparingTo("112.55");
        assertThat(summary.transactionCount()).isEqualTo(3);
    }

    // --- one transaction, one transfer ---

    @Test
    void recordsOneTransferRatherThanSeveralWhenMoreThanOnePartnerWouldFit() {
        Account third = account(owner, "monzo", "Third Account");
        seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "OUT"));
        seed(third, row("2026-08-10", "-500.00", Category.TRANSFER, "ALSO OUT"));
        Transaction in = only(seed(savings, row("2026-08-10", "500.00", Category.TRANSFER, "IN")));

        List<Transfer> recorded = detection.detect(List.of(in));

        // Two outgoing legs would each fit; exactly one link is made, and the incoming
        // leg is used once.
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).incoming().id()).isEqualTo(in.id());
        assertThat(transferCount()).isEqualTo(1);
    }

    @Test
    void choosesTheSamePartnerEveryTimeForTheSameData() {
        Account third = account(owner, "monzo", "Third Account");
        seed(current, row("2026-08-10", "-75.00", Category.TRANSFER, "OUT"));
        seed(third, row("2026-08-10", "-75.00", Category.TRANSFER, "ALSO OUT"));
        Transaction in = only(seed(savings, row("2026-08-10", "75.00", Category.TRANSFER, "IN")));

        java.util.UUID chosen = detection.detect(List.of(in)).get(0).outgoing().id();

        // Same inputs, same choice: the tie is broken by date then id, not by chance.
        jdbc.execute("DELETE FROM transfers");
        assertThat(detection.detect(List.of(in)).get(0).outgoing().id()).isEqualTo(chosen);
    }

    @Test
    void prefersTheCounterpartBookedOnTheSameDayOverOneADayAway() {
        Account third = account(owner, "monzo", "Third Account");
        Transaction sameDay = only(seed(current, row("2026-08-10", "-60.00", Category.TRANSFER, "SAME DAY")));
        seed(third, row("2026-08-09", "-60.00", Category.TRANSFER, "DAY BEFORE"));
        Transaction in = only(seed(savings, row("2026-08-10", "60.00", Category.TRANSFER, "IN")));

        assertThat(detection.detect(List.of(in)).get(0).outgoing().id()).isEqualTo(sameDay.id());
    }

    @Test
    void pairsRepeatedIdenticalTransactionsOffOneForOne() {
        // Two identical moves out and two identical moves in are two transfers, not four
        // and not a tangle: each transaction is used exactly once.
        List<Transaction> outgoing = seed(current,
                row("2026-08-10", "-40.00", Category.TRANSFER, "OUT"),
                row("2026-08-10", "-40.00", Category.TRANSFER, "OUT"));
        List<Transaction> incoming = seed(savings,
                row("2026-08-10", "40.00", Category.TRANSFER, "IN"),
                row("2026-08-10", "40.00", Category.TRANSFER, "IN"));

        List<Transfer> recorded = detection.detect(incoming);

        assertThat(recorded).hasSize(2);
        assertThat(transferCount()).isEqualTo(2);
        assertThat(recorded).extracting(transfer -> transfer.outgoing().id())
                .containsExactlyInAnyOrder(outgoing.get(0).id(), outgoing.get(1).id());
        assertThat(recorded).extracting(transfer -> transfer.incoming().id())
                .containsExactlyInAnyOrder(incoming.get(0).id(), incoming.get(1).id());
    }

    @Test
    void leavesTheUnmatchedRepeatAsAnOrdinaryPaymentWhenTheCountsDiffer() {
        seed(current,
                row("2026-08-10", "-40.00", Category.TRANSFER, "OUT"),
                row("2026-08-10", "-40.00", Category.TRANSFER, "OUT"));
        List<Transaction> incoming = seed(savings, row("2026-08-10", "40.00", Category.TRANSFER, "IN"));

        assertThat(detection.detect(incoming)).hasSize(1);
        assertThat(transferCount()).isEqualTo(1);

        // One move out is still unaccounted for, so it remains expenditure.
        assertThat(analytics.summarise(AnalyticsScope.ofUser(owner.id()), AUGUST).expenditure())
                .isEqualByComparingTo("40.00");
    }

    // --- running it again ---

    @Test
    void recordsNothingFurtherWhenRunAgain() {
        Transaction in = only(seed(savings, row("2026-08-10", "500.00", Category.TRANSFER, "IN")));
        seed(current, row("2026-08-10", "-500.00", Category.TRANSFER, "OUT"));

        assertThat(detection.detect(List.of(in))).hasSize(1);
        assertThat(detection.detect(List.of(in))).isEmpty();
        assertThat(detection.detect(List.of(in))).isEmpty();
        assertThat(transferCount()).isEqualTo(1);
    }

    // --- what analytics then sees ---

    @Test
    void takesBothLegsOutOfIncomeAndExpenditureOnceLinked() {
        seed(current,
                row("2026-08-03", "-24.15", Category.GROCERIES, "GROCER"),
                row("2026-08-10", "-500.00", Category.TRANSFER, "TO SAVINGS"));
        List<Transaction> incoming = seed(savings, row("2026-08-10", "500.00", Category.TRANSFER, "FROM CURRENT"));
        AnalyticsScope scope = AnalyticsScope.ofUser(owner.id());

        FinancialSummary before = analytics.summarise(scope, AUGUST);
        assertThat(before.income()).isEqualByComparingTo("500.00");
        assertThat(before.expenditure()).isEqualByComparingTo("524.15");
        assertThat(before.transactionCount()).isEqualTo(3);

        detection.detect(incoming);

        // Only the grocer remains: the 500.00 never left the user's position.
        FinancialSummary after = analytics.summarise(scope, AUGUST);
        assertThat(after.income()).isEqualByComparingTo("0");
        assertThat(after.expenditure()).isEqualByComparingTo("24.15");
        assertThat(after.netSavings()).isEqualByComparingTo("-24.15");
        assertThat(after.transactionCount()).isEqualTo(1);
    }

    // --- helpers ---

    private static Transaction only(List<Transaction> transactions) {
        assertThat(transactions).hasSize(1);
        return transactions.get(0);
    }

    private int transferCount() {
        return jdbc.queryForObject("SELECT count(*) FROM transfers", Integer.class);
    }
}
