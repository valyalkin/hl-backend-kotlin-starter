---
title: 'Cache behavior Integration Test'
type: 'feature'
created: '2026-09-13'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '00d8e203000557c439c076011870fa99ddfb58dc'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 2.7 wired `@Cacheable`/`@CacheEvict` and proved the annotation wiring with an in-memory `ConcurrentMapCacheManager` unit test, but nothing exercises the real Redis-backed path (JDK-serialized round trip through actual Redis) or the fail-fast, no-fallback contract when Redis is unreachable -- so a regression in either could ship undetected.

**Approach:** Add one Integration Test extending the shared Testcontainers base class to prove miss/hit/write-invalidate against real Redis, and one dedicated Integration Test that stops its own Redis container to prove a read-by-id fails with a 500 Problem Detail body and readiness reports `DOWN`, with no Postgres-served fallback.

## Boundaries & Constraints

**Always:**
- The positive-path Integration Test extends `IntegrationTestBase` and reuses its shared, JVM-wide Postgres and Redis containers (AD-21); it never stops or restarts them.
- The positive-path test uses `@MockitoSpyBean` on the real `WidgetRepository` bean to count actual datastore calls (Mockito is already on the test classpath transitively; no new dependency).
- The Redis-down test uses its own dedicated, self-contained Postgres+Redis Testcontainers pair (JUnit-managed `@Testcontainers`/`@Container`, torn down after the class) -- never the shared `IntegrationTestBase` singletons, whose mapped port cannot be reliably restored after `.stop()`/`.start()` within one JVM run.
- Both new tests carry `@Tag("integration")` per existing convention.
- `GlobalExceptionHandler` gains an `@ExceptionHandler(RedisConnectionFailureException::class)` that responds exactly like `SystemException` (500, `code: "SYSTEM_ERROR"`), so a Redis connection failure -- not just the `SystemException` class itself -- produces the epic's documented `SystemException` contract. (Decision, per Open Questions resolution: Option A.)

**Never:**
- No change to `WidgetService`'s cache annotations or `CacheConfig` -- Story 2.7 already wired them; this story only proves the wiring against real infrastructure.
- No change to `WidgetServiceCacheTest.kt` -- its hermetic, non-Redis coverage stays as is.
- No Postgres-down scenario -- out of this story's scope.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Miss then hit | `findById(id)` called twice, no write between, real Redis | Second call served from Redis; spied `widgetRepository.findById` invoked exactly once | N/A |
| Write invalidates | `findById(id)`, then `update(id, name)`, then `findById(id)` again | Redis entry evicted by `update`; third call reaches the repository again and returns the new name | N/A |
| Read-by-id while Redis is down | Dedicated Redis container stopped, `GET /api/v1/widgets/{id}` | 500 `application/problem+json` body (`type=about:blank`, `instance`, `traceId`, `code: "SYSTEM_ERROR"`) | 500 |
| Readiness while Redis is down | Same stopped Redis, `GET /actuator/health/readiness` | 503, body `status: DOWN` | 503 |

</frozen-after-approval>

## Code Map

