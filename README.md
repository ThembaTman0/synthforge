# SynthForge

A JPA-aware fake data seeding library for Spring Boot. Standalone project,
see `synthforge-v1-spec.md` for the full technical specification this
implementation should follow.

## Modules

- `synthforge-core`: entity scanning, generator resolution, seed graph, seed runner
- `synthforge-spring`: Spring Boot autoconfiguration and the `@Seed` annotation
- `synthforge-demo`: a small Spring Boot app with two related entities
  (`Counterparty`, `Payment`) used to validate the above against something
  real, since RemitFlow does not exist as a project yet

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

M3/M4 gates not met; do not start that work (see spec section 11).

## Build

    mvn clean install

## When RemitFlow starts

Add it as a sibling module in this same reactor (`remitflow/`), depending on
`synthforge-spring` directly through Maven's reactor build. No publishing
step is needed while everything lives in one repository. Only pull
SynthForge out into its own separate repository if the M3/M4 gate criteria
in the spec are actually met.
