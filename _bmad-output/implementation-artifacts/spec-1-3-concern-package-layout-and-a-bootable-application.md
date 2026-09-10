---
title: 'Concern-package layout and a bootable application'
type: 'feature'
created: '2026-09-10'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'ed449882366a2b6335864bc916003aed7a770852'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repo carries only `StarterApplication.kt` under the root package and depends on the bare `spring-boot-starter` — no web layer, no actuator, none of the seven concern packages, no README describing the layout. Epic 1's walking skeleton, and every later story that needs a running web context (1.5, 1.6, 1.7), requires a process that boots with webmvc + actuator and answers a liveness probe, plus the one-home-per-concern package tree agents extend (AD-1).

**Approach:** Swap the core starter for `spring-boot-starter-webmvc` and add `spring-boot-starter-actuator`, so the process boots on embedded Tomcat with Boot 4's liveness/readiness probe groups enabled by default (zero config). Create the seven concern-package directories under `com.hl.service`. Add a `README.md` section documenting the package layout as a naming convention. Prove the probe with a `RestTestClient` integration test on a random port.

## Boundaries & Constraints

**Always:**
- Root package stays `com.hl.service`; concern packages are exactly `controller`, `service`, `repository`, `model`, `dto`, `error`, `config` — never more, fewer, or renamed (AD-1, AD-16).
- `StarterApplication.kt` remains the single `@SpringBootApplication` entry point — unchanged.
- Web starter is `spring-boot-starter-webmvc` (not `-web`); it transitively provides the core starter + embedded Tomcat, so `spring-boot-starter` is removed, not kept alongside.
- Actuator via `spring-boot-starter-actuator`; rely on Boot 4.1 defaults (probes enabled, `health` web-exposed) with **no** `application.yaml`/`application.properties` in this story.
- Test starter becomes `spring-boot-starter-webmvc-test` (per-technology test starter, AD-21); it supersedes `spring-boot-starter-test` and brings `RestTestClient` + its auto-config.
- The liveness test uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient`, carries `@Tag("integration")`, and asserts `GET /actuator/health/liveness` → 200 with body `status` `UP`.
- New dependency lines carry no version literal — every coordinate is BOM- or plugin-managed (AD-22); `gradle/libs.versions.toml` is untouched.
- Each of the seven concern-package directories gets exactly one committed empty `.gitkeep` file to make it survive a bare clone; package purposes are documented only in the root `README.md` table, not per directory.

**Never:**
- No `application.yaml`, `application-local.yaml`, `db/migration/`, datasource, Redis, or JPA config — those are Stories 1.4–1.7. No `src/main/resources/` additions at all.
- No narrowing actuator exposure to `health,info,prometheus`, no readiness-group membership wiring — Epic 3 / Stories 1.5–1.6 (AD-17). Default exposure is accepted here.
- No `.java` sources; no change to compiler strictness, the Gradle wrapper, `settings.gradle.kts`, or the Spotless config.
- No ArchUnit or dependency-direction test — AD-1 is convention only (AD-2 retired).
- No per-resource subpackages, no `Widget*` or other domain classes (Epic 2).
- README local-development section is Story 1.8 — this story adds only the package-layout convention.

</frozen-after-approval>

## Code Map

- `build.gradle.kts` -- `dependencies {}` holds `implementation("org.springframework.boot:spring-boot-starter")` and `testImplementation("org.springframework.boot:spring-boot-starter-test")`. Replace the first with `-webmvc`, add `-actuator`, replace the test one with `-webmvc-test`. The `platform(SpringBootPlugin.BOM_COORDINATES)` line and every other block stay as-is.
- `src/main/kotlin/com/hl/service/StarterApplication.kt` -- `@SpringBootApplication` class + `main` calling `runApplication`. Already correct; do not modify. Anchor for the "single entry point" AC.
- `src/main/kotlin/com/hl/service/` -- currently only `StarterApplication.kt`. Add seven sibling directories `controller/ service/ repository/ model/ dto/ error/ config/`, each holding one empty `.gitkeep`.
- `src/test/kotlin/com/hl/service/StarterApplicationIT.kt` -- existing `@SpringBootTest` `contextLoads`, `@Tag("integration")`. Leave as-is; still compiles under the new test starter.
- `README.md` -- does not exist. Create with a "Package layout" section: root package, a table of the seven concern packages → what each holds (mirror ARCHITECTURE-SPINE "Consistency Conventions"), and the note that this is convention only, no build-time check.
- `gradle/libs.versions.toml` -- do NOT touch; all new deps are BOM-managed.
- Host facts: `./gradlew` 9.7.1, JDK 25 toolchain auto-provisions; new webmvc/actuator/test jars resolve from Maven Central on first build (one-time network).

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- in `dependencies {}`: replace `spring-boot-starter` with `spring-boot-starter-webmvc`, add `spring-boot-starter-actuator` (both `implementation`), replace `spring-boot-starter-test` with `spring-boot-starter-webmvc-test` (`testImplementation`). No version literals, no other edits.
- [x] `src/main/kotlin/com/hl/service/{controller,service,repository,model,dto,error,config}/.gitkeep` -- create all seven directories, each with one empty `.gitkeep`, so the tree survives a bare clone.
- [x] `README.md` -- create at repo root with a "Package layout" section only: root package `com.hl.service`, a table mapping the seven concern packages to what belongs in each, and a sentence that it is a naming convention with no ArchUnit/boundary check. No local-dev content (Story 1.8).
- [x] `src/test/kotlin/com/hl/service/LivenessProbeIT.kt` -- new: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@AutoConfigureRestTestClient`, `@Tag("integration")`; inject `RestTestClient`; assert `GET /actuator/health/liveness` → 200 and JSON `status` == `UP`.
- [x] Run `./gradlew spotlessApply` over new Kotlin, then confirm `./gradlew build` is green.

