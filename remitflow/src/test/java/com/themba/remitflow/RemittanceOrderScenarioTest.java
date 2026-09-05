package com.themba.remitflow;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Scenario-level, end-to-end coverage of RemitFlow's REST API per
 * remitflow-v1-spec.md section 10: a real embedded server (RANDOM_PORT)
 * called over plain HTTP with RestClient, exactly as an external caller
 * would, chaining create -> submit -> settle/reject.
 *
 * Valid input is drawn from SynthForge's own startup-seeded
 * Counterparty/Corridor rows rather than hand-built fixtures - that seeded
 * data is the thing under test as much as the API is. Deliberately-invalid
 * cases (unknown ids, an over-the-bound amount, a non-payable beneficiary,
 * illegal transitions) are still hand-built: SynthForge's fallback rule
 * (spec section 7 rule 4) guarantees every seeded Counterparty a non-null
 * iban/bic, so there is no seeded "invalid" row to draw on for those.
 *
 * This complements, not replaces, CounterpartyApiTest / CorridorApiTest /
 * OrderApiTest (MockMvc, synthetic fixtures, single-endpoint level) and
 * OrderServiceTest (no Spring context at all). Own H2 URL, like every
 * other test class in this module, so seeded/created rows here never
 * collide with another test class's counts.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:remitflow-scenario")
@ActiveProfiles("test")
class RemittanceOrderScenarioTest {

    private static final Logger log = LoggerFactory.getLogger(RemittanceOrderScenarioTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private CorridorRepository corridorRepository;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    /** Every seeded Counterparty is payable (see class javadoc); pick one. */
    private Long seededPayableBeneficiaryId() {
        return counterpartyRepository.findAll().stream()
                .filter(Counterparty::isPayable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "expected SynthForge to have seeded a payable Counterparty"))
                .getId();
    }

    private Long seededCorridorId() {
        return corridorRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("expected SynthForge to have seeded a Corridor"))
                .getId();
    }

    private OrderResponse createOrder(Long beneficiaryId, Long corridorId, String amount, String reference) {
        return client().post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OrderCreateRequest(beneficiaryId, corridorId, new BigDecimal(amount), reference))
                .retrieve()
                .body(OrderResponse.class);
    }

    @Test
    void happyPathCreateSubmitSettle() {
        Long beneficiaryId = seededPayableBeneficiaryId();
        Long corridorId = seededCorridorId();

        OrderResponse created = createOrder(beneficiaryId, corridorId, "100.00", "SCN-1");
        log.info("create: order {} status={}", created.id(), created.status());
        assertEquals(OrderStatus.CREATED, created.status());

        OrderResponse submitted = client().post().uri("/api/v1/orders/{id}/submit", created.id())
                .retrieve().body(OrderResponse.class);
        log.info("submit: order {} status={}", submitted.id(), submitted.status());
        assertEquals(OrderStatus.SUBMITTED, submitted.status());

        OrderResponse settled = client().post().uri("/api/v1/orders/{id}/settle", created.id())
                .retrieve().body(OrderResponse.class);
        log.info("settle: order {} status={}", settled.id(), settled.status());
        assertEquals(OrderStatus.SETTLED, settled.status());
    }

    @Test
    void happyPathCreateThenReject() {
        Long beneficiaryId = seededPayableBeneficiaryId();
        Long corridorId = seededCorridorId();

        OrderResponse created = createOrder(beneficiaryId, corridorId, "50.00", "SCN-2");

        OrderResponse rejected = client().post().uri("/api/v1/orders/{id}/reject", created.id())
                .retrieve().body(OrderResponse.class);
        log.info("reject: order {} status={}", rejected.id(), rejected.status());
        assertEquals(OrderStatus.REJECTED, rejected.status());
    }

    @Test
    void creatingWithUnknownBeneficiaryReturns404() {
        Long corridorId = seededCorridorId();

        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> createOrder(999999L, corridorId, "50.00", null));
        log.info("create with unknown beneficiary: HTTP {}", ex.getStatusCode());
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void creatingOverMaxAmountReturns422() {
        Long beneficiaryId = seededPayableBeneficiaryId();
        Long corridorId = seededCorridorId();

        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> createOrder(beneficiaryId, corridorId, "999999.00", null));
        log.info("create over max amount: HTTP {}", ex.getStatusCode());
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void submittingWithNonPayableBeneficiaryReturns422() {
        Counterparty unpayable = new Counterparty();
        unpayable.setCompanyName("No IBAN Scenario Ltd");
        Long unpayableId = counterpartyRepository.save(unpayable).getId();
        Long corridorId = seededCorridorId();

        OrderResponse created = createOrder(unpayableId, corridorId, "50.00", "SCN-3");

        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> client().post().uri("/api/v1/orders/{id}/submit", created.id())
                        .retrieve().body(OrderResponse.class));
        log.info("submit non-payable beneficiary: HTTP {}", ex.getStatusCode());
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void settlingBeforeSubmitReturns409() {
        Long beneficiaryId = seededPayableBeneficiaryId();
        Long corridorId = seededCorridorId();
        OrderResponse created = createOrder(beneficiaryId, corridorId, "50.00", "SCN-4");

        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> client().post().uri("/api/v1/orders/{id}/settle", created.id())
                        .retrieve().body(OrderResponse.class));
        log.info("settle before submit: HTTP {}", ex.getStatusCode());
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void rejectingTwiceReturns409OnSecondAttempt() {
        Long beneficiaryId = seededPayableBeneficiaryId();
        Long corridorId = seededCorridorId();
        OrderResponse created = createOrder(beneficiaryId, corridorId, "50.00", "SCN-5");
        client().post().uri("/api/v1/orders/{id}/reject", created.id()).retrieve().body(OrderResponse.class);

        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> client().post().uri("/api/v1/orders/{id}/reject", created.id())
                        .retrieve().body(OrderResponse.class));
        log.info("reject twice: HTTP {}", ex.getStatusCode());
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void creatingWithoutAmountReturns400() {
        Long beneficiaryId = seededPayableBeneficiaryId();
        Long corridorId = seededCorridorId();

        // hand-built raw JSON: a missing required field, exercising Bean
        // Validation rather than a domain rule.
        String body = "{\"beneficiaryId\":" + beneficiaryId + ",\"corridorId\":" + corridorId + "}";
        HttpClientErrorException ex = assertThrows(HttpClientErrorException.class,
                () -> client().post().uri("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class));
        log.info("create without amount: HTTP {}", ex.getStatusCode());
        assertEquals(400, ex.getStatusCode().value());
    }
}
