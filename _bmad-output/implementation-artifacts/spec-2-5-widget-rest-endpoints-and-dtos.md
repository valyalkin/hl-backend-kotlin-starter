---
title: 'Widget REST endpoints and DTOs'
type: 'feature'
created: '2026-09-12'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'c43698805518baf39ba2c31a628140b1a76a65c0'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Epic 2 has a service layer (Story 2.4) that can create, read, update, delete, and list widgets, but nothing exposes it over HTTP — no client can reach it yet.

**Approach:** Add `controller/WidgetController` mapping `/api/v1/widgets` to the five `WidgetService` operations, plus `dto/WidgetRequest`, `dto/WidgetResponse`, and a generic `dto/PageResponse<T>` envelope, following the one REST/pagination convention (AD-9, AD-10).

## Boundaries & Constraints

**Always:**
- Path is `/api/v1/widgets` (plural, lowercase); the domain `Widget` type never appears in any `WidgetController` method signature — only `WidgetRequest`/`WidgetResponse`/`PageResponse<WidgetResponse>`.
- `POST` collection returns `201` with a `Location` header pointing at `/api/v1/widgets/{id}` and the created `WidgetResponse` body.
- `GET`/`PUT`/`DELETE` `/{id}` on a missing id let `WidgetService`'s `NotFoundException` propagate to the existing `GlobalExceptionHandler` — no controller-level existence check.
- Collection `GET` takes a Spring Data `Pageable` parameter directly (no hand-rolled paging) and returns `dto/PageResponse<WidgetResponse>` — `{ items, page, size, totalElements, totalPages }`.
- `dto/PageResponse<T>` is generic, carries no widget-specific fields, and is built once for reuse by every future resource.

**Never:**
- No Bean Validation annotations on `WidgetRequest` in this story — Story 2.6 owns request validation.
- No `@Cacheable`/`@CacheEvict` on `WidgetService` in this story — Story 2.7.
- No hand-rolled pagination types — Spring Data `Pageable`/`Page` end to end.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Create | `POST` body `{"name":"gadget"}` | `201`, `Location: /api/v1/widgets/{id}`, body is the created `WidgetResponse` | N/A |
| Read existing | `GET /{id}`, known id | `200` with matching `WidgetResponse` | N/A |
| Read missing | `GET /{id}`, unknown id | -- | `404` Problem Detail (`NOT_FOUND`) |
| Update existing | `PUT /{id}` body `{"name":"new"}`, known id | `200` with updated `WidgetResponse` | N/A |
| Update missing | `PUT /{id}`, unknown id | -- | `404` |
| Delete existing | `DELETE /{id}`, known id | `204`, empty body | N/A |
| Delete missing | `DELETE /{id}`, unknown id | -- | `404` |
| List | `GET ?page=0&size=10` | `200` with `PageResponse` envelope | N/A |

</frozen-after-approval>

## Code Map

