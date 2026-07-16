package com.themba.synthforge.core;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Immutable description of a single JPA-managed field, produced by
 * {@link EntityScanner} and consumed by {@link GeneratorRegistry}.
 * See synthforge-v1-spec.md section 6.
 */
public class FieldMetadata {

    private final String fieldName;
    private final Class<?> fieldType;
    private final List<Annotation> annotations;
    private final boolean nullable;
    private final Integer maxLength;

    public FieldMetadata(String fieldName, Class<?> fieldType, List<Annotation> annotations,
                          boolean nullable, Integer maxLength) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.annotations = annotations;
        this.nullable = nullable;
        this.maxLength = maxLength;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Class<?> getFieldType() {
        return fieldType;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public boolean isNullable() {
        return nullable;
    }

    public Integer getMaxLength() {
        return maxLength;
    }
}
