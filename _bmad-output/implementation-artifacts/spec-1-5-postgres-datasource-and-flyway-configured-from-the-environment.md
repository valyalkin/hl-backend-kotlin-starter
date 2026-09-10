---
title: 'Postgres datasource and Flyway, configured from the environment'
type: 'feature'
created: '2026-09-10'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '4cd7f511e9b1ac74b7351bbc47ff7d85d4901e51'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The service boots with webmvc + actuator but has no persistence — no datasource, no JPA, no Flyway, and no `src/main/resources/` at all. Story 1.5's ACs, Epic 2's `widgets` slice, and the local run loop (1.7) all need the datasource and Flyway wired with every connection setting read from the environment, Flyway owning the schema, and a missing or wrong configuration failing startup loudly rather than degrading silently.

**Approach:** Add the JPA and Flyway starters plus the Postgres driver (all BOM/driver-managed). Introduce `application.yaml` holding only `${ENV_VAR}` placeholders for the datasource — no defaults, so an unset value fails startup — and the fixed policy (`ddl-auto: validate`, checksum validation on, auto-repair off). `application-local.yaml` carries the sole literal credentials, matching the Compose stack. Create the empty `db/migration/` location (first migration is Epic 2). Keep `./gradlew build` database-free by excluding the datasource/JPA/Flyway auto-configuration in test scope until Epic 2's Testcontainers base supersedes it, and add one lightweight test proving startup fails without datasource config.

## Boundaries & Constraints

**Always:**
- The four new dependencies (`spring-boot-starter-data-jpa`, `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql`) carry **no version literal** — BOM- or driver-managed (AD-22). `gradle/libs.versions.toml` is untouched.
- `application.yaml` contains only non-secret defaults and `${ENV_VAR}` placeholders. The datasource url/username/password placeholders have **no default** — an unset value must fail startup with a clear message (AD-15, Story AC-3).
- Datasource env vars are `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (decided 2026-09-10 — Spring Boot canonical relaxed-binding names; explicit `${...}` placeholders in `application.yaml` exist only to force fail-loud on unset). Final "one documented home" formalization is Epic 5 / Story 5.1.
- `application-local.yaml` is the **only** file in the repo or image with literal credentials: `jdbc:postgresql://localhost:5432/hl_service` and `hl_service` / `hl_service`, matching the Compose stack.
- `spring.jpa.hibernate.ddl-auto` is `validate`; `spring.flyway.validate-on-migrate` is `true` (checksum validation, AD-14); no Flyway repair property is set or repair call wired (auto-repair off). Migration location is the Flyway default `classpath:db/migration`.
- `src/main/resources/db/migration/` exists (committed `.gitkeep`) and is empty this epic — an empty migration set is valid; `V1__create_widgets.sql` lands in Epic 2 / Story 2.1.
- `./gradlew build` stays Docker-free: `src/test/resources/application.yaml` excludes the datasource, Hibernate-JPA and Flyway auto-configuration (exact Boot 4.1 FQCNs) so the existing `@SpringBootTest` ITs still boot without a database.

**Never:**
- No Testcontainers, `@ServiceConnection`, or shared IT base class — Epic 2 / Story 2.2. This story does not make `@SpringBootTest` connect to a real database.
- No migration script; no `@Entity`, repository, or domain class — Epic 2.
- No Redis, cache, readiness-group, or `management.*` exposure changes — Stories 1.6 / Epic 3.
- No `bootRun` profile default — Story 1.7. No README changes — Story 1.8.
- No change to `docker-compose.yaml`, `.env`, `settings.gradle.kts`, `.gitignore`, `StarterApplication.kt`, or the existing ITs.

</frozen-after-approval>

## Code Map

