package com.themba.remitflow;

import java.math.BigDecimal;

/** Response body for the corridor endpoints. Never the entity itself. */
public record CorridorResponse(
        Long id,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal exchangeRate,
        BigDecimal feePercent) {

    static CorridorResponse from(Corridor corridor) {
        return new CorridorResponse(
                corridor.getId(),
                corridor.getSourceCurrency(),
                corridor.getTargetCurrency(),
                corridor.getExchangeRate(),
                corridor.getFeePercent());
    }
}
