---
title: 'Auth Seam documentation'
type: 'chore'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Auth Seam exists (Story 5.4) but nothing tells a developer how to switch it on, which keys to set, how Actuator is treated, or how Integration Tests authenticate.

**Approach:** Add a README "Auth Seam" section stating the v1 posture and Auth0 direction, the exact keys (issuer URI, audience, JWKS URI) with their environment variables, the enabled-mode rules including Actuator, and how tests authenticate. Documentation only.

</frozen-after-approval>

## Implementation Notes

README only. Keys and behaviour taken from `SecurityConfig.kt` and `AuthSeamEnabledIT`/`TestIssuer` as built in Story 5.4. Environment-variable names are Spring's relaxed-binding forms of the property keys, not independently run. Section appended at the end of the README; Story 5.6 restructures the README around the five essential tasks.

## Review Triage Log

Review layers not run (no subagent approval). The `jwk-set-uri` override was not exercised by any test.
