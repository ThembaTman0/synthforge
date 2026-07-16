package com.themba.synthforge.core;

/**
 * A strategy for generating a single field's value.
 * See synthforge-v1-spec.md sections 6 and 7.
 */
public interface FieldGenerator<T> {

    boolean supports(FieldMetadata field);

    T generate(FieldMetadata field, GenerationContext context);
}
