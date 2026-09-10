---
title: 'Format and lint enforced in the build'
type: 'chore'
created: '2026-09-10'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '86d21ecd09f34fdfc69be171b36d9fdf483c38bb'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 1.1 pinned Spotless 8.10.2 and ktlint 1.5.0 in the version catalog but wired nothing into the build. There is no format-apply task, no format-check, and `./gradlew check` does not fail on unformatted or lint-violating Kotlin — so style becomes a review topic and miswired formatting is not caught mechanically (FR-3, AD-22).

**Approach:** Apply the already-pinned Spotless plugin in `build.gradle.kts` and configure a single ktlint step (version read from the catalog) over all Kotlin in the repo — `src/**/*.kt` and the Gradle Kotlin DSL scripts. Add a root `.editorconfig` as the one home for ktlint rule configuration. Rely on the Spotless plugin auto-wiring `check` → `spotlessCheck`; `spotlessApply` is the format-apply task.

## Boundaries & Constraints

**Always:**
- Spotless applied via `alias(libs.plugins.spotless)`; the ktlint version comes from `libs.versions.ktlint` (`1.5.0`) so `gradle/libs.versions.toml` stays the single edit point (AD-22). No version literal for Spotless or ktlint in `build.gradle.kts`.
- `spotlessApply` rewrites offending files to canonical style; `spotlessCheck` reports violations, exits non-zero, and modifies nothing.
- `./gradlew check` (and therefore `./gradlew build`) fails when `spotlessCheck` fails — via the Spotless plugin's automatic `check` dependency, verified in the task graph, not a hand-written `dependsOn`.
- Spotless covers every Kotlin file in the repo: `src/**/*.kt` (via `kotlin {}`) and `*.gradle.kts` / `settings.gradle.kts` (via `kotlinGradle {}`).
- `.editorconfig` at the repo root sets `ktlint_code_style = ktlint_official`; all ktlint rule tuning lives there, not inline in the build script.
- The committed tree passes `spotlessCheck` clean: `spotlessApply` is run over the Story 1.1 seed files and any canonicalization it produces is committed.

**Never:**
- No detekt, Checkstyle, or any second static-analysis tool. Adding one later must be a `build.gradle.kts` + catalog change only, no restructuring.
- No new dependency or version literal outside `gradle/libs.versions.toml`. No changes to compiler strictness, existing catalog entries, the wrapper, or any runtime/source behavior.
- No CI workflow — that is Epic 4 (Story 4.2). No license headers, import-order overrides, or custom Spotless steps beyond the one ktlint step.

</frozen-after-approval>

## Code Map

- `build.gradle.kts` -- add `alias(libs.plugins.spotless)` to `plugins {}` and one `spotless { kotlin { … } kotlinGradle { … } }` block. Currently applies only `kotlin.jvm`, `kotlin.spring`, `spring.boot`. This is the entire build wiring for the story.
- `gradle/libs.versions.toml` -- already has `spotless = "8.10.2"`, `ktlint = "1.5.0"` under `[versions]` and the `spotless` plugin alias (`id = "com.diffplug.spotless"`). Reuse as-is; only the line-10 deferral comment ("wired into the build in Story 1.2") needs updating.
- `.editorconfig` -- new, repo root. ktlint reads it automatically.
- `src/main/kotlin/com/hl/service/StarterApplication.kt`, `src/test/kotlin/com/hl/service/StarterApplicationIT.kt` -- the only existing `.kt` files (both look ktlint-clean already); may be touched by `spotlessApply`. Runtime behavior must not change.
- `.gitignore` -- already ignores `.gradle/` and `build/`; Spotless produces no tracked artifacts. No change expected.
- Host facts: `./gradlew` at 9.7.1 with a JDK 25 toolchain that auto-provisions; `.gradle/9.7.1` cache is populated, so local runs need no network for the Spotless/ktlint jars if already resolved.

## Tasks & Acceptance

**Execution:**
- [x] `gradle/libs.versions.toml` -- update the comment on line 10 to state Spotless/ktlint are now wired (Story 1.2); leave every `[versions]`, `[plugins]`, `[libraries]` entry unchanged.
- [x] `build.gradle.kts` -- add `alias(libs.plugins.spotless)`; add `spotless { kotlin { target("src/**/*.kt"); ktlint(libs.versions.ktlint.get()) } kotlinGradle { target("*.gradle.kts"); ktlint(libs.versions.ktlint.get()) } }`. Do not add a manual `tasks.named("check")` dependency.
- [x] `.editorconfig` -- create at repo root: `root = true`; base whitespace rules (`charset = utf-8`, `end_of_line = lf`, `indent_style = space`, `indent_size = 4`, `insert_final_newline = true`, `trim_trailing_whitespace = true`); `[*.{kt,kts}]` with `ktlint_code_style = ktlint_official` and `ktlint_standard_no-wildcard-imports = enabled`.
- [x] `src/main/kotlin/com/hl/service/StarterApplication.kt`, `src/test/kotlin/com/hl/service/StarterApplicationIT.kt` -- run `./gradlew spotlessApply`; commit any resulting canonicalization (expected: none or trivial), verify behavior is unchanged.

