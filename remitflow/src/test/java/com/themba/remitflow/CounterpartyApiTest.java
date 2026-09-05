package com.themba.remitflow;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc coverage for POST/GET /api/v1/counterparties, per
 * remitflow-v1-spec.md sections 7 and 10. Own H2 URL and no active profile
 * (so SynthForge startup seeding does not run), keeping row counts
 * deterministic.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:remitflow-counterparty-api")
@AutoConfigureMockMvc
@Transactional
class CounterpartyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenFetchRoundTrips() throws Exception {
        CounterpartyRequest request = new CounterpartyRequest(
                "Acme Exports", "ops@acme.example", "DE89370400440532013000", "COBADEFFXXX", "Germany");

        String body = mockMvc.perform(post("/api/v1/counterparties")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.companyName").value("Acme Exports"))
                .andReturn().getResponse().getContentAsString();

        CounterpartyResponse created = objectMapper.readValue(body, CounterpartyResponse.class);

        mockMvc.perform(get("/api/v1/counterparties/" + created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Exports"));

        mockMvc.perform(get("/api/v1/counterparties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void fetchingUnknownIdReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/counterparties/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void creatingWithoutCompanyNameReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/counterparties")
                        .contentType("application/json")
                        .content("{\"email\":\"ops@acme.example\"}"))
                .andExpect(status().isBadRequest());
    }
}
