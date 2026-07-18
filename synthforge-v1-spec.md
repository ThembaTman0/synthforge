# SynthForge V1: JPA-Aware Fake Data Seeding Library

## Technical Specification (Lean MVP, for direct implementation)

Status: draft v2 (standalone repository, RemitFlow does not need to exist yet)
Scope: this document is the entire spec for V1. Do not add features, modules, or
abstractions not listed here. If something seems missing, ask before inventing it.

---

## 1. Purpose

A small library that scans JPA entities and populates them with realistic,
relationship-consistent fake data, driven by field name, field type, and
Bean Validation / JPA annotations already present on the entity. It exists to
remove hand-written seed scripts and repetitive test-data builders from a
Spring Boot codebase.

This is its own standalone project from the start. RemitFlow (the B2B
cross-border remittance gateway API from the Spring Boot curriculum) does
not exist as a project yet, so this repository includes a small demo module
with its own entities to validate against in the meantime. When RemitFlow
starts, it is added as a sibling module in this same repository rather than
the other way around, see section 5.

## 2. Scope for V1

In scope:
- Scanning JPA entities via the JPA Metamodel API (not raw reflection)
- Generating primitive and common value-object fields (String, numeric types,
  LocalDate/LocalDateTime, enum, BigDecimal, boolean, UUID)
- Respecting `@NotNull`, `@Size`, `@Email`, `@Column(unique = true, nullable = false)`
  when generating values
- Resolving `@ManyToOne` and `@OneToOne` (owning side) relationships so seeded
  child entities always reference valid, already-persisted parents
- A Spring Boot autoconfiguration module with a single annotation to trigger
  seeding on startup in non-production profiles

Explicitly out of scope for V1 (see section 12):
- AI-based generation
- REST API, GraphQL API, CLI
- `@OneToMany` / `@ManyToMany` seeding
- Composite/embeddable primary keys
- Any banking-, ISO20022-, or country-specific "provider" packages
- Any website, branding, or enterprise edition

## 3. Problem Statement

Hand-written seed data (SQL scripts, `@BeforeEach` builders, Testcontainers
init scripts) drifts from the entity model as fields change, and doesn't
respect relationships or constraints automatically. Generic fake-data
libraries (Java Faker, Datafaker) solve realistic *values* but have no
awareness of JPA relationships, so seeding a graph of related entities still
means writing manual wiring code.

## 4. Architecture Overview

```
+----------------------+
|  synthforge-core     |
|  - EntityScanner     |
|  - FieldMetadata     |
|  - GeneratorRegistry |
|  - SeedGraph         |
|  - SeedRunner        |
+----------------------+
          ^
          |
+----------------------+
|  synthforge-spring   |
|  - @Seed annotation  |
|  - AutoConfiguration |
|  - ApplicationRunner |
+----------------------+
          ^
          |
+----------------------+
|  synthforge-demo     |
|  - Counterparty      |
|  - Payment           |
+----------------------+
```

Three modules for V1: `synthforge-core`, `synthforge-spring`, and a demo
Spring Boot application (`synthforge-demo`) to validate against, since
RemitFlow does not exist as a project yet. Do not create `synthforge-cli`,
`synthforge-rest`, or any domain provider module.

## 5. Repository Layout

Standalone Maven multi-module project, not embedded inside anything else:

```
synthforge/
├── pom.xml                  (parent, packaging=pom)
├── synthforge-core/
│   └── src/main/java/com/themba/synthforge/core/...
├── synthforge-spring/
│   └── src/main/java/com/themba/synthforge/spring/...
└── synthforge-demo/
    └── src/main/java/com/themba/synthforge/demo/...
        (Counterparty, Payment: the example entities from section 9, used to
        validate the scanner and generators against something real since
        RemitFlow does not exist as a project yet)
```

When RemitFlow starts, add it as a new sibling module (`remitflow/`) in this
same reactor, depending on `synthforge-spring` directly. That is a module
addition, not a library extraction or a publish step, since the code never
has to leave this repository to be used there. Only consider publishing to
an external Maven repository (personal or public) if SynthForge proves
useful somewhere beyond these two projects, see the gate in section 11.

