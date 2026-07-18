# RemitFlow V1: B2B Cross-Border Remittance Gateway API

## Technical Specification (Lean MVP, for direct implementation)

Status: draft v1
Scope: this document is the entire spec for RemitFlow V1. Do not add
features, modules, or abstractions not listed here. If something seems
missing, ask before inventing it. The style and discipline mirror
`synthforge-v1-spec.md`, which this project also serves as the second
consumer for.

---

## 1. Purpose

A small B2B API through which a business submits cross-border payment
instructions ("remittance orders") to beneficiary businesses abroad. The
gateway validates orders against known counterparties and currency
corridors, snapshots fees and FX conversion at creation time, and tracks
each order through a simple status lifecycle.

RemitFlow is a Spring Boot curriculum project: its first job is to teach
real-world API design (domain modelling, validation, state transitions,
error handling, testing). Its second job is to be the consumer that opens
SynthForge's M3 gate — creating this module *is* the M3 act described in
`synthforge-v1-spec.md` section 11.

## 2. Scope for V1

In scope:
- Three JPA entities: `Counterparty`, `Corridor`, `RemittanceOrder`
  (section 6), persisted via Spring Data JPA on H2
- REST endpoints for creating and reading all three, plus order status
  transitions (section 7)
- Bean Validation on all inputs; fee and FX conversion snapshotted at
  order creation (section 8)
- SynthForge seeding of all three entities in dev and test profiles
- Unit tests for business rules, integration tests for the API surface

Explicitly out of scope for V1 (see section 12):
- Real FX rate feeds, real payment rails, or any banking integration
- Authentication / authorization
- ISO 20022, SWIFT messaging, compliance/KYC checks
- Webhooks, idempotency keys, pagination, rate limiting
- Any frontend

## 3. Problem Statement

Every payments-flavoured tutorial stops at CRUD. The interesting parts of
a real gateway are the rules: an order references a counterparty that must
exist and be payable, a corridor that must support the currency pair,
amounts that must be bounded, fees that must be snapshotted (not
recomputed when rates change), and a lifecycle in which illegal
transitions are rejected. V1 exists to build exactly those parts, small
enough to finish.

## 4. Architecture Overview

Single Spring Boot module, standard three layers, no speculative
abstraction:

```
controller  (REST, request/response records, validation)
    |
service     (business rules: corridor checks, fee snapshot, transitions)
    |
repository  (Spring Data JPA interfaces)
```

One module. Do not split into remitflow-core / remitflow-api / etc.

## 5. Repository Layout

RemitFlow is a sibling module in the SynthForge reactor, per
`synthforge-v1-spec.md` section 5:

```
synthforge/
├── pom.xml                  (add <module>remitflow</module>)
├── synthforge-core/
├── synthforge-spring/
├── synthforge-demo/
└── remitflow/
    └── src/main/java/com/themba/remitflow/...
```

`remitflow/pom.xml` depends on `synthforge-spring` (same reactor version)
and the Spring Boot starters it needs (web, data-jpa, validation, h2).
Creating this module satisfies SynthForge's M3 gate; update SynthForge's
README and CLAUDE.md status lines in the same commit.

## 6. Domain Model

Field names are chosen to be honest domain names first — that most of
them also hit SynthForge's field-name heuristics is the point of
dogfooding, not an accident.

```java
@Entity
@Seed(count = 20)
public class Counterparty {
    @Id @GeneratedValue
    private Long id;

    @NotNull @Size(max = 120)
    private String companyName;

    @Email
    private String email;

    @Size(max = 34)
    private String iban;

    @Size(max = 11)
    private String bic;

    private String country;
}
```

```java
@Entity
@Seed(count = 5)
public class Corridor {
    @Id @GeneratedValue
    private Long id;

    @NotNull @Size(min = 3, max = 3)
    private String sourceCurrency;   // ISO 4217

    @NotNull @Size(min = 3, max = 3)
    private String targetCurrency;   // ISO 4217

    @NotNull @Positive
    private BigDecimal exchangeRate; // target units per source unit

    @NotNull
    private BigDecimal feePercent;   // 0 .. 100
}
```

```java
@Entity
@Seed(count = 100)
public class RemittanceOrder {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Counterparty beneficiary;

    @ManyToOne(optional = false)
    private Corridor corridor;

    @NotNull @Positive
    private BigDecimal amount;       // in corridor.sourceCurrency

    @NotNull
    private BigDecimal feeAmount;    // snapshotted at creation

    @NotNull
    private BigDecimal targetAmount; // snapshotted at creation

    @NotNull @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Size(max = 35)
    private String reference;

    private LocalDateTime createdAt;
}

public enum OrderStatus { CREATED, SUBMITTED, SETTLED, REJECTED }
```

