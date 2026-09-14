- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-gradle-build-skeleton-with-a-version-catalog.md`
  summary: Add a top-level LICENSE file — the repository is meant to be cloned/forked but carries no license.
  evidence: blind-hunter review pass 1 flagged the omission; no Epic 1–5 story adds a LICENSE. Not caused by Story 1.1; a project-setup gap.

## Deferred from: code review of spec-1-1-gradle-build-skeleton-with-a-version-catalog (2026-09-09)

- No README in the repo after Story 1.1 introduces the whole build system: no `./gradlew build` / `bootRun` instructions, no JDK 25 prerequisite, no note that the toolchain auto-provisions via the foojay resolver. Owned by Story 1.8 (Document the local development loop).
- No CI workflow runs `./gradlew build` on push/PR, and `gradle-wrapper.jar` itself is not integrity-checked (only the distribution `distributionSha256Sum` is pinned). Owned by Epic 4 — Story 4.2 (CI on every PR) should also add Gradle's `wrapper-validation-action`.

## Deferred from: code review of spec-1-2-format-and-lint-enforced-in-the-build (2026-09-10)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-format-and-lint-enforced-in-the-build.md`
  summary: The README (Story 1.8) must document that `ktlint_official`'s `standard:filename` rule requires PascalCase Kotlin source file names — a lowercase or underscore-prefixed `.kt` file now fails `./gradlew check`.
  evidence: Story 1.2 review (blind-hunter, verification-gap) — adopting `ktlint_official` activated `standard:filename`; there is no developer-facing note until the README lands. Convention documentation is owned by Story 1.8.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-format-and-lint-enforced-in-the-build.md`
  summary: CI (Story 4.2) should prove the lint gate actually fails the build — plant a known formatting / wildcard-import violation and assert `./gradlew build` exits non-zero — not merely run `./gradlew build` against an already-clean tree.
  evidence: Story 1.2 review (verification-gap, blind-hunter) — the `check` → `spotlessCheck` dependency is supplied implicitly by the Spotless plugin and verified only by a manual, ephemeral check; a future Spotless bump could silently drop enforcement with nothing catching it. A Gradle TestKit module is disproportionate now; Story 4.2 owns CI.

## Deferred from: code review of spec-1-3-concern-package-layout-and-a-bootable-application (2026-09-10)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-concern-package-layout-and-a-bootable-application.md`
  summary: The `tasks.withType<Test>` block in `build.gradle.kts` sets only `useJUnitPlatform()` — no `testLogging { events(...); exceptionFormat = FULL }`. An integration-test failure during `./gradlew build` (local or CI) prints minimal output, adding debugging friction for every clone.
  evidence: Story 1.3 review iteration 1 (blind-hunter). Pre-existing since the Story 1.1 build skeleton; not caused by this change. CI output/ergonomics is owned by Epic 4 — Story 4.2 (CI builds and tests every pull request).

