---
title: 'Served OpenAPI and local-only Swagger UI'
type: 'feature'
created: '2026-09-13'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '07f1a108db3ed4aa816a20881a77fe8f64af5a1f'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The `widgets` slice has no machine-readable API surface today — no OpenAPI document is served on any profile, and there is no interactive UI for local exploration — even though every future resource is meant to copy this slice's pattern.

**Approach:** Add the already-catalogued `springdoc-openapi-starter-webmvc-ui` dependency so `/v3/api-docs` is served automatically in every profile via its auto-configuration, and gate Swagger UI to the `local` profile only through `springdoc.swagger-ui.enabled`.

## Boundaries & Constraints

**Always:**
- `/v3/api-docs` returns 200 with a document reflecting `/api/v1/widgets` on every profile, using springdoc's auto-configuration only — no hand-written OpenAPI bean or controller.
- `springdoc.swagger-ui.enabled` is `false` by default (`application.yaml`) and `true` only under `local` (`application-local.yaml`).
- The dependency version comes from the existing catalog entry (`springdocOpenapi` = `3.1.1`) — no new version literal in `build.gradle.kts`.

**Never:**
- No `OpenApiCustomizer`/`GroupedOpenApi` bean, no new `@Operation`/`@Schema` annotations on `WidgetController` or the DTOs — the reflected document comes entirely from existing Spring request-mapping metadata.
- No change to `WidgetController`'s routes, DTOs, or any other Epic 2 behavior.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| OpenAPI document on any profile | `GET /v3/api-docs` | 200, JSON document whose paths include `/api/v1/widgets` | N/A |
| Swagger UI on the default (non-`local`) profile | `GET /swagger-ui/index.html` | 404 — auto-configuration disabled | 404 |
| Swagger UI under `local` | `GET /swagger-ui/index.html`, `local` profile active | 200, HTML page served | N/A |

</frozen-after-approval>

## Code Map

- `gradle/libs.versions.toml` -- already declares `springdocOpenapi = "3.1.1"` and the `springdoc-openapi-starter-webmvc-ui` alias for this story; no change needed.
- `build.gradle.kts` -- add `implementation(libs.springdoc.openapi.starter.webmvc.ui)` to `dependencies {}`; this alone pulls in `/v3/api-docs` and `/swagger-ui/**` auto-configuration.
- `src/main/resources/application.yaml` -- add `springdoc.swagger-ui.enabled: false` so Swagger UI is off by default on every profile.
- `src/main/resources/application-local.yaml` -- add `springdoc.swagger-ui.enabled: true`, overriding the default only under `local`.
- `src/test/resources/application.yaml` -- fully shadows main `application.yaml` during tests (per its own header comment); restate `springdoc.swagger-ui.enabled: false` here too so the default-profile IT below sees the same disabled state as production.
- `src/test/kotlin/com/hl/service/WidgetHttpToStoreIT.kt`, `src/test/kotlin/com/hl/service/RedisDownIT.kt` -- reference for this story's new IT(s): `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient`'s `RestTestClient` for real HTTP.
- `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt` -- extend for the default-profile IT (shared Postgres+Redis containers, unmodified).

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- add the springdoc dependency via the existing catalog alias -- enables `/v3/api-docs` and Swagger UI auto-configuration
- [x] `src/main/resources/application.yaml` -- set `springdoc.swagger-ui.enabled: false` -- disables Swagger UI by default on every profile
- [x] `src/main/resources/application-local.yaml` -- set `springdoc.swagger-ui.enabled: true` -- re-enables Swagger UI only under `local`
- [x] `src/test/resources/application.yaml` -- restate `springdoc.swagger-ui.enabled: false` -- keeps the test config's full shadow of `application.yaml` accurate
- [x] `src/test/kotlin/com/hl/service/OpenApiIT.kt` -- new; extends `IntegrationTestBase`; asserts `GET /v3/api-docs` returns 200 with `/api/v1/widgets` among its paths, and `GET /swagger-ui/index.html` returns 404 on the default profile -- covers the first two I/O scenarios
- [x] `src/test/kotlin/com/hl/service/SwaggerUiLocalProfileIT.kt` -- new; extends `IntegrationTestBase`, `@ActiveProfiles("local")`; asserts `GET /swagger-ui/index.html` returns 200 -- covers the `local`-only I/O scenario

**Acceptance Criteria:**
- Given the running service on any profile, when a client requests `/v3/api-docs`, then it returns 200 with a document whose paths include `/api/v1/widgets`.
- Given the default (non-`local`) profile, when a client requests `/swagger-ui/index.html`, then it returns 404.
- Given the `local` profile, when a client requests `/swagger-ui/index.html`, then it returns 200.

## Implementation Notes