**Acceptance Criteria:**
- Given unformatted or lint-violating Kotlin, when `./gradlew spotlessApply` runs, then the offending files are rewritten to canonical `ktlint_official` style.
- Given unformatted or lint-violating Kotlin, when `./gradlew spotlessCheck` (or `./gradlew check`) runs, then it lists the violations, exits non-zero, and leaves every file byte-identical.
- Given the committed, formatted tree, when `./gradlew check` runs, then `spotlessCheck` passes and `git diff` is empty afterwards.
- Given the build, when `build.gradle.kts` and `gradle/libs.versions.toml` are inspected, then the only lint machinery is Spotless → ktlint pinned in the catalog, and no detekt or other static-analysis tool is configured — adding one later would be config only.

## Implementation Notes

- **Files changed:** `build.gradle.kts` (spotless plugin alias + `spotless {}` block), `gradle/libs.versions.toml` (line-10 comment only — no version touched), `.editorconfig` (new, repo root). `spotlessApply` produced **no** changes to the two Story 1.1 seed `.kt` files — they were already `ktlint_official`-clean.
- **`check` wiring:** confirmed automatic. `./gradlew check --dry-run` lists `:spotlessKotlinCheck`, `:spotlessKotlinGradleCheck`, `:spotlessCheck` before `:test`; no manual `dependsOn` added. `./gradlew build` runs `:spotlessCheck` as part of `:check`.
- **ktlint version** passed as `libs.versions.ktlint.get()` (`1.5.0`) — catalog stays the single edit point (AD-22). No `detekt`/`checkstyle` anywhere (`grep` clean).
- **AC verification via scratch file** `src/main/kotlin/com/hl/service/ScratchSample.kt` (created, then deleted):
  - `val scratchSample=1` → `./gradlew spotlessCheck` → `BUILD FAILED`, reported `-val·scratchSample=1 / +val·scratchSample·=·1`, file left byte-identical.
  - `./gradlew spotlessApply` → rewrote it to `val scratchSample = 1`.
  - Note: `ktlint_official` bundles the `standard:filename` rule (source file names must be PascalCase) — an initial `_Scratch.kt` probe failed on the filename before the operator-spacing lint; renamed the probe to satisfy it. This rule now applies to all future `src/**/*.kt`.
- **Post-change state:** `./gradlew spotlessCheck` and `./gradlew build` both `BUILD SUCCESSFUL`; `git diff` clean for `src/`.

### Review pass 1 patches (2026-09-10)

- `.editorconfig`: added `max_line_length = 140` under `[*.{kt,kts}]` (makes the ktlint_official line-length rule deterministic — "all tuning lives here"); added `[*.{yml,yaml,json}] indent_size = 2` and `[*.md] trim_trailing_whitespace = false / max_line_length = off` so the global `[*]` whitespace rules don't strip Markdown hard line breaks or reindent YAML/JSON.
- `build.gradle.kts`: hoisted the catalog lookup to `val ktlintVersion = libs.versions.ktlint.get()` (was duplicated); broadened `kotlinGradle` target from `*.gradle.kts` to `**/*.gradle.kts` so future `buildSrc/` or nested Gradle Kotlin DSL scripts are covered, matching the spec's "every Kotlin file in the repo" boundary.
- `gradle/libs.versions.toml`: comment reworded again to `# spotless/ktlint: consumed by the spotless {} block in build.gradle.kts (wired in Story 1.2).` — the prior wording still read as a forward deferral.
- Re-verified: `./gradlew spotlessCheck`, `./gradlew check --dry-run` (spotless tasks present), `./gradlew build` all green; `src/` diff still clean.

## Spec Change Log

## Review Triage Log

### Pass 1 (2026-09-10)

Layers: blind-hunter (N=2, filed 10), edge-case-hunter (5), verification-gap (1 gap + 1 other). Diff `86d21ec..working tree`. No `intent_gap` / `bad_spec` → no loopback; `review_loop_iteration` unchanged (0).

