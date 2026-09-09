package com.fapp.analytics;

import com.fapp.account.Account;
import com.fapp.persistence.SeededDomainTest;
import com.fapp.transaction.Category;
import com.fapp.transaction.Transaction;
import com.fapp.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Every expected figure here is worked out by hand from the transactions seeded below,
 * so a wrong sum fails the test rather than being confirmed by whatever the code
 * happened to produce.
 *
 * <p>The dataset, all in sterling:
 *
 * <pre>
 *   Monzo Current                              BoS Current
 *   2026-08-01  +2000.00  INCOME               2026-08-05  -500.00  BILLS
 *   2026-08-03   -150.00  GROCERIES            2026-08-12   -60.00  TRANSPORT
 *   2026-08-10   -200.00  RESTAURANTS          2026-08-28   -60.00  TRANSPORT
 *   2026-08-20    -25.00  UNCATEGORISED
 *   2026-08-22    +30.00  SHOPPING
 *   2026-09-01   -100.00  GROCERIES
 * </pre>
 *
 * <p>August, meaning 2026-08-01 up to but excluding 2026-09-01:
 *
 * <pre>
 *   Monzo   income 2030.00  expenditure 375.00  net  1655.00  5 movements
 *   BoS     income    0.00  expenditure 620.00  net  -620.00  3 movements
 *   user    income 2030.00  expenditure 995.00  net  1035.00  8 movements
 * </pre>
 *
 * The 2026-09-01 row sits exactly on the exclusive end and must not appear in any of it.
 */
class AnalyticsServiceTest extends SeededDomainTest {

    private static final AnalyticsPeriod AUGUST = period("2026-08-01", "2026-09-01");
    private static final AnalyticsPeriod SEPTEMBER = period("2026-09-01", "2026-10-01");

    @Autowired
    private AnalyticsService analytics;

    private User owner;
    private Account monzo;
    private Account bos;
    private AnalyticsScope userScope;

    @BeforeEach
    void seedTheDataset() {
        owner = user("owner@example.com");
        monzo = account(owner, "monzo", "Monzo Current");
        bos = account(owner, "bank_of_scotland", "BoS Current");
        userScope = AnalyticsScope.ofUser(owner.id());

        seed(monzo,
                row("2026-08-01", "2000.00", Category.INCOME, "SALARY"),
                row("2026-08-03", "-150.00", Category.GROCERIES, "GROCER"),
                row("2026-08-10", "-200.00", Category.RESTAURANTS, "RESTAURANT"),
                row("2026-08-20", "-25.00", Category.UNCATEGORISED, "MYSTERY"),
                row("2026-08-22", "30.00", Category.SHOPPING, "RETURNED ITEM"),
                row("2026-09-01", "-100.00", Category.GROCERIES, "GROCER SEPT"));
        seed(bos,
                row("2026-08-05", "-500.00", Category.BILLS, "RENT"),
                row("2026-08-12", "-60.00", Category.TRANSPORT, "TRAIN"),
                row("2026-08-28", "-60.00", Category.TRANSPORT, "TRAIN"));
    }

    // --- summary ---

    @Test
    void addsUpTheWholeOfAUsersAugust() {
        FinancialSummary summary = analytics.summarise(userScope, AUGUST);

        assertThat(summary.income()).isEqualByComparingTo("2030.00");
        assertThat(summary.expenditure()).isEqualByComparingTo("995.00");
        assertThat(summary.netSavings()).isEqualByComparingTo("1035.00");
        assertThat(summary.transactionCount()).isEqualTo(8);
        assertThat(summary.period()).isEqualTo(AUGUST);
    }

