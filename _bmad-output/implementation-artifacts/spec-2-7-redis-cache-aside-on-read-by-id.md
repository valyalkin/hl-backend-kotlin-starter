---
title: 'Redis cache-aside on read-by-id'
type: 'feature'
created: '2026-09-13'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'e9e39f6c63d65e0bad194d42e503cf35d111b061'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `WidgetService.findById` always queries Postgres, and `update`/`delete` never invalidate anything, so every read pays a database round trip even though Redis is already a hard dependency (Story 1.6) with nothing caching against it yet.

**Approach:** Annotate `findById` `@Cacheable` and `update`/`delete` `@CacheEvict` (same cache, same key) so Spring's caching abstraction — not hand-written orchestration — serves reads from Redis and invalidates them on write, with a configurable TTL.

## Boundaries & Constraints

**Always:**
- `WidgetService.findById` is `@Cacheable(cacheNames = ["widgets"], key = "#id")`; `update` and `delete` are `@CacheEvict(cacheNames = ["widgets"], key = "#id")` — same cache name and key expression across all three.
- `spring-boot-starter-cache` is added to `build.gradle.kts` (BOM-managed, no version literal per AD-22) so `@EnableCaching`/`RedisCacheManager` autoconfiguration is on the classpath.
- A new `config/CacheConfig.kt` (`@Configuration @EnableCaching`) activates caching — the first file in the previously-empty `config/` concern package (AD-1).
- TTL is set via the framework's own `spring.cache.redis.time-to-live` property in `application.yaml`, defaulting to `10m` — no custom `CacheManager` bean.
- `Widget` implements `java.io.Serializable` — `RedisCacheConfiguration.defaultCacheConfig()`'s default value serializer is JDK `RedisSerializer.java()`, which requires the cached type to be `Serializable`; `id`/`createdAt`/`updatedAt` are already `Serializable`.
- No manual cache orchestration: no hand-written Redis get/put/fallthrough, no `CacheErrorHandler` bean (Redis failures propagate and fail fast, per AD-13).

**Never:**
- No change to `create` — it is neither `@Cacheable` nor a cache-populating write in this story.
- No custom `CacheManager`, key generator, or JSON/Jackson value serializer — keep the framework default (JDK serialization) to minimize footprint.
- No Redis-down failure-mode test and no Testcontainers-backed cache Integration Test here — that is Story 2.8's dedicated scope.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Read same id twice | `findById(id)` called twice, no write between | Second call is served from the cache; the fake/counting repository sees exactly one call | N/A |
| Update a cached id | `findById(id)`, then `update(id, name)`, then `findById(id)` again | Cache entry evicted by `update`; the third call reaches the repository again and returns the new name | N/A |
| Delete a cached id | `findById(id)`, then `delete(id)`, then `findById(id)` again | Cache entry evicted by `delete`; the third call reaches the repository and throws `NotFoundException` | `NotFoundException` |

</frozen-after-approval>

## Code Map

- `build.gradle.kts` -- add `implementation("org.springframework.boot:spring-boot-starter-cache")` alongside the other starters.
- `src/main/kotlin/com/hl/service/config/CacheConfig.kt` -- new; `@Configuration @EnableCaching class CacheConfig`.
- `src/main/kotlin/com/hl/service/model/Widget.kt` -- add `, java.io.Serializable` to the `data class` declaration; no field changes.
- `src/main/kotlin/com/hl/service/service/WidgetService.kt` -- add `@Cacheable(cacheNames = ["widgets"], key = "#id")` on `findById`; add `@CacheEvict(cacheNames = ["widgets"], key = "#id")` on `update` and `delete` (alongside their existing `@Transactional`).
- `src/main/resources/application.yaml` -- add `spring.cache.type: redis` and `spring.cache.redis.time-to-live: 10m` near the existing `spring.data.redis.*` block.
- `src/test/kotlin/com/hl/service/service/WidgetServiceCacheTest.kt` -- new; proves the cache-aside wiring with `ApplicationContextRunner` (styled like `RedisHealthDownTest`), `@EnableCaching` + in-memory `ConcurrentMapCacheManager` (no Redis, hermetic per NFR-1), and a small call-counting wrapper around `FakeWidgetRepository` (reused as-is, not modified).

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add `spring-boot-starter-cache` -- enables `@EnableCaching`/`RedisCacheManager` autoconfiguration
- [x] `src/main/kotlin/com/hl/service/config/CacheConfig.kt` -- add `@EnableCaching` configuration class -- activates Spring's caching abstraction
- [x] `src/main/kotlin/com/hl/service/model/Widget.kt` -- implement `java.io.Serializable` -- required by the default Redis value serializer
- [x] `src/main/kotlin/com/hl/service/service/WidgetService.kt` -- annotate `findById` `@Cacheable`, `update`/`delete` `@CacheEvict`, same cache and key -- framework-managed cache-aside (epic goal)
- [x] `src/main/resources/application.yaml` -- set `spring.cache.type: redis` and `spring.cache.redis.time-to-live: 10m` -- documents the configurable TTL default
- [x] `src/test/kotlin/com/hl/service/service/WidgetServiceCacheTest.kt` -- add hit/evict tests per the I/O matrix -- proves the annotations actually take effect under a real caching proxy

