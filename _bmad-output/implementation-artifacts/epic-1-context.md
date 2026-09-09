# Epic 1 Context: A cloned service that builds and runs locally

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic delivers the walking skeleton every later epic builds on. From a fresh `git clone` with only a JDK and Docker installed, `docker compose up` followed by `./gradlew bootRun` must yield a running Spring Boot service that compiles under strict Kotlin, passes format/lint checks in the build, connects to Postgres and Redis, runs Flyway on startup, and answers Kubernetes-style liveness and readiness probes — with every deployment-varying setting read from the environment. There is no domain code yet (no `widgets`, no REST resource, no error model); those arrive in Epic 2. The focus here is a reproducible build, the fixed package layout, externalized configuration, the local dependency stack, and health/readiness wiring.

## Stories

- Story 1.1: Gradle build skeleton with a version catalog
- Story 1.2: Format and lint enforced in the build
- Story 1.3: Concern-package layout and a bootable application
- Story 1.4: Compose Stack for local dependencies
- Story 1.5: Postgres datasource and Flyway, configured from the environment
- Story 1.6: Redis connection and readiness gating
- Story 1.7: Run the service locally on the `local` profile
- Story 1.8: Document the local development loop

## Requirements & Constraints

- **Reproducible build from a bare clone.** `./gradlew build` must compile, run tests, and produce a runnable Spring Boot artifact using only the committed Gradle wrapper — no local Gradle install. The JVM is fixed by a Gradle toolchain, not the developer's default JDK. No dynamic dependency versions (`+`, `latest.release`) anywhere.
- **Single version catalog.** Every third-party dependency version has exactly one edit point in `gradle/libs.versions.toml`. Only versions the Spring Boot BOM does not manage are pinned there (Kotlin, Gradle/Kotlin plugins, springdoc, Spotless/ktlint); BOM-managed libs (Flyway, Testcontainers, Postgres driver, Micrometer, Lettuce, Spring Data Redis, Logback) carry no version literal.
- **Kotlin-only, strict.** `src/main` and `src/test` contain `.kt` files only. Compiler runs `-Xjsr305=strict`; `allWarningsAsErrors = true` for this module — any warning fails the build.
- **Format/lint gate the build.** One task auto-formats; a check task reports without rewriting and exits non-zero on violations; `./gradlew check` (and CI) fails on unformatted or lint-violating code. No secondary static-analysis tool (e.g. detekt) in v1 — adding one later must be a config-only change.
- **Fixed package layout.** Root package `com.hl.service`, invariant, never renamed. Seven concern packages each holding every resource's classes for that concern: `controller`, `service`, `repository`, `model`, `dto`, `error`, `config`. This is a naming convention documented in the README, not a boundary the build enforces — no ArchUnit or dependency-direction test in v1.
- **Bootable application.** A single `@SpringBootApplication` entry point (`StarterApplication.kt`). With the webmvc and actuator starters on the classpath, the process starts and `GET /actuator/health/liveness` returns 200 `UP`. Liveness and readiness health groups are enabled (Boot 4 default; framework-default probe paths, no per-service reconfiguration).
- **Environment-only configuration.** 100% of deployment-varying settings come from environment variables / externalized Spring config. `application.yaml` holds defaults and env-variable placeholders only. `application-local.yaml` is the sole file carrying literal (throwaway) credentials. No other profile file carries environment-specific values. No secret material anywhere else in the repo or image. Every setting has a documented default where applicable.
- **Postgres from the environment, fail loud.** Datasource URL, username, and password come from config. Missing or wrong datasource config fails startup with a clear error — never a silent degraded mode.
- **Flyway owns the schema.** Flyway runs on startup and records history in the database (an empty migration set is valid in this epic; the first `widgets` migration lands in Epic 2). `spring.jpa.hibernate.ddl-auto = validate`; checksum validation on; auto-repair off. A missing or checksum-mismatched migration fails startup loudly. Migrations are `db/migration/V<n>__snake_case.sql`, sequential integers, immutable after merge; tables plural snake_case.
- **Redis is a hard dependency.** Connection settings (host, port, any credentials) come from config, none hard-coded. The Redis health contributor is a member of the readiness health group: readiness reports `UP` only when both datasource and Redis contributors are `UP`, `DOWN` during startup, and `DOWN` if Redis stops while running. No `CacheErrorHandler` bean is registered — Spring's default fail-fast behavior is left in place.
- **Local run loop.** With the Compose Stack up, `./gradlew bootRun` on the `local` profile starts the service, connects to the Compose Stack's Postgres and Redis, applies migrations, and answers `GET /actuator/health` with `UP`.
- **Compose Stack.** A single `docker compose up` from the repo root starts Postgres and Redis and nothing else (no OpenTelemetry collector, no other service). Postgres and Redis image tags are declared once in a root `.env` file, referenced by `docker-compose.yaml`, and the same `.env` feeds Testcontainers so local and CI versions stay in lockstep. Works on a clean machine with only Docker installed.
- **Documented local loop.** The README has a dedicated local-development section covering prerequisites, bringing up dependencies, running the service, running tests, and viewing API docs. Following it top-to-bottom on a clean machine (Docker + JDK) yields a running service and a green `./gradlew build` with no undocumented steps. Note Swagger UI is `local`-only and there is no local trace viewing in v1.
- **Portability & determinism (NFR).** Everything works on Linux and macOS with only a JDK and Docker — no other host dependencies. A given commit builds the same image contents given the same base image.

