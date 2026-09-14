---
title: 'Add a REST resource guide and Example Slice removal'
type: 'feature'
created: '2026-09-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '7a51f6f15dc981d21406059162622a2daf68659c'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The `widgets` slice is meant to be the copy-paste pattern for every future resource and cleanly removable once a service has its own resources, but the README has no step naming exactly which files to create for a new resource, and no step describing how to remove the Example Slice without breaking the build.

**Approach:** Add two new README subsections: "Add a REST resource" — an ordered file/package checklist mirroring the `widgets` slice's exact concern-package layout — and "Remove the Example Slice" — the exact list of files to delete, including two files coupled to `widgets` by usage rather than by name (`FakeWidgetRepository.kt`, plus required edits to `OpenApiIT.kt` and `RedisDownIT.kt`, which hard-code the `/api/v1/widgets` HTTP path).

## Boundaries & Constraints

**Always:**
- New content lives under two new `###` subsections in `README.md`, placed after the existing "View API docs" subsection: "Add a REST resource" and "Remove the Example Slice".
- "Add a REST resource" names every file to create, by exact path and concern package, mirroring the `widgets` slice one-for-one: `model/`, `repository/` (`@Entity` + `JpaRepository` interface), `service/`, `controller/`, `dto/` (Request + Response), a new Flyway migration, Unit Tests, and an Integration Test extending `IntegrationTestBase`. States the "zero Plumbing" guarantee: no edit to build files, configuration, observability wiring, container config, or CI workflow.
- "Remove the Example Slice" lists the exact files to delete (every `Widget*` production and test file, `V1__create_widgets.sql`, and `FakeWidgetRepository.kt` — not `Widget*`-prefixed but widget-only test support) and calls out, by name, the two non-`Widget`-named test files that must be edited because they hard-code the `/api/v1/widgets` path (`OpenApiIT.kt`'s `/v3/api-docs` path assertion; `RedisDownIT.kt`'s request URI), with the concrete edit each needs (point at your own resource's equivalent path, or delete the assertion/test if you have not added a replacement resource yet).
- Both new steps are dry-run verified in this session (not committed) before being written up, so their instructions are provably accurate against this repository state.

**Never:**
- No actual deletion of the `widgets` slice from the repository in this story — Epic 2 and later epics still depend on it existing as the reference; this story only documents the two procedures.
- No new ArchUnit or other build-time enforcement of the concern-package convention — it stays documentation-only, matching the existing "Package layout" section's own language.
- No change to any other README section, source file, or build/config file.

</frozen-after-approval>

## Code Map

- `README.md` -- insert the two new `###` subsections after the existing "View API docs" subsection (the last one under "Local development").
- Files to name in "Remove the Example Slice" (verified by repo-wide `grep -rl widget`; the widgets slice's true footprint, not just the `Widget*` glob):
  - Production: `src/main/kotlin/com/hl/service/model/Widget.kt`, `repository/WidgetEntity.kt`, `repository/WidgetRepository.kt`, `service/WidgetService.kt`, `controller/WidgetController.kt`, `dto/WidgetRequest.kt`, `dto/WidgetResponse.kt`, `src/main/resources/db/migration/V1__create_widgets.sql`.
  - Test (delete): `src/test/kotlin/com/hl/service/WidgetHttpToStoreIT.kt`, `controller/WidgetControllerTest.kt`, `repository/WidgetEntityTest.kt`, `repository/WidgetRepositoryIT.kt`, `service/WidgetServiceCacheIT.kt`, `service/WidgetServiceCacheTest.kt`, `service/WidgetServiceTest.kt`, `support/FakeWidgetRepository.kt`.
  - Test (edit, not delete): `src/test/kotlin/com/hl/service/OpenApiIT.kt` (line asserting `$.paths` contains `/api/v1/widgets`), `src/test/kotlin/com/hl/service/RedisDownIT.kt` (two `/api/v1/widgets/$id` URI references). `SwaggerUiLocalProfileIT.kt` needs no edit — it has no widget-specific assertions.
  - Not in scope for the guide: doc-comment-only widget mentions in `application.yaml`, `PageResponse.kt`, `CacheConfig.kt` — no compile or runtime coupling, cosmetic only.
- Files to name in "Add a REST resource" (the widgets slice's own layout, as the mirror pattern): `model/Widget.kt`, `repository/WidgetEntity.kt` + `WidgetRepository.kt`, `service/WidgetService.kt`, `controller/WidgetController.kt`, `dto/WidgetRequest.kt` + `WidgetResponse.kt`, `db/migration/V1__create_widgets.sql`, plus the test files listed above as the Unit/Integration Test pattern to copy.

## Tasks & Acceptance

**Execution:**
- [x] `README.md` -- add "### Add a REST resource" subsection naming every file/package to create (mirroring the `widgets` slice), stating the zero-Plumbing guarantee and the shared-base-class Integration Test requirement -- satisfies AC1
- [x] `README.md` -- add "### Remove the Example Slice" subsection listing every file to delete plus the two required test edits (`OpenApiIT.kt`, `RedisDownIT.kt`) and the `./gradlew build` verification step -- satisfies AC2
- [x] Dry-run both new steps in this session (throwaway resource + `git status --short`; then simulate the removal + `./gradlew build`), discarding all throwaway changes afterward -- proves the written instructions are accurate without leaving the repo altered

**Acceptance Criteria:**
- Given the README "Add a REST resource" section, when followed for a second resource beyond `widgets`, then it names exactly which files to create and in which concern package, `git diff --name-only` shows only new resource files (no edit to build files, configuration, observability wiring, container config, or CI workflow), and the new resource's Integration Test extends the shared base class and adds nothing infrastructural.
- Given the README "Remove the Example Slice" section, when followed (delete the listed files, apply the two listed test edits), then `./gradlew build` stays green.

## Implementation Notes

- README's file/package list for "Add a REST resource" was derived directly from reading each of the eight `widgets` production files and cross-checked against `repo-wide grep -rli widget` so nothing in the slice's true footprint was missed.
- "Add a REST resource" states the migration file as `V<next>__create_<resource>.sql` rather than hard-coding `V2`, since a real second resource's version number depends on how many migrations already exist; the guide calls out that `widgets` is `V1` so the next one is `V2` in the still-two-migration state of this repo.
- `GlobalExceptionHandlerTest.kt` also matches `grep -rli widget` (it uses the string `"widget missing"` as an arbitrary example message in a synthetic controller/exception, per the spec's Code Map) but has no coupling to `WidgetController`/`WidgetRepository`/the `/api/v1/widgets` path, so it needed no mention in either new section — confirmed by inspection, consistent with the spec's "Not in scope" bullet for cosmetic-only mentions.
- Dry run 1 ("Add a REST resource"): created a full throwaway `Gadget` resource (model/repository entity+interface/service/controller/dto request+response/migration `V2__create_gadgets.sql`/one Unit Test/one Integration Test extending `IntegrationTestBase`) exactly per the new guide text. `git status --short` showed only the new files (plus this story's own in-flight `README.md`/sprint-status edits) — no build/config/container/CI file touched. `./gradlew compileKotlin compileTestKotlin` passed. All throwaway files were then deleted and `git status --short` reconfirmed clean of them.
- Dry run 2 ("Remove the Example Slice"): used a disposable `git worktree` (`/tmp/hl-scratch`, detached at the baseline commit) rather than editing the real working tree, so the main tree was never at risk. Deleted the 8 production + 8 test files listed in the guide, then applied both required edits: `OpenApiIT.kt`'s widgets-path assertion removed (no replacement resource in this dry run, per the guide's own fallback instruction) and `RedisDownIT.kt`'s widgets-path-dependent test method removed for the same reason, keeping its unrelated `readiness reports DOWN` test intact. `./gradlew build` in the worktree reported `BUILD SUCCESSFUL`. The worktree was then removed (`git worktree remove --force`) and deleted from disk; `git worktree list` and `git status --short` on the main tree confirm no trace of it remains and the `widgets` slice is fully unchanged there.
- No ArchUnit or other build-time enforcement was added; the new sections are documentation-only, matching the "Never" boundary.
- Post-review patch round (see Review Triage Log below): "Remove the Example Slice" now notes that `OpenApiIT.kt`/`RedisDownIT.kt`'s doc comments and `OpenApiIT.kt`'s test display name also reference widgets/the deleted `WidgetHttpToStoreIT` and must be updated alongside the two listed path edits; added a caveat that deleting `V1__create_widgets.sql` against a Postgres volume that already applied it fails startup loudly under `validate-on-migrate: true` unless the volume is reset first (`docker compose down -v`); and added a one-line note that the cosmetic "widget" mentions left in `application.yaml`, `PageResponse.kt`, and `CacheConfig.kt` are expected and can be ignored. "Add a REST resource" now tells the reader to check the latest migration on the integration branch before naming `V<next>` (parallel-branch collision guard), and the table header was reworded to "Concern package / location" so the migration row's filesystem path no longer breaks the column's stated semantics. All five are README-only wording changes; re-verified by reading the rendered section and a Python markdown-table column-count check (no mismatches). No source file covers this documentation-only change, so no test suite was run for this patch round.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| `RedisDownIT.kt`'s KDoc mentions `WidgetService.findById`; `OpenApiIT.kt`'s KDoc mentions the deleted `WidgetHttpToStoreIT` and its test name says "with widgets path reflected" — the guide's "edit, not delete" instructions for these two files name only the functional assertion/URI lines, leaving these comments and the test name stale (blind-hunter) | low | Confirmed: `RedisDownIT.kt:37-41`, `OpenApiIT.kt:21,33` read as described; neither is covered by the guide's two listed edits. Cosmetic only (doc comments/test names aren't compiled or type-checked); trivial one-line fix. | patch |
| Deleting `V1__create_widgets.sql` per the guide, then running the service against a Postgres volume that already applied it (e.g. an earlier `docker compose`/`bootRun` session), fails startup loudly under `validate-on-migrate: true` with no repair configured — the guide only verifies via `./gradlew build` (fresh Testcontainers), which never exercises this path (blind-hunter, edge-case-hunter) | medium | Confirmed: `application.yaml:15-19`'s own comment states no repair property/call exists, so a missing migration "fails startup loudly." A persisted local Postgres volume that already ran `V1` hits exactly this on the next `bootRun` after following the guide; `./gradlew build`'s Testcontainers have no such history so it can't catch this. | patch |
| The "Add a REST resource" migration-numbering instruction (`V<next>`) has no guard against two developers adding resources in parallel and picking the same version number (edge-case-hunter) | low | Real but narrow: this starter is normally worked story-by-story (per this project's own BMAD workflow), so concurrent resource additions are unlikely here; still a one-line, trivial caveat to add. | patch |
| The "Add a REST resource" table's "Concern package" column holds a filesystem path (`src/main/resources/db/migration`) for the migration row instead of a Kotlin package, breaking the column's stated semantics (blind-hunter) | low | Confirmed at the table's last row in the new README section; a labeling nit, trivial to reword. | patch |
| The guide never tells the reader that cosmetic "widget" mentions will remain in `application.yaml`, `PageResponse.kt`, and `CacheConfig.kt`'s comments after following it, so a post-cleanup grep for "widget" still gets hits (blind-hunter) | low | Confirmed those three comments exist and are unaddressed by the new sections; a stray-hit false alarm for a careful reader. Trivial one-line reassurance to add. | patch |
| README's existing "Package layout" section still claims "the starter ships with none of these classes yet," which the new sections' concrete widgets file lists contradict (blind-hunter) | low | Confirmed at `README.md`'s "Package layout" section, unchanged by this diff. Pre-existing since Story 2.1 first added `Widget*` classes — not caused by this story's diff. | defer |
| The Implementation Notes' phrase "still-two-migration state of this repo" misstates the current repo, which has exactly one migration file outside the (already-discarded) dry run (blind-hunter) | false | The phrase describes the dry run's transient state, not shipped README content, and Implementation Notes is an agent-owned internal record rather than documentation anyone follows — no reader is misled by it. Its only possible fix is editing this build's own spec, which the routing rules exclude outright regardless. | false |

## Verification

**Commands:**
- Dry run "Add a REST resource": created a minimal throwaway `Gadget` resource exactly per the new guide text, ran `git status --short` — confirmed only new files appeared — then discarded them with `rm`, reconfirmed via `git status --short`. Result: matches AC1.
- Dry run "Remove the Example Slice": applied the listed deletions and the two test edits inside a scratch `git worktree` (not the real tree), ran `./gradlew build` — `BUILD SUCCESSFUL in 12s`, `12 actionable tasks: 12 executed` — then removed the worktree entirely. Result: matches AC2. The `widgets` slice and its tests are unchanged in the actual repository (verified via `git status --short` and `git diff --stat` showing only `README.md` additions and the pre-existing `sprint-status.yaml`/spec-file changes).
