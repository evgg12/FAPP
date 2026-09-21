package com.fapp.api;

import com.fapp.transaction.RecategorisationResult;
import com.fapp.transaction.TransactionRecategorisationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reapplies today's merchant rules to a user's uncategorised transactions.
 *
 * <p>A POST because it changes stored data, and safe to call repeatedly: a row a rule
 * recognises stops being uncategorised, so a second call has nothing left to do.
 */
@RestController
@RequestMapping("/api/users/{userId}/transactions")
@Tag(name = "Recategorisation", description = "Reapply categorisation rules to transactions")
class RecategorisationController {

    private final TransactionRecategorisationService recategorisation;

    RecategorisationController(TransactionRecategorisationService recategorisation) {
        this.recategorisation = recategorisation;
    }

    @PostMapping("/recategorise")
    @Operation(summary = "Reapply categorisation rules",
            description = "Reapplies current merchant rules to all uncategorised transactions. Idempotent: running multiple times will not change already categorised transactions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recategorisation completed",
                    content = @Content(schema = @Schema(implementation = RecategorisationResult.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing authentication credentials")
    })
    @SecurityRequirement(name = "basicAuth")
    RecategorisationResult recategorise(@PathVariable UUID userId) {
        return recategorisation.recategoriseUncategorised(userId);
    }
}
