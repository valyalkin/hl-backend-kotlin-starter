---
title: 'Request validation with field-level errors'
type: 'feature'
created: '2026-09-12'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '9807c4599e7e60deb1f44e7c5beffbf58bf17513'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `WidgetRequest` accepts a blank or whitespace-only `name` today (Story 2.5 deliberately deferred this), and a `MethodArgumentNotValidException` has no dedicated handler, so a violating request would fall through to `GlobalExceptionHandler`'s inherited superclass handling and come back as a bare `ProblemDetail` with no `code`/`traceId`/`errors`.

**Approach:** Add a Bean Validation annotation to `WidgetRequest.name`, apply `@Valid` on the controller's request bodies, and add a `handleMethodArgumentNotValid` override to `GlobalExceptionHandler` that renders the same Problem Detail shape with an `errors` array of `{field, code, message}` (AD-11; architecture memlog decision: the per-field `code` is the constraint's own code, distinct from the four exception-type codes).

## Boundaries & Constraints

**Always:**
- `WidgetRequest.name` carries `@field:NotBlank` (or equivalent) so a missing, empty, or whitespace-only value is rejected.
- `WidgetController.create` and `.update` mark their `WidgetRequest` parameter `@Valid` so both entry points reject invalid bodies identically.
- A violating request returns `400` `application/problem+json` with `type`/`instance`/`detail`/`code`/`traceId` exactly like the three existing exception-type responses, plus an `errors` array of `{field, code, message}` — one entry per violated field, not a second error schema.
- The top-level `code` for a validation failure is a new fixed literal (`VALIDATION_ERROR`), additional to the existing `BUSINESS_ERROR`/`NOT_FOUND`/`SYSTEM_ERROR`/`UNEXPECTED_ERROR` set; each field entry's own `code` is the violated constraint's simple name (e.g. `NotBlank`), never one of those four.
- `spring-boot-starter-validation` is added to `build.gradle.kts` (BOM-managed, no version literal per AD-22) — required for `@Valid`/Bean Validation processing; nothing in the build pulls it in transitively today.

**Never:**
- No new error type in `error/` and no change to `AppException`/`BusinessException`/`NotFoundException`/`SystemException` — validation is orthogonal to the three exception types (architecture memlog decision).
- No `@Size`, `@Pattern`, or other constraints beyond `@NotBlank` — the `widgets` table's `name` column is unconstrained `TEXT` and the epic only requires demonstrating "at least one field".
- No change to `handleExceptionInternal` or any other inherited `ResponseEntityExceptionHandler` behavior — only the one specific override point for `MethodArgumentNotValidException`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Create, blank name | `POST` body `{"name":""}` | -- | `400` Problem Detail, `code=VALIDATION_ERROR`, `errors=[{field:"name", code:"NotBlank", message:...}]` |
| Create, whitespace-only name | `POST` body `{"name":"   "}` | -- | same as above |
| Create, valid name | `POST` body `{"name":"gadget"}` | `201` as today | N/A |
| Update, blank name | `PUT /{id}` body `{"name":""}`, known id | -- | `400` Problem Detail with `errors`, same shape as create |

</frozen-after-approval>

## Code Map