## Deferred from: code review of spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment (2026-09-10)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: Story 2.1 (first JPA entity) must wire Kotlin no-arg support for JPA — add the `org.jetbrains.kotlin.plugin.jpa` (no-arg) plugin, or hand-write no-arg constructors on every `@Entity`. The `spring-boot-starter-data-jpa` dependency is on the classpath as of Story 1.5, but only `kotlin-spring` (all-open) is applied; a Kotlin `@Entity` without a no-arg constructor fails at runtime.
  evidence: Story 1.5 review (blind-hunter). Not a defect in Story 1.5 — the frozen scope forbids any `@Entity` here — but the gap becomes live the moment `WidgetEntity` (AD-4) is introduced.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: DB-backed verification of Story 1.5's persistence wiring is deferred to Epic 2 / Story 2.2. No automated test currently covers: the app booting and connecting with a real datasource; Flyway creating `flyway_schema_history` on startup; the policy values `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.validate-on-migrate=true`, `spring.jpa.open-in-view=false` holding; or Flyway failing loudly on a checksum-mismatched / missing migration. Story 2.2's `@ServiceConnection` Testcontainers base is the natural home for these assertions.
  evidence: Story 1.5 review (blind-hunter, verification-gap). Frozen intent excludes Testcontainers and a real-DB `@SpringBootTest` from this story; the spec routes DB-backed verification to Story 2.2. Manual AC-4 is the only current cover and must be run before Story 1.5 is marked done.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: Story 1.8 (README local loop) must document `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` as the required datasource env vars for any non-`local` run, that `application-local.yaml` + `SPRING_PROFILES_ACTIVE=local` is the local alternative, and that an exported `SPRING_DATASOURCE_*` overrides the `local` profile (standard Spring property-source precedence — Hikari logs the effective URL, so it is visible but easy to miss).
  evidence: Story 1.5 review (blind-hunter, edge-case-hunter). Frozen "Never: No README changes — Story 1.8"; operator documentation is owned by Story 1.8.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: RESOLVED by Story 1.6 — adding `spring-boot-starter-data-redis` did NOT require a test-scope exclude. Verified empirically (twice, independently): with the starter on the classpath and nothing on port 6379, `StarterApplicationIT` and `LivenessProbeIT` both stay green. Lettuce's connection factory is lazily connected — no eager ping at context startup. `src/test/resources/application.yaml` was left unchanged.
  evidence: Story 1.5 review (blind-hunter). The exclude list must manually track every persistence autoconfig `main` pulls in; drift is silent until a `@SpringBootTest` breaks. This particular drift never materialized.

## Deferred from: code review of spec-1-6-redis-connection-and-readiness-gating (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-redis-connection-and-readiness-gating.md`
  summary: No automated test exercises `/actuator/health/readiness` end-to-end — that Postgres+Redis both reachable yields `UP` and Redis stopped yields `DOWN` via the actual `management.endpoint.health.group.readiness.include` wiring. A regression in that property (dropped, misspelled, or reverted) would ship undetected by `./gradlew build`; only `RedisHealthDownTest` (a hand-built `ApplicationContextRunner` bypassing the group config) and a one-time manual `curl` check cover this today.
  evidence: Story 1.6 review (blind-hunter, verification-gap [pre-verified]). Frozen intent explicitly excludes live-Redis/Postgres tests here (mirrors Story 1.5's same deferral); Story 2.2's `@ServiceConnection` Testcontainers base is the intended home for this assertion.

## Deferred from: code review of spec-1-7-run-the-service-locally-on-the-local-profile (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-run-the-service-locally-on-the-local-profile.md`
  summary: No automated regression guard covers the `bootRun` task's default-to-`local`-profile behavior in `build.gradle.kts` — a future edit to that Gradle block (e.g. accidentally removed, or the env-var check inverted) would silently break the default with nothing failing in `./gradlew build` or CI. Coverage would need a Gradle TestKit functional test, which this project has no infrastructure for yet.
  evidence: Story 1.7 review (blind-hunter). Real gap, but adding TestKit infra is beyond this story's footprint (a one-line Gradle task default); manual verification (see spec's Verification/Implementation Notes) is the only current cover, mirroring the precedent Stories 1.5/1.6 set for build/infra wiring not easily unit-tested.