    @Test
    void addsUpOneAccountOnItsOwn() {
        FinancialSummary monzoOnly = analytics.summarise(AnalyticsScope.of(owner.id(), monzo.id()), AUGUST);
        FinancialSummary bosOnly = analytics.summarise(AnalyticsScope.of(owner.id(), bos.id()), AUGUST);

        assertThat(monzoOnly.income()).isEqualByComparingTo("2030.00");
        assertThat(monzoOnly.expenditure()).isEqualByComparingTo("375.00");
        assertThat(monzoOnly.netSavings()).isEqualByComparingTo("1655.00");
        assertThat(monzoOnly.transactionCount()).isEqualTo(5);

        // Nothing came in on this account, so it is expenditure only and net is negative.
        assertThat(bosOnly.income()).isEqualByComparingTo("0.00");
        assertThat(bosOnly.expenditure()).isEqualByComparingTo("620.00");
        assertThat(bosOnly.netSavings()).isEqualByComparingTo("-620.00");
        assertThat(bosOnly.transactionCount()).isEqualTo(3);

        // The two accounts add up to the user.
        assertThat(monzoOnly.income().add(bosOnly.income())).isEqualByComparingTo("2030.00");
        assertThat(monzoOnly.expenditure().add(bosOnly.expenditure())).isEqualByComparingTo("995.00");
    }

    @Test
    void addsUpADayThatHeldOnlyIncome() {
        FinancialSummary salaryDay = analytics.summarise(userScope, period("2026-08-01", "2026-08-02"));

        assertThat(salaryDay.income()).isEqualByComparingTo("2000.00");
        assertThat(salaryDay.expenditure()).isEqualByComparingTo("0.00");
        assertThat(salaryDay.netSavings()).isEqualByComparingTo("2000.00");
        assertThat(salaryDay.transactionCount()).isEqualTo(1);
    }

    @Test
    void addsUpADayThatHeldOnlyExpenditure() {
        FinancialSummary grocerDay = analytics.summarise(userScope, period("2026-08-03", "2026-08-04"));

        assertThat(grocerDay.income()).isEqualByComparingTo("0.00");
        assertThat(grocerDay.expenditure()).isEqualByComparingTo("150.00");
        assertThat(grocerDay.netSavings()).isEqualByComparingTo("-150.00");
        assertThat(grocerDay.transactionCount()).isEqualTo(1);
    }

    @Test
    void reportsZerosRatherThanNullsForAPeriodWithNothingInIt() {
        FinancialSummary january = analytics.summarise(userScope, period("2026-01-01", "2026-02-01"));

        assertThat(january.income()).isEqualByComparingTo("0");
        assertThat(january.expenditure()).isEqualByComparingTo("0");
        assertThat(january.netSavings()).isEqualByComparingTo("0");
        assertThat(january.transactionCount()).isZero();
    }

    @Test
    void includesTheFirstDayAndExcludesTheLast() {
        // The salary lands on the inclusive start and the September grocer on the
        // exclusive end; August must contain the first and not the second.
        assertThat(analytics.summarise(userScope, AUGUST).transactionCount()).isEqualTo(8);
        assertThat(analytics.summarise(userScope, SEPTEMBER).expenditure()).isEqualByComparingTo("100.00");
        assertThat(analytics.summarise(userScope, SEPTEMBER).transactionCount()).isEqualTo(1);
    }

    @Test
    void neverMixesInAnotherUsersMoney() {
        User stranger = user("stranger@example.com");
        Account theirs = account(stranger, "monzo", "Their Monzo");
        seed(theirs,
                row("2026-08-04", "-9999.00", Category.SHOPPING, "NOT MINE"),
                row("2026-08-06", "8888.00", Category.INCOME, "ALSO NOT MINE"));

        FinancialSummary mine = analytics.summarise(userScope, AUGUST);
        assertThat(mine.income()).isEqualByComparingTo("2030.00");
        assertThat(mine.expenditure()).isEqualByComparingTo("995.00");
        assertThat(mine.transactionCount()).isEqualTo(8);

        FinancialSummary theirSummary = analytics.summarise(AnalyticsScope.ofUser(stranger.id()), AUGUST);
        assertThat(theirSummary.expenditure()).isEqualByComparingTo("9999.00");
        assertThat(theirSummary.income()).isEqualByComparingTo("8888.00");
    }

