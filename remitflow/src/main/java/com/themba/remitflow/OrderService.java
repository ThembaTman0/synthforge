package com.themba.remitflow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order creation implements remitflow-v1-spec.md section 8 rules 1-4:
 * beneficiary/corridor existence (404), the max-amount bound (422), and the
 * fee/target snapshot arithmetic, computed once here and never recomputed
 * afterward. Status transitions (rules 5-6) are M3, not implemented yet.
 */
@Service
public class OrderService {

    private final RemittanceOrderRepository orderRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final CorridorRepository corridorRepository;
    private final BigDecimal maxOrderAmount;

    public OrderService(RemittanceOrderRepository orderRepository,
                         CounterpartyRepository counterpartyRepository,
                         CorridorRepository corridorRepository,
                         @Value("${remitflow.max-order-amount}") BigDecimal maxOrderAmount) {
        this.orderRepository = orderRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.corridorRepository = corridorRepository;
        this.maxOrderAmount = maxOrderAmount;
    }

    public OrderResponse create(OrderCreateRequest request) {
        Counterparty beneficiary = counterpartyRepository.findById(request.beneficiaryId())
                .orElseThrow(() -> new NotFoundException(
                        "Counterparty " + request.beneficiaryId() + " not found"));
        Corridor corridor = corridorRepository.findById(request.corridorId())
                .orElseThrow(() -> new NotFoundException(
                        "Corridor " + request.corridorId() + " not found"));

        if (request.amount().compareTo(maxOrderAmount) > 0) {
            throw new BusinessRuleViolationException(
                    "amount " + request.amount() + " exceeds remitflow.max-order-amount ("
                            + maxOrderAmount + ")");
        }

        BigDecimal feeAmount = request.amount()
                .multiply(corridor.getFeePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal targetAmount = request.amount().subtract(feeAmount)
                .multiply(corridor.getExchangeRate())
                .setScale(2, RoundingMode.HALF_UP);

        RemittanceOrder order = new RemittanceOrder();
        order.setBeneficiary(beneficiary);
        order.setCorridor(corridor);
        order.setAmount(request.amount());
        order.setFeeAmount(feeAmount);
        order.setTargetAmount(targetAmount);
        order.setStatus(OrderStatus.CREATED);
        order.setReference(request.reference());
        order.setCreatedAt(LocalDateTime.now());

        return OrderResponse.from(orderRepository.save(order));
    }

    public OrderResponse findById(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("RemittanceOrder " + id + " not found"));
    }

    public List<OrderResponse> findAll(OrderStatus status) {
        List<RemittanceOrder> orders = status != null
                ? orderRepository.findByStatus(status)
                : orderRepository.findAll();
        return orders.stream().map(OrderResponse::from).toList();
    }

    /** Section 8 rules 5-6: CREATED -> SUBMITTED, only if the beneficiary is payable. */
    public OrderResponse submit(Long id) {
        RemittanceOrder order = getOrderOrThrow(id);
        requireStatus(order, OrderStatus.CREATED, OrderStatus.SUBMITTED);
        if (!order.getBeneficiary().isPayable()) {
            throw new BusinessRuleViolationException("beneficiary " + order.getBeneficiary().getId()
                    + " is not payable: iban and bic are both required to submit");
        }
        order.setStatus(OrderStatus.SUBMITTED);
        return OrderResponse.from(orderRepository.save(order));
    }

    /** Section 8 rule 5: SUBMITTED -> SETTLED. */
    public OrderResponse settle(Long id) {
        RemittanceOrder order = getOrderOrThrow(id);
        requireStatus(order, OrderStatus.SUBMITTED, OrderStatus.SETTLED);
        order.setStatus(OrderStatus.SETTLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    /** Section 8 rule 5: CREATED or SUBMITTED -> REJECTED. */
    public OrderResponse reject(Long id) {
        RemittanceOrder order = getOrderOrThrow(id);
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.SUBMITTED) {
            throw new IllegalTransitionException("cannot transition RemittanceOrder " + id
                    + " from " + order.getStatus() + " to REJECTED");
        }
        order.setStatus(OrderStatus.REJECTED);
        return OrderResponse.from(orderRepository.save(order));
    }

    private RemittanceOrder getOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("RemittanceOrder " + id + " not found"));
    }

    private void requireStatus(RemittanceOrder order, OrderStatus required, OrderStatus target) {
        if (order.getStatus() != required) {
            throw new IllegalTransitionException("cannot transition RemittanceOrder " + order.getId()
                    + " from " + order.getStatus() + " to " + target);
        }
    }
}
