---
title: "PRD Addendum: Spring Boot Kotlin Starter Service"
status: final
created: 2026-09-07
updated: 2026-09-07
---

# PRD Addendum

Depth captured during PRD discovery that belongs downstream — architecture and solution design — rather than in the PRD itself. The PRD states *what* capabilities the Starter must have; this addendum records *how* candidates and trade-offs stood at PRD time.

> **Status:** the architecture spine (`_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md`, 2026-09-07) has since resolved the questions this addendum framed. Each section below carries a **Resolved** line pointing at the governing Architecture Decision (AD). The addendum is kept for the rationale trail; the AD is authoritative where they differ.

## 1. Version strategy

- **Decision:** use the latest *stable* release of each of Kotlin, Spring Boot, and the JVM LTS, plus current stable Gradle, as of the architecture step. No numbers are pinned in the PRD.
- **Architecture step must:** pin exact versions in `gradle/libs.versions.toml` and the wrapper; record the JVM LTS chosen (e.g. the current LTS) and the Spring Boot line; confirm Kotlin/Spring Boot compatibility; note the Kotlin plugin(s) needed (`kotlin("plugin.spring")`, `kotlin("plugin.jpa")`, serialization if used).
- **Upgrade posture:** version catalog is the single edit point. No dynamic (`+`, `latest.release`) versions anywhere — reproducibility is a Cross-Cutting NFR.
- **Resolved (architecture — Stack table, AD-22):** JDK 25 LTS (Gradle toolchain), Kotlin 2.4.0, Spring Boot 4.1.1, Gradle 9.7.1, springdoc-openapi 3.1.1, ArchUnit 1.5.0, Spotless 8.10.2 / ktlint 1.5.0. The catalog pins only what the Spring Boot BOM does not manage; Flyway, Testcontainers, the Postgres driver, Micrometer, Lettuce and Logback are inherited from the BOM without version literals.

## 2. Image build mechanism

- **Decision:** Spring Boot Gradle plugin `bootBuildImage` (Cloud Native Buildpacks / Paketo). No Dockerfile in the repo.
- **Rationale:** no Dockerfile to maintain or drift; layered images and sensible non-root defaults out of the box; one less thing each Consumer Service can get subtly wrong.
- **Alternatives considered:**
  - *Jib* — no Docker daemon needed, fast; but less standard in the Spring ecosystem and another plugin to reason about.
  - *Hand-written Dockerfile* — maximum control, maximum drift surface; rejected for a template whose whole point is that the plumbing is already right.
- **Architecture step must:** decide the base builder/run image and how it is pinned; confirm non-root uid/gid; decide whether the image build runs in CI on every PR or only on `main` (PRD requires publish only on `main`; building on PRs is optional and a speed trade-off — see SM-C1).
- **Resolved (architecture — AD-19, AD-20):** builder is `paketobuildpacks/builder-noble-java-tiny` pinned to an explicit tag (never floating — reproducibility NFR). `BP_JVM_CDS_ENABLED` and the AOT cache stay **off** in v1: an open Paketo defect (`paketo-buildpacks/spring-boot#581`) fails the build on Java 25 + Boot 4. The image builds **only on `main`**, never on PRs; the PR pipeline is format check + ArchUnit + Unit + Integration Tests. Heap is left to the buildpack's container-aware calculator; the README documents `JAVA_TOOL_OPTIONS` / `BPL_JVM_*`.

## 3. Registry (Open Question OQ-1)

- **Tentative:** GitHub Container Registry (`ghcr.io`).
- **Decision criteria:**
  1. Effectively free for internal/private use at this scale.
  2. The self-hosted Kubernetes cluster can pull with minimal friction — ideally a single `imagePullSecret` (a token with `read:packages`) referenced by the charts repo, or a public package if that is acceptable.
  3. Auth from GitHub Actions is simple (`GITHUB_TOKEN` can push to `ghcr.io` for the same repo/org with `packages: write`).
