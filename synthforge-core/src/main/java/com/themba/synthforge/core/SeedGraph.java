package com.themba.synthforge.core;

import jakarta.persistence.OneToOne;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a dependency graph from @ManyToOne / @OneToOne (owning side)
 * relationships across all entities in the {@link Metamodel}, and returns a
 * topological order so parents always come before children. Inverse
 * {@code @OneToOne} (mappedBy) and plural associations create no edge, per
 * spec section 8. Self-referencing relationships are ignored for ordering
 * purposes (an entity cannot precede itself). The order is deterministic:
 * ties are broken by class name.
 */
public class SeedGraph {

    public List<Class<?>> topologicalOrder(Metamodel metamodel) {
        Set<Class<?>> entityClasses = new HashSet<>();
        for (EntityType<?> entityType : metamodel.getEntities()) {
            entityClasses.add(entityType.getJavaType());
        }

        Map<Class<?>, Set<Class<?>>> parentsOf = new HashMap<>();
        for (EntityType<?> entityType : metamodel.getEntities()) {
            Class<?> child = entityType.getJavaType();
            parentsOf.putIfAbsent(child, new HashSet<>());
            for (SingularAttribute<?, ?> attribute : entityType.getSingularAttributes()) {
                if (!isOwningToOne(attribute)) {
                    continue;
                }
                Class<?> parent = attribute.getJavaType();
                if (!parent.equals(child) && entityClasses.contains(parent)) {
                    parentsOf.get(child).add(parent);
                }
            }
        }

        List<Class<?>> order = new ArrayList<>();
        Set<Class<?>> remaining = new TreeSet<>(Comparator.comparing(Class::getName));
        remaining.addAll(entityClasses);
        while (!remaining.isEmpty()) {
            Class<?> next = remaining.stream()
                    .filter(candidate -> order.containsAll(parentsOf.get(candidate)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Relationship cycle detected among entities " + remaining
                                    + "; cyclic @ManyToOne/@OneToOne graphs cannot be seeded in V1"));
            order.add(next);
            remaining.remove(next);
        }
        return order;
    }

    /** Owning-side @ManyToOne, or @OneToOne without mappedBy. */
    private boolean isOwningToOne(SingularAttribute<?, ?> attribute) {
        PersistentAttributeType type = attribute.getPersistentAttributeType();
        if (type == PersistentAttributeType.MANY_TO_ONE) {
            return true;
        }
        if (type != PersistentAttributeType.ONE_TO_ONE) {
            return false;
        }
        Member member = attribute.getJavaMember();
        if (member instanceof Field field) {
            OneToOne oneToOne = field.getAnnotation(OneToOne.class);
            return oneToOne == null || oneToOne.mappedBy().isEmpty();
        }
        return true;
    }
}
