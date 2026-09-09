package com.fapp.api;

import com.fapp.goal.GoalNotFoundException;
import com.fapp.goal.SavingsGoal;
import com.fapp.goal.SavingsGoalService;
import com.fapp.money.Money;
import jakarta.validation.Valid;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's savings goals.
 *
 * <p>Thin: the arithmetic of progress belongs to the domain and ownership to the service.
 * This turns a request into money and a date and back into JSON.
 */
@RestController
@RequestMapping("/api/users/{userId}/goals")
class SavingsGoalController {

    private final SavingsGoalService goals;

    SavingsGoalController(SavingsGoalService goals) {
        this.goals = goals;
    }

    @PostMapping
    ResponseEntity<SavingsGoalResponse> create(@PathVariable UUID userId,
                                               @Valid @RequestBody CreateSavingsGoalRequest request) {
        SavingsGoal created = goals.create(
                userId,
                request.name(),
                Money.of(request.targetAmount(), Currency.getInstance(request.currency())),
                request.targetDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(SavingsGoalResponse.of(created));
    }

    @GetMapping
    List<SavingsGoalResponse> list(@PathVariable UUID userId) {
        return goals.findAll(userId).stream().map(SavingsGoalResponse::of).toList();
    }

    @GetMapping("/{goalId}")
    SavingsGoalResponse get(@PathVariable UUID userId, @PathVariable UUID goalId) {
        return SavingsGoalResponse.of(goals.find(userId, goalId));
    }

    /** The user's featured goal. Answers the same not-found shape as any other goal lookup. */
    @GetMapping("/featured")
    SavingsGoalResponse getFeatured(@PathVariable UUID userId) {
        return SavingsGoalResponse.of(goals.findFeatured(userId).orElseThrow(() -> new GoalNotFoundException(
                "GOAL_NOT_FOUND", "no featured savings goal for this user")));
    }

    @PutMapping("/{goalId}/featured")
    SavingsGoalResponse feature(@PathVariable UUID userId, @PathVariable UUID goalId) {
        return SavingsGoalResponse.of(goals.feature(userId, goalId));
    }

    @DeleteMapping("/{goalId}/featured")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfeature(@PathVariable UUID userId, @PathVariable UUID goalId) {
        goals.unfeature(userId, goalId);
    }

    @PutMapping("/{goalId}")
    SavingsGoalResponse update(@PathVariable UUID userId,
                               @PathVariable UUID goalId,
                               @Valid @RequestBody UpdateSavingsGoalRequest request) {
        Currency currency = goals.find(userId, goalId).target().currency();
        return SavingsGoalResponse.of(goals.update(
                userId,
                goalId,
                request.name(),
                Money.of(request.targetAmount(), currency),
                request.targetDate(),
                Money.of(request.currentAmount(), currency)));
    }

    @DeleteMapping("/{goalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID userId, @PathVariable UUID goalId) {
        goals.delete(userId, goalId);
    }
}
