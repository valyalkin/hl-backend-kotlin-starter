---
title: 'Widget domain model, JPA entity, and first migration'
type: 'feature'
created: '2026-09-11'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'c61122f13c2008523e38c87dc962bbe6c6c0d674'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The service has no domain type, no persistence layer, and no schema migration — there is nothing for Epic 2's REST, service, and cache stories to build on.

**Approach:** Introduce an immutable `Widget` domain class in `model/`, a separate `@Entity` (`WidgetEntity`) with hand-written mapping in `repository/`, a `JpaRepository` for it, and `V1__create_widgets.sql` that creates the `widgets` table — establishing the layered pattern every later story copies.

## Boundaries & Constraints

**Always:**
- `Widget` lives in `model/`, is immutable (all-val Kotlin class), and owns its own `UUID` generated via `UUID.randomUUID()` before any persistence call.
- `WidgetEntity` and `WidgetRepository` live in `repository/`; all mapping code lives there — no dependency from `model/` toward `repository/`.
- Mapping is hand-written (`toDomain()` / `fromDomain()`) with no mapping framework added to the build.
- `V1__create_widgets.sql` uses a `uuid` primary key, `timestamptz` for timestamps, sequential integer migration name; it is immutable after merge (Flyway checksum enforced).
- `spring.jpa.hibernate.ddl-auto = validate` must pass against the migrated schema — Hibernate generates nothing.
- Timestamps are `java.time.Instant` on the domain/entity and `timestamptz` in Postgres; stored and exchanged in UTC.

**Never:**
- Do not add any mapping framework (MapStruct, ModelMapper, etc.) to the build.
- Do not put JPA annotations on `Widget`; the domain type must remain a plain Kotlin class.
- Do not let the entity or repository types appear in `model/` or any other package.
- Do not rename or alter the migration file after it is written (immutable by convention).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Clean startup | DB has no `widgets` table | Flyway applies `V1__create_widgets.sql`; `ddl-auto=validate` passes; service starts | N/A |
| Repeated startup | Migration already applied | Flyway no-op; checksum matches; service starts normally | N/A |
| Tampered migration | `V1__create_widgets.sql` checksum does not match history | Startup fails with Flyway checksum error; no silent degradation | Flyway throws; Spring fails context load |
| Round-trip mapping | `WidgetEntity.fromDomain(widget)` then `entity.toDomain()` | Resulting `Widget` equals original on all fields | N/A |

</frozen-after-approval>

## Code Map

