package com.themba.synthforge.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity to be seeded on startup when synthforge is enabled
 * for the active profile. See synthforge-v1-spec.md section 9.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Seed {

    int count() default 10;
}
