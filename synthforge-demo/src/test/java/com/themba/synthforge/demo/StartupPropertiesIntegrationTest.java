package com.themba.synthforge.demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The synthforge.* generation knobs from spec section 9 must flow from
 * application properties into the startup run's GenerationContext: a
 * 5.00..5.00 amount range pins every Payment amount, and a zero-day date
 * window pins every valueDate to today. A fixed seed keeps the run
 * reproducible. Own H2 URL so other startup tests' row counts are
 * unaffected.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:synthforge-demo-properties",
        "synthforge.seed=42",
        "synthforge.date-window-days=0",
        "synthforge.amount-min=5.00",
        "synthforge.amount-max=5.00"
})
@ActiveProfiles("test")
@Transactional
class StartupPropertiesIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void generationKnobsFlowFromPropertiesIntoSeededData() {
        List<Payment> payments =
                em.createQuery("select p from Payment p", Payment.class).getResultList();
        assertEquals(200, payments.size(), "@Seed(count = 200) still applies");

        for (Payment payment : payments) {
            assertEquals(new BigDecimal("5.00"), payment.getAmount(),
                    "synthforge.amount-min/max must bound every amount");
            assertEquals(LocalDate.now(), payment.getValueDate(),
                    "synthforge.date-window-days=0 must pin valueDate to today");
        }
    }
}
