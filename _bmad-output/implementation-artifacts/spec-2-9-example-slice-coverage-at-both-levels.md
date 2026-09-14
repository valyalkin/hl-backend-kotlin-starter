---
title: 'Example Slice coverage at both levels'
type: 'feature'
created: '2026-09-13'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '2c54517fcbe3493c8138d532b120bfe89bc26134'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The `widgets` slice is the copy-paste reference for every future resource, but its test coverage has a gap against Story 2.9's contract: no test drives a real HTTP request all the way through Postgres and Redis and back (existing Integration Tests hit either the service layer against real Redis, or the repository against real Postgres, never both via HTTP), and the slice's own code never deliberately throws a `BusinessException` — only `NotFoundException` is exercised end to end today.

**Approach:** Add one full HTTP-to-store Integration Test that creates a widget over real HTTP and asserts the response, the persisted Postgres row, and the Redis cache entry. Add a duplicate-name business rule to `create`/`update` as the deliberate `BusinessException` throw site, covered by Unit and controller-level tests matching the existing `NotFoundException` pattern. The `SystemException` requirement is already satisfied by Story 2.8's `RedisConnectionFailureException` → `SystemException` path (proven by `RedisDownIT`); no new code needed for it.

## Boundaries & Constraints

**Always:**
- The new HTTP-to-store Integration Test extends `IntegrationTestBase`, reuses the shared JVM-wide Postgres/Redis containers, and carries `@Tag("integration")`.
- `create` rejects a `name` that already belongs to another widget; `update` rejects a `name` that already belongs to a *different* widget (renaming a widget to its own current name is not a conflict). Both throw `BusinessException("Widget name '$name' already exists")`.
- New Unit Tests for the duplicate-name rule use the existing hand-written `FakeWidgetRepository` pattern — no mocking framework, no Spring context.

**Never:**
- No change to `GlobalExceptionHandler`'s existing exception-to-response mapping — only new throw sites in Widget code, not new handler behavior.
- No change to the cache TTL, pagination envelope, or validation rules already shipped in Stories 2.5–2.8.
- No new `SystemException` throw site — Story 2.8's Redis-down coverage already satisfies this story's AC for that type.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| HTTP create round-trips to Postgres and Redis | `POST /api/v1/widgets` with a valid, unique name | 201 with `WidgetResponse`; a matching row exists in Postgres; a subsequent read is served from Redis (repository not called again) | N/A |
| Create with a duplicate name | `POST /api/v1/widgets` with a `name` already used by an existing widget | 400 Problem Detail, `code: "BUSINESS_ERROR"` | 400 |
| Update to a name already used by a different widget | `PUT /api/v1/widgets/{id}` with another widget's `name` | 400 Problem Detail, `code: "BUSINESS_ERROR"` | 400 |
| Update to the widget's own current name | `PUT /api/v1/widgets/{id}` with its unchanged `name` | 200, no conflict | N/A |

</frozen-after-approval>

## Code Map

