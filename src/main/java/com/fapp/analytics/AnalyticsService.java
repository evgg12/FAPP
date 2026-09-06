package com.fapp.analytics;

import com.fapp.account.Account;
import com.fapp.account.AccountRepository;
import com.fapp.transaction.Category;
import com.fapp.user.UserRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Currency;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns transactions into financial facts.
 *
 * <p>Entirely deterministic: every figure is a sum, a subtraction or a ratio of figures
 * the database returned, and running the same request twice over unchanged data gives
 * the same answer to the last penny. Nothing here estimates, guesses or asks a model.
 *
 * <p>What lives here rather than in SQL is the interpretation: net savings is income
 * minus expenditure, a month with no transactions is still a month worth reporting, the
 * period before August is July, and a percentage of nothing does not exist. What lives
 * in {@link AnalyticsRepository} is the summing.
 *
 * <p>Scoping is checked before any query runs. A request naming an account that belongs
 * to someone else is refused in the same way as one naming an account that does not
 * exist, so analytics cannot become a way to learn what other people hold.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    /** Largest-expenses rows returned when a caller does not say. */
    public static final int DEFAULT_EXPENSE_LIMIT = 10;

    /** Ceiling on largest-expenses rows, so one request cannot ask for everything. */
    public static final int MAX_EXPENSE_LIMIT = 100;

    private final AnalyticsRepository analytics;
    private final UserRepository users;
    private final AccountRepository accounts;

    AnalyticsService(AnalyticsRepository analytics, UserRepository users, AccountRepository accounts) {
        this.analytics = analytics;
        this.users = users;
        this.accounts = accounts;
    }

    public FinancialSummary summarise(AnalyticsScope scope, AnalyticsPeriod period) {
        return summariseWithin(scope, period);
    }

    public List<CategorySummary> summariseByCategory(AnalyticsScope scope, AnalyticsPeriod period) {
        require(scope);
        return analytics.summariseByCategory(scope.userId(), scope.accountId(), period.from(), period.to())
                .stream()
                .map(totals -> CategorySummary.of(
                        Category.valueOf(totals.getCategory()),
                        totals.getIncome(),
                        totals.getExpenditure(),
                        totals.getTransactionCount()))
                .toList();
    }

    /**
     * Every month the period touches, in order. Months the database returned nothing for
     * are filled in as zeros rather than left out.
     */
    public List<MonthlySummary> summariseByMonth(AnalyticsScope scope, AnalyticsPeriod period) {
        require(scope);
        Map<YearMonth, AnalyticsRepository.MonthTotals> found = new HashMap<>();
        for (AnalyticsRepository.MonthTotals totals
                : analytics.summariseByMonth(scope.userId(), scope.accountId(), period.from(), period.to())) {
            found.put(YearMonth.from(totals.getMonth()), totals);
        }

        List<MonthlySummary> months = new ArrayList<>();
        for (YearMonth month : period.months()) {
            AnalyticsRepository.MonthTotals totals = found.get(month);
            months.add(totals == null
                    ? MonthlySummary.of(month, BigDecimal.ZERO, BigDecimal.ZERO, 0)
                    : MonthlySummary.of(month, totals.getIncome(), totals.getExpenditure(),
                            totals.getTransactionCount()));
        }
        return List.copyOf(months);
    }

    /**
     * Every account the user holds. An account filter is not accepted: narrowing a
     * breakdown by account to one account is what {@link #summarise} already answers.
     */
    public List<AccountSummary> summariseByAccount(UUID userId, AnalyticsPeriod period) {
        requireUser(userId);
        return analytics.summariseByAccount(userId, period.from(), period.to()).stream()
                .map(totals -> AccountSummary.of(
                        totals.getAccountId(),
                        totals.getProvider(),
                        totals.getAccountName(),
                        totals.getIncome(),
                        totals.getExpenditure(),
                        totals.getTransactionCount()))
                .toList();
    }

    public List<LargestExpense> findLargestExpenses(AnalyticsScope scope, AnalyticsPeriod period, int limit) {
        require(scope);
        if (limit < 1 || limit > MAX_EXPENSE_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_EXPENSE_LIMIT + ", was " + limit);
        }
        return analytics.findLargestExpenses(
                        scope.userId(), scope.accountId(), period.from(), period.to(), limit)
                .stream()
                .map(row -> new LargestExpense(
                        row.getTransactionId(),
                        row.getBookingDate(),
                        row.getDescription(),
                        row.getMerchant(),
                        row.getAmount(),
                        Category.valueOf(row.getCategory()),
                        row.getAccountId(),
                        row.getAccountName()))
                .toList();
    }

    /** This period against the equivalent one immediately before it. */
    public PeriodComparison compare(AnalyticsScope scope, AnalyticsPeriod period) {
        require(scope);
        return PeriodComparison.of(
                summariseWithin(scope, period),
                summariseWithin(scope, period.previous()));
    }

    private FinancialSummary summariseWithin(AnalyticsScope scope, AnalyticsPeriod period) {
        require(scope);
        AnalyticsRepository.Totals totals =
                analytics.summarise(scope.userId(), scope.accountId(), period.from(), period.to());
        return FinancialSummary.of(period, totals.getIncome(), totals.getExpenditure(),
                totals.getTransactionCount());
    }

    private void require(AnalyticsScope scope) {
        requireUser(scope.userId());
        if (scope.accountId() == null) {
            requireOneCurrency(scope.userId());
        }
        if (scope.accountId() != null) {
            // Resolved through the owner, so an account belonging to someone else is
            // indistinguishable from one that does not exist.
            Optional<Account> account = accounts.findById(scope.accountId())
                    .filter(candidate -> candidate.userId().equals(scope.userId()));
            if (account.isEmpty()) {
                throw new UnknownAnalyticsSubjectException(
                        "ACCOUNT_NOT_FOUND", "no account with id " + scope.accountId() + " for this user");
            }
        }
    }

    /**
     * Refuses a user-wide total that would have to add unlike currencies.
     *
     * <p>Only checked when no account was named: an account has one currency for its
     * life, so a figure narrowed to one account is always coherent. Converting would need
     * an exchange rate and a date, which is a feature rather than a default.
     */
    private void requireOneCurrency(UUID userId) {
        List<Currency> currencies = accounts.findCurrenciesByUser(userId);
        if (currencies.size() > 1) {
            throw new MixedCurrencyException(
                    "this user holds accounts in " + currencies.stream()
                            .map(Currency::getCurrencyCode).sorted().toList()
                            + ", which cannot be totalled together; ask for one account at a time");
        }
    }

    private void requireUser(UUID userId) {
        if (!users.existsById(userId)) {
            throw new UnknownAnalyticsSubjectException("USER_NOT_FOUND", "no user with id " + userId);
        }
    }
}