    @Test
    void refusesAnAccountThatBelongsToSomebodyElseTheSameWayAsOneThatDoesNotExist() {
        User stranger = user("stranger@example.com");
        Account theirs = account(stranger, "monzo", "Their Monzo");

        assertThatExceptionOfType(UnknownAnalyticsSubjectException.class)
                .isThrownBy(() -> analytics.summarise(AnalyticsScope.of(owner.id(), theirs.id()), AUGUST))
                .satisfies(e -> assertThat(e.code()).isEqualTo("ACCOUNT_NOT_FOUND"));

        assertThatExceptionOfType(UnknownAnalyticsSubjectException.class)
                .isThrownBy(() -> analytics.summarise(
                        AnalyticsScope.of(owner.id(), UUID.randomUUID()), AUGUST))
                .satisfies(e -> assertThat(e.code()).isEqualTo("ACCOUNT_NOT_FOUND"));

        assertThatExceptionOfType(UnknownAnalyticsSubjectException.class)
                .isThrownBy(() -> analytics.summarise(AnalyticsScope.ofUser(UUID.randomUUID()), AUGUST))
                .satisfies(e -> assertThat(e.code()).isEqualTo("USER_NOT_FOUND"));
    }

    // --- internal transfers ---

    @Test
    void leavesARecordedInternalTransferOutOfIncomeAndExpenditure() {
        User saver = user("saver@example.com");
        Account current = account(saver, "monzo", "Monzo Current");
        Account pot = account(saver, "monzo", "Holiday Pot");
        Transaction out = seed(current,
                row("2026-08-10", "-500.00", Category.TRANSFER, "TO HOLIDAY POT")).get(0);
        Transaction in = seed(pot,
                row("2026-08-10", "500.00", Category.TRANSFER, "FROM CURRENT")).get(0);
        AnalyticsScope scope = AnalyticsScope.ofUser(saver.id());

        // Unlinked, the two legs look like real money moving in and out.
        FinancialSummary before = analytics.summarise(scope, AUGUST);
        assertThat(before.income()).isEqualByComparingTo("500.00");
        assertThat(before.expenditure()).isEqualByComparingTo("500.00");
        assertThat(before.transactionCount()).isEqualTo(2);

        linkAsTransfer(out, in);

        // Recorded as a transfer, neither leg is income or expenditure: the money never
        // left the user's position.
        FinancialSummary after = analytics.summarise(scope, AUGUST);
        assertThat(after.income()).isEqualByComparingTo("0");
        assertThat(after.expenditure()).isEqualByComparingTo("0");
        assertThat(after.netSavings()).isEqualByComparingTo("0");
        assertThat(after.transactionCount()).isZero();

        // And it disappears from every other view too, so the totals still reconcile.
        assertThat(analytics.summariseByCategory(scope, AUGUST)).isEmpty();
        assertThat(analytics.findLargestExpenses(scope, AUGUST, 10)).isEmpty();
        assertThat(analytics.summariseByAccount(saver.id(), AUGUST))
                .allSatisfy(account -> assertThat(account.transactionCount()).isZero());
    }

    @Test
    void leavesASavingsPotMovementOutOfEveryOrdinaryTotal() {
        // A pot movement has no counterpart account to link a Transfer against, so it is
        // never a recorded transfer leg -- only its category says it is not ordinary
        // income or expenditure. Every headline total must honour that, not just the
        // category breakdown.
        User saver = user("pot-saver@example.com");
        Account current = account(saver, "monzo", "Monzo Current");
        seed(current,
                row("2026-08-04", "-40.00", Category.GROCERIES, "GROCER"),
                row("2026-08-15", "-300.00", Category.SAVINGS, "TO POT"));
        AnalyticsScope scope = AnalyticsScope.ofUser(saver.id());

        FinancialSummary summary = analytics.summarise(scope, AUGUST);
        assertThat(summary.expenditure()).isEqualByComparingTo("40.00");
        assertThat(summary.transactionCount()).isEqualTo(1);

        assertThat(analytics.summariseByMonth(scope, AUGUST))
                .singleElement()
                .satisfies(month -> assertThat(month.expenditure()).isEqualByComparingTo("40.00"));

        assertThat(analytics.summariseByAccount(saver.id(), AUGUST))
                .singleElement()
                .satisfies(account -> {
                    assertThat(account.expenditure()).isEqualByComparingTo("40.00");
                    assertThat(account.transactionCount()).isEqualTo(1);
                });

        assertThat(analytics.findLargestExpenses(scope, AUGUST, 10))
                .singleElement()
                .satisfies(expense -> assertThat(expense.amount()).isEqualByComparingTo("40.00"));

        // Still reported, correctly, as a pot movement.
        SavingsPot pot = analytics.summarisePot(scope, AUGUST);
        assertThat(pot.paidIn()).isEqualByComparingTo("300.00");
    }

