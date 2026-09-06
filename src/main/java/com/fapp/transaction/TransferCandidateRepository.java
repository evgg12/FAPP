package com.fapp.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Finds the transactions that could be the other half of an internal transfer.
 *
 * <p>Every condition that makes a pair plausible is applied in the database, so the
 * handful of rows that come back are already candidates rather than something to sift
 * through in Java. A transfer needs the counterpart to be the same user's, on a
 * different account, in the same currency, of exactly the opposite amount and within a
 * day or so — which taken together is a very small set even over years of statements.
 *
 * <p>Rows already recorded as a leg of some transfer are excluded, which is what makes
 * running detection twice a no-op rather than a source of duplicates.
 *
 * <p>Extends only {@link Repository}, so it exposes this one query and no way to write.
 */
public interface TransferCandidateRepository extends Repository<Transaction, UUID> {

    /**
     * @param userId       the owner; a transfer never crosses users
     * @param accountId    the account to exclude, being the one the known leg sits on
     * @param amount       the exact signed amount sought, which is the known leg's
     *                     amount negated
     * @param currency     must match; cross-currency transfers are out of scope
     * @param earliest     earliest acceptable booking date, inclusive
     * @param latest       latest acceptable booking date, inclusive
     * @return candidates ordered by date then id, so the same data always yields the
     *         same order and the service's choice is reproducible
     */
    @Query("""
            select c from Transaction c
            where c.userId = :userId
              and c.account.id <> :accountId
              and c.amount.amount = :amount
              and c.amount.currency = :currency
              and c.bookingDate >= :earliest
              and c.bookingDate <= :latest
              and not exists (select tr.id from Transfer tr where tr.outgoing = c)
              and not exists (select tr.id from Transfer tr where tr.incoming = c)
            order by c.bookingDate asc, c.id asc
            """)
    List<Transaction> findCandidates(@Param("userId") UUID userId,
                                     @Param("accountId") UUID accountId,
                                     @Param("amount") BigDecimal amount,
                                     @Param("currency") Currency currency,
                                     @Param("earliest") LocalDate earliest,
                                     @Param("latest") LocalDate latest);
}
