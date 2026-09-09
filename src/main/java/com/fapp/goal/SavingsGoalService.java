package com.fapp.goal;

import com.fapp.money.Money;
import com.fapp.user.User;
import com.fapp.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, reading, revising and removing savings goals.
 *
 * <p>Every operation is scoped by owner and resolves the goal by id <em>and</em> user, so
 * one user can never reach another's goal — not even to learn that it exists. The
 * arithmetic of progress belongs to {@link SavingsGoal} itself; this handles ownership,
 * persistence and the rules about what may change.
 *
 * <p>Takes a {@link Clock} rather than calling {@code LocalDate.now()}, so "a goal cannot
 * be created already overdue" is a rule that can be tested rather than one that depends
 * on the day the suite runs.
 */
@Service
@Transactional
public class SavingsGoalService {

    private final SavingsGoalRepository goals;
    private final UserRepository users;
    private final Clock clock;

    SavingsGoalService(SavingsGoalRepository goals, UserRepository users, Clock clock) {
        this.goals = goals;
        this.users = users;
        this.clock = clock;
    }

    public SavingsGoal create(UUID userId, String name, Money target, LocalDate targetDate) {
        User owner = users.findById(userId).orElseThrow(() -> new GoalNotFoundException(
                "USER_NOT_FOUND", "no user with id " + userId));
        SavingsGoal goal = SavingsGoal.of(owner, name, target, targetDate, LocalDate.now(clock));
        goals.saveAndFlush(goal);
        // Answered from the instance just built: ids are assigned in the constructor, so
        // save merges and returns a copy whose owner is a lazy proxy.
        return goal;
    }

    @Transactional(readOnly = true)
    public List<SavingsGoal> findAll(UUID userId) {
        requireUser(userId);
        return goals.findByUser_IdOrderByTargetDateAscNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public SavingsGoal find(UUID userId, UUID goalId) {
        requireUser(userId);
        return goals.findByIdAndUser_Id(goalId, userId).orElseThrow(() -> new GoalNotFoundException(
                "GOAL_NOT_FOUND", "no savings goal with id " + goalId + " for this user"));
    }

    /**
     * Revises a goal. The currency is fixed for its life and is not accepted here;
     * everything else about the goal may change.
     */
    public SavingsGoal update(UUID userId,
                              UUID goalId,
                              String name,
                              Money target,
                              LocalDate targetDate,
                              Money currentAmount) {
        SavingsGoal goal = find(userId, goalId);
        goal.rename(name);
        goal.retarget(target, targetDate);
        goal.recordCurrentAmount(currentAmount);
        return goal;
    }

    /** Adds to, or withdraws from, what is put aside towards a goal. */
    public SavingsGoal contribute(UUID userId, UUID goalId, Money amount) {
        SavingsGoal goal = find(userId, goalId);
        goal.contribute(amount);
        return goal;
    }

    public void delete(UUID userId, UUID goalId) {
        goals.delete(find(userId, goalId));
    }

    @Transactional(readOnly = true)
    public List<SavingsGoal> findFeatured(UUID userId) {
        requireUser(userId);
        return goals.findByUser_IdAndFeaturedTrueOrderByCreatedAtAsc(userId);
    }

    /**
     * Marks a goal as featured, alongside any others the user has already featured.
     * Featuring an already-featured goal is a no-op, so the operation is idempotent.
     */
    public SavingsGoal feature(UUID userId, UUID goalId) {
        SavingsGoal goal = find(userId, goalId);
        goal.feature();
        return goal;
    }

    /** Clears the featured flag. Unfeaturing a goal that is not featured is a no-op. */
    public void unfeature(UUID userId, UUID goalId) {
        find(userId, goalId).unfeature();
    }

    private void requireUser(UUID userId) {
        if (!users.existsById(userId)) {
            throw new GoalNotFoundException("USER_NOT_FOUND", "no user with id " + userId);
        }
    }
}
