package com.fapp.transaction;

import com.fapp.account.Account;
import com.fapp.persistence.SeededDomainTest;
import com.fapp.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Reapplying merchant rules to transactions that were imported before the rules existed.
 *
 * <p>The interesting assertions are about what the backfill does <em>not</em> do: it must
 * not touch a category a bank supplied, must not disturb transaction identity, and must
 * not reach across users.
 */
class TransactionRecategorisationServiceTest extends SeededDomainTest {

    @Autowired
    private TransactionRecategorisationService recategorisation;

    @Autowired
    private TransactionRepository transactions;

    private User owner;
    private Account account;

    @BeforeEach
    void anAccount() {
        owner = user("owner@example.com");
        account = account(owner, "bank_of_scotland", "BoS Current");
    }

    @Test
    void categorisesWhatTheRulesNowRecogniseAndLeavesTheRestAlone() {
        seed(account,
                row("2026-08-03", "-24.15", Category.UNCATEGORISED, "LIDL GB SAMPLETON"),
                row("2026-08-04", "-32.40", Category.UNCATEGORISED, "WAGAMAMA SAMPLETON"),
                row("2026-08-05", "-8.99", Category.UNCATEGORISED, "APPLE.COM/BILL"),
                row("2026-08-06", "-10.00", Category.UNCATEGORISED, "SOME SHOP WITH NO RULE"));

        RecategorisationResult result = recategorisation.recategoriseUncategorised(owner.id());

        assertThat(result.examined()).isEqualTo(4);
        assertThat(result.recategorised()).isEqualTo(3);
        assertThat(result.stillUncategorised()).isEqualTo(1);

        assertThat(categoryOf("LIDL GB SAMPLETON")).isEqualTo(Category.GROCERIES);
        assertThat(categoryOf("WAGAMAMA SAMPLETON")).isEqualTo(Category.RESTAURANTS);
        assertThat(categoryOf("APPLE.COM/BILL")).isEqualTo(Category.SUBSCRIPTIONS);
        assertThat(categoryOf("SOME SHOP WITH NO RULE")).isEqualTo(Category.UNCATEGORISED);
    }

    @Test
    void recordsThatARuleMadeTheDecision() {
        seed(account, row("2026-08-03", "-24.15", Category.UNCATEGORISED, "LIDL GB SAMPLETON"));

        recategorisation.recategoriseUncategorised(owner.id());

        assertThat(stored("LIDL GB SAMPLETON").categorySource()).isEqualTo(CategorySource.RULE);
    }

    @Test
    void neverOverwritesACategoryABankSupplied() {
        // A bank filing a Lidl shop under eating out is still better evidence than a
        // merchant name we recognise, so the backfill does not look at it at all.
        seed(account, row("2026-08-03", "-24.15", Category.RESTAURANTS, "LIDL GB SAMPLETON"));

        RecategorisationResult result = recategorisation.recategoriseUncategorised(owner.id());

        assertThat(result.examined()).isZero();
        assertThat(stored("LIDL GB SAMPLETON").category()).isEqualTo(Category.RESTAURANTS);
        assertThat(stored("LIDL GB SAMPLETON").categorySource()).isEqualTo(CategorySource.ADAPTER);
    }

