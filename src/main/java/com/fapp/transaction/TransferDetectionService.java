package com.fapp.transaction;

import com.fapp.account.AccountRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recognises when two transactions are the two sides of one movement between accounts
 * the user already owns, and records the link.
 *
 * <p>The point of it is arithmetic. Moving 500.00 from a current account to a savings
 * account appears as a payment out of one and a payment into the other, and analytics
 * would otherwise count it as 500.00 of expenditure <em>and</em> 500.00 of income —
 * inflating both totals and making net savings meaningless. Linking the legs takes them
 * out of both.
 *
 * <p><strong>Deliberately conservative.</strong> A pair is only linked when the money
 * itself says so: same owner, two different accounts, the same currency, exactly equal
 * and opposite amounts, and booking dates no more than
 * {@value #DATE_TOLERANCE_DAYS} day apart. Merchant, description and category are not
 * consulted at all. In particular {@link Category#TRANSFER} proves nothing — 50.00 sent
 * to a friend is filed exactly the same way and is genuinely money gone. A missed
 * transfer leaves a figure slightly overstated; a wrong one erases a real payment from
 * the user's spending, which is far worse.
 *
 * <p>All six of the rules a transfer must satisfy are already enforced by
 * {@link Transfer#of}, so they are not restated here. This finds a plausible partner
 * and asks that factory to accept it; anything it refuses is not linked.
 */
@Service
public class TransferDetectionService {

    /**
     * How many calendar days apart the two legs may be booked.
     *
     * <p>One, because the same movement routinely settles on different days at two
     * banks — sent late on the Tuesday, credited on the Wednesday. Widening it buys a
     * few more matches at the cost of pairing genuinely unrelated payments that happen
     * to be equal and opposite in the same week, which is not a trade worth making.
     */
    public static final int DATE_TOLERANCE_DAYS = 1;

    private final TransferCandidateRepository candidates;
    private final AccountRepository accounts;
    private final EntityManager entityManager;

    TransferDetectionService(TransferCandidateRepository candidates,
                             AccountRepository accounts,
                             EntityManager entityManager) {
        this.candidates = candidates;
        this.accounts = accounts;
        this.entityManager = entityManager;
    }

    /**
     * Looks for internal transfers involving any of these transactions and records the
     * ones it finds.
     *
     * <p>The counterpart is normally a transaction imported earlier from the other
     * account, since one statement covers one account and cannot contain both legs. So
     * a transfer is typically recognised when the second of the two accounts is
     * imported, however long after the first that happens.
     *
     * <p>Safe to run again over the same transactions: anything already recorded as a
     * leg is excluded from the search, so a second run finds nothing to do.
     *
     * @param transactions transactions to look for partners for, already persisted
     * @return the transfers recorded, empty if none were found
     */
    @Transactional
    public List<Transfer> detect(Collection<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        if (transactions.isEmpty()) {
            return List.of();
        }
        UUID userId = transactions.iterator().next().userId();
        // A transfer needs somewhere to transfer to. With one account there is nothing
        // to look for, which is the common case and worth not querying for at all.
        if (accounts.countByUser_Id(userId) < 2) {
            return List.of();
        }

        // Claimed within this run, so one transaction cannot end up in two transfers
        // before the database has had a chance to say so.
        Set<UUID> claimed = new HashSet<>();
        List<Transfer> recorded = new ArrayList<>();

        for (Transaction leg : inStableOrder(transactions)) {
            if (claimed.contains(leg.id())) {
                continue;
            }
            Transaction counterpart = findCounterpart(leg, claimed);
            if (counterpart == null) {
                continue;
            }
            Transfer transfer = leg.amount().isNegative()
                    ? Transfer.of(leg, counterpart, TransferDetectionSource.RULE)
                    : Transfer.of(counterpart, leg, TransferDetectionSource.RULE);
            entityManager.persist(transfer);
            claimed.add(leg.id());
            claimed.add(counterpart.id());
            recorded.add(transfer);
        }
        return List.copyOf(recorded);
    }

    /**
     * The best partner for one leg, or {@code null}.
     *
     * <p>Where several transactions would do — two accounts each holding an equal and
     * opposite payment on the same day — one is chosen rather than several created. The
     * closest booking date wins, then the earlier date, then the lower id: a total
     * order, so the same statements always produce the same links. Which of two equally
     * plausible partners that picks is arbitrary, but it is consistent, and it is one
     * link rather than an ambiguous pair of them.
     */
    private Transaction findCounterpart(Transaction leg, Set<UUID> claimed) {
        LocalDate booked = leg.bookingDate();
        return candidates.findCandidates(
                        leg.userId(),
                        leg.accountId(),
                        leg.amount().negated().amount(),
                        leg.amount().currency(),
                        booked.minusDays(DATE_TOLERANCE_DAYS),
                        booked.plusDays(DATE_TOLERANCE_DAYS))
                .stream()
                .filter(candidate -> !claimed.contains(candidate.id()))
                .min(Comparator
                        .comparingLong((Transaction candidate) ->
                                Math.abs(booked.toEpochDay() - candidate.bookingDate().toEpochDay()))
                        .thenComparing(Transaction::bookingDate)
                        .thenComparing(Transaction::id))
                .orElse(null);
    }

    /** Processed in a fixed order so a batch always produces the same links. */
    private static List<Transaction> inStableOrder(Collection<Transaction> transactions) {
        return transactions.stream()
                .sorted(Comparator
                        .comparing(Transaction::bookingDate)
                        .thenComparing(transaction -> transaction.amount().amount())
                        .thenComparing(Transaction::id))
                .toList();
    }
}