Note on seeded data: SynthForge will produce random statuses and
independently random fee/target amounts. That is acceptable for dev
browsing; tests that care about lifecycle correctness create their own
orders through the service.

## 7. API Surface

Base path `/api/v1`. JSON in/out, request and response types are Java
records, not entities.

| Method | Path | Behaviour |
|---|---|---|
| POST | `/counterparties` | create; 201 + body |
| GET | `/counterparties` | list all |
| GET | `/counterparties/{id}` | 200 or 404 |
| POST | `/corridors` | create; 201 + body |
| GET | `/corridors` | list all |
| POST | `/orders` | create CREATED order; runs section 8 rules; 201, 404 (unknown beneficiary/corridor), or 422 (rule violation) |
| GET | `/orders/{id}` | 200 or 404 |
| GET | `/orders?status=` | list, optional status filter |
| POST | `/orders/{id}/submit` | CREATED -> SUBMITTED; 200, 404, 409 (illegal transition), or 422 (beneficiary not payable) |
| POST | `/orders/{id}/settle` | SUBMITTED -> SETTLED; 200, 404, or 409 |
| POST | `/orders/{id}/reject` | CREATED or SUBMITTED -> REJECTED; 200, 404, or 409 |

Validation failures return Spring's standard Problem Details (RFC 9457)
via `@ControllerAdvice`; no custom error envelope.

## 8. Business Rules

At order creation:
1. Beneficiary and corridor must exist (404 otherwise).
2. `amount` must be positive and at most `remitflow.max-order-amount`
   (422 otherwise).
3. Snapshot at creation, never recomputed:
   `feeAmount = amount * feePercent / 100`,
   `targetAmount = (amount - feeAmount) * exchangeRate`,
   both rounded HALF_UP to 2 decimal places.
4. Status starts at CREATED; `createdAt` is set server-side.

At transitions:
5. Legal transitions are exactly: CREATED -> SUBMITTED,
   SUBMITTED -> SETTLED, CREATED -> REJECTED, SUBMITTED -> REJECTED.
   Anything else is 409.
6. `submit` additionally requires the beneficiary to be payable: iban
   and bic both present (422 otherwise).

## 9. Configuration Example

```yaml
remitflow:
  max-order-amount: 50000.00

synthforge:
  enabled-profiles: [dev, test]
  seed: 42
  amount-min: 10.00
  amount-max: 50000.00

spring:
  datasource:
    url: jdbc:h2:mem:remitflow
```

## 10. Testing Strategy

- Unit tests on the order service: fee/target snapshot arithmetic
  (including rounding), the max-amount bound, every legal and every
  illegal status transition, and the payable-beneficiary rule.
- MockMvc integration tests per endpoint: happy path plus the listed
  error statuses (404/409/422) and one Bean Validation 400.
- One startup test on the test profile asserting SynthForge seeded the
  three `@Seed` counts with all order references valid — this doubles as
  SynthForge's first in-reactor consumer regression test.

## 11. Milestones

**M1**: `remitflow/` module in the reactor, the three entities,
repositories, SynthForge seeding working in the dev profile (browsable
via H2 console). Creating this module is SynthForge's M3; update its
README and CLAUDE.md in the same commit.

**M2**: REST endpoints for counterparties, corridors, and order
create/read, with validation, Problem Details errors, and MockMvc tests.

**M3**: order lifecycle (submit/settle/reject) with the section 8 rules
and full unit-test coverage of transitions and arithmetic.

**M4 gate** (only after M1–M3 are done and used day-to-day): revisit the
section 12 non-goals — auth, idempotency, pagination — and promote at
most one at a time, spec amendment first.

## 12. Explicit Non-Goals (for now, not forever)

- Real FX feeds (rates live in the Corridor table, seeded or hand-set)
- Real payment execution, banking rails, SWIFT/ISO 20022
- Authentication, authorization, multi-tenancy
- Webhooks, idempotency keys, pagination, rate limiting
- Docker, deployment, CI beyond the reactor's existing workflow
- Any frontend

## 13. Notes for the implementing model

- Implement M1, then M2, then M3, in that order. Do not start M4 work.
- Do not introduce a module, class, endpoint, or entity field that is
  not named in this document; if a gap appears, stop and describe it.
- Package root is `com.themba.remitflow`; Java 21; versions come from
  the reactor parent pom.
- Entities are seeded by SynthForge in dev/test — do not write manual
  seed SQL or `CommandLineRunner` seeders.
- When SynthForge's generated data looks wrong for a field here, that
  is consumer feedback: record it in SynthForge's README backlog rather
  than working around it locally.