- `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt` — extend, unmodified; shared Postgres+Redis containers.
- `src/test/kotlin/com/hl/service/repository/WidgetRepositoryIT.kt` — reference; Postgres-only IT pattern (`@Autowired EntityManager`).
- `src/test/kotlin/com/hl/service/service/WidgetServiceCacheIT.kt` — reference; Redis-only IT pattern (`@MockitoSpyBean` on `WidgetRepository`, `verify`/`clearInvocations`). The new HTTP-to-store IT combines both: real HTTP via `@AutoConfigureRestTestClient`/`RestTestClient` (styled like `RedisDownIT`), plus the spy to prove the cache hit.
- `src/main/kotlin/com/hl/service/repository/WidgetRepository.kt` — add two Spring Data derived queries: `existsByName(name: String): Boolean` (for `create`) and `existsByNameAndIdNot(name: String, id: UUID): Boolean` (for `update`, excluding the widget's own row).
- `src/main/kotlin/com/hl/service/service/WidgetService.kt` — `create` calls `existsByName` before saving; `update` calls `existsByNameAndIdNot` before mutating; both throw `BusinessException` on a hit.
- `src/test/kotlin/com/hl/service/support/FakeWidgetRepository.kt` — implement both new methods over the existing in-memory `LinkedHashMap` (linear scan by `entity.name`, matching the fake's existing style).
- `src/test/kotlin/com/hl/service/service/WidgetServiceTest.kt` — add Unit Tests for the duplicate-name rule on `create` and `update`, styled like the existing `NotFoundException` test pairs.
- `src/test/kotlin/com/hl/service/controller/WidgetControllerTest.kt` — add standalone-MockMvc tests proving the rule renders as 400 `code: "BUSINESS_ERROR"`.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/kotlin/com/hl/service/repository/WidgetRepository.kt` -- add `existsByName` and `existsByNameAndIdNot` -- gives the service the queries it needs to detect a conflicting name
- [x] `src/test/kotlin/com/hl/service/support/FakeWidgetRepository.kt` -- implement both new methods -- keeps the fake in sync with the interface for hermetic Unit Tests
- [x] `src/main/kotlin/com/hl/service/service/WidgetService.kt` -- `create`/`update` throw `BusinessException` on a duplicate name -- gives the slice its missing deliberate 400 path
- [x] `src/test/kotlin/com/hl/service/service/WidgetServiceTest.kt` -- Unit Tests: `create` throws on a duplicate name, `update` throws on another widget's name, `update` allows keeping its own name -- covers the domain rule in isolation
- [x] `src/test/kotlin/com/hl/service/controller/WidgetControllerTest.kt` -- standalone-MockMvc tests for the same three cases at the HTTP level -- matches the existing `NotFoundException` HTTP-level pair
- [x] `src/test/kotlin/com/hl/service/WidgetHttpToStoreIT.kt` -- new; real HTTP `POST` then `GET` against `IntegrationTestBase`'s containers, asserting the response body, the persisted Postgres row (via `WidgetRepository`/`EntityManager`), and that the second `GET` is served from Redis (spy `verify` on `WidgetRepository`, one invocation total) -- the missing "HTTP-to-store" Integration Test the AC names explicitly

**Acceptance Criteria:**
- Given the Example Slice Unit Tests, when they run, then they cover the duplicate-name domain rule with no container and no Spring context, using `FakeWidgetRepository`.
- Given the Example Slice HTTP-to-store Integration Test, when it runs, then it drives a real HTTP request through to Postgres and Redis and back, asserting the response status/body, the persisted row, and the cache entry (no second repository call).
- Given the Example Slice code, when inspected, then it deliberately throws at least one `BusinessException` (duplicate name, this story), one `NotFoundException` (existing, Story 2.4), and one `SystemException` (existing Redis-down path, Story 2.8), and all three paths are covered by tests.

## Implementation Notes

- `WidgetHttpToStoreIT`'s persisted-row assertion reads through `WidgetRepository` (spy bean) rather than a raw `EntityManager` query, since the spy is already needed for the Redis-hit assertion and reading the same row through it does not affect the invocation counts asserted afterward (`clearInvocations` runs after that read).
- `WidgetHttpToStoreIT` builds the POST body from `WidgetRequest` (Jackson-serialized) rather than a raw JSON string, and reads response fields via `jsonPath`/`JsonPath.read` on the raw byte body (never deserializing into `WidgetResponse`) to match the codebase's existing pattern (`WidgetControllerTest`, `RedisDownIT`) and avoid depending on Jackson's Kotlin-constructor deserialization, which this project does not configure.
- Verified locally with `./gradlew build`: BUILD SUCCESSFUL, spotless/ktlint clean, all 13 suites green (`WidgetServiceTest` 13 tests, `WidgetControllerTest` 17 tests, `WidgetHttpToStoreIT` 1 test, plus all pre-existing suites), 0 failures/errors.
- Post-review fixes: the duplicate-name rule now uses `existsByNameIgnoreCase`/`existsByNameIgnoreCaseAndIdNot` (case-insensitive) in both `WidgetRepository` and `FakeWidgetRepository`; `WidgetRepositoryIT` gained two tests calling these derived queries directly against real Postgres; `WidgetHttpToStoreIT` now deletes its created row via `widgetRepository.deleteById(id)` after its assertions so it no longer leaves a permanent row in the shared container; `WidgetServiceTest` gained a test proving `update` throws `NotFoundException` (not `BusinessException`) when the id is missing even though the supplied name would also conflict. Re-verified by running only the four affected suites (`WidgetServiceTest` 14 tests, `WidgetControllerTest` 17 tests, `WidgetRepositoryIT` 3 tests, `WidgetHttpToStoreIT` 1 test) -- all green.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| Check-then-act duplicate-name enforcement has no DB-level unique constraint on `widgets.name`; concurrent `create`/`update` calls with the same name can both pass `existsByName`/`existsByNameAndIdNot` before either commits (blind-hunter, edge-case-hunter, verification-gap) | low | Confirmed: `V1__create_widgets.sql` has no unique index and the diff adds none; two concurrent writers can defeat the uniqueness rule. Unlikely to be hit in this Starter's demo/dev usage, and the proper fix (a unique constraint plus `DataIntegrityViolationException` handling) is a schema change, not a direct correction. | rejected (low severity, fix exceeds direct correction) |
| `existsByName`/`existsByNameAndIdNot` force a full table scan of `widgets` on every write since `name` has no index (blind-hunter) | low | Confirmed: no index on `name`; negligible at this Starter's demo scale, and adding one is a schema change beyond a direct correction. | rejected (low severity, fix exceeds direct correction) |
| `BusinessException`'s message/details echo the conflicting name back to the caller, letting `create`/`update` be used to probe other widgets' names (blind-hunter) | false | Deliberate per architecture AD-11: message and details are echoed to the client for every exception type, including this one -- not a defect introduced by this diff. | false |
| Renaming a widget to its own current name would wrongly throw `BusinessException` if a pre-existing duplicate row already shares that name (edge-case-hunter) | false | Only reachable if a duplicate already exists, which requires the already-rejected race-condition finding above; under the normal invariant (`create`/`update` block duplicates), no two widgets ever share a name, so `existsByNameAndIdNot` on an unchanged name is always `false`. | false |
| `existsByName`/`existsByNameAndIdNot` are only exercised through `FakeWidgetRepository`'s hand-written re-implementation; nothing proves the real Spring Data-generated query resolves correctly against Postgres (verification-gap) | medium | Pre-verified by the verification-gap layer: `WidgetHttpToStoreIT` only ever creates one widget per run (a random, always-unique name), and `WidgetRepositoryIT` never calls either method -- a regression in the real derived query (e.g. always returning `false`, silently disabling the rule) would pass the full suite undetected, defeating this story's actual purpose. | patch |
| `WidgetHttpToStoreIT` never deletes the widget it creates, unlike every sibling Integration Test (`@Transactional` rollback) -- permanently adds a row to the shared, JVM-wide Postgres container (blind-hunter) | low | Confirmed by the test's own Implementation Notes; doesn't break any assertion today since no other test counts total rows, but breaks the established Integration Test hygiene convention. | patch |
| Duplicate-name check is case-sensitive and unnormalized -- `"Gadget"` and `"gadget"` are treated as distinct names (blind-hunter, edge-case-hunter) | low | Confirmed: `existsByName`/`existsByNameAndIdNot` use exact string equality. A case-insensitive fix (`existsByNameIgnoreCase`) is a direct, low-risk correction. | patch |
| No test proves `update`'s `NotFoundException` check (missing id) takes precedence over its `BusinessException` check (conflicting name) when both conditions hold (blind-hunter) | low | Confirmed: the implemented ordering is correct (id lookup before name-conflict check) but untested; a trivial test addition closes the gap. | patch |

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless clean; full suite green including the new IT and Unit Tests.