## 6. Core Interfaces

```java
public interface FieldGenerator<T> {
    boolean supports(FieldMetadata field);
    T generate(FieldMetadata field, GenerationContext context);
}
```

```java
public class FieldMetadata {
    private final String fieldName;
    private final Class<?> fieldType;
    private final List<Annotation> annotations;
    private final boolean nullable;
    private final Integer maxLength;
    // getters only, immutable
}
```

```java
public class GeneratorRegistry {
    void register(FieldGenerator<?> generator);
    Object resolve(FieldMetadata field, GenerationContext context);
    // resolution order: explicit annotation match > field-name heuristic
    // match > type-default match > fallback random alphanumeric string
}
```

```java
public class EntityScanner {
    // uses jakarta.persistence.metamodel.Metamodel obtained from the
    // injected EntityManagerFactory, so only JPA-managed attributes are
    // ever considered
    List<FieldMetadata> scan(EntityType<?> entityType);
}
```

```java
public class SeedGraph {
    // builds a dependency graph from @ManyToOne / @OneToOne owning-side
    // relationships across all registered entities, then returns a
    // topological order so parents are always seeded before children
    List<Class<?>> topologicalOrder(Metamodel metamodel);
}
```

```java
public class SeedRunner {
    void seed(Class<?> entityClass, int count, EntityManager em);
    void seed(Class<?> entityClass, int count, EntityManager em,
              GenerationContext context);
    // persists via EntityManager directly; does not require a
    // JpaRepository for the entity being seeded. The GenerationContext
    // overload carries the configurable knobs from section 7 (random
    // seed, recent-date window, amount range); the short form uses
    // defaults
}
```

## 7. Smart Field Detection Rules

Resolution priority, highest first:

1. Explicit Bean Validation / JPA annotation on the field
   - `@Email` -> email generator
   - `@Column(unique = true)` -> unique-guaranteed variant of whatever the
     next rule would have chosen
   - `@Size(max = n)` -> truncate/generate within that bound
2. Field name heuristic (case-insensitive substring match)
   - `firstName`, `name` -> person given name
   - `surname`, `lastName` -> person family name
   - `email` -> email address
   - `phone` -> phone number
   - `currency` -> ISO 4217 currency code
   - `amount`, `balance` -> BigDecimal within a configurable realistic range
3. Type default
   - `LocalDate` -> recent date within a configurable window
   - `enum` types -> random value from the enum's constants
   - `BigDecimal` -> small positive value, two decimal places
   - `String` with no other match -> random alphanumeric string
4. Fallback: random alphanumeric string bounded by `@Size` if present,
   otherwise a fixed default length.

Use Datafaker under the hood for the actual realistic value generation
(names, emails, addresses). Do not build a name/locale corpus from scratch;
that is explicitly out of scope for V1.

## 8. Relationship Handling for V1

- `@ManyToOne` and `@OneToOne` (owning side only): supported. The scanner
  detects the target entity type, and `SeedGraph` guarantees the target is
  seeded first so a valid managed reference can be attached. Because a
  one-to-one join column is unique, each seeded child receives a distinct,
  not-yet-referenced parent; the parent's seed count must therefore be at
  least the child's, and running out of unreferenced parents is an error.
- `@OneToMany`, `@ManyToMany`, and inverse `@OneToOne`: not handled in V1.
  If an entity has one of these, the field is simply skipped (left null or
  empty collection), not generated.
- Composite/embeddable primary keys: not handled in V1. Entities using them
  should be excluded from seeding for now.

## 9. Configuration Example

The entities below live in the `synthforge-demo` module (section 5), so
they're usable today without waiting for RemitFlow to exist.

```xml
<dependency>
    <groupId>com.themba</groupId>
    <artifactId>synthforge-spring-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

```java
@Entity
@Seed(count = 50)
public class Counterparty {
    @Id @GeneratedValue
    private Long id;

    @NotNull
    private String name;

