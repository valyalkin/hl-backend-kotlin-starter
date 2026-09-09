---
title: "PRD: Spring Boot Kotlin Starter Service"
status: final
created: 2026-09-07
updated: 2026-09-09
---

# PRD: Spring Boot Kotlin Starter Service

*Repository: `hl-backend-kotlin-starter`. "Spring Boot Kotlin Starter Service" is the descriptive product name used throughout this PRD.*

## 0. Document Purpose

This PRD is for Alex (owner and primary implementer), for any engineer who later maintains or extends the starter, and for the downstream BMad workflows — architecture, epics and stories, build — that turn it into code. It builds directly on the finalized **Product Brief: Spring Boot Kotlin Starter Service** (`_bmad-output/planning-artifacts/briefs/brief-hl-backend-kotlin-starter-2026-09-07/brief.md`) and its **addendum**; it does not restate the market rationale or the "what makes this different" argument found there.

Structure: vocabulary is fixed in the Glossary (§3) and used verbatim everywhere else. Capabilities are grouped into features (§4) with globally numbered Functional Requirements (`FR-N`) nested beneath them, so downstream artifacts have stable references even if features get reorganized. System-wide qualities live in Cross-Cutting NFRs (§10). Inferences not explicitly confirmed by Alex are tagged `[ASSUMPTION: ...]` inline and collected in the Assumptions Index (§9), which also records the assumptions the architecture step has since resolved. Technology-selection detail and rejected-alternative rationale live in the companion `addendum.md`. The architecture step has since run; where it settled or overturned an assumption, the affected FR carries a *Settled by* / *Overturned by* note with the Architecture Decision id, and §9.2 collects them.

## 1. Vision

Every backend service in the stack needs the same 80% of plumbing before a line of domain code: a Kotlin + Spring Boot build, Postgres and Redis wiring, a local run profile, container dependencies for tests, a Kubernetes-ready image, and a CI pipeline. The **Starter** is that 80%, solved once and kept working: a cloneable repository that builds, tests, runs locally, and publishes a deployable image out of the box, organized in clean-architecture layers, with one **Example Slice** (`widgets`: REST → Postgres → Redis) that shows the pattern to copy end to end.

The Starter exists because the plumbing will increasingly be extended by AI coding agents, not only by hand. That raises the bar on structure: one obvious home for each kind of code, conventions identical across every service, and a test suite that fails loudly when something is wired wrong. A new **Consumer Service** should reach *runs locally + green CI + publishable image* in under 15 minutes by changing four parameters and nothing else.

Success two to three years out: every Kotlin backend service in the stack traces back to this baseline and shares its structure, so a dependency bump or a probe fix worked out once is mechanical to apply everywhere. Vault-backed secrets and Auth0 authentication are deliberately out of v1, but the Starter is shaped so they drop in later without a rewrite.

## 2. Target User

### 2.1 Jobs To Be Done

- **Stand up a new production-ready service fast** — go from clone to a running, CI-green, publishable service by changing a known short list of parameters, without re-deriving any plumbing.
- **Keep services from drifting apart** — share one package structure, one dependency set, one test strategy across every service, so cross-service work and upgrades stay mechanical.
- **Let an agent extend a service without guesswork** — give an AI coding agent an unambiguous place to add each kind of code and a test suite that catches miswiring immediately.
- **Trust that "it works on my laptop" equals "it works in CI and Kubernetes"** — the local profile, the CI run, and the deployed image behave the same because they share configuration and start the same dependencies.
- **Protect the roadmap** — keep configuration and the security boundary shaped so Vault and Auth0 slot in later as the standard, not as a per-service project.

### 2.2 Non-Users (v1)

- **Non-JVM services** — the Starter is Kotlin + Spring Boot specific; a Go or Node service gets nothing from it.
- **The public / open source** — this is an internal baseline, fork-and-adapt within the stack; it is not published, documented, or supported for outside use.
- **Services that need messaging, gRPC, multiple datasources, or a multi-module build on day one** — those are out of v1 (§5, §6.2); such a service would start here and add them, but the Starter does not ship them.
- **Chart / deployment authors** — Helm charts and Argo CD config live in a separate repository; the Starter's responsibility ends at a well-behaved image plus CI.

### 2.3 Key User Journeys

Internal tooling with two operator roles — a human engineer and an AI coding agent — so journeys are captured at light scope: a named actor, entry state, a short path, and how they know it worked.

- **UJ-1. Alex spins up a new service from the Starter.**
  Alex has a new service to build. He clones the Starter into a fresh repo, opens the README checklist, and changes four parameters: service name, database name + credentials, HTTP port, image name. He runs `docker compose up` and one Gradle command; the service starts and answers its health endpoint. He pushes to `main`; GitHub Actions builds, runs the full unit + integration suite, and publishes an image to the registry. Elapsed time under 15 minutes. **Climax:** the CI run is green and an image tag exists. **Resolution:** he deletes or rewrites the Example Slice and starts on domain code. **Edge case:** if he misses a parameter, the failing check names it (wrong DB name → integration test fails with a clear connection error; wrong port → documented single source of truth). If Redis is not up locally, readiness never goes green and `bootRun` reports the same not-ready state it would in production — Redis is a hard dependency (FR-10), so this failure looks identical everywhere by design.

- **UJ-2. Alex adds a new REST resource.**
  Working in a Consumer Service, Alex needs a new resource, `orders`. He copies the Example Slice's shape: a domain type, an application use case, an inbound REST adapter, an outbound persistence adapter, a Flyway migration, and tests at both levels. He touches no build file, no configuration, no observability wiring, no CI. **Climax:** the boundary-enforcement test and the new integration test pass on the first honest attempt. **Resolution:** `orders` is live with the same conventions as every other resource in the stack.

- **UJ-3. An AI coding agent implements a feature end to end.**
  An agent is told to add a capability to a Consumer Service. It reads the layer conventions and the Example Slice, places each piece of code in the layer the structure dictates, and writes unit and integration tests mirroring the example. It runs the suite; the boundary test fails loudly if it put a persistence call in the domain layer, and the integration tests fail loudly if a dependency is miswired. **Climax:** a green suite the agent can trust as its done-signal. **Resolution:** the change is consistent with the rest of the codebase with no human restructuring.

## 3. Glossary

Downstream workflows and readers use these terms exactly. FRs, UJs, and SMs use them verbatim; a synonym anywhere in this PRD is a discipline violation.

