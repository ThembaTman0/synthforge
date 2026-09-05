package com.themba.remitflow;

/** Response body for the counterparty endpoints. Never the entity itself. */
public record CounterpartyResponse(
        Long id,
        String companyName,
        String email,
        String iban,
        String bic,
        String country) {

    static CounterpartyResponse from(Counterparty counterparty) {
        return new CounterpartyResponse(
                counterparty.getId(),
                counterparty.getCompanyName(),
                counterparty.getEmail(),
                counterparty.getIban(),
                counterparty.getBic(),
                counterparty.getCountry());
    }
}
