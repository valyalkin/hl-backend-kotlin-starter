---
title: 'Generator explicitly deferred, seam documented'
type: 'chore'
created: '2026-10-04'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The README does not say that no generator ships in v1, so a maintainer may wonder whether a rename script is missing, and the checklist is not framed as a future generator's spec.

**Approach:** Add a "No generator in v1" subsection to the README's Four Parameters section stating the deferral and listing the five manual steps a generator would replace. No script, no code.

</frozen-after-approval>

## Implementation Notes

Confirmed no rename/scaffolding automation exists in tracked project files (`git ls-files` matches only BMad skill docs under `.claude/`, which are tooling, not part of the Starter). README only. The "Continuous integration" link anchor targets the section added in Story 4.2.

## Review Triage Log

Review layers not run (no subagent approval).
