# Reviewer Gate — Rubric Review

**Target:** `ARCHITECTURE-SPINE.md` (Spring Boot Kotlin Starter Service, 2026-09-07)
**Reviewed against:** PRD `prd-hl-backend-kotlin-starter-2026-09-07/prd.md`
**Reviewer:** Claude (subagent), 2026-09-09

## Overall Verdict

The spine is unusually disciplined — 22 ADs each carry Binds/Prevents/Rule, the Capability → Architecture Map traces every FR-1..FR-40 to a governing AD, the operational envelope is drawn explicitly (dev laptop → CI → self-hosted K8s/GHCR/Argo CD), and the Deferred list mostly closes off divergence rather than leaving it open. It is close to ready, but not clean: one PRD-flagged divergence point was explicitly left for architecture to absorb and was not absorbed (AD-18 / `/v3/api-docs`), and two of the "enforced by ArchUnit" claims (AD-3, AD-6) are not actually covered by AD-2's literal banned-import list. Recommend: fix before finalizing, not a full re-do.

## Findings

### HIGH — AD-18's Rule does not cover `/v3/api-docs`, despite the PRD explicitly flagging this for architecture to resolve

PRD FR-18 states: *"When the Auth Seam (FR-36) is enabled, the OpenAPI JSON endpoint (`/v3/api-docs`) requires a valid token... (Extends the AD-18 matcher set; PRD decision, fail-safe by default.)"* and §9.2 explicitly says: *"PRD decision this update; extends the AD-18 matcher set — **flag for the architecture step to absorb**."*

AD-18's Rule, as written, is: *"Enabled requires a valid JWT on `/api/**`, leaves `/actuator/health**` open, and requires a token for every other actuator endpoint."* `/v3/api-docs` is neither under `/api/**` nor under `/actuator/**` — springdoc serves it at its own root path. So the matcher set AD-18 actually specifies leaves `/v3/api-docs` in an undefined bucket: not explicitly protected, not explicitly open. This is exactly the kind of divergence point this spine exists to close — two independently built Consumer Services (or two AI agents extending the seam) could easily land on opposite answers (one leaves the OpenAPI JSON open when auth is enabled, one protects it), and the PRD's own resolved-assumption trail (§8 OQ-5, §9.2) implies this was supposed to be folded into AD-18 and wasn't.

