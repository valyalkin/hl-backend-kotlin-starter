---
title: 'The Four Parameters, each with one documented home'
type: 'feature'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing says which files a developer edits to turn the Starter into a Consumer Service, and the HTTP port has no configured home at all (it is Boot's implicit 8080).

**Approach:** Give each of the four parameters (service name, database name + credentials, HTTP port, image name) exactly one documented home (AD-16) in a README "The Four Parameters" section, add `server.port: ${SERVER_PORT:8080}` as the port's home, and state that `.env` is plumbing and `com.hl.service` plus the concern packages are invariant. The checklist stays at four files.

</frozen-after-approval>

## Implementation Notes

Homes: service name -> `settings.gradle.kts` `rootProject.name`; database name + credentials -> `docker-compose.yaml` and `application-local.yaml` (both named in the README, since the value must appear in two files; deployed config is env-only); HTTP port -> new `server.port` key in `application.yaml` (also `SERVER_PORT`); image name -> derived, no edit (local image from `rootProject.name`, published image from the GitHub repository name in CI), documented as such. Added explanatory comments in `docker-compose.yaml` and `application.yaml`.

Verified: `SERVER_PORT=8099 ./gradlew bootRun` served readiness on 8099 and nothing on 8080; `./gradlew build` green (exit 0, no test failures). Image name not independently settable is a design choice worth a look: if a service needs an image name different from its repository, that needs a new hook.

## Review Triage Log

Review layers not run (no subagent approval). Not verified: a full clone-and-rename run (that is Story 5.3).
