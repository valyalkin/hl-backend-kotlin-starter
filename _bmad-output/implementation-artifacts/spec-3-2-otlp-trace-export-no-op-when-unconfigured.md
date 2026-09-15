---
title: 'OTLP trace export, no-op when unconfigured'
type: 'feature'
created: '2026-09-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '46db545301fce91a2e5c9857ea2d3e44fa05c3aa'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The service exports no distributed traces today — there is no Micrometer Tracing/OTLP wiring — so a production OTLP collector has nothing to receive, and if wiring existed naively it could retry against a default `localhost` endpoint and spam connection errors in every local/CI run where nothing is listening.

**Approach:** Add Micrometer Tracing's OTel bridge and an OTLP exporter (BOM-managed, AD-22 style) driven entirely by `management.otlp.tracing.endpoint` (unset by default → no-op) and `management.tracing.sampling.probability` (documented default `1.0`), so spans for an inbound request and its outbound cache calls export automatically once an endpoint is configured, with zero application code. JDBC/DB spans are out of scope: Spring Boot ships no built-in JDBC tracing instrumentation, and the only way to add one is a non-BOM-managed third-party dependency, which conflicts with this spec's BOM-managed-only constraint (renegotiated 2026-09-14; see Spec Change Log).

## Boundaries & Constraints

**Always:**
- Endpoint and sampling are configuration values only (`management.otlp.tracing.endpoint`, `management.tracing.sampling.probability`) — no `@Configuration` class, custom `SpanExporter`, or tracing Kotlin code.
- `management.otlp.tracing.endpoint` stays unset in every checked-in profile (main, test, local) — no-op by omission, matching FR-21's "unset by default."
- `management.tracing.sampling.probability` defaults to `1.0`, is environment-overridable, and is documented in README.
- New dependencies are added the BOM-managed way (bare coordinates in `build.gradle.kts` with an AD-22 comment); no entries in `gradle/libs.versions.toml`.
- `src/test/resources/application.yaml` mirrors whatever is added to `src/main/resources/application.yaml`.

**Never:**
- No Compose Stack changes — no OTel collector or trace viewer container (explicit epic non-goal).
- No separate management port; no readiness/liveness health-group changes.
- No code-level `Tracer`/MDC wiring unless empirical investigation proves Spring Boot's built-in structured logging doesn't already populate trace/span ids automatically.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| No endpoint configured (default) | Service starts, serves requests | No exporter/connection-refused entries in logs; exporter is a genuine no-op | N/A |
| Endpoint configured | Inbound request triggers an outbound cache call | Spans for the request and the outbound cache call are exported to that endpoint | N/A |
| Sampling default inspected | `application.yaml` files | `management.tracing.sampling.probability` present, documented default `1.0` | N/A |

</frozen-after-approval>

## Code Map

