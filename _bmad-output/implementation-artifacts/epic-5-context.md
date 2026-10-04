# Epic 5 Context: Clone-to-service in under 15 minutes, roadmap protected

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

A developer clones the Starter, changes only four parameters (service name, database name + credentials, HTTP port, image name) from a README checklist, and gets a running, CI-green, image-publishing service with no source edit, in under 15 minutes. The epic also installs an inactive, fail-safe OAuth2 resource-server Auth Seam so Auth0 can be adopted later without structural change, and finishes the README so the five essential tasks are each discoverable.

## Stories

- Story 5.1: The Four Parameters, each with one documented home
- Story 5.2: Generator explicitly deferred, seam documented
- Story 5.3: Changing only the Four Parameters yields a working service
- Story 5.4: Inactive, fail-safe OAuth2 resource-server Auth Seam
- Story 5.5: Auth Seam documentation
- Story 5.6: README covers the five essential tasks

## Requirements & Constraints

- Exactly four things change per Consumer Service: service name (the Gradle project name), database name + credentials, HTTP port, image name. Each has one documented home; where a value must physically appear twice, it is derived from one source or the README names both places. The checklist stays in single-digit file edits.
- The base package `com.hl.service` and the concern package names are invariant and never edited.
- The root `.env` holds only the Postgres/Redis image tags; it is plumbing, not a home for any of the four parameters.
- No generator or rename automation ships in v1; the README checklist is written to be its future spec.
- Auth Seam: inert by default (explicit permit-all chain, never a generated password); when enabled, JWT required on `/api/**` and `/v3/api-docs`, `/actuator/health**` open, other actuator endpoints token-gated; enabled without a resolvable issuer URI fails startup, never allow-all.
- The README states what is deliberately out of v1: generator, charts, messaging, auth enforcement, Vault.

## Technical Decisions

- Auth Seam is a single `app.auth.enabled` property (default `false`) selecting one of two `SecurityFilterChain` beans in `config/SecurityConfig.kt`. Issuer, audience and JWKS come from configuration.
- Dependency versions follow the existing convention: BOM-managed with no literals, catalog only for what the Boot BOM does not manage.
- Local credentials live only in `application-local.yaml`; deployed configuration is environment-only.

## Cross-Story Dependencies

- Story 5.1 defines the checklist that 5.2 reframes as a generator spec and 5.3 exercises end to end.
- Story 5.3 depends on the CI workflow and image from Epic 4, and on the Epic 2 Example Slice removal guide for the follow-up step.
- Story 5.4 adds spring-security to the classpath, so existing endpoints, Integration Tests and actuator behaviour must be unchanged while disabled; 5.5 documents it.
- Story 5.6 pulls together sections from Epics 1-3 and 5.