**Acceptance Criteria:**
- Given `WidgetService`, when inspected, then `findById` is `@Cacheable` and `update`/`delete` are `@CacheEvict` for the same cache and key, with no manual cache-orchestration code.
- Given the TTL, when configuration is inspected, then it is a `spring.cache.redis.time-to-live` value defaulting to `10m`.
- Given the new cache test, when it runs, then it asserts a second read within TTL avoids the repository and that `update`/`delete` evict so a subsequent read does not return stale data.

## Implementation Notes

- `WidgetServiceCacheTest`'s `CountingWidgetRepository` wraps `FakeWidgetRepository` via Kotlin interface delegation (`WidgetRepository by delegate`), overriding only `findById` to count calls -- the shared fake itself is untouched, per the spec.
- `update`'s "evicts" test does not assert an exact call-count total: `WidgetService.update` calls `widgetRepository.findById` directly as part of its own logic (separate from the cached service-level `findById`), so the count already moves before the cache is checked again. The test instead asserts the count strictly increases across the post-eviction read, which proves eviction without hardcoding `update`'s internal implementation detail.
- Verified: `./gradlew build` -- BUILD SUCCESSFUL; ktlint/spotless clean; full suite green (39 tests, including the 3 new `WidgetServiceCacheTest` cases and the existing Testcontainers-backed `StarterApplicationIT`/`LivenessProbeIT`, which now boot with `RedisCacheManager` autoconfigured against the real Testcontainers Redis with no failures). Independently re-ran `./gradlew build` after implementation and confirmed the `WidgetServiceCacheTest` XML report shows `tests="3" failures="0" errors="0"`.
- Review pass (see Review Triage Log) sent 4 `patch` findings back to the implementation agent: declare an explicit `serialVersionUID` on `Widget` (prevents an implicit-UID shift from invalidating already-cached entries on an unrelated recompile/toolchain change), add `sync = true` to `findById`'s `@Cacheable` (cache-stampede protection), add a fourth `WidgetServiceCacheTest` case proving `key = "#id"` discriminates per-id (two widgets, evicting one leaves the other's cache entry untouched), and mirror the new `spring.cache.*` keys into `src/test/resources/application.yaml` to match that file's own "mirrors the main config" comment. All 4 applied. Verified independently: `./gradlew build` -- BUILD SUCCESSFUL; full suite now 40 tests, 0 failures/errors, including the new 4th `WidgetServiceCacheTest` case.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| `Widget` implements `java.io.Serializable` with no explicit `serialVersionUID` (blind-hunter + edge-case-hunter) | medium | Confirmed: the JVM computes a default `serialVersionUID` from the class's structure/bytecode when none is declared; any future field change to `Widget` -- or even an unrelated Kotlin/JDK toolchain bump that shifts generated bytecode -- changes the computed UID, so cached entries written under the old UID fail to deserialize (`InvalidClassException`) during the 10-minute TTL window of a rolling deploy. Cheap to prevent. | patch |
| All three `WidgetServiceCacheTest` cases exercise exactly one widget id each, so nothing proves `key = "#id"` discriminates per-id -- a regression collapsing the key to a constant (colliding two widgets' cache entries) would still pass every existing assertion (blind-hunter) | low | Confirmed: each test's `CountingWidgetRepository`/`widgetService` interaction touches a single `created.id`; no test reads two distinct ids back-to-back to confirm isolation. Fix is a direct test addition, not a design change. | patch |
| `WidgetService.findById`'s `@Cacheable` is not `sync = true`, so concurrent misses for the same id can issue duplicate simultaneous Postgres reads (edge-case-hunter + blind-hunter, "cache-stampede") | low | Confirmed: no synchronization is applied; each caller finding a cache miss queries independently. Not a correctness bug (each still returns the right data), only duplicated work under a narrow concurrent-miss window. `sync = true` is a one-attribute fix compatible with `RedisCacheManager`. | patch |
| `src/test/resources/application.yaml` documents itself as mirroring `src/main/resources/application.yaml` but was not updated with the new `spring.cache.type`/`spring.cache.redis.time-to-live` keys (blind-hunter) | low | Confirmed: the file's own comment says it "mirrors src/main/resources/application.yaml"; the new cache keys are absent. Currently inert (no IT invokes the cached service method -- see the deferred verification-gap finding below), but leaves stale/missing config for Story 2.8 to inherit. Direct two-line addition. | patch |
| `@Transactional` and `@CacheEvict` on `update`/`delete` have no explicit advisor ordering, so eviction could in principle fire before the transaction commits, letting a concurrent reader repopulate the cache with pre-commit data (blind-hunter + edge-case-hunter) | maybe-false | Verified the mechanism is real: decompiling `spring-context`/`spring-tx` 7.0.9 shows both `ProxyCachingConfiguration` and `ProxyTransactionManagementConfiguration` set their advisor's order from their own `@EnableCaching`/`@EnableTransactionManagement` attribute (defaulting to `Ordered.LOWEST_PRECEDENCE` when unset, as here), so the two advisors tie and their relative order depends on Spring Boot's internal autoconfiguration registration order -- not something this diff controls or asserts. Whether the race actually manifests in this app's specific proxy order was not established (would need a concurrency test with a deliberately slowed transaction, or reflective inspection of the live advisor chain); if real, this would be medium (a stale-repopulation window in the copyable reference pattern). | defer |
| The real `RedisCacheManager`-backed cache-aside path (JDK-serialization round trip through actual Redis) is never exercised by any test -- `WidgetServiceCacheTest` uses an in-memory `ConcurrentMapCacheManager` that never serializes, and none of the three `@SpringBootTest` ITs that boot a real Testcontainers Redis call `WidgetService.findById` (verification-gap, pre-verified) | medium | Filed with evidence: traced all three real-Redis-backed integration tests (`StarterApplicationIT`, `LivenessProbeIT`, `WidgetRepositoryIT`) and confirmed none invoke the cached service method; a regression (e.g., dropping `Serializable`) would ship undetected. Per the spec's own "Never" boundary, the real-Redis-backed cache Integration Test is explicitly Story 2.8's scope, not this story's. | defer |
| Redis-down failure handling (no `CacheErrorHandler`, so `findById`/`update`/`delete` throw when Redis is unreachable) (edge-case-hunter) | false | This is the spec's explicit, intentional design (`Boundaries & Constraints` "Always": "no `CacheErrorHandler` bean") matching AD-13's fail-fast policy and the epic's own stated requirement ("Redis down → GET /{id} returns 500 ... no Postgres-served fallback"). Not a defect -- it is the required behavior. | false |
| `sprint-status.yaml` shows `2-7-...` as `in-progress` while the spec's own frontmatter is `status: 'in-review'` (blind-hunter) | false | Expected in-flight state, identical precedent already logged in spec-2-3's and spec-2-4's own triage logs: sprint status syncs to `review` once this step completes successfully, per the workflow's own step-05. | false |
| No test proves a failed lookup (`NotFoundException`) is not cached (edge-case-hunter-style gap) | low | Not-caching-on-exception is a guaranteed Spring Cache framework contract (`CacheAspectSupport` only populates the cache after successful invocation), not custom logic introduced by this diff -- unlike the `key = "#id"` expression (kept above), there is no bespoke behavior here to regress. A dedicated test would only re-prove the framework's own guarantee. | rejected (low severity, redundant -- verifies framework behavior, not this diff's own logic) |

