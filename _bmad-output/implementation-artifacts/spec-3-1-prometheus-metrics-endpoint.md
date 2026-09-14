---
title: 'Prometheus metrics endpoint'
type: 'feature'
created: '2026-09-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'f58389055f8df05afc684fa74cc0b74fb730335e'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The service has no scrapeable metrics endpoint — actuator's default web exposure is `health` only, so a Prometheus server pointed at it today gets nothing, and JVM/HTTP/datasource/cache health is invisible until an operator adds instrumentation by hand.

**Approach:** Add the `micrometer-registry-prometheus` dependency and widen actuator's web exposure to exactly `health,info,prometheus` via configuration, so `GET /actuator/prometheus` returns Prometheus-format text covering JVM, HTTP server, datasource, and cache metrics with zero application code (AD-17).

## Boundaries & Constraints

**Always:**
- Actuator web exposure is exactly `health,info,prometheus` — nothing else — on the main port (8080). No `management.server.port` is introduced.
- The change is configuration-only: a build dependency plus YAML properties. No `@Configuration` class, custom `MeterBinder`, or metrics-related Kotlin code.
- `src/test/resources/application.yaml` mirrors whatever is added to `src/main/resources/application.yaml`, since it fully shadows the main file during tests (established convention, e.g. Story 1.6).
- The new Micrometer dependency is added the BOM-managed way: a bare coordinate string in `build.gradle.kts` with an `AD-22` comment, no entry in `gradle/libs.versions.toml`.

**Never:**
- No readiness/liveness health-group changes — `management.endpoint.health.group.readiness.include` stays `readinessState,db,redis` exactly as Story 1.6 left it.
- No separate management port, no `management.metrics.export.prometheus.*` tuning beyond what's needed to make the endpoint work, no custom tag/filter beans.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Scrape exposed endpoint | `GET /actuator/prometheus` | 200, `Content-Type` Prometheus text format, body contains JVM (`jvm_memory_used_bytes`), HTTP server (`http_server_requests_seconds_count`), datasource (`hikaricp_connections_active`), and cache (`cache_gets_total` or equivalent) metric families | N/A |
| Non-exposed endpoint stays hidden | `GET /actuator/env` (or any endpoint outside `health,info,prometheus`) | 404 | N/A |
| Existing health/info endpoints unaffected | `GET /actuator/health`, `GET /actuator/info` | 200, unchanged from current behavior | N/A |

</frozen-after-approval>

## Code Map

- `build.gradle.kts:38` -- add `implementation("io.micrometer:micrometer-registry-prometheus")` next to the existing `spring-boot-starter-actuator` line; BOM-managed (AD-22), no `libs.versions.toml` entry.
- `src/main/resources/application.yaml:41-45` -- existing `management.endpoint.health.group.readiness.include` block; add a sibling `management.endpoints.web.exposure.include: health,info,prometheus` key under the same `management:` root. Leave the readiness group untouched.
- `src/test/resources/application.yaml:17-21` -- mirror the same `management.endpoints.web.exposure.include` addition (this file fully shadows main `application.yaml` during tests).
- `src/test/kotlin/com/hl/service/LivenessProbeIT.kt:46-56` -- `` `non-exposed actuator endpoint returns 404` `` probes `/actuator/metrics` claiming only `health` is exposed; stale once exposure widens. Repoint at `/actuator/env` and fix the comment.
- `src/test/kotlin/com/hl/service/OpenApiIT.kt` -- reference for `RestTestClient` + `IntegrationTestBase` + JsonPath-scoped assertions (avoid whole-body substring checks) to follow in the new IT below.
- `README.md:89-91` (`### Run the service`) -- existing `curl .../actuator/health` example; add a sibling subsection for the Prometheus endpoint, same terse style as `### View API docs`.

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add `implementation("io.micrometer:micrometer-registry-prometheus")` with an AD-22 comment -- gives `/actuator/prometheus` a format to render.
- [x] `src/main/resources/application.yaml` -- add `management.endpoints.web.exposure.include: health,info,prometheus` -- widens exposure to exactly the three required endpoints.
- [x] `src/test/resources/application.yaml` -- mirror the same exposure key -- keeps test config in sync with main (this file fully shadows it).
- [x] `src/test/kotlin/com/hl/service/LivenessProbeIT.kt` -- repoint the "non-exposed actuator endpoint" test at `/actuator/env`, fix its comment -- keeps the test's premise accurate post-widening.
- [x] `src/test/kotlin/com/hl/service/PrometheusMetricsIT.kt` (new) -- same style as `LivenessProbeIT`/`OpenApiIT`, extends `IntegrationTestBase` -- assert `GET /actuator/prometheus` is 200 with a body containing JVM, HTTP server, datasource, and cache metric families; assert `GET /actuator/info` is 200.
- [x] `README.md` -- add a `### Metrics` subsection after `### Run the service` with a `curl .../actuator/prometheus` example and the three exposed endpoint names -- documents the endpoint for operators.
- [x] `src/main/resources/application.yaml` / `src/test/resources/application.yaml` (not in the original Code Map) -- add `spring.cache.cache-names: widgets` -- see Implementation Notes: required for the cache metric family to actually appear.

