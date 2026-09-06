package com.fapp.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param targetAmount must be more than nothing
 * @param currency     ISO 4217; fixed for the goal's life
 * @param targetDate   when to have it by; the service refuses a date already past
 */
public record CreateSavingsGoalRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal targetAmount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$",
                message = "must be a three-letter ISO 4217 code") String currency,
        @NotNull LocalDate targetDate) {
}