- **Starter** — this repository: the template Spring Boot + Kotlin service with all shared plumbing solved, plus the Example Slice. The artifact this PRD specifies.
- **Consumer Service** — a service created by cloning the Starter and changing the Four Parameters. Each Consumer Service has different domain logic and reuses the plumbing unchanged.
- **Example Slice** — the one reference feature that ships in the Starter: a `widgets` resource exercising REST → application → Postgres → Redis end to end, with tests at both levels. Meant to be copied as a pattern and then deleted or rewritten.
- **Four Parameters** — the complete set of values a person changes to turn the Starter into a Consumer Service: (1) service name, (2) database name + credentials, (3) HTTP port, (4) image name. Each has exactly one documented home.
- **Layer** — one of the four clean-architecture layers with a fixed package location and fixed dependency rules: **domain**, **application**, **adapter-in** (inbound, e.g. REST), **adapter-out** (outbound, e.g. persistence, cache).
- **Boundary Rule** — a dependency constraint between Layers (e.g. domain must not depend on Spring, adapter-in must not depend on adapter-out) that the Boundary Test enforces.
- **Boundary Test** — an automated test in the Starter's suite that fails the build when a Boundary Rule is violated.
- **Plumbing** — everything in the Starter that is not domain logic: build, layering, persistence and cache wiring, REST conventions, observability, runtime behavior, local dev setup, container image, CI. Adding a resource "touches no Plumbing" when no Plumbing file changes.
- **Local Profile** — the Spring profile (`local`) plus `application-local.yaml` that runs the service against the Compose Stack.
- **Compose Stack** — the `docker compose` definition that starts the service's local dependencies for development. Postgres and Redis only in v1; no OpenTelemetry collector (deferred — §6.2).
- **Integration Test** — a test that starts real Postgres and Redis in containers (Testcontainers) and exercises a path through one or more adapters. Runs in CI with no external infrastructure.
- **Unit Test** — a test of domain or application logic with no container and no Spring context.
- **Error Code** — a fixed, machine-readable identifier (`BUSINESS_ERROR`, `NOT_FOUND`, `SYSTEM_ERROR`, or a per-field validation code) carried as an extension member of every Problem Detail body. One per exception *type*, assigned by the Global Exception Handler — not a per-business-rule catalogue, and not carried by the exception itself. *(Narrowed by architecture AD-11 from the original per-business-rule design — AD-12 retired.)*
- **Business Exception** — `BusinessException`: an error whose cause is the caller's request or a violated business rule, not tied to a specific resource lookup. Maps to 400.
- **Not-Found Exception** — `NotFoundException`: the requested resource does not exist. Maps to 404.
- **System Exception** — `SystemException`: an error whose cause is the service itself or a dependency it calls. Maps to 500.
- **Problem Detail** — the one error body schema, RFC 7807 `application/problem+json`, used by every 4xx and 5xx response the service returns.
- **Global Exception Handler** — the single adapter-in component that translates every exception into the standard error body. Nothing else in the service constructs an error response.
- **Auth Seam** — a committed but inactive OAuth2 resource-server (JWT) configuration plus documentation, present so authentication can be enabled later without structural change. Not active in v1.
- **Registry** — the OCI image registry the CI pipeline publishes to: a **private** package on GitHub Container Registry (`ghcr.io`). CI pushes with the workflow's built-in `GITHUB_TOKEN` under `permissions: packages: write` (no stored secret to rotate); the Kubernetes cluster pulls with an `imagePullSecret` owned by the separate charts repository. *(Settled by architecture AD-20, OQ-1.)*

## 4. Features

### 4.1 Build and language baseline

**Description:** The Starter is a single-module Gradle project using the Kotlin DSL, Kotlin as the only source language, and the latest stable Kotlin / Spring Boot / JVM LTS at architecture time. Dependencies are declared through a Gradle version catalog so a Consumer Service upgrades in one place. Format and lint are wired into the build and enforced in CI. Compiler settings are strict enough that common miswiring fails at compile time. Realizes UJ-1.

**Functional Requirements:**

#### FR-1: Gradle Kotlin DSL build with version catalog

A developer or agent can build, test, and run the Starter with standard Gradle tasks, and can see every third-party dependency version in one catalog file.

**Consequences (testable):**
- `./gradlew build` compiles, runs Unit Tests and Integration Tests, and produces a runnable Spring Boot artifact.
- Third-party dependency versions are declared in a single `gradle/libs.versions.toml`, which pins only what the Spring Boot BOM does not manage (Kotlin, the Gradle plugins, springdoc, ArchUnit, Spotless/ktlint); no version literals in module build files. *(Settled by architecture AD-22.)*
- The Gradle wrapper is committed; no local Gradle install is required.

#### FR-2: Kotlin-only sources with strict compiler settings

The Starter contains no Java sources and compiles under null-safety-strict settings.

**Consequences (testable):**
- `src/main` and `src/test` contain `.kt` files only.
- Kotlin compiler runs with strict JSR-305 / null-safety handling of Spring and Jakarta annotations. `[ASSUMPTION: `-Xjsr305=strict`.]`
- Warnings-as-errors is enabled for the Starter's own code. `[ASSUMPTION.]`

#### FR-3: Format and lint enforced

A developer or agent can auto-format the codebase with one task, and CI fails on unformatted or lint-violating code.

**Consequences (testable):**
- A format-apply task rewrites code to the canonical style; a format-check task reports violations without rewriting. Spotless (8.10.2) drives ktlint (1.5.0). *(Settled by architecture AD-22, Stack.)*
- `./gradlew check` (and therefore CI) fails when the format-check task fails.
- No secondary static-analysis tool (e.g. detekt) is required in v1; adding one later is a config change, not a restructuring.

### 4.2 Clean-architecture skeleton

**Description:** Code is organized into four Layers, each with a fixed package location and fixed dependency direction. Boundary Rules are not a convention in a wiki — they are checked by a Boundary Test that fails the build. Adding functionality means adding a domain type, an application use case, and adapters that mirror the Example Slice, with no change to Plumbing. Realizes UJ-2, UJ-3.

**Functional Requirements:**

#### FR-4: Four defined Layers with fixed locations

The Starter defines exactly four Layers — domain, application, adapter-in, adapter-out — each at a documented package path, and every file in the repo belongs to exactly one.

**Consequences (testable):**
- Each Layer has a single documented base package; the README and the Example Slice both use those paths.
- The Example Slice places its code across all four Layers, demonstrating each.

#### FR-5: Boundary Rules enforced by an automated test

A defined set of Boundary Rules is enforced by a Boundary Test that runs in `./gradlew check` and fails the build on violation.

**Consequences (testable):**
- The Boundary Test fails when domain depends on application, adapter-in, adapter-out, Spring, Jakarta, or Hibernate.
- The Boundary Test fails when application depends on adapter-in or adapter-out concrete types, or on any Spring package other than the two allowed below, or on `org.springframework.web`, `org.springframework.data`, `org.springframework.http`, `jakarta.persistence`, `jakarta.servlet`, or `org.hibernate`.
- The application Layer may use exactly two Spring annotation packages — `org.springframework.stereotype` (`@Service` / `@Component`) and `org.springframework.transaction.annotation` (`@Transactional`) — and nothing else from Spring. Component scanning wires use cases, so there is no shared composition-root `@Configuration` a new resource must edit (protects FR-6, SM-2). The domain Layer stays entirely framework-free. *(Overturned by architecture AD-3 — the earlier PRD assumption was "no Spring in application by default".)*
- The Boundary Test fails when adapter-in depends on adapter-out (or vice versa) directly rather than through application ports.
- A deliberately introduced violation fails CI; the failure message names the offending class and rule.
- The Boundary Test is implemented with ArchUnit (`layeredArchitecture()` plus explicit banned-import rules), run as a normal JUnit test in `./gradlew check`. *(Settled by architecture AD-2, OQ-6.)*

#### FR-6: Adding a resource touches no Plumbing

