# Rebuilding SynthForge from scratch: a learning guide

The goal is not to reproduce this repository — it is to be able to.
Work in a fresh directory (e.g. `synthforge-rebuild/`) that never touches
this one. Your only requirements document is `synthforge-v1-spec.md`.
This repo is your answer key: attempt each stage first, compare after.

Rules that make this work:

1. No copy-paste from the reference implementation, ever. Read, close
   the file, write your own.
2. Only open the reference for a stage after your version compiles and
   its tests pass — or after you've been genuinely stuck for 30+ minutes.
3. Every stage ends with the checkpoint questions. If you can't answer
   one out loud without looking, the stage isn't done.
4. Follow the order below. It is the order the concepts depend on, and
   roughly the order the real project was built in.

Tooling: Java 21, Maven, Spring Boot 4.1.x, Datafaker 2.x — same as the
reference, so differences you see are design differences, not version
noise.

---

## Stage 0 — The reactor skeleton (~30 min)

Build: a parent `pom.xml` (`packaging=pom`) with three modules —
`core`, `spring`, `demo` — where core has **no** Spring dependency
(only `jakarta.persistence-api` and Datafaker), spring depends on core,
demo depends on spring. Import `spring-boot-dependencies` as a BOM in
`dependencyManagement`.

Understand: what a Maven reactor build is; what a BOM import does; why
the strict dependency direction exists.

Checkpoint:
- Why does keeping core Spring-free matter five years from now?
- What breaks if demo depends on core directly instead of spring?

## Stage 1 — Meet the JPA Metamodel before writing any library code (~1 h)

Build: a throwaway Spring Boot app with one toy `@Entity`. Inject
`EntityManagerFactory`, get its `Metamodel`, and print every
`SingularAttribute` of the entity: name, Java type, whether it's
optional, its `PersistentAttributeType`.

Understand: `EntityType`, `SingularAttribute`, attribute vs raw
reflection field. Add a `transient` field and a `static` field to the
entity and observe they never appear.

Checkpoint:
- Why does the spec mandate the Metamodel instead of
  `Class.getDeclaredFields()`? Name two concrete things reflection
  would get wrong.
- What is the difference between a singular and a plural attribute, and
  why does V1 only ever look at singular ones?

## Stage 2 — FieldMetadata and EntityScanner (~2 h)

Build: an immutable `FieldMetadata` (name, type, annotations, nullable,
maxLength) and an `EntityScanner` that turns an `EntityType<?>` into a
`List<FieldMetadata>`.

The two interesting derivations:
- `nullable`: combine `attribute.isOptional()` with `@NotNull` and
  `@Column(nullable = false)`.
- `maxLength`: `@Size(max)` wins; fall back to `@Column.length` for
  Strings (careful: `@Column.length` has a default value — when is it
  meaningful?).

Understand: why the scanner reads annotations from the Java *field* and
what that means for getter-annotated (property-access) entities.

Checkpoint:
- Why is `FieldMetadata` immutable with getters only?
- A String field has `@Size(max = 50)` and `@Column(length = 255)`.
  What maxLength do you report, and why?

## Stage 3 — GeneratorRegistry and the resolution order (~3 h)

Build: `FieldGenerator<T>` (supports/generate), `GeneratorRegistry.resolve`
implementing spec §7 exactly: user-registered generators first, then
explicit annotation (`@Email`), then field-name heuristics, then type
defaults, then the random-string fallback bounded by `@Size`.

Start with a *small* heuristic set — surname, email, phone, currency,
amount, generic name — and get the ordering contract right before adding
more. Then add `@Column(unique = true)` handling: a retry loop over
whatever rule matched, tracking issued values, failing loudly after N
attempts.

Understand: substring matching makes *order* part of the contract
(`username` contains `name`; `emailAddress` contains `address`). Write a
test that fails if someone reorders the checks.

Checkpoint:
- Why must the unique-retry wrap the whole resolution rather than being
  its own generator?
