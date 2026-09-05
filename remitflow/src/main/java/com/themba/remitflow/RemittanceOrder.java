package com.themba.remitflow;

import com.themba.synthforge.spring.Seed;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single payment instruction from this business to a beneficiary
 * Counterparty, over a Corridor. Fee and target amount are snapshotted at
 * creation (remitflow-v1-spec.md section 8 rule 3), never recomputed.
 *
 * <p>Note for anyone seeding this via SynthForge (@Seed below): the
 * generated feeAmount/targetAmount will not actually satisfy the section 8
 * arithmetic, since SynthForge generates each field independently and has
 * no notion of cross-field business rules — that computation belongs in
 * the service layer (section 4), not the seeder. This is expected and by
 * design (see remitflow-v1-spec.md section 6), not a SynthForge bug.</p>
 */
@Entity
@Seed(count = 100)
public class RemittanceOrder {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Counterparty beneficiary;

    @ManyToOne(optional = false)
    private Corridor corridor;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private BigDecimal feeAmount;

    @NotNull
    private BigDecimal targetAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Size(max = 35)
    private String reference;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Counterparty getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(Counterparty beneficiary) {
        this.beneficiary = beneficiary;
    }

    public Corridor getCorridor() {
        return corridor;
    }

    public void setCorridor(Corridor corridor) {
        this.corridor = corridor;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
