package com.fapp.goal;

import com.fapp.money.Money;
import com.fapp.persistence.SeededDomainTest;
import com.fapp.user.User;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Goals against real PostgreSQL: ownership, persistence and the database's own rules. */
class SavingsGoalServiceTest extends SeededDomainTest {

    private static final LocalDate JUNE_2030 = LocalDate.of(2030, 6, 1);

    @Autowired
    private SavingsGoalService goals;

    private User owner;

    @BeforeEach
    void aUser() {
        owner = user("saver@example.com");
    }

    @Test
    void createsAndReadsBackAGoalWithItsProgress() {
        SavingsGoal created = goals.create(owner.id(), "Car Fund", Money.of("8000.00", "GBP"), JUNE_2030);
        goals.update(owner.id(), created.id(), "Car Fund", Money.of("8000.00", "GBP"), JUNE_2030,
                Money.of("2350.00", "GBP"));

        SavingsGoal reloaded = goals.find(owner.id(), created.id());
        assertThat(reloaded.name()).isEqualTo("Car Fund");
        assertThat(reloaded.userId()).isEqualTo(owner.id());
        assertThat(reloaded.target().currency().getCurrencyCode()).isEqualTo("GBP");
        assertThat(reloaded.progress().remaining().amount()).isEqualByComparingTo("5650.00");
        assertThat(reloaded.progress().percentage()).isEqualByComparingTo("29.38");
        assertThat(reloaded.createdAt()).isNotNull();
    }

    @Test
    void keepsMoneyExactThroughTheDatabase() {
        SavingsGoal created =
                goals.create(owner.id(), "Precise", Money.of("3333.3333", "GBP"), JUNE_2030);
        goals.contribute(owner.id(), created.id(), Money.of("1111.1111", "GBP"));

        SavingsGoal reloaded = goals.find(owner.id(), created.id());
        assertThat(reloaded.target().amount()).isEqualByComparingTo("3333.3333");
        assertThat(reloaded.currentAmount().amount()).isEqualByComparingTo("1111.1111");
        assertThat(reloaded.progress().remaining().amount()).isEqualByComparingTo("2222.2222");
    }

    @Test
    void listsAUsersGoalsSoonestFirst() {
        goals.create(owner.id(), "Later", Money.of("100.00", "GBP"), LocalDate.of(2031, 1, 1));
        goals.create(owner.id(), "Sooner", Money.of("100.00", "GBP"), LocalDate.of(2030, 1, 1));

        assertThat(goals.findAll(owner.id())).extracting(SavingsGoal::name)
                .containsExactly("Sooner", "Later");
    }

    @Test
    void neverShowsOneUsersGoalsToAnother() {
        SavingsGoal mine = goals.create(owner.id(), "Mine", Money.of("100.00", "GBP"), JUNE_2030);
        User stranger = user("stranger@example.com");
        goals.create(stranger.id(), "Theirs", Money.of("500.00", "GBP"), JUNE_2030);

        assertThat(goals.findAll(owner.id())).extracting(SavingsGoal::name).containsExactly("Mine");
        assertThat(goals.findAll(stranger.id())).extracting(SavingsGoal::name).containsExactly("Theirs");

        // The same answer as a goal that does not exist, so one user cannot even learn
        // that another's goal is there.
        assertThatExceptionOfType(GoalNotFoundException.class)
                .isThrownBy(() -> goals.find(stranger.id(), mine.id()))
                .satisfies(e -> assertThat(e.code()).isEqualTo("GOAL_NOT_FOUND"));
        assertThatExceptionOfType(GoalNotFoundException.class)
                .isThrownBy(() -> goals.delete(stranger.id(), mine.id()));

        // And it is still there afterwards.
        assertThat(goals.find(owner.id(), mine.id()).name()).isEqualTo("Mine");
    }

    @Test
    void refusesToWorkWithAUserThatDoesNotExist() {
        assertThatExceptionOfType(GoalNotFoundException.class)
                .isThrownBy(() -> goals.create(
                        UUID.randomUUID(), "Ghost", Money.of("100.00", "GBP"), JUNE_2030))
                .satisfies(e -> assertThat(e.code()).isEqualTo("USER_NOT_FOUND"));
        assertThatExceptionOfType(GoalNotFoundException.class)
                .isThrownBy(() -> goals.findAll(UUID.randomUUID()));
    }

