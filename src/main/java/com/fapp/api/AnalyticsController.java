package com.fapp.api;

import com.fapp.analytics.AccountSummary;
import com.fapp.analytics.AnalyticsPeriod;
import com.fapp.analytics.AnalyticsScope;
import com.fapp.analytics.AnalyticsService;
import com.fapp.analytics.CategorySummary;
import com.fapp.analytics.FinancialSummary;
import com.fapp.analytics.LargestExpense;
import com.fapp.analytics.MonthlySummary;
import com.fapp.analytics.PeriodComparison;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading a user's financial facts.
 *
 * <p>Every figure is calculated by {@link AnalyticsService}; nothing is added up here.
 * This resolves the request into a period and a scope and hands over.
 *
 * <p>{@code from} and {@code to} are required on every endpoint and never defaulted.
 * A dashboard that quietly picked "this month" would give a different answer tomorrow
 * for the same request, and a figure whose window is implicit is not a fact anybody can
 * check. They are ISO dates, {@code from} inclusive and {@code to} exclusive.
 *
 * <p>One set of endpoints serves both user-level and account-level questions through an
 * optional {@code accountId}, rather than a duplicate tree of account-scoped paths.
 */
@RestController
@RequestMapping("/api/users/{userId}/analytics")
class AnalyticsController {

    private final AnalyticsService analytics;

    AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** Income, expenditure, net savings and how many movements produced them. */
    @GetMapping("/summary")
    FinancialSummary summary(@PathVariable UUID userId,
                             @RequestParam(required = false) UUID accountId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.summarise(AnalyticsScope.of(userId, accountId), new AnalyticsPeriod(from, to));
    }

    /** Where the money went, broken down by category. */
    @GetMapping("/categories")
    List<CategorySummary> categories(@PathVariable UUID userId,
                                     @RequestParam(required = false) UUID accountId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.summariseByCategory(AnalyticsScope.of(userId, accountId), new AnalyticsPeriod(from, to));
    }

    /** Month by month across the requested window, including quiet months. */
    @GetMapping("/monthly")
    List<MonthlySummary> monthly(@PathVariable UUID userId,
                                 @RequestParam(required = false) UUID accountId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.summariseByMonth(AnalyticsScope.of(userId, accountId), new AnalyticsPeriod(from, to));
    }

    /**
     * Every account the user holds, with its own totals. Takes no {@code accountId}: a
     * one-account breakdown is what {@code /summary} already answers.
     */
    @GetMapping("/accounts")
    List<AccountSummary> accounts(@PathVariable UUID userId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.summariseByAccount(userId, new AnalyticsPeriod(from, to));
    }

    /** The biggest outgoings, most expensive first. */
    @GetMapping("/largest-expenses")
    List<LargestExpense> largestExpenses(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "" + AnalyticsService.DEFAULT_EXPENSE_LIMIT) int limit) {
        return analytics.findLargestExpenses(
                AnalyticsScope.of(userId, accountId), new AnalyticsPeriod(from, to), limit);
    }

    /** This window against the equivalent one immediately before it. */
    @GetMapping("/comparison")
    PeriodComparison comparison(@PathVariable UUID userId,
                                @RequestParam(required = false) UUID accountId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.compare(AnalyticsScope.of(userId, accountId), new AnalyticsPeriod(from, to));
    }
}
