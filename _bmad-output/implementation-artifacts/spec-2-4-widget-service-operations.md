---
title: 'Widget service operations'
type: 'feature'
created: '2026-09-12'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'c2f922f87f610f81ab34ab2081a5be6999e23883'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Epic 2 has a domain model, persistence, and an error contract, but no service layer — application logic for widgets has nowhere to live, so nothing can create, read, update, delete, or list widgets yet.

**Approach:** Add `service/WidgetService`, a `@Service` that maps between `Widget` and `WidgetEntity`, exposes create/read-by-id/update/delete/list operations, delegates to `WidgetRepository`, and raises `NotFoundException` for any operation on a missing id.

## Boundaries & Constraints

**Always:**
- `WidgetService` returns domain `Widget` types only — never `WidgetEntity`, never a DTO.
- The class is `@Service`; mutating methods (create, update, delete) are `@Transactional` by convention.
- Read-by-id, update, and delete of a nonexistent id all throw `NotFoundException("Widget $id not found")` — one consistent not-found signal across the resource, even though the story's own AC only requires it for read.
- `create` builds the domain `Widget` (id via its own `UUID.randomUUID()` default, `createdAt = updatedAt = Instant.now()`), persists it via `repository.save(WidgetEntity.fromDomain(...))`, and returns `.toDomain()` of the saved entity.
- `update(id, name)` is a full replacement of the only mutable field: it loads the existing entity (or throws), sets `name`, refreshes `updatedAt`, leaves `id`/`createdAt` untouched, saves, and returns the mapped domain widget.
- `list(pageable: Pageable)` returns `Page<Widget>` directly via `repository.findAll(pageable).map { it.toDomain() }` — Spring Data `Page`/`Pageable` end to end, no hand-rolled pagination type.
- Unit tests use a hand-written in-memory fake `WidgetRepository` (a `MutableMap`-backed class), no mocking framework, no Spring context — matching the existing `WidgetEntityTest`/`GlobalExceptionHandlerTest` style.

**Never:**
- No REST controller, DTOs, or Redis caching in this story (Stories 2.5 and 2.7).
- No custom Spring Data query methods beyond what `JpaRepository` already provides.
- The fake repository does not need to implement every `JpaRepository` method — only the ones `WidgetService` actually calls; let the rest throw `NotImplementedError()`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Create | `name = "gadget"` | Saves an entity with a fresh UUID and `createdAt = updatedAt = now()`; returns the matching `Widget` | N/A |
| Read existing | known `id` | Returns the `Widget` matching the stored entity | N/A |
| Read missing | random `id` | -- | `NotFoundException` |
| Update existing | known `id`, `name = "new"` | `name` replaced, `updatedAt` refreshed, `id`/`createdAt` unchanged; returns the updated `Widget` | N/A |
| Update missing | random `id` | -- | `NotFoundException` |
| Delete existing | known `id` | Entity removed from the repository | N/A |
| Delete missing | random `id` | -- | `NotFoundException` |
| List | `page = 0, size = 10` | Returns `Page<Widget>` mapped from `repository.findAll(pageable)` | N/A |

</frozen-after-approval>

## Code Map

