package com.themba.synthforge.spring;

import com.themba.synthforge.core.GenerationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code synthforge.*} property namespace from spec section 9: which
 * profiles enable startup seeding, and the generation knobs passed through
 * to {@link GenerationContext}. Bound at runtime with {@code Binder} rather
 * than {@code @ConfigurationProperties}, matching how {@code enabled-profiles}
 * has always been read (the conditional-on-property route cannot match YAML
 * list syntax).
 */
public class SynthforgeProperties {

    private List<String> enabledProfiles = new ArrayList<>();

    /** Fixed random seed for reproducible data; null means pick one randomly. */
    private Long seed;

    private int dateWindowDays = GenerationContext.DEFAULT_DATE_WINDOW_DAYS;

    private BigDecimal amountMin = GenerationContext.DEFAULT_AMOUNT_MIN;

    private BigDecimal amountMax = GenerationContext.DEFAULT_AMOUNT_MAX;

    public List<String> getEnabledProfiles() {
        return enabledProfiles;
    }

    public void setEnabledProfiles(List<String> enabledProfiles) {
        this.enabledProfiles = enabledProfiles;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public int getDateWindowDays() {
        return dateWindowDays;
    }

    public void setDateWindowDays(int dateWindowDays) {
        this.dateWindowDays = dateWindowDays;
    }

    public BigDecimal getAmountMin() {
        return amountMin;
    }

    public void setAmountMin(BigDecimal amountMin) {
        this.amountMin = amountMin;
    }

    public BigDecimal getAmountMax() {
        return amountMax;
    }

    public void setAmountMax(BigDecimal amountMax) {
        this.amountMax = amountMax;
    }
}