- No hand-written OpenAPI bean/controller was added; `/v3/api-docs` and `/swagger-ui/**` are served purely by springdoc's auto-configuration once the starter is on the classpath, exactly as the spec requires.
- `OpenApiIT` asserts the `/v3/api-docs` body contains the literal substring `/api/v1/widgets` (via `String.contains`) rather than parsing the JSON into a typed model, matching the codebase's existing lightweight-assertion style for response bodies (`jsonPath`/raw-string checks in `WidgetHttpToStoreIT`/`RedisDownIT`) without pulling in a new OpenAPI-model dependency.
- `SwaggerUiLocalProfileIT` activates the `local` profile via `@ActiveProfiles("local")` while still extending `IntegrationTestBase`; the shared Testcontainers' `@ServiceConnection`-registered connection details bean take precedence over `application-local.yaml`'s literal `localhost:5432` datasource properties, so activating `local` does not attempt a real connection to a local Postgres/Redis instance.
- Verified locally with `./gradlew build`: BUILD SUCCESSFUL, spotless/ktlint clean, all suites compiled and executed including the two new ITs (`OpenApiIT` 2 tests, `SwaggerUiLocalProfileIT` 1 test), all passing.
- Pre-existing, unrelated flake observed: `WidgetServiceCacheIT`'s "update evicts the Redis entry..." test intermittently fails only when the full suite runs together (passes reliably in isolation); reproduced identically on baseline `main` before this story's changes were applied (2 unrelated failures in that run), confirming it is not caused by this story's changes. Left unfixed as out of scope for this spec; logged in `deferred-work.md`.
- Post-review patch round (see Review Triage Log below): `README.md`'s "View API docs" section now describes the shipped behavior instead of "Not available yet"; `SwaggerUiLocalProfileIT` gained a second test asserting `/v3/api-docs` also returns 200 under `local`; `OpenApiIT`'s widgets-path assertion now reads `String(result.responseBody!!)` (bare non-null assertion — footgun rendering a `NullPointerException` on empty body) → replaced with a `?: error(...)` guard and a `JsonPath`-scoped `$.paths` key check (was a whole-body substring `contains`); and the `/v3/api-docs` response now also asserts `Content-Type: application/json`. Re-verified by running `OpenApiIT` (2 tests) and `SwaggerUiLocalProfileIT` (2 tests) plus `spotlessCheck` -- all green.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Evidence | Route |
|---|---|---|---|
| README.md's "View API docs" section still reads "Not available yet — the OpenAPI JSON and `local`-only Swagger UI ship with the first REST resource in Epic 2" (blind-hunter) | medium | Confirmed: `README.md:120-125` was never touched by this diff even though this story ships exactly that feature; every reader of the primary reference doc is told the feature doesn't exist. | patch |
| `/v3/api-docs` "on any profile" (spec's Always bullet + first AC) is only exercised under the default profile (`OpenApiIT`); `SwaggerUiLocalProfileIT` never re-checks `/v3/api-docs` under `local` (blind-hunter) | low | Confirmed: no test hits `/v3/api-docs` while the `local` profile is active. Unlikely to hide a real bug since `springdoc.swagger-ui.enabled` only gates Swagger UI, not `/v3/api-docs` (per the spec's own Always bullet), but the universal claim is untested and the fix is a trivial added assertion. | patch |
| `OpenApiIT`'s `String(result.responseBody!!)` uses a bare non-null assertion; a null body would throw a raw `NullPointerException` instead of a descriptive failure (blind-hunter, edge-case-hunter) | low | Confirmed at `OpenApiIT.kt` line ~201. Unlikely to trigger in practice (a 200 from springdoc always carries a body) but the fix is a trivial direct correction (`?: error(...)`). | patch |
| `assertThat(body).contains("/api/v1/widgets")` checks the raw response text rather than confirming the path is registered under the document's `paths` key; a stray match elsewhere (description, tag, example) would still pass (blind-hunter, edge-case-hunter) | low | Confirmed: the assertion is a plain substring check over the whole body, not scoped to `$.paths`. Trivial direct correction: switch to a `JsonPath`-scoped check. | patch |
| No test asserts the `Content-Type` of the `/v3/api-docs` response; a regression serving the document as plain text/YAML with the same substring would slip through (blind-hunter) | low | Confirmed: neither new IT checks the response's content type. Trivial direct correction: add `.expectHeader().contentType(MediaType.APPLICATION_JSON)`. | patch |
| The default-profile 404 check only probes `/swagger-ui/index.html`, not the underlying static assets (`/swagger-ui/swagger-ui.css` etc.); a partial disablement leaving assets reachable wouldn't be caught (blind-hunter) | false | springdoc gates its entire Swagger UI auto-configuration class (including static resource registration) behind the single `springdoc.swagger-ui.enabled` property — there is no code path that disables the index route while leaving the webjar asset mappings registered, so the described partial-disablement scenario cannot occur. | false |
| The Implementation Notes disclose a pre-existing, intermittent `WidgetServiceCacheIT` flake (reproduced on baseline `main`), but it was never logged anywhere actionable beyond this spec's prose (blind-hunter) | low | Confirmed: not present in `deferred-work.md` or any other tracked location; this story's own verification reproduced it on unmodified `main`, so it is not caused by this change. | defer |

## Verification

**Commands:**
- `./gradlew build` -- expected: BUILD SUCCESSFUL; ktlint/spotless clean; full suite green including the two new ITs.
