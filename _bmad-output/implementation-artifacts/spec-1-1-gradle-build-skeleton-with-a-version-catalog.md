---
title: 'Gradle build skeleton with a version catalog'
type: 'chore'
created: '2026-09-09'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'f1e22a43e6977e083f5fa08aca55b40bfd0f2fe4'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repository has no build system — a fresh clone cannot compile or produce an artifact. Every later Epic 1 story needs a reproducible, single-module Gradle build with every dependency version pinned in one place.

**Approach:** Add a single-module Gradle Kotlin DSL build: committed wrapper (Gradle 9.7.1), a JDK 25 toolchain independent of the developer's JDK, strict Kotlin compilation, and one version catalog (`gradle/libs.versions.toml`) as the single edit point for non-BOM-managed versions. Apply the Spring Boot plugin plus a minimal `@SpringBootApplication` so `./gradlew build` yields a runnable Spring Boot jar.

## Boundaries & Constraints

**Always:**
- Wrapper only: `./gradlew build` succeeds with the committed wrapper, no local Gradle install, distribution pinned to 9.7.1.
- JVM fixed by a Gradle toolchain at JDK 25, auto-provisioned via the `foojay-resolver-convention` settings plugin (hosts may carry only some other JDK).
- `gradle/libs.versions.toml` is the only place a third-party version literal appears — pinning Kotlin 2.4.0, the Kotlin/Gradle plugins, springdoc-openapi 3.1.1, Spotless 8.10.2 / ktlint 1.5.0. Sole exception: the `foojay-resolver-convention` version is a literal in `settings.gradle.kts` (settings plugins evaluate before the catalog); not duplicated into the catalog.
- Anything the Spring Boot 4.1.1 BOM manages carries no version literal in any build file. No dynamic version (`+`, `latest.release`) anywhere.
- Kotlin only: `src/main` and `src/test` hold `.kt` files, no `.java`. Compiler runs `-Xjsr305=strict` and `allWarningsAsErrors = true` for this module.
- Root package `com.hl.service`. Service name = Gradle project name, set only in `settings.gradle.kts`.
- Spring Boot BOM imported via `platform(SpringBootPlugin.BOM_COORDINATES)` — no `io.spring.dependency-management` plugin.

**Never:**
- No concern-package tree, webmvc/actuator starters, or health wiring — Story 1.3.
- No Spotless/ktlint task wiring, no `.editorconfig` — catalog only *pins* them; enforcement is Story 1.2.
- No Compose stack, `.env`, datasource, Flyway migration, or Redis — Stories 1.4–1.6.
- No multi-module split; no detekt or other static analysis. Do not commit a JDK or Gradle distribution.

</frozen-after-approval>

## Code Map

Greenfield — no existing source or build files; nothing to preserve.

- `.../architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md` -- authoritative versions: **Stack** table, **AD-22** (catalog pins only what the BOM does not), **AD-23** (Kotlin strictness), **Structural Seed** (target file tree).
- `_bmad-output/implementation-artifacts/epic-1-context.md` -- distilled Epic 1 constraints and story boundaries.
- `_bmad-output/planning-artifacts/epics.md` -- Story 1.1 source AC.
- Host facts: `gradle` 8.13 at `/opt/homebrew/bin/gradle` (can generate the 9.7.1 wrapper); installed JDKs are 17–21 only — toolchain auto-provisioning is required, not optional.

## Tasks & Acceptance

