package com.fapp.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A what-if question.
 *
 * <p>{@code from} and {@code to} say which real history to average from — the same
 * half-open window analytics uses, {@code from} inclusive and {@code to} exclusive — and
 * are required, because a projection whose basis is implicit is not checkable.
 *
 * @param accountId                narrow the baseline to one account, or omit for all
 * @param horizonMonths            how far ahead to project
 * @param oneOffPurchase           a single hypothetical outgoing; zero or more
 * @param monthlyExpenditureChange signed: negative models spending less
 * @param monthlyIncomeChange      signed
 * @param goalId                   report the effect on this goal, or omit
 */
public record SimulationRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        UUID accountId,
        @NotNull @Min(1) @Max(120) Integer horizonMonths,
        @DecimalMin("0") BigDecimal oneOffPurchase,
        BigDecimal monthlyExpenditureChange,
        BigDecimal monthlyIncomeChange,
        UUID goalId) {
}
