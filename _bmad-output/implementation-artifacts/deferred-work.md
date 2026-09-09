- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-gradle-build-skeleton-with-a-version-catalog.md`
  summary: Add a top-level LICENSE file — the repository is meant to be cloned/forked but carries no license.
  evidence: blind-hunter review pass 1 flagged the omission; no Epic 1–5 story adds a LICENSE. Not caused by Story 1.1; a project-setup gap.

## Deferred from: code review of spec-1-1-gradle-build-skeleton-with-a-version-catalog (2026-09-09)

- No README in the repo after Story 1.1 introduces the whole build system: no `./gradlew build` / `bootRun` instructions, no JDK 25 prerequisite, no note that the toolchain auto-provisions via the foojay resolver. Owned by Story 1.8 (Document the local development loop).
- No CI workflow runs `./gradlew build` on push/PR, and `gradle-wrapper.jar` itself is not integrity-checked (only the distribution `distributionSha256Sum` is pinned). Owned by Epic 4 — Story 4.2 (CI on every PR) should also add Gradle's `wrapper-validation-action`.