    @Test
    void changesNothingElseAboutATransaction() {
        Transaction before = seed(account,
                row("2026-08-03", "-24.15", Category.UNCATEGORISED, "LIDL GB SAMPLETON")).get(0);
        String fingerprint = before.fingerprint();
        short occurrence = before.occurrence();

        recategorisation.recategoriseUncategorised(owner.id());

        Transaction after = stored("LIDL GB SAMPLETON");
        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.fingerprint()).isEqualTo(fingerprint);
        assertThat(after.occurrence()).isEqualTo(occurrence);
        assertThat(after.fingerprintVersion()).isEqualTo(before.fingerprintVersion());
        assertThat(after.amount().amount()).isEqualByComparingTo("-24.15");
        assertThat(after.amount().currency()).isEqualTo(before.amount().currency());
        assertThat(after.bookingDate()).isEqualTo(before.bookingDate());
        assertThat(after.description()).isEqualTo("LIDL GB SAMPLETON");
        assertThat(after.accountId()).isEqualTo(account.id());
        assertThat(after.userId()).isEqualTo(owner.id());
    }

    @Test
    void doesNothingFurtherWhenRunAgain() {
        seed(account,
                row("2026-08-03", "-24.15", Category.UNCATEGORISED, "LIDL GB SAMPLETON"),
                row("2026-08-04", "-10.00", Category.UNCATEGORISED, "SOME SHOP WITH NO RULE"));

        assertThat(recategorisation.recategoriseUncategorised(owner.id()).recategorised()).isEqualTo(1);

        // The Lidl row is no longer uncategorised, so it is not even examined again. The
        // unrecognised one is offered again and refused again: the same answer.
        RecategorisationResult second = recategorisation.recategoriseUncategorised(owner.id());
        assertThat(second.examined()).isEqualTo(1);
        assertThat(second.recategorised()).isZero();
        assertThat(categoryOf("LIDL GB SAMPLETON")).isEqualTo(Category.GROCERIES);
    }

    @Test
    void neverReachesAnotherUsersTransactions() {
        User stranger = user("stranger@example.com");
        Account theirs = account(stranger, "monzo", "Their Monzo");
        seed(account, row("2026-08-03", "-24.15", Category.UNCATEGORISED, "LIDL GB SAMPLETON"));
        seed(theirs, row("2026-08-03", "-30.00", Category.UNCATEGORISED, "WAGAMAMA SAMPLETON"));

        assertThat(recategorisation.recategoriseUncategorised(owner.id()).recategorised()).isEqualTo(1);

        // Theirs is untouched until they ask for it themselves.
        assertThat(stored("WAGAMAMA SAMPLETON").category()).isEqualTo(Category.UNCATEGORISED);
        assertThat(recategorisation.recategoriseUncategorised(stranger.id()).recategorised()).isEqualTo(1);
        assertThat(stored("WAGAMAMA SAMPLETON").category()).isEqualTo(Category.RESTAURANTS);
    }

    @Test
    void leavesLinkedTransfersLinked() {
        Account other = account(owner, "monzo", "Monzo Current");
        Transaction out = seed(account,
                row("2026-08-10", "-500.00", Category.UNCATEGORISED, "LIDL GB SAMPLETON")).get(0);
        Transaction in = seed(other,
                row("2026-08-10", "500.00", Category.UNCATEGORISED, "WAGAMAMA SAMPLETON")).get(0);
        linkAsTransfer(out, in);

        recategorisation.recategoriseUncategorised(owner.id());

        // Transfer detection keys on amount, date, account and currency, none of which a
        // recategorisation can change, so the link survives untouched.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers", Integer.class)).isEqualTo(1);
        assertThat(categoryOf("LIDL GB SAMPLETON")).isEqualTo(Category.GROCERIES);
    }

    @Test
    void refusesAUserThatDoesNotExist() {
        assertThatExceptionOfType(UnknownUserException.class)
                .isThrownBy(() -> recategorisation.recategoriseUncategorised(UUID.randomUUID()))
                .withMessageContaining("no user with id");
    }

    @Test
    void reportsNothingToDoForAUserWithNoTransactions() {
        RecategorisationResult result = recategorisation.recategoriseUncategorised(owner.id());

        assertThat(result.examined()).isZero();
        assertThat(result.recategorised()).isZero();
    }

    private Category categoryOf(String description) {
        return stored(description).category();
    }

    private Transaction stored(String description) {
        List<Transaction> all = transactionTemplate.execute(status -> transactions.findAll());
        return all.stream()
                .filter(transaction -> transaction.description().equals(description))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no transaction described as " + description));
    }
}
