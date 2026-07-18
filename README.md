# SynthForge

A JPA-aware fake data seeding library for Spring Boot. Standalone project,
see `synthforge-v1-spec.md` for the full technical specification this
implementation should follow.

Positioning in one line: [Instancio](https://github.com/instancio/instancio)
generates objects for tests; SynthForge seeds databases for running apps.
Annotate an entity with `@Seed`, start your app in a dev profile, and the
database is populated with realistic, relationship-consistent rows — no
seeding code, correct parent-before-child ordering, profile-gated so it
can never touch production, and idempotent across restarts.

## Modules

- `synthforge-core`: entity scanning, generator resolution, seed graph, seed runner
- `synthforge-spring`: Spring Boot autoconfiguration and the `@Seed` annotation
- `synthforge-demo`: a small Spring Boot app with two related entities
  (`Counterparty`, `Payment`) used to validate the above against something
  real, since RemitFlow does not exist as a project yet

## Getting started

Requires Java 21. SynthForge is not published to a remote Maven
repository yet (see the M4 gate in the spec), so install it locally
first:

    git clone https://github.com/ThembaTman0/synthforge.git
    cd synthforge
    mvn install

Then, in your Spring Boot app:

1. Add the dependency:

   ```xml
   <dependency>
       <groupId>com.themba.synthforge</groupId>
       <artifactId>synthforge-spring</artifactId>
       <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```

2. Annotate the JPA entities you want fake rows for:

   ```java
   @Entity
   @Seed(count = 50)
   public class Counterparty { ... }
   ```

3. Enable seeding for the profiles where you want it (never production),
   plus optional generation knobs, in `application.yml`:

   ```yaml
   synthforge:
     enabled-profiles: [dev, test]
     # optional:
     seed: 42               # fixed -> identical data every restart
     date-window-days: 90
     amount-min: 1.00
     amount-max: 10000.00
   ```

4. Start the app in an enabled profile. On startup SynthForge seeds
   every `@Seed` entity: parents before children (`@ManyToOne` /
   owning `@OneToOne` references always point at persisted rows),
   constraint-aware (`@NotNull`, `@Size`, `@Email`, unique columns),
   with realistic values driven by field names (`email`, `name`,
   `currency`, `amount`, ...). Tables that already contain rows are
   skipped, so restarts don't duplicate data. `@OneToMany` /
   `@ManyToMany` fields are left alone.

## Status

M1 and M2 implemented.

- M1: `EntityScanner`, `GeneratorRegistry` (spec section 7 resolution
  rules), and `SeedRunner` in `synthforge-core`, validated by unit tests
  and by an integration test in `synthforge-demo` that seeds
  `Counterparty` and `Payment` on H2.
- M2: `SeedGraph.topologicalOrder` (parents before children over owning
  `@ManyToOne`/`@OneToOne` edges) and the `synthforge-spring`
  autoconfiguration, which seeds all `@Seed` entities on startup when a
  profile listed under `synthforge.enabled-profiles` is active. Validated
  by a startup integration test in `synthforge-demo` and by running the
  demo app with the `dev` profile.

Post-M2 hardening (July 2026): owning `@OneToOne` children each receive a
distinct parent (the join column is unique), the `GenerationContext`
knobs (random seed, date window, amount range) are reachable via a
`SeedRunner.seed` overload and via the `synthforge.*` application
properties (`seed`, `date-window-days`, `amount-min`, `amount-max`),
and startup seeding skips entities whose table already has rows, making
restarts against a persistent database idempotent. A fixed
`synthforge.seed` makes startup data reproducible; otherwise the chosen
seed is logged.

M3/M4 gates not met; do not start that work (see spec section 11).

### M3 gate criteria (measurable)

The spec (section 11) gates M3 on M1/M2 having "genuinely proven useful"
and RemitFlow existing. Concretely, all of the following must be true
before adding the `remitflow/` module:

1. RemitFlow exists in this reactor with at least two entities related
   through an owning-side `@ManyToOne` or `@OneToOne`.
2. RemitFlow needs seed data in its dev or test profile that would
   otherwise require hand-written SQL scripts or fixture builders —
   i.e. there is a concrete first use case, not a hypothetical one.
3. The demo module's startup seeding and integration tests have been
   running green in CI (including repeated runs with no
   unique-constraint violations), demonstrating M1/M2 work day-to-day.

If any of these is false, M3 stays closed.

## Build

    mvn clean install

## When RemitFlow starts

Add it as a sibling module in this same reactor (`remitflow/`), depending on
`synthforge-spring` directly through Maven's reactor build. No publishing
step is needed while everything lives in one repository. Only pull
SynthForge out into its own separate repository if the M3/M4 gate criteria
in the spec are actually met.
