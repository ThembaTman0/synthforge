package com.themba.remitflow;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request body for POST /api/v1/orders. See remitflow-v1-spec.md sections 6 and 8. */
public record OrderCreateRequest(
        @NotNull Long beneficiaryId,
        @NotNull Long corridorId,
        @NotNull @Positive BigDecimal amount,
        @Size(max = 35) String reference) {
}
