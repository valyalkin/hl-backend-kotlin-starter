---
title: 'README covers the five essential tasks'
type: 'feature'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The five essential tasks (Four Parameters, add a REST resource, local development, Auth Seam, observability endpoints) exist in the README but are scattered, observability has no single entry, and nothing states what v1 leaves out.

**Approach:** Add a top-of-README index of the five tasks, a consolidated "Observability endpoints" section, and a "Scope: what v1 leaves out" section (generator, charts, messaging, auth enforcement, Vault) pointing to the brief, addendum and PRD. Confirm the "Add a REST resource" guide produces a working resource with passing tests.

</frozen-after-approval>

## Implementation Notes

README: index, `## Observability endpoints`, `## Scope: what v1 leaves out`; replaced the stale "ships with none of these classes yet" paragraph (a deferred item from Story 2.11); added a sample-data warning to the REST-resource guide.

Guide check: copied the whole `widgets` slice (main, tests, migration as `V2`) as `gadgets` in a scratch copy and ran `./gradlew build`. First attempt failed in two ways. (a) Mechanical-rename artifacts: the widget tests use `widget` and `gadget` as two sample names, so renaming collapses them; fixed by choosing different sample values, now noted in the guide. (b) A real defect: "FATAL: too many clients" from the shared test Postgres, because each cached Spring test context holds a Hikari pool of 10 against `max_connections=100` -- the same limit behind the intermittent `WidgetServiceCacheIT` failure in `deferred-work.md`, made worse by Story 5.4's extra context. Fixed by capping the pool at 3 in `src/test/resources/application.yaml`. After both, the two-resource copy builds green with all tests passing; the Starter's own `./gradlew build` is green with the cap.

## Review Triage Log

Review layers not run (no subagent approval). The pool cap was run through two full builds, not repeated enough times to prove the old intermittent failure is gone.
