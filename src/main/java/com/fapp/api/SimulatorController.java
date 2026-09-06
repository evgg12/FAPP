package com.fapp.api;

import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.AnalyticsScope;
import com.fapp.simulator.ScenarioAdjustment;
import com.fapp.simulator.SimulationResult;
import com.fapp.simulator.SimulatorService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers what-if questions.
 *
 * <p>A POST that creates nothing. The scenario has too many optional figures to be a
 * readable query string, and posting it makes plain that the answer is a calculation
 * rather than a resource: no transaction, balance or goal is changed by asking.
 */
@RestController
@RequestMapping("/api/users/{userId}/simulations")
class SimulatorController {

    private final SimulatorService simulator;

    SimulatorController(SimulatorService simulator) {
        this.simulator = simulator;
    }

    @PostMapping
    SimulationResult simulate(@PathVariable UUID userId, @Valid @RequestBody SimulationRequest request) {
        return simulator.simulate(
                AnalyticsScope.of(userId, request.accountId()),
                new AnalyticsPeriod(request.from(), request.to()),
                new ScenarioAdjustment(
                        request.oneOffPurchase(),
                        request.monthlyExpenditureChange(),
                        request.monthlyIncomeChange()),
                request.horizonMonths(),
                request.goalId());
    }
}