- `src/test/kotlin/com/hl/service/service/WidgetServiceCacheIT.kt` -- new; `@SpringBootTest @Tag("integration") @Transactional class WidgetServiceCacheIT : IntegrationTestBase()`, injects `WidgetService` and `@MockitoSpyBean lateinit var widgetRepository: WidgetRepository`; covers the first two I/O-matrix rows against real Redis.
- `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt` -- add `@ExceptionHandler(RedisConnectionFailureException::class)` delegating to the same 500/`SYSTEM_ERROR` response as `handleSystem`; `RedisDownIT` below depends on this.
- `src/test/kotlin/com/hl/service/RedisDownIT.kt` -- new; own `@Container @ServiceConnection` Postgres + Redis (JUnit `@Testcontainers`, not `IntegrationTestBase`'s pair), `@SpringBootTest(webEnvironment = RANDOM_PORT) @AutoConfigureRestTestClient`, styled like `LivenessProbeIT`; stops its own Redis mid-test then asserts the last two I/O-matrix rows via `RestTestClient`.
- `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt` -- reference only, pattern the positive test extends; not modified.
- `src/test/kotlin/com/hl/service/LivenessProbeIT.kt` -- reference only, `RestTestClient` + readiness-endpoint pattern to mirror in `RedisDownIT`.
- `src/test/kotlin/com/hl/service/RedisHealthDownTest.kt` -- reference only, existing (non-Testcontainers) Redis-down precedent; not modified.
- `src/main/kotlin/com/hl/service/service/WidgetService.kt` -- reference only, methods under test; not modified.

## Tasks & Acceptance

**Execution:**
- [x] `src/test/kotlin/com/hl/service/service/WidgetServiceCacheIT.kt` -- add miss/hit/write-invalidate assertions against real Redis -- proves the cache-aside wiring from Story 2.7 under actual Redis serialization/TTL, not just the annotation proxy
- [x] `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt` -- add the `RedisConnectionFailureException` → `SystemException`/`SYSTEM_ERROR` translation -- matches the epic's literal "propagates Redis failures as SystemException" wording
- [x] `src/test/kotlin/com/hl/service/RedisDownIT.kt` -- add Redis-down 500 + readiness-`DOWN` assertions -- proves the fail-fast, no-fallback contract (AD-13)

**Acceptance Criteria:**
- Given the cache Integration Test extending the shared base class, when it runs, then it asserts a miss populates the cache, a hit avoids the datastore, and a write invalidates the entry.
- Given Redis is stopped, when a read-by-id is attempted, then it returns 500 (`code: "SYSTEM_ERROR"`, standard Problem Detail body) and readiness reports `DOWN`, and there is no Postgres-served fallback.

## Implementation Notes

- `WidgetServiceCacheIT` uses `@MockitoSpyBean` on the real `WidgetRepository` bean plus `Mockito.verify`/`clearInvocations` (not a hand-rolled counting wrapper like the unit test's `CountingWidgetRepository`) so the spy wraps the actual Spring Data JPA bean talking to real Postgres, and invocation counts are reset between phases (create -> initial read -> clear -> update -> clear -> post-eviction read) so each assertion is scoped to exactly the calls that phase should make, rather than an implementation-detail-sensitive running total.
- `RedisDownIT` needed `org.testcontainers.junit.jupiter.Container`/`@Testcontainers`, which was not yet on the test classpath (only `spring-boot-testcontainers` and `testcontainers-postgresql` were); added `testImplementation("org.testcontainers:testcontainers-junit-jupiter")` to `build.gradle.kts` (BOM-managed, no version literal, per AD-22). This is the only `build.gradle.kts` change.
- `RedisDownIT` duplicates `IntegrationTestBase`'s private `readImageTag(...)` `.env`-reading helper rather than sharing it, since `IntegrationTestBase` is reference-only/not-modified per the Code Map and the helper is `private` -- consistent with the spec's "own dedicated, self-contained Postgres+Redis Testcontainers pair" framing.
- `GlobalExceptionHandler.handleRedisConnectionFailure` mirrors `handleSystem` exactly (500, `code: "SYSTEM_ERROR"`, same server-side error log with `traceId`), placed directly after `handleSystem` in the source; `@ExceptionHandler(Exception::class)`'s existing `handleUnexpected` would otherwise have caught `RedisConnectionFailureException` and mislabeled it `UNEXPECTED_ERROR`.
- Verified: `./gradlew build` -- BUILD SUCCESSFUL; ktlint/spotless clean; full suite green with no failures/errors, including `WidgetServiceCacheIT` (2/2) and `RedisDownIT` (1/1) in their own JUnit XML reports.
- Review pass (see Review Triage Log) sent 4 `patch` findings back to the implementation agent: fix the class-level KDoc's stale handler count/description, correct the inaccurate "handler ordering matters" comment on `handleRedisConnectionFailure`, extract a shared `respondAsSystemFailure` helper so `handleSystem`/`handleRedisConnectionFailure` can't drift apart, and split `RedisDownIT`'s single `@Test` into two independent ones. All 4 applied. Verified independently: `./gradlew build` -- BUILD SUCCESSFUL; `RedisDownIT` now 2/2, `GlobalExceptionHandlerTest` 4/4, full suite green.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| `GlobalExceptionHandler`'s class KDoc says "the three `AppException` subtypes... plus any unanticipated exception" and "the four `@ExceptionHandler` methods below" but a fifth handler for a non-`AppException` type (`RedisConnectionFailureException`) was added without updating the counts/description (blind-hunter) | low | Confirmed: KDoc text unchanged by this diff while a fifth `@ExceptionHandler` method was added. | patch |
| `handleRedisConnectionFailure`'s comment claims it's "placed directly after `handleSystem`" to avoid `handleUnexpected` catching it first (blind-hunter) | low | Confirmed inaccurate: Spring's `ExceptionHandlerMethodResolver` selects the most-specific matching exception type via `ExceptionDepthComparator`, not method declaration order, so the stated reasoning would hold regardless of placement. | patch |
| `handleRedisConnectionFailure` duplicates `handleSystem`'s trace-id/log/respond logic almost verbatim, no shared helper (blind-hunter) | low | Confirmed duplication of the same three-step pattern across two handlers; a future change to one could silently diverge from the other, eroding the "sole/consistent producer of error bodies" invariant (AD-11). | patch |
| `RedisDownIT` asserts two independent behaviors (500 body, readiness `DOWN`) in one `@Test`, so a failure in the first assertion chain masks the second (blind-hunter) | low | Confirmed: both assertion chains run sequentially in one method; a readiness-reporting regression would go unsignaled if the earlier assertion already failed. | patch |
| `RedisDownIT` never explicitly proves "no Postgres-served fallback" (blind-hunter) | false | The test targets a randomly generated UUID never persisted; `@Cacheable(sync = true)` intercepts before `WidgetService.findById`'s body runs, so any Postgres fallback would return 404 or 200, never 500 -- the existing 500/`SYSTEM_ERROR` assertion already disproves a fallback. | false |
| `WidgetServiceCacheIT` leaves Redis entries un-evicted across tests since `@Transactional` only rolls back Postgres, not Redis (blind-hunter) | false | Each test's widget id is `UUID.randomUUID()` (application-generated), so cache keys never collide across runs; leaked entries are inert and bounded by the existing 10-minute `spring.cache.redis.time-to-live` default -- no assertion or outcome is affected. | false |
| No assertion on the Problem Detail `title` field in `RedisDownIT` (blind-hunter) | false | `title` is not part of this application's documented Problem Detail contract (epic-2-context.md enumerates `type`, `instance`, `detail`, `code`, `traceId`, `details`, `errors` -- not `title`), and no other existing test in the codebase asserts it either. | false |
| `readImageTag(...)` duplicated verbatim between `IntegrationTestBase` and `RedisDownIT` (blind-hunter) | low | Confirmed, but deliberate per this spec's Design Notes (`RedisDownIT` must stay a fully independent Testcontainers pair; `IntegrationTestBase`'s helper is `private`). De-duplicating means modifying `IntegrationTestBase`, which the Code Map marks reference-only/not-modified -- fix exceeds a direct correction for a low-risk, stable duplication. | rejected (low severity, fix exceeds direct correction) |
| `update`/`delete`'s `@CacheEvict` (default `beforeInvocation = false`) could let the Postgres write commit before a Redis-down eviction throws, returning 500 for a write that actually persisted, with the stale value still cached (blind-hunter) | maybe-false | Real mechanism if the commit-then-evict ordering holds, but `WidgetService.kt` is untouched by this diff (annotations added in Story 2.7); the exact advisor ordering was already flagged and left unresolved in spec-2-7's own Review Triage Log ("no explicit advisor ordering... maybe-false"). Pre-existing, not caused by this story. Severity if confirmed: medium. | defer |
| `GlobalExceptionHandler` maps only `RedisConnectionFailureException` to `SYSTEM_ERROR`; sibling types (`RedisSystemException`, `QueryTimeoutException`, `SerializationException`) still fall to `UNEXPECTED_ERROR`, understating the epic's general "propagates Redis failures as SystemException" wording (edge-case-hunter) | medium (if broader scope intended) | Confirmed narrow by design: this spec's frozen Intent, per the human's Open-Questions decision, names `RedisConnectionFailureException` specifically -- the one type Story 2.8's own AC ("Given Redis is stopped") exercises and the only one verified passing in `RedisDownIT`. Broader Redis-failure-mode coverage is a real but separately-scoped hardening gap the frozen intent does not claim to close. | defer |
| `RedisDownIT` stops a live Testcontainers Redis mid-test; an already-open Lettuce connection could in principle still succeed on the next command before the break surfaces, risking flakiness (edge-case-hunter) | maybe-false | Plausible in principle, but this diff's own `RedisDownIT` run passed cleanly (1/1, confirmed via its JUnit XML report) with no observed race. Settling this needs several repeated CI runs or an explicit await-until-unreachable step. If real: intermittent CI flakiness (medium). | defer |

## Design Notes

`RedisDownIT` cannot extend `IntegrationTestBase`: Spring Boot's `@ServiceConnection` support discovers annotated container fields across the whole test class hierarchy, so a subclass declaring its own Redis container alongside the inherited shared one would register two competing `RedisConnectionDetails` candidates. Standing up a fully independent, JUnit-managed Postgres+Redis pair for this one class avoids that conflict and, more importantly, avoids ever stopping the JVM-shared singleton that every other Integration Test in the suite depends on -- Testcontainers cannot guarantee the same mapped port after `.stop()`/`.start()`, so a shared-container stop would risk leaving later tests unable to reconnect.

The Redis-down read does not need a widget actually persisted in Postgres first: `@Cacheable(sync = true)` intercepts before `WidgetService.findById`'s body runs, so a Redis connection failure surfaces before any repository/Postgres call -- any UUID, including one that doesn't exist, exercises the failure path.

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless pass; both new Integration Tests pass alongside the full existing suite.
