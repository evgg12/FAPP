package com.fapp.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Health", description = "Application health check")
public class HealthController {

    @GetMapping
    @Operation(summary = "Check application health",
            description = "Liveness probe that confirms the application is running and serving HTTP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application is healthy",
                    content = @Content(schema = @Schema(implementation = HealthResponse.class)))
    })
    public HealthResponse health() {
        return new HealthResponse("UP", "fapp");
    }

    public record HealthResponse(String status, String application) {
    }
}
