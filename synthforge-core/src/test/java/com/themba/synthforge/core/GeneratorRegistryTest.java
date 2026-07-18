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
        private String username;                 // contains "name": must NOT become a first name
        private String fullName;                 // contains "name": must become a full name
        private String companyName;              // contains "name": must become a company name
        private String emailAddress;             // contains "address": email must win
        private String ipAddress;                // contains "address": IPv4 must win
        private String shippingAddress;
        private String city;
        private String country;
        private String countryCode;              // contains "country": code must win
        private String postalCode;
        private String website;
        private String description;
        private String iban;
        private String bic;
        private String accountNumber;
        private String invoiceNumber;
        private LocalDate birthDate;             // must NOT come from the recent-date window
        private BigDecimal price;
        private BigDecimal vatRate;
        private Integer quantity;
        private Long stockLevel;
        private Double latitude;
        private Double longitude;
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
    void usernameBeatsTheGenericNameHeuristic() throws Exception {
        String value = (String) registry.resolve(metadata("username"), context);
        assertEquals(value.toLowerCase(), value, "expected a lowercase username, got: " + value);
        assertTrue(value.contains("."), "expected first.last style username, got: " + value);
    }

    @Test
    void fullNameHeuristicProducesFirstAndLastName() throws Exception {
        String value = (String) registry.resolve(metadata("fullName"), context);
        assertTrue(value.contains(" "), "expected a full name with a space, got: " + value);
    }

    @Test
    void emailAddressFieldGetsAnEmailNotAStreetAddress() throws Exception {
        String value = (String) registry.resolve(metadata("emailAddress"), context);
        assertTrue(value.contains("@"), "email must beat the address heuristic, got: " + value);
    }

    @Test
    void ipAddressFieldGetsAnIpv4NotAStreetAddress() throws Exception {
        String value = (String) registry.resolve(metadata("ipAddress"), context);
        assertTrue(value.matches("\\d{1,3}(\\.\\d{1,3}){3}"),
                "IPv4 must beat the address heuristic, got: " + value);
    }

    @Test
    void countryCodeBeatsTheCountryHeuristic() throws Exception {
        String value = (String) registry.resolve(metadata("countryCode"), context);
        assertTrue(value.matches("[A-Z]{2}"), "expected ISO country code, got: " + value);
    }

    @Test
    void addressFamilyHeuristicsProduceValues() throws Exception {
        for (String fieldName : List.of("shippingAddress", "city", "country", "postalCode",
                "companyName", "website", "description")) {
            String value = (String) registry.resolve(metadata(fieldName), context);
            assertNotNull(value, fieldName);
            assertFalse(value.isBlank(), fieldName);
        }
    }

    @Test
    void financeHeuristicsProduceWellFormedValues() throws Exception {
        String iban = (String) registry.resolve(metadata("iban"), context);
        assertTrue(iban.matches("[A-Z]{2}\\d{2}[A-Za-z0-9]+"), "expected IBAN, got: " + iban);
        String bic = (String) registry.resolve(metadata("bic"), context);
        assertTrue(bic.matches("[A-Z0-9]{8}([A-Z0-9]{3})?"), "expected BIC, got: " + bic);
        String account = (String) registry.resolve(metadata("accountNumber"), context);
        assertTrue(account.matches("\\d{10}"), "expected digit string, got: " + account);
        String invoice = (String) registry.resolve(metadata("invoiceNumber"), context);
        assertTrue(invoice.matches("[A-Z0-9]{10}"), "expected uppercase code, got: " + invoice);
    }

    @Test
    void birthDateHeuristicProducesAnAdultDateNotARecentOne() throws Exception {
        for (int i = 0; i < 20; i++) {
            LocalDate value = (LocalDate) registry.resolve(metadata("birthDate"), context);
            assertFalse(value.isAfter(LocalDate.now().minusYears(18)),
                    "birth date younger than 18: " + value);
            assertFalse(value.isBefore(LocalDate.now().minusYears(81)),
                    "birth date older than 81: " + value);
        }
    }

    @Test
    void priceJoinsTheConfigurableAmountFamily() throws Exception {
        BigDecimal value = (BigDecimal) registry.resolve(metadata("price"), context);
        assertEquals(2, value.scale());
        assertTrue(value.compareTo(context.amountMin()) >= 0);
        assertTrue(value.compareTo(context.amountMax()) <= 0);
    }

    @Test
    void rateHeuristicStaysWithinZeroToHundred() throws Exception {
        for (int i = 0; i < 20; i++) {
            BigDecimal value = (BigDecimal) registry.resolve(metadata("vatRate"), context);
            assertEquals(2, value.scale());
            assertTrue(value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0,
                    "expected 0..100, got: " + value);
        }
    }

    @Test
    void quantityAndStockHeuristicsProduceSmallPositiveCounts() throws Exception {
        for (int i = 0; i < 20; i++) {
            int quantity = (int) registry.resolve(metadata("quantity"), context);
            assertTrue(quantity >= 1 && quantity < 100, "expected 1..99, got: " + quantity);
            long stock = (long) registry.resolve(metadata("stockLevel"), context);
            assertTrue(stock >= 1 && stock < 100, "expected 1..99, got: " + stock);
        }
    }

    @Test
    void coordinateHeuristicsProduceValidRanges() throws Exception {
        for (int i = 0; i < 20; i++) {
            double latitude = (double) registry.resolve(metadata("latitude"), context);
            assertTrue(latitude >= -90 && latitude <= 90, "invalid latitude: " + latitude);
            double longitude = (double) registry.resolve(metadata("longitude"), context);
            assertTrue(longitude >= -180 && longitude <= 180, "invalid longitude: " + longitude);
        }
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
