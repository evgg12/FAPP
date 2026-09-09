package com.fapp.pinned;

import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransactionRepository;
import com.fapp.user.User;
import com.fapp.user.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, reading, revising and removing pinned groups, and pinning or unpinning
 * transactions within them.
 *
 * <p>Every operation is scoped by owner and resolves a group by id <em>and</em> user,
 * exactly as {@code SavingsGoalService} does, so one user can never reach or affect
 * another's group -- not even to learn that it exists. Pinning a transaction is checked
 * the same way: a transaction that does not belong to the caller is reported as not
 * found rather than added.
 */
@Service
@Transactional
public class PinnedGroupService {

    private final PinnedGroupRepository groups;
    private final PinnedGroupTransactionRepository memberships;
    private final TransactionRepository transactions;
    private final UserRepository users;

    PinnedGroupService(PinnedGroupRepository groups,
                       PinnedGroupTransactionRepository memberships,
                       TransactionRepository transactions,
                       UserRepository users) {
        this.groups = groups;
        this.memberships = memberships;
        this.transactions = transactions;
        this.users = users;
    }

    public PinnedGroup create(UUID userId, String name, String notes) {
        User owner = users.findById(userId).orElseThrow(() -> new PinnedGroupNotFoundException(
                "USER_NOT_FOUND", "no user with id " + userId));
        PinnedGroup group = PinnedGroup.of(owner, name, notes);
        groups.save(group);
        return group;
    }

    @Transactional(readOnly = true)
    public List<PinnedGroup> findAll(UUID userId) {
        requireUser(userId);
        return groups.findByUser_IdOrderByCreatedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public PinnedGroup find(UUID userId, UUID groupId) {
        requireUser(userId);
        return groups.findByIdAndUser_Id(groupId, userId).orElseThrow(() -> new PinnedGroupNotFoundException(
                "PINNED_GROUP_NOT_FOUND", "no pinned group with id " + groupId + " for this user"));
    }

    public PinnedGroup update(UUID userId, UUID groupId, String name, String notes) {
        PinnedGroup group = find(userId, groupId);
        group.update(name, notes);
        return group;
    }

    public void delete(UUID userId, UUID groupId) {
        groups.delete(find(userId, groupId));
    }

    @Transactional(readOnly = true)
    public List<PinnedGroupTransaction> transactionsIn(UUID userId, UUID groupId) {
        PinnedGroup group = find(userId, groupId);
        return memberships.findByGroup_IdOrderByPinnedAtAsc(group.id());
    }

    /**
     * Pins each of the given transactions into the group. Already-pinned transactions
     * are left untouched rather than rejected, so a request mixing new and already-
     * pinned ids adds only what is new.
     *
     * @throws PinnedGroupNotFoundException if any transaction does not belong to this user
     */
    public List<PinnedGroupTransaction> addTransactions(UUID userId, UUID groupId, List<UUID> transactionIds) {
        PinnedGroup group = find(userId, groupId);
        List<PinnedGroupTransaction> added = new ArrayList<>();
        for (UUID transactionId : transactionIds) {
            if (memberships.existsByGroup_IdAndTransaction_Id(group.id(), transactionId)) {
                continue;
            }
            Transaction transaction = requireOwnTransaction(userId, transactionId);
            added.add(memberships.save(PinnedGroupTransaction.of(group, transaction)));
        }
        return added;
    }

    public void removeTransaction(UUID userId, UUID groupId, UUID transactionId) {
        PinnedGroup group = find(userId, groupId);
        memberships.deleteByGroup_IdAndTransaction_Id(group.id(), transactionId);
    }

    /** A user's transactions pinned on their own, most recently pinned first. */
    @Transactional(readOnly = true)
    public List<PinnedGroupTransaction> individualPins(UUID userId) {
        requireUser(userId);
        return memberships.findIndividualByUserId(userId);
    }

    /** Every transaction id this user has pinned, individually or into any group. */
    @Transactional(readOnly = true)
    public Set<UUID> pinnedTransactionIds(UUID userId) {
        requireUser(userId);
        return new LinkedHashSet<>(memberships.findPinnedTransactionIdsByUserId(userId));
    }

    /**
     * Pins a transaction on its own, with an optional short note. Idempotent: pinning an
     * already individually-pinned transaction again returns the existing pin rather than
     * creating a second one or failing.
     *
     * @throws PinnedGroupNotFoundException if the transaction does not belong to this user
     */
    public PinnedGroupTransaction pinIndividually(UUID userId, UUID transactionId, String note) {
        return memberships.findIndividualByUserIdAndTransactionId(userId, transactionId)
                .orElseGet(() -> {
                    Transaction transaction = requireOwnTransaction(userId, transactionId);
                    return memberships.save(PinnedGroupTransaction.individual(transaction, note));
                });
    }

    /** Removes an individual pin. Unpinning a transaction that is not individually pinned is a no-op. */
    public void unpinIndividually(UUID userId, UUID transactionId) {
        memberships.deleteIndividualByUserIdAndTransactionId(userId, transactionId);
    }

    /**
     * The transaction behind this id, if it belongs to the caller. Somebody else's
     * transaction reads as absent, not as forbidden, so the API cannot be used to learn
     * whether an id belongs to another user.
     */
    private Transaction requireOwnTransaction(UUID userId, UUID transactionId) {
        Transaction transaction = transactions.findById(transactionId).orElseThrow(
                () -> new PinnedGroupNotFoundException(
                        "TRANSACTION_NOT_FOUND", "no transaction with id " + transactionId));
        if (!transaction.userId().equals(userId)) {
            throw new PinnedGroupNotFoundException(
                    "TRANSACTION_NOT_FOUND", "no transaction with id " + transactionId);
        }
        return transaction;
    }

    private void requireUser(UUID userId) {
        if (!users.existsById(userId)) {
            throw new PinnedGroupNotFoundException("USER_NOT_FOUND", "no user with id " + userId);
        }
    }
}