    // --- categories ---

    @Test
    void breaksAugustDownByCategoryBiggestSpendFirst() {
        List<CategorySummary> categories = analytics.summariseByCategory(userScope, AUGUST);

        assertThat(categories).extracting(CategorySummary::category).containsExactly(
                Category.BILLS,
                Category.RESTAURANTS,
                Category.GROCERIES,
                Category.TRANSPORT,
                Category.UNCATEGORISED,
                Category.INCOME,
                Category.SHOPPING);

        assertThat(categoryNamed(categories, Category.BILLS).expenditure()).isEqualByComparingTo("500.00");
        assertThat(categoryNamed(categories, Category.RESTAURANTS).expenditure()).isEqualByComparingTo("200.00");
        assertThat(categoryNamed(categories, Category.GROCERIES).expenditure()).isEqualByComparingTo("150.00");
        // Two train fares of 60.00 each.
        assertThat(categoryNamed(categories, Category.TRANSPORT).expenditure()).isEqualByComparingTo("120.00");
        assertThat(categoryNamed(categories, Category.TRANSPORT).transactionCount()).isEqualTo(2);
        assertThat(categoryNamed(categories, Category.INCOME).income()).isEqualByComparingTo("2000.00");
    }

    @Test
    void keepsUncategorisedAsACategoryOfItsOwn() {
        CategorySummary uncategorised =
                categoryNamed(analytics.summariseByCategory(userScope, AUGUST), Category.UNCATEGORISED);

        assertThat(uncategorised.expenditure()).isEqualByComparingTo("25.00");
        assertThat(uncategorised.net()).isEqualByComparingTo("-25.00");
        assertThat(uncategorised.transactionCount()).isEqualTo(1);
    }

    @Test
    void reportsIncomeAndExpenditureSeparatelyWithinACategory() {
        // The returned item is positive but categorised as shopping, so it is income
        // inside a category that is normally spending, and is not netted away silently.
        CategorySummary shopping =
                categoryNamed(analytics.summariseByCategory(userScope, AUGUST), Category.SHOPPING);

        assertThat(shopping.income()).isEqualByComparingTo("30.00");
        assertThat(shopping.expenditure()).isEqualByComparingTo("0.00");
        assertThat(shopping.net()).isEqualByComparingTo("30.00");
    }

    @Test
    void makesTheCategoryTotalsAddUpToTheSummary() {
        List<CategorySummary> categories = analytics.summariseByCategory(userScope, AUGUST);
        FinancialSummary summary = analytics.summarise(userScope, AUGUST);

        assertThat(sum(categories.stream().map(CategorySummary::income).toList()))
                .isEqualByComparingTo(summary.income());
        assertThat(sum(categories.stream().map(CategorySummary::expenditure).toList()))
                .isEqualByComparingTo(summary.expenditure());
        assertThat(categories.stream().mapToLong(CategorySummary::transactionCount).sum())
                .isEqualTo(summary.transactionCount());
    }

    @Test
    void filtersCategoriesByDateAndByAccount() {
        assertThat(analytics.summariseByCategory(userScope, SEPTEMBER))
                .singleElement()
                .satisfies(only -> {
                    assertThat(only.category()).isEqualTo(Category.GROCERIES);
                    assertThat(only.expenditure()).isEqualByComparingTo("100.00");
                });

        assertThat(analytics.summariseByCategory(AnalyticsScope.of(owner.id(), bos.id()), AUGUST))
                .extracting(CategorySummary::category)
                .containsExactly(Category.BILLS, Category.TRANSPORT);

        assertThat(analytics.summariseByCategory(userScope, period("2026-01-01", "2026-02-01"))).isEmpty();
    }