- `src/main/kotlin/com/hl/service/dto/WidgetRequest.kt` -- add `@field:NotBlank` on `name`; drop the "no validation yet" doc comment.
- `src/main/kotlin/com/hl/service/controller/WidgetController.kt` -- add `@Valid` to the `WidgetRequest` parameter on `create` and `update`.
- `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt` -- widen `respond`'s and the four existing handlers' return type from `ResponseEntity<ProblemDetail>` to `ResponseEntity<Any>` (matches `ResponseEntityExceptionHandler`'s own override signature, avoiding a cast); add an `errors: List<FieldValidationError>? = null` parameter to `respond`, set as the `errors` property when non-null; add a private `FieldValidationError(field, code, message)` data class; add `override fun handleMethodArgumentNotValid(ex, headers, status, request): ResponseEntity<Any>` that maps `ex.bindingResult.fieldErrors` (via `.code ?: "INVALID"`, `.defaultMessage ?: ""`) to `FieldValidationError` and delegates to `respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex, emptyMap(), request, errors = errors)`, reading the servlet request via `(request as ServletWebRequest).request`.
- `build.gradle.kts` -- add `implementation("org.springframework.boot:spring-boot-starter-validation")` alongside the other starters.
- `src/test/kotlin/com/hl/service/controller/WidgetControllerTest.kt` -- add tests for the blank-name matrix rows above.

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add `spring-boot-starter-validation` -- enables `@Valid`/Bean Validation processing
- [x] `src/main/kotlin/com/hl/service/dto/WidgetRequest.kt` -- add `@field:NotBlank` on `name` -- satisfies "at least one constrained field" (FR-17)
- [x] `src/main/kotlin/com/hl/service/controller/WidgetController.kt` -- add `@Valid` to `create`/`update` -- both entry points enforce the constraint
- [x] `src/main/kotlin/com/hl/service/controller/GlobalExceptionHandler.kt` -- override `handleMethodArgumentNotValid`, widen `respond`'s return type, add `FieldValidationError` -- renders violations in the standard Problem Detail shape (AD-11)
- [x] `src/test/kotlin/com/hl/service/controller/WidgetControllerTest.kt` -- add blank-name tests for create and update, asserting status, `Content-Type`, and `errors` contents

**Acceptance Criteria:**
- Given `WidgetRequest`, when inspected, then it carries a Bean Validation annotation on `name`.
- Given a request that violates the constraint, when the controller receives it, then the response is `400` `application/problem+json` with an `errors` array of `{field, code, message}`, produced by `GlobalExceptionHandler`.
- Given the new validation test, when it runs, then it asserts the status, the `Content-Type`, and the contents of `errors`.

## Implementation Notes

- `ResponseEntityExceptionHandler.handleMethodArgumentNotValid`'s signature in this Spring Framework 7 / Boot 4 codebase is `protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException, HttpHeaders, HttpStatusCode, WebRequest)` -- confirmed by decompiling the resolved `spring-webmvc` jar (`javap`) rather than assuming an older Spring 6-era signature (`HttpStatus` and a narrower `ProblemDetail` return type). The `WebRequest` parameter is cast to `ServletWebRequest` to reach `.request` (an `HttpServletRequest`), matching the other four handlers' `respond` call shape.
- `respond`'s `code`, `details`, `request`, and `traceId` parameters kept their existing positions/defaults; only a trailing `errors: List<FieldValidationError>? = null` was added, so all four pre-existing call sites are unchanged.
- Verified: `./gradlew build` -- BUILD SUCCESSFUL; spotless/ktlint pass with no reflow needed; full suite green, including `WidgetControllerTest` (`tests="13" failures="0" errors="0"`, the 3 new blank/whitespace-name cases alongside the 10 pre-existing ones) and all other pre-existing suites unaffected.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| A JSON body that omits `name` entirely (`{}`) never reaches `@Valid`/`@NotBlank`: Jackson's reflective constructor call hits Kotlin's own `Intrinsics.checkNotNullParameter` null-check and throws before Bean Validation runs, surfacing as `HttpMessageNotReadableException` — handled by the inherited superclass handler as a bare `ProblemDetail` with no `code`/`traceId`/`errors`, not the `VALIDATION_ERROR` shape the spec's own "Always" bullet implies for a "missing" value (blind-hunter + edge-case-hunter) | medium | Confirmed real: `GlobalExceptionHandler`'s own class doc already names this exact gap, and Story 2.5's own triage log already deferred the identical "malformed JSON body ... falls through to inherited handling" case for the same reason. This spec's own "Never" boundary explicitly forbids touching `handleExceptionInternal`/other inherited `ResponseEntityExceptionHandler` behavior, so fixing this is out of scope here too. | defer |
| `handleMethodArgumentNotValid` maps only `ex.bindingResult.fieldErrors`, never `ex.bindingResult.globalErrors`, so a class-level constraint violation would return `code=VALIDATION_ERROR` with an empty `errors` array (edge-case-hunter) | false | Not reachable: `WidgetRequest` carries no class-level (object-level) Bean Validation constraint, only the field-level `@NotBlank` added by this story — `globalErrors` is always empty today. | false |
| No dedicated case in `GlobalExceptionHandlerTest.kt` exercises the new `handleMethodArgumentNotValid` override the way the other four handlers each get their own case there (blind-hunter) | low | The override's `type`/`instance`/`code`/`traceId`/`errors` shape is already directly asserted through `WidgetControllerTest`'s three new cases, satisfying the story's own AC; adding a parallel standalone case would require new test-controller/DTO wiring for no additional coverage. | rejected (low severity, non-trivial/redundant fix) |
| `update`'s new test only covers a blank (`""`) name, not the whitespace-only (`"   "`) case already covered for `create`, leaving the two `@Valid` entry points asymmetrically tested (blind-hunter) | low | Confirmed: `@Valid` is applied identically to both methods per the spec, but only `create` proves the whitespace-only row of the matrix; `update` does not. | patch |
| `FieldValidationError(it.field, it.code ?: "INVALID", ...)`'s `?: "INVALID"` fallback is unreachable and untested since `FieldError.getCode()` always resolves to the bare constraint name for a Bean Validation violation (blind-hunter) | false | Intentional defensive default, consistent with this same file's pre-existing `ex.message ?: ex.javaClass.simpleName` idiom for a Java API that is typed nullable but not expected to be null in practice. | false |
| `(request as ServletWebRequest).request` is an unchecked cast that would throw `ClassCastException` if `handleMethodArgumentNotValid` were ever invoked with a non-`ServletWebRequest` (edge-case-hunter + blind-hunter) | low | This project has no reactive-stack dependency anywhere (`spring-boot-starter-webmvc` only); under Spring MVC's exclusively servlet-based dispatch, `ResponseEntityExceptionHandler` always supplies a `ServletWebRequest` here. Guarding against a WebFlux-only scenario that cannot occur in this codebase is speculative. | rejected (low severity, speculative/non-trivial guard) |
| The top-level `detail` on a validation failure is `MethodArgumentNotValidException`'s verbose, auto-generated message (rejected value, codes list) rather than a concise summary (edge-case-hunter) | low | Consistent with this handler's own established philosophy for exceptions without a custom message (`handleUnexpected` already echoes an arbitrary exception's raw message verbatim); the structured `errors` array, not `detail`, is the surface a client parses for field-level detail. | rejected (low severity, non-trivial special-casing) |
| The reviewed diff excludes the new `spec-2-6-...md` file (untracked, so absent from `git diff <baseline> -- .`), leaving reviewers unable to check the code against the spec from the diff alone (blind-hunter) | false | Process artifact of how `{diff_file}` was staged, not a code defect; the edge-case-hunter layer was independently given the spec's path as `claims_file` per the workflow, so the spec's content was available to the layer that needed it. | false |

## Design Notes

`FieldError.getCode()` returns the *last* entry of Spring's resolved message-codes array (`DefaultMessageCodesResolver`'s least-specific fallback), which for a Bean Validation violation is the bare constraint annotation name (e.g. `NotBlank`) -- not the field-qualified variant. No extra parsing needed.

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless pass; new `WidgetControllerTest` cases pass alongside the full existing suite.
