package com.themba.remitflow;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc coverage for POST/GET /api/v1/corridors, per
 * remitflow-v1-spec.md sections 7 and 10.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:remitflow-corridor-api")
@AutoConfigureMockMvc
@Transactional
class CorridorApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenListRoundTrips() throws Exception {
        CorridorRequest request =
                new CorridorRequest("EUR", "USD", new BigDecimal("1.08"), new BigDecimal("2.5"));

        mockMvc.perform(post("/api/v1/corridors")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceCurrency").value("EUR"))
                .andExpect(jsonPath("$.targetCurrency").value("USD"));

        mockMvc.perform(get("/api/v1/corridors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceCurrency").value("EUR"));
    }

    @Test
    void creatingWithInvalidCurrencyLengthReturns400() throws Exception {
        CorridorRequest request =
                new CorridorRequest("EU", "USD", new BigDecimal("1.08"), new BigDecimal("2.5"));

        mockMvc.perform(post("/api/v1/corridors")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
