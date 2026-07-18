package com.themba.synthforge.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Persists generated entities via {@link EntityManager} directly; does not
 * require a JpaRepository for the entity being seeded. Owning-side
 * {@code @ManyToOne} fields are wired to a randomly chosen, already-persisted
 * parent row; owning-side {@code @OneToOne} fields receive a distinct parent
 * each, because the join column is unique. Parents must be seeded before
 * children (SeedGraph orders this automatically at startup).
 * Inverse and plural associations are skipped per spec section 8.
 *
 * <p>Runs inside the caller's transaction; this class does not manage
 * transactions itself.</p>
 */
public class SeedRunner {

    private final EntityScanner scanner = new EntityScanner();
    private final GeneratorRegistry registry;

    public SeedRunner() {
        this(new GeneratorRegistry());
    }

    public SeedRunner(GeneratorRegistry registry) {
        this.registry = registry;
    }

    public void seed(Class<?> entityClass, int count, EntityManager em) {
        seed(entityClass, count, em, new GenerationContext());
    }

    /**
     * Variant taking an explicit {@link GenerationContext}, which carries the
     * configurable knobs from spec section 7 (random seed for reproducible
     * runs, recent-date window, realistic amount range).
     */
    public void seed(Class<?> entityClass, int count, EntityManager em, GenerationContext context) {
        EntityType<?> entityType = em.getMetamodel().entity(entityClass);
        List<FieldMetadata> fields = scanner.scan(entityType);
        Map<Class<?>, List<?>> parentsByType = new HashMap<>();
        Map<String, Iterator<Object>> oneToOneParentsByField = new HashMap<>();

        for (int i = 0; i < count; i++) {
            Object entity = instantiate(entityClass);
            for (FieldMetadata field : fields) {
                if (hasAnnotation(field, GeneratedValue.class) || hasAnnotation(field, Version.class)) {
                    continue;
                }
                if (isOwningOneToOne(field)) {
                    setField(entity, field,
                            nextUnreferencedParent(field, entityClass, em, oneToOneParentsByField, context));
                    continue;
                }
                if (isManyToOne(field)) {
                    setField(entity, field, pickParent(field, em, parentsByType, context));
                    continue;
                }
                if (isAssociation(field)) {
                    continue; // inverse @OneToOne etc.: out of scope for V1, left null
                }
                Object value = registry.resolve(field, context);
                if (value != null) {
                    setField(entity, field, value);
                }
            }
            em.persist(entity);
        }
    }

    private boolean isManyToOne(FieldMetadata field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation instanceof ManyToOne) {
                return true;
            }
        }
        return false;
    }

    /** Owning-side @OneToOne (no mappedBy); the join column is unique. */
    private boolean isOwningOneToOne(FieldMetadata field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation instanceof OneToOne oneToOne && oneToOne.mappedBy().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A @OneToOne parent can back at most one child row, so parents are
     * handed out at most once each: parents not already referenced by an
     * existing child row are shuffled up front and consumed one per child.
     */
    private Object nextUnreferencedParent(FieldMetadata field, Class<?> childClass, EntityManager em,
                                          Map<String, Iterator<Object>> oneToOneParentsByField,
                                          GenerationContext context) {
        Iterator<Object> available = oneToOneParentsByField.computeIfAbsent(field.getFieldName(), name -> {
            List<Object> parents = new ArrayList<>(loadAll(field.getFieldType(), em));
            parents.removeAll(referencedParents(childClass, field, em));
            Collections.shuffle(parents, context.random());
            return parents.iterator();
        });
        if (!available.hasNext()) {
            throw new IllegalStateException("Cannot seed @OneToOne field '" + field.getFieldName()
                    + "' on " + childClass.getSimpleName() + ": each child needs its own "
                    + field.getFieldType().getSimpleName()
                    + " row and no unreferenced ones remain. Seed at least as many parents as children.");
        }
        return available.next();
    }

    /** Parents already taken by a persisted child row of this @OneToOne field. */
    private List<Object> referencedParents(Class<?> childClass, FieldMetadata field, EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<?> root = query.from(childClass);
        query.select(root.get(field.getFieldName()))
                .where(cb.isNotNull(root.get(field.getFieldName())));
        return em.createQuery(query).getResultList();
    }

    private boolean isAssociation(FieldMetadata field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation instanceof ManyToOne || annotation instanceof OneToOne
                    || annotation instanceof OneToMany || annotation instanceof ManyToMany) {
                return true;
            }
        }
        return false;
    }

    private Object pickParent(FieldMetadata field, EntityManager em,
                              Map<Class<?>, List<?>> parentsByType, GenerationContext context) {
        List<?> parents = parentsByType.computeIfAbsent(field.getFieldType(), type -> loadAll(type, em));
        if (parents.isEmpty()) {
            throw new IllegalStateException("Cannot seed field '" + field.getFieldName()
                    + "': no persisted " + field.getFieldType().getSimpleName()
                    + " rows exist to reference. Seed the parent entity first"
                    + " (SeedGraph orders this automatically at startup).");
        }
        return parents.get(context.random().nextInt(parents.size()));
    }

    private <T> List<T> loadAll(Class<T> type, EntityManager em) {
        CriteriaQuery<T> query = em.getCriteriaBuilder().createQuery(type);
        query.select(query.from(type));
        return em.createQuery(query).getResultList();
    }

    private boolean hasAnnotation(FieldMetadata field, Class<? extends Annotation> annotationType) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotationType.isInstance(annotation)) {
                return true;
            }
        }
        return false;
    }

    private Object instantiate(Class<?> entityClass) {
        try {
            return entityClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + entityClass.getName()
                    + "; seeded entities need an accessible no-arg constructor", e);
        }
    }

    private void setField(Object entity, FieldMetadata field, Object value) {
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            try {
                Field javaField = current.getDeclaredField(field.getFieldName());
                javaField.setAccessible(true);
                javaField.set(entity, value);
                return;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot set field '" + field.getFieldName()
                        + "' on " + entity.getClass().getName(), e);
            }
        }
        throw new IllegalStateException("Field '" + field.getFieldName()
                + "' not found on " + entity.getClass().getName());
    }
}
