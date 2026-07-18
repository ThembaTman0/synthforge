package com.themba.synthforge.core;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the four-level resolution priority from synthforge-v1-spec.md
 * section 7: explicit annotation match beats field-name heuristic beats
 * type default beats fallback. FieldMetadata instances are built from the
 * annotated fields of the {@link Samples} holder below.
 */
class GeneratorRegistryTest {

    private final GeneratorRegistry registry = new GeneratorRegistry();
    private final GenerationContext context = new GenerationContext(42L);

    enum Status { NEW, SETTLED, FAILED }

    @SuppressWarnings("unused")
    private static class Samples {
        @Email
        private String phone;                    // annotation must beat the phone-number heuristic
        private String email;                    // heuristic must beat the plain-String type default
        private String surname;
        private String firstName;
        private String currency;
        private BigDecimal amount;
        private LocalDate valueDate;
        private Status status;
        private UUID token;
        private Instant createdAt;
        private OffsetDateTime settledAt;
        private ZonedDateTime bookedAt;
        private LocalTime cutoffTime;
        private Year fiscalYear;
        private YearMonth statementMonth;
        private BigInteger reference;
        private char category;
        private byte[] attachment;
        @Size(min = 5, max = 8)
        private String mystery;                  // no heuristic match: fallback bounded by @Size
        @Column(unique = true)
        private String code;                     // unique-guaranteed variant
    }

    private static FieldMetadata metadata(String fieldName) throws Exception {
        Field field = Samples.class.getDeclaredField(fieldName);
        Integer maxLength = null;
        Size size = field.getAnnotation(Size.class);
        if (size != null && size.max() != Integer.MAX_VALUE) {
            maxLength = size.max();
        }
        return new FieldMetadata(fieldName, field.getType(),
                List.of(field.getAnnotations()), true, maxLength);
    }

    @Test
    void registeredGeneratorTakesPrecedenceOverBuiltInRules() throws Exception {
        registry.register(new FieldGenerator<String>() {
            @Override
            public boolean supports(FieldMetadata field) {
                return field.getFieldName().equals("email");
            }

            @Override
            public String generate(FieldMetadata field, GenerationContext context) {
                return "fixed@example.com";
            }
        });
        assertEquals("fixed@example.com", registry.resolve(metadata("email"), context));
    }

    @Test
    void resolvesByAnnotationBeforeFieldNameHeuristic() throws Exception {
        // the field is named "phone" but carries @Email: annotation wins
        Object value = registry.resolve(metadata("phone"), context);
        assertInstanceOf(String.class, value);
        assertTrue(((String) value).contains("@"),
                "@Email must beat the phone-number heuristic, got: " + value);
    }

    @Test
    void resolvesByFieldNameHeuristicBeforeTypeDefault() throws Exception {
        // the type default for String is a bare alphanumeric string, which
        // can never contain '@'; the name heuristic must win
        Object value = registry.resolve(metadata("email"), context);
        assertTrue(((String) value).contains("@"), "expected an email address, got: " + value);
    }

    @Test
    void surnameHeuristicProducesAFamilyName() throws Exception {
        Object value = registry.resolve(metadata("surname"), context);
        assertInstanceOf(String.class, value);
        assertFalse(((String) value).isBlank());
    }

    @Test
    void currencyHeuristicProducesIso4217Code() throws Exception {
        Object value = registry.resolve(metadata("currency"), context);
        assertTrue(((String) value).matches("[A-Z]{3}"), "expected ISO 4217 code, got: " + value);
    }

    @Test
    void amountHeuristicProducesRealisticTwoDecimalBigDecimal() throws Exception {
        BigDecimal value = (BigDecimal) registry.resolve(metadata("amount"), context);
        assertEquals(2, value.scale());
        assertTrue(value.compareTo(context.amountMin()) >= 0, "below configured minimum: " + value);
        assertTrue(value.compareTo(context.amountMax()) <= 0, "above configured maximum: " + value);
    }

    @Test
    void enumTypeDefaultPicksOneOfTheConstants() throws Exception {
        Object value = registry.resolve(metadata("status"), context);
        assertInstanceOf(Status.class, value);
    }

    @Test
    void localDateTypeDefaultIsRecentWithinWindow() throws Exception {
        LocalDate value = (LocalDate) registry.resolve(metadata("valueDate"), context);
        assertNotNull(value);
        assertFalse(value.isAfter(LocalDate.now()));
        assertFalse(value.isBefore(LocalDate.now().minusDays(context.dateWindowDays())));
    }

    @Test
    void uuidTypeDefaultProducesUuid() throws Exception {
        assertInstanceOf(UUID.class, registry.resolve(metadata("token"), context));
    }

    @Test
    void instantTypeDefaultIsRecentWithinWindow() throws Exception {
        Instant value = (Instant) registry.resolve(metadata("createdAt"), context);
        Instant now = Instant.now();
        assertFalse(value.isAfter(now));
        assertFalse(value.isBefore(now.minus(context.dateWindowDays() + 1L, ChronoUnit.DAYS)));
    }

    @Test
    void offsetAndZonedDateTimeTypeDefaultsAreRecent() throws Exception {
        OffsetDateTime offset = (OffsetDateTime) registry.resolve(metadata("settledAt"), context);
        assertFalse(offset.isAfter(OffsetDateTime.now()));
        ZonedDateTime zoned = (ZonedDateTime) registry.resolve(metadata("bookedAt"), context);
        assertFalse(zoned.isAfter(ZonedDateTime.now()));
    }

    @Test
    void localTimeTypeDefaultProducesATimeOfDay() throws Exception {
        assertInstanceOf(LocalTime.class, registry.resolve(metadata("cutoffTime"), context));
    }

    @Test
    void yearAndYearMonthTypeDefaultsComeFromTheRecentWindow() throws Exception {
        Year year = (Year) registry.resolve(metadata("fiscalYear"), context);
        assertFalse(year.isAfter(Year.now()));
        YearMonth month = (YearMonth) registry.resolve(metadata("statementMonth"), context);
        assertFalse(month.isAfter(YearMonth.now()));
    }

    @Test
    void bigIntegerTypeDefaultIsSmallAndPositive() throws Exception {
        BigInteger value = (BigInteger) registry.resolve(metadata("reference"), context);
        assertTrue(value.signum() > 0, "expected a positive BigInteger, got: " + value);
    }

    @Test
    void charTypeDefaultIsAlphanumeric() throws Exception {
        char value = (char) registry.resolve(metadata("category"), context);
        assertTrue(Character.isLetterOrDigit(value), "expected alphanumeric char, got: " + value);
    }

    @Test
    void byteArrayTypeDefaultIsNonEmpty() throws Exception {
        byte[] value = (byte[]) registry.resolve(metadata("attachment"), context);
        assertNotNull(value);
        assertTrue(value.length > 0);
    }

    @Test
    void fallbackRespectsSizeBounds() throws Exception {
        for (int i = 0; i < 50; i++) {
            String value = (String) registry.resolve(metadata("mystery"), context);
            assertTrue(value.length() >= 5 && value.length() <= 8,
                    "expected length within @Size(min=5, max=8), got: " + value);
            assertTrue(value.matches("[A-Za-z0-9]+"), "expected alphanumeric, got: " + value);
        }
    }

    @Test
    void uniqueColumnNeverRepeatsAValue() throws Exception {
        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            assertTrue(seen.add(registry.resolve(metadata("code"), context)),
                    "duplicate value for @Column(unique = true) field at iteration " + i);
        }
    }
}
