package com.themba.remitflow;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * RemitFlow M1 validation from remitflow-v1-spec.md sections 6, 9 and 11:
 * with the "test" profile active, SynthForge's autoconfiguration seeds all
 * @Seed entities on startup in SeedGraph order, so Counterparty and Corridor
 * rows exist before any RemittanceOrder references them. This is the first
 * in-reactor entity with two distinct owning-side @ManyToOne parents, so it
 * also doubles as SynthForge's first multi-parent-graph regression test
 * (see remitflow-v1-spec.md section 10).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StartupSeedingIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void startupSeedsAnnotatedCountsWithValidReferencesOnBothParents() {
        assertEquals(20, count("Counterparty"), "@Seed(count = 20) on Counterparty");
        assertEquals(5, count("Corridor"), "@Seed(count = 5) on Corridor");
        assertEquals(100, count("RemittanceOrder"), "@Seed(count = 100) on RemittanceOrder");

        List<RemittanceOrder> orders =
                em.createQuery("select o from RemittanceOrder o", RemittanceOrder.class).getResultList();
        for (RemittanceOrder order : orders) {
            assertNotNull(order.getBeneficiary(), "every order must reference a Counterparty");
            assertNotNull(order.getBeneficiary().getId(), "referenced Counterparty must be persisted");
            assertNotNull(order.getCorridor(), "every order must reference a Corridor");
            assertNotNull(order.getCorridor().getId(), "referenced Corridor must be persisted");
        }
    }

    private long count(String entityName) {
        return em.createQuery("select count(e) from " + entityName + " e", Long.class)
                .getSingleResult();
    }
}
