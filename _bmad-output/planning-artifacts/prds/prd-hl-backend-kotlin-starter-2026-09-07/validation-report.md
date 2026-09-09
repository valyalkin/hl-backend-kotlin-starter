# Validation Report — Spring Boot Kotlin Starter Service

- **PRD:** `_bmad-output/planning-artifacts/prds/prd-hl-backend-kotlin-starter-2026-09-07/prd.md`
- **Rubric:** `.claude/skills/bmad-prd/assets/prd-validation-checklist.md`
- **Run at:** 2026-09-07T21:05:00Z
- **Grade:** Good (at time of run)

> **Post-run:** all 4 medium findings and the quick lows were fixed in `prd.md` immediately after this run, and the PRD was finalized (`status: final`). See the memlog for the change list. This report is retained as the snapshot that drove those fixes.

## Overall verdict

This is a disciplined, well-shaped capability spec for an internal build substrate. The thesis — plumbing that AI agents will extend, so structure must be enforced not merely documented — is stated, and the features follow from it; scope honesty is high and, after the architecture reconciliation, the open-items surface is small (six still-open assumptions, one NOTE FOR PM, zero open questions).

What keeps it from a clean bill are four medium gaps that the reconciliation introduced or exposed: the readiness-probe requirement (FR-19) was not updated alongside the rest of the Redis hard-dependency decision; the accepted availability cost of that decision is unstated; the pagination contract has no FR home (it lives only in the architecture); and the Auth Seam's treatment of the `/v3/api-docs` endpoint is unspecified. None block the architecture (already complete) and all are small edits, but they should be closed before epics and stories source-extract from this document.

## Dimension verdicts

- Decision-readiness — strong
- Substance over theater — strong
- Strategic coherence — strong
- Done-ness clarity — adequate
- Scope honesty — strong
- Downstream usability — strong
- Shape fit — strong

## Findings by severity

### Critical (0)

_None._

### High (0)

_None._

### Medium (4)

**[Mechanical / Adversarial]** — FR-19 readiness spec not updated with the Redis decision (§4.7 FR-19)
FR-10, the §4.4 description, §6.1 and §9.2 all say readiness gates on Redis. FR-19 — the actual readiness requirement — still lists only "datasource availability". An engineer implementing FR-19 from the PRD wires only the datasource, dropping the load-bearing half of the "fail fast, no degraded mode" decision.
Fix: FR-19 consequence → "…including datasource **and Redis** availability (FR-10)."

**[Decision-readiness]** — Redis hard-dependency trade-off is unstated (§4.4 FR-10, §11)
The mechanism is specified; the accepted cost is not — the service's availability is now bounded by Redis's, for a component introduced as a read optimisation, when Postgres alone could still serve every request. The architecture memlog accepted this explicitly; the PRD should carry the sentence.
Fix: one sentence in FR-10 or §11 Constraints naming the availability coupling as an accepted cost.

**[Adversarial]** — Auth status of the OpenAPI JSON endpoint is unspecified (§4.6 FR-18, §4.12 FR-37)
FR-18 commits to serving `/v3/api-docs` "in every profile" including production. FR-37 defines the Auth Seam's endpoint posture only for actuator paths; `/v3/api-docs` is neither actuator nor under `/api/v1/**`. When the seam is enabled, the PRD does not say whether the API schema is exposed to anonymous callers in production.
Fix: add a line to FR-18 or FR-37 stating whether `/v3/api-docs` requires a token when the Auth Seam is on, and reflect it in the FR-38 README section.

**[Done-ness]** — No functional requirement for the pagination contract (§4.5 FR-13)
FR-13 says the collection `GET` "lists paged" but no parameters or response shape. The `?page=`/`?size=` contract and the `{items,page,size,totalElements,totalPages}` envelope live only in architecture AD-10, while epics/stories are told to source-extract from the PRD.
Fix: add a testable consequence to FR-13 (or FR-13a) capturing the offset-paging parameters and the envelope shape.

### Low (6)

**[Done-ness]** — Graceful-shutdown default not stated (§4.7 FR-23)
Architecture fixes 30 s (AD-17); FR-23 says only "a documented default". Fix: state "30 s, env-overridable".

**[Done-ness]** — Resource-footprint NFR has no measurable bound (§10, §4.10 FR-32)
"Comfortable container limits" / "a small domain" are adjectives; FR-32's Kubernetes contract asserts nothing about memory or CPU. Fix: add a rough ceiling, or state that sizing is explicitly out of scope for v1 and owned by the charts repo.

**[Done-ness]** — "First honest attempt" is not adjudicable (§4.2 FR-6, §7 SM-2)
No definition of what makes an attempt dishonest. Fix: define it, or replace with "changing no Plumbing file".

**[Scope honesty]** — Stale inline wording (§10 Startup time)
The assumption still reads "confirm during architecture"; architecture is complete. Fix: "confirm during the build".

**[Downstream / Adversarial]** — FR-14 reads as contradicting the reconciled FR-5 (§4.5 FR-14)
"No Spring web or JPA types" is still true but must be read together with FR-5's new two-annotation allowance to not look inconsistent. Fix: "no Spring web, data, or JPA types (the two DI annotations of FR-5 aside)".

**[Adversarial / Mechanical]** — Smaller items
- FR-25's new root `.env` (Postgres/Redis image tags) is unmentioned in the Four-Parameters scope; SM-C3 counts "one home per parameter", so state `.env` is infra-only and not a parameter home.
- Title still "*Working title — confirm.*" and mismatched with the project name `hl-backend-kotlin-starter`.
- §12 still frames the addendum as "the architecture step's input"; that step is done and the addendum now carries per-section Resolved lines.
- `*(Resolved in architecture)*` tags conflate genuine reversals (FR-5, FR-10, FR-25) with plain confirmations (FR-1, FR-3, FR-8, FR-13, FR-22); consider "Overturned by" vs "Settled by".

## Mechanical notes

- FR-19 / Redis contradiction — see Medium above.
- `/v3/api-docs` Auth Seam gap — see Medium above.
- Title unconfirmed; project-name mismatch.
- §12 framing stale post-architecture.
- Assumptions Index round-trip clean: inline `[ASSUMPTION]` at FR-2 (×2), FR-11, FR-15, FR-33, FR-36, §10 all appear in §9.1.
- FR-39/FR-40 sit out of numeric order in §4.6 — pre-existing deliberate choice per §0, not a regression.

## Reviewer files

- `review-rubric.md`
- `review-adversarial-general.md`