A developer or agent can add a new REST resource by adding files that mirror the Example Slice, changing zero Plumbing files.

**Consequences (testable):**
- A documented step-by-step in the README lists exactly which files to create for a new resource and in which Layer.
- Following it for a second resource (beyond `widgets`) requires no edit to build files, configuration, observability wiring, container config, or CI workflow.
- The new resource's Integration Test reuses the existing container setup with no new infrastructure code — it extends the shared container base class and adds nothing infrastructural (AD-21).

### 4.3 Persistence — Postgres and Flyway

**Description:** Postgres is wired through Spring Data JPA / Hibernate in an adapter-out Layer, with all connection settings externalized. Schema is owned by Flyway migrations that run on startup; the Example Slice ships the first migration. Repository behavior is proven against a real Postgres in an Integration Test. Realizes UJ-1, UJ-2.

**Functional Requirements:**

#### FR-7: Postgres datasource, externally configured

The service connects to Postgres using connection settings supplied entirely from the environment / Spring configuration, with no credentials in code or committed files (except local-only development defaults in `application-local.yaml`).

**Consequences (testable):**
- Datasource URL, username, and password come from environment variables / config; grep of `src/main` and committed non-local config finds no production credentials.
- With the Compose Stack up and the Local Profile active, the service connects on startup.
- Missing or wrong datasource configuration fails startup with a clear error, not a silent degraded mode.

#### FR-8: Flyway migrations run on startup

Schema changes are applied by Flyway automatically when the service starts, and the migration history is tracked in the database.

**Consequences (testable):**
- On a clean database, startup applies all migrations and records them in Flyway's history table.
- The Example Slice includes at least one versioned migration creating the `widgets` table.
- Startup fails loudly if a migration is missing or checksums mismatch — Flyway checksum validation is on, auto-repair is off. *(Settled by architecture AD-14.)*

#### FR-9: Persistence proven by Integration Test

The Starter includes an Integration Test that runs the persistence adapter against a real Postgres started in a container.

**Consequences (testable):**
- The test starts Postgres via Testcontainers, lets Flyway migrate it, and performs a write-then-read through the JPA adapter.
- The test runs in CI with no external database and no developer setup beyond Docker being available.
- Hibernate runs with `spring.jpa.hibernate.ddl-auto = validate`; Flyway is the only schema authority. *(Settled by architecture AD-14.)*
- Integration Tests live in the single `src/test` source set, carry `@Tag("integration")`, and extend one shared base class that owns static Testcontainers Postgres + Redis (`@ServiceConnection`) started once per JVM; there is no separate Gradle source set for them, and test isolation is per-test data cleanup rather than per-test containers. *(Settled by architecture AD-21, OQ-7.)*

### 4.4 Caching — Redis

**Description:** Redis is wired as an adapter-out Layer with externalized connection settings. The Example Slice uses cache-aside for read-by-id: check Redis, fall through to Postgres on a miss, populate Redis with a TTL. Redis is a **hard dependency** — there is no degraded mode; the readiness probe gates on it. Cache behavior is proven against a real Redis in an Integration Test. Realizes UJ-1.

**Functional Requirements:**

#### FR-10: Redis connection, externally configured

The service connects to Redis using settings supplied from the environment / Spring configuration.

**Consequences (testable):**
- Redis host, port, and any credentials come from environment / config; none are hard-coded.
- With the Compose Stack up and the Local Profile active, the cache is reachable on startup.
- Redis is a hard dependency with **no fallback path**. The Redis health contributor is a member of the readiness health group, so a pod without a healthy Redis never enters rotation. The cache adapter contains no try/catch and no degraded mode; a Redis failure at request time propagates as a System Exception → 500 (FR-39). *(Overturned by architecture AD-13 — the earlier assumption was degrade-to-Postgres; OQ-2.)*
- **Accepted cost:** this couples the service's availability to Redis's. A Redis outage takes the service out of rotation even though Postgres could still serve every read from the datastore. The trade was made deliberately for the least code and no hidden behavior (AD-13); revisit if Redis availability becomes the binding constraint on the service's SLO.

#### FR-11: Example Slice uses cache-aside on read-by-id

Reading a `widget` by id checks Redis first, falls through to Postgres on a miss, and populates Redis with a TTL; writes invalidate or refresh the cached entry.

**Consequences (testable):**
- A second read-by-id within the TTL is served from Redis without a Postgres query (observable via test instrumentation or query counting).
- An update or delete of a `widget` removes or refreshes its cached entry so a subsequent read does not return stale data.
- The TTL is a configuration value with a documented default. `[ASSUMPTION: default 10 minutes.]`

#### FR-12: Cache behavior proven by Integration Test

The Starter includes an Integration Test that exercises cache hit, cache miss, and invalidation against a real Redis started in a container.

**Consequences (testable):**
- The test starts Redis via Testcontainers alongside Postgres.
- It asserts a miss populates the cache, a hit avoids the datastore, and a write invalidates the entry.
- With Redis stopped, a read-by-id returns 500 (System Exception) and readiness reports down — there is no Postgres-served fallback. *(AD-13.)*

### 4.5 Example Slice — `widgets`

**Description:** One vertical feature, `widgets`, exercises the whole stack: REST endpoints in adapter-in, a use case in application, a domain type, and adapter-out persistence + cache. It has Unit Tests for domain/application and Integration Tests for the HTTP-to-store path. It is the thing a developer or agent copies; it can be deleted wholesale without breaking any Plumbing. Realizes UJ-2, UJ-3.

**Functional Requirements:**

#### FR-13: `widgets` REST CRUD endpoints

A client can create, read (by id and list), update, and delete a `widget` over REST/JSON.

**Consequences (testable):**
- Endpoints are under `/api/v1/widgets` (plural, lowercase): `POST` collection creates and returns the `widget` with its id (201 + `Location`); `GET /{id}` returns it; `GET` collection lists paged; `PUT /{id}` is a full update; `DELETE /{id}` returns 204. *(Settled by architecture AD-9.)*
- The collection `GET` uses offset pagination — `?page=` (0-based) and `?size=` (a configuration value with a documented default) — and returns a fixed envelope: `{ items, page, size, totalElements, totalPages }`. The same contract is inherited by every Consumer Service resource; the Starter defines its own `PageRequest` / `PageResult` types so Spring Data's `Pageable` / `Page` never cross into the application Layer (FR-5). *(Settled by architecture AD-10; cursor/keyset paging is deferred.)*
- Request bodies are validated; invalid input returns 400 with the standard error body (FR-16).
- A `GET` for a missing id returns 404 with the standard error body.

#### FR-14: End-to-end path through all Layers

The `widgets` feature demonstrates a request flowing adapter-in → application → adapter-out (Postgres + Redis) and back, with each Layer's responsibility visibly separated.

**Consequences (testable):**
- The REST adapter contains no persistence or cache calls; it calls an application port.
- The application use case contains no Spring web, data, or JPA types (the two DI annotations FR-5 permits — `@Service`/`@Component`, `@Transactional` — aside); it orchestrates domain + outbound ports.
- The Boundary Test passes for the Example Slice.

#### FR-15: Example Slice test coverage at both levels

The Example Slice ships Unit Tests for its domain/application logic and Integration Tests for its HTTP-to-store path.

