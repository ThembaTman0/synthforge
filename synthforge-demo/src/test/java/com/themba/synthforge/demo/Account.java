package com.themba.synthforge.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

/**
 * Test-only entity: child side of the Profile <- Account owning
 * {@code @OneToOne}. The join column is unique, so each seeded Account
 * must receive a distinct Profile. See {@link Profile} for why this lives
 * in test sources.
 */
@Entity
public class Account {

    @Id
    @GeneratedValue
    private Long id;

    @OneToOne(optional = false)
    private Profile profile;

    private String currency;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
