package com.fapp.analytics;

import com.fapp.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Aggregates transactions in PostgreSQL and hands back totals.
 *
 * <p>Native SQL on purpose. Every figure here is a sum over a set of rows, and summing
 * is what the database is for: loading a year of transactions into Java to add them up
 * would move the same arithmetic somewhere slower for no gain in clarity. Keeping it in
 * SQL also keeps the arithmetic in {@code numeric}, so nothing passes through a binary
 * floating-point type on the way out.
 *
 * <p>Extends only {@link Repository}, so it exposes these queries and nothing else. It
 * cannot be used to save or delete a transaction by accident.
 *
 * <p>Three rules are enforced in every query, and they are the whole of the business
 * meaning that belongs at this level:
 *
 * <ul>
 *   <li><strong>Scope.</strong> Always filtered by {@code user_id}, which the schema
 *       guarantees agrees with the transaction's account. An account filter narrows
 *       further; the service checks the account belongs to the user before asking.
 *   <li><strong>Range.</strong> {@code booking_date >= from AND booking_date < to},
 *       matching {@link AnalyticsPeriod}'s half-open window, and using
 *       {@code booking_date} because it is the only canonical analytical date.
 *   <li><strong>Internal transfers.</strong> A transaction that is a recorded leg of a
 *       transfer between two of the user's own accounts is left out entirely. Moving
 *       500.00 from a current account to a savings account is not 500.00 of income and
 *       500.00 of expenditure, and counting it as both would inflate every total and
 *       make net savings meaningless.
 * </ul>
 *
 * <p>Totals are cast to {@code numeric(19,4)} so an empty period returns zero at the
 * scale money is stored at rather than a null the caller has to interpret.
 */
public interface AnalyticsRepository extends Repository<Transaction, UUID> {

    /*
     * Reused across the queries below; kept written out rather than composed, because a
     * half-built SQL string is harder to read and to check than a repeated predicate.
     *
     *   AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.outgoing_transaction_id = t.id)
     *   AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.incoming_transaction_id = t.id)
     *
     * Both are anti-joins on the unique indexes uq_transfers_outgoing and
     * uq_transfers_incoming, so each is an index lookup rather than a scan.
     */

    @Query(nativeQuery = true, value = """
            SELECT CAST(COALESCE(SUM(CASE WHEN t.amount > 0 THEN t.amount END), 0) AS numeric(19,4)) AS "income",
                   CAST(COALESCE(SUM(CASE WHEN t.amount < 0 THEN -t.amount END), 0) AS numeric(19,4)) AS "expenditure",
                   COUNT(*) AS "transactionCount"
            FROM transactions t
            WHERE t.user_id = :userId
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.booking_date >= :from
              AND t.booking_date < :to
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.outgoing_transaction_id = t.id)
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.incoming_transaction_id = t.id)
            """)
    Totals summarise(@Param("userId") UUID userId,
                     @Param("accountId") UUID accountId,
                     @Param("from") LocalDate from,
                     @Param("to") LocalDate to);