## Deferred from: code review of spec-2-1-widget-domain-model-jpa-entity-and-first-migration (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-widget-domain-model-jpa-entity-and-first-migration.md`
  summary: `WidgetEntity` is a final Kotlin class (no `kotlin-jpa`/allopen plugin); a future story that adds a lazy association or calls `getReferenceById()` may hit a Hibernate proxying failure.
  evidence: Story 2.1 review (blind-hunter, maybe-false). No lazy association or `getReferenceById()` call exists yet, so nothing is reachable today; settle by checking the first story that adds either.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-widget-domain-model-jpa-entity-and-first-migration.md`
  summary: `WidgetEntity`'s JPA mapping (`@Column` names/types) and the `V1__create_widgets.sql` schema are never validated against a real Hibernate session or Flyway run in `./gradlew build` — `WidgetEntityTest` is pure in-memory, and the test config excludes JPA/datasource/Flyway autoconfiguration until Story 2.2 lands.
  evidence: Story 2.1 review (verification-gap, pre-verified/filed as defer). Story 2.2 (shared Testcontainers base class and a persistence Integration Test) is the explicit, already-planned owner of this coverage.

## Deferred from: code review of spec-2-3-rfc-7807-error-contract-and-the-global-exception-handler (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-rfc-7807-error-contract-and-the-global-exception-handler.md`
  summary: `GlobalExceptionHandler.respond()` builds `problem.instance = URI.create(request.requestURI)`, which throws `IllegalArgumentException` for a request path containing characters outside `java.net.URI`'s RFC 2396 grammar — turning error rendering itself into an unhandled 500 with no problem+json body for that narrow class of request.
  evidence: edge-case-hunter review. Reachability is unsettled: Tomcat's default connector (`relaxedPathChars`/`relaxedQueryChars` both unset) rejects non-compliant paths before dispatch, so this project's default config likely never reaches the vulnerable line; only a future connector-relaxation config, a different embedded server, or a reverse proxy that forwards a raw path would exercise it. Settle by adding a `relaxedPathChars` integration test or wrapping `URI.create` defensively if that configuration is ever adopted.

## Deferred from: code review of spec-2-4-widget-service-operations (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-4-widget-service-operations.md`
  summary: `WidgetService.update` has no optimistic locking, so two concurrent updates to the same widget can silently lose one write with no error.
  evidence: edge-case-hunter review. Real if it occurs (would be medium severity — a silent lost update), but nothing in Epic 2's requirements or architecture calls for optimistic locking on widgets, and no test or usage in this story's scope demonstrates the race. Settle by adding a `@Version` column to `WidgetEntity` (plus a migration) and a concurrency integration test hitting two simultaneous updates, if this pattern needs to guard against lost updates.

## Deferred from: code review of spec-2-5-widget-rest-endpoints-and-dtos (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-5-widget-rest-endpoints-and-dtos.md`
  summary: A non-UUID `/api/v1/widgets/{id}` path segment, a malformed JSON request body, or a missing `Content-Type` header falls through to `ResponseEntityExceptionHandler`'s inherited handling and comes back as a bare `ProblemDetail` — missing the `code`/`traceId`/`details` extension members every other error response carries.
  evidence: blind-hunter + edge-case-hunter (two independent findings on the same root cause). Confirmed real: `GlobalExceptionHandler`'s own class doc (Story 2.3) already names this exact gap — only the four explicit `@ExceptionHandler` methods add `code`/`traceId`/`details`; framework-raised exceptions are still handled by the superclass's inherited, more-specific handlers. The gap predates this story; `WidgetController`'s new endpoints are simply the first callers to actually exercise it. Settle by overriding `handleExceptionInternal` (or the specific `handleTypeMismatch`/`handleHttpMessageNotReadable` methods) in `GlobalExceptionHandler` to add the same three extension members to every framework-raised exception.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-5-widget-rest-endpoints-and-dtos.md`
  summary: No `jackson-module-kotlin` dependency and no `-java-parameters` Kotlin compiler flag exist anywhere in the build, which is the usual pairing needed for Jackson to reliably deserialize a Kotlin data class's constructor.
  evidence: blind-hunter review. Confirmed the dependency is genuinely absent; every test still passes today (Jackson resolves `WidgetRequest`'s single-arg constructor without help), so this is a latent fragility rather than a live bug. Predates this story — `WidgetRequest`/`WidgetResponse` are simply the first DTOs to depend on it. Settle by adding `com.fasterxml.jackson.module:jackson-module-kotlin` (Boot auto-registers it once on the classpath) if a future DTO shape change or Jackson upgrade ever breaks deserialization.

## Deferred from: code review of spec-2-6-request-validation-with-field-level-errors (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-request-validation-with-field-level-errors.md`
  summary: A `POST`/`PUT` `/api/v1/widgets` body that omits `name` entirely (e.g. `{}`) never reaches `@Valid`/`@NotBlank`, because Jackson's reflective constructor call trips Kotlin's own null-check before Bean Validation runs; it surfaces as `HttpMessageNotReadableException` and comes back as a bare `ProblemDetail` without `code`/`traceId`/`errors`, unlike an empty-string or whitespace-only `name`.
  evidence: blind-hunter + edge-case-hunter (two independent findings on the same root cause). Confirmed real and reachable via ordinary client input. Same root cause already deferred from spec-2-5's review (`ResponseEntityExceptionHandler`'s inherited handling of framework-raised exceptions predates Story 2.3); this story's own "Never" boundary explicitly excludes touching that inherited-handler machinery, so it stays out of scope here too. Settle together with the spec-2-5 deferral by overriding `handleHttpMessageNotReadable` (or `handleExceptionInternal`) in `GlobalExceptionHandler`.

