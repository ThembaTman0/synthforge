package com.themba.synthforge.core;

import net.datafaker.Faker;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Shared state passed to generators during a single seeding run: one seeded
 * {@link Random}, a {@link Faker} built on it so runs are reproducible from
 * the seed, tracking of already-issued values for unique columns, and the
 * configurable ranges from synthforge-v1-spec.md section 7 (recent-date
 * window, realistic amount range).
 */
public class GenerationContext {

    public static final int DEFAULT_DATE_WINDOW_DAYS = 365;
    public static final BigDecimal DEFAULT_AMOUNT_MIN = new BigDecimal("1.00");
    public static final BigDecimal DEFAULT_AMOUNT_MAX = new BigDecimal("10000.00");

    private final Random random;
    private final Faker faker;
    private final Map<String, Set<Object>> usedValuesByField = new HashMap<>();
    private final int dateWindowDays;
    private final BigDecimal amountMin;
    private final BigDecimal amountMax;

    public GenerationContext() {
        this(new Random(), DEFAULT_DATE_WINDOW_DAYS, DEFAULT_AMOUNT_MIN, DEFAULT_AMOUNT_MAX);
    }

    public GenerationContext(long seed) {
        this(new Random(seed), DEFAULT_DATE_WINDOW_DAYS, DEFAULT_AMOUNT_MIN, DEFAULT_AMOUNT_MAX);
    }

    public GenerationContext(long seed, int dateWindowDays, BigDecimal amountMin, BigDecimal amountMax) {
        this(new Random(seed), dateWindowDays, amountMin, amountMax);
    }

    private GenerationContext(Random random, int dateWindowDays, BigDecimal amountMin, BigDecimal amountMax) {
        this.random = random;
        this.faker = new Faker(Locale.ENGLISH, random);
        this.dateWindowDays = dateWindowDays;
        this.amountMin = amountMin;
        this.amountMax = amountMax;
    }

    public Random random() {
        return random;
    }

    public Faker faker() {
        return faker;
    }

    public int dateWindowDays() {
        return dateWindowDays;
    }

    public BigDecimal amountMin() {
        return amountMin;
    }

    public BigDecimal amountMax() {
        return amountMax;
    }

    /**
     * Records the value as issued for the field, returning false if the same
     * value was already issued during this run. Used to honour
     * {@code @Column(unique = true)}.
     */
    public boolean markUsedIfNew(String fieldName, Object value) {
        return usedValuesByField.computeIfAbsent(fieldName, k -> new HashSet<>()).add(value);
    }
}
