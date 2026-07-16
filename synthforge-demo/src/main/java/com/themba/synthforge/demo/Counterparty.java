package com.themba.synthforge.demo;

import com.themba.synthforge.spring.Seed;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

/**
 * Example entity from synthforge-v1-spec.md section 9. Parent side of the
 * Counterparty -> Payment relationship, so it must be seeded first.
 */
@Entity
@Seed(count = 50)
public class Counterparty {

    @Id
    @GeneratedValue
    private Long id;

    @NotNull
    private String name;

    @Email
    private String email;

    private String currency;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
