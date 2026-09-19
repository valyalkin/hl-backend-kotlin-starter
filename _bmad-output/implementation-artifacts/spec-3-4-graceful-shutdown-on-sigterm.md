---
title: 'Graceful shutdown on SIGTERM'
type: 'feature'
created: '2026-09-16'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'ffbe94fb0aefa3d40673f612f2aaa8d690e8748e'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The service today shuts down abruptly (Boot's default `server.shutdown=immediate`): on SIGTERM it stops accepting connections and tears down in-flight requests at once, so a rolling deploy or pod eviction can drop a request mid-flight.

**Approach:** Turn on Spring Boot's built-in graceful shutdown (`server.shutdown: graceful`) with an explicit, environment-overridable `spring.lifecycle.timeout-per-shutdown-phase` (documented default 30s), zero application code (AD-17, same discipline as Stories 3.1-3.3): on SIGTERM the embedded server stops accepting new requests, lets in-flight requests finish within the timeout, then the process exits. Real SIGTERM timing against the built image is verified later by Epic 4's documented local Kubernetes-contract check, not by this story.

## Boundaries & Constraints

**Always:**
- Graceful shutdown is Spring Boot's built-in support only (`server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase`) -- no custom `SmartLifecycle`, shutdown hook, or other shutdown Kotlin code.
- The phase timeout has a documented default of 30s and is overridable via an environment variable, same pattern as the existing `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` config value.
- Applies on every profile (including `local`) -- no profile-specific override.

**Never:**
- No new REST endpoint or business logic added to production code purely to make shutdown behavior testable.
- No automated test claims to reproduce a real OS SIGTERM; that end-to-end signal-timing verification is Epic 4's documented local Kubernetes-contract check (`docker run` + curl probes + SIGTERM timing) against the built image, not this story's.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Runtime configuration inspected | Service running, any profile | `server.shutdown` resolves to `graceful`; `spring.lifecycle.timeout-per-shutdown-phase` resolves to `30s` unless overridden | N/A |
| Timeout overridden | `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` env var set | Effective timeout matches the override, not the 30s default | N/A |
| Context shutdown while a request is in flight | `ApplicationContext.close()` invoked mid-request | New requests stop being accepted; the in-flight request completes and returns its normal response before the process exits | N/A |

</frozen-after-approval>

## Code Map

- `src/main/resources/application.yaml` -- add a top-level `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: ${SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE:30s}`, mirroring the existing `management.tracing.sampling.probability` pattern (explicit value equal to the framework default, for discoverability + override).
- `src/test/resources/application.yaml` -- mirror the same two keys (existing mirroring convention, e.g. `logging.structured.format.console`, `management.tracing.sampling.probability`).
- `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt` -- reuse as the base for the new IT; do not modify.
- `src/main/kotlin/com/hl/service/repository/WidgetRepository.kt` -- interface to delegate-and-slow-down in a test-only `@Primary` bean (see Design Notes), the same interface-override technique `support/FakeWidgetRepository.kt` already uses for unit tests, applied here via Kotlin interface delegation instead.
- `src/test/kotlin/com/hl/service/LivenessProbeIT.kt` -- reference for the `RANDOM_PORT` + `RestTestClient` + `IntegrationTestBase` pattern this story's new IT reuses.
- `src/test/kotlin/com/hl/service/support/BrokenRedisConnectionConfig.kt` -- reference for the `@Primary`-bean-scoped-to-one-test-context technique (Story 3.3 precedent) this story's slow-repository config follows.
- `README.md:158-181` (`### Logs`) -- add a sibling `### Shutdown` subsection after it, same terse style as `Metrics`/`Tracing`/`Logs`.
- `src/test/kotlin/com/hl/service/OtlpTracingNoOpIT.kt` -- reference for the `@Value`-bound property-default assertion pattern (Matrix Test Audit addition, see Implementation Notes).
- `src/test/kotlin/com/hl/service/OtlpTracingExportIT.kt` -- reference for the `@DynamicPropertySource` technique giving a scenario its own Spring context (Matrix Test Audit addition).

## Tasks & Acceptance

**Execution:**
- [x] `src/main/resources/application.yaml` -- add `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: ${SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE:30s}` -- enables graceful drain with a documented, overridable timeout.
- [x] `src/test/resources/application.yaml` -- mirror the same two keys.
- [x] `src/test/kotlin/com/hl/service/support/SlowWidgetRepositoryConfig.kt` (new) -- `@TestConfiguration` providing a `@Primary` `WidgetRepository` bean that delegates to the real one (Kotlin `by` delegation) but sleeps before `findById` returns, so a `GET /api/v1/widgets/{id}` request can be held in flight on demand.
- [x] `src/test/kotlin/com/hl/service/GracefulShutdownIT.kt` (new) -- extends `IntegrationTestBase`, `webEnvironment = RANDOM_PORT`, imports `SlowWidgetRepositoryConfig`; fires a slow `GET` on a background thread, calls `context.close()` once the request is in flight, and asserts (a) the in-flight request still completes with its normal response, (b) a new connection attempt made after `close()` starts is rejected rather than served.
- [x] `README.md` -- add `### Shutdown` documenting `server.shutdown: graceful`, the 30s default, and its env-var override, same style as the sibling sections.
- [x] `src/test/kotlin/com/hl/service/GracefulShutdownConfigIT.kt` (new; added during Matrix Test Audit -- see Implementation Notes) -- `@Value`-binds `server.shutdown`/`spring.lifecycle.timeout-per-shutdown-phase` on the shared default context and asserts `graceful`/`30s`.
- [x] `src/test/kotlin/com/hl/service/GracefulShutdownTimeoutOverrideIT.kt` (new; added during Matrix Test Audit) -- `@DynamicPropertySource` sets `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=5s` in its own context and asserts the bound `Duration` reflects the override, not the 30s default.

**Acceptance Criteria:**
- Given the runtime configuration, when inspected, then `server.shutdown=graceful` is set and `spring.lifecycle.timeout-per-shutdown-phase` has a documented default of 30s, overridable by environment variable.
- Given a request is in flight, when the application context is closed (the same lifecycle event a real SIGTERM triggers), then it stops accepting new requests, the in-flight request completes within the timeout, and the process then exits.

## Implementation Notes

Implemented `server.shutdown: graceful` and
`spring.lifecycle.timeout-per-shutdown-phase: ${SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE:30s}`
in `application.yaml`, mirrored in `src/test/resources/application.yaml`.
Both Design Notes questions confirmed empirically:

1. The Kotlin interface-delegation `SlowWidgetRepository` (`by delegate`,
   `Thread.sleep` override on `findById`, registered `@Primary` via
   `SlowWidgetRepositoryConfig`) reliably holds a real HTTP request in
   flight -- a 1s sleep against a several-second test timeout did not flake
   across repeated runs. A `CountDownLatch` (`findByIdEntered`) that counts
   down the instant `findById` is entered lets the test wait for the request
   to be genuinely in flight server-side rather than guessing with a fixed
   delay before calling `context.close()`.
2. New-connection rejection is asserted with a raw `java.net.Socket` connect
   attempt (200ms connect timeout) polled after `context.close()` starts on
   its own thread: the embedded Tomcat connector stops accepting new
   connections immediately once graceful shutdown begins, well before the
   in-flight request's sleep elapses or the shutdown phase completes.

A real, unanticipated obstacle surfaced during implementation:
`GracefulShutdownIT` manually calling the real `ApplicationContext.close()`
mid-test (as the I/O matrix specifies) conflicts with Spring Framework 7's
`@SpringBootTest` context-caching machinery. Several of Spring's default
`TestExecutionListener`s (`EventPublishingTestExecutionListener`,
`CommonCachesTestExecutionListener`, `MockitoResetTestExecutionListener`, and
Boot's `MockMvcPrintOnlyOnFailureTestExecutionListener`/`WebDriverTestExecutionListener`)
each call `TestContext.getApplicationContext()` during teardown to publish
lifecycle events or reset state; once the context is genuinely closed by the
test itself this throws (`IllegalStateException`), failing the test even
though every actual assertion in the test body had already passed (confirmed
by inspecting captured log output: `Commencing graceful shutdown...` /
`Graceful shutdown complete` bracketing exactly the sleep duration, proving
correct drain behavior). Fixed by narrowing `GracefulShutdownIT`'s own
`@TestExecutionListeners` to `REPLACE_DEFAULTS` with only the listeners that
never touch the context post-test (`DirtiesContextTestExecutionListener`
included -- it only calls `markApplicationContextDirty(...)`, never
`getApplicationContext()`), plus `@DirtiesContext` so the closed context is
correctly evicted from Spring's test cache afterward. A first attempt at
fixing this via a build-wide `spring.test.context.cache.pause=never` JVM
property (disabling Spring 7's cache-pause/restart cycle entirely) also
"fixed" `GracefulShutdownIT` but broke `StructuredLoggingIT`/
`StructuredLoggingLocalProfileIT` (Story 3.3): those two tests turned out to
implicitly depend on that same pause/restart cycle to re-run Boot's
JVM-wide-singleton `LoggingSystem` initialization between the default and
`local` profiles' different `logging.structured.format.console` values.
Reverted that global property; the scoped `@TestExecutionListeners` fix on
`GracefulShutdownIT` alone is sufficient and leaves every other test
untouched. `./gradlew build` (including `GracefulShutdownIT` and
`spotlessCheck`) reran green multiple times, including from-scratch
(`--rerun-tasks`) runs, with no flakes observed; 67/67 tests pass.

Matrix Test Audit (step-03) found the I/O matrix's first two rows --
"runtime configuration inspected" (the `graceful`/`30s` values themselves)
and "timeout overridden" -- had no automated coverage: `GracefulShutdownIT`
only proves the third row's drain *behavior*, never asserts the property
values or override path directly. Added `GracefulShutdownConfigIT`
(`@Value`-bound assertion on the shared default context, mirrors
`OtlpTracingNoOpIT`'s sampling-probability-default test) and
`GracefulShutdownTimeoutOverrideIT` (`@DynamicPropertySource` sets
`SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=5s` in its own context, mirrors
`OtlpTracingExportIT`'s technique). Full suite re-run: 69/69 tests pass, 0
failures/errors; `./gradlew build` green including `spotlessCheck`.

## Spec Change Log

## Review Triage Log

- **low / patch** — `application.yaml`'s new `timeout-per-shutdown-phase` comment says it follows "the same pattern as MANAGEMENT_TRACING_SAMPLING_PROBABILITY **above**" (blind-hunter), but that property is defined later in the file (~line 88), below the new shutdown block (lines 1-15) — verified by reading the file. Fixed the word to "below".
- **false / reject** — "No custom SmartLifecycle/shutdown-hook code -- this one property is the entire change" claimed inaccurate since two properties (`server.shutdown`, `spring.lifecycle.timeout-per-shutdown-phase`) were added (blind-hunter). Refuted: that sentence sits in the comment block directly above `shutdown: graceful` only, correctly describing that one property as Boot's actual behavioral switch; the timeout key has its own separate comment block ("spelled out here for discoverability") that makes no such claim.
- **medium / patch** — `GracefulShutdownIT`'s `requestThread` and `closeThread` (`kotlin.concurrent.thread { ... }`) capture no exception; if `context.close()` throws, the thread simply dies and `closeThread.isAlive` still becomes `false`, so `assertThat(closeThread.isAlive).isFalse()` passes even though the close path actually failed -- undermining the one test whose entire purpose is proving clean shutdown (blind-hunter + edge-case-hunter, same root cause on both threads). Verified: no assertion anywhere checks that `context.close()` (or the request) completed without throwing. Added a captured `Throwable?` per thread, asserted null after `join()`.
- **low / patch** — `objectMapper.readTree(createResult.responseBody)` accesses the (nullable) response body with no null-safety, unlike `OtlpTracingExportIT`/`OtlpTracingNoOpIT`'s explicit `?: error(...)` pattern for the same call shape (edge-case-hunter) -- a null body would surface as an unexplained NPE instead of a diagnosable message. Added the same `?: error(...)` guard.
- **low / reject** — No automated test proves `spring.lifecycle.timeout-per-shutdown-phase` actually cuts off a request that *exceeds* it -- only the "completes within the timeout" side is covered (blind-hunter). Real gap, but the enforcement mechanism itself is Spring Boot's own long-established `WebServerGracefulShutdownLifecycle`/Tomcat internals, not code this story wrote; our diff's only risk surface (the two config values) is already covered by `GracefulShutdownConfigIT`/`GracefulShutdownTimeoutOverrideIT`. A proper cutoff test needs new test infrastructure (a dedicated short-timeout context plus a request sleeping past it) -- more than a direct correction -- so rejected per the low-finding policy (unlikely in practice + non-trivial fix).
- **low / patch** — The README's new `### Shutdown` section doesn't mention how this interacts with the readiness probe during the drain window (blind-hunter). Verified by decompiling `spring-boot-web-server-4.1.1.jar`: `org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle` (a `SmartLifecycle`, only registered when `server.shutdown=graceful`) publishes `AvailabilityChangeEvent(REFUSING_TRAFFIC)` in its `stop()` -- confirming the readiness probe flips to DOWN automatically once shutdown begins, with zero extra wiring. Added one sentence to the README section.
- **false / reject** — `sprint-status.yaml` shows as modified but isn't part of the reviewed diff (blind-hunter). Not a defect: `{diff_file}` deliberately excludes `_bmad-output` (BMAD workflow bookkeeping/story-tracking state, not part of the story's code changeset) per this workflow's own diff-scoping, same as every prior story's review.
- **low / patch** — `GracefulShutdownIT.canConnect()`'s 200ms-timeout socket poll only proves the TCP accept queue stops; a connection accepted in the narrow race window right as `close()` starts isn't separately verified as handled cleanly (blind-hunter). Added a doc-comment note acknowledging and accepting this as a bounded, inherent race in the test's own design, consistent with how `BrokenRedisConnectionConfig`'s doc comment (Story 3.3) already accepts a similar TOCTOU risk in its own ephemeral-port technique.
- **low / patch** — The spec's `## Verification` manual-check command has no documented expected log output to check against (blind-hunter), unlike Implementation Notes' captured `Commencing graceful shutdown...`/`Graceful shutdown complete` lines. Added those expected lines to the Verification section itself.

## Design Notes

Two facts need empirical confirmation during implementation (mirrors Stories 3.1-3.3's precedent):
1. Whether a Kotlin interface-delegation (`class SlowWidgetRepository(private val delegate: WidgetRepository) : WidgetRepository by delegate`) with a `Thread.sleep` override on `findById`, registered `@Primary` in a nested `@TestConfiguration`, reliably holds a real HTTP request in flight long enough to call `context.close()` against it without flaking under normal CI timing (e.g. a few hundred ms sleep vs. a several-second test timeout).
2. The precise way to assert "new requests are rejected" after `close()` starts but before the JVM exits -- e.g. a raw `java.net.Socket` connect attempt failing, vs. `RestTestClient` throwing a connection-refused exception -- since the embedded server's listening socket lifecycle during the graceful phase isn't otherwise documented.

If empirical confirmation shows this technique is flaky or unreliable, fall back to asserting AC1 (config values) only via a straightforward property-binding test, and note in Implementation Notes that AC2's real drain behavior is verified by Epic 4's documented local Kubernetes-contract check instead -- this is consistent with `epic-3-context.md`'s own Cross-Story Dependencies, which already assigns real SIGTERM timing verification to that later check.

## Verification

**Commands:**
- `./gradlew build` -- expected: full suite passes including the new `GracefulShutdownIT`; format/lint passes.
- `./gradlew bootRun` then `kill -TERM <pid>` while curling a widget endpoint -- expected: the in-flight curl completes, the process exits within ~30s, and no new connections are accepted after the signal. Console log should bracket the drain with `Commencing graceful shutdown. Waiting for active requests to complete` then, once the request finishes, `Graceful shutdown complete`.
