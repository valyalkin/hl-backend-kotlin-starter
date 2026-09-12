---
title: 'RFC 7807 error contract and the Global Exception Handler'
type: 'feature'
created: '2026-09-12'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '50104395b1aa6fb9c7c1fdb29d4a5cc09ea66b40'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing in the service yet renders errors consistently — `error/` and `controller/` are empty placeholders, so every future 4xx/5xx (business, not-found, system, or unanticipated) has no shared shape to land in, and Stories 2.5/2.6 have nothing to delegate to.

**Approach:** Add a sealed `AppException` family (`BusinessException`/`NotFoundException`/`SystemException`) in `error/`, and one `@RestControllerAdvice` `GlobalExceptionHandler` in `controller/` that renders every exception it sees as an RFC 7807 `application/problem+json` body with `code`/`traceId`/`details` extension members.

## Boundaries & Constraints

**Always:**
- Sealed `AppException` base plus `BusinessException` (→400), `NotFoundException` (→404), `SystemException` (→500) in `error/`, each with exactly one constructor shape: `message: String, details: Map<String, Any?> = emptyMap()`.
- Exactly one `@RestControllerAdvice` (`GlobalExceptionHandler`, in `controller/`) produces every error body. `type` = `about:blank`, `instance` = request path, `detail` = the exception's own `message`. Extension members: `code` (fixed literal per type — `BUSINESS_ERROR`/`NOT_FOUND`/`SYSTEM_ERROR`/`UNEXPECTED_ERROR` for any unrecognized exception — assigned by the handler, never carried by the exception), `traceId`, `details` (omitted when empty).
- `SystemException`'s message and details are echoed to the client as-is (no scrubbed variant), and every `SystemException` is also logged server-side at ERROR level with the same trace id.
- All handled responses carry `Content-Type: application/problem+json`.
- `traceId` is read via `MDC.get("traceId")`, defaulting to `""` when absent. No new `Filter`/interceptor or correlation-id generator is added in this story — no tracing dependency exists yet (Epic 3/Story 3.2 adds Micrometer Tracing + OTLP export and will populate this same MDC key; until then every error response's `traceId` is an empty string). *(Decision, resolved 2026-09-12.)*

**Never:**
- No `ErrorCode`/`ErrorKind` catalogue (AD-12 retired) and no per-instance `code` parameter on any exception.
- No Bean Validation (`MethodArgumentNotValidException`) handler or `errors` extension member — that is Story 2.6's scope, which needs `WidgetRequest` to exist first. Do not add any DTO or `WidgetRequest` here.
- No `WidgetController` or other business endpoint — this story only adds `error/`, `controller/GlobalExceptionHandler.kt`, and its test, using a minimal test-only controller.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Business rule violation | Endpoint throws `BusinessException("bad input")` | 400, `application/problem+json`, `code=BUSINESS_ERROR`, `detail="bad input"` | N/A |
| Missing resource | Endpoint throws `NotFoundException("widget missing")` | 404, `code=NOT_FOUND` | N/A |
| System failure | Endpoint throws `SystemException("db down", mapOf("cause" to "timeout"))` | 500, `code=SYSTEM_ERROR`, body echoes `detail`+`details`; server log at ERROR carries the same `traceId` | N/A |
| Unanticipated exception | Endpoint throws a plain `IllegalStateException("boom")` | 500, `code=UNEXPECTED_ERROR`, `detail` = the exception's message | N/A |

</frozen-after-approval>

## Code Map

- `src/main/kotlin/com/hl/service/error/AppException.kt` -- new; sealed base + the three subtypes (AD-11). No file exists here yet, only `.gitkeep`.
- `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt` -- new; `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` (a base-class choice for a future story to build on -- `handleExceptionInternal` is not overridden here, so framework-raised MVC exceptions do not yet get `code`/`traceId`/`details`, per FR-40's "framework" clause) with `@ExceptionHandler`s for the three `AppException` subtypes plus a catch-all `Exception::class` → 500/`UNEXPECTED_ERROR`. A private helper builds each `ProblemDetail` (`instance`, `code`, `traceId` from `MDC.get("traceId") ?: ""`, `details`).
- `src/main/kotlin/com/hl/service/error/.gitkeep`, `src/main/kotlin/com/hl/service/controller/.gitkeep` -- delete once each package has a real file.
- `src/test/kotlin/com/hl/service/controller/GlobalExceptionHandlerTest.kt` -- new; a minimal test-only `@RestController` (one endpoint per exception kind) wired via `MockMvcBuilders.standaloneSetup(...)` — no Spring context, no `@WebMvcTest` slice, matching the existing unit-test style (`WidgetEntityTest.kt`) and NFR-1's hermetic-test rule.
- `build.gradle.kts` -- `spring-boot-starter-webmvc-test` (MockMvc) is already a `testImplementation`; no dependency change needed.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/kotlin/com/hl/service/error/AppException.kt` -- add sealed `AppException` + `BusinessException`/`NotFoundException`/`SystemException` -- the fixed exception types AD-11 requires
- [x] `src/main/kotlin/com/hl/service/error/.gitkeep` -- delete -- package now has real content
- [x] `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt` -- add the `@RestControllerAdvice` with handlers for the three types plus the catch-all -- the sole error-body producer (AD-11)
- [x] `src/main/kotlin/com/hl/service/controller/.gitkeep` -- delete -- package now has real content
- [x] `src/test/kotlin/com/hl/service/controller/GlobalExceptionHandlerTest.kt` -- add the minimal test controller plus 4 tests (one per I/O Matrix row) asserting status, `Content-Type`, and `code` -- proves the handler end to end

**Acceptance Criteria:**
- Given the `error/` package, when inspected, then a sealed `AppException` base and the three subtypes exist, each with the single `message`/`details` constructor shape, and there is no `ErrorCode` catalogue.
- Given `GlobalExceptionHandler`, when any of the four exception kinds reaches it, then the response is `application/problem+json` with `type=about:blank`, `instance` = the request path, `detail` = the exception's message, and the AC-specified `code`.
- Given a `SystemException`, when the handler renders it, then the client receives its message and details, and the exception is also logged at ERROR level with the same `traceId`.
- Given the handler's tests, when they run, then they cover Business, Not-Found, System, and an unanticipated exception, each asserting status, `Content-Type`, and `code`.

## Implementation Notes

- Naming the SLF4J logger field `logger` collided with a same-named protected field already declared on `ResponseEntityExceptionHandler`'s superclass, tripping a Kotlin/Java interop bug (KT-56386) as a compile error. Renamed it to `log`.
- Verified: `./gradlew build` -- BUILD SUCCESSFUL, spotless/ktlint pass, all test classes green including the 4 new `GlobalExceptionHandlerTest` cases (confirmed via `build/test-results/test/TEST-com.hl.service.controller.GlobalExceptionHandlerTest.xml`, `tests="4" failures="0" errors="0"`); the `SystemException` case's log line (`ERROR ... System failure (traceId=): db down`) confirms the server-side ERROR log with trace id.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| Tests never assert `$.type`, `$.instance`, `$.traceId`, or the absence of `$.details` on empty-details responses, though these are explicit AC/contract fields (blind-hunter + verification-gap) | medium | Confirmed: all 4 `GlobalExceptionHandlerTest` cases assert only status/`Content-Type`/`code`(/`detail`); none reference `$.type`, `$.instance`, `$.traceId`, or `$.details` absence — a regression in any of those fields would pass CI undetected. | patch |
| `handleUnexpected` never logs server-side, unlike `handleSystem` (blind-hunter) | medium | Confirmed: `handleUnexpected` has no log call; a genuinely unanticipated 500 (the case operators most need to diagnose) leaves zero server-side trace, while a deliberately-thrown `SystemException` does. | patch |
| The `GlobalExceptionHandler` doc comment and the spec's Design Notes overstate that extending `ResponseEntityExceptionHandler` gets framework MVC exceptions the same `code`/`traceId`/`details` treatment "structurally" (blind-hunter + edge-case-hunter + verification-gap, same root cause) | medium | Confirmed: the class overrides none of `ResponseEntityExceptionHandler`'s handler methods (`handleExceptionInternal`, `createProblemDetail`, etc.); Spring's `ExceptionHandlerMethodResolver` prefers the superclass's more-specific inherited `@ExceptionHandler`s for framework exceptions over the local broad `Exception::class` handler, so those responses get a bare `ProblemDetail` with none of this story's extension members — the claim doesn't match runtime behavior. | patch |
| Exceptions thrown with a null `message` (e.g. bare `IllegalStateException()`/`NullPointerException()`) render `detail: ""`, an empty, undiagnostic value (blind-hunter + edge-case-hunter) | low | Confirmed: `respond()` does `detail ?: ""`; a message-less exception reaching any handler collapses to an empty string rather than any diagnostic fallback. | patch |
| `handleUnexpected` echoes any uncaught exception's raw `message` to the client with no scrubbing — a potential information-disclosure gap (blind-hunter) | medium | Confirmed the code does this, but it's the frozen I/O & Edge-Case Matrix's own specified behavior for the "Unanticipated exception" row (`detail` = the exception's message) — an explicit, human-approved decision, not a coding defect; fixing it means renegotiating frozen intent. | rejected |
| Throwable subtypes that aren't `Exception` (e.g. `StackOverflowError`, `OutOfMemoryError`) bypass `GlobalExceptionHandler` entirely (edge-case-hunter) | low | Real, but matches standard Spring MVC/JVM practice everywhere — catching `Error` broadly is generally discouraged since the JVM may be in an unrecoverable state; unlikely to be met in everyday use and the fix (catching `Throwable`) is a non-trivial, debatable design change. | rejected |
| `URI.create(request.requestURI)` could throw for a small class of unusual request paths, making `respond()` itself fail (edge-case-hunter) | maybe-false | Reachability depends on the embedded container's leniency (Tomcat's default connector rejects non-compliant paths before dispatch; only a non-default `relaxedPathChars`/`relaxedQueryChars` config would let one through, which this project doesn't set) — not settled without testing an actual relaxed-connector configuration. | defer |
| `sprint-status.yaml` shows `in-progress` while the spec is `in-review` and every task is complete (blind-hunter) | false | Per the workflow's own step-05, sprint status syncs to `review` once this step completes successfully with no loopback — this is expected in-flight state, not a defect. | false |
| The I/O & Edge-Case Matrix's "Error Handling" column is `N/A` in every row (blind-hunter) | low | True, but the only possible fix is editing the spec's own prose/table — out of this review's scope. | rejected |
| The `SystemException` path's logged `traceId` and response-body `traceId` could diverge with no automated cross-check (verification-gap) | false | Both the log call and the response in `handleSystem` read the same local `traceId` value captured once at the top of the method, so they cannot diverge without also changing that shared variable. | false |

## Design Notes

`GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` rather than a bare `@RestControllerAdvice` class: a future story can override `handleExceptionInternal` on that base class to inject `code`/`traceId`/`details` into every MVC framework exception (malformed body, unsupported media type, etc.), covering FR-40's "framework" exception clause. That override does **not** exist in this story -- `handleExceptionInternal` is untouched, so a framework exception is still handled by the superclass's own more-specific inherited handlers and comes back as a bare `ProblemDetail` with none of these extension members. Only the base-class choice is in place now; no test exercises this path yet (no endpoint exists to trigger one until Story 2.5). The three `AppException` subtypes and the `Exception::class` catch-all use ordinary `@ExceptionHandler` methods, which Spring prefers over the inherited ones for exact type matches, and are the only handlers that currently add `code`/`traceId`/`details`.

Standalone `MockMvc` (`MockMvcBuilders.standaloneSetup(testController, GlobalExceptionHandler())`) over `@WebMvcTest`: it boots no Spring context at all, keeping this a true unit test of the advice logic rather than a context-loading slice test — cheaper and consistent with the project's existing unit-test style.

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless pass; `GlobalExceptionHandlerTest`'s four cases pass alongside the existing suite.
