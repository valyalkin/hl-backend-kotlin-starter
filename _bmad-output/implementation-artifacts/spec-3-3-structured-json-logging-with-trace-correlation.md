---
title: 'Structured JSON logging with trace correlation'
type: 'feature'
created: '2026-09-15'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '3d53f16d8d86b68888ad25a419f2f404b5f39d01'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Logs today use Boot's default human-readable console pattern on every profile: no machine-parseable shape, and no visible link to the trace/span id Story 3.2 already writes into SLF4J's MDC per active span, so a log aggregator can neither parse fields nor join a line to its distributed trace.

**Approach:** Turn on Spring Boot's built-in structured JSON console logging (`logging.structured.format.console: ecs`, Elastic Common Schema — the vendor-neutral choice for a starter template with no specified downstream log sink) for every profile except `local`, with zero application code and no encoder dependency or owned logback XML (AD-17, same discipline as Stories 3.1/3.2): each stdout line becomes single-line JSON carrying at minimum timestamp, level, logger, and message, and lines emitted while a request's span is active also carry that span's trace id and span id, sourced automatically from the MDC entries Story 3.2 already populates. `local` keeps its current human-readable console output.

## Boundaries & Constraints

**Always:**
- Structured logging is Spring Boot's built-in support only (`logging.structured.format.console`) — no logging encoder library dependency, no owned `logback.xml`/`logback-spring.xml`.
- Every profile except `local` emits structured JSON console output; `local` keeps human-readable console output.
- No new or changed log statements, levels, or MDC wiring — this story only changes how existing log output is encoded.

**Never:**
- No file appender or secondary log sink — stdout only, same as today.
- No custom `StructuredLogFormatter` class — pick one of Boot's built-in common format ids only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Default (non-local) profile, any log line | Service running | Line is valid single-line JSON with timestamp, level, logger, message | N/A |
| Log line inside an active request span | Inbound request causes a log line while its span is active | That line also carries the trace id and span id | N/A |
| `local` profile | `local` profile active | Console output stays human-readable, not JSON | N/A |

</frozen-after-approval>

## Code Map