**Consequences (testable):**
- Unit Tests cover the use case and any domain rules with no container and no Spring context.
- An Integration Test drives a real HTTP request through to Postgres and Redis and back, asserting status, body, persisted row, and cache entry.
- Deleting the entire Example Slice (code + tests + its migration) leaves `./gradlew build` green. `[ASSUMPTION: verified by a documented "remove the example" step; the Boundary Test and Plumbing have no compile dependency on `widgets`.]`

### 4.6 REST API conventions

**Description:** Conventions every Consumer Service inherits: a single JSON error shape, a three-type exception taxonomy (Business Exception / Not-Found Exception / System Exception) funnelled through one Global Exception Handler that emits a Problem Detail carrying a fixed Error Code, consistent validation semantics, and machine-readable API docs so humans and agents can see the surface. Realizes UJ-2, UJ-3.

**Functional Requirements:**

#### FR-16: Standard JSON error response

All error responses across the service share one documented JSON structure.

**Consequences (testable):**
- 4xx and 5xx responses use one schema for error bodies: the Problem Detail schema, RFC 7807 `application/problem+json`. This is a confirmed decision, not an assumption.
- Validation failures (400), not-found (404), and unhandled exceptions (500) all produce that schema; 500 bodies never leak stack traces or internal details.
- Every error body carries an Error Code alongside the human-readable message (FR-39, FR-40).
- Exactly one component produces error bodies — the Global Exception Handler (FR-40); no controller, use case, or filter builds an error response of its own.
- The schema is documented in the README and exercised by at least one test.

#### FR-17: Request validation

Inbound request DTOs are validated declaratively, and violations produce 400 with field-level detail in the standard error body.

**Consequences (testable):**
- Bean Validation annotations on request DTOs are enforced; a violating request returns 400 listing the offending fields.
- Validation failures reach the client through the Global Exception Handler (FR-40) under a standard validation Error Code, not through a separate error path.
- The Example Slice demonstrates at least one constrained field.

#### FR-18: Machine-readable API documentation

The running service exposes an OpenAPI description of its REST surface.

**Consequences (testable):**
- An OpenAPI JSON document is served by the running service in **every** profile and reflects the `widgets` endpoints. Swagger UI is served **only under the `local` profile** (`springdoc.swagger-ui.enabled`), so an interactive UI is never exposed in production without a deliberate decision. *(Settled by architecture OQ-3: springdoc-openapi `springdoc-openapi-starter-webmvc-ui`.)*
- When the Auth Seam (FR-36) is enabled, the OpenAPI JSON endpoint (`/v3/api-docs`) requires a valid token — it is not one of the paths left open, so the API schema is not served to anonymous callers in production. When the seam is disabled (v1 default) the endpoint is open, matching every other endpoint's v1 posture. *(Extends the AD-18 matcher set; PRD decision, fail-safe by default.)*
- Adding a resource per FR-6 adds it to the OpenAPI document with no extra wiring.

#### FR-39: Business, Not-Found, and System exception taxonomy

Every error the service raises deliberately is exactly one of three fixed types — **Business Exception** (400, the caller's request or a business rule is at fault), **Not-Found Exception** (404, the requested resource does not exist), or **System Exception** (500, the service or one of its dependencies failed) — and each carries a human-readable message plus optional structured details, not a per-business-rule error code.

**Consequences (testable):**
- Three types, `BusinessException`, `NotFoundException`, and `SystemException`, are the roots of every deliberately thrown exception in the service's own code. Each has exactly one constructor shape — `message: String` and an optional `details: Map<String, Any?>` (default empty) — with no per-instance error-code parameter and no central `ErrorCode` catalogue. *(Settled by architecture AD-11; supersedes this FR's original per-business-rule catalogue design. AD-12, the enum catalogue, is retired.)*
- Each exception's HTTP status is fixed by its type, not chosen per instance: `BusinessException` always 400, `NotFoundException` always 404, `SystemException` always 500. There is no dedicated conflict/409 status in v1 — a caller-fault case that is not specifically "not found" is a 400.
- The exception's own `message` and `details` are exactly what the client receives in the Problem Detail body — for all three types, **including System Exception**. There is no server-log-only variant: whatever is passed to the exception is public API surface, so calling code must not put secrets or internal implementation detail in a message or a details map. *(Explicit product decision, overturning this FR's original "clients branch on Error Code, never on message text" framing for the message itself — the message is now part of the contract too.)*
- The Example Slice throws at least one Business Exception, one Not-Found Exception, and one System Exception, and all three paths are covered by tests.

#### FR-40: Global Exception Handler produces the standard Problem Detail body

A single Global Exception Handler translates every exception — Business, Not-Found, System, framework, or unanticipated — into a Problem Detail body (FR-16) carrying a fixed, type-level Error Code.

