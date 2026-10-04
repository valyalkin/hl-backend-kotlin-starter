---
title: 'Kubernetes runtime contract verified locally'
type: 'chore'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The image's runtime contract (probe paths, env-only config, non-root, SIGTERM, startup time) is only implied; the charts repository has no documented way to confirm it without a cluster.

**Approach:** Document a local `docker run` + curl + `docker kill -s SIGTERM` check in the README, with the commands actually run against the built image, and state that cluster deployment belongs to the charts repository. No application or build change.

</frozen-after-approval>

## Implementation Notes

Ran the check against the freshly built `hl-backend-kotlin-starter:0.0.1-SNAPSHOT` with the Compose stack up. Measured: liveness and readiness `UP` at default paths; user `1002:1001`, `java` as UID 1002; ready ~3.8 s after `docker run` (Boot "Started" 2.871 s); SIGTERM -> Boot graceful shutdown logged, exit code 143 (normal JVM status for SIGTERM) within ~0.2 s. `docker exec ... id` fails (no `id` in the tiny image), so the README uses `docker inspect`/`docker top`. Only README.md changed.

## Review Triage Log

Review layers not run (no subagent approval). Single run on one machine; startup figure is indicative, not a CI-enforced check.
