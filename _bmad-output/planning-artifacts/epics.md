---
stepsCompleted: ["step-01-validate-prerequisites", "step-02-design-epics", "step-03-create-stories", "step-04-final-validation"]
inputDocuments:
  - "_bmad-output/planning-artifacts/prds/prd-hl-backend-kotlin-starter-2026-09-07/prd.md"
  - "_bmad-output/planning-artifacts/prds/prd-hl-backend-kotlin-starter-2026-09-07/addendum.md"
  - "_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md"
---

# hl-backend-kotlin-starter - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for the Spring Boot Kotlin Starter Service, decomposing the requirements from the PRD and the Architecture Spine into implementable stories.

**Reconciliation note:** The Architecture Spine (updated 2026-09-09) is authoritative wherever PRD FR text conflicts with it. The spine's 2026-09-09 direction change moved the Starter from *enforced clean-architecture boundaries* to *Spring-idiomatic layers, convention only*. It retired AD-2 (ArchUnit boundary test), AD-3 (two-annotation application layer), AD-5 (outbound-port interfaces), AD-6 (hand-orchestrated cache-aside), AD-7 (transaction/cache ordering rule), and AD-12 (ErrorCode enum catalogue). Affected FRs below carry a **Reconciled** note.

There is no UX Design Specification — this is a headless backend service.

## Requirements Inventory

### Functional Requirements

**Feature 4.1 — Build and language baseline**

FR-1: A developer or agent can build, test, and run the Starter with standard Gradle tasks (`./gradlew build` compiles, runs Unit + Integration Tests, produces a runnable Spring Boot artifact), and can see every third-party dependency version in one catalog file (`gradle/libs.versions.toml`). The Gradle wrapper is committed; no local Gradle install required.

FR-2: The Starter contains no Java sources and compiles under null-safety-strict settings. `src/main` and `src/test` contain `.kt` files only; Kotlin compiler runs `-Xjsr305=strict`; warnings-as-errors is enabled for the Starter's own module. *(Settled by AD-23.)*

FR-3: A developer or agent can auto-format the codebase with one task; CI fails on unformatted or lint-violating code. Spotless 8.10.2 drives ktlint 1.5.0. A format-apply task rewrites code; a format-check task reports without rewriting; `./gradlew check` (and CI) fails when format-check fails. No secondary static-analysis tool (detekt) in v1. *(Settled by AD-22.)*

**Feature 4.2 — Clean-architecture skeleton (now: Spring-idiomatic layers, convention only)**

FR-4: The Starter defines a fixed root package `com.hl.service` and one package per concern — `controller`, `service`, `repository`, `model`, `dto`, `error`, `config` — each holding every resource's classes for that concern. The README and the Example Slice both use those paths. **Reconciled (AD-1):** this is a naming convention, not a boundary the build checks; the four-layer model in PRD FR-4 is replaced by these seven concern packages.

FR-5: **Overtaken by the architecture direction change.** PRD FR-5 required a build-failing ArchUnit Boundary Test enforcing dependency rules. **Reconciled (AD-2 retired):** there is no build-time boundary check in v1. Dependencies point the conventional Spring direction (controller → service → repository) by convention only; cross-cutting architectural calls are left to each Consumer Service. No story is generated to build a Boundary Test.

FR-6: A developer or agent can add a new REST resource by adding files that mirror the Example Slice, changing zero Plumbing files (no edit to build files, configuration, observability wiring, container config, or CI workflow). A documented README step lists exactly which files to create and in which concern package. The new resource's Integration Test extends the shared container base class and adds nothing infrastructural. *(Reconciled: "touches no Plumbing" retained; the Boundary-Test verification of it is dropped per FR-5.)*

**Feature 4.3 — Persistence: Postgres and Flyway**

FR-7: The service connects to Postgres using connection settings supplied entirely from the environment / Spring configuration, with no credentials in code or committed files except local-only defaults in `application-local.yaml`. Missing or wrong datasource config fails startup with a clear error, not a silent degraded mode. *(AD-15.)*

FR-8: Flyway applies schema changes automatically on startup and tracks migration history in the database. The Example Slice ships at least one versioned migration creating the `widgets` table. `spring.jpa.hibernate.ddl-auto = validate`; checksum validation on; auto-repair off; startup fails loudly on a missing or mismatched migration. Migrations are `db/migration/V<n>__snake_case.sql`, sequential integers, immutable after merge. *(Settled by AD-14.)*

FR-9: The Starter includes an Integration Test that runs the persistence adapter against a real Postgres started via Testcontainers: Flyway migrates it, then a write-then-read through the JPA layer. Runs in CI with no external database, no developer setup beyond Docker. Integration Tests live in the single `src/test` source set, carry `@Tag("integration")`, and extend one shared base class owning static Testcontainers Postgres + Redis (`@ServiceConnection`) started once per JVM; isolation is per-test data cleanup. *(Settled by AD-21.)*

**Feature 4.4 — Caching: Redis**

FR-10: The service connects to Redis using settings supplied from the environment / Spring configuration; none hard-coded. Redis is a **hard dependency with no fallback path**: the Redis health contributor is a member of the readiness health group, so a pod without healthy Redis never enters rotation. No `CacheErrorHandler` bean is registered — Spring's default fail-fast behavior applies; a Redis failure at request time propagates as a System Exception → 500. *(Overturned from PRD's degrade-to-Postgres by AD-13.)*

FR-11: Reading a `widget` by id is cached and writes invalidate the cached entry. **Reconciled (AD-13):** implemented with Spring's `@Cacheable` / `@CacheEvict` directly on the service method — framework-managed, no owned cache-orchestration code (PRD FR-11's hand-orchestrated check-Redis-then-Postgres is retired with AD-6). A second read within the TTL is served from cache without a Postgres query; an update or delete removes/refreshes the entry so a later read is not stale. TTL is a configuration value with a documented default.

FR-12: The Starter includes an Integration Test exercising cache hit, cache miss, and invalidation against a real Redis started via Testcontainers alongside Postgres. With Redis stopped, a read-by-id returns 500 (System Exception) and readiness reports down — no Postgres-served fallback. *(AD-13.)*

**Feature 4.5 — Example Slice: `widgets`**

FR-13: A client can create, read (by id and list), update, and delete a `widget` over REST/JSON. Endpoints under `/api/v1/widgets` (plural, lowercase): `POST` collection creates and returns the widget with its id (201 + `Location`); `GET /{id}` returns it; `GET` collection lists paged; `PUT /{id}` is a full update; `DELETE /{id}` returns 204. Collection `GET` uses offset pagination (`?page=` 0-based, `?size=` with a documented default) and returns a fixed envelope `{ items, page, size, totalElements, totalPages }`. **Reconciled (AD-10):** pagination uses Spring Data's `Pageable`/`Page` directly end to end; the envelope is built once in `dto/` (PRD's Starter-owned `PageRequest`/`PageResult` types are not introduced). Invalid input returns 400; a `GET` for a missing id returns 404, both with the standard error body. *(AD-9.)*

FR-14: The `widgets` feature demonstrates a request flowing controller → service → repository (Postgres + Redis) and back, with each concern's responsibility separated. The controller contains no persistence or cache calls; the domain type (`Widget` in `model/`) is never serialized and never appears in a controller signature — DTOs (`WidgetRequest` / `WidgetResponse` in `dto/`) do. The `@Entity` (`WidgetEntity` in `repository/`) is a distinct type from the domain model, with explicit hand-written mapping and no mapping framework. *(Reconciled to AD-1, AD-4, AD-9; the Boundary-Test assertion is dropped.)*

FR-15: The Example Slice ships Unit Tests for its domain/service logic (no container, no Spring context, hand-written in-memory fakes for the repository) and Integration Tests driving a real HTTP request through to Postgres and Redis and back, asserting status, body, persisted row, and cache entry. Deleting the entire Example Slice (code + tests + its migration) leaves `./gradlew build` green — verified by a documented "remove the example" step. *(AD-21.)*

**Feature 4.6 — REST API conventions**

FR-16: All 4xx and 5xx responses share one schema: RFC 7807 `application/problem+json`. Validation failures (400), not-found (404), and unhandled exceptions (500) all produce it. Exactly one component produces error bodies — the Global Exception Handler; no controller, service, or filter builds its own. The schema is documented in the README and exercised by at least one test. **Reconciled (AD-11):** 500 bodies **do** carry the exception's own `message` and `details` (explicit product decision overturning PRD's "500 never leaks internal detail"); calling code must not put secrets in a message or details map.

