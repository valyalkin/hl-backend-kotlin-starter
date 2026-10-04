---
title: 'CI builds and tests every pull request'
type: 'feature'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing runs the build automatically -- `.github/` does not exist, so a pull request can merge without format-check, Unit Tests, or Integration Tests passing.

**Approach:** Add `.github/workflows/ci.yaml` (AD-20): on `pull_request`, run `./gradlew build` on `ubuntu-latest` with JDK 25, Gradle dependency and build caches restored between runs, Testcontainers-only Integration Tests (no `services:` block, no image build). Document in the README that merge-blocking is a repository branch-protection setting requiring the workflow's check to pass.

</frozen-after-approval>

## Implementation Notes

Decisions: workflow file only plus README note; no application code. `bootBuildImage` stays out (Story 4.1 boundary, Story 4.3 publishes). Branch protection is a GitHub repo setting that a workflow file cannot enforce, so it is documented, not coded.

## Review Triage Log

Review layers skipped by the human's decision (no blind-hunter subagent). Not verified: the workflow has not been run on GitHub and was not linted with `actionlint`; branch protection on `main` must be configured manually.