**Fix:** Add `/v3/api-docs` (and reasonably `/swagger-ui/**` if it's ever served outside `local`) to AD-18's explicit matcher rule.

### MEDIUM-HIGH — AD-3 and AD-6's Rules are not actually enforced by the mechanism the spine cites (AD-2's ArchUnit banned-import list)

AD-3's Rule claims the application layer "sees exactly two Spring annotations" (`org.springframework.stereotype`, `@Transactional`) and "nothing else from Spring." AD-6's Rule forbids `@Cacheable`/`@CacheEvict` in the application layer. Both read as allowlist/negative-space claims.

But AD-2 — the AD that says dependency direction "is enforced by ArchUnit" — specifies application's banned imports as a **blocklist**: `org.springframework.web..`, `org.springframework.data..`, `org.springframework.http..`, `jakarta.persistence..`, `jakarta.servlet..`, `org.hibernate..`. Nothing in that list bans `org.springframework.cache..`, `org.springframework.scheduling..`, `org.springframework.validation..`, `org.springframework.context..`, etc. So an application-layer class could import `org.springframework.cache.annotation.Cacheable` — directly violating AD-3's "exactly two annotations" claim and AD-6's explicit "not `@Cacheable`" rule — and the ArchUnit test as specified would not catch it. The checklist's bar is "every AD's Rule is enforceable and actually prevents its stated divergence"; as written, AD-3/AD-6 fail that bar for any Spring package outside AD-2's literal blocklist.

**Fix:** Either state AD-2's application-layer check as an allowlist (only `org.springframework.stereotype..` and `org.springframework.transaction.annotation..` permitted from `org.springframework..`) or extend the banned-import list to include `org.springframework.cache..` explicitly, plus a catch-all "no other `org.springframework..` package."

### MEDIUM — The Deferred note on messaging/gRPC/second datasource has no landing spot given AD-1/AD-16's closed, invariant layer list

Deferred says: *"Messaging, gRPC, a second datasource, multi-module build — a Consumer Service adds these itself; nothing in this spine forecloses them."* But AD-1's Rule fixes the layer set as exactly `domain`, `application`, `adapter.in.web`, `adapter.out.persistence`, `adapter.out.cache` — a closed list, not "adapter.out.* extensible." AD-16 then says *"the base package (AD-1), the layer paths, and every ArchUnit rule literal are invariant and are never touched"* as the mechanism that keeps parameterization cheap.

Taken together, a Consumer Service that adds messaging (e.g., a new `adapter.out.messaging` layer) has no documented way to do so without touching the "invariant" ArchUnit rule literals AD-16 forbids touching. This is silent on exactly the kind of decision that would otherwise be made once and then diverge per-service: does adding a new adapter-out concern require a documented, blessed extension procedure (e.g., "add a new adapter.out.<concern> layer per AD-2's pattern, extend the ArchUnit ruleset the same way"), or is it left to individual judgment? As written, two Consumer Services independently adding Kafka would plausibly diverge on package location and on whether/how they touch the ArchUnit config. This is a "Deferred" item that could let independently-built units diverge in practice — the checklist's specific concern.

**Fix:** Either add a short AD (or a sentence to AD-1/AD-2) describing the extension pattern for a new adapter-out layer, or explicitly say in Deferred that AD-16's invariance is scoped to the four v1 layers and does not bind future layers.

### MEDIUM — Deferred's cursor/keyset-pagination note directly contradicts AD-10's own stated "Prevents"

AD-10's Prevents clause: *"each resource inventing a list envelope."* Its Rule: *"Offset paging... One pagination contract."* But Deferred says: *"Cursor/keyset pagination — AD-10 is offset paging; revisit **per-resource** when a collection outgrows it."* "Per-resource" revisiting is precisely "each resource inventing a list envelope" — the divergence AD-10 exists to prevent. This isn't fatal (it's a reasonable escape hatch for a real scaling problem), but as worded it's an internal contradiction between an AD's stated guarantee and the Deferred section's escape hatch, and it gives no guidance on what changes when a resource "outgrows" offset paging (new envelope shape? versioned endpoint? both old and new coexisting on one resource?).

**Fix:** Reword to something like "a future resource-specific pagination mode would be a new, explicitly-versioned AD, not a per-resource ad hoc choice" — or accept the risk explicitly and say so.

### LOW — NFR-portability is bound in frontmatter but has no explicit AD, convention-table row, or Deferred entry

The frontmatter's `binds:` list includes `NFR-portability` ("works on Linux and macOS with only a JDK and Docker installed"). Every other bound NFR maps to at least one AD (hermetic-tests→AD-21, configuration→AD-15, determinism→AD-22, agent-legibility→ the whole convention apparatus, observability-parity→AD-17). Portability is satisfied only implicitly (nothing in the stack is Linux-only; buildpacks/Testcontainers/Docker Compose are cross-platform) but is never explicitly decided anywhere. Low risk since nothing currently contradicts it, but per the checklist's "every dimension the altitude owns is decided, deferred, or an explicit open question," this is a silent NFR.

**Fix:** A one-line addition to Consistency Conventions or a Deferred entry ("portability is a property of the chosen tools, not a separate rule; no OS-specific tooling is permitted") would close this cleanly.

### LOW / NEEDS VERIFICATION — "Self-hosted Kubernetes" in the operational-envelope diagram is not traceable to an AD or a cited PRD statement

The Operational Envelope mermaid diagram labels the K8S subgraph "Self-hosted Kubernetes." Neither the spine's prose nor the PRD excerpt reviewed here states this explicitly (the PRD only says "Kubernetes," "the cluster," and "a separate charts repository," without specifying self-hosted vs. managed/cloud). This may be legitimately sourced from the product brief (not reviewed here), in which case it's fine — but as it stands it's an unsourced specific claim embedded only in a diagram label, not backed by a Rule or a traceable citation, which brushes against "no invented content." Recommend confirming against the brief and, if correct, promoting it to a stated fact in prose (not just a diagram label) with a source reference.

## What the spine does well (for balance)

- Every FR-1..FR-40 is traced through the Capability → Architecture Map; no PRD feature area is silently uncovered.
- The operational envelope (dev laptop → CI → K8s, GHCR, charts/Argo CD) is drawn explicitly and answers deployment/infra/ops questions the checklist flags as easy to leave silent.
- AD-13 (Redis hard dependency) and AD-18 (Auth Seam fail-safe) are exemplary Rules: concrete, fail loud, and directly overturn/settle PRD assumptions (OQ-2, and the auth fail-safe posture) with an explicit "Overrides" note — exactly the traceability the checklist wants.
- The Deferred list is mostly well-formed: each entry names the interface point that keeps the future work from being a rewrite (e.g., Vault → AD-15 is the whole prep; UUID v7 → one-line swap in `<Resource>Id.new()`).
- No template placeholders, no `[TODO]`/Lorem-ipsum artifacts found; every AD has Binds/Prevents/Rule populated.

## Recommendation

Fix the HIGH finding (AD-18 `/v3/api-docs` matcher) before finalizing — it's a concrete, PRD-flagged gap. Strongly recommend fixing the AD-3/AD-6 enforceability gap in the same pass since it's a one-line change to AD-2's banned-import list. The two MEDIUM Deferred-section contradictions (messaging/gRPC landing spot, cursor-pagination escape hatch) are worth a sentence each but are not blocking. LOW items are optional polish.
