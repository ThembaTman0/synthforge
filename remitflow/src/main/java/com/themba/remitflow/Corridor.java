package com.themba.remitflow;

import com.themba.synthforge.spring.Seed;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A supported source-to-target currency pair with its exchange rate and fee.
 * See remitflow-v1-spec.md section 6.
 */
@Entity
@Seed(count = 5)
public class Corridor {

    @Id
    @GeneratedValue
    private Long id;

    @NotNull
    @Size(min = 3, max = 3)
    private String sourceCurrency;

    @NotNull
    @Size(min = 3, max = 3)
    private String targetCurrency;

    @NotNull
    @Positive
    private BigDecimal exchangeRate;

    @NotNull
    private BigDecimal feePercent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public void setTargetCurrency(String targetCurrency) {
        this.targetCurrency = targetCurrency;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public BigDecimal getFeePercent() {
        return feePercent;
    }

    public void setFeePercent(BigDecimal feePercent) {
        this.feePercent = feePercent;
    }
}
