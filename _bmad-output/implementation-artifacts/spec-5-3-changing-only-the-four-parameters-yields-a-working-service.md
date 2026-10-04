---
title: 'Changing only the Four Parameters yields a working service'
type: 'chore'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The "clone, change four parameters, get a working service" promise is asserted but has never been exercised.

**Approach:** Dry-run it on a copy of the Starter (renamed `orders-service`, database `orders`, port 8085, editing only the four documented homes) and document the walkthrough in the README, stating exactly what was and was not exercised. No application code change.

</frozen-after-approval>

## Implementation Notes

Dry run in the scratchpad from `git archive HEAD`, editing exactly four files (`settings.gradle.kts`, `docker-compose.yaml`, `application-local.yaml`, `application.yaml`). Results: `./gradlew build` exit 0 (warm Gradle cache, ~22 s); `bootRun` readiness UP on 8085, `/api/v1/widgets` 200, Flyway created `widgets` in the `orders` database as user `orders`; `bootBuildImage` produced `orders-service:0.0.1-SNAPSHOT`. Not exercised: push-to-`main` publish (needs a real GitHub repo); from reading `ci.yaml` it derives the image from `GITHUB_REPOSITORY`, so no edit is expected. The 15-minute wall-clock figure was not measured (warm caches, no GitHub setup). The copy and its Compose volumes and image were removed afterward.

## Review Triage Log

Review layers not run (no subagent approval).