- `build.gradle.kts` -- `dependencies {}` currently: webmvc, actuator, webmvc-test. Add `implementation("org.springframework.boot:spring-boot-starter-data-jpa")`, `implementation("org.flywaydb:flyway-core")`, `runtimeOnly("org.flywaydb:flyway-database-postgresql")`, `runtimeOnly("org.postgresql:postgresql")`. No other edits; `tasks.withType<Test>` block unchanged.
- `src/main/resources/` -- does not exist; create. Add `application.yaml`, `application-local.yaml` (content in Design Notes), and `db/migration/.gitkeep`.
- `src/test/resources/` -- does not exist; create. Add `application.yaml` with `spring.autoconfigure.exclude` listing the three autoconfig classes. NOTE: Boot 4.0 split `spring-boot-autoconfigure` into per-module artifacts — confirm each FQCN against `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in the resolved `spring-boot-*` jars before finalizing.
- `src/test/kotlin/com/hl/service/DatasourceFailFastTest.kt` -- new. `ApplicationContextRunner` + `DataSourceAutoConfiguration`, no properties; assert the context failed and the failure names the missing `url` / DataSource. Deliberately not `@SpringBootTest` (needs no DB, unaffected by the test-scope excludes); no `@Tag("integration")`.
- `src/test/kotlin/com/hl/service/{StarterApplicationIT,LivenessProbeIT}.kt` -- do NOT modify. Must still boot green once the test-scope excludes are in place.
- Reference: `epic-1-context.md` (AD-14, AD-15, AD-22); `docker-compose.yaml` (Postgres db/user/password `hl_service`, port 5432).

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add the four BOM/driver-managed dependency lines (two `implementation`, two `runtimeOnly`); no version literals. *(Implemented as five — see Implementation Notes: `spring-boot-flyway` added for Boot 4.1's split-out Flyway autoconfig.)*
- [x] `src/main/resources/application.yaml` -- create: datasource url/username/password as no-default `${SPRING_DATASOURCE_*}` placeholders; `spring.jpa.hibernate.ddl-auto: validate`; `spring.jpa.open-in-view: false`; `spring.flyway.validate-on-migrate: true` with a comment that auto-repair is intentionally not wired.
- [x] `src/main/resources/application-local.yaml` -- create: literal `spring.datasource` url/username/password for the Compose stack (`hl_service`). Only file with credentials.
- [x] `src/main/resources/db/migration/.gitkeep` -- create empty; establishes the Flyway location, empty this epic.
- [x] `src/test/resources/application.yaml` -- create: `spring.autoconfigure.exclude` for datasource, Hibernate-JPA and Flyway autoconfig (exact Boot 4.1 FQCNs).
- [x] `src/test/kotlin/com/hl/service/DatasourceFailFastTest.kt` -- create: `ApplicationContextRunner` proves startup fails with a clear message when no datasource URL is set.
- [x] Run `./gradlew spotlessApply` then `./gradlew build`; confirm green with no reformatting.

**Acceptance Criteria:**
- Given a grep of `src/main` and every committed config file except `application-local.yaml`, when searching for a JDBC URL, username or password literal, then none is found.
- Given `application.yaml`, when inspected, then `spring.jpa.hibernate.ddl-auto` is `validate`, `spring.flyway.validate-on-migrate` is `true`, no Flyway repair property is set, and the datasource url/username/password are placeholders with no default value.
- Given `./gradlew build` on a machine with **no** running Postgres and no Docker, when it runs, then compile, `spotlessCheck`, `DatasourceFailFastTest`, `StarterApplicationIT` and `LivenessProbeIT` all pass.
- Given the Compose stack up and `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`, when the app starts, then it connects to Postgres with the `application-local.yaml` settings, Flyway creates its `flyway_schema_history` table, and `GET /actuator/health` returns `UP`; stopping Postgres and repeating fails startup with a clear datasource error (no silent degraded mode).

## Implementation Notes

- **Fifth dependency added — `org.springframework.boot:spring-boot-flyway`.** The frozen list names four deps and Code Map said "no other edits" to `build.gradle.kts`, but Boot 4.0+ split `spring-boot-autoconfigure` into per-technology modules and `FlywayAutoConfiguration` no longer ships with `flyway-core` nor transitively via the data-jpa starter. Without `spring-boot-flyway` the frozen constraints are unsatisfiable: `spring.flyway.validate-on-migrate` would be inert, the test-scope exclude of "Flyway auto-configuration" would target a non-existent class, and AC-4's `flyway_schema_history` creation would not happen. Added BOM-managed, no version literal, `libs.versions.toml` untouched — consistent with AD-22 and the frozen intent. Flagged for review adjudication (Spec Change Log candidate).
- **Boot 4.1 autoconfig FQCNs** (verified against `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in the resolved jars): `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`, `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`, `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`. `DatasourceFailFastTest` imports the same `jdbc.autoconfigure.DataSourceAutoConfiguration`.
- **Resolved versions (all BOM/driver-supplied):** flyway-core / flyway-database-postgresql 12.4.0, spring-boot-flyway 4.1.1, postgresql 42.7.13, spring-boot-starter-data-jpa 4.1.1, hibernate-core 7.4.5.Final.
- **Verification run (2026-09-10, no Postgres, no Docker, nothing on :5432):** `./gradlew clean build` → `BUILD SUCCESSFUL`; `spotlessCheck` clean; tests `DatasourceFailFastTest` (1), `StarterApplicationIT` (1), `LivenessProbeIT` (2) all passed. Scope diff: only the 6 spec'd files, +88 lines; every frozen "no change" file untouched. Credential-literal grep: real values only in `application-local.yaml`; `application.yaml` carries `${...}` placeholders only. `db/migration/.gitkeep` is not git-ignored.
- **Not run — manual AC-4** (Compose up → `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` → `/actuator/health` UP + `flyway_schema_history` present → stop Postgres → loud startup failure). Needs Docker; deferred to a manual pass before the story is marked done.
- **Verification nuance for review:** `DatasourceFailFastTest` exercises `DataSourceAutoConfiguration` with no properties (Hikari/DataSourceProperties "url missing" failure). The production path is placeholder-resolution failure (`Could not resolve placeholder 'SPRING_DATASOURCE_URL'`) — same loud-failure guarantee, different mechanism; the placeholder path is only covered by manual AC-4.