- `src/main/kotlin/com/hl/service/service/WidgetService.kt` -- new; `@Service`, constructor-injects `WidgetRepository`, exposes the five operations.
- `src/main/kotlin/com/hl/service/service/.gitkeep` -- delete once the package has real content.
- `src/main/kotlin/com/hl/service/model/Widget.kt` -- existing; the domain type the service returns (no change).
- `src/main/kotlin/com/hl/service/repository/WidgetEntity.kt` -- existing; `fromDomain`/`toDomain` do the mapping (no change).
- `src/main/kotlin/com/hl/service/repository/WidgetRepository.kt` -- existing; `JpaRepository<WidgetEntity, UUID>` injected into the service (no change).
- `src/main/kotlin/com/hl/service/error/AppException.kt` -- existing; `NotFoundException` thrown on missing ids (no change).
- `src/test/kotlin/com/hl/service/service/WidgetServiceTest.kt` -- new; hand-written fake `WidgetRepository` plus tests for create/read/update/delete/list and the not-found paths, styled like `WidgetEntityTest.kt`/`GlobalExceptionHandlerTest.kt`.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/kotlin/com/hl/service/service/WidgetService.kt` -- add `@Service` class with `create`/`findById`/`update`/`delete`/`list` -- gives the application logic one home (epic goal)
- [x] `src/main/kotlin/com/hl/service/service/.gitkeep` -- delete -- package now has real content
- [x] `src/test/kotlin/com/hl/service/service/WidgetServiceTest.kt` -- add the fake `WidgetRepository` plus tests covering create/read/update/delete/list and the not-found path -- proves the service end to end without Spring

**Acceptance Criteria:**
- Given `service/WidgetService`, when inspected, then it maps between `Widget` and `WidgetEntity`, is annotated `@Service` (with `@Transactional` on mutating methods), and returns domain types, never entities or DTOs.
- Given a read-by-id, update, or delete for an id that does not exist, when the service handles it, then it throws `NotFoundException`.
- Given a list request with `page` and `size`, when the service handles it, then it queries via Spring Data `Pageable` and returns a `Page<Widget>`.
- Given the service's unit tests, when they run, then they use a hand-written in-memory fake `WidgetRepository`, no Spring context and no container, and cover create/read/update/delete/list plus the not-found path.

## Implementation Notes

- The fake repository's actual `JpaRepository` surface is ~26 methods (including the deprecated `getOne`/`getById` and the `QueryByExampleExecutor` methods), not the ~15 estimated in Design Notes; all are implemented as literal Kotlin overrides (required for every inherited Java abstract method), with everything but `save`/`findById`/`existsById`/`deleteById`/`findAll(Pageable)` throwing `NotImplementedError()`.
- Verified: `./gradlew build` -- BUILD SUCCESSFUL, spotless/ktlint pass, all test classes green including the 8 new `WidgetServiceTest` cases (confirmed via `build/test-results/test/TEST-com.hl.service.service.WidgetServiceTest.xml`, `tests="8" failures="0" errors="0"`); re-ran `./gradlew build` independently after implementation to confirm no regressions.
- Review pass (see Review Triage Log) sent 5 `patch` findings back to the implementation agent: truncate `update`'s `updatedAt` to microseconds (matching `create`'s precision), catch `EmptyResultDataAccessException` in `delete` so a concurrent-delete race still surfaces `NotFoundException`, add `list()` tests for an empty repository and a second-page slice, document `FakeWidgetRepository.findAll`'s sort limitation, and tighten the `updatedAt` refresh assertion from `isAfterOrEqualTo` to `isAfter`. All 5 applied. Verified independently: `./gradlew build` -- BUILD SUCCESSFUL; `WidgetServiceTest` now `tests="10" failures="0" errors="0"`.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| `sprint-status.yaml` shows `in-progress` for this story while the spec is `in-review` and every task is complete (blind-hunter) | false | Per the workflow's own step-05, sprint status syncs to `review` once this step completes successfully with no loopback — this is expected in-flight state, matching the identical precedent logged in spec-2-3's own triage log. | false |
| Design Notes still says `WidgetRepository` "declares roughly fifteen methods" while Implementation Notes corrects this to ~26 (blind-hunter) | low | Confirmed: the two sections disagree; Implementation Notes' count (~26, including deprecated and `QueryByExampleExecutor` methods) is the accurate one. | rejected (fix edits the spec) |
| `WidgetService` is required to be `@Transactional` on mutating methods, but no test verifies the annotation is present (blind-hunter) | low | True — a pure unit test can't cheaply assert annotation presence without reflection, and no test attempts it. | rejected (unlikely to be hit in everyday use; fix requires non-trivial reflection-based test infra) |
| `update`'s not-found test doesn't assert the store is left unchanged (blind-hunter) | false | Confirmed by reading `WidgetService.update`: `findById(id).orElseThrow { ... }` throws before any mutation is reached — there is no code path that leaves a partial write, so nothing exists to assert. | false |
| `create`/`update` accept a blank/empty `name` with no validation (edge-case-hunter) | false | Per the epic's architecture (Story 2.6 owns Bean Validation on `WidgetRequest` at the DTO/controller layer), the service layer intentionally performs no business-rule validation — matches the established separation of concerns, not a defect. | false |
| `list()` tests only cover a single page that holds every widget — no empty-repository case, no multi-page boundary slicing (blind-hunter + edge-case-hunter overflow note, narrowed) | low | Confirmed: `WidgetServiceTest`'s one list test uses `PageRequest.of(0, 10)` with exactly 2 widgets; `FakeWidgetRepository.findAll`'s `start`/`end` slicing math is otherwise unexercised. | patch |
| `FakeWidgetRepository.findAll(pageable)` silently ignores `pageable.sort` (edge-case-hunter) | low | Confirmed: `findAll` always returns `store.values` in insertion order regardless of `pageable.sort`; undocumented in the test file. | patch |
| `WidgetService.delete`'s `existsById` → `deleteById` is a non-atomic check-then-act; a concurrent delete of the same id between the two calls surfaces a raw repository exception instead of `NotFoundException` (blind-hunter + edge-case-hunter) | medium | Confirmed: the two calls are separate, unsynchronized repository operations; Spring Data JPA's `deleteById` throws `EmptyResultDataAccessException` when the row is already gone, which today would propagate unhandled instead of the resource's single not-found signal. | patch |
| `update` sets `entity.updatedAt = Instant.now()` without the microsecond truncation `WidgetEntity.fromDomain` applies for `create` (edge-case-hunter) | medium | Confirmed: `WidgetEntity.kt`'s own `fromDomain` truncates to `ChronoUnit.MICROS` to match Postgres `timestamptz` precision; `WidgetService.update` bypasses that helper and sets the raw nanosecond-precision `Instant` directly, so the returned widget's `updatedAt` would diverge from what a real Postgres round-trip returns. | patch |
| Concurrent `update` calls can silently lose a write with no `@Version`/optimistic locking (edge-case-hunter) | maybe-false | Real if it occurs (would be medium — a silent lost update), but nothing in the epic's requirements or architecture calls for optimistic locking on widgets, and no test or usage in this story's scope demonstrates the race; would need a concurrency integration test hitting two simultaneous updates to settle. | defer |
| `pageNumber * pageSize` can overflow `Int` in the fake's slicing math (edge-case-hunter) | false | Not reachable via any call site in this diff: the only caller uses the literal `PageRequest.of(0, 10)`, nowhere near overflow scale. | false |
| `Pageable.unpaged()` would throw from the fake's `findAll` (edge-case-hunter) | false | Not reachable via any call site in this diff: no caller in this story passes `Pageable.unpaged()` to `WidgetService.list`. | false |
| `update`'s "refreshes updatedAt" test asserts `isAfterOrEqualTo`, which still passes if the refresh regresses to a no-op (verification-gap) | medium | Pre-verified by the verification-gap layer: `WidgetServiceTest.kt:355` uses `isAfterOrEqualTo(created.updatedAt)`, which does not fail on equality — a future regression that drops the `updatedAt` refresh would ship undetected. | patch |

## Design Notes

`WidgetRepository` is `JpaRepository<WidgetEntity, UUID>`, which declares roughly fifteen methods. The in-memory fake in `WidgetServiceTest` implements only the ones `WidgetService` actually calls (`save`, `findById`, `existsById`, `deleteById`, `findAll(Pageable)`), backed by a `LinkedHashMap<UUID, WidgetEntity>`; every other interface method throws `NotImplementedError()` since nothing under test invokes them. `findAll(Pageable)` slices the map's values (in insertion order) to build a `PageImpl`, since no real Postgres backs the fake.

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless pass; the new `WidgetServiceTest` cases pass alongside the existing suite.
