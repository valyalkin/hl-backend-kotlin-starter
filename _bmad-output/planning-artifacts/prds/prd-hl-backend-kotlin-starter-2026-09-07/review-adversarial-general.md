# Adversarial Review — Spring Boot Kotlin Starter Service PRD

Stance: assume the reconciliation introduced seams. Look for places where a decision folded in from the architecture now contradicts an older paragraph, or where "resolved" hides an unanswered question.

## Findings

- **medium — The readiness probe spec was not updated with the rest of the Redis decision.**
  FR-10, §4.4 description, §6.1, §9.2 all now say the Redis health contributor is in the readiness group. FR-19 — the actual readiness requirement — still says readiness "reflects readiness to serve, including datasource availability" and stops there. An engineer implementing FR-19 from the PRD wires only the datasource. The Redis membership is the load-bearing half of the "fail fast, no degraded mode" decision and it lives in the wrong FR.
  *Fix:* FR-19 consequence → "…including datasource **and Redis** availability (FR-10)."

- **medium — `/v3/api-docs` in production, auth status unspecified.**
  FR-18 now commits to serving the OpenAPI JSON "in every profile" including production. FR-37 defines the Auth Seam's endpoint posture but only for actuator (`health`/`info`/`prometheus`). springdoc's `/v3/api-docs` is not an actuator endpoint and not under `/api/v1/**`. So when the seam is enabled, the PRD does not say whether the API schema is served to anonymous callers in production. That is a genuine security-relevant decision with no home.
  *Fix:* add a line to FR-18 or FR-37 — either "`/v3/api-docs` requires a token when the Auth Seam is enabled" or "…stays open; the schema is not considered sensitive" — and note it in the Auth Seam README section (FR-38).

- **medium — Pagination has a description but no requirement.**
  FR-13 says the collection GET "lists paged". Nothing in the PRD says with what parameters or what the response body looks like. AD-10 in the architecture defines `?page=`/`?size=` and the five-field envelope, but epics/stories are told to source-extract from the PRD. As written, "lists paged" is the same class of soft phrase the rubric warns about.
  *Fix:* fold the offset-paging parameters and envelope shape into FR-13 as a testable consequence, or add FR-13a.

- **low — "Redis a hard dependency" vs UJ-1's promise.**
  UJ-1 and the Vision lean on "it works on my laptop == CI == Kubernetes". With Redis now able to hold the pod out of rotation, the failure mode "Redis container didn't start locally" now looks identical to a production outage — which is arguably *good* (parity), but the PRD never connects the two. A sentence in UJ-1's edge-case list ("Redis not up → readiness never goes green, same as prod") would make the parity intentional rather than incidental.

- **low — FR-14 reads as contradicting the reconciled FR-5.**
  "The application use case contains no Spring web or JPA types" was written when the assumption was *no Spring at all* in application. FR-5 now explicitly admits `@Service`/`@Component`/`@Transactional`. FR-14 is not technically wrong (those are neither web nor JPA) but the two FRs now need to be read together to not look inconsistent.
  *Fix:* one clause in FR-14.

- **low — `.env` is new Plumbing the Four-Parameters story doesn't mention.**
  FR-25 now introduces a root `.env` holding Postgres/Redis image tags, read by both Compose and Testcontainers. FR-33's assumption about "a value that must appear twice" uses "port in config and in a compose healthcheck" as its example. A reader could reasonably wonder whether the HTTP port or DB name also lives in `.env`. It does not — `.env` is infra image tags only — but the PRD should say so, because "one documented home per parameter" (SM-C3) is a metric someone will check.
  *Fix:* FR-25 or FR-33 — "the `.env` file holds only Postgres/Redis image tags and is not one of the Four Parameters' homes."

- **low — "Resolved in architecture" is doing two different jobs.**
  Some `*(Resolved in architecture: AD-x)*` tags mark a genuine reversal of a stated PRD position (FR-5, FR-10, FR-25). Others just record that an open assumption got a concrete value (FR-1, FR-3, FR-8, FR-13, FR-22). A reader skimming for "what changed" can't tell the reversals from the confirmations. Minor, but consider "Overturned by AD-x" vs "Settled by AD-x".

## Not findings (checked, fine)

- FR-7's "not a silent degraded mode" for the datasource is now consistent with — not contradicted by — the Redis hard-dependency stance. Nice parallel.
- §9.1 / §9.2 split round-trips correctly against the remaining inline tags.
- The AD-NN references all resolve to real decisions in `ARCHITECTURE-SPINE.md`.
- No FR-number collisions from the edits; FR-39/FR-40 placement is a pre-existing deliberate choice, not new.
