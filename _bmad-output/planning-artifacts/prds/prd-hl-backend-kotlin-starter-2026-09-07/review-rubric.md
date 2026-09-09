# PRD Quality Review — Spring Boot Kotlin Starter Service

Reviewed: 2026-09-07 (post architecture-reconciliation update)
Files: `prd.md`, `addendum.md`

## Overall verdict

This is a disciplined, well-shaped capability spec for an internal build substrate. The thesis (plumbing that AI agents will extend, so structure has to be enforced not merely documented) is stated and the features follow from it; scope honesty is high and, after the architecture reconciliation, the open-items surface is small. What holds it back from a clean bill are four medium consistency/coverage gaps introduced or exposed by the reconciliation: the readiness-probe spec (FR-19) no longer matches the Redis hard-dependency decision; the accepted availability trade-off of that decision is unstated; the pagination contract has no FR home; and the Auth Seam's treatment of the OpenAPI JSON endpoint is unspecified. None are hard to fix and none block the architecture (already done), but they should be closed before epics/stories source-extract from this document.

## Decision-readiness — strong

Choices are stated as choices. §8 is now genuinely closed (seven questions resolved, each with an AD reference), the `[NOTE FOR PM]` at generator deferral sits on a real tension rather than a safe checkpoint, and FR-10 / FR-25 name what was overturned. The one gap: the Redis hard-dependency decision (FR-10) states the *mechanism* (readiness gates on Redis, no fallback) but not the *cost* a decision-maker would weigh — that the service's availability is now bounded by Redis's, for a component introduced as a read optimisation. The architecture memlog accepted this explicitly ("a Redis outage takes the service out of rotation even though Postgres could still serve every request"); the PRD should carry that sentence.

### Findings
- **medium** Redis hard-dependency trade-off is unstated (§4.4 FR-10, §11) — The mechanism is specified but the accepted cost (uptime now coupled to Redis; Postgres alone could still serve every request) is not. For a PRD that markets its trade-off honesty this is a real omission. *Fix:* add one sentence to FR-10 or to §11 Constraints naming the availability coupling as an accepted cost.

## Substance over theater — strong

No persona theater (three lean UJs, each driving FRs). Vision is specific to this product and would not swap into another PRD. NFRs carry product-specific bite — "hermetic tests" ties to SM-3, "agent legibility" to SM-2 and UJ-3 — rather than boilerplate. The three counter-metrics (CI time, dependency footprint, files-touched) are real constraints with named tensions.

### Findings
_None._

## Strategic coherence — strong

There is a thesis and the document bets on it. Feature order tracks the thesis (the Boundary Test and the single-home package layout come before the Example Slice, which comes before conveniences). Success Metrics validate the thesis (SM-1 clone-to-green, SM-2 zero-plumbing addition) rather than measuring activity. MVP scope is a coherent "platform / substrate" kind and the scope logic matches.

### Findings
_None._

## Done-ness clarity — adequate

Most FRs carry at least one testable consequence and the soft language is mostly bounded by a nearby number (startup "quickly enough" → ~10s assumption; CI "short" → SM-C1 ~10 min). Three weak spots: (1) the graceful-shutdown default is "a documented default" in FR-23 while the architecture fixed 30s (AD-17) — fold the number in; (2) the resource-footprint NFR is entirely adjective-bound ("comfortable container limits", "a small domain") and, now that sizing is punted to the buildpack calculator, FR-32's Kubernetes contract carries no memory/CPU assertion at all; (3) "on the first honest attempt" (FR-6, SM-2) is not adjudicable as written.