- `src/main/resources/application.yaml:49-77` -- existing `management:` block; add a new top-level `logging:` block with `logging.structured.format.console: ecs`.
- `src/main/resources/application-local.yaml` -- override to keep human-readable console output under `local`; confirm empirically the correct way to override a base-profile `logging.structured.format.console` (mirrors Stories 3.1/3.2's decompile-to-confirm approach for uncertain Boot behavior).
- `src/test/resources/application.yaml:1-42` -- mirror the main profile's `logging.structured.format.console` key (AD-21 parity, same pattern as Stories 3.1/3.2).
- `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt:93,105` -- existing `log.error(...)` calls (SYSTEM_ERROR/UNEXPECTED_ERROR paths), the only guaranteed server-side log line during a real request today; reused by the new IT to observe a JSON line with trace/span id populated. Story 3.2's `Slf4JEventListener` already writes `MDC["traceId"]`/`MDC["spanId"]` per active span — this story adds no MDC wiring, only changes console encoding.
- `src/test/kotlin/com/hl/service/RedisDownIT.kt` -- existing pattern for triggering a real 500 SYSTEM_ERROR over HTTP; reference for the new IT's request trigger.
- `README.md:121-156` (`### Tracing`) -- add a sibling `### Logs` subsection, same terse style.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/resources/application.yaml` -- add `logging.structured.format.console: ecs` -- turns on JSON structured console logging for every non-local profile.
- [x] `src/main/resources/application-local.yaml` -- explicitly keep human-readable console output for `local` -- confirm and use the correct override mechanism.
- [x] `src/test/resources/application.yaml` -- mirror the main profile's structured-format key.
- [x] `src/test/kotlin/com/hl/service/StructuredLoggingIT.kt` (new; single class matching filename) -- extends `IntegrationTestBase`; captures stdout around a request that triggers `GlobalExceptionHandler`'s SYSTEM_ERROR log path (mirrors `RedisDownIT`'s trigger); asserts (a) at least one captured line parses as JSON with timestamp/level/logger/message present, (b) the line(s) emitted for that request also carry the trace id and span id.
- [x] `src/test/kotlin/com/hl/service/StructuredLoggingLocalProfileIT.kt` (new; added during Matrix Test Audit -- see Implementation Notes) -- extends `IntegrationTestBase` under `@ActiveProfiles("local")`; same request trigger, asserts none of the captured lines parse as JSON.
- [x] `README.md` -- add `### Logs` documenting the `ecs` format choice and the `local` profile's human-readable exception.

**Acceptance Criteria:**
- Given the default (non-local) profile, when the service logs anything, then each stdout line is valid single-line JSON with at least timestamp, level, logger, and message.
- Given an inbound request that produces a log line while its span is active, when that line is emitted, then it also carries the trace id and span id.
- Given the `local` profile, when the service logs, then console output stays human-readable, not JSON.

## Implementation Notes

Implemented `logging.structured.format.console: ecs` in `application.yaml`,
overridden back to an empty value (`''`) in `application-local.yaml` --
confirmed by decompiling Boot 4.1.1's `DefaultLogbackConfiguration` that a
blank resolved value, not an omitted key, is what triggers the fallback to
the plain `PatternLayoutEncoder`, since the `local` profile file layers on
top of the base file rather than replacing it. Verified live: `ecs` output
carries `@timestamp` top-level, `log.level`/`log.logger` nested under `log`,
and -- because the ECS formatter spreads every MDC entry as its own
top-level field -- `traceId`/`spanId` appear top-level with no extra wiring,
confirming both Design Notes questions.

`StructuredLoggingIT` triggers the existing SYSTEM_ERROR log path (same
Redis-unreachable trigger as `RedisDownIT`, adapted via a
`@Primary`-overridden `DataRedisConnectionDetails` bean scoped to its own
Spring context so the shared Testcontainers singleton is never stopped) and
captures the real bytes Logback's `CONSOLE` appender writes.

Matrix Test Audit (step-03) found the I/O matrix's `local` profile row had
no automated test -- only manual `bootRun`+`curl` inspection covered it.
Added `StructuredLoggingLocalProfileIT` (`@ActiveProfiles("local")`, same
trigger/capture technique, asserting no captured line parses as JSON) and
made `StructuredLoggingIT.TeeOutputStream` non-private so both classes share
one implementation. Full suite re-run: 66/66 tests pass, 0 failures/errors;
`./gradlew build` green including `spotlessCheck`.

## Spec Change Log

## Review Triage Log

- **low / patch** — `StructuredLoggingLocalProfileIT` reached into `StructuredLoggingIT.TeeOutputStream`/`.BrokenRedisConnectionConfig` (nested types of another test class) instead of a shared support location, and both classes independently duplicated an identical `consoleAppender()` private helper (blind-hunter x2, same root cause: reusable test helpers left in the wrong place). Extracted `consoleAppender()`/`TeeOutputStream` to `support/ConsoleAppenderCapture.kt` and `BrokenRedisConnectionConfig` to `support/BrokenRedisConnectionConfig.kt` (mirrors the existing `FakeWidgetRepository.kt` extraction precedent in the same package); both IT classes now import from there.
- **low / patch** — `consoleAppender()`'s `rootLogger.getAppender("CONSOLE") as OutputStreamAppender<ILoggingEvent>` would throw an opaque NPE/ClassCastException instead of a diagnosable message if the appender were ever missing or a different type (edge-case-hunter, both call sites). Folded into the same extraction above: added a `check(appender is OutputStreamAppender<*>)` guard with a message naming the assumption, fixed once for both callers.
- **low / patch** — `StructuredLoggingIT`'s `spanId` assertion only checked `isNotBlank()`, which a formatter regression emitting a constant placeholder on every line would still satisfy (blind-hunter). Verified real OTel span id shape empirically (`docker compose up`, `./gradlew bootRun`, a forced Redis-down 500): 16-character lowercase hex, distinct in length from the 32-character `traceId`. Strengthened to `.matches("[0-9a-f]{16}")`.
- **low / patch** — README's new `### Logs` section had no example output, unlike `### Metrics`'s runnable `curl` command (blind-hunter). Added a trimmed real captured JSON line (from the same manual verification above) and an explicit "run `./gradlew bootRun` and check your terminal" pointer for the `local`-profile case.
- **low / patch** — `src/test/resources/application.yaml`'s new mirroring comment cited "AD-21" for the test/main `application.yaml`-mirroring convention (verification-gap); AD-21 (`ARCHITECTURE-SPINE.md`) is actually "one test source set; containers are shared by a base class," unrelated to config mirroring — none of the three prior mirrored properties (Story 3.1/3.2) cite an AD number for this convention either. This story's own spec Code Map introduced the mismatched citation, copied verbatim into the comment; corrected the comment to match the sibling comments' style (no AD citation). Spec Code Map wording left as-is (historical planning record, per prior stories' precedent of not retroactively editing it).
- **medium / defer** — Production `src/main/resources/application.yaml`'s `logging.structured.format.console: ecs` is only ever exercised indirectly: `StructuredLoggingIT` runs against `src/test/resources/application.yaml`'s own independent `console: ecs` copy, since Spring resolves `classpath:/application.yaml` to one resource and the test file fully shadows the main one (verification-gap, pre-verified). Breaking the main file's value alone would silently revert production console output to human-readable while `./gradlew build` stayed green. Filed as `defer`, not `patch`: this is the same pre-existing, repo-wide shadow-copy pattern every earlier mirrored property (Story 3.1/3.2's tracing/actuator/cache-name keys) already carries — a real fix means a module-wide config-parity check, out of scope for this story alone.
- **false / reject** — `sprint-status.yaml` shows `3-3: in-progress` while the spec itself is `in-review` with every task checked and a green build, called "inconsistent with the file's documented workflow" (blind-hunter). Not a defect: the sprint tracker only advances to `review` at step-05 (`Mark Spec Done`), which runs after this review passes — Stories 3.1/3.2 show the identical, expected mid-review state.
- **false / reject** — Only `GlobalExceptionHandler`'s SYSTEM_ERROR log line is exercised by an automated test; claimed a formatter bug could affect non-error-level lines differently and go undetected (blind-hunter). Refuted: `logging.structured.format.console` configures one encoder for the whole `CONSOLE` appender, shared by every `ILoggingEvent` regardless of level (confirmed against Boot 4.1.1's structured-logging internals during implementation) — there is no level-conditional formatting path a single passing line could fail to exercise.
- **false / reject** — YAML comments citing decompiled "Boot 4.1.1" internals as evidence don't flag themselves as needing re-verification on the next Boot upgrade (blind-hunter). No established convention in this codebase adds such disclaimers to version-specific empirical comments (Stories 3.1/3.2's own decompile-sourced comments don't either); vague, no named per-instance harm beyond generic future risk.
- **low / reject** — `BrokenRedisConnectionConfig`'s ephemeral-port technique (open-then-close a `ServerSocket(0)`) has a theoretical time-of-check/time-of-use race if the OS reissues the port before Lettuce connects (edge-case-hunter; self-flagged by the implementer too). A standard, widely-used test pattern; did not reproduce across dozens of runs. A real fix means redesigning the failure trigger entirely (e.g., a bind-and-hold approach, or a non-routable target IP that trades instant "connection refused" for a slow TCP timeout) — more than a direct correction, and unlikely to bite in everyday use.

All patch entries applied directly (no step-03 subagent session to re-engage — this review ran standalone in a resumed session after a rate-limit interruption). Full suite re-run after all patches: 66/66 tests pass, 0 failures/errors; `./gradlew build` green including `spotlessCheck`.

## Design Notes

Two facts need empirical confirmation during implementation (mirrors Stories 3.1/3.2's precedent):
1. The correct way to override/disable `logging.structured.format.console` for the `local` profile — an empty value, an explicit "off"-style value, or something else — since `application-local.yaml` layers on top of the base `application.yaml` rather than replacing it.
2. Exactly how the chosen format surfaces MDC's `traceId`/`spanId` in the JSON output (field names/location) — needed to write a precise assertion in `StructuredLoggingIT`.

## Verification

**Commands:**
- `./gradlew build` -- expected: full suite passes including the new `StructuredLoggingIT`; format/lint passes.
- `./gradlew bootRun` (default profile) then curl a widget endpoint, inspect stdout -- expected: single-line JSON log output.
- `./gradlew bootRun -Dspring.profiles.active=local` then curl a widget endpoint, inspect stdout -- expected: human-readable console output, not JSON.