    @Test
    void keepsCategoriesToTheRequestedUser() {
        User stranger = user("stranger@example.com");
        seed(account(stranger, "monzo", "Their Monzo"),
                row("2026-08-04", "-9999.00", Category.ENTERTAINMENT, "NOT MINE"));

        assertThat(analytics.summariseByCategory(userScope, AUGUST))
                .extracting(CategorySummary::category)
                .doesNotContain(Category.ENTERTAINMENT);
    }

    // --- monthly ---

    @Test
    void reportsEveryMonthInTheWindowIncludingQuietOnes() {
        List<MonthlySummary> months = analytics.summariseByMonth(userScope, period("2026-07-01", "2026-10-01"));

        assertThat(months).extracting(MonthlySummary::month).containsExactly(
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));

        MonthlySummary july = months.get(0);
        assertThat(july.income()).isEqualByComparingTo("0");
        assertThat(july.expenditure()).isEqualByComparingTo("0");
        assertThat(july.netSavings()).isEqualByComparingTo("0");
        assertThat(july.transactionCount()).isZero();

        MonthlySummary august = months.get(1);
        assertThat(august.income()).isEqualByComparingTo("2030.00");
        assertThat(august.expenditure()).isEqualByComparingTo("995.00");
        assertThat(august.netSavings()).isEqualByComparingTo("1035.00");
        assertThat(august.transactionCount()).isEqualTo(8);