    @Query(nativeQuery = true, value = """
            SELECT t.category AS "category",
                   t.custom_category AS "customCategory",
                   CAST(COALESCE(SUM(CASE WHEN t.amount > 0 THEN t.amount END), 0) AS numeric(19,4)) AS "income",
                   CAST(COALESCE(SUM(CASE WHEN t.amount < 0 THEN -t.amount END), 0) AS numeric(19,4)) AS "expenditure",
                   COUNT(*) AS "transactionCount"
            FROM transactions t
            WHERE t.user_id = :userId
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.booking_date >= :from
              AND t.booking_date < :to
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.outgoing_transaction_id = t.id)
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.incoming_transaction_id = t.id)
              AND t.category <> 'SAVINGS'
            GROUP BY t.category, t.custom_category
            ORDER BY 4 DESC, 3 DESC, 1 ASC
            """)
    List<CategoryTotals> summariseByCategory(@Param("userId") UUID userId,
                                             @Param("accountId") UUID accountId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    @Query(nativeQuery = true, value = """
            SELECT CAST(date_trunc('month', t.booking_date) AS date) AS "month",
                   CAST(COALESCE(SUM(CASE WHEN t.amount > 0 THEN t.amount END), 0) AS numeric(19,4)) AS "income",
                   CAST(COALESCE(SUM(CASE WHEN t.amount < 0 THEN -t.amount END), 0) AS numeric(19,4)) AS "expenditure",
                   COUNT(*) AS "transactionCount"
            FROM transactions t
            WHERE t.user_id = :userId
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.booking_date >= :from
              AND t.booking_date < :to
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.outgoing_transaction_id = t.id)
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.incoming_transaction_id = t.id)
            GROUP BY 1
            ORDER BY 1
            """)
    List<MonthTotals> summariseByMonth(@Param("userId") UUID userId,
                                       @Param("accountId") UUID accountId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * Every account the user holds, whether or not it saw activity. The range and the
     * transfer exclusion sit in the join rather than the where clause, so an account
     * with nothing in the period comes back as zeros instead of vanishing.
     */
    @Query(nativeQuery = true, value = """
            SELECT a.id AS "accountId",
                   a.provider AS "provider",
                   a.display_name AS "accountName",
                   CAST(COALESCE(SUM(CASE WHEN t.amount > 0 THEN t.amount END), 0) AS numeric(19,4)) AS "income",
                   CAST(COALESCE(SUM(CASE WHEN t.amount < 0 THEN -t.amount END), 0) AS numeric(19,4)) AS "expenditure",
                   COUNT(t.id) AS "transactionCount"
            FROM accounts a
            LEFT JOIN transactions t
                   ON t.account_id = a.id
                  AND t.booking_date >= :from
                  AND t.booking_date < :to
                  AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.outgoing_transaction_id = t.id)
                  AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.incoming_transaction_id = t.id)
            WHERE a.user_id = :userId
            GROUP BY a.id, a.provider, a.display_name
            ORDER BY 5 DESC, 3 ASC
            """)
    List<AccountTotals> summariseByAccount(@Param("userId") UUID userId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /**
     * Biggest outgoings first. Ordered by the stored signed amount ascending, so the
     * most negative comes first, then by date and id so the order is total and a tie
     * never comes back in a different sequence twice.
     */
    @Query(nativeQuery = true, value = """
            SELECT t.id AS "transactionId",
                   t.booking_date AS "bookingDate",
                   t.description AS "description",
                   t.merchant AS "merchant",
                   CAST(-t.amount AS numeric(19,4)) AS "amount",
                   t.category AS "category",
                   a.id AS "accountId",
                   a.display_name AS "accountName"
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            WHERE t.user_id = :userId
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.booking_date >= :from
              AND t.booking_date < :to
              AND t.amount < 0
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.outgoing_transaction_id = t.id)
              AND NOT EXISTS (SELECT 1 FROM transfers tr WHERE tr.incoming_transaction_id = t.id)
            ORDER BY t.amount ASC, t.booking_date DESC, t.id ASC
            LIMIT :limit
            """)
    List<ExpenseRow> findLargestExpenses(@Param("userId") UUID userId,
                                         @Param("accountId") UUID accountId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("limit") int limit);

    /**
     * The savings pot: what has been moved into it and what has been taken back out.
     *
     * <p>Only {@code SAVINGS} rows count, which is what a pot transfer is categorised as
     * on import. Money going into a pot leaves the account it came from, so it is stored
     * negative; the balance is therefore what went in less what came out, and the
     * caller does that subtraction on these two figures rather than on a signed sum.
     *
     * <p>No transfer exclusion here. A pot movement appears once in a statement, and
     * excluding it as an internal transfer is exactly what would make a pot balance
     * always read zero.
     */
    @Query(nativeQuery = true, value = """
            SELECT CAST(COALESCE(SUM(CASE WHEN t.amount < 0 THEN -t.amount END), 0) AS numeric(19,4)) AS "paidIn",
                   CAST(COALESCE(SUM(CASE WHEN t.amount > 0 THEN t.amount END), 0) AS numeric(19,4)) AS "withdrawn",
                   COUNT(*) AS "transactionCount"
            FROM transactions t
            WHERE t.user_id = :userId
              AND (:accountId IS NULL OR t.account_id = :accountId)
              AND t.booking_date >= :from
              AND t.booking_date < :to
              AND t.category = 'SAVINGS'
            """)
    PotTotals summarisePot(@Param("userId") UUID userId,
                           @Param("accountId") UUID accountId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    /** Money in, money out and how many movements produced them. */
    interface Totals {
        BigDecimal getIncome();

        BigDecimal getExpenditure();

        long getTransactionCount();
    }

    interface CategoryTotals extends Totals {
        String getCategory();

        String getCustomCategory();
    }

    interface PotTotals {
        BigDecimal getPaidIn();

        BigDecimal getWithdrawn();

        long getTransactionCount();
    }

    interface MonthTotals extends Totals {
        LocalDate getMonth();
    }

    interface AccountTotals extends Totals {
        UUID getAccountId();

        String getProvider();

        String getAccountName();
    }

    interface ExpenseRow {
        UUID getTransactionId();

        LocalDate getBookingDate();

        String getDescription();

        String getMerchant();

        BigDecimal getAmount();

        String getCategory();

        UUID getAccountId();

        String getAccountName();
    }
}
