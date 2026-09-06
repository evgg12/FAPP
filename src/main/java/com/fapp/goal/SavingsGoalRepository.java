package com.fapp.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    /** A user's goals, soonest due first, then named so the order is total. */
    List<SavingsGoal> findByUser_IdOrderByTargetDateAscNameAsc(UUID userId);

    /**
     * Resolved by goal <em>and</em> owner in one query, so a goal belonging to somebody
     * else is simply not found rather than found and then rejected.
     */
    Optional<SavingsGoal> findByIdAndUser_Id(UUID id, UUID userId);
}
