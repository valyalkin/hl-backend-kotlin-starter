---
title: 'Document the local development loop'
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

**Problem:** The README documents package layout and how to start the Compose stack, but has no single section walking a developer (or coding agent) from a clean clone to a running service, a passing build, and a passing test run — so nothing in the repo guarantees the local loop actually works end to end as documented.

**Approach:** Add a "Local development" section to the README covering prerequisites, bringing up the Compose stack, running the service (`./gradlew bootRun`, `local` profile by default per Story 1.7), running tests, and viewing API docs — noting that Swagger UI/OpenAPI ships in Epic 2 and there is no local trace viewing in v1. Fold the existing "Local dependencies" content into this section rather than duplicating it.

</frozen-after-approval>

## Implementation Notes

- **`README.md`** -- replaced the "Local dependencies" section with a new "Local development" section: an intro sentence stating the top-to-bottom-on-a-clean-machine guarantee, then five subsections — Prerequisites (Docker, a JDK for the wrapper's own bootstrap — JDK 25 is provisioned by the Gradle toolchain), Bring up dependencies (the prior "Local dependencies" content, unchanged, folded in), Run the service (`./gradlew bootRun` defaulting to `local` per Story 1.7, `curl .../actuator/health`, how to override the profile), Run tests (`./gradlew build`, Docker-free today via the test-scope auto-configuration excludes, notes Epic 2 adds Testcontainers-backed ITs), and View API docs (not available yet — OpenAPI/Swagger UI land with Epic 2's first REST resource; no local trace viewing in v1, per epic-1-context and PRD §4.8).
- No other files changed — pure documentation.
- Full manual verification (2026-09-11, Docker available): `docker compose up -d` → both containers `healthy`; `./gradlew bootRun` with `SPRING_PROFILES_ACTIVE` unset → `GET /actuator/health` returned `{"groups":["liveness","readiness"],"status":"UP"}` within seconds; stopped the app, ran `./gradlew --stop` and `docker compose down` — no lingering containers or processes. Separately, `./gradlew spotlessCheck build` passed Docker-free (all existing tests green), confirming the "Run tests" subsection's claim.
- Scope diff: `README.md` only, plus this spec file and `sprint-status.yaml` (status `backlog` → `in-progress` → `review`, synced by the workflow).
- Post-review patch: reworded the Redis claim (it has no profile-specific credentials — it connects via the plain `localhost:6379` default already in `application.yaml`, not anything in `application-local.yaml`); added the missing minimum JDK version to run Gradle itself (17–26, confirmed against Gradle's own compatibility docs) alongside the existing JDK-25-toolchain note; added teardown steps (`Ctrl-C` for `bootRun`, `docker compose down`) that were missing from an otherwise complete loop; added brief port-conflict pointers for 5432/6379/8080; fixed the sample `curl` output to match the actual verified response shape (`groups` key included); added an Epic 3 pointer to the tracing note, mirroring the existing Epic 2 pointer style.

## Review Triage Log

Iteration 0 — one layer (blind-hunter, kB≈6.7 → N=3, 10 findings returned). Outcome: 6 patch, 1 false, 2 reject.

**Patch:**
- **Redis claim implied `application-local.yaml` carries Redis credentials too** — it only overrides the datasource; Redis connects via the plain default already in `application.yaml`. Low (misleading, not broken). Fix: reworded to say so explicitly.
- **No teardown step documented** despite the section promising a complete top-to-bottom loop. Low/Medium. Fix: added "stop `bootRun` (`Ctrl-C`), then `docker compose down`."
- **Prerequisites didn't state a minimum JDK version** for bootstrapping the Gradle wrapper itself (distinct from the toolchain's JDK 25). Low. Fix: added "17–26", confirmed via Gradle's own compatibility docs (docs.gradle.org/9.7.1/userguide/compatibility.html).
- **No mention of how to stop the foregrounded `bootRun` process** before moving to the next section. Low. Fix: folded into the teardown fix above plus an inline `Ctrl-C` note.
- **No pointer for port conflicts** on 5432/6379/8080, a plausible first-run snag on a dev machine that already runs Postgres/Redis/another Spring app. Low. Fix: added one-sentence pointers in both the "Bring up dependencies" and "Run the service" subsections.
- **Sample `curl` output omitted the `groups` key** that the real, verified response actually leads with. Low. Fix: replaced the sample with the exact verified response body.
- **Tracing note gave no pointer to when/where.** Low. Fix: added an "(Epic 3)" pointer mirroring the existing Epic 2 Swagger pointer.

**Rejected:**
- **No repeated "from repo root" context on later command blocks.** Low value; stated once at the top of the section and commands are conventionally run from repo root — repeating it on every snippet would add clutter for a case readers rarely trip on.
- **No closing "what's next" pointer after "View API docs."** Out of scope for this story's AC (document the local loop, not build an onboarding guide); the README has no such pattern elsewhere either.

**False:**
- **Sprint status left at `in-progress` instead of `review`.** False at the time the reviewer looked — this workflow's own Finalize Spec step moves it to `review` after the review pass completes, which happens after this note was reviewed.
