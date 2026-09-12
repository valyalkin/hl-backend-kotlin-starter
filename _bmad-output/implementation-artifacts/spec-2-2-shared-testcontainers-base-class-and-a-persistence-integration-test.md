---
title: 'Shared Testcontainers base class and a persistence integration test'
type: 'feature'
created: '2026-09-11'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'acdf70735a3a988a1f7a2068ebe2e61f10658c40'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 2.1 left `Widget`, `WidgetEntity`, `WidgetRepository`, and the Flyway migration in place, but nothing exercises them against a real Postgres, and every later Epic 2 Integration Test needs shared containers instead of reinventing infrastructure per test.

**Approach:** Add one abstract `IntegrationTestBase` that owns static, JVM-shared Postgres and Redis Testcontainers wired via `@ServiceConnection`; add a `WidgetRepositoryIT` that writes then reads a `Widget` through `WidgetRepository`; retarget the test-scope Spring config and the two pre-existing Integration Tests to run against these containers instead of today's blanket JPA/Datasource/Flyway exclusion.

## Boundaries & Constraints

**Always:**
- `IntegrationTestBase` lives in `support/` (test source set); its companion object holds `@JvmStatic` fields annotated `@ServiceConnection` for a `PostgreSQLContainer` and a `GenericContainer` (Redis), started once in a companion `init` block and never stopped explicitly (JVM-shared singleton, per AD-21).
- Image tags come from the root `.env` (`POSTGRES_IMAGE`, `REDIS_IMAGE`) — read the file directly; no hardcoded version literal.
- Every `@SpringBootTest`-based test in the module extends `IntegrationTestBase` and adds nothing infrastructural.
- `WidgetRepositoryIT` lives in `repository/`, carries `@Tag("integration")`, and cleans up its own data (e.g. `@Transactional` rollback) rather than relying on per-test containers.
- No Testcontainers version literal — BOM-managed (AD-22).

**Never:**
- Do not add a dedicated Testcontainers Redis module; use `GenericContainer` with `@ServiceConnection(name = "redis")` (no Boot-BOM-managed dedicated Redis container class exists).
- Do not use the `@Testcontainers`/`@Container` JUnit lifecycle annotations — they stop containers per class, breaking the JVM-shared singleton requirement.
- Do not leave the `spring.autoconfigure.exclude` list in `src/test/resources/application.yaml`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Write-then-read | A new `Widget` saved via `WidgetRepository` | `findById` returns the same `Widget` after `fromDomain`/`toDomain` | N/A |
| Shared containers | Two IT classes run in the same JVM | Same Postgres/Redis container instances are reused; no second startup | N/A |

</frozen-after-approval>

## Code Map