- Why fail after 100 attempts instead of looping forever, and why is
  the error message ("value space too small for the requested row
  count") the most important part of that feature?
- Your `applyStringBounds` truncates to max and pads to min. In which
  rule-order do bounds apply relative to generation, and why?

## Stage 4 — GenerationContext and reproducibility (~1 h)

Build: a context holding one seeded `Random`, a `Faker` built **on that
same Random**, the unique-value tracking, and the configurable knobs
(date window, amount range).

Experiment: create two contexts with the same seed and generate 20
values through your registry — assert the sequences are identical. Then
build the Faker with its own `new Random()` and watch reproducibility
silently break.

Checkpoint:
- Why one shared Random and not one per generator?
- What, exactly, does a "seed" reproduce — values, or values *and*
  their order? What could still differ between two runs on different
  days? (Hint: look at how you generate dates.)

## Stage 5 — SeedRunner, and the bug you should hit on purpose (~3 h)

Build: `seed(entityClass, count, em)` — instantiate via no-arg
constructor, skip `@GeneratedValue`/`@Version` fields, resolve values
through the registry, set fields via reflection (walk superclasses),
persist via `EntityManager`. For owning `@ManyToOne`/`@OneToOne`
fields, load persisted parents and attach one; skip inverse and plural
associations entirely.

Now the deliberate bug: wire `@OneToOne` children to a *randomly chosen*
parent (the obvious implementation), seed 5 parents and 5 children
against H2, and watch the unique join-column constraint blow up —
sometimes. Sit with why it's *sometimes*. Then fix it properly: each
parent used at most once, clear error when parents run out. The
reference hit this exact bug; commit `5878a4c` is the answer key.

Checkpoint:
- Why does `@OneToOne` imply a unique FK when `@ManyToOne` doesn't?
- Why must parents already be persisted before children are generated
  (what would happen with unsaved parent references)?
- Why does the runner not manage its own transaction?

## Stage 6 — The demo module: M1 complete (~1 h)

Build: `Counterparty` and `Payment` from spec §9, an H2-backed
integration test that manually seeds parents then children and asserts
counts, non-null valid references, and no constraint violations across
repeated runs. This is the spec's §10 test and your M1 gate.

## Stage 7 — SeedGraph: topological ordering (~2 h)

Build: from the Metamodel, an edge child→parent for every owning-side
to-one association; return a topological order, deterministic (tie-break
by class name), throwing a clear error on cycles.

Experiment: three-level chain (C→B→A) must order A, B, C. Then create a
deliberate cycle and verify the error names the entities involved.

Checkpoint:
- Why are inverse (`mappedBy`) `@OneToOne` sides excluded from edges?
- Why must the order be deterministic even though any valid topological
  order would work?
- Why are self-references excluded rather than treated as cycles?

## Stage 8 — The Spring layer: M2 complete (~3 h)

Build: `@Seed(count)`, an `@AutoConfiguration` registered via
`META-INF/spring/...AutoConfiguration.imports`, and an
`ApplicationRunner` that: binds `synthforge.enabled-profiles`, checks
active profiles, orders `@Seed` entities via SeedGraph, seeds each in
one transaction, and skips any entity whose table already has rows.

Two things to *discover* rather than read:
- Try gating with `@ConditionalOnProperty` first and watch it fail to
  match a YAML list. Then learn the `Binder` API. (The reference's
  CLAUDE.md records this exact lesson.)
- Restart your demo app twice against a *file-based* H2 URL without the
  skip logic and watch the rows double. Then add the skip.

Checkpoint:
- Walk through the full startup sequence: who calls your runner, when,
  and what has already happened by then?
- Why an `ApplicationRunner` and not `@PostConstruct` or a
  `CommandLineRunner` in a random `@Component`?
- Why does the whole run share one transaction and one
  GenerationContext?

## Stage 9 — The property surface (~1.5 h)

Build: a properties class for the `synthforge.*` namespace (seed,
date-window-days, amount-min/max) bound with `Binder`, flowing into the
startup run's GenerationContext; log the chosen seed when none is fixed.

Checkpoint:
- Why does logging the random seed turn every run into a reproducible
  one?
- Trace `synthforge.amount-max: 500` from YAML to the digits of a
  generated Payment amount — every hop.

## Stage 10 — Compare and close (~2 h)

- Read the reference's history in build order: `git log --reverse
  --oneline`. Each commit message explains a why; check them against
  the whys you discovered yourself.
- Diff your design decisions against the reference where they differ,
  and decide — with reasons — whose is better. Yours sometimes will be.
- Finally, re-read `synthforge-v1-spec.md` §13 and CLAUDE.md, and note
  how much of the codebase exists *because the spec forbade more*.

---

## When you're done

You should be able to answer, cold: why the Metamodel; why resolution
order is a contract; why one seeded Random; why parents before children
and how the graph guarantees it; why `@OneToOne` needed distinct
parents; why Binder instead of `@ConditionalOnProperty`; why startup
seeding is idempotent. Those seven answers *are* the library — the rest
is typing.

Then return to `remitflow-v1-spec.md` and build RemitFlow on top of a
tool you now fully own.
