package com.fapp.transaction;

import com.fapp.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies today's merchant rules to transactions imported before those rules existed.
 *
 * <p>Categorisation normally happens during import, which leaves everything imported
 * earlier as it was. Adding a rule for Lidl does nothing for the Lidl shops already
 * stored, and re-importing statements to pick them up would be an absurd way to get
 * there. This walks the rows that have no category and offers them to the same
 * categoriser the import uses.
 *
 * <p>Deliberately narrow about what it will touch:
 *
 * <ul>
 *   <li>Only transactions whose category is {@link Category#UNCATEGORISED}. A category a
 *       bank supplied is never overwritten — the same rule the import follows.
 *   <li>Only the category and where it came from. Amount, date, currency, description,
 *       merchant, account, fingerprint and occurrence are untouched, so transaction
 *       identity does not move: deduplication and transfer detection both key on things
 *       this cannot change.
 *   <li>Only one user's transactions, resolved by owner.
 * </ul>
 *
 * <p>Repeatable by construction. A row a rule recognises stops being uncategorised, so a
 * second run does not see it again; a row no rule recognises is offered again and refused
 * again, which is the same answer. Running it twice changes nothing the first run did not.
 */
@Service
public class TransactionRecategorisationService {

    private final TransactionRepository transactions;
    private final TransactionCategoriser categoriser;
    private final UserRepository users;

    TransactionRecategorisationService(TransactionRepository transactions,
                                       TransactionCategoriser categoriser,
                                       UserRepository users) {
        this.transactions = transactions;
        this.categoriser = categoriser;
        this.users = users;
    }

    /**
     * @throws UnknownUserException if there is no such user
     */
    @Transactional
    public RecategorisationResult recategoriseUncategorised(UUID userId) {
        if (!users.existsById(userId)) {
            throw new UnknownUserException("no user with id " + userId);
        }
        List<Transaction> uncategorised =
                transactions.findByUserIdAndCategory(userId, Category.UNCATEGORISED);

        int recategorised = 0;
        for (Transaction transaction : uncategorised) {
            var category = categoriser.categorise(
                    transaction.merchant().orElse(null), transaction.description());
            if (category.isPresent()) {
                transaction.recategorise(category.get(), CategorySource.RULE);
                recategorised++;
            }
        }
        return new RecategorisationResult(uncategorised.size(), recategorised);
    }
}