FR-17: Inbound request DTOs are validated declaratively (Bean Validation annotations); a violating request returns 400 listing the offending fields. Validation failures reach the client through the Global Exception Handler as an `errors` array of `{field, code, message}` in the same Problem Detail schema — not a second error shape. The Example Slice demonstrates at least one constrained field.

FR-18: The running service exposes an OpenAPI JSON description of its REST surface in **every** profile, reflecting the `widgets` endpoints and any resource added per FR-6 with no extra wiring. Swagger UI is served **only under the `local` profile** (`springdoc.swagger-ui.enabled`). When the Auth Seam is enabled, `/v3/api-docs` requires a valid token; when disabled (v1 default) it is open. springdoc-openapi `springdoc-openapi-starter-webmvc-ui` 3.1.1. *(OQ-3, AD-18.)*

FR-39: Every error the service raises deliberately is exactly one of three fixed types in `error/` — `BusinessException` (400), `NotFoundException` (404), `SystemException` (500) — rooted on a sealed base (`AppException`) carrying `message: String` and `details: Map<String, Any?> = emptyMap()`. **Reconciled (AD-11; AD-12 retired):** there is no per-business-rule `ErrorCode` catalogue and no per-instance code parameter. HTTP status is fixed by type, not chosen per instance; no dedicated 409 in v1. The exception's `message` and `details` are exactly what the client receives, for all three types including `SystemException`. The Example Slice throws at least one of each type, all covered by tests.

FR-40: A single `@RestControllerAdvice` (`GlobalExceptionHandler` in `controller/`) translates every exception — Business, Not-Found, System, framework, or unanticipated — into a Problem Detail body carrying a fixed type-level `code` (`BUSINESS_ERROR` / `NOT_FOUND` / `SYSTEM_ERROR` / `UNEXPECTED_ERROR`) assigned by the handler. `type` is `about:blank`; `instance` is the request path; `detail` is the exception's own message; extension members are `code`, `traceId`, `details` (omitted when empty), and `errors` (validation only). HTTP status is the primary client-branching signal (types map 1:1 to 400/404/500). Every error response carries `Content-Type: application/problem+json`. 500 responses include the `SystemException`'s message and details and the exception is still logged in full at error level with the trace id. Tests cover five paths: Business, Not-Found, System, Bean Validation failure, and an unanticipated exception — each asserting status, `Content-Type`, and code. *(Settled by AD-11.)*

**Feature 4.7 — Observability and runtime behavior**

FR-19: The service exposes health with distinct liveness and readiness groups suitable for Kubernetes probes. Readiness includes **datasource and Redis** availability. During startup readiness reports down; after startup, up. Probe paths are the framework defaults (liveness/readiness probes enabled by default in Boot 4) and require no per-service reconfiguration. *(AD-17.)*

FR-20: The service exposes Micrometer metrics in Prometheus format at a scrapeable endpoint including JVM, HTTP server, datasource, and cache metrics. No additional wiring needed for a Prometheus server to scrape it. Actuator exposes `health`, `info`, `prometheus` only, on the main port. *(AD-17.)*

FR-21: The service exports distributed traces over OTLP (Micrometer Tracing over the OTel bridge) to an endpoint taken from configuration, and runs cleanly with tracing effectively disabled when no endpoint is set — the exporter is a no-op when unset (not a retry against localhost), and it is unset by default, so tracing is a no-op locally and in CI. `management.tracing.sampling.probability` is a configuration value with a documented default. *(Settled by AD-17.)*

FR-22: Application logs are emitted as single-line JSON to stdout using Spring Boot's built-in structured logging (`logging.structured.format.console`) — no encoder dependency, no logback XML — including timestamp, level, logger, message, and within a request the trace id and span id. The `local` profile keeps human-readable console output. No secondary log sink or file appender by default. *(Settled by AD-17.)*

FR-23: On SIGTERM the service stops accepting new requests, finishes in-flight requests within a bounded period, then exits. Graceful shutdown is enabled (`server.shutdown=graceful`) with an env-overridable phase timeout, documented default 30 s (`spring.lifecycle.timeout-per-shutdown-phase`). *(Settled by AD-17.)*

FR-24: Every deployment-varying setting is read from environment variables / externalized Spring configuration. `application.yaml` holds defaults and env-variable placeholders only; `application-local.yaml` is the sole file with literal (throwaway) credentials. The same image runs in any environment given different environment variables; no secret material anywhere else in the repo or image. *(AD-15.)*

**Feature 4.8 — Local development**

