---
title: 'Redis connection and readiness gating'
type: 'feature'
created: '2026-09-11'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'ddf06e1e9d4eb15d96e403aaabc1a8845ee389fe'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The service has Postgres wired (Story 1.5) but no Redis connection, and the readiness probe never reflects the datasource or a missing Redis — a pod could enter rotation with a dead cache or database and nothing would show it.

**Approach:** Add the Spring Data Redis (Lettuce) starter, host/port/password from configuration (defaulting to the Compose stack's no-auth `localhost:6379`), and widen the `readiness` health group to require the existing `db` contributor plus a new `redis` contributor both `UP`. No caching usage or `CacheErrorHandler` bean — this story only wires the connection and its health signal.

## Boundaries & Constraints

**Always:**
- Add one dependency: `implementation("org.springframework.boot:spring-boot-starter-data-redis")` — no version literal (AD-22); `libs.versions.toml` untouched. Transitively pulls `spring-boot-data-redis` autoconfig + Lettuce + Spring Data Redis — no second dependency (unlike Flyway in 1.5).
- `application.yaml`: `spring.data.redis.host/port/password` as `${SPRING_DATA_REDIS_HOST:localhost}` / `${SPRING_DATA_REDIS_PORT:6379}` / `${SPRING_DATA_REDIS_PASSWORD:}` — defaults equal the Compose stack (AD-15's general defaults-and-placeholders rule; no fail-loud requirement here, unlike the datasource).
- `application.yaml`: `management.endpoint.health.group.readiness.include: readinessState,db,redis` — readiness is `UP` only once the `db` and `redis` contributors (bean names verified against the 4.1.1 jars) plus Boot's own readiness state are all `UP` (AD-13, AD-17).
- No `CacheErrorHandler` bean anywhere (AD-13) — Spring's default fail-fast on a Redis error stays in place.

**Never:**
- No `application-local.yaml` change — Redis's defaults already equal the Compose stack; no fallback-less value like the datasource's.
- No `src/test/resources/application.yaml` exclude for Redis. Verified empirically: starter added, nothing on port 6379, `StarterApplicationIT`/`LivenessProbeIT` both stay green (Lettuce connects lazily). Resolves the 2026-09-10 `deferred-work.md` heads-up; it doesn't materialize.
- No `@Cacheable`/`@CacheEvict`/`@EnableCaching` or cache config class — Epic 2's `widgets` slice.
- No `docker-compose.yaml` / `.env` change — Redis already exists (Story 1.4).
- No test requiring live Redis/Postgres (no Testcontainers, same as 1.5) — happy-path readiness-UP is a manual check; Story 2.2 is the automated home.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Redis and Postgres both reachable | `GET /actuator/health/readiness` | `200 {"status":"UP"}`, `db` and `redis` both `UP` | N/A |
| Redis unreachable | `GET /actuator/health/readiness` | `200 {"status":"DOWN"}`, `redis` member `DOWN` | Health reports DOWN; no exception to the client |

</frozen-after-approval>

## Code Map

- `build.gradle.kts` -- add the one `implementation(...)` line next to the existing starters.
- `src/main/resources/application.yaml` -- add `spring.data.redis.*` and `management.endpoint.health.group.readiness.include` (see Design Notes).
- `src/test/kotlin/com/hl/service/RedisHealthDownTest.kt` -- new. `ApplicationContextRunner` + `DataRedisAutoConfiguration` + `DataRedisHealthContributorAutoConfiguration` + `HealthContributorRegistryAutoConfiguration`, pointed at unreachable `127.0.0.1:1`; asserts `redisHealthContributor.health()` is `DOWN`. Mirrors `DatasourceFailFastTest.kt`'s isolation (no `@SpringBootTest`, no `@Tag("integration")`).
- `src/test/kotlin/com/hl/service/{StarterApplicationIT,LivenessProbeIT,DatasourceFailFastTest}.kt` -- do NOT modify; both ITs confirmed green with the Redis starter present and nothing on 6379.
- Reference: `epic-1-context.md` (AD-13/15/17/22); `spec-1-5-...md` (the `db`-contributor / `${SPRING_DATASOURCE_*}` pattern this mirrors); `docker-compose.yaml` (Redis, port 6379, no auth).

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add the `spring-boot-starter-data-redis` dependency line.
- [x] `src/main/resources/application.yaml` -- add the `spring.data.redis.*` placeholders and the readiness group `include`.
- [x] `src/test/kotlin/com/hl/service/RedisHealthDownTest.kt` -- create.
- [x] Run `./gradlew spotlessApply` then `./gradlew build`; confirm green with no reformatting.

**Acceptance Criteria:**
- Given the Compose stack up and `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`, when the app starts, then it connects to Redis using `application.yaml`'s configured host/port (no hard-coded value in `.kt` source).
- Given `GET /actuator/health/readiness` with Postgres and Redis both reachable, then the response is `UP` with `db` and `redis` both `UP`.
- Given `GET /actuator/health/readiness` with Redis stopped, then the response is `DOWN`.
- Given `./gradlew build` on a machine with no Docker and nothing on 6379, then compile, `spotlessCheck`, `RedisHealthDownTest`, `DatasourceFailFastTest`, `StarterApplicationIT` and `LivenessProbeIT` all pass.

## Implementation Notes

- **Autoconfig FQCNs verified against the resolved 4.1.1 jars** (`spring-boot-data-redis-4.1.1.jar`, `spring-boot-health-4.1.1.jar`), matching the Code Map's expectation:
  - `org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration`
  - `org.springframework.boot.data.redis.autoconfigure.health.DataRedisHealthContributorAutoConfiguration`
  - `org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration` (Boot 4.1 moved this out of `spring-boot-actuator-autoconfigure` into the new `spring-boot-health` module, transitively pulled in).
  - The `redisHealthContributor` bean method is confirmed by decompiling `DataRedisHealthContributorAutoConfiguration` — one `RedisConnectionFactory` bean means `AbstractCompositeHealthContributorConfiguration.createContributor` returns the single `DataRedisHealthIndicator` directly (not a composite), so the test resolves the bean by name as `HealthIndicator` and calls `.health()`.
- **Kotlin nullability wrinkle:** `HealthIndicator.health()` compiles under strict JSR-305 (`-Xjsr305=strict`, AD-23) as returning `Health?` (platform/annotation nullability), so the test uses `contributor.health()!!.status` rather than a plain call — consistent with the module's `allWarningsAsErrors` strictness.
- **Resolved versions (BOM-managed, no `libs.versions.toml` entry):** `spring-boot-starter-data-redis` / `spring-boot-data-redis` / `spring-data-redis` 4.1.1, `lettuce-core` 7.5.2.RELEASE.
- **Full manual verification run (2026-09-11, Docker available):** `docker compose up -d`, then `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` — `GET /actuator/health/readiness` → `{"status":"UP"}` with Postgres and Redis both healthy. `docker compose stop redis` → readiness flips to `{"status":"DOWN"}` within ~3s. `docker compose start redis` → recovers to `UP` within a few seconds (Lettuce's lazy reconnect). `docker compose down` and the `bootRun` process were stopped afterward; no lingering containers or processes.
- Scope diff: exactly the three spec'd files touched (`build.gradle.kts`, `src/main/resources/application.yaml`, new `RedisHealthDownTest.kt`); every frozen "Never" file (`application-local.yaml`, `src/test/resources/application.yaml`, `docker-compose.yaml`, `.env`, the three existing test classes) left untouched.
- **Post-review patch (2026-09-11):** `RedisHealthDownTest`'s `contributor.health()!!.status` force-unwrap replaced with `assertThat(health).isNotNull()` then `assertThat(health!!.status)` — a null `health()` now fails with a descriptive AssertJ message instead of a bare NPE. Re-verified: `./gradlew clean build` green (Docker-free, nothing on 6379/5432), all 4 test classes pass, `spotlessCheck` clean, redis deps still BOM-resolved, `libs.versions.toml` still untouched. Two other patch-routed findings (stale `deferred-work.md` entry, `sprint-status.yaml` placeholder timestamp) were fixed directly rather than through the implementation subagent, since both are tracking-doc edits with no test/compile impact.
- Not committed — per standing instruction, changes are only committed when explicitly asked.

## Spec Change Log

## Review Triage Log

Iteration 0 — three layers (blind-hunter, edge-case-hunter, verification-gap). No intent_gap or bad_spec; no loopback. Outcome: 3 patch, 1 defer, 5 reject/false.

**Patch:**

- **#1 — Stale `deferred-work.md` "Story 1.6 heads-up" entry never closed out** (blind-hunter). `low`. The 2026-09-10 heads-up about needing a test-scope Redis exclude is resolved by this story (verified: no exclude needed) but the entry was left open, misleading a future reader. Fix: annotate or remove it.
- **#2 — `RedisHealthDownTest` uses `!!` force-unwrap instead of an AssertJ null-check** (blind-hunter). `low`. Inconsistent with the file's AssertJ-based style; a null `health()` would fail with a bare NPE instead of a descriptive assertion failure. Fix: `assertThat(contributor.health()).isNotNull()` before reading `.status`, or equivalent.
- **#3 — `sprint-status.yaml`'s `last_updated` is a round placeholder (`09-11-2026 00:00`), not real wall-clock time** (blind-hunter). `low`. Cosmetic tracking inaccuracy. Fix: write the actual current timestamp.

**Defer** (see `deferred-work.md`):

- **No automated test exercises `/actuator/health/readiness` end-to-end (UP with both deps reachable, DOWN when Redis stops)** (blind-hunter, verification-gap [pre-verified]). Real gap — a regression in `management.endpoint.health.group.readiness.include` would ship undetected by `./gradlew build`. Frozen intent explicitly excludes live-Redis/Postgres tests here (mirrors Story 1.5); Story 2.2's `@ServiceConnection` Testcontainers base is the intended home → defer.

**Rejected:**

- **`show-details` defaults to `never`, so `/actuator/health/readiness` never shows a `db`/`redis` breakdown in the body, appearing to contradict the AC/I-O-matrix wording** (blind-hunter). `low` / fix-is-a-spec-edit. The epics.md AC only requires the *aggregate* readiness status to reflect both contributors — verified working (manual DOWN/UP flip). My own spec's I/O-matrix phrasing overclaimed visible per-component detail the epics.md intent never required; correcting that is a spec-wording fix, excluded from patch/defer by rule.
- **No explicit Lettuce connection/command timeout — a silently-dropped-packet network failure could hang rather than fail fast** (blind-hunter, edge-case-hunter). `low`. Real but narrow: this repo's target environments (local Compose, self-hosted k8s intra-cluster) fail fast (connection-refused/no-route), not silent-drop. Fix requires picking new timeout config — more than a direct correction, and unlikely to bite in the environments this story targets.
- **`SPRING_DATA_REDIS_PORT` set to a non-numeric value throws a raw Spring binder conversion error instead of a custom message** (edge-case-hunter). `false`. This *is* the intended fail-loud behavior (AD-15) — Spring's binder error already names the offending property and value, the same class of "clear enough" failure Story 1.5 established as acceptable for the datasource.
- **`RedisHealthDownTest` could stall if the connection to `127.0.0.1:1` isn't refused immediately** (edge-case-hunter). `false`. The target is loopback, not a network path — an unbound loopback port is always answered synchronously by the local kernel with `RST`; there's no firewall or route in between that could silently drop the packet.
- **Spec's Approach sentence ("widen the readiness health group to require the existing `db` contributor...") implies Story 1.5 already gated readiness on `db`** (edge-case-hunter). `false`/fix-is-a-spec-edit. Verified against Story 1.5's `application.yaml` at the baseline commit — it has no `management`/health-group config at all; this story creates the readiness group from scratch with both `db` and `redis`, not just "widening" it. Inaccurate prose, not a code defect.

## Design Notes

- **`application.yaml` addition:**
  ```yaml
  spring:
    data:
      redis:
        host: ${SPRING_DATA_REDIS_HOST:localhost}
        port: ${SPRING_DATA_REDIS_PORT:6379}
        password: ${SPRING_DATA_REDIS_PASSWORD:}
  management:
    endpoint:
      health:
        group:
          readiness:
            include: readinessState,db,redis
  ```
  `readinessState` stays in the include list — dropping it would lose Boot's own "not accepting traffic yet" startup signal.
- **Proven during planning (no Docker):** an `ApplicationContextRunner` with `spring.data.redis.port=1` (nothing listening) came back `redisHealthContributor.health().status == DOWN`, context started fine (Lettuce is lazy) — confirms both the DOWN mechanism and safety alongside the Docker-free ITs.

## Verification

**Commands:**
- `./gradlew build` -- expected: `BUILD SUCCESSFUL`; all tests green, no Postgres or Redis running.
- `./gradlew dependencies --configuration runtimeClasspath | grep -iE 'redis|lettuce'` -- expected: `spring-boot-starter-data-redis`, `spring-boot-data-redis`, `spring-data-redis`, `lettuce-core`, BOM-supplied versions; no `libs.versions.toml` entry added.
- `./gradlew spotlessCheck` -- expected: clean.

**Manual checks:**
- `docker compose up -d` then `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`: `curl -s localhost:8080/actuator/health/readiness` → `UP` with `db` and `redis` components. `docker compose stop redis`, repeat → `DOWN`. Restart Redis to confirm recovery. Ctrl-C to stop.