**Acceptance Criteria:**
- Given the source tree, when `src/main/kotlin/com/hl/service` is inspected, then the root package is `com.hl.service`, the directories `controller`, `service`, `repository`, `model`, `dto`, `error`, `config` all exist and are git-tracked, and `StarterApplication.kt` is the only `@SpringBootApplication`.
- Given `spring-boot-starter-webmvc` + `spring-boot-starter-actuator` on the classpath and no config file, when the application starts, then it boots on an embedded servlet container and `GET /actuator/health/liveness` returns `200` with body `status` `UP`.
- Given the running actuator, when `GET /actuator/metrics` is requested, then it returns `404` — confirming no exposure configuration was added (default `health`-only exposure).
- Given the repo, when `README.md` is read, then it documents the seven-package layout as a naming convention and states no build-time boundary check exists.
- Given the committed tree, when `./gradlew build` runs, then compile, `spotlessCheck`, and all tests pass and `git diff` is empty afterward.

## Implementation Notes

- `LivenessProbeIT` also asserts `GET /actuator/metrics` → 404, covering AC-3 (no exposure config added) in the same integration test rather than a separate check.
- Verified on a `./gradlew clean build`: `compileKotlin`, `spotlessCheck`, `StarterApplicationIT.contextLoads`, and both `LivenessProbeIT` cases executed and passed; `git diff` after the build is limited to the intended source changes (`build.gradle.kts`, `README.md`, `LivenessProbeIT.kt`) plus the seven staged `.gitkeep` files.

## Spec Change Log

## Review Triage Log

Iteration 0 — three layers (blind-hunter, edge-case-hunter, verification-gap).