- `src/main/kotlin/com/hl/service/controller/WidgetController.kt` -- new; `@RestController @RequestMapping("/api/v1/widgets")`, constructor-injects `WidgetService`, five handler methods.
- `src/main/kotlin/com/hl/service/dto/WidgetRequest.kt` -- new; `data class WidgetRequest(val name: String)`, no validation annotations yet.
- `src/main/kotlin/com/hl/service/dto/WidgetResponse.kt` -- new; `data class WidgetResponse(id, name, createdAt, updatedAt)` with a `companion object { fun from(widget: Widget) }`.
- `src/main/kotlin/com/hl/service/dto/PageResponse.kt` -- new; generic envelope with `companion object { fun <T, R> from(page: Page<T>, mapper: (T) -> R): PageResponse<R> }`.
- `src/main/kotlin/com/hl/service/dto/.gitkeep` -- delete once the package has real content.
- `src/main/kotlin/com/hl/service/service/WidgetService.kt` -- existing; all five methods consumed as-is, no change.
- `src/main/resources/application.yaml` -- add `spring.data.web.pageable.default-page-size: 20` so the collection `GET`'s default is a discoverable config value, not an implicit framework default.
- `src/test/kotlin/com/hl/service/support/FakeWidgetRepository.kt` -- new; the in-memory `WidgetRepository` fake currently private inside `WidgetServiceTest`, extracted so `WidgetControllerTest` can reuse it (no behavior change).
- `src/test/kotlin/com/hl/service/service/WidgetServiceTest.kt` -- existing; drop the inline `FakeWidgetRepository` class, reference the extracted one instead.
- `src/test/kotlin/com/hl/service/controller/WidgetControllerTest.kt` -- new; standalone `MockMvc` over a real `WidgetController(WidgetService(FakeWidgetRepository()))` plus `GlobalExceptionHandler`, styled like `GlobalExceptionHandlerTest`.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/kotlin/com/hl/service/dto/WidgetRequest.kt` -- add DTO -- wire representation for create/update bodies
- [x] `src/main/kotlin/com/hl/service/dto/WidgetResponse.kt` -- add DTO + `from(Widget)` mapper -- wire representation for responses, domain never serialized directly
- [x] `src/main/kotlin/com/hl/service/dto/PageResponse.kt` -- add generic envelope + `from(Page, mapper)` -- one pagination contract (AD-10), reusable by any future resource
- [x] `src/main/kotlin/com/hl/service/dto/.gitkeep` -- delete -- package now has real content
- [x] `src/main/kotlin/com/hl/service/controller/WidgetController.kt` -- add the five `/api/v1/widgets` handlers -- exposes the service layer over HTTP (epic goal)
- [x] `src/main/resources/application.yaml` -- add `spring.data.web.pageable.default-page-size: 20` -- documents the collection `GET`'s default size
- [x] `src/test/kotlin/com/hl/service/support/FakeWidgetRepository.kt` -- extract the fake from `WidgetServiceTest` -- lets `WidgetControllerTest` reuse it without duplicating ~90 lines of `JpaRepository` stubs
- [x] `src/test/kotlin/com/hl/service/service/WidgetServiceTest.kt` -- reference the extracted fake -- no behavior change
- [x] `src/test/kotlin/com/hl/service/controller/WidgetControllerTest.kt` -- add tests covering create/read/update/delete/list and the three not-found paths -- proves the controller end to end without a Spring context

**Acceptance Criteria:**
- Given `controller/WidgetController` and `dto/WidgetRequest`/`dto/WidgetResponse`, when inspected, then the path is `/api/v1/widgets` and the domain type never appears in a controller signature.
- Given the collection `GET`, when called with `?page=`/`?size=`, then the response is the shared `dto/PageResponse` envelope.
- Given a `GET /{id}` for a missing id, when the handler processes it, then the response is `404` with the standard Problem Detail body produced by the existing `GlobalExceptionHandler`.

## Implementation Notes

- `PageResponse<T>`'s `from` companion function required an explicit `T : Any` bound (`fun <T : Any, R> from(page: Page<T>, mapper: (T) -> R)`) because Spring Data's `Page<T>` itself is declared `Page<T : Any>`; without the bound `compileKotlin` fails with "Type argument is not within its bounds".
- The standalone `MockMvc` setup in `WidgetControllerTest` needed `PageableHandlerMethodArgumentResolver` registered explicitly via `.setCustomArgumentResolvers(...)` -- a full Spring Boot context supplies this resolver automatically via Spring Data Web auto-configuration, but a bare `MockMvcBuilders.standaloneSetup(...)` (matching `GlobalExceptionHandlerTest`'s style) does not; without it, the `list` handler's `Pageable` parameter failed to resolve (`IllegalStateException: No primary or single unique constructor found for interface org.springframework.data.domain.Pageable`), surfacing as a 500 through `GlobalExceptionHandler`'s catch-all.
- No Jackson Kotlin module (`jackson-module-kotlin`) or `-java-parameters` compiler flag is configured anywhere in the build; despite that, Jackson successfully deserialized `WidgetRequest` bodies (`POST`/`PUT`) in every test -- worth keeping in mind if a future DTO's shape changes and deserialization suddenly breaks, since this project is one dependency short of the usual safety net for Kotlin data class deserialization.
- Verified: `./gradlew clean build` -- BUILD SUCCESSFUL; spotless/ktlint pass (one auto-formatting reflow applied to `WidgetController.list` via `spotlessApply`); full suite green, including the new `WidgetControllerTest` (`tests="10" failures="0" errors="0"`) and the updated `WidgetServiceTest` (`tests="10" failures="0" errors="0"`), plus all pre-existing suites (`GlobalExceptionHandlerTest`, `WidgetEntityTest`, `WidgetRepositoryIT`, `StarterApplicationIT`, `LivenessProbeIT`, `DatasourceFailFastTest`, `RedisHealthDownTest`) unaffected.
- Review pass (see Review Triage Log) sent 1 `patch` finding back to the implementation agent: the "Location header points at the created widget's id" test extracted the created id via a hand-rolled regex instead of `jsonPath`, unlike every other test in the file. Fixed by reading `$.id` via `com.jayway.jsonpath.JsonPath.read` (already on the test classpath transitively via Spring Test, no build file change needed). Two findings were deferred (pre-existing `GlobalExceptionHandler` gap on framework-raised exceptions; missing `jackson-module-kotlin`/`-java-parameters` safety net) -- see `deferred-work.md`. Re-verified independently: `./gradlew clean build` -- BUILD SUCCESSFUL; `WidgetControllerTest` `tests="10" failures="0" errors="0"`; all 9 test classes across the suite green.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| `sprint-status.yaml` shows `in-progress` for this story while the spec is `in-review` (blind-hunter) | false | Expected in-flight state: per the workflow's own step-05, sprint status syncs to `review` only once the review step completes cleanly with no loopback -- identical to the precedent already logged in spec-2-3's and spec-2-4's own triage logs. | false |
| `spring.data.web.pageable.default-page-size: 20` has no test proving it's wired through real Spring Data Web auto-config -- `WidgetControllerTest`'s standalone `MockMvc` uses a hand-built `PageableHandlerMethodArgumentResolver`, bypassing `application.yaml` entirely (blind-hunter + verification-gap) | low | Confirmed the gap is real, but Spring Data Web's own built-in `default-page-size` is already `20` -- identical to the value we set -- so a regression here would not change any observed behavior today. The fix (a new `@WebMvcTest`-level test class) is more than a trivial correction. | rejected (low severity, non-trivial fix) |
| No `max-page-size` configured, and no test proves an oversized `?size=` is clamped rather than fully materialized (blind-hunter) | false | Spring Data Web's built-in `max-page-size` default (2000) already caps this; nothing in this diff removes or weakens that guard. | false |
| A non-UUID `/{id}` path segment, a malformed JSON body, or a missing `Content-Type` on `POST`/`PUT` falls through to `ResponseEntityExceptionHandler`'s inherited handling and returns a bare `ProblemDetail` without `code`/`traceId`/`details` (blind-hunter + edge-case-hunter x2) | medium (if it occurred) | Confirmed real: `GlobalExceptionHandler`'s own class doc already names this exact gap ("today, a framework exception is handled by the superclass's own more-specific inherited handlers and comes back as a bare `ProblemDetail`"). The gap predates this story (Story 2.3); `WidgetController`'s new endpoints are simply the first callers to newly exercise it. | defer |
| `WidgetRequest` deserializes correctly despite no `jackson-module-kotlin` and no `-java-parameters` compiler flag anywhere in the build (blind-hunter) | low | Confirmed the dependency is genuinely absent and every test still passes today; this is a latent fragility in the build's Kotlin/Jackson setup that predates this story's DTOs, not something Story 2.5 introduced. | defer |
| `WidgetController` ships with no OpenAPI/springdoc annotations despite `springdoc-openapi-starter-webmvc-ui` already being a project dependency (blind-hunter) | false | Out of scope by the epic's own story split: `epic-2-context.md` explicitly assigns OpenAPI/Swagger wiring to Story 2.10, which by design lands after the rest of the slice (including this story) is complete. | false (out of scope -- Story 2.10) |
| The "Location header points at the created widget's id" test extracts the id via a hand-rolled regex over the raw response body instead of `jsonPath`, unlike every other test in the same file (blind-hunter + edge-case-hunter) | low | Confirmed: fragile and inconsistent with the file's own established style -- could silently mismatch or throw an unhelpful `NullPointerException` if the JSON shape ever changes. Fix is a small, direct rewrite using the same `jsonPath` idiom already used elsewhere in the file. | patch |
| Only 3 Given/When/Then Acceptance Criteria are written against an 8-row I/O & Edge-Case Matrix (blind-hunter) | false | By the spec template's own design, Acceptance Criteria cover system-level behaviors not already captured by the matrix -- they are not meant to restate each matrix row, so a smaller AC count is expected, not a gap. | false |
| `create`/`update` accept a blank or whitespace-only `name` with no rejection (edge-case-hunter) | false | Explicitly out of scope: the spec's own frozen `Never` boundary states no Bean Validation annotations on `WidgetRequest` in this story -- Story 2.6 owns request validation. | false (out of scope -- Story 2.6) |
| `FakeWidgetRepository.findAll`'s `pageNumber * pageSize` can overflow `Int` for large page/size values, yielding a negative `start` and an `IndexOutOfBoundsException` (edge-case-hunter) | false | Not reachable via any call site in this diff: no test constructs an overflow-inducing `Pageable`, and the real (non-fake) production path uses Spring Data JPA, which computes the offset in `long` arithmetic, not this fake's `Int` math. Identical to the precedent already rejected in spec-2-4's own triage log for the same fake. | false |

## Design Notes

`PageResponse.from(page, mapper)` takes the mapper as a parameter rather than requiring `Page<WidgetResponse>` at the call site, so the controller can write `PageResponse.from(widgetService.list(pageable), WidgetResponse::from)` in one line while keeping `PageResponse` itself widget-agnostic.

`Location` header: `ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(widget.id).toUri()` -- standard Spring MVC idiom, works the same under `MockMvc` and a real servlet container.

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless pass; new `WidgetControllerTest` cases pass alongside the existing suite.
