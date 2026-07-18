package com.themba.synthforge.demo;

import com.themba.synthforge.core.GenerationContext;
import com.themba.synthforge.core.SeedRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 validation from synthforge-v1-spec.md sections 10 and 11: SeedRunner
 * invoked manually (parents first — SeedGraph ordering arrives in M2)
 * against the real Counterparty / Payment entities on in-memory H2.
 * Hibernate flush-time Bean Validation is active, so a value violating
 * @NotNull / @Email would fail these tests on flush.
 */
@SpringBootTest
@Transactional
class SeedRunnerIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    private final SeedRunner runner = new SeedRunner();

    @Test
    void seedsRequestedCountsWithValidParentReferences() {
        runner.seed(Counterparty.class, 50, em);
        runner.seed(Payment.class, 200, em);
        em.flush();

        assertEquals(50, count("Counterparty"));
        assertEquals(200, count("Payment"));

        List<Payment> payments =
                em.createQuery("select p from Payment p", Payment.class).getResultList();
        for (Payment payment : payments) {
            assertNotNull(payment.getCounterparty(), "every Payment must reference a Counterparty");
            assertNotNull(payment.getCounterparty().getId(), "referenced Counterparty must be persisted");
            assertNotNull(payment.getAmount());
            assertEquals(2, payment.getAmount().scale());
            assertNotNull(payment.getValueDate());
        }

        List<Counterparty> counterparties =
                em.createQuery("select c from Counterparty c", Counterparty.class).getResultList();
        for (Counterparty counterparty : counterparties) {
            assertNotNull(counterparty.getName(), "@NotNull name must be generated");
            assertTrue(counterparty.getEmail().contains("@"), "email heuristic must produce an address");
            assertTrue(counterparty.getCurrency().matches("[A-Z]{3}"), "currency heuristic must produce an ISO code");
        }
    }

    @Test
    void repeatedRunsDoNotViolateConstraints() {
        runner.seed(Counterparty.class, 50, em);
        runner.seed(Payment.class, 200, em);
        em.flush();
        runner.seed(Counterparty.class, 50, em);
        runner.seed(Payment.class, 200, em);
        em.flush();

        assertEquals(100, count("Counterparty"));
        assertEquals(400, count("Payment"));
    }

    @Test
    void explicitGenerationContextControlsAmountRangeAndDateWindow() {
        GenerationContext context = new GenerationContext(
                42L, 0, new BigDecimal("5.00"), new BigDecimal("5.00"));
        runner.seed(Counterparty.class, 5, em, context);
        runner.seed(Payment.class, 20, em, context);
        em.flush();

        List<Payment> payments =
                em.createQuery("select p from Payment p", Payment.class).getResultList();
        assertEquals(20, payments.size());
        for (Payment payment : payments) {
            assertEquals(new BigDecimal("5.00"), payment.getAmount(),
                    "amount must come from the configured range");
            assertEquals(LocalDate.now(), payment.getValueDate(),
                    "a zero-day window must pin valueDate to today");
        }
    }

    @Test
    void failsClearlyWhenParentsHaveNotBeenSeeded() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> runner.seed(Payment.class, 1, em));
        assertTrue(e.getMessage().contains("Counterparty"), "message should name the missing parent");
    }

    private long count(String entityName) {
        return em.createQuery("select count(e) from " + entityName + " e", Long.class)
                .getSingleResult();
    }
}
