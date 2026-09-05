package com.themba.remitflow;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request body for POST /api/v1/corridors. See remitflow-v1-spec.md section 6. */
public record CorridorRequest(
        @NotNull @Size(min = 3, max = 3) String sourceCurrency,
        @NotNull @Size(min = 3, max = 3) String targetCurrency,
        @NotNull @Positive BigDecimal exchangeRate,
        @NotNull BigDecimal feePercent) {
}
