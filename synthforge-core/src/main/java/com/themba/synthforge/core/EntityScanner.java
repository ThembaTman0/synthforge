package com.themba.synthforge.core;

import jakarta.persistence.Column;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scans a JPA {@link EntityType} via the Metamodel API and produces
 * {@link FieldMetadata} for each managed singular attribute, so only
 * JPA-managed attributes are ever considered. Plural attributes
 * (@OneToMany / @ManyToMany) are out of scope for V1 (spec section 8) and
 * are never surfaced. See synthforge-v1-spec.md section 6.
 */
public class EntityScanner {

    public List<FieldMetadata> scan(EntityType<?> entityType) {
        List<SingularAttribute<?, ?>> attributes = new ArrayList<>(entityType.getSingularAttributes());
        attributes.sort(Comparator.comparing(SingularAttribute::getName));

        List<FieldMetadata> result = new ArrayList<>();
        for (SingularAttribute<?, ?> attribute : attributes) {
            Member member = attribute.getJavaMember();
            if (!(member instanceof Field javaField)) {
                continue; // property (getter) access mapping is not supported in V1
            }
            List<Annotation> annotations = List.of(javaField.getAnnotations());
            result.add(new FieldMetadata(
                    attribute.getName(),
                    attribute.getJavaType(),
                    annotations,
                    isNullable(attribute, annotations),
                    maxLength(attribute, annotations)));
        }
        return result;
    }

    private boolean isNullable(SingularAttribute<?, ?> attribute, List<Annotation> annotations) {
        if (!attribute.isOptional()) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof NotNull) {
                return false;
            }
            if (annotation instanceof Column column && !column.nullable()) {
                return false;
            }
        }
        return true;
    }

    private Integer maxLength(SingularAttribute<?, ?> attribute, List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Size size && size.max() != Integer.MAX_VALUE) {
                return size.max();
            }
        }
        if (attribute.getJavaType() == String.class) {
            for (Annotation annotation : annotations) {
                if (annotation instanceof Column column) {
                    return column.length();
                }
            }
        }
        return null;
    }
}