**Execution:**
- [x] `settings.gradle.kts` -- create -- `rootProject.name = "hl-backend-kotlin-starter"`; `pluginManagement` + `dependencyResolutionManagement` repos (`gradlePluginPortal()`, `mavenCentral()`); `plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }`.
- [x] `gradle/libs.versions.toml` -- create -- `[versions]`/`[plugins]`/`[libraries]` per Boundaries: Kotlin 2.4.0, `kotlin-jvm` + `kotlin-spring` plugins, `org.springframework.boot` 4.1.1, `com.diffplug.spotless` 8.10.2 + ktlint 1.5.0, `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.1.1 (declared, unreferenced until Epic 2).
- [x] `build.gradle.kts` -- create -- apply `kotlin-jvm`, `kotlin-spring`, `org.springframework.boot`; `kotlin { jvmToolchain(25) }`; `compilerOptions { freeCompilerArgs.add("-Xjsr305=strict"); allWarningsAsErrors = true }`; `dependencies { implementation(platform(SpringBootPlugin.BOM_COORDINATES)); implementation("org.springframework.boot:spring-boot-starter") }`; `mavenCentral()` repo.
- [x] `gradle/wrapper/gradle-wrapper.properties` + `gradle-wrapper.jar` + `gradlew` + `gradlew.bat` -- generate via `gradle wrapper --gradle-version 9.7.1 --distribution-type bin`; commit all four; `gradlew` executable.
- [x] `src/main/kotlin/com/hl/service/StarterApplication.kt` -- create -- minimal `@SpringBootApplication` class + `fun main(args)` calling `runApplication<StarterApplication>(*args)`, so `bootJar` resolves a main class. Story 1.3 extends it with the concern packages and health wiring.
- [x] `.gitignore` -- create -- ignore `.gradle/`, `build/`, `.idea/`, `*.iml`.

**Acceptance Criteria:**
- Given a fresh clone with only some JDK and Docker, when `./gradlew build` runs, then it succeeds with the committed wrapper at Gradle 9.7.1 and no local Gradle install, using a JDK 25 toolchain regardless of the default JDK.
- Given `gradle/libs.versions.toml`, when inspected, then Kotlin 2.4.0, the Kotlin/Gradle plugins, springdoc-openapi 3.1.1 and Spotless 8.10.2 / ktlint 1.5.0 are pinned there; no build file carries a version literal for a BOM-managed dependency or a dynamic version; the only third-party version literal outside the catalog is `foojay-resolver-convention` in `settings.gradle.kts`.
- Given `src/main` and `src/test`, when the build compiles them, then only `.kt` sources exist, the compiler runs `-Xjsr305=strict`, and any compiler warning fails the build.
- Given `./gradlew build` completes, when `build/libs/` is inspected, then a runnable Spring Boot jar is produced and `java -jar` on it starts a Spring context with no stack trace.

### Review Findings

Ad hoc code review (2026-09-09), 4 layers — blind-hunter, edge-case-hunter, verification-gap, acceptance-auditor. Diff `f1e22a4..1b21d29`. edge-case-hunter and verification-gap returned no findings.

- [x] [Review][Decision→Patch] Smoke test `StarterApplicationTests` matched neither AD-21 test shape (named `…Tests`, booted `@SpringBootTest` with no `@Tag`). Resolved: renamed to `StarterApplicationIT` + `@Tag("integration")`, forward-consistent with AD-21's integration shape. [src/test/kotlin/com/hl/service/StarterApplicationIT.kt:9]
- [x] [Review][Patch] JUnit `@Test` written fully-qualified (`@org.junit.jupiter.api.Test`) instead of imported. Resolved: `import org.junit.jupiter.api.Test` + `@Test`. [src/test/kotlin/com/hl/service/StarterApplicationIT.kt:10]
- [x] [Review][Patch] `spotless`/`ktlint` catalog entries had no deferral note like `springdoc`'s. Resolved: added `# spotless/ktlint: pinned here now, wired into the build in Story 1.2.` [gradle/libs.versions.toml:10]
- [x] [Review][Defer] No README anywhere in the repo after the build system is introduced — no `./gradlew build` / `bootRun` steps, no JDK 25 / foojay auto-provision note [repo root] — deferred: owned by Story 1.8 (Document the local development loop); not caused by this change
- [x] [Review][Defer] No CI runs `./gradlew build` on push/PR and `gradle-wrapper.jar` itself is unverified (only `distributionSha256Sum` is pinned) [.github/workflows/] — deferred: owned by Epic 4 (Story 4.2 CI + Gradle `wrapper-validation-action`); not caused by this change

**Rejected**