**Consequences (testable):**
- Exactly one `@RestControllerAdvice` produces error responses for the whole service.
- A Business Exception yields 400; a Not-Found Exception yields 404; a System Exception, and any exception the handler does not recognize, yields 500. Each carries a fixed Error Code — `BUSINESS_ERROR`, `NOT_FOUND`, `SYSTEM_ERROR`, or `UNEXPECTED_ERROR` for an unrecognized exception — assigned by the handler per exception type, not per business rule. *(Settled by architecture AD-11; AD-12's per-business-rule catalogue is retired.)*
- Every error response carries `Content-Type: application/problem+json` and conforms to RFC 7807: `type` is `about:blank`, plus `title`, `status`, `detail` (the exception's own `message`), `instance` (the request path), and the extension members `code` (the fixed type-level code), `traceId`, `details` (the exception's details map, omitted when empty), and — for validation failures — `errors`.
- The HTTP status is now the primary signal a client branches on — the three exception types map 1:1 to 400/404/500. `code` is a secondary, type-level hint, not a fine-grained business-rule identifier; a Consumer Service that needs finer client-branchable codes builds its own scheme on top of `details` — not provided by the Starter. *(Narrows this FR's original "clients branch on Error Code, not status" contract.)*
- Validation failures (FR-17) carry field-level detail in the `errors` extension member of the same Problem Detail schema — an array of `{field, code, message}`, not a second error shape.
- A test asserts that every error response the Example Slice can produce validates against the documented Problem Detail schema.
- 500 responses **include** the `SystemException`'s own `message` and `details`, same as the other two types — there is no server-log-only variant. The exception is still logged in full at error level with the trace id (FR-22) regardless of what reaches the client, so the log line and the response can be correlated by trace id. *(Explicit product decision, overturning this FR's original "500 never leaks internal detail" rule — Settled by architecture AD-11.)*
- Tests cover five paths: a Business Exception, a Not-Found Exception, a System Exception, a Bean Validation failure (FR-17), and an unanticipated exception. Each asserts status, `Content-Type`, and Error Code.

### 4.7 Observability and runtime behavior

**Description:** The service is observable and well-behaved under an orchestrator from the first commit: health with liveness/readiness groups, Prometheus metrics, OTLP traces, structured JSON logs correlated by trace id, graceful shutdown, and configuration exclusively from the environment. Realizes UJ-1.

**Functional Requirements:**

#### FR-19: Health endpoints with liveness and readiness groups

The service exposes health information with distinct liveness and readiness groups suitable for Kubernetes probes.

**Consequences (testable):**
- A liveness endpoint reflects process health; a readiness endpoint reflects readiness to serve, including **datasource and Redis** availability (FR-10) — both are members of the readiness health group.
- During startup (before migrations/connections are ready) readiness reports down; after startup it reports up.
- Probe paths are the framework defaults and require no per-service reconfiguration (supports SM-5).

#### FR-20: Prometheus metrics

The service exposes Micrometer metrics in Prometheus format at a scrapeable endpoint.

**Consequences (testable):**
- A metrics endpoint returns Prometheus-format text including JVM, HTTP server, datasource, and cache metrics.
- No additional wiring is needed for a Prometheus server to scrape it (supports SM-6).

#### FR-21: OTLP trace export, no-op when unconfigured

The service exports distributed traces over OTLP to an endpoint taken from configuration, and runs cleanly with tracing effectively disabled when no endpoint is set.

**Consequences (testable):**
- With an OTLP endpoint configured, spans for inbound HTTP requests and outbound DB/cache calls are exported.
- With no endpoint configured, the service starts and serves normally with no exporter errors in the logs — the exporter is a no-op when the endpoint is unset, not a retry against a default localhost endpoint. It is unset by default, so tracing is a no-op locally and in CI. *(Settled by architecture AD-17.)*
- The sampling probability (`management.tracing.sampling.probability`) is a configuration value with a documented default. *(AD-17.)*

#### FR-22: Structured JSON logging with trace correlation

Application logs are emitted as structured JSON to stdout, including trace and span identifiers when a request is in scope.

**Consequences (testable):**
- Log lines on stdout are single-line JSON with at least timestamp, level, logger, message, and — within a request — trace id and span id, using Spring Boot's built-in structured logging (`logging.structured.format.console`) with no encoder dependency or logback XML. The `local` profile keeps human-readable console output. *(Settled by architecture AD-17.)*
- No secondary log sink or file appender is configured by default.

#### FR-23: Graceful shutdown

On receiving a termination signal the service stops accepting new requests, finishes in-flight requests within a bounded period, then exits.

**Consequences (testable):**
- Graceful shutdown is enabled (`server.shutdown=graceful`) with an env-overridable phase timeout; the documented default is 30 s (`spring.lifecycle.timeout-per-shutdown-phase`). *(Settled by architecture AD-17.)*
- A request in flight when SIGTERM arrives completes (within the timeout) rather than being dropped.

#### FR-24: Configuration exclusively from the environment

Every deployment-varying setting is read from environment variables / externalized Spring configuration; nothing deployment-specific is baked into the image or committed outside `application-local.yaml`.

**Consequences (testable):**
- The image contains no environment-specific configuration; the same image runs in any environment given the right environment variables.
- No secret values are present in the repository except throwaway local-development credentials in `application-local.yaml`.
- This keeps the Vault path open: a later Vault mechanism can supply the same environment variables / config with no code change (§5, addendum).

### 4.8 Local development

**Description:** One command brings up dependencies, one command runs the service against them, and the loop is documented. The Local Profile is the only place local-only defaults live. Realizes UJ-1.

**Functional Requirements:**

#### FR-25: Compose Stack for dependencies

`docker compose up` starts every local dependency the service needs — Postgres and Redis, and nothing else in v1.

**Consequences (testable):**
- A single `docker compose up` from the repo root starts Postgres and Redis — and no other service — with image tags declared once in a root `.env` file that Testcontainers also reads, so CI and local runs use identical versions.
- A local OpenTelemetry collector is **not** included; tracing stays configuration-only locally (OTLP endpoint unset → exporter no-op, FR-21). A collector plus a trace UI is deferred (§6.2). *(Overturned by architecture AD-17 — the earlier assumption bundled an off-by-default collector into the Compose Stack; OQ-4.)*
- Data directories and ports are configured so the stack works on a clean machine with Docker installed and nothing else.

#### FR-26: Local Profile runs the service against the Compose Stack

With the Compose Stack up, one Gradle command runs the service locally on the `local` profile.

**Consequences (testable):**
- `./gradlew bootRun` with the `local` profile active starts the service, connects to the Compose Stack's Postgres and Redis, applies migrations, and answers its health endpoint.
- `application-local.yaml` holds the only local-only defaults; no other profile carries environment-specific values.

#### FR-27: Documented development loop

The README documents the full local loop: prerequisites, bring up dependencies, run the service, run tests, and view API docs (Swagger UI under the `local` profile). There is no local trace viewing in v1 — tracing is configuration-only locally.

**Consequences (testable):**
- Following the README on a clean machine (Docker + JDK) yields a running service and a green `./gradlew build` with no undocumented steps.

### 4.9 Container image and CI pipeline

**Description:** The image is produced by the Spring Boot Gradle plugin's `bootBuildImage` (buildpacks — no hand-maintained Dockerfile), runs as non-root, and is layered for cache efficiency. GitHub Actions builds and fully tests every pull request, and on push to `main` additionally publishes the image to the Registry. Integration Tests run in CI with no external services. Realizes UJ-1.

**Functional Requirements:**

#### FR-28: Image built via `bootBuildImage`

`./gradlew bootBuildImage` produces an OCI image of the service with no Dockerfile in the repo.

**Consequences (testable):**
- The task produces a runnable image locally.
- The image runs as a non-root user by default.
- The image starts from environment configuration alone (FR-24) and answers its health endpoints (FR-19).

#### FR-29: CI builds and tests every pull request

A GitHub Actions workflow runs on every pull request and executes the full build including Unit Tests and Integration Tests.

**Consequences (testable):**
- The workflow runs `./gradlew build` (or equivalent) including format-check, the Boundary Test, Unit Tests, and Integration Tests.
- Integration Tests run using Testcontainers on GitHub-hosted `ubuntu-latest` runners with no external database or cache and no service containers declared in the workflow. *(Settled by architecture AD-20.)*
- A failing check blocks merge.

#### FR-30: CI publishes the image on push to `main`

On push to `main`, after a green build, the workflow builds and publishes the image to the Registry.

**Consequences (testable):**
- Publication happens only for `main`, only after tests pass.
- The image is tagged with the git short SHA, and `latest` is moved to that image; no semver tags in v1. *(AD-20.)*
- Publication authenticates with the workflow's built-in `GITHUB_TOKEN` under `permissions: packages: write` and pushes to a **private** `ghcr.io` package; there is no stored registry credential to commit or rotate. *(Settled by architecture AD-20; OQ-1.)*

#### FR-31: CI is reasonably fast and cached

The pipeline caches Gradle dependencies and build outputs so routine runs stay short.

**Consequences (testable):**
- Gradle's dependency cache and build cache are restored between runs.
- A no-op-change PR pipeline completes within the SM-C1 budget.

### 4.10 Kubernetes runtime contract

**Description:** The Starter guarantees the image is deployable to Kubernetes with no image-side changes: non-root, env-configured, correct probe endpoints, honors SIGTERM. Charts and manifests are explicitly out of scope. Realizes UJ-1.

**Functional Requirements:**

#### FR-32: Image satisfies the Kubernetes runtime contract

The published image runs under Kubernetes without probe reconfiguration or image changes.

**Consequences (testable):**
- Liveness and readiness probes pointed at the framework-default paths (FR-19) succeed against the running image with no overrides.
- The container runs as non-root (FR-28) and reads all configuration from environment variables (FR-24).
- The process exits cleanly within the graceful-shutdown timeout on SIGTERM (FR-23).
- Verified by a documented local check (e.g. `docker run` with the probe endpoints curled and a SIGTERM timing check); an actual cluster deployment is the charts repo's concern.

### 4.11 Parameterization

**Description:** Turning the Starter into a Consumer Service means changing the Four Parameters and nothing else. Each has one home. A generator that automates this is explicitly deferred; v1 is a README checklist. Realizes UJ-1.

**Functional Requirements:**

#### FR-33: Exactly four parameters, each with one documented home

Service name, database name + credentials, HTTP port, and image name each have a single, documented location.

**Consequences (testable):**
- The README names, for each of the Four Parameters, the exact file(s) and key(s) that hold it.
- No parameter is duplicated across multiple files such that changing one place is insufficient or leaves an inconsistency. `[ASSUMPTION: where a value must appear twice (e.g. port in config and in a compose healthcheck), it is derived from one source or the README calls out both explicitly.]`
- The root `.env` file (FR-25) holds only the Postgres and Redis container image tags shared by Compose and Testcontainers; it is Plumbing, not a home for any of the Four Parameters.

#### FR-34: Changing only the four parameters yields a working service

After changing only the Four Parameters per the README, the service runs locally, passes CI, and publishes an image.

**Consequences (testable):**
- A walkthrough changing only those values produces a green `./gradlew build`, a successful local run against the Compose Stack, and (on `main`) a published image.
- No source code change is required to reach that state (the Example Slice may then be removed separately).

#### FR-35: Generator is out of scope, seam documented

v1 ships no rename/generator automation; the README notes it as deferred and lists the manual steps it would replace.

**Consequences (testable):**
- The README's parameter checklist is complete enough to be the spec for a future generator.
- No half-built generator script ships in v1.

### 4.12 Auth Seam

**Description:** Authentication is not implemented in v1, but the shape for it is present: a committed, inactive OAuth2 resource-server (JWT) configuration behind a profile/flag, plus documentation for enabling it and handling it in tests. This protects the Auth0 roadmap item. Realizes the roadmap, not a v1 journey.

**Functional Requirements:**

#### FR-36: Inactive OAuth2 resource-server configuration present

The repository contains an OAuth2 resource-server (JWT) security configuration that is not active in the default profile.

**Consequences (testable):**
- With default configuration, no authentication filter is enforced; all endpoints behave as they do today.
- Enabling the documented profile/flag activates JWT validation using issuer / audience / JWKS values taken from configuration.
- The inactive configuration compiles and is covered by at least a smoke test in its enabled mode. `[ASSUMPTION: a test profile mints/validates a token against a test issuer.]`

#### FR-37: Auth Seam documentation

The README explains how to turn the Auth Seam on: which flag, which configuration values, how Integration Tests authenticate, and how Actuator endpoints are treated.

**Consequences (testable):**
- The README section lists the exact configuration keys for issuer, audience, and JWKS URI.
- It states the v1 posture (seam only, nothing enforced) and the intended Auth0 direction (§5, addendum).
- Actuator exposes `health`, `info`, and `prometheus` only, on the main port. When the Auth Seam is enabled, `/actuator/health**` (including the liveness and readiness groups) stays unauthenticated so probes need no credentials; every other actuator endpoint, and the OpenAPI JSON endpoint (`/v3/api-docs`, FR-18), requires a valid token. A separate management port is deferred. *(Settled by architecture AD-17, AD-18, OQ-5; the `/v3/api-docs` rule is the PRD extension noted in FR-18.)*

### 4.13 Documentation

**Description:** A single README is the entry point for a human or an agent picking up a Consumer Service. It is short and task-oriented. Realizes UJ-1, UJ-2, UJ-3.

**Functional Requirements:**

#### FR-38: README covers the essential tasks

The README documents: the Four Parameters and where each lives; how to add a REST resource; the local development loop; how to enable the Auth Seam; and the observability endpoints.

**Consequences (testable):**
- Each of those five topics has a dedicated, findable section.
- The "add a REST resource" section is concrete enough that following it produces a working resource with passing tests (ties to FR-6).
- The README states what is deliberately out of v1 (generator, charts, messaging, auth enforcement, Vault) with a pointer to the brief/addendum.

## 5. Non-Goals (Explicit)

- **Not a framework or a library.** The Starter is copied, not depended on. There is no published artifact, no version, no upgrade channel in v1. A mechanism for downstream services to pull baseline improvements is a future idea, not this.
- **Not a deployment system.** No Helm charts, no Argo CD config, no Kubernetes manifests. The deliverable stops at a well-behaved image plus CI that builds, tests, and publishes it.
- **Not multi-anything.** Single module, single Postgres database, single Redis instance, single service. No multi-module build, no second datasource, no messaging (Kafka/RabbitMQ), no gRPC.
- **Not implementing authentication.** The Auth Seam is structure and documentation only. No identity provider integration, no token issuance, no enforced authorization in v1.
- **Not implementing secret management.** v1 externalizes all configuration so Vault can inject later; it does not integrate Vault, Vault Agent, CSI, or any secret store.
- **Not a service generator.** No rename task, no scaffolding CLI, no cookiecutter. Manual parameter change against a README checklist is the v1 mechanism, by choice.
- **Not supporting non-JVM or non-Spring stacks.**
- **Not a showcase of every Spring feature.** Only the Plumbing the stack actually needs, kept lean (SM-C2).

## 6. MVP Scope

### 6.1 In Scope

- Single-module Gradle (Kotlin DSL) build, Kotlin-only, latest stable Kotlin/Spring Boot/JVM LTS, version catalog, format+lint enforced (FR-1–FR-3).
- Four-Layer clean-architecture skeleton with a build-failing Boundary Test (FR-4–FR-6).
- Postgres via JPA/Hibernate + Flyway migrations, externalized config, proven by Integration Test (FR-7–FR-9).
- Redis cache-aside wiring (Redis a hard dependency — no degraded mode; readiness gates on it), externalized config, proven by Integration Test (FR-10–FR-12).
- One Example Slice (`widgets`, REST → Postgres → Redis) with Unit + Integration Tests, removable without breaking Plumbing (FR-13–FR-15).
- REST conventions: standard JSON error shape, a Business/Not-Found/System exception taxonomy funnelled through one Global Exception Handler emitting RFC 7807 Problem Details carrying fixed type-level Error Codes, request validation, served OpenAPI (FR-16–FR-18, FR-39–FR-40).
- Observability & runtime: health with liveness/readiness groups, Prometheus metrics, OTLP traces (no-op when unset), structured JSON logs with trace correlation, graceful shutdown, env-only configuration (FR-19–FR-24).
- Local development: Compose Stack (Postgres + Redis only), Local Profile, one run command, documented loop (FR-25–FR-27).
- Container image via `bootBuildImage` (non-root) and a GitHub Actions pipeline: full build+test on PRs, publish to Registry on `main` tagged SHA + `latest`, Testcontainers in CI, caching (FR-28–FR-31).
- Kubernetes runtime contract met by the image, verified locally (FR-32).
- Parameterization: Four Parameters each with one home, README checklist, generator explicitly deferred (FR-33–FR-35).
- Auth Seam: inactive OAuth2 resource-server config + documentation (FR-36–FR-37).
- README covering the five essential tasks (FR-38).

### 6.2 Out of Scope for MVP

- **Rename / generator automation** — deferred until a Consumer Service exists to template from. `[NOTE FOR PM: emotionally load-bearing for the "new service in minutes" vision; revisit right after the first Consumer Service ships.]`
- **Helm charts / Argo CD config** — separate repository.
- **Vault integration** — roadmap; v1 only keeps config externalized to enable it.
- **Auth0 / any real authentication** — roadmap; v1 only leaves the Auth Seam.
- **Messaging (Kafka/RabbitMQ), gRPC** — a Consumer Service adds these itself.
- **Multi-module build, second datasource, read replicas.**
- **Second static-analysis tool (detekt), mutation testing, coverage gates** — can be added later as config; not a v1 requirement.
- **Local OpenTelemetry collector / trace UI** — the Compose Stack is Postgres + Redis only; tracing is configuration-only locally. Add a collector plus a trace UI when someone needs to read a local trace.
- **Semver / release tagging of images** — v1 uses SHA + `latest` only.
- **A pull-through upgrade mechanism for existing Consumer Services** — future idea.

## 7. Success Metrics

Each SM cross-references the FR(s) it validates. Counter-metrics counterbalance specific primary metrics.

**Primary**

- **SM-1: Clone-to-green time.** Median wall-clock time for Alex to take a fresh clone to *running locally + green CI + published image*, changing only the Four Parameters, is **under 15 minutes**. Validates FR-25–FR-34.
- **SM-2: Zero-plumbing resource addition.** Adding a new REST resource by mirroring the Example Slice changes **zero Plumbing files** (build, config, observability, container, CI — verified by `git diff --name-only` touching only new resource files) and `./gradlew build` passes, with any failures on the way being transcription errors the developer corrects without a design or Plumbing change. Validates FR-4–FR-6, FR-13–FR-18.

**Secondary**

- **SM-3: Hermetic test suite.** The full Unit + Integration suite passes in CI with **zero external infrastructure** and no flakiness over 20 consecutive runs. Validates FR-9, FR-12, FR-29.
- **SM-4: Example Slice is removable.** Deleting the Example Slice (code, tests, migration) leaves `./gradlew build` green. Validates FR-15.
- **SM-5: Probes work unmodified.** The image passes liveness/readiness with the framework-default probe paths and no per-service reconfiguration. Validates FR-19, FR-32.
- **SM-6: Telemetry with no extra wiring.** A Prometheus scrape returns service metrics and, with an OTLP endpoint set, traces appear — both with no wiring beyond configuration values. Validates FR-20, FR-21.

**Counter-metrics (do not optimize)**

- **SM-C1: Starter's own CI time.** Keep the Starter's own pipeline **under ~10 minutes** for a routine change. Counterbalances SM-3 — do not chase hermeticity/coverage by letting the pipeline sprawl.
- **SM-C2: Dependency footprint.** Keep the direct dependency count lean and every dependency justified. Counterbalances SM-1/SM-2 — do not make onboarding or extension "easy" by pre-adding libraries a given Consumer Service may not want.
- **SM-C3: Files touched for the Four Parameters.** Keep the number of files a person edits for parameterization in **single digits**. Counterbalances SM-1 — do not spread configuration for flexibility's sake.

## 8. Questions Resolved in Architecture

All seven open questions were settled by the architecture spine (`_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md`). Recorded here for traceability; the resolutions are folded into the FRs above.

1. **OQ-1 — Registry** → a **private** package on `ghcr.io`. CI pushes with the workflow's built-in `GITHUB_TOKEN` under `permissions: packages: write` (no stored secret to rotate); the cluster pulls with an `imagePullSecret` owned by the charts repo. *(AD-20; FR-30, Glossary "Registry".)*
2. **OQ-2 — Redis-down behavior** → **hard dependency, fail fast.** Redis is a member of the readiness health group; the cache adapter has no try/catch and no degraded mode; a Redis failure at request time is a System Exception → 500. Overturns the earlier degrade-to-Postgres assumption. *(AD-13; FR-10, FR-12.)*
3. **OQ-3 — API docs surface** → springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`). OpenAPI JSON in every profile; Swagger UI only under the `local` profile. *(FR-18.)*
4. **OQ-4 — Local OTel collector** → **not included.** The Compose Stack is Postgres + Redis only; tracing stays configuration-only locally (OTLP endpoint unset → no-op). A collector plus trace UI is deferred (§6.2). Overturns the FR-25 assumption. *(AD-17; FR-21, FR-25.)*
5. **OQ-5 — Actuator exposure under auth** → actuator exposes `health`, `info`, `prometheus` only, on the main port. `/actuator/health**` stays unauthenticated when the Auth Seam is on; every other actuator endpoint requires a token. *(AD-17, AD-18; FR-37.)*
6. **OQ-6 — Boundary Test tool** → **ArchUnit 1.5.0** (not Konsist), using `layeredArchitecture()` plus explicit banned-import rules. *(AD-2; FR-5.)*
7. **OQ-7 — Integration Test separation** → **no separate Gradle source set.** One `src/test` source set; Integration Tests carry `@Tag("integration")` and extend one shared base class owning static Testcontainers Postgres + Redis (`@ServiceConnection`) per JVM. *(AD-21; FR-9, FR-29.)*

## 9. Assumptions Index

### 9.1 Still open — confirm during the build

- §4.1 FR-2 — Kotlin compiler runs `-Xjsr305=strict`; warnings-as-errors on for the Starter's own code.
- §4.4 FR-11 — cache-aside TTL default 10 minutes (architecture fixes only that it is a documented configuration value).
- §4.5 FR-15 — Example Slice removal verified via a documented step; no Plumbing compile dependency on `widgets`.
- §4.11 FR-33 — where a parameter must appear twice, it is derived from one source or the README flags both.
- §4.12 FR-36 — an enabled-mode smoke test mints/validates a token against a test issuer; whether most Integration Tests run with the filter disabled or always authenticated is a build-time call.
- §10 Startup time — target under ~10s to readiness on a typical CI/runtime container, measured on the documented local check.

### 9.2 Resolved by the architecture spine

Folded into the FRs above; listed here so the trail is visible.

- §3 Glossary / §4.9 FR-30 — Registry is a private `ghcr.io` package; CI pushes via the built-in `GITHUB_TOKEN`, cluster pulls via a charts-repo `imagePullSecret`. *(AD-20, OQ-1.)*
- §4.1 FR-1 — dependency versions live in a single `gradle/libs.versions.toml`, pinning only what the Spring Boot BOM does not manage. *(AD-22.)*
- §4.1 FR-3 — format/lint is Spotless (8.10.2) driving ktlint (1.5.0); no detekt in v1. *(AD-22, Stack.)*
- §4.2 FR-5 — the application Layer may use `org.springframework.stereotype` and `org.springframework.transaction.annotation` only; nothing else from Spring; component scanning wires use cases. Boundary Test is ArchUnit. *(AD-3, AD-2, OQ-6.)*
- §4.3 FR-8 / FR-9 — Hibernate `ddl-auto = validate`; Flyway is sole schema authority, checksum validation on, auto-repair off. *(AD-14.)*
- §4.4 FR-10 — Redis is a hard dependency: fail fast, readiness gates on Redis, no degraded mode, a failure is a System Exception → 500. *(AD-13, OQ-2.)*
- §4.5 FR-13 — REST is `/api/v1/{resource}` (plural lowercase); `POST` collection creates (201 + `Location`), `PUT /{id}` full update, `DELETE /{id}` returns 204. *(AD-9.)*
- §4.5 FR-13 — collection `GET` uses offset paging (`?page=`, `?size=`) with a `{items, page, size, totalElements, totalPages}` envelope and Starter-owned `PageRequest` / `PageResult` types; cursor paging deferred. *(AD-10.)*
- §4.7 FR-23 — graceful shutdown on, 30 s phase timeout (env-overridable). *(AD-17.)*
- §4.6 FR-18 — springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`); OpenAPI JSON in every profile, Swagger UI only under `local`. *(OQ-3.)*
- §4.6 FR-39 — `BusinessException` (400) / `NotFoundException` (404) / `SystemException` (500), each `message: String, details: Map<String, Any?> = emptyMap()` — no per-instance code, no central `ErrorCode` catalogue. *(AD-11; AD-12, the enum catalogue, is retired — 2026-09-09 update.)*
- §4.6 FR-40 — each exception type carries a fixed Error Code (`BUSINESS_ERROR`/`NOT_FOUND`/`SYSTEM_ERROR`) assigned by the handler; `type` is `about:blank`; `instance` is the request path; `detail` is the exception's own message; `details` rides as its own extension member; validation detail is a separate `errors` array of `{field, code, message}`. Message and details are echoed to the client for all three types, **including 500** — no server-log-only variant. *(AD-11 — 2026-09-09 update, overturns the original "500 never leaks detail" rule.)*
- §4.7 FR-21 — OTLP exporter is a no-op (not retrying localhost) when unconfigured, and is unconfigured by default; `management.tracing.sampling.probability` is a configuration value. *(AD-17.)*
- §4.7 FR-22 — Spring Boot built-in structured JSON logging in non-`local` profiles; `local` keeps console output. *(AD-17.)*
- §4.8 FR-25 — the Compose Stack is Postgres + Redis only; no OTel collector; image tags come from a root `.env` shared with Testcontainers. *(AD-17, AD-21, OQ-4.)*
- §4.9 FR-29 — CI runs on GitHub-hosted `ubuntu-latest` with Testcontainers and no workflow service containers. *(AD-20.)*
- §4.12 FR-37 — actuator exposes `health`, `info`, `prometheus` only; `/actuator/health**` stays open under the Auth Seam, other actuator endpoints require a token. *(AD-17, AD-18, OQ-5.)*
- §4.6 FR-18 — when the Auth Seam is enabled, `/v3/api-docs` requires a token (schema not served anonymously in production); open when the seam is disabled. *(PRD decision this update; extends the AD-18 matcher set — flag for the architecture step to absorb.)*
- §10 Determinism — no dynamic (`+`, `latest.release`) versions anywhere; the JVM is fixed by a Gradle toolchain. *(AD-22.)*
- §10 Resource footprint — heap sizing is left to the buildpack's container-aware calculator; the README documents the knob rather than fixing a number. *(AD-17.)*
- §11 Auth Seam fail-safe — `app.auth.enabled=true` without a resolvable `issuer-uri` fails context startup; disabled mode installs an explicit permit-all chain. *(AD-18.)*

