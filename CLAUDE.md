# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

SynthForge: a JPA-aware fake data seeding library for Spring Boot. **`synthforge-v1-spec.md` is the complete, binding spec for V1** — read it before implementing anything. Its rules for this codebase:

- Do not add features, modules, classes, or annotations not named in the spec. If a gap appears, stop and describe it rather than designing around it.
- Milestones M1, M2, and M3 are implemented. Do not start M4 (publishing) — it has explicit gate criteria that are not met.
- Out of scope for V1: `@OneToMany`/`@ManyToMany` seeding, composite keys, CLI, REST/GraphQL, AI generation, domain provider packages. Skip such fields; don't build support.
- Use Datafaker for realistic values, not hand-rolled random logic.

Current status: M1 and M2 are implemented and tested. Startup seeding runs when a profile listed under `synthforge.enabled-profiles` is active; `SeedGraph` orders parents before children, and entities whose table already has rows are skipped (idempotent restarts). When invoking `SeedRunner` manually, seed parents first (`SeedRunner` throws `IllegalStateException` otherwise); owning `@OneToOne` children each consume a distinct parent, so the parent count must cover the child count. Note: the whole `synthforge.*` namespace (enabled-profiles plus the generation knobs `seed`, `date-window-days`, `amount-min`, `amount-max`) is bound at runtime with `Binder` into `SynthforgeProperties`, not `@ConditionalOnProperty` — the latter cannot match YAML list syntax. A fixed `synthforge.seed` reproduces identical startup data; when omitted, the chosen seed is logged.

Two other root documents govern related, non-code work: `remitflow-v1-spec.md` is the binding spec for the `remitflow` module (M1 of that spec is done — entities, repositories, and startup seeding; its own M2/M3 REST and lifecycle milestones are separate from SynthForge's own milestones and not yet started); `REBUILD-GUIDE.md` is a learning curriculum for reimplementing this library from scratch and is not part of the shipped library or referenced by any build/test command.

## Build and test

Maven multi-module reactor, Java 21, Spring Boot 4.1.0.

```
mvn clean install                                      # build everything
mvn -pl synthforge-core test                           # tests for one module
mvn -pl synthforge-core test -Dtest=GeneratorRegistryTest   # single test class
mvn -pl synthforge-demo spring-boot:run                # run the demo app (H2 in-memory)
```

## Architecture

Three modules, strict dependency direction: `demo` → `spring` → `core`.

- **`synthforge-core`** (`com.themba.synthforge.core`): deliberately has **no Spring dependency** — only `jakarta.persistence-api` and Datafaker. Contains:
  - `EntityScanner` — reads JPA-managed attributes via the JPA Metamodel API (never raw reflection) into immutable `FieldMetadata`.
  - `GeneratorRegistry` + `FieldGenerator<T>` — resolves each field to a value using a strict priority order (spec §7): explicit Bean Validation/JPA annotation → field-name heuristic → type default → fallback random string bounded by `@Size`.
  - `SeedGraph` — topological order over `@ManyToOne`/`@OneToOne` owning-side relationships so parents are persisted before children.
  - `SeedRunner` — persists via `EntityManager` directly (no repository required).
- **`synthforge-spring`** (`com.themba.synthforge.spring`): `@Seed(count = n)` annotation and `SynthforgeAutoConfiguration` (registered via `META-INF/spring/...AutoConfiguration.imports`). On startup in a profile listed under the `synthforge.enabled-profiles` property, it finds `@Seed` entities, orders them with `SeedGraph`, and runs `SeedRunner` for each.
- **`synthforge-demo`**: minimal Spring Boot app with two related entities, `Counterparty` ← `Payment` (`@ManyToOne`), on H2. This is the validation target for M1/M2 — the spec's integration test (§10) asserts seeded row counts, non-null valid parent references, and no unique-constraint violations across repeated runs.
- **`remitflow`** (`com.themba.remitflow`): the real second consumer, whose own spec is `remitflow-v1-spec.md`. `Counterparty`/`Corridor` ← `RemittanceOrder` (two distinct owning-side `@ManyToOne`s on one child) is SynthForge's first multi-parent seeding case in a real reactor module. Deploy-skipped (`maven.deploy.skip=true`) — it never publishes alongside `synthforge-core`/`synthforge-spring` even after M4 (see spec §11).