- [blind-hunter] No `gradle.properties` (daemon JVM args, parallel / build-cache / configuration-cache) — low: `./gradlew build` verified green without it, no defect met in everyday use, fix adds a new multi-setting file. Reasonable later enhancement, not a review defect.
- [blind-hunter] No `.gitattributes` (`gradlew` CRLF corruption on Windows `core.autocrlf=true`) — low: epic NFR scopes platforms to Linux + macOS only. Already rejected in Pass 1 (row 9).
- [blind-hunter] `.gitignore` omits `.env` / `application-local.*` / `*.log` / IDE metadata — false for the first two: epic context has `.env` (image tags) and `application-local.yaml` (throwaway creds) committed by design, so ignoring them would be wrong; `*.log` / `bin/` / `out/` / `.vscode/` are speculative and were rejected in Pass 1 (row 4).
- [blind-hunter] No `.editorconfig` for ktlint rules — false / out of scope: spec Boundaries explicitly defer `.editorconfig` to Story 1.2.
- [blind-hunter] No `src/main/resources/application.yaml` / `spring.application.name` — false / out of scope: `contextLoads()` boots green without it; config files are owned by Stories 1.3 / 1.5 / 1.7.
- [blind-hunter] JUnit not surfaced through the version catalog — false: JUnit is Spring Boot BOM-managed, so a catalog version literal would violate the "no version literal for BOM-managed deps" constraint.
- [blind-hunter] `@SpringBootApplication` scans `com.hl.service.*`, narrower than `group = "com.hl"` — false: all seven concern packages live under `com.hl.service` by invariant; `group` is the artifact coordinate, not a scan root.
- [blind-hunter] Only `mavenCentral()`, no Spring milestone repo; `allWarningsAsErrors` makes future deprecations fatal — false: Boot 4.1.1 resolves from Central (build verified green); `allWarningsAsErrors` is a frozen spec constraint with a documented override path in Design Notes.
- [acceptance-auditor] Tasks & Acceptance line for `build.gradle.kts` still names a `mavenCentral()` repo block the shipped file (correctly) omits — real spec drift from Pass 1 patch #6, but the only fix edits the spec under review; recorded here as spec hygiene, not a code finding.

## Implementation Notes

- All six files created as specced. Build plugins applied via `alias(libs.plugins.*)`; BOM via `platform(SpringBootPlugin.BOM_COORDINATES)`; no `io.spring.dependency-management`.
- Verified on a machine with only JDKs 17–21: `./gradlew build` → `BUILD SUCCESSFUL`; foojay auto-provisioned Temurin **25.0.4.1** into `~/.gradle/jdks`; `compileKotlin` runs clean under `--warning-mode=all` (the Design-Notes Kotlin-2.4.0→JDK-25 warning risk did **not** materialise — no `jvmTarget`/`-Xjdk-release` override needed). `bootJar` bytecode major version = 69 (Java 25). `java -jar build/libs/hl-backend-kotlin-starter.jar` on JDK 25 → Spring Boot 4.1.1 context starts ("Started StarterApplicationKt in ~0.5s"), exits cleanly, no stack trace.
- Version-literal scan: only `foojay-resolver-convention "1.0.0"` in `settings.gradle.kts`; none in `build.gradle.kts`; no dynamic versions.
- Wrapper generation: host Gradle 8.13 cannot apply the Spring Boot 4.1.1 plugin (needs Gradle ≥ 8.14), so `gradle wrapper --gradle-version 9.7.1` was run with `build.gradle.kts` moved aside, then restored. Committed wrapper files are correct; real builds run on 9.7.1.
- Running the built jar requires JDK 25 (bytecode target 25); older JDKs give `UnsupportedClassVersionError`. Consistent with the toolchain fixing the JVM; the "only a JDK" AC is met because `./gradlew build` provisions 25 itself. First build on a fresh clone needs network (Gradle dist, JDK 25, dependencies).
- `spotless` plugin alias + `ktlint` version are declared in the catalog but unreferenced — Story 1.2 wires them.

### Review pass 1 patches (2026-09-09)

- `.gitignore`: added `.kotlin/` and `.DS_Store`.
- `gradle/wrapper/gradle-wrapper.properties`: pinned `distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` (official gradle-9.7.1-bin.zip checksum); wrapper validates it on run.
- Repository config single-sourced: `settings.gradle.kts` `dependencyResolutionManagement` now has `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` + `mavenCentral()` only (`gradlePluginPortal()` stays in `pluginManagement`); the `repositories {}` block was removed from `build.gradle.kts`.
- `build.gradle.kts`: added `group = "com.hl"`, `version = "0.0.1-SNAPSHOT"` — bootJar is now `hl-backend-kotlin-starter-0.0.1-SNAPSHOT.jar`.
- AC#4 now has an automated guard: `src/test/kotlin/com/hl/service/StarterApplicationTests.kt` (`@SpringBootTest` + empty `contextLoads()`), `testImplementation("org.springframework.boot:spring-boot-starter-test")` (BOM-managed), `tasks.withType<Test> { useJUnitPlatform() }`. `./gradlew clean build` green; `:test` runs 1 test, boots a real context.
- Deferred: top-level `LICENSE` → `deferred-work.md`.

## Spec Change Log

## Review Triage Log

### Pass 1 (2026-09-09)