FR-25: A single `docker compose up` from the repo root starts Postgres and Redis — and no other service — with image tags declared once in a root `.env` file that Testcontainers also reads, so CI and local runs use identical versions. No local OpenTelemetry collector. Data directories and ports work on a clean machine with only Docker installed. *(Overturned from the PRD's bundled collector by AD-17; `.env` convention.)*

FR-26: With the Compose Stack up, `./gradlew bootRun` on the `local` profile starts the service, connects to the Compose Stack's Postgres and Redis, applies migrations, and answers its health endpoint. `application-local.yaml` holds the only local-only defaults. *(AD-15.)*

FR-27: The README documents the full local loop: prerequisites, bring up dependencies, run the service, run tests, and view API docs (Swagger UI under `local`). Following it on a clean machine (Docker + JDK) yields a running service and a green `./gradlew build` with no undocumented steps. No local trace viewing in v1.

**Feature 4.9 — Container image and CI pipeline**

FR-28: `./gradlew bootBuildImage` produces a runnable OCI image with no Dockerfile in the repo. Builder is `paketobuildpacks/builder-noble-java-tiny` pinned to an explicit tag. `BP_JVM_CDS_ENABLED` and the AOT cache stay off in v1 (open Paketo defect on Java 25 + Boot 4). The image runs as a non-root user, starts from environment configuration alone, and answers its health endpoints. *(Settled by AD-19.)*

FR-29: A GitHub Actions workflow runs on every pull request and executes `./gradlew build` — format-check, Unit Tests, and Integration Tests — on `ubuntu-latest` using Testcontainers with no external database/cache and no service containers declared in the workflow. A failing check blocks merge. *(Settled by AD-20.)*

FR-30: On push to `main`, after a green build, the workflow builds and publishes the image to a **private** `ghcr.io` package, tagged with the git short SHA, with `latest` moved to that image (no semver tags in v1). Publication authenticates with the workflow's built-in `GITHUB_TOKEN` under `permissions: packages: write` — no stored registry credential. *(Settled by AD-20.)*

FR-31: The pipeline caches Gradle dependencies and build outputs so routine runs stay short; a no-op-change PR pipeline completes within the SM-C1 budget (~10 min). *(AD-20.)*

**Feature 4.10 — Kubernetes runtime contract**

FR-32: The published image runs under Kubernetes without probe reconfiguration or image changes: liveness/readiness probes at the framework-default paths succeed against the running image with no overrides; the container runs as non-root and reads all configuration from environment variables; the process exits cleanly within the graceful-shutdown timeout on SIGTERM. Verified by a documented local check (`docker run` + curl probes + SIGTERM timing). *(AD-17, AD-19.)*

**Feature 4.11 — Parameterization**

FR-33: Service name (Gradle project name), database name + credentials, HTTP port, and image name each have a single documented home. The README names the exact file(s) and key(s) for each. No parameter is duplicated such that changing one place leaves an inconsistency; where a value must appear twice it is derived from one source or the README flags both. The root `.env` holds only the Postgres/Redis container image tags and is Plumbing, not a home for any of the Four Parameters. The base package `com.hl.service` and concern package names are invariant. *(AD-16.)*

FR-34: After changing only the Four Parameters per the README, the service runs locally, passes CI, and publishes an image — with no source code change required to reach that state. Verified by a walkthrough producing a green `./gradlew build`, a successful local run against the Compose Stack, and (on `main`) a published image. *(AD-16.)*

FR-35: v1 ships no rename/generator automation. The README's parameter checklist is complete enough to be the spec for a future generator; no half-built generator script ships.

**Feature 4.12 — Auth Seam**

FR-36: The repository contains an OAuth2 resource-server (JWT) security configuration (`config/SecurityConfig.kt`) that is not active in the default profile. A single `app.auth.enabled` property (default `false`) selects one of two `SecurityFilterChain` beans. Disabled installs an **explicit** permit-all chain (so `spring-security` on the classpath does not silently secure everything). Enabled requires a valid JWT on `/api/**` and `/v3/api-docs`, leaves `/actuator/health**` open, and requires a token for every other actuator endpoint, using issuer / audience / JWKS values from configuration. Enabled without a resolvable `issuer-uri` fails context startup — it can never degrade to allow-all. The inactive configuration compiles and is covered by at least a smoke test in its enabled mode. *(Settled by AD-18.)*

FR-37: The README explains how to turn the Auth Seam on: the exact configuration keys for issuer, audience, and JWKS URI; the v1 posture (seam only, nothing enforced); the intended Auth0 direction; how Integration Tests authenticate; and how Actuator endpoints are treated (`health`, `info`, `prometheus` exposed; `/actuator/health**` open under the seam, other actuator endpoints require a token). *(AD-17, AD-18.)*

**Feature 4.13 — Documentation**

FR-38: The README documents, each in a dedicated findable section: the Four Parameters and where each lives; how to add a REST resource (concrete enough to produce a working resource with passing tests, ties to FR-6); the local development loop; how to enable the Auth Seam; and the observability endpoints. It states what is deliberately out of v1 (generator, charts, messaging, auth enforcement, Vault) with a pointer to the brief/addendum.

### NonFunctional Requirements

NFR-1 (Hermetic tests): No test — Unit or Integration — may require infrastructure the repo does not start itself. Integration Tests use Testcontainers; nothing reaches a shared or external database, cache, or network service. The full suite passes in CI with zero external infrastructure and no flakiness over 20 consecutive runs. *(SM-3.)*

NFR-2 (Startup time): The service reaches readiness in under ~10 s on a typical CI/runtime container, measured via the documented local check (FR-32). *(§10, AD-17.)*

NFR-3 (Configuration): 100% of deployment-varying settings come from environment / externalized config. No secret material in the image or the repo, excepting throwaway `application-local.yaml` credentials. Hard constraint — it is what keeps the Vault roadmap cheap.

NFR-4 (Determinism / reproducibility): A given commit builds the same image contents given the same base image. Dependency versions are locked via `gradle/libs.versions.toml`, with no dynamic (`+`, `latest.release`) versions anywhere; the JVM is fixed by a Gradle toolchain, not the developer's local JDK. *(AD-22.)*

NFR-5 (Resource footprint): v1 fixes no numeric memory/CPU ceiling. Heap sizing is left to the buildpack's container-aware calculator; the README documents the knob (`JAVA_TOOL_OPTIONS` / `BPL_JVM_*`). Container resource limits are set by the charts repo, not the image. *(AD-17.)*

NFR-6 (Agent legibility): Structure, naming, and the Example Slice are consistent enough that an AI coding agent can place new code and tests correctly from the conventions alone. *(SM-2, UJ-3.)*

NFR-7 (Portability): Everything (build, tests, image, local loop) works on Linux and macOS with only a JDK and Docker installed; no other host dependencies.

NFR-8 (Observability parity): Metrics, traces, and logs behave identically across `local`, CI, and production save for endpoint configuration and log formatting — no observability code path is production-only and therefore untested.

NFR-9 (Starter CI budget — counter-metric SM-C1): The Starter's own pipeline stays under ~10 minutes for a routine change. Do not chase hermeticity/coverage by letting the pipeline sprawl.

NFR-10 (Dependency footprint — counter-metric SM-C2): Keep the direct dependency count lean and every dependency justified. Do not pre-add libraries a given Consumer Service may not want.

NFR-11 (Parameterization spread — counter-metric SM-C3): Keep the number of files a person edits for the Four Parameters in single digits.

NFR-12 (Security constraints): Non-root container. No secrets in repo or image. The Auth Seam fails safe (enabled + unresolvable `issuer-uri` → context startup failure; disabled → explicit permit-all chain). *(§11, AD-18.)*

NFR-13 (Operational contract): The image is the contract — probe paths, SIGTERM behavior, and env configuration are fixed points the charts repo depends on. Changing any is a breaking change to every Consumer Service.

NFR-14 (Deployment boundary): The Starter delivers an image + CI. Cluster wiring (manifests, values, secrets, ingress) is the separate charts repository's responsibility and out of scope here.

### Additional Requirements

Technical requirements from the Architecture Spine that shape stories:

- **Greenfield scaffold, no external starter template.** The repo is built from scratch to the spine's Structural Seed tree (`.env`, `docker-compose.yaml`, `gradle/libs.versions.toml`, `.github/workflows/ci.yaml`, `src/main/kotlin/com/hl/service/{controller,service,repository,model,dto,error,config}`, `src/main/resources/{application.yaml,application-local.yaml,db/migration}`, `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt`). This is Epic 1's foundational work.
- **Fixed stack versions (AD-22, Stack table):** JDK 25 LTS (Gradle toolchain), Kotlin 2.4.0, Spring Boot 4.1.1, Gradle 9.7.1 (wrapper), springdoc-openapi 3.1.1, Spotless 8.10.2 / ktlint 1.5.0, buildpack builder `paketobuildpacks/builder-noble-java-tiny` at an explicit tag. Flyway, Testcontainers, Postgres driver, Micrometer, Lettuce, Spring Data Redis, Logback inherited from the Spring Boot BOM without version literals.
- **Boot 4 specifics (Stack notes):** web starter is `spring-boot-starter-webmvc`; test starters are per-technology (`spring-boot-starter-<tech>-test`); Jackson 3 lives under group `tools.jackson`; baseline is Jakarta EE 11 / Servlet 6.1 / Spring Framework 7; liveness and readiness probes enabled by default.
- **Root package `com.hl.service`, seven concern packages (AD-1):** `controller`, `service`, `repository`, `model`, `dto`, `error`, `config`. Naming convention, not a build-checked boundary. No per-resource subpackage requirement.
- **Domain model vs JPA entity are distinct types (AD-4):** immutable Kotlin class in `model/`; `@Entity` + `JpaRepository` in `repository/`; explicit hand-written mapping on the entity; no mapping framework added.
- **Application-generated UUID v4 identifiers (AD-8):** plain `java.util.UUID` (`UUID.randomUUID()`), generated in the domain before persistence, on both the domain class and the `@Entity`; Postgres column type `uuid`; the database never generates identifiers.
- **REST URL/verb convention (AD-9):** `/api/v1/{resource}` plural lowercase; `POST` collection → 201 + `Location`; `GET /{id}`; `GET` collection paged; `PUT /{id}` full update; `DELETE /{id}` → 204. DTOs `<Resource>Request` / `<Resource>Response` in `dto/`; the domain type is never serialized and never in a controller signature.
- **One pagination contract (AD-10):** offset paging via Spring Data `Pageable`/`Page` used directly end to end (no hand-rolled pagination types); response envelope `{items, page, size, totalElements, totalPages}` built once in `dto/` (`PageResponse.kt`) and reused.
- **One error body, one producer, three exception types, no catalogue (AD-11):** RFC 7807 `application/problem+json` built by the single `@RestControllerAdvice` (`GlobalExceptionHandler` in `controller/`); `error/` holds a sealed base `AppException` plus `BusinessException` (400) / `NotFoundException` (404) / `SystemException` (500), each with one constructor shape `message: String, details: Map<String, Any?> = emptyMap()`; `code` is a fixed literal per type assigned by the matching `@ExceptionHandler`; `type` = `about:blank`; `instance` = request path; `errors` array only for Bean Validation; message + details echoed to the client for all three types including 500; every exception also logged server-side with the trace id.
- **Redis fail-fast (AD-13):** no `CacheErrorHandler` bean; Spring's default `@Cacheable`/`@CacheEvict` failure propagation to 500; Redis health contributor in the readiness health group.
- **Flyway is sole schema authority (AD-14):** `spring.jpa.hibernate.ddl-auto = validate`; migrations `db/migration/V<n>__snake_case.sql`, sequential integers, immutable after merge; checksum validation on, auto-repair off; tables plural snake_case.
- **Environment-only configuration (AD-15):** `application.yaml` = defaults + env placeholders only; `application-local.yaml` = the sole file with literal throwaway credentials; same image runs anywhere given different env vars.
- **Four Parameters, and nothing else (AD-16):** service name (Gradle project name), database name + credentials, HTTP port, image name — each one documented home; base package and concern package names invariant.
- **Observability is configuration, never code (AD-17):** built-in structured JSON logging (`logging.structured.format.console`), `local` keeps console output; actuator exposes `health`, `info`, `prometheus` only, on the main port; readiness group = datasource + Redis; Micrometer Tracing over the OTel bridge, OTLP endpoint unset by default (no-op), sampling probability configurable; graceful shutdown on, 30 s phase timeout; heap left to the buildpack calculator.
- **Auth Seam is one property, fails safe (AD-18):** `app.auth.enabled` (default `false`) → one of two `SecurityFilterChain` beans; disabled = explicit permit-all; enabled requires JWT on `/api/**` and `/v3/api-docs`, leaves `/actuator/health**` open, token for every other actuator endpoint; enabled without resolvable `issuer-uri` fails context startup.
- **Buildpack image from a pinned builder (AD-19):** `bootBuildImage` only, no Dockerfile; builder pinned to an explicit tag; CDS/AOT off in v1; image runs non-root, env-configured, framework-default probe paths, clean SIGTERM.
- **CI shape and registry (AD-20):** GitHub Actions on `ubuntu-latest`; every PR runs `./gradlew build` with Testcontainers and no workflow service containers, failure blocks merge; only `main` builds and pushes the image to a private `ghcr.io` package tagged git short SHA + `latest`, auth via built-in `GITHUB_TOKEN` + `permissions: packages: write`; Gradle dependency and build caches restored between runs.
- **One test source set, shared container base class (AD-21):** single `src/test`; Integration Tests `@Tag("integration")`; one abstract base class owns static Postgres + Redis (`@ServiceConnection`) started once per JVM and shared; isolation = per-test data cleanup; Unit Tests use hand-written in-memory fakes, no mocking framework dependency.
- **Kotlin compiler strictness (AD-23):** `src/main` and `src/test` Kotlin-only, no `.java`; `-Xjsr305=strict`; `allWarningsAsErrors = true` for the Starter's own module.
- **Dates convention:** `java.time` types; UTC; ISO-8601 on the wire; `timestamptz` in Postgres.
- **`.env` convention:** Postgres and Redis image tags declared once in the root `.env`, consumed by both Compose and Testcontainers.

### UX Design Requirements

None — this is a headless backend service with no UI. No UX Design Specification exists.

### FR Coverage Map

| FR | Epic | Coverage |
| --- | --- | --- |
| FR-1 | 1 | Gradle Kotlin DSL build + version catalog, wrapper committed |
| FR-2 | 1 | Kotlin-only sources, `-Xjsr305=strict`, warnings-as-errors |
| FR-3 | 1 | Spotless/ktlint format-apply + format-check gating `check` |
| FR-4 | 1 | Root package `com.hl.service`, seven concern packages (convention only) |
| FR-5 | — | **Retired** — no build-time Boundary Test in v1 (AD-2 retired); no story generated |
| FR-6 | 2 | "Add a resource" README step; zero-Plumbing verification |
| FR-7 | 1 | Postgres datasource from env, fail-fast on misconfiguration |
| FR-8 | 1 | Flyway on startup, `ddl-auto=validate`, checksum on / auto-repair off (widgets migration lands in Epic 2) |
| FR-9 | 2 | Persistence Integration Test via Testcontainers |
| FR-10 | 1 | Redis connection from env; health contributor in readiness group; no `CacheErrorHandler` bean |
| FR-11 | 2 | `@Cacheable`/`@CacheEvict` read-through + invalidation on `widgets` |
| FR-12 | 2 | Cache hit/miss/invalidation Integration Test; Redis-down → 500 + readiness down |
| FR-13 | 2 | `/api/v1/widgets` CRUD, verbs/status codes, pagination envelope |
| FR-14 | 2 | End-to-end layer separation; domain never serialized; distinct entity type; hand-written mapping |
| FR-15 | 2 | Unit + Integration tests at both levels; removable Example Slice (documented step) |
| FR-16 | 2 | RFC 7807 `application/problem+json`, single producer, 500 echoes message/details |
| FR-17 | 2 | Bean Validation → 400 with `errors` array in the same schema |
| FR-18 | 2 | OpenAPI JSON every profile; Swagger UI `local` only; `/v3/api-docs` auth-gated when seam on |
| FR-19 | 1 | Liveness/readiness groups; readiness = datasource + Redis; framework-default probe paths |
| FR-20 | 3 | Micrometer Prometheus endpoint (JVM/HTTP/datasource/cache metrics) |
| FR-21 | 3 | OTLP exporter, no-op when endpoint unset; sampling probability configurable |
| FR-22 | 3 | Built-in structured JSON logging with trace/span ids; `local` keeps console output |
| FR-23 | 3 | `server.shutdown=graceful`, 30 s phase timeout, env-overridable |
| FR-24 | 1 | All deployment-varying settings from env; `application-local.yaml` sole literal-credentials file |
| FR-25 | 1 | `docker compose up` → Postgres + Redis only; image tags in root `.env` shared with Testcontainers |
| FR-26 | 1 | `./gradlew bootRun` on `local` connects, migrates, answers health |
| FR-27 | 1 | README local development loop section |
| FR-28 | 4 | `bootBuildImage`, no Dockerfile, pinned builder, non-root, CDS/AOT off |
| FR-29 | 4 | GitHub Actions PR build: format-check + Unit + Integration via Testcontainers, blocks merge |
| FR-30 | 4 | `main` publishes to private `ghcr.io`, SHA + `latest`, built-in `GITHUB_TOKEN` |
| FR-31 | 4 | Gradle dependency + build caches restored; PR pipeline within ~10 min (SM-C1) |
| FR-32 | 4 | Image meets K8s runtime contract; documented local `docker run` + probe + SIGTERM check |
| FR-33 | 5 | Four Parameters, each one documented home; `.env` is Plumbing, not a parameter home |
| FR-34 | 5 | Changing only the four params → green build, local run, published image; no source change |
| FR-35 | 5 | No generator ships; checklist complete enough to be its future spec |
| FR-36 | 5 | Inactive OAuth2 resource-server config; `app.auth.enabled` two-chain fail-safe; enabled-mode smoke test |
| FR-37 | 5 | README Auth Seam section: config keys, v1 posture, test auth, actuator treatment |
| FR-38 | 5 | README covers all five essential tasks + the out-of-v1 list |

All 40 PRD FRs are accounted for; FR-5 is explicitly retired by the architecture direction change and produces no story. NFRs are addressed within epics: NFR-3/NFR-4/NFR-7 (config, determinism, portability) in Epic 1; NFR-1/NFR-6 (hermetic tests, agent legibility) in Epic 2; NFR-2/NFR-8 (startup time, observability parity) in Epic 3; NFR-5/NFR-9/NFR-12 (resource footprint, CI budget, non-root security) in Epic 4; NFR-11/NFR-12 (parameter spread, Auth Seam fail-safe) in Epic 5. NFR-10/NFR-13/NFR-14 are standing guardrails across all epics.

## Epic List

### Epic 1: A cloned service that builds and runs locally

From `git clone`, running `docker compose up` and `./gradlew bootRun` yields a Spring Boot service that compiles under strict Kotlin, passes format/lint, connects to Postgres and Redis, runs Flyway on startup, and answers Kubernetes-style liveness/readiness probes — with every setting read from the environment. No domain code yet; this is the walking skeleton every later epic builds on.
**FRs covered:** FR-1, FR-2, FR-3, FR-4, FR-7, FR-8, FR-10, FR-19, FR-24, FR-25, FR-26, FR-27

### Epic 2: A working reference feature to copy — `widgets` end to end

A complete `/api/v1/widgets` CRUD resource exercising REST → service → Postgres → Redis: distinct domain and JPA entity types, application-generated UUIDs, `@Cacheable`/`@CacheEvict` caching, the offset-pagination envelope, the RFC 7807 error contract with one `GlobalExceptionHandler` and three exception types, request validation, served OpenAPI plus `local`-only Swagger UI, and Unit + Integration tests at both levels. Documented as the pattern to copy, and removable without breaking the build.
**FRs covered:** FR-6, FR-9, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-39, FR-40

### Epic 3: A service that's observable and well-behaved in production

Prometheus metrics, OTLP tracing (a no-op when unconfigured), structured JSON logs correlated by trace id, and graceful shutdown — all driven by configuration and identical across local, CI, and production, with no observability code path that only runs in production.
**FRs covered:** FR-20, FR-21, FR-22, FR-23

### Epic 4: A published image and a green CI pipeline

`./gradlew bootBuildImage` produces a non-root OCI image that meets the Kubernetes runtime contract (framework-default probe paths, SIGTERM, env configuration), verified by a documented local check. GitHub Actions builds and fully tests every pull request with Testcontainers and no workflow service containers; a push to `main` publishes the image to a private `ghcr.io` package tagged with the git short SHA plus `latest`, with Gradle dependency and build caches restored between runs.
**FRs covered:** FR-28, FR-29, FR-30, FR-31, FR-32

### Epic 5: Clone-to-service in under 15 minutes, roadmap protected

The Four Parameters each get one documented home and a README checklist, so changing only them yields a running, CI-green, image-publishing service with no source edit. The inactive, fail-safe Auth Seam (`app.auth.enabled` selecting one of two `SecurityFilterChain` beans) is in place so Auth0 drops in later without structural change. The README covers all five essential tasks and states what is deliberately out of v1.
**FRs covered:** FR-33, FR-34, FR-35, FR-36, FR-37, FR-38

## Epic 1: A cloned service that builds and runs locally

From `git clone`, running `docker compose up` and `./gradlew bootRun` yields a Spring Boot service that compiles under strict Kotlin, passes format/lint, connects to Postgres and Redis, runs Flyway on startup, and answers Kubernetes-style liveness/readiness probes — with every setting read from the environment. No domain code yet; this is the walking skeleton every later epic builds on. **FRs:** FR-1, FR-2, FR-3, FR-4, FR-7, FR-8, FR-10, FR-19, FR-24, FR-25, FR-26, FR-27. **NFRs:** NFR-3, NFR-4, NFR-7. **Architecture:** AD-1, AD-14, AD-15, AD-17, AD-22, AD-23.

### Story 1.1: Gradle build skeleton with a version catalog

As the Starter maintainer,
I want a single-module Gradle Kotlin DSL build with pinned versions in one catalog and a committed wrapper,
So that a clone builds reproducibly with no local Gradle install and every dependency version has one edit point.

**Acceptance Criteria:**

**Given** a fresh clone with only a JDK and Docker installed
**When** I run `./gradlew build`
**Then** the build succeeds using the committed wrapper (Gradle 9.7.1) with no local Gradle install
**And** the JVM is fixed by a Gradle toolchain (JDK 25), not the developer's default JDK.

**Given** the build configuration
**When** I inspect `gradle/libs.versions.toml`
**Then** it pins Kotlin 2.4.0, the Gradle/Kotlin plugins, springdoc-openapi 3.1.1, and Spotless 8.10.2 / ktlint 1.5.0
**And** Flyway, Testcontainers, the Postgres driver, Micrometer, Lettuce, Spring Data Redis and Logback carry no version literal and are inherited from the Spring Boot 4.1.1 BOM
**And** no build file contains a version literal for a managed dependency, and no dynamic version (`+`, `latest.release`) appears anywhere.

**Given** the Kotlin compilation settings
**When** the build compiles `src/main` and `src/test`
**Then** only `.kt` sources are present (no `.java`)
**And** the compiler runs with `-Xjsr305=strict`
**And** `allWarningsAsErrors = true` is set for the Starter's own module, failing the build on any warning.

**Given** `./gradlew build`
**When** it completes
**Then** it produces a runnable Spring Boot artifact.

### Story 1.2: Format and lint enforced in the build

As a developer or coding agent,
I want one task to auto-format the code and a check that fails CI on violations,
So that style never becomes a review topic and miswired formatting is caught mechanically.

**Acceptance Criteria:**

**Given** unformatted or lint-violating Kotlin
**When** I run the format-apply task (Spotless/ktlint)
**Then** the code is rewritten to the canonical style.

**Given** unformatted or lint-violating Kotlin
**When** I run the format-check task (or `./gradlew check`)
**Then** it reports the violations without rewriting and exits non-zero.

**Given** a clean, formatted tree
**When** I run `./gradlew check`
**Then** the format-check passes and does not modify any file.

**Given** the v1 scope
**When** I inspect the build
**Then** no secondary static-analysis tool (e.g. detekt) is configured, and adding one later would be a config change only.

### Story 1.3: Concern-package layout and a bootable application

As a coding agent extending the service,
I want the fixed root package and one package per concern, plus an application that boots and answers a liveness probe,
So that there is exactly one home for each kind of code and a running process to build on.

**Acceptance Criteria:**

**Given** the source tree
**When** I inspect `src/main/kotlin/com/hl/service`
**Then** the root package is `com.hl.service` and the packages `controller`, `service`, `repository`, `model`, `dto`, `error`, `config` exist
**And** `StarterApplication.kt` is the single `@SpringBootApplication` entry point
**And** the layout is documented in the README as a naming convention (no build-time boundary check exists).

**Given** the Compose Stack is not required for this story
**When** I start the application on the default profile with web (`spring-boot-starter-webmvc`) and actuator on the classpath
**Then** the process starts and `GET /actuator/health/liveness` returns `200` with status `UP`
**And** the liveness and readiness health groups are enabled (Boot 4 default).

### Story 1.4: Compose Stack for local dependencies

As a developer,
I want `docker compose up` to start Postgres and Redis and nothing else, with image tags pinned in one place,
So that local dependencies match CI exactly and come up on a clean machine.

**Acceptance Criteria:**

**Given** a clean machine with Docker
**When** I run `docker compose up` from the repo root
**Then** only Postgres and Redis containers start (no OpenTelemetry collector, no other service)
**And** data directories and ports are configured so the stack works with nothing else installed.

**Given** the Compose definition
**When** I inspect it and the root `.env`
**Then** the Postgres and Redis image tags are declared once in `.env` and referenced by `docker-compose.yaml`
**And** the README notes that the same `.env` feeds Testcontainers so local and CI versions stay in lockstep.

### Story 1.5: Postgres datasource and Flyway, configured from the environment

As the Starter maintainer,
I want the datasource and Flyway wired with all connection settings externalized and Flyway owning the schema,
So that the service connects on startup with no credentials in code and schema drift fails loudly.

**Acceptance Criteria:**

**Given** the Compose Stack is up and the `local` profile is active
**When** the application starts
**Then** it connects to Postgres using URL, username and password taken from environment variables / Spring configuration
**And** Flyway runs on startup and records applied migrations in its history table (an empty migration set is valid at this stage).

**Given** a grep of `src/main` and committed non-`local` configuration
**When** I search for credentials
**Then** none are found; the only literal credentials are local-only defaults in `application-local.yaml`.

**Given** missing or wrong datasource configuration
**When** the application starts
**Then** startup fails with a clear error rather than entering a silent degraded mode.

**Given** the JPA configuration
**When** I inspect it
**Then** `spring.jpa.hibernate.ddl-auto` is `validate`, Flyway checksum validation is on and auto-repair is off
**And** a missing or checksum-mismatched migration fails startup loudly.

### Story 1.6: Redis connection and readiness gating

As an operator,
I want Redis wired from configuration as a hard dependency whose health gates readiness,
So that a pod without a healthy Redis never enters rotation and there is no hidden degraded mode.

**Acceptance Criteria:**

**Given** the Compose Stack is up and the `local` profile is active
**When** the application starts
**Then** it connects to Redis using host, port and any credentials from environment / configuration, none hard-coded.

**Given** the health configuration
**When** I query `GET /actuator/health/readiness`
**Then** the readiness group reports `UP` only when both the datasource and Redis contributors are `UP`
**And** during startup, before connections are ready, readiness reports `DOWN`.

**Given** Redis is stopped while the service runs
**When** the Redis health contributor is next evaluated
**Then** the readiness group reports `DOWN`
**And** no `CacheErrorHandler` bean is registered (Spring's default fail-fast behavior is left in place).

### Story 1.7: Run the service locally on the `local` profile

As a developer,
I want one Gradle command to run the service against the Compose Stack with a clean config split,
So that "works on my laptop" uses the same configuration mechanism as every other environment.

**Acceptance Criteria:**

**Given** the Compose Stack is up
**When** I run `./gradlew bootRun` with the `local` profile active
**Then** the service starts, connects to the Compose Stack's Postgres and Redis, applies migrations, and `GET /actuator/health` reports `UP`.

**Given** the resources
**When** I inspect `application.yaml` and `application-local.yaml`
**Then** `application.yaml` contains only defaults and environment-variable placeholders
**And** `application-local.yaml` is the only file carrying literal (throwaway) credentials
**And** no other profile file carries environment-specific values.

**Given** the configuration handling
**When** I inspect it
**Then** every deployment-varying setting is read from an environment variable / externalized config, with a documented default where applicable.

### Story 1.8: Document the local development loop

As a developer or coding agent picking up a Consumer Service,
I want the README to describe the full local loop end to end,
So that a clean machine reaches a running service and a green build with no undocumented steps.

**Acceptance Criteria:**

**Given** a clean machine with Docker and a JDK
**When** I follow the README's local development section top to bottom
**Then** I reach a running service and a green `./gradlew build` with no step missing.

**Given** the README section
**When** I read it
**Then** it covers prerequisites, bringing up dependencies, running the service, running tests, and viewing API docs (noting Swagger UI is `local`-only and there is no local trace viewing in v1).

## Epic 2: A working reference feature to copy — `widgets` end to end

A complete `/api/v1/widgets` CRUD resource exercising REST → service → Postgres → Redis: distinct domain and JPA entity types, application-generated UUIDs, `@Cacheable`/`@CacheEvict` caching, the offset-pagination envelope, the RFC 7807 error contract with one `GlobalExceptionHandler` and three exception types, request validation, served OpenAPI plus `local`-only Swagger UI, and Unit + Integration tests at both levels. Documented as the pattern to copy; removable without breaking the build. **FRs:** FR-6, FR-9, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-39, FR-40. **NFRs:** NFR-1, NFR-6. **Architecture:** AD-1, AD-4, AD-8, AD-9, AD-10, AD-11, AD-13, AD-14, AD-21.

### Story 2.1: Widget domain model, JPA entity, and first migration

As a coding agent copying the pattern,
I want an immutable `Widget` domain type, a separate `WidgetEntity` with hand-written mapping, and the `widgets` table migration,
So that Hibernate semantics never reach the domain model and the schema is owned by Flyway.

**Acceptance Criteria:**

**Given** the `model/` package
**When** I inspect `Widget`
**Then** it is an immutable Kotlin class carrying a `java.util.UUID` id generated in the domain (`UUID.randomUUID()`) before persistence, with `java.time` timestamps in UTC.

**Given** the `repository/` package
**When** I inspect it
**Then** `WidgetEntity` (`@Entity`) and `WidgetRepository` (`JpaRepository<WidgetEntity, UUID>`) live there
**And** mapping between `Widget` and `WidgetEntity` is explicit hand-written code with no mapping framework on the classpath.

**Given** `src/main/resources/db/migration`
**When** the service starts on a clean database
**Then** `V1__create_widgets.sql` creates the `widgets` table with a `uuid` primary key and `timestamptz` columns
**And** `spring.jpa.hibernate.ddl-auto = validate` passes against the migrated schema.

### Story 2.2: Shared Testcontainers base class and a persistence Integration Test

As the Starter maintainer,
I want one base class that owns Postgres and Redis containers for the whole test run, plus a write-then-read test through the JPA repository,
So that every future resource's Integration Test reuses it with no new infrastructure code.

**Acceptance Criteria:**

**Given** `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt`
**When** I inspect it
**Then** it owns `static` Testcontainers Postgres and Redis annotated `@ServiceConnection`, started once per JVM and shared
**And** Integration Tests extend it, carry `@Tag("integration")`, and add nothing infrastructural
**And** test isolation is per-test data cleanup, not per-test containers.

**Given** the persistence Integration Test
**When** it runs
**Then** Testcontainers starts Postgres, Flyway migrates it, and the test performs a write-then-read through `WidgetRepository` and asserts the row round-trips
**And** it runs with no external database and no setup beyond Docker being available.

### Story 2.3: RFC 7807 error contract and the Global Exception Handler

As a client or coding agent,
I want three fixed exception types and a single handler that renders every error as `application/problem+json`,
So that every 4xx/5xx across the service — and every Consumer Service — has one predictable shape.

**Acceptance Criteria:**

**Given** the `error/` package
**When** I inspect it
**Then** a sealed `AppException` base and `BusinessException` (→400), `NotFoundException` (→404), `SystemException` (→500) are defined, each with exactly one constructor shape `message: String, details: Map<String, Any?> = emptyMap()`
**And** there is no `ErrorCode` catalogue and no per-instance code parameter.

**Given** a single `@RestControllerAdvice` `GlobalExceptionHandler` in `controller/`
**When** any exception reaches it (Business, Not-Found, System, framework, or unrecognized)
**Then** the response is `Content-Type: application/problem+json` with `type` = `about:blank`, `instance` = the request path, `detail` = the exception's own `message`
**And** extension members are `code` (a fixed literal per type — `BUSINESS_ERROR` / `NOT_FOUND` / `SYSTEM_ERROR`, or `UNEXPECTED_ERROR` for an unrecognized exception, assigned by the handler), `traceId`, and `details` (omitted when empty)
**And** the HTTP status is fixed by the exception type, not chosen per instance.

**Given** a `SystemException`
**When** the handler renders it
**Then** the client receives its `message` and `details` (no server-log-only variant)
**And** the exception is also logged at error level with the same trace id.

**Given** the handler's tests (a minimal test controller may be used)
**When** they run
**Then** they cover a Business, a Not-Found, a System, and an unanticipated exception, each asserting status, `Content-Type`, and `code`.

### Story 2.4: Widget service operations

As a coding agent,
I want a `WidgetService` that creates, reads by id, updates, deletes, and lists widgets,
So that the application logic sits in one place and orchestrates the domain and the repository.

**Acceptance Criteria:**

**Given** `service/WidgetService`
**When** I inspect it
**Then** it maps between `Widget` and `WidgetEntity`, is annotated `@Service` (and `@Transactional` on mutating methods by convention), and returns domain types, never entities or DTOs.

**Given** a read-by-id for an id that does not exist
**When** the service handles it
**Then** it throws `NotFoundException`.

**Given** a list request with `page` and `size`
**When** the service handles it
**Then** it queries via Spring Data `Pageable` and returns a `Page` (or a domain-level equivalent carrying total counts) for the controller to render.

**Given** unit tests for the service
**When** they run
**Then** they use a hand-written in-memory fake `WidgetRepository`, no Spring context and no container, and cover create/read/update/delete/list plus the not-found path.

### Story 2.5: Widget REST endpoints and DTOs

As an API client,
I want `/api/v1/widgets` CRUD over JSON with the standard verbs, status codes, and list envelope,
So that every resource in every Consumer Service behaves identically.

**Acceptance Criteria:**

**Given** `controller/WidgetController` and `dto/WidgetRequest` / `dto/WidgetResponse`
**When** I inspect them
**Then** the path is `/api/v1/widgets` (plural, lowercase), the domain type never appears in a controller signature, and DTOs carry the wire representation.

**Given** the endpoints
**When** I exercise them
**Then** `POST` collection creates and returns `201` with a `Location` header and the created `WidgetResponse`; `GET /{id}` returns `200`; `PUT /{id}` is a full update; `DELETE /{id}` returns `204`.

**Given** the collection `GET`
**When** I call it with `?page=` (0-based) and `?size=` (a configured default)
**Then** the response is the shared `dto/PageResponse` envelope `{ items, page, size, totalElements, totalPages }`, built once and reusable by any resource.

**Given** a `GET /{id}` for a missing id
**When** the handler processes it
**Then** the response is `404` with the standard Problem Detail body (via the Story 2.3 handler).

### Story 2.6: Request validation with field-level errors

As an API client,
I want invalid request bodies rejected with `400` and per-field detail in the standard error shape,
So that validation failures are not a separate error path to handle.

**Acceptance Criteria:**

**Given** `WidgetRequest`
**When** I inspect it
**Then** it carries Bean Validation annotations on at least one field.

**Given** a request that violates a constraint
**When** the controller receives it
**Then** the response is `400` `application/problem+json` with an `errors` extension member — an array of `{ field, code, message }` — produced by the `GlobalExceptionHandler`, not a second error schema.

**Given** a validation test
**When** it runs
**Then** it asserts the status, the `Content-Type`, and the contents of `errors`.

### Story 2.7: Redis cache-aside on read-by-id

As an operator,
I want read-by-id served from Redis with writes invalidating the entry, using Spring's cache annotations,
So that caching is framework-managed with no hand-written orchestration to copy wrong.

**Acceptance Criteria:**

**Given** `WidgetService`
**When** I inspect the read-by-id method
**Then** it is annotated `@Cacheable` and the update and delete methods are annotated `@CacheEvict` for the same key
**And** no cache-orchestration code (manual Redis get/put/fallthrough) exists.

**Given** a widget has been read once
**When** it is read again within the TTL
**Then** the second read is served from Redis with no Postgres query (observable via query counting or instrumentation).

**Given** a widget is updated or deleted
**When** it is read again
**Then** the cached entry has been evicted/refreshed and the read does not return stale data.

**Given** the TTL
**When** I inspect configuration
**Then** it is a configuration value with a documented default (10 minutes).

### Story 2.8: Cache behavior Integration Test

As the Starter maintainer,
I want an Integration Test proving cache hit, miss, invalidation, and the Redis-down failure mode,
So that the hard-dependency contract is verified, not assumed.

**Acceptance Criteria:**

**Given** the cache Integration Test extending the shared base class
**When** it runs
**Then** it asserts a miss populates the cache, a hit avoids the datastore, and a write invalidates the entry.

**Given** Redis is stopped
**When** a read-by-id is attempted
**Then** it returns `500` (System Exception, standard Problem Detail body) and readiness reports `DOWN`
**And** there is no Postgres-served fallback.

### Story 2.9: Example Slice coverage at both levels

As the Starter maintainer,
I want Unit Tests for the slice's logic and one Integration Test driving HTTP → store → back, plus one deliberate throw of each exception type,
So that the copy target demonstrates the full testing pattern.

**Acceptance Criteria:**

**Given** the Example Slice Unit Tests
**When** they run
**Then** they cover the use case and any domain rules with no container and no Spring context, using hand-written fakes.

**Given** the Example Slice HTTP-to-store Integration Test
**When** it runs
**Then** it drives a real HTTP request through to Postgres and Redis and back, asserting the response status and body, the persisted row, and the cache entry.

**Given** the Example Slice code
**When** I inspect it
**Then** it deliberately throws at least one `BusinessException`, one `NotFoundException`, and one `SystemException`, and all three paths are covered by tests.

### Story 2.10: Served OpenAPI and `local`-only Swagger UI

As a human or agent inspecting the API surface,
I want an OpenAPI JSON document in every profile and Swagger UI only under `local`,
So that the surface is machine-readable everywhere without exposing an interactive UI in production.

**Acceptance Criteria:**

**Given** the running service on any profile
**When** I request the OpenAPI JSON endpoint (`/v3/api-docs`)
**Then** it returns a document reflecting the `/api/v1/widgets` endpoints.

**Given** the `local` profile
**When** I open Swagger UI
**Then** it is served; on every other profile it is disabled (`springdoc.swagger-ui.enabled`).

**Given** an additional annotated `@RestController` mapping (e.g. a throwaway test resource)
**When** the service starts
**Then** it appears in the OpenAPI document with no springdoc configuration change.

### Story 2.11: "Add a REST resource" guide and Example Slice removal

As a developer or coding agent,
I want a README step listing exactly which files to create for a new resource, and a documented step to remove the Example Slice,
So that adding a resource touches zero Plumbing and the slice can be deleted cleanly.

**Acceptance Criteria:**

**Given** the README "add a REST resource" section
**When** I follow it for a second resource (beyond `widgets`)
**Then** it names exactly which files to create and in which concern package
**And** `git diff --name-only` shows only new resource files — no edit to build files, configuration, observability wiring, container config, or CI workflow
**And** the new resource's Integration Test extends the shared base class and adds nothing infrastructural.

**Given** the README "remove the example" step
**When** I follow it (delete `Widget*` code, its tests, and `V1__create_widgets.sql`)
**Then** `./gradlew build` stays green — no Plumbing has a compile dependency on `widgets`.

## Epic 3: A service that's observable and well-behaved in production

Prometheus metrics, OTLP tracing (a no-op when unconfigured), structured JSON logs correlated by trace id, and graceful shutdown — all driven by configuration and identical across local, CI, and production, with no observability code path that only runs in production. **FRs:** FR-20, FR-21, FR-22, FR-23. **NFRs:** NFR-2, NFR-8. **Architecture:** AD-17.

### Story 3.1: Prometheus metrics endpoint

As an operator,
I want Micrometer metrics in Prometheus format at a scrapeable endpoint with no extra wiring,
So that a Prometheus server can scrape service health from the first commit.

**Acceptance Criteria:**

**Given** the actuator configuration
**When** I inspect it
**Then** actuator exposes `health`, `info`, and `prometheus` only, on the main port — nothing else.

**Given** the running service
**When** I scrape the Prometheus endpoint
**Then** it returns Prometheus-format text including JVM, HTTP server, datasource, and cache metrics.

**Given** a Prometheus server pointed at the endpoint
**When** it scrapes
**Then** no additional application wiring is required beyond configuration.

### Story 3.2: OTLP trace export, no-op when unconfigured

As an operator,
I want traces exported over OTLP to a configured endpoint, and a clean no-op when no endpoint is set,
So that tracing works in production without breaking local or CI runs.

**Acceptance Criteria:**

**Given** no OTLP endpoint is configured (the default)
**When** the service starts and serves requests
**Then** it runs normally with no exporter errors in the logs — the exporter is a no-op, not a retry against a default localhost endpoint.

**Given** an OTLP endpoint is configured
**When** an inbound HTTP request triggers outbound DB and cache calls
**Then** spans for the request and the outbound calls are exported to that endpoint (Micrometer Tracing over the OTel bridge).

**Given** the sampling configuration
**When** I inspect it
**Then** `management.tracing.sampling.probability` is a configuration value with a documented default.

### Story 3.3: Structured JSON logging with trace correlation

As an operator,
I want single-line JSON logs on stdout carrying trace and span ids within a request,
So that logs and traces correlate and no log path is production-only.

**Acceptance Criteria:**

**Given** a non-`local` profile
**When** the service logs
**Then** each line on stdout is single-line JSON via Spring Boot's built-in structured logging (`logging.structured.format.console`) — no encoder dependency, no logback XML — with at least timestamp, level, logger, and message.

**Given** a request is in scope
**When** log lines are emitted during it
**Then** they include the trace id and span id.

**Given** the `local` profile
**When** the service logs
**Then** output stays human-readable console format.

**Given** the logging configuration
**When** I inspect it
**Then** no secondary log sink or file appender is configured.

### Story 3.4: Graceful shutdown on SIGTERM

As the platform,
I want the service to drain in-flight requests within a bounded period on SIGTERM before exiting,
So that rolling deploys don't drop requests.

**Acceptance Criteria:**

**Given** the runtime configuration
**When** I inspect it
**Then** `server.shutdown=graceful` is set and `spring.lifecycle.timeout-per-shutdown-phase` has a documented default of 30 s, overridable by environment variable.

**Given** a request is in flight
**When** the process receives SIGTERM
**Then** it stops accepting new requests, the in-flight request completes within the timeout, and the process then exits.

## Epic 4: A published image and a green CI pipeline

`./gradlew bootBuildImage` produces a non-root OCI image that meets the Kubernetes runtime contract (framework-default probe paths, SIGTERM, env configuration), verified by a documented local check. GitHub Actions builds and fully tests every pull request with Testcontainers and no workflow service containers; a push to `main` publishes the image to a private `ghcr.io` package tagged with the git short SHA plus `latest`, with Gradle dependency and build caches restored between runs. **FRs:** FR-28, FR-29, FR-30, FR-31, FR-32. **NFRs:** NFR-2, NFR-5, NFR-9, NFR-12. **Architecture:** AD-19, AD-20.

### Story 4.1: Image built via `bootBuildImage`

As the platform,
I want an OCI image produced by buildpacks with no Dockerfile, running non-root from environment configuration,
So that every Consumer Service ships the same well-behaved image with nothing to drift.

**Acceptance Criteria:**

**Given** the repo
**When** I inspect it
**Then** there is no Dockerfile, and `bootBuildImage` is configured with builder `paketobuildpacks/builder-noble-java-tiny` pinned to an explicit tag (never floating)
**And** `BP_JVM_CDS_ENABLED` and the AOT cache are off.

**Given** `./gradlew bootBuildImage`
**When** it completes
**Then** it produces a runnable image locally that runs as a non-root user by default.

**Given** the built image
**When** I run it with configuration supplied entirely from environment variables
**Then** it starts and answers its liveness and readiness health endpoints.

**Given** heap sizing
**When** I inspect the README
**Then** it documents the buildpack's container-aware calculator and the `JAVA_TOOL_OPTIONS` / `BPL_JVM_*` knob rather than fixing a number.

### Story 4.2: CI builds and tests every pull request

As a maintainer,
I want every pull request to run the full build with Testcontainers on hosted runners and block merge on failure,
So that no change lands without format-check, Unit, and Integration Tests passing.

**Acceptance Criteria:**

**Given** `.github/workflows/ci.yaml`
**When** a pull request is opened or updated
**Then** the workflow runs `./gradlew build` — format-check, the Unit Tests, and the Integration Tests — on `ubuntu-latest`.

**Given** the Integration Tests
**When** they run in CI
**Then** they use Testcontainers with no external database or cache and no service containers declared in the workflow.

**Given** a failing check
**When** the PR is evaluated for merge
**Then** merge is blocked.

**Given** consecutive runs
**When** the workflow executes
**Then** Gradle's dependency cache and build cache are restored between runs, and a no-op-change PR pipeline completes within the ~10-minute budget.

### Story 4.3: CI publishes the image on push to `main`

As the platform,
I want a green push to `main` to publish the image to a private `ghcr.io` package tagged by commit,
So that a deployable artifact exists for every mainline commit with no stored credential to rotate.

**Acceptance Criteria:**

**Given** a push to `main`
**When** the build passes
**Then** the workflow builds and publishes the image to a **private** `ghcr.io` package
**And** the image is tagged with the git short SHA, and `latest` is moved to that image (no semver tags in v1).

**Given** any branch that is not `main`, or a failing build
**When** the workflow runs
**Then** no image is published.

**Given** the publish step
**When** I inspect it
**Then** it authenticates with the workflow's built-in `GITHUB_TOKEN` under `permissions: packages: write` — there is no stored registry secret.

### Story 4.4: Kubernetes runtime contract verified locally

As the platform,
I want a documented local check that the image honors the probe paths, env config, non-root, and SIGTERM contract,
So that the charts repository can depend on the image behaving predictably without a cluster in the loop.

**Acceptance Criteria:**

**Given** the README's runtime-contract check
**When** I follow it (`docker run` the image with env configuration and curl the probe endpoints)
**Then** liveness and readiness succeed at the framework-default paths with no overrides
**And** the container is confirmed to run as non-root and to read all configuration from environment variables.

**Given** the running container
**When** I send SIGTERM and time the exit
**Then** the process exits cleanly within the graceful-shutdown timeout.

**Given** the same local check
**When** I measure startup
**Then** the service reaches readiness in under ~10 s on a modest container
**And** the README states that an actual cluster deployment is the charts repository's concern, not this check's.

## Epic 5: Clone-to-service in under 15 minutes, roadmap protected

The Four Parameters each get one documented home and a README checklist, so changing only them yields a running, CI-green, image-publishing service with no source edit. The inactive, fail-safe Auth Seam (`app.auth.enabled` selecting one of two `SecurityFilterChain` beans) is in place so Auth0 drops in later without structural change. The README covers all five essential tasks and states what is deliberately out of v1. **FRs:** FR-33, FR-34, FR-35, FR-36, FR-37, FR-38. **NFRs:** NFR-11, NFR-12. **Architecture:** AD-16, AD-18.

### Story 5.1: The Four Parameters, each with one documented home

As a developer turning the Starter into a Consumer Service,
I want each of the four parameters to have exactly one documented location,
So that I change a known short list and nothing else.

**Acceptance Criteria:**

**Given** the README parameter checklist
**When** I read it
**Then** it names, for service name (the Gradle project name), database name + credentials, HTTP port, and image name, the exact file(s) and key(s) that hold each.

**Given** any parameter that must physically appear in more than one place (e.g. a port in config and in a healthcheck)
**When** I follow the checklist
**Then** the value is derived from one source, or the README explicitly calls out both locations — changing one documented place is never silently insufficient.

**Given** the root `.env`
**When** I inspect it
**Then** it holds only the Postgres and Redis container image tags and is described as Plumbing, not a home for any of the Four Parameters
**And** the base package `com.hl.service` and the concern package names are documented as invariant.

**Given** the number of files edited for parameterization
**When** I complete the checklist
**Then** it stays in single digits.

### Story 5.2: Generator explicitly deferred, seam documented

As a maintainer,
I want the README to state that no generator ships in v1 and to list the manual steps a generator would replace,
So that the checklist doubles as the future generator's spec and no half-built script is left behind.

**Acceptance Criteria:**

**Given** the repo
**When** I search for rename/scaffolding automation
**Then** none ships — no half-built generator script is present.

**Given** the README
**When** I read the parameterization section
**Then** it notes the generator is deliberately deferred and lists the manual steps it would replace, and the checklist is complete enough to serve as that spec.

### Story 5.3: Changing only the Four Parameters yields a working service

As a developer,
I want a documented walkthrough that changes only the four parameters and reaches a running, CI-green, image-publishing service,
So that the under-15-minute promise is verified, not asserted.

**Acceptance Criteria:**

**Given** a fresh clone
**When** I change only the Four Parameters per the README and make no source code change
**Then** `./gradlew build` is green, the service runs locally against the Compose Stack, and a push to `main` publishes an image.

**Given** the walkthrough
**When** I follow it
**Then** reaching that state requires no edit outside the documented parameter homes (the Example Slice may be removed afterward as a separate step).

### Story 5.4: Inactive, fail-safe OAuth2 resource-server Auth Seam

As a maintainer protecting the Auth0 roadmap,
I want a committed OAuth2 resource-server config that is inert by default and fails safe when enabled,
So that authentication can be switched on later with no structural change and never degrades to allow-all.

**Acceptance Criteria:**

**Given** `config/SecurityConfig.kt`
**When** I inspect it
**Then** a single `app.auth.enabled` property (default `false`) selects one of two `SecurityFilterChain` beans.

**Given** the default configuration (`app.auth.enabled=false`)
**When** the service runs
**Then** an **explicit** permit-all chain is installed and every endpoint behaves as it does today — `spring-security` on the classpath does not silently secure anything with a generated password.

**Given** `app.auth.enabled=true` with a resolvable issuer
**When** the service runs
**Then** a valid JWT is required on `/api/**` and `/v3/api-docs`, `/actuator/health**` stays open, and every other actuator endpoint requires a token
**And** issuer, audience, and JWKS values are taken from configuration.

**Given** `app.auth.enabled=true` without a resolvable `issuer-uri`
**When** the context starts
**Then** startup fails — it can never degrade to allow-all.

**Given** the enabled-mode smoke test
**When** it runs
**Then** it mints/validates a token against a test issuer and confirms the inactive configuration compiles and enforces JWT validation when switched on.

### Story 5.5: Auth Seam documentation

As a developer enabling authentication later,
I want the README to explain exactly how to turn the seam on,
So that adoption is configuration and test wiring, not rediscovery.

**Acceptance Criteria:**

**Given** the README Auth Seam section
**When** I read it
**Then** it lists the exact configuration keys for issuer URI, expected audience, and JWKS URI
**And** it states the v1 posture (seam only, nothing enforced) and the intended Auth0 direction
**And** it explains how Integration Tests authenticate when the seam is on, and how Actuator endpoints are treated (`health`, `info`, `prometheus` exposed; `/actuator/health**` open, other actuator endpoints token-gated).

### Story 5.6: README covers the five essential tasks

As a human or agent picking up a Consumer Service,
I want one short, task-oriented README with a dedicated section per essential task,
So that the whole surface is discoverable from one entry point.

**Acceptance Criteria:**

**Given** the README
**When** I scan it
**Then** it has a dedicated, findable section for each of: the Four Parameters and where each lives; how to add a REST resource; the local development loop; how to enable the Auth Seam; and the observability endpoints.

**Given** the "add a REST resource" section
**When** I follow it
**Then** it produces a working resource with passing tests (ties to Story 2.11).

**Given** the README
**When** I read its scope statement
**Then** it states what is deliberately out of v1 — generator, charts, messaging, auth enforcement, Vault — with a pointer to the brief and addendum.
