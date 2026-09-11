---
title: 'Run the service locally on the local profile'
type: 'feature'
created: '2026-09-11'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `./gradlew bootRun` does not default to the `local` Spring profile, so a developer must remember to export `SPRING_PROFILES_ACTIVE=local` themselves before the service will pick up `application-local.yaml`'s Compose-stack credentials — otherwise startup fails loudly on the unset `SPRING_DATASOURCE_*` placeholders.

**Approach:** Configure the `bootRun` Gradle task so it defaults `SPRING_PROFILES_ACTIVE` to `local` only when the invoking shell hasn't already set it, so a bare `./gradlew bootRun` (with the Compose stack up) connects to Postgres and Redis, applies migrations, and reports `GET /actuator/health` `UP` — while an explicit `SPRING_PROFILES_ACTIVE` from the caller still wins.

</frozen-after-approval>

## Implementation Notes

- **`build.gradle.kts`** -- added `tasks.named<BootRun>("bootRun") { ... }`: sets `SPRING_PROFILES_ACTIVE=local` as an environment variable on the forked `bootRun` JVM only when the invoking shell hasn't already set it. An explicit caller-set env var is left untouched and wins. Imports `org.springframework.boot.gradle.tasks.run.BootRun` alongside the existing `SpringBootPlugin` import. Reads the caller's env var via `providers.environmentVariable("SPRING_PROFILES_ACTIVE")` rather than `System.getenv(...)` so the check is a tracked Gradle configuration-cache input (post-review patch, see Review Triage Log).
- **`src/main/resources/application-local.yaml`** -- updated the breadcrumb comment left by Story 1.6 ("Story 1.7 makes it the bootRun default") to describe the now-implemented default instead of pointing forward to it. No functional change to this file.
- No new source files or tests: this is Gradle task wiring, not application code covered by `ApplicationContextRunner`/unit tests. Verified manually instead (see Verification), matching the precedent set by Stories 1.5/1.6's manual readiness checks.
- Full manual verification run (2026-09-11, Docker available): `docker compose up -d`, waited for both containers `healthy`, then `./gradlew bootRun` with `SPRING_PROFILES_ACTIVE` unset in the shell — `GET /actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`, `GET /actuator/health/readiness` → `UP`, `GET /actuator/health/liveness` → `UP`. Confirms the `local` profile activated by default (unset profile would otherwise fail loudly on the datasource placeholders per Story 1.5). Repeated after the post-review `providers.environmentVariable` patch with the same result. Process and Gradle daemon stopped and `docker compose down` afterward; no lingering containers or processes.
- Override check: `SPRING_PROFILES_ACTIVE=default ./gradlew bootRun` (Compose stack up) failed the build with `IllegalArgumentException: 'url' must start with "jdbc"` while creating the Hikari `DataSource` — confirms the caller's explicit profile wins over the `local` default (no fallback to the Compose-stack credentials).
- `./gradlew spotlessApply build` -- green, no reformatting, all existing tests (`DatasourceFailFastTest`, `RedisHealthDownTest`, `StarterApplicationIT`, `LivenessProbeIT`) still pass Docker-free.
- Scope diff: `build.gradle.kts` (the `bootRun` default), `src/main/resources/application-local.yaml` (breadcrumb comment update, no functional change), plus the tracking-doc edits `_bmad-output/implementation-artifacts/sprint-status.yaml` (story status `backlog` → `in-progress`) and this spec file. No other file touched.
- Not committed while this note was written (mid-implementation, before review ran); committed after the review below finished, per this workflow's standing commit step.

## Review Triage Log

Iteration 0 — one layer (blind-hunter, kB≈5.88 → N=3, 10 findings returned). Outcome: 4 patch, 1 defer, 5 reject/false.

**Patch:**
- **`context` list dropped `ARCHITECTURE-SPINE.md`**, unlike every prior story in this series. Low. Fix: re-added it.
- **`build.gradle.kts`'s `bootRun` block read `System.getenv(...)` directly** — an untracked Gradle configuration-cache input; a cached configuration wouldn't notice the env var changing. Low (config cache isn't enabled in this project today, but the build's own output nudges toward it, and the fix is a one-line swap). Fix: switched to `providers.environmentVariable(...)`; re-verified default and override behavior both still hold.
- **Implementation Notes said "committed ... once review finished (see Commit step)" while review hadn't run yet** and no "Commit step" section exists in this document (it refers to this workflow's own step, not a spec section) — confusing and, at time of writing, inaccurate. Medium (misleading provenance note). Fix: reworded to state it plainly and only claim what was true when written.
- **`sprint-status.yaml`'s status-flip wasn't listed in Implementation Notes' scope accounting**, unlike spec-1-6's explicit "Scope diff" line. Low. Fix: added a Scope diff line covering every touched file, tracking docs included.

**Defer** (see `deferred-work.md`):
- **No automated regression guard on the `bootRun` default-profile behavior** — a future edit to the Gradle block could silently break it with nothing failing in CI. Real gap; fixing it needs Gradle TestKit infra this project doesn't have yet, beyond this story's footprint (mirrors the manual-verification precedent Stories 1.5/1.6 set for build/infra wiring).

**Rejected:**
- **`baseline_commit` frontmatter field missing.** False. That field is set and consumed only by the `dispatch` route (`step-03-implement.md` writes it; `step-04-review.md` diffs against it for review subagents). The `oneshot` route used here reviews the live worktree directly — it has no use for it.
- **Missing `Tasks & Acceptance` section.** False. The `oneshot` route's own instructions explicitly permit deleting every section but `Intent` and `Implementation Notes`.
- **Missing `Boundaries & Constraints` / `Code Map` / `Design Notes` / `Verification` / `Spec Change Log` sections.** False, same reason as above — `oneshot`-route design, not an omission.
- **`Review Triage Log` absent.** False at the time the reviewer looked — this workflow adds that section at the Finalize-Spec step, which runs after review, not before.
- **First use of `route: 'oneshot'` in this story series, undocumented.** False/not-a-defect. Every prior story in the epic had either an intent gap or a larger footprint that ruled out `oneshot`; this story's Approach and footprint genuinely qualify, and `route` itself is the record of that decision — no separate justification text is required by the workflow.
