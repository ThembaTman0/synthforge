package com.themba.remitflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests on OrderService per remitflow-v1-spec.md section 10: fee/target
 * arithmetic, the max-amount bound, every legal and every illegal status
 * transition (section 8 rules 5-6), and the payable-beneficiary rule. No
 * Spring context; repositories are mocked, so these run without a database.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private RemittanceOrderRepository orderRepository;
    @Mock
    private CounterpartyRepository counterpartyRepository;
    @Mock
    private CorridorRepository corridorRepository;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, counterpartyRepository, corridorRepository,
                new BigDecimal("50000.00"));
    }

    // --- creation: arithmetic and the max-amount bound ---

    @Test
    void createComputesFeeAndTargetAmountWithHalfUpRounding() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.of(payableBeneficiary()));
        when(corridorRepository.findById(1L)).thenReturn(Optional.of(corridor()));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreateRequest request = new OrderCreateRequest(1L, 1L, new BigDecimal("1000.00"), "INV-1");
        OrderResponse response = service.create(request);

        // feeAmount = 1000.00 * 2.5 / 100 = 25.00
        // targetAmount = (1000.00 - 25.00) * 1.08 = 1053.00
        assertEquals(new BigDecimal("25.00"), response.feeAmount());
        assertEquals(new BigDecimal("1053.00"), response.targetAmount());
        assertEquals(OrderStatus.CREATED, response.status());
    }

    @Test
    void createRejectsAmountOverMax() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.of(payableBeneficiary()));
        when(corridorRepository.findById(1L)).thenReturn(Optional.of(corridor()));

        OrderCreateRequest request = new OrderCreateRequest(1L, 1L, new BigDecimal("999999.00"), null);

        assertThrows(BusinessRuleViolationException.class, () -> service.create(request));
    }

    @Test
    void createRejectsUnknownBeneficiary() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.empty());

        OrderCreateRequest request = new OrderCreateRequest(1L, 1L, new BigDecimal("100.00"), null);

        assertThrows(NotFoundException.class, () -> service.create(request));
    }

    // --- submit: CREATED -> SUBMITTED, gated on a payable beneficiary ---

    @Test
    void submitTransitionsCreatedToSubmittedWhenBeneficiaryIsPayable() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(orderWithStatus(OrderStatus.CREATED, payableBeneficiary())));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(OrderStatus.SUBMITTED, service.submit(10L).status());
    }

    @Test
    void submitRejectsNonPayableBeneficiary() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(orderWithStatus(OrderStatus.CREATED, nonPayableBeneficiary())));

        assertThrows(BusinessRuleViolationException.class, () -> service.submit(10L));
    }

    @Test
    void submitRejectsEveryStatusOtherThanCreated() {
        for (OrderStatus from : new OrderStatus[]{OrderStatus.SUBMITTED, OrderStatus.SETTLED, OrderStatus.REJECTED}) {
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(orderWithStatus(from, payableBeneficiary())));
            assertThrows(IllegalTransitionException.class, () -> service.submit(10L),
                    "expected submit to reject from " + from);
        }
    }

    // --- settle: SUBMITTED -> SETTLED only ---

    @Test
    void settleTransitionsSubmittedToSettled() {
        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(orderWithStatus(OrderStatus.SUBMITTED, payableBeneficiary())));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(OrderStatus.SETTLED, service.settle(10L).status());
    }

    @Test
    void settleRejectsEveryStatusOtherThanSubmitted() {
        for (OrderStatus from : new OrderStatus[]{OrderStatus.CREATED, OrderStatus.SETTLED, OrderStatus.REJECTED}) {
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(orderWithStatus(from, payableBeneficiary())));
            assertThrows(IllegalTransitionException.class, () -> service.settle(10L),
                    "expected settle to reject from " + from);
        }
    }

    // --- reject: CREATED or SUBMITTED -> REJECTED ---

    @Test
    void rejectTransitionsFromCreatedOrSubmitted() {
        for (OrderStatus from : new OrderStatus[]{OrderStatus.CREATED, OrderStatus.SUBMITTED}) {
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(orderWithStatus(from, payableBeneficiary())));
            when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertEquals(OrderStatus.REJECTED, service.reject(10L).status(),
                    "expected reject to succeed from " + from);
        }
    }

    @Test
    void rejectRejectsEveryStatusOtherThanCreatedOrSubmitted() {
        for (OrderStatus from : new OrderStatus[]{OrderStatus.SETTLED, OrderStatus.REJECTED}) {
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(orderWithStatus(from, payableBeneficiary())));
            assertThrows(IllegalTransitionException.class, () -> service.reject(10L),
                    "expected reject to reject from " + from);
        }
    }

    @Test
    void transitioningAnUnknownOrderThrowsNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.submit(999L));
    }

    private Counterparty payableBeneficiary() {
        Counterparty beneficiary = new Counterparty();
        beneficiary.setId(1L);
        beneficiary.setIban("DE89370400440532013000");
        beneficiary.setBic("COBADEFFXXX");
        return beneficiary;
    }

    private Counterparty nonPayableBeneficiary() {
        Counterparty beneficiary = new Counterparty();
        beneficiary.setId(2L);
        return beneficiary; // no iban/bic
    }

    private Corridor corridor() {
        Corridor corridor = new Corridor();
        corridor.setId(1L);
        corridor.setSourceCurrency("EUR");
        corridor.setTargetCurrency("USD");
        corridor.setExchangeRate(new BigDecimal("1.08"));
        corridor.setFeePercent(new BigDecimal("2.5"));
        return corridor;
    }

    private RemittanceOrder orderWithStatus(OrderStatus status, Counterparty beneficiary) {
        RemittanceOrder order = new RemittanceOrder();
        order.setId(10L);
        order.setBeneficiary(beneficiary);
        order.setCorridor(corridor());
        order.setAmount(new BigDecimal("100.00"));
        order.setFeeAmount(new BigDecimal("2.50"));
        order.setTargetAmount(new BigDecimal("105.30"));
        order.setStatus(status);
        return order;
    }
}