### Findings
- **medium** No functional requirement for the pagination contract (§4.5 FR-13) — FR-13 says the collection `GET` "lists paged" but the `?page=`/`?size=` parameters and the `{items,page,size,totalElements,totalPages}` envelope live only in the architecture (AD-10). A story-creation pass working from the PRD has no anchor for list-endpoint behaviour. *Fix:* add a consequence to FR-13 (or a short dedicated FR) capturing the offset-paging parameters and the response envelope shape.
- **low** Graceful-shutdown default not stated (§4.7 FR-23) — architecture fixes 30 s (`spring.lifecycle.timeout-per-shutdown-phase`, AD-17); FR-23 says only "a documented default". *Fix:* state "30 s, env-overridable".
- **low** Resource-footprint NFR has no measurable bound (§10, §4.10 FR-32) — "comfortable container limits" / "small domain" are adjectives; FR-32's contract asserts nothing about memory or CPU. *Fix:* either add a rough ceiling (e.g. "starts and serves within a 512 MiB container") or state explicitly that sizing is out of scope for v1 and owned by the charts repo.
- **low** "First honest attempt" is not adjudicable (§4.2 FR-6, §7 SM-2) — no definition of what makes an attempt dishonest. *Fix:* define it ("excluding transcription errors corrected without a design change") or drop it in favour of "changing no Plumbing file".

## Scope honesty — strong

§5 Non-Goals does real work; §6.2 Out of Scope is explicit and now lists the OTel collector. Inline `[ASSUMPTION]` tags round-trip with §9.1. Open-items density is low (six still-open assumptions, one NOTE FOR PM, zero open questions) — appropriate for a green-light-to-build PRD.

### Findings
- **low** Stale inline wording (§10 Startup time) — the assumption still reads "confirm during architecture"; architecture is complete and carried the target forward without fixing a hard number. *Fix:* change to "confirm during the build".

## Downstream usability — strong

Glossary present and used verbatim; FR / UJ / SM IDs unique and contiguous; the AD-NN cross-references into `ARCHITECTURE-SPINE.md` resolve. FR-39/FR-40 sitting in §4.6 out of numeric sequence is a documented, deliberate choice (§0) but a top-to-bottom reader still meets FR-18 → FR-39 → FR-40 → FR-19.

### Findings
- **low** FR-14 does not acknowledge the two Spring annotations FR-5 now permits (§4.5 FR-14) — "the application use case contains no Spring web or JPA types" is still true but a reader cross-checking against the reconciled FR-5 sees apparent tension. *Fix:* "no Spring web, data, or JPA types (the two DI annotations of FR-5 aside)".

## Shape fit — strong

Internal single-operator tool plus an agent operator; the PRD correctly runs a capability-spec shape with deliberately light UJs (§2.3 calibrates this itself). Metrics are an appropriate mix of operational (SM-3, SM-5) and workflow (SM-1). Greenfield — no existing-code references to get wrong. Neither over- nor under-formalised.

### Findings
_None._

## Mechanical notes

- **Contradiction** — FR-19's readiness consequence lists only "datasource availability"; FR-10 and §9.2 now say readiness gates on Redis as well. FR-19 is the readiness spec and should name both. (Medium — carried into the adversarial section.)
- **Auth Seam gap** — FR-18 serves the OpenAPI JSON in every profile; FR-37 enumerates only `health`/`info`/`prometheus` for actuator and says nothing about `/v3/api-docs`, which is not an actuator path. When `app.auth.enabled=true`, whether that endpoint requires a token is unspecified. (Medium — carried into the adversarial section.)
- Title still marked "*Working title — confirm.*" and reads "Spring Boot Kotlin Starter Service" while the project is `hl-backend-kotlin-starter`. Low.
- §12 "Downstream Depth" still frames the addendum as "the architecture step's input"; the architecture step is complete and the addendum now carries per-section "Resolved" lines. Low.
- Frontmatter `status: draft`, `updated: 2026-09-07` — unchanged; finalisation is a remaining step, not a finding.
- Assumptions Index round-trip: inline `[ASSUMPTION]` at FR-2 (×2), FR-11, FR-15, FR-33, FR-36, §10 all appear in §9.1. Clean.