- `src/main/kotlin/com/hl/service/model/` -- currently has only `.gitkeep`; `Widget.kt` goes here
- `src/main/kotlin/com/hl/service/repository/` -- currently has only `.gitkeep`; `WidgetEntity.kt` and `WidgetRepository.kt` go here
- `src/main/resources/db/migration/` -- currently has only `.gitkeep`; `V1__create_widgets.sql` goes here
- `src/main/resources/application.yaml` -- `spring.jpa.hibernate.ddl-auto: validate` and Flyway `validate-on-migrate: true` already set; no changes needed
- `build.gradle.kts` -- `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `postgresql` driver already on the classpath via BOM; no new dependencies needed

## Tasks & Acceptance

**Execution:**
- [x] `src/main/kotlin/com/hl/service/model/Widget.kt` -- create immutable data class with `id: UUID`, `name: String`, `createdAt: Instant`, `updatedAt: Instant`; `id` defaults to `UUID.randomUUID()` -- establishes the domain type all later stories reference
- [x] `src/main/kotlin/com/hl/service/repository/WidgetEntity.kt` -- create `@Entity @Table(name = "widgets")` with matching fields; add `fun toDomain(): Widget` and `companion object { fun fromDomain(w: Widget): WidgetEntity }` -- keeps all mapping in the repository layer
- [x] `src/main/kotlin/com/hl/service/repository/WidgetRepository.kt` -- create `interface WidgetRepository : JpaRepository<WidgetEntity, UUID>` -- gives later service stories a repository to inject
- [x] `src/main/resources/db/migration/V1__create_widgets.sql` -- create `widgets` table with `id UUID PRIMARY KEY`, `name TEXT NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL` -- Flyway applies on startup; checksum locked after merge

**Acceptance Criteria:**
- Given a clean database, when the service starts with `local` profile, then Flyway applies `V1__create_widgets.sql` and `ddl-auto=validate` passes with no errors.
- Given the migrated schema, when the service restarts, then Flyway is a no-op and startup succeeds.
- Given `Widget` is inspected, then it carries no JPA annotations and has no import from the `repository` package.
- Given `WidgetEntity.fromDomain(widget).toDomain()`, then the result equals the original `Widget` on all fields.
- Given `./gradlew build`, then it compiles clean under strict Kotlin with `allWarningsAsErrors = true`.

## Implementation Notes

`./gradlew build` verified: compiles clean under `allWarningsAsErrors = true`, ktlint/spotless pass, and `WidgetEntityTest` (round-trip mapping row of the I/O matrix) runs and passes.

The other three I/O matrix rows (clean startup, repeated startup, tampered migration) require Flyway running against a live Postgres via `./gradlew bootRun` with the Compose stack up, per this spec's own Verification section -- no automated Integration Test exists yet (the shared Testcontainers base class is Story 2.2's scope). Docker Desktop's daemon in this build environment could not pull images (even `docker pull hello-world` hung indefinitely), so this path was not exercised here. Human should run `./gradlew bootRun` locally to confirm Flyway applies `V1__create_widgets.sql` cleanly and `ddl-auto=validate` passes before merging.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| `spec_file` frontmatter said `in-review` while `sprint-status.yaml` still said `in-progress` (blind-hunter) | false | `sprint-status.yaml` sync to `review` is a step-05 action per this workflow, not step-04; will resolve before this run ends. | rejected |
| `WidgetEntity` is a final Kotlin class with no `kotlin-jpa`/allopen plugin; future `getReferenceById()`/lazy associations could fail to proxy (blind-hunter) | maybe-false (if true, medium) | No lazy association or `getReferenceById()` call exists anywhere in this diff or the codebase yet, so no reachable bad outcome today; would need a future story that adds a relation or calls `getReferenceById()` to settle. | defer |
| `Widget.id`/`WidgetEntity.id` default to a fresh `UUID.randomUUID()`, risking accidental duplicate rows if a caller omits `id` (blind-hunter) | false | Spec's own (non-frozen) Design Notes state this default is intentional, matching AD-8 ("database never generates identifiers"); fixing it means editing the spec's stated design, not the code. | rejected |
| Execution checkboxes marked `[x]` while 3 of 4 I/O matrix rows are unverified, `status` pushed to `in-review` regardless (blind-hunter) | false | Checkboxes cover file-creation tasks only (all four files exist and build); the separate runtime-verification gap is disclosed immediately below in Implementation Notes, and the human explicitly chose to proceed without live verification this session. | rejected |
| No `@Version` optimistic-locking column on `WidgetEntity` (blind-hunter) | false | No update/mutation code path exists anywhere in the codebase yet (repository is a bare `JpaRepository`, no service layer) — nothing to race on; concurrency control belongs to a later story that adds updates. | rejected |
| `WidgetEntity`'s no-arg-constructor defaults call `Instant.now()` twice, wasting allocations on every Hibernate hydration (blind-hunter) | low | True but negligible (two `Instant.now()` calls, immediately overwritten by Hibernate field-access population); a clean fix needs nullable/lateinit properties, which is more than a trivial deletion. | rejected (low, fix non-trivial) |
| `Widget` is a structural-equality `data class`; `WidgetEntity` has default identity equality, an asymmetry (blind-hunter) | false | Standard, recommended JPA practice — giving a mutable Hibernate-managed entity structural `equals()`/`hashCode()` is a well-known footgun (breaks in `Set`/`Map` across the entity lifecycle); the asymmetry is correct, not accidental. | rejected |
| `Instant` (nanosecond precision) round-tripped through `TIMESTAMPTZ` (microsecond precision) is never exercised by a real DB write — the in-memory `WidgetEntityTest` round-trip can't catch precision loss (edge-case-hunter) | medium | Postgres `timestamptz` stores microsecond precision; `java.time.Instant` carries nanoseconds. No current test persists through the migration, so this is unexercised; the next story to actually save a `Widget` and assert equality against a re-fetched one would hit silent precision loss. | patch |
| `WidgetEntity`'s JPA/schema mapping has no test that boots Hibernate or Flyway; `ddl-auto=validate` is never exercised by `./gradlew build` (verification-gap) | defer (filed) | Verification-gap reviewer traced `WidgetEntityTest` (pure Kotlin, no Spring context) and the test config (`src/test/resources/application.yaml`) which explicitly excludes JPA/datasource/Flyway autoconfiguration until Story 2.2 adds the Testcontainers base class; disposition filed by that layer as `defer` with the story-sequencing evidence. | defer |

## Design Notes

Mapping lives exclusively in `WidgetEntity` as `toDomain()` / `fromDomain()` so the `model/` layer has no knowledge of persistence types. This is the hand-written mapping pattern the later service layer uses — no framework, no overhead.

`Widget` gives its `id` a default of `UUID.randomUUID()` so callers (the service layer in Story 2.4) can construct a `Widget` with `id` already set before saving, matching AD-8's rule that the database never generates identifiers.

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL with no compilation warnings or errors
- `./gradlew bootRun` (with Compose Stack up) -- expected: service starts, Flyway reports `Successfully applied 1 migration`, `GET /actuator/health` returns `{"status":"UP"}`