- `build.gradle.kts:38-42` -- actuator/`micrometer-registry-prometheus` block (Story 3.1); add tracing/OTLP deps beside it, following the Flyway autoconfig-module split pattern at `build.gradle.kts:52-56` (AD-22): `io.micrometer:micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`, plus Boot 4's split autoconfig module (likely `org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry`) — confirm the exact module set empirically once resolved, as Story 3.1 did for cache-names.
- `src/main/resources/application.yaml:49-64` -- existing `management:` block; add `management.tracing.sampling.probability: 1.0`, leave `management.otlp.tracing.endpoint` unset.
- `src/test/resources/application.yaml:21-34` -- mirror the sampling key; endpoint stays unset.
- `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt:176` -- `currentTraceId()` reads `MDC.get("traceId")` today (per spec-2-3's note, populated only once tracing exists); verify empirically whether the OTel bridge + Boot's built-in structured logging populate this key automatically — if not, this file needs the minimal fix, recorded in Implementation Notes.
- `src/test/kotlin/com/hl/service/PrometheusMetricsIT.kt` -- style reference (RestTestClient + `IntegrationTestBase`) for the new IT below.
- `README.md:103-119` (`### Metrics`) and `:138-146` (`### View API docs`, existing Epic-3 forward-pointer) -- add a sibling `### Tracing` subsection, same terse style, reconciling the existing forward-pointer sentence.

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add OTel bridge + OTLP exporter + Boot tracing autoconfig module(s), BOM-managed with an AD-22 comment -- gives the app a real OTLP span exporter to configure.
- [x] `src/main/resources/application.yaml` -- add `management.tracing.sampling.probability: 1.0`, leave endpoint unset -- documents the sampling default and keeps export off by default.
- [x] `src/test/resources/application.yaml` -- mirror the sampling key.
- [x] `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt` -- fix only if MDC population isn't automatic (see Code Map) -- keeps the existing `traceId` Problem Detail field working. **No change made**: empirically confirmed unnecessary (see Implementation Notes).
- [x] `src/test/kotlin/com/hl/service/OtlpTracingNoOpIT.kt` and `OtlpTracingExportIT.kt` (new; split into two files during review to match the project's one-class-per-file convention -- see Review Triage Log) -- extend `IntegrationTestBase`; assert (a) default profile serves a request with no exporter-related WARNING/SEVERE log lines, (b) with a local stub HTTP receiver as the configured endpoint, a request triggering DB+cache calls results in span data POSTed to that receiver.
- [x] `README.md` -- add `### Tracing` documenting the endpoint/sampling knobs and no-op-by-default behavior; reconcile the existing forward-pointer near line 143.

**Acceptance Criteria:**
- Given no OTLP endpoint configured, when the service starts and serves requests, then no exporter/connection errors appear in logs.
- Given an OTLP endpoint configured, when an inbound request triggers an outbound cache call, then spans for the request and that call are exported to that endpoint. (DB/JDBC spans are out of scope — no built-in Boot instrumentation exists; see Spec Change Log.)
- Given the configuration files, when inspected, then `management.tracing.sampling.probability` is present with documented default `1.0` in both main and test `application.yaml`.

## Implementation Notes

Resolved both Design Notes questions empirically by decompiling the actual
resolved jars (`spring-boot-micrometer-tracing-opentelemetry-4.1.1.jar`,
`micrometer-tracing-bridge-otel-1.7.1.jar`, the OTLP HTTP sender jar) and by
running the new `OtlpTracingIT` against a real stub receiver:

1. **The Code Map's assumed property name is wrong; a working key exists
   under a different name.** Boot 4.1.1's OTLP tracing auto-configuration
   (`OtlpTracingAutoConfiguration` in `spring-boot-micrometer-tracing-opentelemetry`)
   binds `OtlpTracingProperties` to prefix
   `management.opentelemetry.tracing.export.otlp`, not
   `management.otlp.tracing` as the frozen Intent's "Always" bullet and the
   Code Map state. `management.otlp.tracing.endpoint` (the name in the spec
   text) is a Boot-3-era key now carrying an **error-level** deprecation
   (`additional-spring-configuration-metadata.json`,
   `replacement: management.opentelemetry.tracing.export.otlp.endpoint`,
   `since: 4.0.0`) with no `@ConfigurationProperties` class bound to it any
   more -- setting it would silently bind to nothing and never enable
   export, permanently failing AC2 while looking correct at a glance. Used
   the real, working key (`management.opentelemetry.tracing.export.otlp.endpoint`)
   everywhere: `application.yaml` (left unset), README, and `OtlpTracingIT`'s
   `@DynamicPropertySource`. This does not touch the frozen Intent text
   itself (human-owned); flagging the discrepancy here per the Design Notes'
   own instruction to confirm empirically. `management.tracing.sampling.probability`
   was NOT affected -- confirmed unchanged, still bound by
   `spring-boot-micrometer-tracing`'s `TracingProperties`, framework default
   `0.1` (this repo overrides to the spec's documented `1.0`).

   Also confirmed the no-op mechanics precisely: `OtlpTracingConnectionDetails`
   (the bean that supplies the exporter's target URL) only exists via
   `@ConditionalOnProperty("management.opentelemetry.tracing.export.otlp.endpoint")`;
   the exporter bean config (`OtlpTracingConfigurations$Exporters`) requires
   `@ConditionalOnBean(OtlpTracingConnectionDetails)`. With the property
   unset, no `OtlpHttpSpanExporter` bean is created at all -- not a
   default-`localhost` exporter that then fails to connect. So "unset alone"
   does satisfy the no-op AC; no extra `management.tracing.export.otlp.enabled=false`-style
   guard is needed, matching the frozen Boundaries' "no-op by omission."

2. **MDC population is automatic; `GlobalExceptionHandler` needs no fix.**
   `OpenTelemetryTracingAutoConfiguration` unconditionally registers a
   `io.micrometer.tracing.otel.bridge.Slf4JEventListener` bean (`@Bean`,
   `@ConditionalOnMissingBean` only) whenever the OTel bridge is on the
   classpath. Decompiled that class directly: it calls
   `MDC.put("traceId", ...)` / `MDC.put("spanId", ...)` on span
   start/scope-restore -- the exact key `GlobalExceptionHandler.currentTraceId()`
   already reads (`MDC.get("traceId")`). No application code change needed;
   left the file untouched, matching the "Never" boundary's default (no
   Tracer/MDC wiring) rather than the fallback fix path.

3. **Dependency set, confirmed against the resolved Boot BOM**:
   `io.micrometer:micrometer-tracing-bridge-otel`,
   `io.opentelemetry:opentelemetry-exporter-otlp`, and
   `org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry` --
   exactly the Code Map's guess, verified by fetching
   `spring-boot-dependencies-4.1.1.pom` and the individual module POMs
   (`spring-boot-starter-opentelemetry`'s own dependency list matches this
   trio plus transitives). Deliberately did NOT add the `spring-boot-starter-opentelemetry`
   starter itself: it additionally pulls `spring-boot-starter-micrometer-metrics`
   and `io.micrometer:micrometer-registry-otlp` (an OTLP *metrics* exporter),
   which is out of this spec's scope (trace export only) and would be a
   second, unrequested no-op-by-omission surface to reason about.

4. **The frozen Intent's "DB ... calls export automatically" claim does not
   hold as literally stated -- flagging, not fixing.** Ran `OtlpTracingExportIT`
   against a real stub receiver and inspected the raw captured OTLP/HTTP
   payload byte-for-byte: it contains an HTTP server span
   (`http post /api/v1/widgets`, `http get /api/v1/widgets/{id}`) and several
   Redis command spans (`db.system=redis`, `db.operation=GET/SET/CLIENT/HELLO`,
   `peer.service=Redis`) from Lettuce's own Micrometer Tracing integration --
   both genuinely automatic, zero application code, confirming the cache half
   of the claim. No Postgres/JDBC span of any kind appears anywhere in the
   payload, despite the same request round-tripping through
   `WidgetRepository`/Hikari/Postgres on its first (cache-miss) read. Spring
   Boot ships no built-in JDBC/Hibernate-level tracing instrumentation --
   that requires a separate, unrequested third-party library (e.g.
   `datasource-micrometer-spring-boot-starter`) and, per the Boundaries, an
   explicit `@ConfigurationProperties`/wiring decision outside this spec's
   listed Code Map. Left this un-added: adding it would be scope creep past
   the frozen Approach's "zero application code" via configuration alone,
   and past the specific dependency trio the Code Map/Design Notes named.
   `OtlpTracingIT` asserts only what's empirically true (HTTP span + Redis
   cache-call span present); it does not assert a DB-specific span. AC2's
   wording ("DB and cache calls... spans... exported") is satisfied only
   partially as literally written -- this is a real product-scope gap for a
   human to decide on (add a JDBC tracing dependency in a follow-up story,
   or narrow the AC/Intent), not something resolved silently here.

Full suite re-run after all changes: 63/63 tests pass (0 failures/errors),
`./gradlew build` green including `spotlessCheck`.

Matrix Test Audit (step-03) found the I/O matrix's "Sampling default inspected"
row had no automated test -- only config presence (`application.yaml`) and
README prose asserted the documented `1.0` default, with nothing verifying it
actually binds at runtime. Added `management tracing sampling probability
binds to its documented default of 1_0` to `OtlpTracingNoOpIT` (`@Value`-injects
`management.tracing.sampling.probability` and asserts `1.0`). Full suite
re-run: 64/64 tests pass, 0 failures/errors.

## Spec Change Log

- Implemented the OTLP endpoint property as `management.opentelemetry.tracing.export.otlp.endpoint`
  instead of the frozen Intent/Code Map's `management.otlp.tracing.endpoint`:
  the latter is an error-level-deprecated Boot 3 key that does not bind to
  anything in the resolved Boot 4.1.1 dependency set (see Implementation
  Notes #1). Frozen Intent/Boundaries text left unmodified per
  human-ownership; this log entry is the renegotiation record. Every
  non-frozen file (Code Map's own guidance aside, `application.yaml`,
  README, `OtlpTracingIT`) uses the corrected, working key.
- Did not modify `GlobalExceptionHandler.kt`: Design Note #2 resolved to "MDC
  population is automatic" (see Implementation Notes #2), so the Code Map's
  conditional fix path was not needed.
- `OtlpTracingIT`'s "DB and cache calls" assertion covers only the Redis
  cache-call span, not a Postgres/JDBC span -- see Implementation Notes #4
  for why DB-level spans are not currently automatic and were left
  unaddressed rather than papered over with an unverified assertion.
- **Human renegotiation (2026-09-14):** the frozen Intent/Boundaries/I-O
  Matrix originally required DB-call spans alongside cache-call spans.
  Implementation Notes #4 found Spring Boot 4.1.1 ships no built-in JDBC
  tracing instrumentation, and the only way to add it is a non-BOM-managed
  third-party dependency (e.g. `datasource-micrometer-spring-boot-starter`),
  which directly conflicts with this spec's frozen "BOM-managed way, no
  `gradle/libs.versions.toml` entries" constraint. Presented to the human as
  a choice; decided to narrow scope to cache-call spans only rather than
  relax the BOM-managed constraint. Amended the frozen Approach, the I/O
  Matrix's "Endpoint configured" row, and the matching Acceptance Criterion
  to drop the DB-span requirement. Known-bad state this avoids: claiming AC2
  fully satisfied while a DB-only request (no cache hit or miss) would
  silently export zero DB spans, contradicting the original AC. Deferred DB
  span support as a follow-up story in `deferred-work.md`. KEEP: the
  cache-call span assertion in `OtlpTracingExportIT` and the no-op AC1
  finding both stand as originally implemented -- unaffected by this
  amendment.

## Review Triage Log

- **medium / patch** — `RedisDownIT`'s `traceId` assertion only checked `.exists()`, which is `true` even when the field is `""` (`GlobalExceptionHandler.respond()` sets `traceId` unconditionally, empty or not) (verification-gap). This is the only integration-level test that observes `traceId` through a real, fully-booted Spring context and a genuine error path; if the OTel bridge/MDC wiring this story adds ever silently regressed (autoconfiguration-exclusion typo, a future Boot renaming the MDC key, bean not registered), `currentTraceId()` would revert to always `""` and no test would catch it — `GlobalExceptionHandlerTest` boots no Spring context and can't observe the real path, and neither `OtlpTracingNoOpIT` nor `OtlpTracingExportIT` inspects the `traceId` response field. Undermines this story's own promoted README claim that `traceId` "is populated automatically." Trivial fix: `.exists()` → `.isNotEmpty()` (verified against `spring-test`'s `AbstractJsonPathAssertions` — no new dependency).
- **low / patch** — `OtlpTracingIT.kt` defined two top-level classes (`OtlpTracingNoOpIT`, `OtlpTracingExportIT`) in one file matching neither class name (blind-hunter). Every other Integration Test in the module follows a strict one-file/one-matching-class convention (`PrometheusMetricsIT.kt`→`PrometheusMetricsIT`, `LivenessProbeIT.kt`→`LivenessProbeIT`, etc. — confirmed by listing the package); ktlint doesn't catch it because both classes are top-level. Split into `OtlpTracingNoOpIT.kt`/`OtlpTracingExportIT.kt`, each matching its class name; class docs cross-reference each other for the pair's shared design rationale.
- **low / patch** — `OtlpTracingExportIT`'s "outbound Redis cache call" assertion checked only `db.system`+`redis`, which Lettuce also stamps on connection-handshake spans (`CLIENT`/`HELLO`) per this story's own Implementation Notes #4 — so the assertion would pass from mere Redis connectivity, not proof of the cache-aside `GET`/`SET` the AC actually asks for (edge-case-hunter, same underlying gap independently flagged by blind-hunter). Added a `.contains("SET")` check: the cache-miss read this test drives populates Redis via `SET` (Story 2.7), a token that appears nowhere else in this payload (only POST/GET HTTP methods are used), so it ties the assertion to an actual data command rather than a handshake.
- **low / patch** — `OtlpTracingNoOpIT`'s WARNING/SEVERE filter matched broad keywords (`"span"`, `"export"`, `"refused"`, etc.) against any JUL record's message or logger name (edge-case-hunter). An unrelated WARNING/SEVERE record from some other JUL-based component coincidentally containing one of these generic terms would fail this test for reasons unconnected to trace export. Verified the real target logger empirically (decompiled `opentelemetry-exporter-sender-okhttp-1.62.0.jar`'s `OkHttpHttpSender.class`: `Logger.getLogger(...)` on a name under `io.opentelemetry.*`); narrowed the filter to `loggerName.startsWith("io.opentelemetry")`, a direct correction with no new complexity.
- **false / reject** — Frozen Intent's "Always" bullet and Code Map both name `management.otlp.tracing.endpoint`, which this story's own Implementation Notes #1 prove is a dead, error-level-deprecated Boot 3 key — a reader following the Code Map's or Verification section's literal commands would configure a property that binds to nothing (blind-hunter x2, edge-case-hunter). Real inconsistency, but every proposed fix is an edit to this spec's own text (frozen Intent is human-owned and was already explicitly renegotiated in the Spec Change Log below; Code Map/Verification are this build's spec prose) — out of this review's scope by the process rule against spec-editing fixes. Not re-routed to `deferred-work.md` either: tracking "fix a stale property name in a workflow artifact" there would just relocate the same excluded fix, not address a product/code gap. The actual shipped code (`application.yaml`, README, the IT's `@DynamicPropertySource`) all use the correct, working key throughout — verified directly.
- **false / reject** — Spec's `Design Notes` section still reads as open pre-implementation questions despite `Implementation Notes` #1/#2 already answering both (blind-hunter). Real editorial staleness, but the only fix is an edit to this spec's own text — out of scope by the same rule.
- **false / reject** — README's sampling-default line states this repo's `1.0` default without also stating Spring Boot's own out-of-the-box default of `0.1` (blind-hunter). The line is scoped with "in this repo," which already signals a repo-specific override; no concrete harm demonstrated beyond a vague "could be clearer" with no named consequence.
- **low / reject** — Sampling-probability test asserts the checked-in default binds at runtime; a `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` env var override in some environment would break it (edge-case-hunter). Verified nothing in this repo (`.env`, compose files, or any CI workflow — none exists yet) sets this var; the test is exactly what the spec's own Matrix Test Audit called for. Guarding against a hypothetical future override would add a branch/condition for state not demonstrated to occur — more than a direct correction — and is unlikely to bite in everyday use.
- **low / reject** — `OtlpTracingNoOpIT`'s JUL handler has no positive/negative control proving it actually captures records; if attachment silently failed, the "no WARNING/SEVERE" assertion would trivially and vacuously pass (blind-hunter). Real in principle, but `Logger.getLogger("").addHandler(...)` is bog-standard JDK API with no plausible silent-failure mode here, so the likelihood in everyday use is negligible; a real positive control would need its own test deliberately forcing an export failure (e.g., an unreachable endpoint), which is meaningfully more than a direct correction.

All patch entries applied directly (no step-03 subagent session to re-engage — this review ran standalone against an already-in-review spec). Full suite re-run after all patches: 64/64 tests pass, 0 failures/errors; `./gradlew build` green including `spotlessCheck`.

## Design Notes

Two facts need empirical confirmation during implementation before the Code Map's dependency/MDC details are treated as final (mirrors Story 3.1's cache-names precedent):
1. Whether Boot 4.1.1's OTLP tracing autoconfiguration only creates an `OtlpHttpSpanExporter` bean when `management.otlp.tracing.endpoint` is explicitly set, or defaults to `http://localhost:4318/v1/traces` regardless — decides whether "unset" alone satisfies the no-op AC, or an extra guard (e.g. `management.tracing.enabled=false` by default) is also needed.
2. Whether trace/span ids reach `MDC.get("traceId")` automatically once the OTel bridge is on the classpath, or need an explicit, still configuration-only, wiring step.

## Verification

**Commands:**
- `./gradlew build` -- expected: full suite passes including the new `OtlpTracingIT`; format/lint passes.
- `./gradlew bootRun` (default profile) then curl a widget endpoint, inspect stdout -- expected: no OTLP export/connection-refused log lines.
- `./gradlew bootRun -Dmanagement.otlp.tracing.endpoint=http://localhost:<stub-port>/v1/traces` then curl a widget endpoint -- expected: stub receives a POST containing span data.
