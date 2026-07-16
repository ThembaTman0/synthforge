package com.themba.synthforge.demo;

import com.themba.synthforge.spring.Seed;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Example entity from synthforge-v1-spec.md section 9. Child side of the
 * Counterparty -> Payment relationship: every Payment must reference an
 * already-persisted Counterparty.
 */
@Entity
@Seed(count = 200)
public class Payment {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Counterparty counterparty;

    private BigDecimal amount;

    private LocalDate valueDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Counterparty getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(Counterparty counterparty) {
        this.counterparty = counterparty;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }
}
