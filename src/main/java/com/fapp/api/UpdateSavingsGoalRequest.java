package com.fapp.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Everything about a goal that may change. The currency is deliberately absent: it is
 * fixed at creation, because changing it would reinterpret every figure already recorded.
 */
public record UpdateSavingsGoalRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal targetAmount,
        @NotNull @DecimalMin("0") BigDecimal currentAmount,
        @NotNull LocalDate targetDate) {
}
