package com.themba.remitflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Response body for the order endpoints. Never the entity itself. */
public record OrderResponse(
        Long id,
        Long beneficiaryId,
        Long corridorId,
        BigDecimal amount,
        BigDecimal feeAmount,
        BigDecimal targetAmount,
        OrderStatus status,
        String reference,
        LocalDateTime createdAt) {

    static OrderResponse from(RemittanceOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getBeneficiary().getId(),
                order.getCorridor().getId(),
                order.getAmount(),
                order.getFeeAmount(),
                order.getTargetAmount(),
                order.getStatus(),
                order.getReference(),
                order.getCreatedAt());
    }
}
