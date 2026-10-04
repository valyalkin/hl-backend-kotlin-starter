---
title: 'Inactive, fail-safe OAuth2 resource-server Auth Seam'
type: 'feature'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Adopting Auth0 later would otherwise mean a structural change, and simply adding `spring-security` to the classpath would silently secure everything with a generated password.

**Approach:** Add `config/SecurityConfig.kt` (AD-18) where `app.auth.enabled` (default `false`) selects one of two `SecurityFilterChain` beans: an explicit permit-all chain, or an OAuth2 resource-server chain requiring a JWT on everything except `/actuator/health**` (so `/api/**`, `/v3/api-docs` and other actuator endpoints are gated). Issuer, audience and JWKS come from Boot's `spring.security.oauth2.resourceserver.jwt.*` properties; enabled without an `issuer-uri` fails startup.

</frozen-after-approval>

## Implementation Notes

Decisions: (1) used Boot's own `spring.security.oauth2.resourceserver.jwt.issuer-uri` / `audiences` / `jwk-set-uri` keys, so no custom decoder code; (2) "resolvable issuer-uri" is implemented as *present and non-blank*: a missing issuer fails startup, but an issuer that is set and unreachable does not (Boot resolves it lazily, so requests get 401 until it is reachable, which still fails closed); (3) the enabled chain uses `anyRequest().authenticated()` after the health permit, so unlisted paths are also gated; (4) CSRF is disabled and sessions are stateless in the JWT chain, and CSRF is disabled in the permit-all chain so non-GET requests behave as before. Added `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` (BOM-managed). `app.auth.enabled: ${APP_AUTH_ENABLED:false}` added to `application.yaml`.

Tests: `AuthSeamDisabledIT` (one chain, every endpoint open), `AuthSeamEnabledIT` (health open; api/openapi/prometheus 401 without token and 200 with a valid one; wrong audience, wrong issuer and wrong signing key all 401) against a throwaway local OIDC issuer (`support/TestIssuer`, JDK `HttpServer` plus Nimbus), and `AuthSeamFailSafeTest` (enabled without issuer fails; default installs only the permit-all chain). `AuthSeamEnabledIT` is `@DirtiesContext(AFTER_CLASS)`: the extra cached Spring context pushed the shared Postgres past `max_connections` ("too many clients") and broke other ITs, which is the same pre-existing limit behind the intermittent `WidgetServiceCacheIT` failure in `deferred-work.md`. `./gradlew build` is green. No generated-password log line appeared.

Not done here: README documentation of the keys and test wiring (Story 5.5), and the `AuthSeamEnabledIT` does not cover POST/PUT/DELETE with a token.

## Review Triage Log

Review layers not run (no subagent approval).