## Deferred from: code review of spec-2-7-redis-cache-aside-on-read-by-id (2026-09-13)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-7-redis-cache-aside-on-read-by-id.md`
  summary: No test exercises the real `RedisCacheManager`-backed cache-aside path (a `Widget` surviving an actual JDK-serialization round trip through Redis) — `WidgetServiceCacheTest` uses an in-memory `ConcurrentMapCacheManager` that never serializes, and none of the three `@SpringBootTest` Integration Tests that boot a real Testcontainers Redis (`StarterApplicationIT`, `LivenessProbeIT`, `WidgetRepositoryIT`) call `WidgetService.findById`.
  evidence: verification-gap review (pre-verified). Confirmed by tracing all three real-Redis-backed integration tests: none invoke the cached service method, so a regression (e.g. dropping `Serializable`, misconfiguring `spring.cache.type`) would ship undetected by `./gradlew build`. Explicitly out of scope for this story per its own "Never" boundary; Story 2.8's dedicated Testcontainers-backed cache Integration Test should call through `WidgetService.findById`/`update` against real Redis to close this gap.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-7-redis-cache-aside-on-read-by-id.md`
  summary: `@Transactional` and `@CacheEvict` on `WidgetService.update`/`delete` have no explicit advisor ordering, so eviction's advisor could in principle run before the transactional advisor commits, letting a concurrent reader repopulate the cache with pre-commit (soon-to-be-stale) data.
  evidence: blind-hunter + edge-case-hunter (two independent findings on the same root cause). Verified the mechanism is real by decompiling `spring-context`/`spring-tx` 7.0.9: both `ProxyCachingConfiguration` and `ProxyTransactionManagementConfiguration` set their advisor's order from their own `@EnableCaching`/`@EnableTransactionManagement` attribute, defaulting to `Ordered.LOWEST_PRECEDENCE` when unset (as here) — the two advisors tie, and which one wraps the other depends on Spring Boot's internal autoconfiguration registration order, not anything this diff controls. Whether the race actually manifests in this app's live proxy chain was not established; if real, this would be medium severity (a stale-repopulation window in the reference pattern every future resource copies). Settle with a concurrency test against a deliberately slowed transaction, or by reflectively inspecting the live advisor chain's order — or preempt it by setting `@EnableTransactionManagement(order = 0)`/`@EnableCaching(order = 1)` (or evicting via `TransactionSynchronizationManager.registerSynchronization(...)` after commit) if the pattern needs a guaranteed-safe ordering.

