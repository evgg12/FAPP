package com.fapp.pinned;

import com.fapp.account.Account;
import com.fapp.persistence.SeededDomainTest;
import com.fapp.transaction.Category;
import com.fapp.transaction.Transaction;
import com.fapp.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Pinned groups against real PostgreSQL: ownership, idempotent membership and cascades. */
class PinnedGroupServiceTest extends SeededDomainTest {

    @Autowired
    private PinnedGroupService groups;

    private User owner;
    private Account account;
    private List<Transaction> transactions;

    @BeforeEach
    void aUserWithTransactions() {
        owner = user("owner@example.com");
        account = account(owner, "monzo", "Owner's Monzo");
        transactions = seed(account,
                row("2026-01-01", "-10.00", Category.GROCERIES, "Greengrocer"),
                row("2026-01-02", "-20.00", Category.TRANSPORT, "Trains"),
                row("2026-01-03", "-30.00", Category.SHOPPING, "Shop"));
    }

    @Test
    void createsAndReadsBackAGroupWithItsNotes() {
        PinnedGroup created = groups.create(owner.id(), "IOU: Sam", "Lent for the trip");

        PinnedGroup reloaded = groups.find(owner.id(), created.id());
        assertThat(reloaded.name()).isEqualTo("IOU: Sam");
        assertThat(reloaded.notes()).contains("Lent for the trip");
        assertThat(reloaded.userId()).isEqualTo(owner.id());
        assertThat(reloaded.createdAt()).isNotNull();
    }

    @Test
    void aGroupMayHaveNoNotesAtAll() {
        PinnedGroup created = groups.create(owner.id(), "No notes", null);

        assertThat(groups.find(owner.id(), created.id()).notes()).isEmpty();
    }

    @Test
    void renamesAGroupAndReplacesItsNotes() {
        PinnedGroup created = groups.create(owner.id(), "IOU: Sam", "Lent for the trip");

        groups.update(owner.id(), created.id(), "IOU: Samantha", "Paid back half");

        PinnedGroup reloaded = groups.find(owner.id(), created.id());
        assertThat(reloaded.name()).isEqualTo("IOU: Samantha");
        assertThat(reloaded.notes()).contains("Paid back half");
    }

    @Test
    void addsOneTransactionToAGroup() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);

        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        List<PinnedGroupTransaction> members = groups.transactionsIn(owner.id(), group.id());
        assertThat(members).extracting(PinnedGroupTransaction::transactionId)
                .containsExactly(transactions.get(0).id());
    }

    @Test
    void addsMultipleTransactionsInOneCall() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);

        groups.addTransactions(owner.id(), group.id(),
                List.of(transactions.get(0).id(), transactions.get(1).id()));

        assertThat(groups.transactionsIn(owner.id(), group.id())).hasSize(2);
    }

    @Test
    void removesATransactionFromAGroupWithoutDeletingIt() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);
        groups.addTransactions(owner.id(), group.id(),
                List.of(transactions.get(0).id(), transactions.get(1).id()));

        groups.removeTransaction(owner.id(), group.id(), transactions.get(0).id());

        assertThat(groups.transactionsIn(owner.id(), group.id())).extracting(PinnedGroupTransaction::transactionId)
                .containsExactly(transactions.get(1).id());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE id = ?", Integer.class, transactions.get(0).id()))
                .isEqualTo(1);
    }

    /** Re-adding an already-pinned transaction changes nothing; it is not an error. */
    @Test
    void addingTheSameTransactionTwiceIsIdempotent() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);
        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        assertThat(groups.transactionsIn(owner.id(), group.id())).hasSize(1);
    }

    /** A mixed request adds only the ids that are not already members. */
    @Test
    void aMixOfNewAndAlreadyPinnedTransactionsAddsOnlyTheNewOnes() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);
        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        groups.addTransactions(owner.id(), group.id(),
                List.of(transactions.get(0).id(), transactions.get(1).id(), transactions.get(2).id()));

        assertThat(groups.transactionsIn(owner.id(), group.id())).extracting(PinnedGroupTransaction::transactionId)
                .containsExactlyInAnyOrder(
                        transactions.get(0).id(), transactions.get(1).id(), transactions.get(2).id());
    }

    /** The unique constraint is the final guarantee even if the service's check is bypassed. */
    @Test
    void theDatabaseRefusesADuplicateMembershipRowDirectly() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);
        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> jdbc.update(
                "INSERT INTO pinned_group_transactions (id, group_id, user_id, transaction_id)"
                        + " VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), group.id(), owner.id(), transactions.get(0).id()));
    }

    @Test
    void deletesAGroupWithoutTouchingItsTransactions() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);
        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        groups.delete(owner.id(), group.id());

        assertThatExceptionOfType(PinnedGroupNotFoundException.class)
                .isThrownBy(() -> groups.find(owner.id(), group.id()));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE id = ?", Integer.class, transactions.get(0).id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pinned_group_transactions", Integer.class)).isZero();
    }

    @Test
    void deletingTheUnderlyingTransactionLeavesNoOrphanedMembershipRow() {
        PinnedGroup group = groups.create(owner.id(), "IOU: Sam", null);
        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        jdbc.update("DELETE FROM accounts WHERE id = ?", account.id());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pinned_group_transactions WHERE group_id = ?", Integer.class, group.id()))
                .isZero();
        // The group itself survives its transaction disappearing.
        assertThat(groups.find(owner.id(), group.id()).id()).isEqualTo(group.id());
    }

    @Test
    void neverShowsOneUsersGroupsToAnother() {
        PinnedGroup mine = groups.create(owner.id(), "Mine", null);
        User stranger = user("stranger@example.com");
        groups.create(stranger.id(), "Theirs", null);

        assertThat(groups.findAll(owner.id())).extracting(PinnedGroup::name).containsExactly("Mine");
        assertThat(groups.findAll(stranger.id())).extracting(PinnedGroup::name).containsExactly("Theirs");
        assertThatExceptionOfType(PinnedGroupNotFoundException.class)
                .isThrownBy(() -> groups.find(stranger.id(), mine.id()))
                .satisfies(e -> assertThat(e.code()).isEqualTo("PINNED_GROUP_NOT_FOUND"));
    }

    @Test
    void aUserCannotAddAnotherUsersTransactionToTheirGroup() {
        PinnedGroup group = groups.create(owner.id(), "Mine", null);
        User stranger = user("stranger@example.com");
        Account strangerAccount = account(stranger, "monzo", "Stranger's Monzo");
        Transaction strangersTransaction = seed(strangerAccount,
                row("2026-01-01", "-5.00", Category.GROCERIES, "Not yours")).get(0);

        assertThatExceptionOfType(PinnedGroupNotFoundException.class)
                .isThrownBy(() -> groups.addTransactions(
                        owner.id(), group.id(), List.of(strangersTransaction.id())))
                .satisfies(e -> assertThat(e.code()).isEqualTo("TRANSACTION_NOT_FOUND"));
        assertThat(groups.transactionsIn(owner.id(), group.id())).isEmpty();
    }

    @Test
    void takesAUsersPinnedGroupsWithThemWhenTheyAreDeleted() {
        PinnedGroup group = groups.create(owner.id(), "Mine", null);
        groups.addTransactions(owner.id(), group.id(), List.of(transactions.get(0).id()));

        jdbc.update("DELETE FROM users WHERE id = ?", owner.id());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pinned_groups", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pinned_group_transactions", Integer.class)).isZero();
    }
}
