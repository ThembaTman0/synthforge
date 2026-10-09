# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

SynthForge: a JPA-aware fake data seeding library for Spring Boot. **`synthforge-v1-spec.md` is the complete, binding spec for V1** - read it before implementing anything. Its rules for this codebase:

- Do not add features, modules, classes, or annotations not named in the spec. If a gap appears, stop and describe it rather than designing around it.
- Milestones M1, M2, M3, and M4 are all done. `synthforge-core` and `synthforge-spring` 0.1.0 are published on Maven Central under `io.github.thembatman0` (see spec §11 for the groupId-casing history and the flatten-maven-plugin fix that publish needed). Publishing to Central is a one-way door: every future release needs a real version bump, a clean build, and the same signed-and-validated deploy path - there is no "just fix it in place." The four publish plugins (source/javadoc/gpg/central-publish/flatten) live behind a `release` Maven profile on `synthforge-core`/`synthforge-spring`, not the default build - a plain `mvn install`/CI never needs a GPG key; an actual publish needs `mvn deploy -Prelease -pl synthforge-core,synthforge-spring`.
- Out of scope for V1: `@OneToMany`/`@ManyToMany` seeding, composite keys, CLI, REST/GraphQL, AI generation, domain provider packages. Skip such fields; don't build support.
- Use Datafaker for realistic values, not hand-rolled random logic.

Current status: V1 is complete and published (M1-M4 done, 0.1.0 on Maven Central); the work now is adoption and feedback, not new features. CI tests the library on both Spring Boot 4.1 and 3.5. Startup seeding runs when a profile listed under `synthforge.enabled-profiles` is active; `SeedGraph` orders parents before children, and entities whose table already has rows are skipped (idempotent restarts). When invoking `SeedRunner` manually, seed parents first (`SeedRunner` throws `IllegalStateException` otherwise); owning `@OneToOne` children each consume a distinct parent, so the parent count must cover the child count. Note: the whole `synthforge.*` namespace (enabled-profiles plus the generation knobs `seed`, `date-window-days`, `amount-min`, `amount-max`) is bound at runtime with `Binder` into `SynthforgeProperties`, not `@ConditionalOnProperty` - the latter cannot match YAML list syntax. A fixed `synthforge.seed` reproduces identical startup data; when omitted, the chosen seed is logged.

Two other root documents govern related, non-code work: `remitflow-v1-spec.md` is the binding spec for the `remitflow` module - its own M1 (entities/seeding), M2 (REST create/read), and M3 (order lifecycle transitions) are all done; these are separate milestones from SynthForge's own M1-M4 above. `REBUILD-GUIDE.md` is a learning curriculum for reimplementing this library from scratch and is not part of the shipped library or referenced by any build/test command.

## Build and test

Maven multi-module reactor, Java 21, Spring Boot 4.1.0.

```
mvn clean install                                      # build everything
mvn -pl synthforge-core test                           # tests for one module
mvn -pl synthforge-core test -Dtest=GeneratorRegistryTest   # single test class
mvn -pl synthforge-demo spring-boot:run                # run the demo app (H2 in-memory)
```

CI (`.github/workflows/ci.yml`) has two jobs: `build` (full reactor on Boot 4.1) and `boot-3-5` (core, spring, demo on Boot 3.5.16 via `-Dspring-boot.version=3.5.16`, with the `remitflow` module removed from the reactor because it uses Boot 4-only test artifacts). The 3.5 version is pinned in the workflow, so Dependabot will not bump it. The git remote for this repo is named `synthforge`, not `origin`.

## Architecture

Three modules, strict dependency direction: `demo` → `spring` → `core`.

- **`synthforge-core`** (`com.themba.synthforge.core`): deliberately has **no Spring dependency** - only `jakarta.persistence-api` and Datafaker. Contains:
  - `EntityScanner` - reads JPA-managed attributes via the JPA Metamodel API (never raw reflection) into immutable `FieldMetadata`.
  - `GeneratorRegistry` + `FieldGenerator<T>` - resolves each field to a value using a strict priority order (spec §7): explicit Bean Validation/JPA annotation → field-name heuristic → type default → fallback random string bounded by `@Size`.
  - `SeedGraph` - topological order over `@ManyToOne`/`@OneToOne` owning-side relationships so parents are persisted before children.
  - `SeedRunner` - persists via `EntityManager` directly (no repository required).
- **`synthforge-spring`** (`com.themba.synthforge.spring`): `@Seed(count = n)` annotation and `SynthforgeAutoConfiguration` (registered via `META-INF/spring/...AutoConfiguration.imports`). On startup in a profile listed under the `synthforge.enabled-profiles` property, it finds `@Seed` entities, orders them with `SeedGraph`, and runs `SeedRunner` for each.
- **`synthforge-demo`**: minimal Spring Boot app with two related entities, `Counterparty` ← `Payment` (`@ManyToOne`), on H2. This is the validation target for M1/M2 - the spec's integration test (§10) asserts seeded row counts, non-null valid parent references, and no unique-constraint violations across repeated runs.
- **`remitflow`** (`com.themba.remitflow`): the real second consumer, whose own spec is `remitflow-v1-spec.md`. `Counterparty`/`Corridor` ← `RemittanceOrder` (two distinct owning-side `@ManyToOne`s on one child) is SynthForge's first multi-parent seeding case in a real reactor module. Deploy-skipped (`maven.deploy.skip=true`) - it never publishes alongside `synthforge-core`/`synthforge-spring` even after M4 (see spec §11).