        MonthlySummary september = months.get(2);
        assertThat(september.income()).isEqualByComparingTo("0");
        assertThat(september.expenditure()).isEqualByComparingTo("100.00");
        assertThat(september.netSavings()).isEqualByComparingTo("-100.00");
        assertThat(september.transactionCount()).isEqualTo(1);
    }

    @Test
    void putsTheBoundaryTransactionInTheMonthTheWindowSaysItIsIn() {
        // The 2026-09-01 row belongs to September, and August's figures do not move.
        assertThat(analytics.summariseByMonth(userScope, AUGUST))
                .singleElement()
                .satisfies(august -> {
                    assertThat(august.month()).isEqualTo(YearMonth.of(2026, 8));
                    assertThat(august.expenditure()).isEqualByComparingTo("995.00");
                });
    }

    @Test
    void reportsMonthlyForOneAccountAtATime() {
        assertThat(analytics.summariseByMonth(AnalyticsScope.of(owner.id(), bos.id()), AUGUST))
                .singleElement()
                .satisfies(august -> {
                    assertThat(august.expenditure()).isEqualByComparingTo("620.00");
                    assertThat(august.income()).isEqualByComparingTo("0.00");
                });
    }

    // --- accounts ---

    @Test
    void breaksAugustDownByAccount() {
        List<AccountSummary> byAccount = analytics.summariseByAccount(owner.id(), AUGUST);

        assertThat(byAccount).hasSize(2)
                .extracting(AccountSummary::accountName)
                .containsExactly("BoS Current", "Monzo Current");

        AccountSummary bosSummary = byAccount.get(0);
        assertThat(bosSummary.provider()).isEqualTo("bank_of_scotland");
        assertThat(bosSummary.accountId()).isEqualTo(bos.id());
        assertThat(bosSummary.income()).isEqualByComparingTo("0.00");
        assertThat(bosSummary.expenditure()).isEqualByComparingTo("620.00");
        assertThat(bosSummary.netSavings()).isEqualByComparingTo("-620.00");
        assertThat(bosSummary.transactionCount()).isEqualTo(3);

        AccountSummary monzoSummary = byAccount.get(1);
        assertThat(monzoSummary.provider()).isEqualTo("monzo");
        assertThat(monzoSummary.income()).isEqualByComparingTo("2030.00");
        assertThat(monzoSummary.expenditure()).isEqualByComparingTo("375.00");
        assertThat(monzoSummary.netSavings()).isEqualByComparingTo("1655.00");
        assertThat(monzoSummary.transactionCount()).isEqualTo(5);
    }

    @Test
    void stillListsAnAccountThatSawNoActivity() {
        Account dormant = account(owner, "monzo", "Dormant Savings");

        assertThat(analytics.summariseByAccount(owner.id(), AUGUST))
                .filteredOn(summary -> summary.accountId().equals(dormant.id()))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.income()).isEqualByComparingTo("0");
                    assertThat(summary.expenditure()).isEqualByComparingTo("0");
                    assertThat(summary.transactionCount()).isZero();
                });
    }

    @Test
    void keepsTheAccountBreakdownToTheRequestedUser() {
        User stranger = user("stranger@example.com");
        account(stranger, "monzo", "Their Monzo");

        assertThat(analytics.summariseByAccount(owner.id(), AUGUST))
                .extracting(AccountSummary::accountName)
                .containsExactly("BoS Current", "Monzo Current");
    }

    // --- largest expenses ---

    @Test
    void ordersTheBiggestOutgoingsFirstAndReportsThemAsPositive() {
        List<LargestExpense> expenses = analytics.findLargestExpenses(userScope, AUGUST, 10);

        assertThat(expenses).hasSize(6);
        assertThat(expenses).extracting(LargestExpense::amount).map(BigDecimal::toPlainString)
                .containsExactly("500.0000", "200.0000", "150.0000", "60.0000", "60.0000", "25.0000");

        LargestExpense biggest = expenses.get(0);
        assertThat(biggest.description()).isEqualTo("RENT");
        assertThat(biggest.merchant()).isEqualTo("RENT");
        assertThat(biggest.category()).isEqualTo(Category.BILLS);
        assertThat(biggest.accountId()).isEqualTo(bos.id());
        assertThat(biggest.accountName()).isEqualTo("BoS Current");
        assertThat(biggest.bookingDate()).isEqualTo(LocalDate.of(2026, 8, 5));

        // Equal amounts are broken by date, most recent first, so the order is total.
        assertThat(expenses.get(3).bookingDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(expenses.get(4).bookingDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    }

    @Test
    void neverCountsIncomeAsAnExpense() {
        assertThat(analytics.findLargestExpenses(userScope, AUGUST, 10))
                .extracting(LargestExpense::description)
                .doesNotContain("SALARY", "RETURNED ITEM");
    }

    @Test
    void returnsOnlyAsManyExpensesAsAsked() {
        assertThat(analytics.findLargestExpenses(userScope, AUGUST, 2))
                .extracting(LargestExpense::description)
                .containsExactly("RENT", "RESTAURANT");

        assertThat(analytics.findLargestExpenses(userScope, AUGUST, 1)).hasSize(1);
    }

    @Test
    void refusesANonsensicalLimit() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> analytics.findLargestExpenses(userScope, AUGUST, 0))
                .withMessageContaining("limit must be between");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> analytics.findLargestExpenses(userScope, AUGUST, 101))
                .withMessageContaining("limit must be between");
    }

    @Test
    void filtersExpensesByDateAndByAccount() {
        assertThat(analytics.findLargestExpenses(userScope, SEPTEMBER, 10))
                .singleElement()
                .satisfies(only -> {
                    assertThat(only.description()).isEqualTo("GROCER SEPT");
                    assertThat(only.amount()).isEqualByComparingTo("100.00");
                });

        assertThat(analytics.findLargestExpenses(AnalyticsScope.of(owner.id(), monzo.id()), AUGUST, 10))
                .extracting(LargestExpense::description)
                .containsExactly("RESTAURANT", "GROCER", "MYSTERY");

        assertThat(analytics.findLargestExpenses(userScope, period("2026-01-01", "2026-02-01"), 10)).isEmpty();
    }

    @Test
    void keepsExpensesToTheRequestedUser() {
        User stranger = user("stranger@example.com");
        seed(account(stranger, "monzo", "Their Monzo"),
                row("2026-08-04", "-9999.00", Category.SHOPPING, "NOT MINE"));

        assertThat(analytics.findLargestExpenses(userScope, AUGUST, 10))
                .extracting(LargestExpense::description)
                .doesNotContain("NOT MINE");
    }

    // --- comparison ---

    @Test
    void comparesSeptemberWithAugust() {
        PeriodComparison comparison = analytics.compare(userScope, SEPTEMBER);

        assertThat(comparison.current()).isEqualTo(SEPTEMBER);
        assertThat(comparison.previous()).isEqualTo(AUGUST);

        assertThat(comparison.income().current()).isEqualByComparingTo("0");
        assertThat(comparison.income().previous()).isEqualByComparingTo("2030.00");
        assertThat(comparison.income().change()).isEqualByComparingTo("-2030.00");
        assertThat(comparison.income().changePercent()).isEqualByComparingTo("-100.00");

        // 895.00 less spent on 995.00 is 89.9497...%, to two places 89.95.
        assertThat(comparison.expenditure().current()).isEqualByComparingTo("100.00");
        assertThat(comparison.expenditure().previous()).isEqualByComparingTo("995.00");
        assertThat(comparison.expenditure().change()).isEqualByComparingTo("-895.00");
        assertThat(comparison.expenditure().changePercent()).isEqualByComparingTo("-89.95");

        // 1135.00 worse on 1035.00 is 109.6618...%, to two places 109.66.
        assertThat(comparison.netSavings().current()).isEqualByComparingTo("-100.00");
        assertThat(comparison.netSavings().previous()).isEqualByComparingTo("1035.00");
        assertThat(comparison.netSavings().change()).isEqualByComparingTo("-1135.00");
        assertThat(comparison.netSavings().changePercent()).isEqualByComparingTo("-109.66");
    }

    @Test
    void reportsNoPercentageWhenThePreviousPeriodHeldNothing() {
        // August against an empty July: the change is real, the percentage is not.
        PeriodComparison comparison = analytics.compare(userScope, AUGUST);

        assertThat(comparison.previous()).isEqualTo(period("2026-07-01", "2026-08-01"));
        assertThat(comparison.income().change()).isEqualByComparingTo("2030.00");
        assertThat(comparison.income().changePercent()).isNull();
        assertThat(comparison.expenditure().change()).isEqualByComparingTo("995.00");
        assertThat(comparison.expenditure().changePercent()).isNull();
        assertThat(comparison.netSavings().changePercent()).isNull();
    }

    @Test
    void reportsNoChangeForTwoIdenticalMonths() {
        User steady = user("steady@example.com");
        Account theirs = account(steady, "monzo", "Monzo Current");
        seed(theirs,
                row("2026-07-05", "-80.00", Category.BILLS, "SAME BILL"),
                row("2026-08-05", "-80.00", Category.BILLS, "SAME BILL"));

        PeriodComparison comparison = analytics.compare(AnalyticsScope.ofUser(steady.id()), AUGUST);

        assertThat(comparison.expenditure().current()).isEqualByComparingTo("80.00");
        assertThat(comparison.expenditure().previous()).isEqualByComparingTo("80.00");
        assertThat(comparison.expenditure().change()).isEqualByComparingTo("0");
        assertThat(comparison.expenditure().changePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void readsAnImprovementFromAnOverdrawnMonthAsPositive() {
        User recovering = user("recovering@example.com");
        Account theirs = account(recovering, "monzo", "Monzo Current");
        seed(theirs,
                row("2026-07-05", "-100.00", Category.BILLS, "JULY BILL"),
                row("2026-08-05", "-50.00", Category.BILLS, "AUGUST BILL"));

        PeriodComparison comparison = analytics.compare(AnalyticsScope.ofUser(recovering.id()), AUGUST);

        assertThat(comparison.netSavings().previous()).isEqualByComparingTo("-100.00");
        assertThat(comparison.netSavings().current()).isEqualByComparingTo("-50.00");
        assertThat(comparison.netSavings().change()).isEqualByComparingTo("50.00");
        assertThat(comparison.netSavings().changePercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void comparesOneAccountRatherThanTheWholeUserWhenAsked() {
        PeriodComparison comparison =
                analytics.compare(AnalyticsScope.of(owner.id(), monzo.id()), SEPTEMBER);

        assertThat(comparison.expenditure().current()).isEqualByComparingTo("100.00");
        assertThat(comparison.expenditure().previous()).isEqualByComparingTo("375.00");
        assertThat(comparison.expenditure().change()).isEqualByComparingTo("-275.00");
    }

    // --- helpers ---

    private static AnalyticsPeriod period(String from, String to) {
        return new AnalyticsPeriod(LocalDate.parse(from), LocalDate.parse(to));
    }

    private static CategorySummary categoryNamed(List<CategorySummary> categories, Category category) {
        return categories.stream()
                .filter(summary -> summary.category() == category)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no summary for " + category));
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
