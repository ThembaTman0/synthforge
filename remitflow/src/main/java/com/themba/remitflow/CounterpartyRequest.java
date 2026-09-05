package com.themba.remitflow;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/v1/counterparties. See remitflow-v1-spec.md section 6. */
public record CounterpartyRequest(
        @NotNull @Size(max = 120) String companyName,
        @Email String email,
        @Size(max = 34) String iban,
        @Size(max = 11) String bic,
        String country) {
}
