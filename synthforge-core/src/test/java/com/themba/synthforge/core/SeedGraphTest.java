package com.themba.synthforge.core;

import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Covers synthforge-v1-spec.md section 10: topological ordering over a
 * small synthetic metamodel with two- and three-level dependency chains,
 * asserting parents always precede children.
 */
class SeedGraphTest {

    private final SeedGraph graph = new SeedGraph();

    // marker classes standing in for entities
    private static class Grandparent {}
    private static class Parent {}
    private static class Child {}
    private static class Unrelated {}

    @Test
    void twoLevelChainSeedsParentBeforeChild() {
        Metamodel metamodel = metamodel(
                entity(Parent.class, basicAttribute()),
                entity(Child.class, manyToOne(Parent.class)));

        List<Class<?>> order = graph.topologicalOrder(metamodel);

        assertEquals(2, order.size());
        assertTrue(order.indexOf(Parent.class) < order.indexOf(Child.class),
                "parent must precede child, got: " + order);
    }

    @Test
    void threeLevelChainKeepsFullAncestryOrder() {
        // declare in reverse order to prove sorting is not accidental
        Metamodel metamodel = metamodel(
                entity(Child.class, manyToOne(Parent.class)),
                entity(Parent.class, manyToOne(Grandparent.class)),
                entity(Grandparent.class, basicAttribute()),
                entity(Unrelated.class, basicAttribute()));

        List<Class<?>> order = graph.topologicalOrder(metamodel);

        assertEquals(4, order.size());
        assertTrue(order.indexOf(Grandparent.class) < order.indexOf(Parent.class),
                "grandparent must precede parent, got: " + order);
        assertTrue(order.indexOf(Parent.class) < order.indexOf(Child.class),
                "parent must precede child, got: " + order);
        assertTrue(order.contains(Unrelated.class), "entities without relationships still appear");
    }

    @Test
    void oneToOneOwningSideCreatesAnEdge() {
        Metamodel metamodel = metamodel(
                entity(Child.class, attribute(PersistentAttributeType.ONE_TO_ONE, Parent.class)),
                entity(Parent.class, basicAttribute()));

        List<Class<?>> order = graph.topologicalOrder(metamodel);

        assertTrue(order.indexOf(Parent.class) < order.indexOf(Child.class),
                "@OneToOne owning side must order parent first, got: " + order);
    }

    @Test
    void cycleIsReportedInsteadOfLoopingForever() {
        Metamodel metamodel = metamodel(
                entity(Parent.class, manyToOne(Child.class)),
                entity(Child.class, manyToOne(Parent.class)));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> graph.topologicalOrder(metamodel));
        assertTrue(e.getMessage().contains("cycle") || e.getMessage().contains("Cycle"));
    }

    private static Metamodel metamodel(EntityType<?>... entityTypes) {
        Metamodel metamodel = mock(Metamodel.class);
        doReturn(Set.of(entityTypes)).when(metamodel).getEntities();
        return metamodel;
    }

    private static EntityType<?> entity(Class<?> javaType, SingularAttribute<?, ?>... attributes) {
        EntityType<?> entityType = mock(EntityType.class);
        doReturn(javaType).when(entityType).getJavaType();
        doReturn(Set.of(attributes)).when(entityType).getSingularAttributes();
        return entityType;
    }

    private static SingularAttribute<?, ?> manyToOne(Class<?> target) {
        return attribute(PersistentAttributeType.MANY_TO_ONE, target);
    }

    private static SingularAttribute<?, ?> basicAttribute() {
        return attribute(PersistentAttributeType.BASIC, String.class);
    }

    private static SingularAttribute<?, ?> attribute(PersistentAttributeType type, Class<?> javaType) {
        SingularAttribute<?, ?> attribute = mock(SingularAttribute.class);
        doReturn(type).when(attribute).getPersistentAttributeType();
        doReturn(javaType).when(attribute).getJavaType();
        return attribute;
    }
}