| # | Finding (layer) | Verdict | Evidence | Route |
|---|---|---|---|---|
| 1 | AC#4 (Spring context startup) has no automated test (verification-gap) | medium | Confirmed: no `src/test`, no test dependency, no CI; AC#4 only ever checked by a one-off manual `java -jar`. Every later story adds beans/config to a module whose context is never booted by `build`/CI. | patch |
| 2 | No `tasks.test { useJUnitPlatform() }` wiring (blind-hunter) | low | Confirmed; moot alone (no tests yet) but needed the moment tests land. | patch (folded into #1) |
| 3 | `.gitignore` omits `.kotlin/` (blind-hunter) | low | Confirmed: `.kotlin/` build-state dir written by the Kotlin 2.x plugin is present in the project root. | patch |
| 4 | `.gitignore` omits `.DS_Store` (blind-hunter) | low | Confirmed; macOS is an explicitly supported platform (epic NFR). `out/`/`bin/`/`*.log` not added — speculative. | patch (folded into #3) |
| 5 | Wrapper has no `distributionSha256Sum` (blind-hunter + edge-case-hunter) | medium | Confirmed: `validateDistributionUrl` checks URL shape only, not archive content. Determinism/portability NFR; low-probability, high-impact supply-chain exposure on every clone. Official checksum available. | patch |
| 6 | Repositories declared in both `settings.gradle.kts` and `build.gradle.kts`; settings block is dead under default `PREFER_PROJECT` (blind-hunter) | low | Confirmed: two sources of truth; a dev editing the settings repo block sees no effect. | patch |
| 7 | `gradlePluginPortal()` inside `dependencyResolutionManagement` (blind-hunter) | low | Confirmed: plugin repo needlessly widens where library artifacts may be fetched. | patch (folded into #6) |
| 8 | No `group` / `version` in `build.gradle.kts` (blind-hunter) | low | Confirmed: jar built with `version: unspecified`, no group coordinate. Not one of the Four Parameters, so safe to set now. | patch |
| 9 | No `.gitattributes` (blind-hunter) | low | Real only for Windows checkouts with `core.autocrlf=true`; the epic's platform scope is Linux + macOS. Fix adds a new multi-rule file, more than a direct correction. | rejected (low, off everyday path for supported platforms) |
| 10 | BOM `platform()` applied only to `implementation`, not `annotationProcessor`/`kapt` (blind-hunter) | — | This story adds no annotation processor; purely speculative about later stories. No present defect. The story that adds a processor adds the line. | rejected (no present harm) |
| 11 | Missing `LICENSE` (blind-hunter) | low | Real project-setup gap for a repo meant to be cloned; not caused by this change and no epic story owns it. | defer |
| 12 | Add invariant-recording comments to code (blind-hunter) | — | Subjective ("messy") with no named harm. | rejected |

No `intent_gap` or `bad_spec` entries — no loopback. `review_loop_iteration` unchanged (0).

## Design Notes

- **Versions are pinned deliberately.** Take every version from the architecture Stack table / AD-22 / AD-23. Do not bump to newer releases; if a pinned version genuinely fails, raise it rather than silently changing it.
- **Risk to watch:** Kotlin 2.4.0 targeting JDK 25 may emit an "unsupported JDK target" warning that `allWarningsAsErrors` turns fatal. If it appears, set `compilerOptions.jvmTarget` explicitly and/or add `-Xjdk-release=25`; do not change the pinned Kotlin or JDK version.
- **BOM import** needs `import org.springframework.boot.gradle.plugin.SpringBootPlugin` in `build.gradle.kts`.
- **Minimal runnable artifact:** only `spring-boot-starter` + a ~6-line main class are added so `bootJar` resolves a main class; starters and the concern tree are Story 1.3.

## Verification

**Commands:**
- `gradle wrapper --gradle-version 9.7.1 --distribution-type bin` then `chmod +x gradlew` -- expected: four wrapper files, `gradlew` executable.
- `./gradlew --version` -- expected: `Gradle 9.7.1`.
- `./gradlew build --info` -- expected: `BUILD SUCCESSFUL`; log shows a JDK 25 toolchain provisioned/selected; no warnings-as-errors failure.
- `ls build/libs && java -jar build/libs/hl-backend-kotlin-starter-*.jar` -- expected: the bootJar (`…-0.0.1-SNAPSHOT.jar`) exists; boots a Spring context and starts cleanly (no web server, so it may idle or exit) with no stack trace.
- `grep -RnE '"[0-9]+\.[0-9]+' build.gradle.kts settings.gradle.kts` -- expected: matches are the `foojay-resolver-convention` version in `settings.gradle.kts` and the project's own `version = "0.0.1-SNAPSHOT"` in `build.gradle.kts` — no third-party *dependency* version literal.
- `./gradlew test` -- expected: `StarterApplicationTests.contextLoads()` runs and passes (real Spring context boots).
