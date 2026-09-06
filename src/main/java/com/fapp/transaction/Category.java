package com.fapp.transaction;

/**
 * The analytical axis for spending and income. A closed set rather than a table: a
 * fixed taxonomy is what lets a category breakdown be proved to sum to the total, and
 * lets the compiler find every place that has to handle a new category.
 *
 * <p>Extensibility lives in the rules that assign categories, not in the taxonomy.
 * User-defined categories are deliberately out of scope for now.
 */
public enum Category {
    GROCERIES,
    RESTAURANTS,
    TRANSPORT,
    SUBSCRIPTIONS,
    BILLS,
    SHOPPING,
    ENTERTAINMENT,
    INCOME,
    TRANSFER,
    SAVINGS,
    /**
     * Assigned when no rule matched. Explicit rather than null so that aggregations
     * never have to special-case a missing category and always account for every penny.
     */
    UNCATEGORISED
}
