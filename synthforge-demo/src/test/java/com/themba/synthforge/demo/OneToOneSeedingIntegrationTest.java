package com.themba.synthforge.demo;

import com.themba.synthforge.core.SeedRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Owning-side {@code @OneToOne} support from spec section 8: the join
 * column is unique, so every seeded Account must reference a distinct
 * Profile, and seeding more children than there are unreferenced parents
 * must fail with a clear message rather than a constraint violation.
 * Hibernate's generated schema enforces the unique FK, so a duplicate
 * reference would also fail these tests on flush.
 */
@SpringBootTest
@Transactional
class OneToOneSeedingIntegrationTest {

    @PersistenceContext
    private EntityManager em;

    private final SeedRunner runner = new SeedRunner();

    @Test
    void eachChildReceivesItsOwnParent() {
        runner.seed(Profile.class, 10, em);
        runner.seed(Account.class, 10, em);
        em.flush();

        List<Account> accounts =
                em.createQuery("select a from Account a", Account.class).getResultList();
        assertEquals(10, accounts.size());

        Set<Long> profileIds = new HashSet<>();
        for (Account account : accounts) {
            assertNotNull(account.getProfile(), "every Account must reference a Profile");
            assertNotNull(account.getProfile().getId(), "referenced Profile must be persisted");
            assertTrue(profileIds.add(account.getProfile().getId()),
                    "each Account must reference a distinct Profile");
        }
    }

    @Test
    void failsClearlyWhenUnreferencedParentsRunOut() {
        runner.seed(Profile.class, 3, em);
        runner.seed(Account.class, 3, em);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> runner.seed(Account.class, 1, em));
        assertTrue(e.getMessage().contains("Profile"), "message should name the exhausted parent");
    }
}
