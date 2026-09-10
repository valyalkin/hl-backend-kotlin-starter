- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-gradle-build-skeleton-with-a-version-catalog.md`
  summary: Add a top-level LICENSE file — the repository is meant to be cloned/forked but carries no license.
  evidence: blind-hunter review pass 1 flagged the omission; no Epic 1–5 story adds a LICENSE. Not caused by Story 1.1; a project-setup gap.

## Deferred from: code review of spec-1-1-gradle-build-skeleton-with-a-version-catalog (2026-09-09)

- No README in the repo after Story 1.1 introduces the whole build system: no `./gradlew build` / `bootRun` instructions, no JDK 25 prerequisite, no note that the toolchain auto-provisions via the foojay resolver. Owned by Story 1.8 (Document the local development loop).
- No CI workflow runs `./gradlew build` on push/PR, and `gradle-wrapper.jar` itself is not integrity-checked (only the distribution `distributionSha256Sum` is pinned). Owned by Epic 4 — Story 4.2 (CI on every PR) should also add Gradle's `wrapper-validation-action`.

## Deferred from: code review of spec-1-2-format-and-lint-enforced-in-the-build (2026-09-10)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-format-and-lint-enforced-in-the-build.md`
  summary: The README (Story 1.8) must document that `ktlint_official`'s `standard:filename` rule requires PascalCase Kotlin source file names — a lowercase or underscore-prefixed `.kt` file now fails `./gradlew check`.
  evidence: Story 1.2 review (blind-hunter, verification-gap) — adopting `ktlint_official` activated `standard:filename`; there is no developer-facing note until the README lands. Convention documentation is owned by Story 1.8.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-format-and-lint-enforced-in-the-build.md`
  summary: CI (Story 4.2) should prove the lint gate actually fails the build — plant a known formatting / wildcard-import violation and assert `./gradlew build` exits non-zero — not merely run `./gradlew build` against an already-clean tree.
  evidence: Story 1.2 review (verification-gap, blind-hunter) — the `check` → `spotlessCheck` dependency is supplied implicitly by the Spotless plugin and verified only by a manual, ephemeral check; a future Spotless bump could silently drop enforcement with nothing catching it. A Gradle TestKit module is disproportionate now; Story 4.2 owns CI.
