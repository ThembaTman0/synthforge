package com.themba.synthforge.core;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Resolves a {@link FieldMetadata} to a generated value using the priority
 * order from synthforge-v1-spec.md section 7: explicit annotation match,
 * then field-name heuristic, then type default, then fallback random
 * string. User-registered generators take precedence over all built-in
 * rules. {@code @Column(unique = true)} wraps whichever rule matched in a
 * unique-guaranteed retry loop, and {@code @Size} bounds are enforced on
 * every generated string.
 */
public class GeneratorRegistry {

    private static final int MAX_UNIQUE_ATTEMPTS = 100;
    private static final int DEFAULT_STRING_LENGTH = 20;
    private static final String ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final List<FieldGenerator<?>> generators = new ArrayList<>();

    public void register(FieldGenerator<?> generator) {
        generators.add(generator);
    }

    public Object resolve(FieldMetadata field, GenerationContext context) {
        for (FieldGenerator<?> custom : generators) {
            if (custom.supports(field)) {
                return custom.generate(field, context);
            }
        }
        if (!isUniqueColumn(field)) {
            return generateOnce(field, context);
        }
        for (int attempt = 0; attempt < MAX_UNIQUE_ATTEMPTS; attempt++) {
            Object candidate = generateOnce(field, context);
            if (candidate == null || context.markUsedIfNew(field.getFieldName(), candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique value for field '"
                + field.getFieldName() + "' after " + MAX_UNIQUE_ATTEMPTS
                + " attempts; the value space is too small for the requested row count");
    }

    private Object generateOnce(FieldMetadata field, GenerationContext context) {
        Object value = byExplicitAnnotation(field, context);
        if (value == null) {
            value = byFieldNameHeuristic(field, context);
        }
        if (value == null) {
            value = byTypeDefault(field, context);
        }
        if (value == null) {
            value = fallback(field, context);
        }
        return applyStringBounds(value, field, context);
    }

    /** Spec section 7, rule 1: explicit Bean Validation / JPA annotation. */
    private Object byExplicitAnnotation(FieldMetadata field, GenerationContext context) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation instanceof Email) {
                return context.faker().internet().emailAddress();
            }
        }
        return null;
    }

    /** Spec section 7, rule 2: case-insensitive field-name substring match. */
    private Object byFieldNameHeuristic(FieldMetadata field, GenerationContext context) {
        String name = field.getFieldName().toLowerCase(Locale.ROOT);
        Class<?> type = field.getFieldType();
        if (type == String.class) {
            if (name.contains("surname") || name.contains("lastname")) {
                return context.faker().name().lastName();
            }
            if (name.contains("email")) {
                return context.faker().internet().emailAddress();
            }
            if (name.contains("phone")) {
                return context.faker().phoneNumber().phoneNumber();
            }
            if (name.contains("currency")) {
                return context.faker().money().currencyCode();
            }
            // "firstname" also contains "name", so a single check covers both
            if (name.contains("name")) {
                return context.faker().name().firstName();
            }
        }
        if (type == BigDecimal.class && (name.contains("amount") || name.contains("balance"))) {
            return randomAmount(context.amountMin(), context.amountMax(), context.random());
        }
        return null;
    }

    /** Spec section 7, rule 3: type default. */
    private Object byTypeDefault(FieldMetadata field, GenerationContext context) {
        Class<?> type = field.getFieldType();
        Random random = context.random();
        if (type == String.class) {
            return randomString(field, random);
        }
        if (type == LocalDate.class) {
            return LocalDate.now().minusDays(random.nextInt(context.dateWindowDays() + 1));
        }
        long windowMinutes = (long) context.dateWindowDays() * 24 * 60;
        if (type == LocalDateTime.class) {
            return LocalDateTime.now().minusMinutes(random.nextLong(windowMinutes + 1));
        }
        if (type == Instant.class) {
            return Instant.now().minus(Duration.ofMinutes(random.nextLong(windowMinutes + 1)));
        }
        if (type == OffsetDateTime.class) {
            return OffsetDateTime.now().minusMinutes(random.nextLong(windowMinutes + 1));
        }
        if (type == ZonedDateTime.class) {
            return ZonedDateTime.now().minusMinutes(random.nextLong(windowMinutes + 1));
        }
        if (type == LocalTime.class) {
            return LocalTime.ofSecondOfDay(random.nextInt(86_400));
        }
        if (type == Year.class) {
            return Year.of(LocalDate.now().minusDays(random.nextInt(context.dateWindowDays() + 1)).getYear());
        }
        if (type == YearMonth.class) {
            LocalDate recent = LocalDate.now().minusDays(random.nextInt(context.dateWindowDays() + 1));
            return YearMonth.of(recent.getYear(), recent.getMonth());
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants[random.nextInt(constants.length)];
        }
        if (type == BigDecimal.class) {
            // small positive value, two decimal places: 0.01 .. 999.99
            return BigDecimal.valueOf(random.nextLong(1, 100_000), 2);
        }
        if (type == BigInteger.class) {
            return BigInteger.valueOf(random.nextLong(1, 1_000_000));
        }
        if (type == Character.class || type == char.class) {
            return ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length()));
        }
        if (type == byte[].class) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            return bytes;
        }
        if (type == Integer.class || type == int.class) {
            return random.nextInt(1_000_000);
        }
        if (type == Long.class || type == long.class) {
            return random.nextLong(1_000_000);
        }
        if (type == Short.class || type == short.class) {
            return (short) random.nextInt(Short.MAX_VALUE + 1);
        }
        if (type == Byte.class || type == byte.class) {
            return (byte) random.nextInt(Byte.MAX_VALUE + 1);
        }
        if (type == Double.class || type == double.class) {
            return random.nextDouble() * 1_000;
        }
        if (type == Float.class || type == float.class) {
            return random.nextFloat() * 1_000;
        }
        if (type == Boolean.class || type == boolean.class) {
            return random.nextBoolean();
        }
        if (type == UUID.class) {
            return new UUID(random.nextLong(), random.nextLong());
        }
        return null;
    }

    /**
     * Spec section 7, rule 4: random alphanumeric string bounded by @Size if
     * present, otherwise a fixed default length. Only applies when the field
     * can actually hold a String; otherwise the field is left unset.
     */
    private Object fallback(FieldMetadata field, GenerationContext context) {
        if (!field.getFieldType().isAssignableFrom(String.class)) {
            return null;
        }
        return randomString(field, context.random());
    }

    private String randomString(FieldMetadata field, Random random) {
        int length = field.getMaxLength() != null
                ? Math.min(field.getMaxLength(), DEFAULT_STRING_LENGTH)
                : DEFAULT_STRING_LENGTH;
        return alphanumeric(length, random);
    }

    /** Truncates to @Size/@Column max and pads to @Size min where needed. */
    private Object applyStringBounds(Object value, FieldMetadata field, GenerationContext context) {
        if (!(value instanceof String s)) {
            return value;
        }
        Integer max = field.getMaxLength();
        int min = 0;
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation instanceof Size size) {
                min = size.min();
                if (max == null && size.max() != Integer.MAX_VALUE) {
                    max = size.max();
                }
            }
        }
        if (max != null && s.length() > max) {
            s = s.substring(0, max);
        }
        if (s.length() < min) {
            s = s + alphanumeric(min - s.length(), context.random());
        }
        return s;
    }

    private boolean isUniqueColumn(FieldMetadata field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation instanceof Column column && column.unique()) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal randomAmount(BigDecimal min, BigDecimal max, Random random) {
        long minCents = min.movePointRight(2).longValueExact();
        long maxCents = max.movePointRight(2).longValueExact();
        return BigDecimal.valueOf(random.nextLong(minCents, maxCents + 1), 2);
    }

    private String alphanumeric(int length, Random random) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
