# SynthForge

A JPA-aware fake data seeding library for Spring Boot.

[Instancio](https://github.com/instancio/instancio) (and its
[instancio-jpa](https://github.com/Mobe91/instancio-jpa) extension) gives
you an API to call from inside a test — build a graph, persist it, for
that test. SynthForge runs itself: annotate the entity, start the app in a
dev profile, and the database is already populated — no test method, no
calling code, anywhere.

## The problem

Faker and Datafaker generate realistic-looking *values* — names, emails,
addresses — but they have no idea your entities are related. The moment
one entity references another (`Payment` → `Counterparty`), you're back to
hand-writing a seed script: create parents first, hold onto their IDs,
wire them into children, hope you didn't violate a `@NotNull` or a unique
constraint along the way. That script rots the first time a field changes.
SynthForge reads your JPA entities directly — annotations, relationships,
and all — and generates a valid, related, constraint-respecting object
graph with a single annotation, so there's no script to write or maintain.

## Install

SynthForge isn't on Maven Central yet (see the M4 gate in
[synthforge-v1-spec.md](synthforge-v1-spec.md)), so for now it's installed
from source into your **local** Maven repository — the same `~/.m2` cache
Maven and Gradle read from for every project on your machine. One-time
setup:

```bash
git clone https://github.com/ThembaTman0/synthforge.git
cd synthforge
mvn install
```

That builds `synthforge-core` and `synthforge-spring` and installs them
locally. Now, **in your own Spring Boot project** — a separate project,
not this cloned folder — add the dependency:

**Maven**

```xml
<dependency>
    <groupId>io.github.ThembaTman0</groupId>
    <artifactId>synthforge-spring</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Gradle**

```groovy
implementation 'io.github.ThembaTman0:synthforge-spring:0.1.0-SNAPSHOT'
```

Requires Java 21 and Spring Boot with Spring Data JPA.

## Before / after

Without SynthForge, seeding two related entities means a hand-wired script:

```java
List<Counterparty> parents = new ArrayList<>();
for (int i = 0; i < 50; i++) {
    parents.add(counterpartyRepo.save(new Counterparty(faker.name().fullName(), faker.internet().emailAddress())));
}
for (int i = 0; i < 200; i++) {
    paymentRepo.save(new Payment(parents.get(random.nextInt(parents.size())), randomAmount()));
}
```

With SynthForge, the entities are the seed script:

```java
@Entity @Seed(count = 50)
public class Counterparty { /* fields only */ }

@Entity @Seed(count = 200)
public class Payment {
    @ManyToOne(optional = false) private Counterparty counterparty; // wired automatically
}
```

Enable it for the profiles you want (never production):

```yaml
synthforge:
  enabled-profiles: [dev, test]
```

Start the app in an enabled profile and both tables are populated, in the
right order, with realistic values, on every restart.

## How it works

- **Entity scanning** — reads JPA-managed attributes through the
  `jakarta.persistence.metamodel.Metamodel` API, never raw reflection, so
  only real persistent fields are ever touched.
- **Relationship ordering** — builds a dependency graph from owning-side
  `@ManyToOne`/`@OneToOne` relationships and topologically sorts it, so
  parent rows always exist before a child is generated to reference them.
- **Constraint-aware generation** — `@NotNull`, `@Size`, `@Email`, and
  field-name heuristics (`email`, `iban`, `amount`, `country`, ...) drive
  realistic values via [Datafaker](https://www.datafaker.net/); a
  `@Column(unique = true)` field gets a bounded retry loop instead of a
  constraint violation.
- **Idempotent restarts** — a table that already has rows is skipped, so
  restarting against a persistent database never duplicates seed data.

Full technical detail — the exact resolution priority, relationship rules,
and configuration reference — is in
[synthforge-v1-spec.md](synthforge-v1-spec.md).

## License

[MIT](LICENSE)
