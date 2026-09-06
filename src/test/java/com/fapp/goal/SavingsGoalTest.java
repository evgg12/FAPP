package com.fapp.goal;

import com.fapp.money.Money;
import com.fapp.user.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** The goal's own rules and arithmetic, with every expected figure worked out by hand. */
class SavingsGoalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final LocalDate JUNE_2027 = LocalDate.of(2027, 6, 1);

    private final User owner = User.of("saver@example.com", "Saver");

    @Test
    void startsWithNothingSavedAndNothingAchieved() {
        SavingsGoal goal = carFund();

        assertThat(goal.id()).isNotNull();
        assertThat(goal.userId()).isEqualTo(owner.id());
        assertThat(goal.name()).isEqualTo("Car Fund");
        assertThat(goal.target()).isEqualTo(Money.of("8000.00", "GBP"));
        assertThat(goal.currentAmount().amount()).isEqualByComparingTo("0");
        assertThat(goal.targetDate()).isEqualTo(JUNE_2027);

        GoalProgress progress = goal.progress();
        assertThat(progress.remaining().amount()).isEqualByComparingTo("8000.00");
        assertThat(progress.percentage()).isEqualByComparingTo("0.00");
        assertThat(progress.achieved()).isFalse();
    }

    @Test
    void reportsProgressExactlyForTheSpecificationsOwnExample() {
        // Car Fund: target 8000.00, saved 2350.00.
        SavingsGoal goal = carFund();
        goal.recordCurrentAmount(Money.of("2350.00", "GBP"));

        GoalProgress progress = goal.progress();
        assertThat(progress.current().amount()).isEqualByComparingTo("2350.00");
        assertThat(progress.remaining().amount()).isEqualByComparingTo("5650.00");
        // 2350 / 8000 = 29.375%, to two places 29.38.
        assertThat(progress.percentage()).isEqualByComparingTo("29.38");
        assertThat(progress.achieved()).isFalse();
    }

    @Test
    void countsAGoalMetExactlyOnTheTargetAsAchieved() {
        SavingsGoal goal = carFund();
        goal.recordCurrentAmount(Money.of("8000.00", "GBP"));

        GoalProgress progress = goal.progress();
        assertThat(progress.remaining().amount()).isEqualByComparingTo("0");
        assertThat(progress.percentage()).isEqualByComparingTo("100.00");
        assertThat(progress.achieved()).isTrue();
    }

    @Test
    void reportsOverachievementHonestlyRatherThanClampingIt() {
        SavingsGoal goal = carFund();
        goal.recordCurrentAmount(Money.of("9600.00", "GBP"));

        GoalProgress progress = goal.progress();
        // 9600 / 8000 = 120%. Saying 100% would hide that they saved 1600 more.
        assertThat(progress.percentage()).isEqualByComparingTo("120.00");
        assertThat(progress.achieved()).isTrue();
        // But there is no such thing as negative money still to find.
        assertThat(progress.remaining().amount()).isEqualByComparingTo("0");
    }

    @Test
    void addsAndWithdrawsContributions() {
        SavingsGoal goal = carFund();

        goal.contribute(Money.of("100.00", "GBP"));
        goal.contribute(Money.of("250.50", "GBP"));
        assertThat(goal.currentAmount().amount()).isEqualByComparingTo("350.50");

        goal.contribute(Money.of("-50.50", "GBP"));
        assertThat(goal.currentAmount().amount()).isEqualByComparingTo("300.00");
    }

    @Test
    void refusesToTakeAGoalBelowNothing() {
        SavingsGoal goal = carFund();
        goal.contribute(Money.of("100.00", "GBP"));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> goal.contribute(Money.of("-100.01", "GBP")))
                .withMessageContaining("below nothing");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> goal.recordCurrentAmount(Money.of("-0.01", "GBP")))
                .withMessageContaining("less than nothing");
        assertThat(goal.currentAmount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void refusesAGoalForNothingOrLess() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SavingsGoal.of(owner, "Nothing", Money.of("0", "GBP"), JUNE_2027, TODAY))
                .withMessageContaining("more than nothing");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SavingsGoal.of(owner, "Negative", Money.of("-1.00", "GBP"), JUNE_2027, TODAY))
                .withMessageContaining("more than nothing");
    }

    @Test
    void refusesAGoalThatIsAlreadyOverdueWhenItIsCreated() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SavingsGoal.of(
                        owner, "Yesterday", Money.of("100.00", "GBP"), TODAY.minusDays(1), TODAY))
                .withMessageContaining("due before it is created");

        // Today itself is acceptable: a goal due now is odd but not incoherent.
        assertThat(SavingsGoal.of(owner, "Today", Money.of("100.00", "GBP"), TODAY, TODAY).targetDate())
                .isEqualTo(TODAY);
    }

    @Test
    void refusesAGoalWithNoName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> SavingsGoal.of(owner, "   ", Money.of("100.00", "GBP"), JUNE_2027, TODAY))
                .withMessageContaining("must be named");
        assertThat(SavingsGoal.of(owner, "  Car Fund  ", Money.of("100.00", "GBP"), JUNE_2027, TODAY).name())
                .isEqualTo("Car Fund");
    }

    @Test
    void keepsTheCurrencyItWasCreatedIn() {
        SavingsGoal goal = carFund();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> goal.contribute(Money.of("100.00", "EUR")))
                .withMessageContaining("this goal is in GBP");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> goal.retarget(Money.of("9000.00", "EUR"), JUNE_2027))
                .withMessageContaining("cannot become EUR");
    }

    @Test
    void allowsTheTargetAndDateToBeRevised() {
        SavingsGoal goal = carFund();
        goal.recordCurrentAmount(Money.of("2350.00", "GBP"));

        goal.retarget(Money.of("4700.00", "GBP"), LocalDate.of(2027, 1, 1));
        goal.rename("Smaller Car Fund");

        assertThat(goal.name()).isEqualTo("Smaller Car Fund");
        assertThat(goal.targetDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        // Halving the target doubles the progress: 2350 of 4700 is exactly half.
        assertThat(goal.progress().percentage()).isEqualByComparingTo("50.00");
        assertThat(goal.progress().remaining().amount()).isEqualByComparingTo("2350.00");
    }

    @Test
    void refusesARevisedTargetOfNothing() {
        SavingsGoal goal = carFund();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> goal.retarget(Money.of("0", "GBP"), JUNE_2027))
                .withMessageContaining("more than nothing");
    }

    @Test
    void keepsMoneyExactRatherThanRoundingIt() {
        SavingsGoal goal = SavingsGoal.of(owner, "Precise", Money.of("3333.3333", "GBP"), JUNE_2027, TODAY);
        goal.recordCurrentAmount(Money.of("1111.1111", "GBP"));

        assertThat(goal.progress().remaining().amount()).isEqualByComparingTo("2222.2222");
        // Only the percentage is rounded, and only because a ratio has no exact form.
        assertThat(goal.progress().percentage().scale()).isEqualTo(GoalProgress.PERCENTAGE_SCALE);
    }

    private SavingsGoal carFund() {
        return SavingsGoal.of(owner, "Car Fund", Money.of("8000.00", "GBP"), JUNE_2027, TODAY);
    }
}