    @Test
    void refusesTwoGoalsOfTheSameNameForOneUser() {
        goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> goals.create(owner.id(), "Car Fund", Money.of("200.00", "GBP"), JUNE_2030));
    }

    @Test
    void allowsTwoUsersTheSameGoalName() {
        User stranger = user("stranger@example.com");
        goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);

        assertThat(goals.create(stranger.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030).id())
                .isNotNull();
    }

    @Test
    void deletesAGoalWithoutTouchingTheOthers() {
        SavingsGoal keep = goals.create(owner.id(), "Keep", Money.of("100.00", "GBP"), JUNE_2030);
        SavingsGoal remove = goals.create(owner.id(), "Remove", Money.of("100.00", "GBP"), JUNE_2030);

        goals.delete(owner.id(), remove.id());

        assertThat(goals.findAll(owner.id())).extracting(SavingsGoal::id).containsExactly(keep.id());
        assertThatExceptionOfType(GoalNotFoundException.class)
                .isThrownBy(() -> goals.find(owner.id(), remove.id()));
    }

    @Test
    void takesAUsersGoalsWithThemWhenTheyAreDeleted() {
        goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);

        jdbc.update("DELETE FROM users WHERE id = ?", owner.id());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM savings_goals", Integer.class)).isZero();
    }

    @Test
    void noGoalIsFeaturedUntilTheUserChoosesOne() {
        goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);

        assertThat(goals.findFeatured(owner.id())).isEmpty();
    }

    @Test
    void featuresAndUnfeaturesAGoal() {
        SavingsGoal goal = goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);

        goals.feature(owner.id(), goal.id());
        assertThat(goals.findFeatured(owner.id())).map(SavingsGoal::id).contains(goal.id());
        assertThat(goals.find(owner.id(), goal.id()).featured()).isTrue();

        goals.unfeature(owner.id(), goal.id());
        assertThat(goals.findFeatured(owner.id())).isEmpty();
        assertThat(goals.find(owner.id(), goal.id()).featured()).isFalse();
    }

    @Test
    void featuringAGoalIsIdempotent() {
        SavingsGoal goal = goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);

        goals.feature(owner.id(), goal.id());
        goals.feature(owner.id(), goal.id());

        assertThat(goals.find(owner.id(), goal.id()).featured()).isTrue();
    }

    @Test
    void unfeaturingAnUnfeaturedGoalIsIdempotent() {
        SavingsGoal goal = goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);

        goals.unfeature(owner.id(), goal.id());

        assertThat(goals.find(owner.id(), goal.id()).featured()).isFalse();
    }

    @Test
    void featuringAnotherGoalUnfeaturesThePrevious() {
        SavingsGoal first = goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);
        SavingsGoal second = goals.create(owner.id(), "Holiday", Money.of("200.00", "GBP"), JUNE_2030);

        goals.feature(owner.id(), first.id());
        goals.feature(owner.id(), second.id());

        assertThat(goals.find(owner.id(), first.id()).featured()).isFalse();
        assertThat(goals.find(owner.id(), second.id()).featured()).isTrue();
        Optional<SavingsGoal> featured = goals.findFeatured(owner.id());
        assertThat(featured).map(SavingsGoal::id).contains(second.id());
    }

    @Test
    void aUserCannotFeatureAnotherUsersGoal() {
        SavingsGoal theirs = goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);
        User stranger = user("stranger@example.com");

        assertThatExceptionOfType(GoalNotFoundException.class)
                .isThrownBy(() -> goals.feature(stranger.id(), theirs.id()));
        assertThatExceptionOfType(GoalNotFoundException.class)
                .isThrownBy(() -> goals.unfeature(stranger.id(), theirs.id()));
        assertThat(goals.find(owner.id(), theirs.id()).featured()).isFalse();
    }

    @Test
    void featuredStatePersistsThroughTheDatabase() {
        SavingsGoal goal = goals.create(owner.id(), "Car Fund", Money.of("100.00", "GBP"), JUNE_2030);
        goals.feature(owner.id(), goal.id());

        assertThat(jdbc.queryForObject(
                "SELECT featured FROM savings_goals WHERE id = ?", Boolean.class, goal.id()))
                .isTrue();
    }

    @Test
    void refusesFiguresTheDatabaseWouldNotAcceptEither() {
        // The domain refuses these before the database sees them, and the database would
        // refuse them too: ck_savings_goals_target and ck_savings_goals_current.
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> goals.create(owner.id(), "Nothing", Money.of("0", "GBP"), JUNE_2030));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update(
                        "INSERT INTO savings_goals (id, user_id, name, target_amount, current_amount,"
                                + " currency, target_date) VALUES (?, ?, 'Bad', -1.0000, 0.0000, 'GBP',"
                                + " DATE '2030-06-01')",
                        UUID.randomUUID(), owner.id()));
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update(
                        "INSERT INTO savings_goals (id, user_id, name, target_amount, current_amount,"
                                + " currency, target_date) VALUES (?, ?, 'Bad', 100.0000, -1.0000, 'GBP',"
                                + " DATE '2030-06-01')",
                        UUID.randomUUID(), owner.id()));
    }
}
