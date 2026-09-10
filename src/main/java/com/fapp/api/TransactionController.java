package com.fapp.api;

import com.fapp.security.CurrentUser;
import com.fapp.transaction.Category;
import com.fapp.transaction.CategorySource;
import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransactionRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correcting what a transaction was for.
 *
 * <p>The category is the one analytical fact FAPP infers rather than reads off a
 * statement, so it is the one field a user is allowed to change. Setting it applies to
 * every other movement of the user's with the same description, because a payee that was
 * miscategorised once was miscategorised every time it appeared, and correcting a
 * recurring shop one row at a time is not work a person should be given. Everything else the
 * bank reported stays as reported. The change is recorded with
 * {@link CategorySource#USER}, which is what stops the merchant rules from overwriting
 * it on a later import.
 */
@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    private final TransactionRepository transactions;
    private final CurrentUser currentUser;

    TransactionController(TransactionRepository transactions, CurrentUser currentUser) {
        this.transactions = transactions;
        this.currentUser = currentUser;
    }

    @PatchMapping("/{transactionId}/category")
    @Transactional
    TransactionResponse recategorise(@PathVariable UUID transactionId,
                                     @Valid @RequestBody RecategoriseTransactionRequest request) {
        Transaction transaction = transactions.findById(transactionId)
                .orElseThrow(() -> notFound(transactionId));
        // Somebody else's transaction reads as absent, not as forbidden.
        if (!transaction.userId().equals(currentUser.requireId())) {
            throw notFound(transactionId);
        }

        boolean custom = request.category() == Category.CUSTOM;
        if (custom && (request.customCategory() == null || request.customCategory().isBlank())) {
            throw new IllegalArgumentException("customCategory is required when category is CUSTOM");
        }

        // Includes the transaction that was asked about, since it shares its own description.
        for (Transaction sameName
                : transactions.findByUserIdAndDescription(transaction.userId(), transaction.description())) {
            if (custom) {
                sameName.recategoriseAs(request.customCategory(), CategorySource.USER);
            } else {
                sameName.recategorise(request.category(), CategorySource.USER);
            }
            transactions.save(sameName);
        }
        return TransactionResponse.of(transaction);
    }

    private static NotFoundException notFound(UUID transactionId) {
        return new NotFoundException("TRANSACTION_NOT_FOUND", "no transaction with id " + transactionId);
    }
}