## Spec Change Log

## Review Triage Log

Iteration 0 — three layers (blind-hunter, edge-case-hunter, verification-gap). No intent_gap or bad_spec; no loopback. Outcome: 2 patch, 4 defer, 3 reject (+ several findings folded into patch #1).

**Patch:**

- **#1 — `DatasourceFailFastTest` does not exercise the production fail-loud mechanism** (blind-hunter, verification-gap [pre-verified], edge-case-hunter). `low`. The test drives `DataSourceAutoConfiguration` in isolation via `ApplicationContextRunner` with no properties and asserts a stack trace containing `"url"` / `"DataSource"`. It never resolves a `${SPRING_DATASOURCE_URL}` placeholder — the mechanism `application.yaml` and AD-15 / AC-3 actually rely on. A regression that adds `${SPRING_DATASOURCE_URL:somedefault}` would ship green. Folds in: brittle case-sensitive substring assertions (blind-hunter, edge-case-hunter — `"url"` matches unrelated frames, breaks on a Boot message reword); hidden dependency on no embedded DB on the classpath (blind-hunter); ambient `SPRING_DATASOURCE_URL` in the test JVM defeating `hasFailed()` (edge-case-hunter). Fix: add `.withPropertyValues("spring.datasource.url=\${SPRING_DATASOURCE_URL}")` so the run exercises unresolved-placeholder failure, assert on the stable exact-case token `SPRING_DATASOURCE_URL`, and KDoc the ambient-env assumption. The "`application.yaml` literally carries no default" half stays covered by diff review + manual AC-4.
- **#2 — `application-local.yaml` comment overclaims** (blind-hunter). `low`. The header says "The ONLY file in the repo or image with literal credentials," but `docker-compose.yaml` hardcodes `POSTGRES_PASSWORD: hl_service` (+ DB, user). True that those are container-init values, not Spring config and not in the image — but a starter file people read to learn conventions should not state a literal falsehood. Fix: one-line reword acknowledging the Compose container-init values as a peer source.

**Defer** (see `deferred-work.md`, section dated 2026-09-10):

- **Kotlin no-arg / JPA support not wired** (blind-hunter). The data-jpa starter is on the classpath as of this story, but `kotlin("plugin.jpa")` (no-arg) is not in the plugin block. Harmless now (frozen "Never: no `@Entity`"); Story 2.1 (first JPA entity) must add the no-arg plugin or hand-write no-arg constructors. Not caused by this story's deliverable → defer.
- **No automated coverage of the DB-backed happy path or the persistence policy values** (blind-hunter, verification-gap [pre-verified]). Nothing tests that the app boots and connects with a real datasource, that Flyway creates `flyway_schema_history`, that `ddl-auto=validate` / `validate-on-migrate` / `open-in-view=false` hold, or that Flyway fails loudly on a checksum mismatch. Frozen intent excludes Testcontainers here ("Never … does not make `@SpringBootTest` connect to a real database"); Story 2.2's `@ServiceConnection` base is the home. Out of scope by intent → defer with a note so 2.2 picks it up.
- **Required datasource env vars are undocumented** (blind-hunter) + **exported `SPRING_DATASOURCE_*` overrides the `local` profile** (edge-case-hunter — standard Spring precedence; the app would connect elsewhere than the Compose stack, and Hikari logs the URL so it is not truly silent). Frozen "Never: No README changes — Story 1.8"; intent defers operator docs to 1.8 → defer with a note.
- **Test-scope autoconfig-exclude drift** (blind-hunter). `src/test/resources/application.yaml` must manually track every persistence autoconfig `main` pulls in; Story 1.6 adding Redis without extending it would make the `@SpringBootTest` ITs fail reaching Redis. Speculative and Story 1.6's to handle → defer a heads-up note.

**Rejected:**

- **Empty `db/migration/` — "No migrations found" warning every startup; no `baseline-on-migrate`** (blind-hunter). `low`. The warning is explicitly sanctioned by the spec Design Notes ("benign … intended until Epic 2"); a `V0__baseline.sql` is forbidden by frozen "Never: no migration script"; `baseline-on-migrate` is config for a non-empty-existing-schema scenario Epic 1 (fresh Compose DB, no migrations) does not have. Fix is blocked by intent or adds premature config.
- **`spring.flyway.validate-on-migrate: true` only restates the Flyway default** (blind-hunter). `false`. Making a default explicit to document intent and guard against a future global override is legitimate, and AD-14 wants checksum validation visible in config. No bad outcome.
- **`application-local.yaml` hardcodes `localhost:5432` with no `${DB_HOST:localhost}` indirection** (blind-hunter). `false` / `low`. The exact literal URL is frozen-specified; running the app *inside* the Compose network is outside Epic 1's intent (spec-1-4: no app container); non-local topologies use `SPRING_DATASOURCE_URL`. The indirection also contradicts AD-15's "`application-local.yaml` holds literals."

## Design Notes

- **`application.yaml` shape:**
  ```yaml
  spring:
    datasource:
      url: ${SPRING_DATASOURCE_URL}
      username: ${SPRING_DATASOURCE_USERNAME}
      password: ${SPRING_DATASOURCE_PASSWORD}
    jpa:
      hibernate:
        ddl-auto: validate
      open-in-view: false
    flyway:
      validate-on-migrate: true   # checksum validation on (AD-14); repair is never auto-run
  ```
  No default on the three datasource values is deliberate: an unset `SPRING_DATASOURCE_URL` makes Spring fail with `Could not resolve placeholder` at startup — the "fail loud, no degraded mode" contract (Story AC-3, AD-15). `open-in-view: false` silences Boot's startup WARN and is the standard choice for a service.
- **`application-local.yaml`:** only `spring.datasource.url/username/password` as literals (`jdbc:postgresql://localhost:5432/hl_service`, `hl_service`, `hl_service`). Not active by default — pass `SPRING_PROFILES_ACTIVE=local` (Story 1.7 makes it the `bootRun` default).
- **Why exclude autoconfig in test scope:** the JPA starter + a no-default URL would break `StarterApplicationIT` / `LivenessProbeIT`, which boot the full context, and Epic 1's build must not require Docker. Epic 2 / Story 2.2 adds the `@ServiceConnection` Testcontainers datasource and revisits this. `src/test/resources/application.yaml` fully shadows the main one in tests, so it needs only the `exclude` keys.
- **`ddl-auto: validate` with zero entities** validates nothing yet; **empty `db/migration/`** makes Flyway create the history table and log a benign "No migrations found" — both intended until Epic 2.
- **`DatasourceFailFastTest`** uses `ApplicationContextRunner` (no `@SpringBootTest`/Tomcat/DB) so it is unaffected by the test-scope excludes.

## Verification

**Commands:**
- `./gradlew build` -- expected: `BUILD SUCCESSFUL`; compile + `spotlessCheck` + `DatasourceFailFastTest` + `StarterApplicationIT` + `LivenessProbeIT` green, with no Postgres running.
- `./gradlew dependencies --configuration runtimeClasspath | grep -E 'flyway|postgresql|hibernate|data-jpa'` -- expected: Flyway core + `flyway-database-postgresql`, `org.postgresql:postgresql`, Hibernate and `spring-boot-starter-data-jpa` resolved with BOM-supplied versions; no `libs.versions.toml` entry added.
- `grep -REn 'jdbc:postgresql|password|hl_service' src/main/resources build.gradle.kts docker-compose.yaml` -- expected: credential literals appear only in `application-local.yaml`.
- `./gradlew spotlessCheck` -- expected: clean.

**Manual checks:**
- With `docker compose up -d`: `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`, then `curl -s localhost:8080/actuator/health` → `{"status":"UP"...}`; `psql` shows a `flyway_schema_history` table in `hl_service`. Stop Postgres, rerun → startup aborts with a datasource/connection error, not a running-but-degraded process. Ctrl-C to stop.
