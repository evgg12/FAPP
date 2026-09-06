package com.fapp.simulator;

import java.math.BigDecimal;

/**
 * Income, expenditure and what is left, per month.
 *
 * <p>Expenditure is positive, as everywhere else in FAPP: 250 means 250 spent.
 * {@code net} is {@code income - expenditure} and may be negative.
 */
public record MonthlyFigures(BigDecimal income, BigDecimal expenditure, BigDecimal net) {

    static MonthlyFigures of(BigDecimal income, BigDecimal expenditure) {
        return new MonthlyFigures(income, expenditure, income.subtract(expenditure));
    }
}
