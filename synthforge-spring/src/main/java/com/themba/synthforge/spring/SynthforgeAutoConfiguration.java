package com.themba.synthforge.spring;

import com.themba.synthforge.core.GenerationContext;
import com.themba.synthforge.core.SeedGraph;
import com.themba.synthforge.core.SeedRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaQuery;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.List;
import java.util.Random;

/**
 * Runs SynthForge seeding on startup, per synthforge-v1-spec.md section 9:
 * when one of the profiles listed under {@code synthforge.enabled-profiles}
 * is active, all {@code @Seed}-annotated entities are seeded in
 * {@link SeedGraph} topological order (parents before children) inside a
 * single transaction. Entities whose table already contains rows are
 * skipped, so restarting against a persistent database does not
 * accumulate duplicates. When the property is absent or no listed profile is
 * active, or the application has no JPA {@link EntityManagerFactory},
 * startup is untouched.
 *
 * <p>The whole {@code synthforge.*} namespace is bound at runtime into
 * {@link SynthforgeProperties} (not via {@code @ConditionalOnProperty},
 * which cannot match YAML list syntax). The generation knobs (seed,
 * date window, amount range) flow into one shared
 * {@link GenerationContext} for the entire run, so a fixed
 * {@code synthforge.seed} reproduces identical data.</p>
 */
@AutoConfiguration
public class SynthforgeAutoConfiguration {

    private static final Log logger = LogFactory.getLog(SynthforgeAutoConfiguration.class);

    @Bean
    public ApplicationRunner synthforgeSeedApplicationRunner(
            ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider,
            Environment environment) {
        return args -> {
            SynthforgeProperties properties = Binder.get(environment)
                    .bind("synthforge", Bindable.of(SynthforgeProperties.class))
                    .orElseGet(SynthforgeProperties::new);
            List<String> enabledProfiles = properties.getEnabledProfiles();
            if (enabledProfiles.isEmpty()
                    || !environment.acceptsProfiles(Profiles.of(enabledProfiles.toArray(String[]::new)))) {
                return;
            }
            EntityManagerFactory entityManagerFactory = entityManagerFactoryProvider.getIfAvailable();
            if (entityManagerFactory == null) {
                logger.warn("synthforge.enabled-profiles is set but no EntityManagerFactory exists; skipping seeding");
                return;
            }
            seedAll(entityManagerFactory, properties);
        };
    }

    private long countRows(Class<?> entityClass, EntityManager em) {
        CriteriaQuery<Long> query = em.getCriteriaBuilder().createQuery(Long.class);
        query.select(em.getCriteriaBuilder().count(query.from(entityClass)));
        return em.createQuery(query).getSingleResult();
    }

    private void seedAll(EntityManagerFactory entityManagerFactory, SynthforgeProperties properties) {
        long randomSeed = properties.getSeed() != null ? properties.getSeed() : new Random().nextLong();
        GenerationContext context = new GenerationContext(randomSeed,
                properties.getDateWindowDays(), properties.getAmountMin(), properties.getAmountMax());
        logger.info("Seeding with random seed " + randomSeed
                + " (set synthforge.seed to this value to reproduce the run)");

        List<Class<?>> order = new SeedGraph().topologicalOrder(entityManagerFactory.getMetamodel());
        SeedRunner seedRunner = new SeedRunner();
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            for (Class<?> entityClass : order) {
                Seed seed = entityClass.getAnnotation(Seed.class);
                if (seed == null) {
                    continue;
                }
                long existing = countRows(entityClass, em);
                if (existing > 0) {
                    logger.info("Skipping " + entityClass.getSimpleName() + ": " + existing
                            + " rows already exist");
                    continue;
                }
                seedRunner.seed(entityClass, seed.count(), em, context);
                logger.info("Seeded " + seed.count() + " " + entityClass.getSimpleName() + " rows");
            }
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