## Deferred from: code review of spec-2-8-cache-behavior-integration-test (2026-09-13)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-8-cache-behavior-integration-test.md`
  summary: `WidgetService.update`/`delete`'s `@CacheEvict` (default `beforeInvocation = false`) could let the Postgres write commit before a Redis-down eviction throws, so a client sees `500` for a write that actually persisted, and the stale cached value survives once Redis recovers.
  evidence: blind-hunter review. Real mechanism if the commit-then-evict ordering holds, but `WidgetService.kt` is untouched by this story's diff (the annotations were added in Story 2.7), and the exact advisor ordering that would confirm or refute it is the same open question already deferred, unresolved, from spec-2-7's own review (see the advisor-ordering entry above). Severity if confirmed: medium (a client-visible false-negative on a persisted write, in the reference pattern every future resource copies). Settle together with that entry.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-8-cache-behavior-integration-test.md`
  summary: `GlobalExceptionHandler` maps only `RedisConnectionFailureException` to `SystemException`/`SYSTEM_ERROR`; sibling Redis exception types (`RedisSystemException`, `QueryTimeoutException`, `SerializationException`) still fall through to the generic `UNEXPECTED_ERROR` handler, understating the epic's general "propagates Redis failures as SystemException" wording.
  evidence: edge-case-hunter review. Confirmed narrow by design: this story's frozen Intent, per the human's own Open-Questions decision, names `RedisConnectionFailureException` specifically — the one type Story 2.8's AC ("Given Redis is stopped") actually exercises and the only one verified passing in `RedisDownIT`. Broader Redis-failure-mode coverage (timeouts, serialization errors under a live-but-degraded Redis) is a real, separately-scoped hardening gap. Settle by adding `@ExceptionHandler` coverage (or a common supertype match) for the other Spring Data Redis exception types, backed by a test that induces each failure mode (e.g. a deliberately slow/misconfigured Redis for timeouts).
- source_spec: `_bmad-output/implementation-artifacts/spec-2-8-cache-behavior-integration-test.md`
  summary: `RedisDownIT` stops a live Testcontainers Redis mid-test; an already-open Lettuce connection could in principle still succeed on the very next command before the OS/Docker layer surfaces the broken connection, risking a flaky pass in CI.
  evidence: edge-case-hunter review. Plausible in principle, but this diff's own `RedisDownIT` run passed cleanly (1/1, confirmed via its JUnit XML report) with no observed race. Unverified either way from a single run; settling this needs several repeated CI runs, or an explicit await-until-unreachable step before issuing the request. If real: intermittent CI flakiness (medium).

## Deferred from: code review of spec-2-10-served-openapi-and-local-only-swagger-ui (2026-09-13)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-10-served-openapi-and-local-only-swagger-ui.md`
  summary: `WidgetServiceCacheIT`'s "update evicts the Redis entry..." test intermittently fails only when the full suite runs together, though it passes reliably in isolation.
  evidence: blind-hunter review. Story 2.10's own implementation subagent reproduced the same intermittent failure on unmodified baseline `main` (stashing this story's changes first), confirming it predates and is unrelated to this story's diff. Not logged anywhere actionable before now. Settle by running the full suite repeatedly to isolate the interacting test(s) (likely shared JVM-wide Testcontainers state or cache-key collision across suites), then fix the ordering/isolation issue.

## Deferred from: code review of spec-2-11-add-a-rest-resource-guide-and-example-slice-removal (2026-09-14)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-11-add-a-rest-resource-guide-and-example-slice-removal.md`
  summary: README's "Package layout" section still claims "The starter ships with none of these classes yet," which Story 2.11's own new "Add a REST resource"/"Remove the Example Slice" sections contradict by naming the concrete `widgets` files that already exist.
  evidence: blind-hunter review. Confirmed at `README.md`'s "Package layout" section, unchanged by this diff. Pre-existing since Story 2.1 first added `Widget*` classes, not caused by this story. Settle by updating that section's wording once a resource exists in the repo (now true since Epic 2 landed `widgets`).
