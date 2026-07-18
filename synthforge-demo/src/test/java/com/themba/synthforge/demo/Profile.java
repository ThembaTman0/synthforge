package com.themba.synthforge.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/**
 * Test-only entity: parent side of the Profile <- Account owning
 * {@code @OneToOne}, used to validate spec section 8's one-to-one support.
 * Lives in test sources so the demo app itself stays exactly the two
 * entities named in spec section 9. Deliberately not annotated with
 * {@code @Seed}: startup seeding must ignore it.
 */
@Entity
public class Profile {

    @Id
    @GeneratedValue
    private Long id;

    private String displayName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