**Acceptance Criteria:**
- Given the running service on the default profile, when a client sends `GET /actuator/prometheus`, then it returns HTTP 200 with Prometheus-format text including at least one JVM, one HTTP server, one datasource, and one cache metric family.
- Given the running service, when a client sends `GET /actuator/env` (or any endpoint outside `health,info,prometheus`), then it returns HTTP 404.
- Given the running service, when a client sends `GET /actuator/health` or `GET /actuator/info`, then both return HTTP 200 unchanged from current behavior.
- Given the actuator configuration files, when inspected, then `management.endpoints.web.exposure.include` is exactly `health,info,prometheus` in both `src/main/resources/application.yaml` and `src/test/resources/application.yaml`, and no `management.server.port` exists anywhere in the repo.

## Implementation Notes

Verified empirically (real `bootRun` + `curl`, decompiled `spring-boot-cache-4.1.1.jar`'s
`CacheMetricsRegistrarConfiguration` bytecode) that Micrometer's cache-metrics
binder only sees caches that already exist on the `CacheManager` at the
moment that configuration class's constructor runs -- once, during context
refresh, before the app serves any HTTP request. `RedisCacheManager`
autoconfigures with no `initialCacheNames` unless `spring.cache.cache-names`
is set, so the "widgets" cache created lazily by `WidgetService`'s
`@Cacheable` never gets metrics bound, no matter how many times it's read
afterwards -- confirmed by curling `/actuator/prometheus` before and after
warming the cache via two `GET /api/v1/widgets/{id}` calls: zero `cache_*`
series either way.

Added `spring.cache.cache-names: widgets` to both `application.yaml` files
(not in the original Code Map) to pre-declare the cache so it exists at
`CacheManager` creation time. Re-verified: `cache_gets_total{cache="widgets",...}`
and friends appear immediately after startup, before any request. This is a
plain YAML property (no Kotlin/`@Configuration` code), consistent with the
frozen Approach's "a build dependency plus YAML properties," and necessary
for the frozen Intent's explicit promise of cache metric visibility to
actually hold.

Trade-off/risk: this couples the metrics config to the `widgets` example
resource's own cache name. If a future service adds its own `@Cacheable`
cache (or removes `widgets` per the README's "Remove the Example Slice"
section), that cache's own name must be added to (or `widgets` removed from)
`spring.cache.cache-names` for its metrics to be scrapeable -- otherwise the
cache still works, it just stays invisible to Prometheus. Left a comment in
`application.yaml` calling this out; did not touch the README's "Remove the
Example Slice" section since it's out of this spec's Code Map and is general
starter-template upkeep rather than an Epic 3 concern.

Matrix Test Audit (step-03) found the I/O matrix's "Existing health/info
endpoints unaffected" row only had automated coverage for `/actuator/info`
(via `PrometheusMetricsIT`) and `/actuator/health/liveness`+`/readiness`
(via `LivenessProbeIT`) -- no test hit the bare `/actuator/health` endpoint
named in that row and in the matching Acceptance Criterion, only a manual
`curl` check. Added `GET actuator health returns 200 unchanged by the
widened exposure` to `PrometheusMetricsIT.kt`, asserting `$.status` is `UP`.
Also added a `Content-Type` assertion (`text/plain` compatible) to the
`/actuator/prometheus` test, matching the matrix row's "Content-Type
Prometheus text format" expectation, which had no assertion. Confirmed the
live value via `bootRun` + `curl -I`: `text/plain;version=0.0.4;charset=utf-8`.
Full suite re-run: 61/61 tests pass, 0 failures/errors.

## Spec Change Log

- Added `spring.cache.cache-names: widgets` to `src/main/resources/application.yaml`
  and `src/test/resources/application.yaml`, beyond the original Code Map's
  two-key-only plan, after empirically confirming the cache metric family
  would otherwise never appear (see Implementation Notes). No frozen
  Intent/Boundaries text changed; this is an addition within "a build
  dependency plus YAML properties."

## Review Triage Log

