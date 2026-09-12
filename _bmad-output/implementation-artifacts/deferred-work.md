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

## Deferred from: code review of spec-1-3-concern-package-layout-and-a-bootable-application (2026-09-10)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-concern-package-layout-and-a-bootable-application.md`
  summary: The `tasks.withType<Test>` block in `build.gradle.kts` sets only `useJUnitPlatform()` — no `testLogging { events(...); exceptionFormat = FULL }`. An integration-test failure during `./gradlew build` (local or CI) prints minimal output, adding debugging friction for every clone.
  evidence: Story 1.3 review iteration 1 (blind-hunter). Pre-existing since the Story 1.1 build skeleton; not caused by this change. CI output/ergonomics is owned by Epic 4 — Story 4.2 (CI builds and tests every pull request).

## Deferred from: code review of spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment (2026-09-10)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: Story 2.1 (first JPA entity) must wire Kotlin no-arg support for JPA — add the `org.jetbrains.kotlin.plugin.jpa` (no-arg) plugin, or hand-write no-arg constructors on every `@Entity`. The `spring-boot-starter-data-jpa` dependency is on the classpath as of Story 1.5, but only `kotlin-spring` (all-open) is applied; a Kotlin `@Entity` without a no-arg constructor fails at runtime.
  evidence: Story 1.5 review (blind-hunter). Not a defect in Story 1.5 — the frozen scope forbids any `@Entity` here — but the gap becomes live the moment `WidgetEntity` (AD-4) is introduced.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: DB-backed verification of Story 1.5's persistence wiring is deferred to Epic 2 / Story 2.2. No automated test currently covers: the app booting and connecting with a real datasource; Flyway creating `flyway_schema_history` on startup; the policy values `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.validate-on-migrate=true`, `spring.jpa.open-in-view=false` holding; or Flyway failing loudly on a checksum-mismatched / missing migration. Story 2.2's `@ServiceConnection` Testcontainers base is the natural home for these assertions.
  evidence: Story 1.5 review (blind-hunter, verification-gap). Frozen intent excludes Testcontainers and a real-DB `@SpringBootTest` from this story; the spec routes DB-backed verification to Story 2.2. Manual AC-4 is the only current cover and must be run before Story 1.5 is marked done.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: Story 1.8 (README local loop) must document `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` as the required datasource env vars for any non-`local` run, that `application-local.yaml` + `SPRING_PROFILES_ACTIVE=local` is the local alternative, and that an exported `SPRING_DATASOURCE_*` overrides the `local` profile (standard Spring property-source precedence — Hikari logs the effective URL, so it is visible but easy to miss).
  evidence: Story 1.5 review (blind-hunter, edge-case-hunter). Frozen "Never: No README changes — Story 1.8"; operator documentation is owned by Story 1.8.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-postgres-datasource-and-flyway-configured-from-the-environment.md`
  summary: RESOLVED by Story 1.6 — adding `spring-boot-starter-data-redis` did NOT require a test-scope exclude. Verified empirically (twice, independently): with the starter on the classpath and nothing on port 6379, `StarterApplicationIT` and `LivenessProbeIT` both stay green. Lettuce's connection factory is lazily connected — no eager ping at context startup. `src/test/resources/application.yaml` was left unchanged.
  evidence: Story 1.5 review (blind-hunter). The exclude list must manually track every persistence autoconfig `main` pulls in; drift is silent until a `@SpringBootTest` breaks. This particular drift never materialized.

## Deferred from: code review of spec-1-6-redis-connection-and-readiness-gating (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-redis-connection-and-readiness-gating.md`
  summary: No automated test exercises `/actuator/health/readiness` end-to-end — that Postgres+Redis both reachable yields `UP` and Redis stopped yields `DOWN` via the actual `management.endpoint.health.group.readiness.include` wiring. A regression in that property (dropped, misspelled, or reverted) would ship undetected by `./gradlew build`; only `RedisHealthDownTest` (a hand-built `ApplicationContextRunner` bypassing the group config) and a one-time manual `curl` check cover this today.
  evidence: Story 1.6 review (blind-hunter, verification-gap [pre-verified]). Frozen intent explicitly excludes live-Redis/Postgres tests here (mirrors Story 1.5's same deferral); Story 2.2's `@ServiceConnection` Testcontainers base is the intended home for this assertion.

## Deferred from: code review of spec-1-7-run-the-service-locally-on-the-local-profile (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-run-the-service-locally-on-the-local-profile.md`
  summary: No automated regression guard covers the `bootRun` task's default-to-`local`-profile behavior in `build.gradle.kts` — a future edit to that Gradle block (e.g. accidentally removed, or the env-var check inverted) would silently break the default with nothing failing in `./gradlew build` or CI. Coverage would need a Gradle TestKit functional test, which this project has no infrastructure for yet.
  evidence: Story 1.7 review (blind-hunter). Real gap, but adding TestKit infra is beyond this story's footprint (a one-line Gradle task default); manual verification (see spec's Verification/Implementation Notes) is the only current cover, mirroring the precedent Stories 1.5/1.6 set for build/infra wiring not easily unit-tested.

## Deferred from: code review of spec-2-1-widget-domain-model-jpa-entity-and-first-migration (2026-09-11)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-widget-domain-model-jpa-entity-and-first-migration.md`
  summary: `WidgetEntity` is a final Kotlin class (no `kotlin-jpa`/allopen plugin); a future story that adds a lazy association or calls `getReferenceById()` may hit a Hibernate proxying failure.
  evidence: Story 2.1 review (blind-hunter, maybe-false). No lazy association or `getReferenceById()` call exists yet, so nothing is reachable today; settle by checking the first story that adds either.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-widget-domain-model-jpa-entity-and-first-migration.md`
  summary: `WidgetEntity`'s JPA mapping (`@Column` names/types) and the `V1__create_widgets.sql` schema are never validated against a real Hibernate session or Flyway run in `./gradlew build` — `WidgetEntityTest` is pure in-memory, and the test config excludes JPA/datasource/Flyway autoconfiguration until Story 2.2 lands.
  evidence: Story 2.1 review (verification-gap, pre-verified/filed as defer). Story 2.2 (shared Testcontainers base class and a persistence Integration Test) is the explicit, already-planned owner of this coverage.

## Deferred from: code review of spec-2-3-rfc-7807-error-contract-and-the-global-exception-handler (2026-09-12)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-rfc-7807-error-contract-and-the-global-exception-handler.md`
  summary: `GlobalExceptionHandler.respond()` builds `problem.instance = URI.create(request.requestURI)`, which throws `IllegalArgumentException` for a request path containing characters outside `java.net.URI`'s RFC 2396 grammar — turning error rendering itself into an unhandled 500 with no problem+json body for that narrow class of request.
  evidence: edge-case-hunter review. Reachability is unsettled: Tomcat's default connector (`relaxedPathChars`/`relaxedQueryChars` both unset) rejects non-compliant paths before dispatch, so this project's default config likely never reaches the vulnerable line; only a future connector-relaxation config, a different embedded server, or a reverse proxy that forwards a raw path would exercise it. Settle by adding a `relaxedPathChars` integration test or wrapping `URI.create` defensively if that configuration is ever adopted.
