package com.themba.synthforge.demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * M2 validation from synthforge-v1-spec.md sections 9 and 11: with the
 * "test" profile active (listed under synthforge.enabled-profiles), the
 * autoconfiguration seeds all @Seed entities on startup in SeedGraph order,
 * so Counterparty rows exist before any Payment references one — no manual
 * SeedRunner invocation anywhere in this class.
 *
 * <p>Startup seeding commits, and the in-memory H2 database is shared by
 * name across contexts in the same JVM, so this context gets its own URL to
 * keep the M1 test's row counts unaffected.</p>
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:synthforge-demo-startup")
@ActiveProfiles("test")
@Transactional
class StartupSeedingIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private ApplicationRunner synthforgeSeedApplicationRunner;

    @Test
    void reRunningTheSeedRunnerSkipsAlreadySeededEntities() throws Exception {
        synthforgeSeedApplicationRunner.run(null);

        assertEquals(50, count("Counterparty"), "second run must skip, not double");
        assertEquals(200, count("Payment"), "second run must skip, not double");
    }

    @Test
    void startupSeedsAnnotatedCountsInDependencyOrder() {
        assertEquals(50, count("Counterparty"), "@Seed(count = 50) on Counterparty");
        assertEquals(200, count("Payment"), "@Seed(count = 200) on Payment");

        List<Payment> payments =
                em.createQuery("select p from Payment p", Payment.class).getResultList();
        for (Payment payment : payments) {
            assertNotNull(payment.getCounterparty(), "every Payment must reference a Counterparty");
            assertNotNull(payment.getCounterparty().getId(), "referenced Counterparty must be persisted");
        }
    }

    private long count(String entityName) {
        return em.createQuery("select count(e) from " + entityName + " e", Long.class)
                .getSingleResult();
    }
}