| # | Finding (layer) | Verdict | Evidence | Route |
|---|---|---|---|---|
| 1 | `.editorconfig` global `[*] trim_trailing_whitespace = true` strips Markdown hard line breaks in the repo's many `.md` files (blind-hunter, edge-case-hunter, verification-gap) | low | Confirmed: `[*]` applies repo-wide; only `[*.{kt,kts}]` is tool-enforced. New file introduced by this change. Fix is additive config. | patch |
| 2 | No `[*.{yml,yaml,json}]` 2-space override; later stories add YAML/JSON where 4-space is wrong (blind-hunter, edge-case-hunter) | low | Confirmed: `.editorconfig` is established here as the single whitespace home; 2-space is the YAML/JSON norm. | patch (grouped with #1) |
| 3 | `max_line_length` unset — ktlint_official line-length rules fall back to an implicit default, undercutting "all tuning lives here" + determinism NFR (blind-hunter) | low | Confirmed: rule reads `max_line_length` from `.editorconfig`; absent = nondeterministic. One-line fix. | patch (grouped with #1) |
| 4 | `libs.versions.toml` comment still reads as a forward deferral; Task 1 asked for "now wired" (blind-hunter) | low | Confirmed: "wired ... in Story 1.2" is ambiguous tense. Direct correction. | patch |
| 5 | `kotlinGradle target("*.gradle.kts")` is non-recursive — future `buildSrc/`/nested scripts unformatted, contradicts spec boundary "every Kotlin file in the repo" (blind-hunter, edge-case-hunter) | low | Confirmed: glob is root-only; no present defect (only root scripts exist) but the stated invariant and the glob disagree. Trivial fix `**/*.gradle.kts`. | patch |
| 6 | `libs.versions.ktlint.get()` duplicated across the two spotless sub-blocks (blind-hunter) | low | Confirmed: two identical catalog lookups; hoist to a local `val`. Cosmetic DRY. | patch |
| 7 | Lint gate (`check` → `spotlessCheck`) has no automated regression guard; a future Spotless bump could silently drop enforcement (verification-gap, blind-hunter) | maybe-false→defer | Real long-term rot risk. The intent defers all CI to Story 4.2 and the repo has no build-logic test infra (TestKit is disproportionate now). verification-gap filed it `defer`. | defer |
| 8 | `ktlint_official`'s `standard:filename` (PascalCase source names) is now build-enforced with no developer-facing note until the README (blind-hunter, verification-gap) | low | Confirmed via the scratch-file probe. Convention docs are owned by Story 1.8. | defer |
| 9 | No `.gitattributes` to back `end_of_line = lf` for Windows contributors (blind-hunter, edge-case-hunter) | false | Out of scope: epic NFR scopes platforms to Linux + macOS only; already rejected twice in the Story 1.1 review. Pre-existing, not caused by this change. | rejected |
| 10 | `kotlin { target("src/**/*.kt") }` excludes `src/**/*.kts` (edge-case-hunter) | false | Speculative: no `.kts` under `src/` exists or is planned; the `kotlin {}` block is for `.kt` sources. | rejected |
| 11 | `libs.versions.ktlint.get()` should be `.orElse("1.5.0").get()` to avoid `NoSuchElementException` if the key is removed (edge-case-hunter) | false | The loud failure is correct ("fail loud, never silent degraded mode"), and the suggested literal fallback would reintroduce a third-party version literal outside the catalog — a direct AD-22 violation. | rejected |
| 12 | `ktlint_standard_no-wildcard-imports = enabled` is redundant under `ktlint_official` (blind-hunter) | low | Redundant ≠ incorrect. The line was requested by spec Task 3, is harmless, and self-documents a rule the story cares about. No named harm. | rejected |

## Design Notes

- The Spotless Gradle plugin makes `check` depend on `spotlessCheck` automatically; confirm with `./gradlew check --dry-run` showing `:spotlessCheck` rather than adding a manual `dependsOn`.
- ktlint code style: `ktlint_official` (ktlint's own recommended modern style) chosen over `intellij_idea` / `android`. Reversible — one line in `.editorconfig`. Recorded as a decision, not an open question.
- ktlint version is passed as `libs.versions.ktlint.get()` so the catalog remains the single edit point (AD-22); do not hard-code `"1.5.0"` in the build script.
- Seed files from Story 1.1 may be reformatted by `spotlessApply` — that is this story's job, not scope creep. If `ktlint_official` flags anything in them, let `spotlessApply` fix it; do not disable rules or hand-fight the formatter.

## Verification

**Commands:**
- `./gradlew spotlessCheck` -- expected: `BUILD SUCCESSFUL` on the committed tree; `git diff --quiet` still clean afterwards.
- `./gradlew check --dry-run` -- expected: task graph lists `:spotlessCheck` (proves it gates `check`).
- Scratch violation: create `src/main/kotlin/com/hl/service/_Scratch.kt` with `val x=1`, run `./gradlew spotlessCheck` -- expected: non-zero exit, violation reported, file unchanged; then `./gradlew spotlessApply` rewrites it to `val x = 1`; delete the scratch file.
- `./gradlew build` -- expected: `BUILD SUCCESSFUL` (compile + test + `spotlessCheck` all green).
- `grep -niE 'detekt|checkstyle' build.gradle.kts gradle/libs.versions.toml` -- expected: no matches.