| Finding (layer) | Verdict | Evidence | Route |
| --- | --- | --- | --- |
| `@Tag("integration")` on `LivenessProbeIT` is inert — no `includeTags`/`excludeTags` in `build.gradle.kts` (blind-hunter) | low | True, but the tag is spec-mandated (frozen Boundaries) and matches the pre-existing `StarterApplicationIT` convention; a tag-filtered task is out of intent scope. Pre-existing, not caused by this change. | rejected (low; pre-existing) |
| `StarterApplicationIT.contextLoads` now redundant with `LivenessProbeIT` — two context boots (blind-hunter) | low | `StarterApplicationIT` is frozen ("Leave as-is"); two lightweight boots in a starter suite is negligible; the only fixes edit a frozen file or the spec. | rejected (low) |
| README table cites not-yet-existing constructs (JPA, `@Transactional`, exception types) with no "future" marker (blind-hunter) | low | Real minor clarity gap in a new file every clone reads; smallest fix is one clarifying sentence, no code/surface. | patch — added a sentence noting the starter ships with none of these classes yet. |
| README does not link the ARCHITECTURE-SPINE source it mirrors (blind-hunter) | low | Speculative drift risk; ARCHITECTURE-SPINE is an internal planning artifact that need not ship with a clone, so linking it from the README is dubious value. | rejected (low) |
| No note that a package's `.gitkeep` should be removed once a real class lands (blind-hunter) | low | Every clone inherits seven marker files — commonly met, not unlikely; fix is one sentence, no complexity. | patch — added a sentence in README to delete `.gitkeep` once the package holds a real class. |
| `/actuator/metrics` → 404 test has no positive control (blind-hunter) | false | The same class asserts `/actuator/health/liveness` → 200, which is the positive control isolating "metrics unexposed" from "actuator broken". | rejected (false) |
| Liveness test never proves a real HTTP round-trip; could pass under MOCK (blind-hunter) | low | `webEnvironment = RANDOM_PORT` is explicit; run log shows "Tomcat started on port …", so a real socket exchange occurred. Added assertions guard a non-demonstrated regression. | rejected (low) |
| Readiness probe gets no baseline coverage (blind-hunter) | out-of-scope | Frozen Boundaries and Intent defer readiness groups/gating to Stories 1.5–1.6 (AD-17); intent itself excludes it. | rejected (out of scope) |
| Liveness health group may be unregistered without config → 404 (edge-case-hunter) | false | `./gradlew clean build` green; `LivenessProbeIT.liveness probe reports UP` executed and passed; actuator log "Exposing 1 endpoint" (health). Boot 4.1 enables probes outside k8s by default. | rejected (false) |
| `-webmvc-test` may not supply `RestTestClient` auto-config → context fails (edge-case-hunter) | false | Clean build green; `RestTestClient` constructor-injected into `LivenessProbeIT`, context loaded, both cases passed. | rejected (false) |
| Removing `spring-boot-starter-test` may break `StarterApplicationIT` compile (edge-case-hunter, low conf) | false | `compileTestKotlin` + `StarterApplicationIT.contextLoads` both green on clean build. | rejected (false) |
| Spec claim "probes enabled by default (zero config)" may be wrong (edge-case-hunter, low conf) | false | Confirmed empirically: liveness probe returns 200/UP with no `application.yaml` present. | rejected (false) |
| verification-gap layer | — | "No verification gaps found." | none |

No `intent_gap` or `bad_spec` entries — no loopback. Two `patch` entries applied to `README.md`; re-verified with `./gradlew clean build` (green).

## Design Notes

- **Probes need no config in Boot 4.1.** `management.endpoint.health.probes.enabled` defaults to `true` outside Kubernetes (Boot 4 change vs Boot 3), and `health` is web-exposed by default — so `/actuator/health/liveness` works with zero `application.yaml`. Do not add config to "enable" it; a 404 means the starter/dependency is wrong, not the config.
- **`RestTestClient`, not `TestRestTemplate`.** Boot 4.1 dropped `TestRestTemplate` from the default test starter; `spring-boot-starter-webmvc-test` supplies `RestTestClient` (Spring Framework 7) via `@AutoConfigureRestTestClient`. Golden shape:
  ```kotlin
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  @AutoConfigureRestTestClient
  @Tag("integration")
  class LivenessProbeIT(@Autowired val client: RestTestClient) {
      @Test
      fun `liveness probe reports UP`() {
          client.get().uri("/actuator/health/liveness").exchange()
              .expectStatus().isOk
              .expectBody().jsonPath("$.status").isEqualTo("UP")
      }
  }
  ```
- **Concern packages are convention, not structure.** Nothing imports them yet; they exist so Epic 2's `Widget*` classes and every Consumer Service have one obvious home per kind of code (AD-1). The build never checks placement.

## Verification

**Commands:**
- `./gradlew build` -- expected: `BUILD SUCCESSFUL`; compile + `spotlessCheck` + `StarterApplicationIT` + `LivenessProbeIT` all green.
- `./gradlew spotlessCheck && git diff --quiet` -- expected: clean, no reformatting pending.
- `find src/main/kotlin/com/hl/service -maxdepth 1 -type d` -- expected: the base dir plus the seven concern dirs.
- `grep -n 'spring-boot-starter"' build.gradle.kts` -- expected: no match (bare core starter gone).
- Manual: `./gradlew bootRun`, then `curl -s -w '%{http_code}' localhost:8080/actuator/health/liveness` → `200` and body contains `"status":"UP"`; `curl -s -o /dev/null -w '%{http_code}' localhost:8080/actuator/metrics` → `404`. Stop with Ctrl-C.