    @Email
    private String email;

    private String currency;
}

@Entity
@Seed(count = 200)
public class Payment {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Counterparty counterparty;

    private BigDecimal amount;

    private LocalDate valueDate;
}
```

```yaml
synthforge:
  enabled-profiles: [dev, test]
  # optional generation knobs (defaults shown, all may be omitted):
  seed: 42                # fixed for reproducible data; omit for random
  date-window-days: 365
  amount-min: 1.00
  amount-max: 10000.00
```

The `synthforge.*` namespace is bound at runtime (with `Binder`, matching
how `enabled-profiles` has always been read) into a `SynthforgeProperties`
class in `synthforge-spring`, and the generation knobs flow into the
`GenerationContext` used for the whole startup run. A fixed `seed` makes
startup data reproducible across restarts; when omitted, a random seed is
chosen and logged so any run can be reproduced after the fact.

On startup in an enabled profile, the autoconfiguration finds all `@Seed`
entities, resolves them via `SeedGraph.topologicalOrder`, and runs
`SeedRunner.seed` for each in order. `Counterparty` rows exist before any
`Payment` row references one. An entity whose table already contains rows
is skipped (and the skip is logged), so restarting against a persistent
database does not accumulate duplicate seed data.

## 10. Testing Strategy

- Unit tests on `GeneratorRegistry` covering the four-level resolution
  priority in section 7, using mocked `FieldMetadata`.
- Unit test on `SeedGraph.topologicalOrder` using a small synthetic
  metamodel with a two- and three-level dependency chain, asserting parents
  always precede children.
- One integration test using an in-memory H2 database with the
  `Counterparty` / `Payment` example above, asserting:
  - the requested row counts are persisted
  - every `Payment.counterparty` reference is non-null and points to a
    persisted row
  - no unique constraint violations occur across repeated runs

## 11. Milestones

**M1**: `synthforge-core` only. `EntityScanner`, `GeneratorRegistry` with
rules from section 7, `SeedRunner` invoked manually from a test class inside
`synthforge-demo` against `Counterparty` and `Payment`. No Spring Boot
starter yet.

**M2**: `synthforge-spring` autoconfiguration, `@Seed` annotation, and
`SeedGraph` relationship ordering for `@ManyToOne` / `@OneToOne`. Used
day-to-day in `synthforge-demo`'s dev and test profiles.

**M3 gate** (only once M1 and M2 have genuinely proven useful against the
demo module, and RemitFlow exists with at least two real related entities):
add `remitflow/` as a new sibling module in this same reactor, with its own
`pom.xml` depending on `synthforge-spring`. This is a module addition, not
an extraction or a publish step, since SynthForge already lives in its own
repository.

**M4 gate** (further out, only if genuinely warranted): if SynthForge
proves useful across RemitFlow and some other, unrelated project, consider
publishing it to a personal or public Maven repository so it can be a
dependency without living in the same reactor. Naming, branding, a public
website, a CLI, or an "enterprise edition" are still not part of this
specification and should not be discussed until M4 has shipped and is in
use somewhere beyond these two projects.

## 12. Explicit Non-Goals (for now, not forever)

- AI-based generation
- Enterprise edition / closed-source tier
- Banking-, ISO20022-, or country-specific provider packages
- Public documentation website
- CLI tool
- REST or GraphQL module
- Support for databases other than H2 (demo module) and whatever RemitFlow
  ends up using once it exists

These may be worth revisiting after M4. They are not part of V1.

## 13. Notes for the implementing model

- Implement M1, then M2, in that order. Do not start M3 or M4 work.
- Do not introduce a module, class, or annotation that isn't named in this
  document. If a gap appears during implementation, stop and describe it
  rather than designing around it silently.
- Validate M1 and M2 against the `synthforge-demo` module's `Counterparty`
  and `Payment` entities. Do not wait for RemitFlow to exist to start this
  work, and do not create a `remitflow/` module until the M3 gate criteria
  are actually met.
- Use Datafaker (not hand-rolled random string logic) for realistic primitive
  values, per section 7.