## Design Notes

`RedisCacheConfiguration.defaultCacheConfig()` (decompiled from the resolved `spring-data-redis` jar) builds its key serializer from `RedisSerializer.string()` and its value serializer from `RedisSerializer.java(classLoader)` — plain JDK serialization. Keys go through a `DefaultFormattingConversionService`, which converts any object to `String` via its own `ObjectToStringConverter` fallback, so `UUID` keys need no change. Values do not get that conversion — hence `Widget` must implement `Serializable`, and no Jackson/JSON serializer is introduced since the default already works with a one-line change.

`spring.cache.redis.time-to-live` is Boot's own property for `RedisCacheManager`'s default TTL (applied to every cache, dynamically created caches included) — this satisfies "TTL is a configuration value with a documented default" with zero custom `CacheManager` code, consistent with "no manual cache orchestration."

`WidgetServiceCacheTest` uses `ConcurrentMapCacheManager`, not a real Redis, because proving the *annotation wiring* (proxy applies, key matches, eviction fires) needs `@EnableCaching` and a `CacheManager` bean but not Redis itself; the Redis-specific behavior (real TTL expiry, the down-Redis failure mode) is Story 2.8's Testcontainers-backed Integration Test, kept out of this hermetic Unit Test (NFR-1).

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless pass; the new `WidgetServiceCacheTest` cases pass alongside the full existing suite.
