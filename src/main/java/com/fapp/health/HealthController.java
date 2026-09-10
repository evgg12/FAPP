package com.fapp.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint used to confirm the application is running and serving HTTP.
 * It deliberately reports no dependency state: dependency health checks arrive
 * with observability (Phase 10), not here.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", "fapp");
    }

    public record HealthResponse(String status, String application) {
    }
}