- `src/test/kotlin/com/hl/service/support/` -- new package; `IntegrationTestBase.kt` goes here
- `src/test/kotlin/com/hl/service/repository/WidgetRepositoryIT.kt` -- new, beside the existing unit test `WidgetEntityTest.kt`
- `src/test/resources/application.yaml` -- currently excludes `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `FlywayAutoConfiguration` (its own comment already forecasts this story replacing that). Remove the exclude block; add `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`, `spring.flyway.validate-on-migrate: true` (mirrors `src/main/resources/application.yaml`, since this file fully shadows it during tests — no property merge)
- `src/test/kotlin/com/hl/service/LivenessProbeIT.kt`, `StarterApplicationIT.kt` -- plain `@SpringBootTest` today; once the exclude above is gone, JPA/Datasource/Flyway autoconfigure activate and need a real connection at context startup — extend `IntegrationTestBase`
- `build.gradle.kts` -- add `testImplementation("org.springframework.boot:spring-boot-testcontainers")` and `testImplementation("org.testcontainers:postgresql")`; both come from the Spring Boot BOM already applied via `implementation(platform(SpringBootPlugin.BOM_COORDINATES))` — no version literal (AD-22)
- `.env` -- already has `POSTGRES_IMAGE=postgres:18.1` / `REDIS_IMAGE=redis:8.2.9` (Story 1.4); the base class reads this file directly, same convention the README already documents
- `src/main/kotlin/com/hl/service/repository/{WidgetEntity,WidgetRepository}.kt`, `src/main/kotlin/com/hl/service/model/Widget.kt` -- reused as-is, exercised by the new IT, no changes
- `README.md` (~line 68) -- reword "Epic 2 adds a Testcontainers base class..." to describe it as already in place

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add `spring-boot-testcontainers` and `testcontainers-postgresql` `testImplementation` dependencies -- provides `@ServiceConnection` support and `PostgreSQLContainer`
- [x] `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt` -- create abstract class with a companion object holding `@JvmStatic @ServiceConnection` `PostgreSQLContainer` and `GenericContainer` (redis) fields, image tags parsed from the root `.env`, both started once in an `init` block -- the shared JVM-wide base every Integration Test extends
- [x] `src/test/kotlin/com/hl/service/repository/WidgetRepositoryIT.kt` -- create `@SpringBootTest @Tag("integration") @Transactional` class extending `IntegrationTestBase`; save a `Widget` via `WidgetRepository.save(WidgetEntity.fromDomain(...))` and assert `findById(...).toDomain()` equals the original -- proves the persistence path end to end
- [x] `src/test/resources/application.yaml` -- remove the Datasource/JPA/Flyway autoconfigure exclusion; add `ddl-auto: validate`, `open-in-view: false`, `flyway.validate-on-migrate: true` -- lets the container-backed connection autoconfigure normally
- [x] `src/test/kotlin/com/hl/service/LivenessProbeIT.kt` -- extend `IntegrationTestBase` -- keeps passing once the exclude above is removed
- [x] `src/test/kotlin/com/hl/service/StarterApplicationIT.kt` -- extend `IntegrationTestBase` -- same
- [x] `README.md` -- update the forward-looking sentence about Epic 2's Testcontainers base class to present tense

**Acceptance Criteria:**
- Given `IntegrationTestBase`, when inspected, then it owns static `@ServiceConnection` Postgres and Redis containers started once per JVM and shared, and Integration Tests extending it add nothing infrastructural.
- Given `WidgetRepositoryIT` runs, when it executes, then Testcontainers starts Postgres, Flyway migrates `V1__create_widgets.sql`, and a write-then-read through `WidgetRepository` round-trips the row, with no external database and no setup beyond Docker being available.
- Given `./gradlew build` with Docker available, when it runs, then every Integration Test — including the pre-existing `LivenessProbeIT` and `StarterApplicationIT` — passes against the shared containers.

## Implementation Notes

- **`org.testcontainers:postgresql` no longer exists.** The Spring Boot 4.1.1 BOM imports `testcontainers-bom:2.0.5` (Testcontainers 2.x), which renamed every module from `<name>` to `testcontainers-<name>`. Used `testImplementation("org.testcontainers:testcontainers-postgresql")` instead of the coordinate named in the spec's Code Map/Tasks. Still BOM-managed, no version literal (AD-22 upheld).
- **`org.testcontainers.containers.PostgreSQLContainer<SELF>` is deprecated in Testcontainers 2.x** (class and both constructors carry `@Deprecated`, which trips `allWarningsAsErrors`). Testcontainers 2.x ships a replacement, non-generic `org.testcontainers.postgresql.PostgreSQLContainer`; `IntegrationTestBase` uses that class instead of the old generic-self-type pattern. Confirmed Spring Boot's `JdbcContainerConnectionDetailsFactory` targets `JdbcDatabaseContainer<?>` generically, so `@ServiceConnection` still wires it correctly. `GenericContainer` (used for Redis) is unaffected — only specific inherited method overrides are deprecated there, not the class.
- **Pre-existing gap surfaced, not caused, by this story:** with the test-scope JPA/Datasource exclusion removed, Spring Data JPA's `PreferredConstructorDiscoverer` reflects on `WidgetEntity` (a Kotlin class) via `kotlin-reflect` and threw `NoClassDefFoundError: kotlin/reflect/full/KClasses` — the project never declared a `kotlin-reflect` dependency, and Story 2.1 never exercised this path because JPA autoconfiguration was excluded in tests at the time. Added `implementation("org.jetbrains.kotlin:kotlin-reflect")` to `build.gradle.kts` (version aligned automatically to the Kotlin Gradle plugin, no literal). This is a `src/main` runtime dependency, not test-only, since any real JPA context boot (including `bootRun`) needs it.
- Verified locally: Docker Desktop pulled `postgres:18.1` and `redis:8.2.9` without issue (no repeat of the `docker pull hello-world` hang noted in Story 2.1's environment) and `./gradlew build` was BUILD SUCCESSFUL, with `WidgetRepositoryIT`, `LivenessProbeIT`, `StarterApplicationIT`, `WidgetEntityTest`, `DatasourceFailFastTest`, and `RedisHealthDownTest` all passing. Container-start timing in the test XML reports confirms single-startup sharing: `StarterApplicationIT` (first IT to run) took ~5s including container start; the other IT classes ran in well under a second with no repeated "Pulling docker image"/"Creating container" log lines.

## Spec Change Log

- 2026-09-12: Implementation swapped the spec's named coordinate `org.testcontainers:postgresql` for `org.testcontainers:testcontainers-postgresql` and the deprecated `org.testcontainers.containers.PostgreSQLContainer<SELF>` for the new `org.testcontainers.postgresql.PostgreSQLContainer`, both forced by the actual Testcontainers 2.0.5 release pulled in via the Spring Boot 4.1.1 BOM (not knowable until dependency resolution/compilation). Added an undocumented `kotlin-reflect` runtime dependency to fix a pre-existing gap this story's real JPA context startup exposed. See Implementation Notes for detail.

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| `WidgetRepositoryIT` calls `save()` then `findById()` in the same `@Transactional` test with no flush/clear, so JPA's first-level cache can return the managed instance without ever issuing a SELECT against Postgres (edge-case-hunter) | high | Confirmed: `EntityManager.find()`/Spring Data's `findById()` checks the persistence-context identity map before querying, and `save()` on a new entity leaves it managed in that same context for the rest of the transaction; the test can pass even if the Flyway schema or Hibernate mapping is wrong, defeating the story's whole "prove the persistence path end to end" purpose and the pattern every later resource's Integration Test copies. | patch |
| Test-scope config's comment claims it "mirrors" `src/main/resources/application.yaml` but drops `management.endpoint.health.group.readiness.include: readinessState,db,redis`; no test calls `/actuator/health/readiness`, so nothing fails if the new Redis `@ServiceConnection(name = "redis")` wiring is broken (blind-hunter + verification-gap, same root cause) | medium | Confirmed by reading `src/test/resources/application.yaml` (missing the block) and grepping the whole test tree for `readiness`/`actuator/health` — no hit. A broken redis service-connection binding would silently fall back to `localhost:6379`; Lettuce's lazy connect means `StarterApplicationIT`/`LivenessProbeIT`/`WidgetRepositoryIT` would still pass, and the regression would only surface as `readiness` reporting `DOWN` in production. | patch |
| README's "Run the tests" section says Docker must be available but doesn't mention a clean machine's first run needs network/registry access to pull `postgres:18.1`/`redis:8.2.9` (blind-hunter) | low | True and plausible for a first-time contributor; fix is a one-sentence addition, not "more than a direct correction," so it doesn't clear the low-rejection bar. | patch |
| Removing the test-scope exclusion means every test now needs Docker, with no `excludeTags`/unit-only Gradle task to preserve a Docker-free loop (blind-hunter) | false | This is the architecture's explicit intent, not an unintended regression: AD-21 mandates one shared Testcontainers base class every `@SpringBootTest` extends, and this story's own AC says "no external database and no setup beyond Docker being available." No Docker-free unit-only task was ever specified. | rejected |
| `readImageTag` only parses bare `KEY=value`; breaks on inline comments, quotes, or `export KEY=value` styling (blind-hunter) | low | Real but purely hypothetical — the actual `.env` is two bare `KEY=value` lines with no plan to change format; unlikely to be met in everyday use and the fix (validating/handling multiple exotic formats) is more than a direct correction. | rejected |
| Redis `GenericContainer` wait strategy only confirms the port is listening, not that `redis-server` can serve commands yet (blind-hunter) | low | Standard, widely-used minimal pattern for Redis Testcontainers (redis binds its port only once initialization completes); no flake observed across multiple runs here; unlikely in everyday use and the fix adds wait-strategy complexity for an undemonstrated failure. | rejected |
| "Shared containers... no second startup" (I/O matrix row) has no automated assertion, only manual log-timing inspection in Implementation Notes (blind-hunter) | low | Real gap but developer-only with no concretely named harm beyond generic "no regression protection"; a meaningful automated assertion here is non-trivial to write and unlikely to be needed in everyday use. | rejected |
| `WidgetRepositoryIT` only covers the happy path; no negative case (e.g. `findById` on a missing id, duplicate-id save) (blind-hunter) | low | Real but outside this story's AC, which asks only for a write-then-read round trip; additional coverage is a nice-to-have, not a defect, and adding new test methods is more than a trivial fix. | rejected |
| No explicit `withStartupTimeout` on either container; a slow or hung Docker daemon pull could block the whole test run indefinitely (edge-case-hunter) | low | The one hang actually observed in this sandbox (`docker pull hello-world`) was a Docker daemon/CLI-level stall, not something a Testcontainers-side startup timeout governs; no normal-operation timeout failure is demonstrated, so this stays hypothetical. | rejected |
| If `postgresContainer.start()` succeeds but `redisContainer.start()` throws, the companion `init` block fails, leaking the started Postgres container and surfacing a confusing `NoClassDefFoundError` on every later reference (edge-case-hunter) | low | Technically correct JVM static-init semantics, but a partial-start failure between two containers starting on the same host is not demonstrated and unlikely in everyday use; the fix (wrap-and-cleanup) guards state that hasn't been shown to occur. | rejected |
| A `.env` line of exactly `KEY=` (empty value) produces an unclear `DockerImageName.parse("")` failure instead of a clear "not found" message (edge-case-hunter) | low | Same class as the bare-parser finding above — hypothetical, not reachable with the actual current two-line `.env`; unlikely in everyday use. | rejected |

## Design Notes

Kotlin gotcha: a `companion object { val x = ... }` property is *not* a static field on the outer class unless annotated `@JvmStatic` — Spring's `@ServiceConnection` field scan and Testcontainers reflection both look for genuine static fields, so `@JvmStatic` is required on both container properties.

No `@Testcontainers`/`@Container` annotations: that JUnit extension stops `static` containers after the last test in the *class*, not the JVM. Starting the containers manually in the companion `init` block (with only `@ServiceConnection` on the fields) is the documented way to get a container that Spring wires automatically but that JUnit never tries to stop — Ryuk cleans it up when the JVM exits.

`@Transactional` belongs on `WidgetRepositoryIT`, not on `IntegrationTestBase`: a later story's end-to-end HTTP test (`RestTestClient` against `RANDOM_PORT`) runs the request on a different thread than the test method, so a base-class-wide rollback transaction would be invisible to it.

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless pass; `WidgetRepositoryIT`, `LivenessProbeIT`, and `StarterApplicationIT` all pass against the Testcontainers-started Postgres/Redis (requires a working Docker daemon that can pull `postgres:18.1` and `redis:8.2.9`)

**Manual checks (if no CLI):**
- If the sandbox's Docker daemon cannot pull images (Story 2.1 hit this — `docker pull hello-world` hung indefinitely in this build environment), record the gap in Implementation Notes and ask the human to run `./gradlew build` locally with Docker Desktop up before merging.