- **medium / patch** — README's "Remove the Example Slice" section (blind-hunter, x2 merged: stale re new test file + stale re new config key). This diff adds two new couplings to `widgets` that the section's existing file/key list doesn't cover: `PrometheusMetricsIT.kt` imports `WidgetRequest` and hard-codes `/api/v1/widgets` to warm the cache (verified: no mention of this file in the section, unlike `OpenApiIT.kt`/`RedisDownIT.kt` which are listed), and `spring.cache.cache-names: widgets` in both `application.yaml` files is never called out as needing an update. A developer following the guide hits a build break (unlisted import) and a silent metrics loss (unlisted config key) that the guide claims to prevent.
- **low / reject** — "no automated safeguard keeps `spring.cache.cache-names` in sync with `@Cacheable` names" (blind-hunter). Real but already mitigated by an explicit warning comment on the property in both `application.yaml` files; building a consistency check is more than a direct correction, and this is unlikely to bite in everyday use of a starter template.
- **false / reject** — "`/actuator/info` returns an empty body since no build-info contributor is configured" (blind-hunter). The frozen Intent and the story's AC (epics.md Story 3.1, epic-3-context.md) require only that `health,info,prometheus` be exposed and respond 200 — neither promises `info` carries build metadata; adding a `buildInfo()`/git-properties contributor is a distinct, unrequested feature outside this story's Approach.
- **false / reject** — "`micrometer-registry-prometheus` should be `runtimeOnly`, not `implementation`, per project convention" (blind-hunter). Checked: `runtimeOnly` in this file is used only for the two raw JDBC/migration-driver jars (`flyway-database-postgresql`, `postgresql`); every other Spring Boot starter/autoconfigured registry (`-starter-validation`, `-starter-cache`, `-starter-actuator`, etc.) is `implementation` despite none being directly referenced from Kotlin source either — no such convention actually exists for this dependency's category.
- **false / reject** — "widened actuator exposure has no access-control/auth guidance" (blind-hunter). Explicitly deferred by architecture, not an oversight: AD-17 keeps actuator on the main port in v1, and an Auth Seam is its own separate, later epic (Epic 5, e.g. `5-4-inactive-fail-safe-oauth2-resource-server-auth-seam`).
- **low / patch** — README's new "Metrics" subsection has no scrape-config pointer or `Content-Type` mention (blind-hunter). Cosmetic completeness gap; direct addition of one line.
- **low / patch** — `LivenessProbeIT`'s "non-exposed actuator endpoint" test dropped its `/actuator/metrics` regression coverage when repointed to `/actuator/env` (blind-hunter + edge-case-hunter, same location/root cause, merged). `/actuator/metrics` is the endpoint most directly adjacent to this feature; losing its explicit 404 check is a real, if minor, coverage gap. Trivial fix: assert both endpoints 404, not just one.
- **low / patch** — `PrometheusMetricsIT.kt:57` uses `createResult.responseBody!!`, an unguarded NPE instead of the file's own `?: error(...)` pattern used two lines later for the same kind of nullable body (edge-case-hunter). Trivial, direct fix for consistency and a clearer test failure message.
- **low / patch** — `PrometheusMetricsIT.kt`'s class doc claims the `cache_gets_total` assertion "holds even if that pre-declared initial-cache-names wiring were ever removed" (edge-case-hunter). Verified false against this story's own Implementation Notes: the cache-metrics binder only sees caches present at `CacheManager` creation, so removing `cache-names: widgets` would in fact make that assertion fail, not silently hold. Misleading comment; trivial wording fix.
- **no verification gaps** — verification-gap layer traced the four claimed metric families to real, non-mocked assertions, confirmed the cache-name property matches actual `@Cacheable` usage, confirmed the readiness health group and `application-local.yaml` are untouched, and confirmed `@Tag("integration")` tests run under the plain `test` task with no exclusion filter. No entry to route.

All patch entries applied by the step-03 implementation subagent, plus one self-caught follow-up: the patched class doc on `PrometheusMetricsIT.kt` was corrected, but the in-method comment a few lines below it (`"...guaranteed to have data, independent of the cache-names pre-declaration..."`) still asserted the same now-disproven claim, directly contradicting the doc comment above it. Reworded to match. Full suite re-run after all patches: 61/61 tests pass, 0 failures/errors.

## Verification

**Commands:**
- `./gradlew build` -- expected: full suite passes, including the new `PrometheusMetricsIT` and updated `LivenessProbeIT`; format/lint check passes.
- `./gradlew bootRun` then `curl -i .../actuator/prometheus` -- expected: 200, body has `jvm_memory_used_bytes`, `http_server_requests`, `hikaricp_connections`, and a `cache_` family; `curl -i .../actuator/env` returns 404.