## 10. Cross-Cutting NFRs

- **Hermetic tests.** No test — Unit or Integration — may require infrastructure the repo does not start itself. Integration Tests use Testcontainers; nothing reaches a shared or external database, cache, or network service. (Ties to SM-3.)
- **Startup time.** The service reaches readiness quickly enough to suit Kubernetes rolling deploys with modest probe delays. `[ASSUMPTION: target under ~10s to readiness on a typical CI/runtime container; confirm by measurement during the build.]`
- **Configuration.** 100% of deployment-varying settings come from environment / externalized config. No secret material in the image or the repo (excepting throwaway `application-local.yaml` credentials). This is a hard constraint, not a preference — it is what keeps the Vault roadmap cheap.
- **Determinism / reproducibility.** A given commit builds the same image contents given the same base image; dependency versions are locked via the catalog, with no dynamic (`+`, `latest.release`) versions anywhere and the JVM fixed by a Gradle toolchain. *(Architecture: AD-22.)*
- **Resource footprint.** v1 fixes no numeric memory/CPU ceiling: heap sizing is left to the buildpack's container-aware calculator and the README documents the knob (`JAVA_TOOL_OPTIONS` / `BPL_JVM_*`). Container resource *limits* are set by the charts repository, not the image, and are out of scope here (§11 Deployment boundary). The only Starter-side assertion is FR-32's: the image starts, answers probes, and honours SIGTERM under a modest container. *(Architecture: AD-17.)*
- **Agent legibility.** Structure, naming, and the Example Slice are consistent enough that an AI coding agent can place new code and tests correctly from the conventions alone; the Boundary Test is the backstop when it doesn't. (Ties to SM-2, UJ-3.)
- **Portability.** Everything (build, tests, image, local loop) works on Linux and macOS with only a JDK and Docker installed; no other host dependencies.
- **Observability parity.** Metrics, traces, and logs behave identically across `local`, CI, and production save for endpoint configuration and log formatting — no observability code path is production-only and therefore untested.

