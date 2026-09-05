package com.themba.remitflow;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
 * MockMvc coverage for POST/GET /api/v1/orders, per remitflow-v1-spec.md
 * sections 7, 8 (creation rules 1-4) and 10. Submit/settle/reject (rules
 * 5-6) are M3 and not tested here.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:remitflow-order-api")
@AutoConfigureMockMvc
@Transactional
class OrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private CorridorRepository corridorRepository;

    private Long beneficiaryId;
    private Long corridorId;

    @BeforeEach
    void seedFixtures() {
        Counterparty beneficiary = new Counterparty();
        beneficiary.setCompanyName("Acme Exports");
        beneficiary.setEmail("ops@acme.example");
        beneficiary.setIban("DE89370400440532013000");
        beneficiary.setBic("COBADEFFXXX");
        beneficiary.setCountry("Germany");
        beneficiaryId = counterpartyRepository.save(beneficiary).getId();

        Corridor corridor = new Corridor();
        corridor.setSourceCurrency("EUR");
        corridor.setTargetCurrency("USD");
        corridor.setExchangeRate(new BigDecimal("1.08"));
        corridor.setFeePercent(new BigDecimal("2.5"));
        corridorId = corridorRepository.save(corridor).getId();
    }

    @Test
    void createSnapshotsFeeAndTargetAmountCorrectly() throws Exception {
        OrderCreateRequest request =
                new OrderCreateRequest(beneficiaryId, corridorId, new BigDecimal("1000.00"), "INV-1");

        // feeAmount = 1000.00 * 2.5 / 100 = 25.00
        // targetAmount = (1000.00 - 25.00) * 1.08 = 1053.00
        mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.feeAmount").value(25.00))
                .andExpect(jsonPath("$.targetAmount").value(1053.00));
    }

    @Test
    void creatingWithUnknownBeneficiaryReturns404() throws Exception {
        OrderCreateRequest request =
                new OrderCreateRequest(999999L, corridorId, new BigDecimal("100.00"), null);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingWithUnknownCorridorReturns404() throws Exception {
        OrderCreateRequest request =
                new OrderCreateRequest(beneficiaryId, 999999L, new BigDecimal("100.00"), null);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingOverMaxOrderAmountReturns422() throws Exception {
        OrderCreateRequest request =
                new OrderCreateRequest(beneficiaryId, corridorId, new BigDecimal("999999.00"), null);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void creatingWithoutAmountReturns400() throws Exception {
        String body = "{\"beneficiaryId\":" + beneficiaryId + ",\"corridorId\":" + corridorId + "}";

        mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fetchByIdAndFilterByStatus() throws Exception {
        OrderCreateRequest request =
                new OrderCreateRequest(beneficiaryId, corridorId, new BigDecimal("500.00"), "INV-2");

        String body = mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        OrderResponse created = objectMapper.readValue(body, OrderResponse.class);

        mockMvc.perform(get("/api/v1/orders/" + created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("INV-2"));

        mockMvc.perform(get("/api/v1/orders").param("status", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/orders").param("status", "SETTLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void fetchingUnknownOrderIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/999999"))
                .andExpect(status().isNotFound());
    }

    // --- M3: submit / settle / reject ---

    @Test
    void submitThenSettleFollowsTheLegalLifecycle() throws Exception {
        Long orderId = createOrder("INV-3");

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/settle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void rejectFromCreatedSucceeds() throws Exception {
        Long orderId = createOrder("INV-4");

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void settlingAnOrderThatWasNeverSubmittedReturns409() throws Exception {
        Long orderId = createOrder("INV-5");

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/settle"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectingAnAlreadyRejectedOrderReturns409() throws Exception {
        Long orderId = createOrder("INV-6");
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/reject")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/reject"))
                .andExpect(status().isConflict());
    }

    @Test
    void submittingWithANonPayableBeneficiaryReturns422() throws Exception {
        Counterparty unpayable = new Counterparty();
        unpayable.setCompanyName("No IBAN Ltd");
        Long unpayableId = counterpartyRepository.save(unpayable).getId();

        OrderCreateRequest request =
                new OrderCreateRequest(unpayableId, corridorId, new BigDecimal("100.00"), "INV-7");
        String body = mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readValue(body, OrderResponse.class).id();

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transitioningAnUnknownOrderReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/orders/999999/submit"))
                .andExpect(status().isNotFound());
    }

    private Long createOrder(String reference) throws Exception {
        OrderCreateRequest request =
                new OrderCreateRequest(beneficiaryId, corridorId, new BigDecimal("100.00"), reference);
        String body = mockMvc.perform(post("/api/v1/orders")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, OrderResponse.class).id();
    }
}