- **Fallbacks:** Docker Hub (rate limits on pulls are the risk), or a small self-hosted registry (more to run and secure).
- **Architecture / setup step must:** confirm the choice, document the exact CI auth (token, permissions block) and the exact cluster-pull mechanism, and reflect the result in FR-30 and the Glossary.
- **Resolved (architecture — AD-20):** a **private** `ghcr.io` package. CI pushes with the workflow's built-in `GITHUB_TOKEN` under `permissions: packages: write` — no stored secret to rotate. The self-hosted cluster pulls with a single `imagePullSecret` built from a `read:packages` PAT, owned and referenced by the separate charts repository.

## 4. Boundary Test tooling (Open Question OQ-6)

- **Requirement (in PRD):** an automated, build-failing check of the Boundary Rules.
- **Candidates:**
  - *ArchUnit* — mature, JVM-standard, expressive rule DSL, runs as a normal JUnit test. Kotlin works but rules are written in a Java-ish style.
  - *Konsist* — Kotlin-native, reads naturally in a Kotlin codebase, younger and smaller community.
- **Architecture step must:** pick one, define the concrete rule set (the PRD's FR-5 consequences are the starting list), and decide packaging (its own `test` vs a dedicated source set / task).
- **Resolved (architecture — AD-2, AD-3):** ArchUnit 1.5.0. `layeredArchitecture()` maps 1:1 onto the layer-first package roots, plus explicit `noClasses().that().resideIn(..)` banned-import rules. Run as a normal JUnit test in the single `src/test` source set (no dedicated source set — AD-21). Konsist rejected as younger with a smaller community and no offsetting advantage against `layeredArchitecture()`. The application layer is allowed exactly `org.springframework.stereotype` and `org.springframework.transaction.annotation`; everything else Spring/Jakarta/Hibernate is banned inward of the adapters.

## 5. REST error model

- **Assumption in PRD:** RFC 7807 `application/problem+json`, one schema for all 4xx/5xx.
- **Notes for architecture:** Spring has first-class `ProblemDetail` support; decide the exact fields (type URI scheme, `instance`, custom extension members for field-level validation errors), and a single `@RestControllerAdvice` as the one handler. 500 bodies must be generic (no stack traces, no exception messages that leak internals).
- **Exception taxonomy (FR-39):** `BusinessException` (4xx) and `SystemException` (5xx) are the two roots, living in the domain Layer and therefore free of Spring/Jakarta/HTTP types. Architecture decides: the exact package; whether the Error Code catalogue is an enum or a sealed hierarchy; how interpolation parameters are carried; and where the code → HTTP status map lives. Since the Boundary Test forbids HTTP types in domain, that map belongs in the Global Exception Handler (adapter-in) unless the domain expresses a status-free classification the handler translates.
- **Carrying the code in the body (FR-40):** the Error Code rides as a `ProblemDetail` extension member (`properties["code"]`) rather than being encoded in the `type` URI. Architecture decides the exact member name, the `type` URI scheme (a stable doc URI per code vs. `about:blank`), and whether `instance` carries the request path or the trace id. The code — not the status, not `detail` — is the client's branching contract.
- **Validation detail shape:** field-level Bean Validation errors are an extension member of the same Problem Detail (e.g. an array of `{field, code, message}`), not a second error schema. Decide the member name and whether field-level entries carry their own Error Codes.
- **No message localization in v1:** "ISO" in the requirement refers to the standardized error *structure* (RFC 7807 `problem+json`), not localized message bundles. `detail` is a plain human-readable string; there is no `MessageSource` / `Accept-Language` resolution. Because clients branch on the Error Code, adding localization later is additive and does not break the contract.
- **Resolved (architecture — AD-11; 2026-09-09 update, AD-12 retired):** `type` = `about:blank`; `instance` = the request path; extension members are `code` (a fixed literal per exception type — `BUSINESS_ERROR`/`NOT_FOUND`/`SYSTEM_ERROR`), `traceId`, `details` (the exception's own map, omitted when empty), and — for validation failures — `errors`, an array of `{field, code, message}`. No `ErrorCode` enum: three exception types, `BusinessException` (400) / `NotFoundException` (404) / `SystemException` (500), each with exactly one constructor shape (`message: String`, `details: Map<String, Any?> = emptyMap()`). `message`/`details` are echoed to the client for all three types, including 500 — a deliberate, explicit product call, not the original "never leak internal detail" default. Per-code documentation `type` URIs are deferred, unaffected by this change.

## 6. Testcontainers lifecycle (Open Question OQ-7)

- **Goals:** hermetic, fast, no flakiness (SM-3), and Consumer Services add a resource's Integration Test with *no new infrastructure code* (FR-6).
- **Options to weigh:** singleton containers started once per JVM and shared (fast, needs care with test isolation / data cleanup) vs. Spring Boot's `@ServiceConnection` + `@Testcontainers` per slice (cleaner wiring, potentially slower). Container reuse (`testcontainers.reuse.enable`) for local runs only.
- **Also decide:** whether Integration Tests are a separate Gradle source set (`integrationTest`) with its own task, or a JUnit `@Tag` on one suite; how they report in CI; and how the Compose Stack image versions are kept in lockstep with the Testcontainers image versions (single properties source).
- **Resolved (architecture — AD-21):** one `src/test` source set, no `integrationTest` set. Integration Tests carry `@Tag("integration")`; a single abstract base class owns static Postgres + Redis containers annotated `@ServiceConnection`, started once per JVM and shared. Isolation is per-test data cleanup, not per-test containers. Postgres and Redis image tags live once in a root `.env` that both Docker Compose and the Gradle build (feeding Testcontainers) read, keeping local and CI versions in lockstep. Unit Tests use hand-written in-memory fakes for ports — no mocking framework is a dependency.

## 7. Auth Seam mechanics (roadmap: Auth0)

Carried forward from the product brief addendum, with PRD-level detail:

- **Likely shape:** Spring Security OAuth2 resource server validating JWTs issued by Auth0.
- **Config keys to expose (documented, unused by default):** issuer URI, expected audience, JWKS URI.
- **Activation:** a profile or a boolean flag; default profile leaves *no* `SecurityFilterChain` enforcing auth. Enabling without valid issuer/audience must fail safe — refuse to start or reject all requests, never allow-all (PRD §11).
- **Tests when enabled:** a test profile that mints tokens against a local/test issuer (e.g. a static JWKS or an embedded authorization server), so Integration Tests can run authenticated. Decide: disable the filter for most Integration Tests vs. always run authenticated with a helper that attaches a token.
- **Actuator (Open Question OQ-5):** proposed — liveness/readiness stay open (probes must not need credentials); everything else requires a token when the seam is on.
- **Per-service config:** audience/issuer are per Consumer Service; note whether that becomes a fifth parameter when auth is adopted (out of scope now).
- **Resolved (architecture — AD-18):** a single `app.auth.enabled` property (default `false`) selects one of two `SecurityFilterChain` beans. Disabled installs an **explicit** permit-all chain (so `spring-security` on the classpath does not silently secure everything with a generated password). Enabled requires a valid JWT on `/api/**`, leaves `/actuator/health**` open, and requires a token for every other actuator endpoint. Enabled without a resolvable `spring.security.oauth2.resourceserver.jwt.issuer-uri` fails context startup — it can never degrade to allow-all. Actuator exposure is `health`, `info`, `prometheus` only, on the main port (AD-17).

## 8. Vault-readiness checklist (roadmap: Vault)

The PRD's FR-24 and §10 Configuration NFR exist to keep this cheap. For the future Vault step:

- All secret-bearing settings are already environment variables / externalized config, so a Vault Agent / sidecar / CSI driver can supply them with no code change.
- `application-local.yaml` is the only place with literal (throwaway) credentials; every other environment is env-driven.
- Architecture should note the intended injection mechanism and how `local` diverges from Vault-backed environments, so the later step is wiring only.

## 9. Deployment context (unchanged from brief)

- Services deploy to a self-hosted Kubernetes cluster (Alex's server).
- GitOps: Helm charts in a **separate charts repository**, synced by Argo CD.
- The Starter's responsibility ends at: a well-behaved image (probes, env config, graceful shutdown, non-root) + CI that builds, tests, and publishes it. Manifests and values are the charts repo's concern.

## 10. Deferred: service generator

- v1 is a manual README checklist for the Four Parameters (FR-33–FR-35).
- The checklist is intentionally written to be complete enough to later become the generator's spec.
- Candidate forms when revisited (after the first Consumer Service exists): a Gradle `init`/rename task, a shell script, or a cookiecutter-style generator. Not decided.
