package com.fapp.api;

import com.fapp.transaction.RecategorisationResult;
import com.fapp.transaction.TransactionRecategorisationService;
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
class RecategorisationController {

    private final TransactionRecategorisationService recategorisation;

    RecategorisationController(TransactionRecategorisationService recategorisation) {
        this.recategorisation = recategorisation;
    }

    @PostMapping("/recategorise")
    RecategorisationResult recategorise(@PathVariable UUID userId) {
        return recategorisation.recategoriseUncategorised(userId);
    }
}
