package com.themba.remitflow;

import com.themba.synthforge.spring.Seed;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Beneficiary business a RemittanceOrder pays out to.
 * See remitflow-v1-spec.md section 6.
 */
@Entity
@Seed(count = 20)
public class Counterparty {

    @Id
    @GeneratedValue
    private Long id;

    @NotNull
    @Size(max = 120)
    private String companyName;

    @Email
    private String email;

    @Size(max = 34)
    private String iban;

    @Size(max = 11)
    private String bic;

    private String country;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