## Technical Decisions

- **Stack versions (fixed):** JDK 25 LTS (Gradle toolchain), Kotlin 2.4.0, Spring Boot 4.1.1, Gradle 9.7.1 (wrapper), springdoc-openapi 3.1.1, Spotless 8.10.2 / ktlint 1.5.0.
- **Boot 4 specifics:** the web starter is `spring-boot-starter-webmvc` (not `-web`); test starters are per-technology (`spring-boot-starter-<tech>-test`); Jackson 3 lives under group `tools.jackson`; baseline is Jakarta EE 11 / Servlet 6.1 / Spring Framework 7; liveness and readiness probes are enabled by default.
- **Single-module Gradle Kotlin DSL build.** No multi-module split.
- **Greenfield scaffold** built from scratch to the architecture's Structural Seed tree — no external starter template. Seed files for this epic: `.env`, `docker-compose.yaml`, `gradle/libs.versions.toml`, `src/main/kotlin/com/hl/service/StarterApplication.kt` + the seven concern package directories, `src/main/resources/{application.yaml,application-local.yaml,db/migration/}`. (`.github/workflows/ci.yaml` and `IntegrationTestBase.kt` are seeded but fleshed out in later epics.)
- **Layering is Spring-idiomatic convention only.** Dependencies point controller → service → repository by convention; nothing fails the build if a resource bends it. The earlier clean-architecture direction (enforced boundaries, two-annotation application layer, outbound-port interfaces, hand-orchestrated cache-aside) was retired.
- **Actuator exposure** is `health`, `info`, `prometheus` only, on the main port (full Prometheus/metrics wiring is Epic 3; this epic just needs health/readiness). Readiness group = datasource + Redis.
- **Dates convention:** `java.time` types, UTC, ISO-8601 on the wire, `timestamptz` in Postgres.
- **The Four Parameters** (service name = Gradle project name, database name + credentials, HTTP port, image name) each get one documented home; full parameterization work is Epic 5, but keep new settings consistent with this rule. Base package and concern package names are invariant.

## Cross-Story Dependencies

- Story 1.1 (build skeleton) is the foundation for every other story in the epic.
- Story 1.3 (bootable application + package layout) must precede 1.5, 1.6, and 1.7, which all need a running context.
- Story 1.4 (Compose Stack + root `.env`) must precede 1.5, 1.6, and 1.7 (the local run needs Postgres and Redis up) and is a prerequisite for the shared Testcontainers base class introduced in Epic 2 (same `.env`).
- Stories 1.5 and 1.6 together define the readiness group; 1.6's readiness assertion depends on 1.5's datasource contributor being wired.
- Story 1.7 (local `bootRun`) integrates 1.3–1.6 and depends on the `application.yaml` / `application-local.yaml` split.
- Story 1.8 (README local loop) documents the result of 1.1–1.7 and should land last.
- Downstream: Epic 2 (`widgets` slice) builds directly on this skeleton — the concern packages, Flyway wiring, `ddl-auto=validate`, and the Compose/`.env` convention are all consumed there. Epic 3 extends the actuator/observability configuration started here. Epic 4 fleshes out the CI workflow file scaffolded here.
