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
