---
title: 'CI publishes the image on push to main'
type: 'feature'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No deployable artifact exists for mainline commits -- CI (Story 4.2) only gates pull requests and never builds or publishes the image.

**Approach:** Extend `.github/workflows/ci.yaml` (AD-20) so a push to `main` runs the same `build` job and then a `publish` job (`needs: build`, `main` only) that runs `bootBuildImage --publishImage` to `ghcr.io/<owner>/<repo>`, tagged with the git short SHA and `latest`, authenticating with the built-in `GITHUB_TOKEN` under `permissions: packages: write`. `build.gradle.kts` reads the registry credentials and tags from environment variables only when present, so local builds are unchanged. No other branch or failing build publishes; no semver tags; no stored registry secret.

</frozen-after-approval>

## Implementation Notes

Package visibility (private) is a GitHub package/org setting, not something the workflow sets; documented in the README. Not verified end to end: no push to GitHub was made.

## Review Triage Log

Review layers not run (no subagent approval). Verified: workflow YAML parses; `spotlessCheck` passes; `bootBuildImage --dry-run` configures with the publish env set. Not verified (Docker daemon was down, nothing pushed): the tag/publish behaviour of `bootBuildImage` against `ghcr.io`, the lower-casing and short-SHA shell expansion, and that the package is private. First push to `main` is the real test.