## 11. Constraints and Guardrails

- **Cost.** The Registry is a private `ghcr.io` package (§8, OQ-1) — no non-trivial cost for internal use. CI runs on hosted runners; keep minutes modest (SM-C1). No paid third-party services in the Plumbing.
- **Security.** Non-root container. No secrets in repo or image. 500 responses never leak internals (FR-16). The Auth Seam fails safe: `app.auth.enabled=true` without a resolvable `issuer-uri` fails context startup (it can never degrade to allow-all), and disabled mode installs an explicit permit-all chain so `spring-security` on the classpath does not silently secure everything. *(Architecture: AD-18.)*
- **Operational.** The image is the contract; probe paths, SIGTERM behavior, and env configuration are fixed points other repos (charts) depend on. Changing any of them is a breaking change to every Consumer Service and must be treated as such.
- **Deployment boundary.** The Starter delivers an image + CI. Cluster wiring (manifests, values, secrets, ingress) is the separate charts repository's responsibility and explicitly not this PRD's.

## 12. Downstream Depth (see `addendum.md`)

Technology-selection detail and rejected alternatives are captured in the companion `addendum.md`: version strategy, image-build mechanism trade-offs, Registry decision criteria, Boundary Test tooling, error-model choice, Testcontainers lifecycle, Auth Seam mechanics, and the Vault-readiness checklist. The architecture step is complete (`architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md`); each addendum section now carries a **Resolved** line pointing at the governing Architecture Decision, and the resolutions are folded into §4 and indexed in §9.2.
