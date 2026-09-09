# Adversarial Seam Review — ARCHITECTURE-SPINE.md

**Reviewer stance:** adversary. Goal: find pairs of engineers/agents who each follow every AD (AD-1..AD-22) to the letter, on independently cloned Consumer Services (or on two resources within one Consumer Service), yet ship incompatible systems. Every scenario below is a genuine hole, not a style nitpick, unless explicitly flagged as minor.

**Reviewed:** `_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md` (2026-09-07 draft)

**Overall verdict:** The spine is unusually disciplined for the *single-resource, single-service* case — the `widgets` slice, cache-aside, transactions, errors and CI are all pinned down tightly. But the spine defines almost nothing about **multi-resource composition** (once a Consumer Service grows past its one example slice) and nothing about **cross-service data ownership** (once multiple Consumer Services are cloned from the same Starter and need to agree on shared concepts). Nearly every hole below lives at that seam: two resources, or two clones, each internally AD-compliant, that disagree about who owns what, how they talk to each other, or what a shared shape looks like. This is not a reason to block AD-1..AD-22 as written — they're solid — but at least 3-4 new ADs are needed before a second real resource gets built on top of this spine.

---

## Finding 1 — No rule for cross-resource orchestration or transaction scope (CRITICAL)

**Binds implicated:** AD-5 (outbound ports only), AD-6 (use case owns cache-aside), AD-7 (transaction boundary)

**Scenario.** Both engineers are adding a second resource, `Order`, that needs to touch `Widget` (e.g. decrement stock on order creation).

- **Engineer A** reads AD-5 literally: `application.x` declares outbound ports, `adapter.out` implements them, and nothing in AD-1/AD-2 forbids `application.order` from importing a type out of `application.widget`. So `CreateOrder` (in `application.order`, `@Transactional`) directly injects `WidgetRepositoryPort` from `application.widget` and mutates widget stock in the *same* transaction as the order insert. Fully AD-2/AD-5/AD-7 compliant: `@Transactional` still only sits on an application use case, and it's still the use case doing cache-aside.
- **Engineer B** reads the four-layer diagram as implying resource independence, and instead has `CreateOrder` call the *existing* `CreateWidget`/`AdjustWidgetStock` use case bean as a black box (treating another resource's use case as its public API, the way a controller would). Also fully AD-2/AD-5/AD-7 compliant — `@Transactional` is still only on application classes.

**Why it breaks.** These two patterns have different failure semantics. A gets one atomic transaction: order and stock either both commit or both roll back. B gets two independently-committed transactions (Spring proxy invocation, not self-invocation, so `AdjustWidgetStock`'s own `@Transactional` opens its own boundary) — if the order insert fails after the stock adjustment already committed, stock is now wrong with no compensating action. Nothing in AD-5/AD-7 says whether a resource's application layer may reach into another resource's ports directly, or must go through that resource's use case, or must not touch another resource's data at all (event/outbox instead). All three are "to the letter" compliant and produce different consistency guarantees on the same feature.

**Recommendation.** A new AD (e.g. AD-23 "Cross-resource composition") stating explicitly whether one resource's use case may (a) inject another resource's outbound ports, (b) call another resource's use case bean, or (c) neither — and if (a), that it does not change the atomicity guarantee, so it should be the default-encouraged pattern; if any cross-resource call is disallowed inside a single `@Transactional`, an explicit saga/outbox convention is needed instead of silence.

---

## Finding 2 — No entity-ownership rule for cross-resource / cross-service data (HIGH)

**Binds implicated:** AD-1, AD-4, AD-8 (implicitly — everything about "every persisted resource")

**Scenario.** Two Consumer Services are cloned from the Starter. Both need "who placed this" data: Service A adds an `Order` resource with an inline `customerName`/`customerEmail` denormalized onto the `orders` table (each field independently AD-4/AD-8 compliant: domain type, entity, UUID id, its own migration). Service B's engineer, working on the same feature independently, instead creates a first-class `Customer` resource (`domain.customer`, `adapter.out.persistence.customer`, its own table and repository) and has `Order` hold a `CustomerId` foreign key.

**Why it breaks.** Nothing in AD-1..AD-22 says how a Consumer Service should model "an entity another resource needs to reference": inline denormalization vs. a first-class owned resource are both AD-4-legal ("the domain type is an immutable Kotlin class... in `domain.x`"; nothing says resource `x` must not just be a value-holder on another resource's row). Because the Starter is explicitly meant to be cloned repeatedly (per the brief/PRD binds), and because AD-16 fixes the four clone-time parameters but says nothing about *modeling* conventions, every Consumer Service is free to invent its own answer — which is fine in isolation, but defeats the "agent legibility" / consistency goal the spine claims (NFR-agent-legibility) the moment two Consumer Services need to interoperate or a shared library/event contract is added later.

**Recommendation.** Either explicitly scope this out ("Deferred: cross-resource entity ownership; each resource is arm's length, no shared aggregate rules" — currently the Deferred section says "a Consumer Service adds these itself" for messaging/second datasource, but never for *entity references*, which is the actual gap), or add a short AD: "a value referenced by another resource is a `<Type>Id` value class from the owning resource's domain package, never a duplicated inline copy" (see also Finding 5).

---

## Finding 3 — `ErrorCode` is a single shared enum with no naming convention (MEDIUM-HIGH)

**Binds implicated:** AD-12, indirectly AD-3's own "Prevents" rationale (avoiding files every resource must edit)

**Scenario.** Engineer A adds `Order` and needs a not-found code; adds `NOT_FOUND` to `domain.shared.error.ErrorCode`. Engineer B, on a parallel branch, adds `Invoice` and also needs a not-found code, and — reading the existing `Widget`'s code (say `WIDGET_NOT_FOUND`) as the actual convention — adds `INVOICE_NOT_FOUND`. Both are AD-12-compliant ("a single Kotlin enum... each constant carries a status-free ErrorKind"); AD-12 never states a naming grammar for constants.

**Why it breaks.** Two things collide here. First, this is exactly the "central file every new resource must edit" failure mode AD-3 explicitly names as a thing to prevent for Spring wiring — but AD-12 mandates precisely that pattern for error codes, guaranteeing a merge conflict on `ErrorCode.kt` for every pair of resources built in parallel. Second, with no mandated naming grammar, a same-named generic constant (`NOT_FOUND`) added twice by two engineers for two different resources is a hard Kotlin compile clash forcing ad hoc renegotiation, while inconsistent naming (`NOT_FOUND` vs `WIDGET_NOT_FOUND` vs `ORDER_NOT_FOUND`) ships silently since nothing enforces it.

**Recommendation.** Tighten AD-12's Rule with a mandatory constant-naming grammar (e.g. `<RESOURCE>_<REASON>`, always resource-prefixed), and consider whether the enum should be partitioned (e.g. one enum per resource package, aggregated at the handler) to remove the merge-collision surface — note this trades off against AD-12's other goal of making "the set of errors a service can return" centrally discoverable, so it needs a deliberate decision, not silence.

---

## Finding 4 — AD-9's verb set is ambiguous about whether it's closed, so "update" gets two shapes (HIGH)

**Binds implicated:** AD-9

**Scenario.** Resource `Order` needs a partial-update capability (e.g. just change `status`). AD-9's Rule lists `POST`/`GET /{id}`/`GET` list/`PUT /{id}` full update/`DELETE`, and its "Prevents" clause is "per-resource path and versioning improvisation" — worded as preventing new *paths*, not new *verbs*.
- **Engineer A** reads the enumerated verb list as exhaustive (it's "One REST URL and verb convention") and refuses to add `PATCH`; instead they widen `OrderRequest` so every field is nullable and reinterpret `PUT /{id}` as "set the fields that are present, leave the rest," changing PUT from full-replace to merge-patch semantics.
- **Engineer B**, on `Invoice`, reads the verb list as merely the common case and adds `PATCH /{id}` for partial update, keeping `PUT /{id}` strictly full-replace.

**Why it breaks.** Both are letter-compliant with AD-9 (nothing says the verb list is closed, nothing defines PUT's null-handling semantics). But a client integrating with both resources (or an agent implementing a third resource, pattern-matching on whichever example it saw first) gets two different mutation contracts for the same word "update" — one where `PUT` omits-means-no-change, one where `PUT` omits-means-null-the-field-and-error/reject on PATCH absence. This is precisely the "conflicting state-mutation paths" failure mode.

**Recommendation.** Tighten AD-9: state explicitly that the five verbs are the *complete* set for v1 (no PATCH), that `PUT` is always full-replace (omitted optional fields become their default/null, never "unchanged"), and that partial-update needs are handled by the client re-sending the full resource — or, if partial update is actually needed, add it to the Rule now rather than leaving it inferable.

---

## Finding 5 — Cross-resource ID references: reuse the value class or duplicate it? (MEDIUM-HIGH)

**Binds implicated:** AD-1, AD-8

**Scenario.** `Order.widgetId` needs to reference a `Widget`. Engineer A imports `domain.widget.WidgetId` directly into `domain.order.Order` (reuses the existing `@JvmInline value class`). Engineer B, reading AD-1's "a resource `X` occupies `<layer>.x`" as implying resource packages should stay self-contained (so that a resource's Integration Test / package can be reasoned about, cloned or deleted independently per the "widgets slice is the template every resource copies" framing), instead defines a parallel `domain.order.WidgetRefId` wrapping a raw `UUID`, duplicating the type rather than importing across resource packages.

**Why it breaks.** Nothing in AD-1/AD-8 says whether same-layer, cross-resource imports are the intended pattern or something to avoid. A's approach couples `order`'s compile unit to `widget`'s package (fine until someone tries to delete/extract a resource cleanly, which the "resource is a self-contained package" framing implies should be possible); B's approach means the "same" identifier now has two distinct nominal types across resources with no shared marker, defeating the type-safety AD-8 is trying to buy (a `WidgetId` and a `WidgetRefId` wrapping the same UUID are not interchangeable, and nothing stops a third resource from making a third wrapper).

**Recommendation.** State explicitly (could ride on the same new AD as Finding 2): a value class from one resource's `domain.x` package is the one legal way to reference that resource's identifier from elsewhere; no resource re-wraps another's id.

---

## Finding 6 — No ErrorKind for business-level authorization denial (MEDIUM)

**Binds implicated:** AD-12, AD-18

**Scenario.** Resource `Order` needs "only the owning customer may update this order" — a business rule, not authentication (AD-18 only governs presence of a valid JWT). `ErrorKind` has exactly four values: `VALIDATION`, `NOT_FOUND`, `CONFLICT`, `INTERNAL` — none map naturally to 401/403. Engineer A maps this denial to `CONFLICT` (→ 409, the nearest existing status in the handler's map) since it's the "closest fit" without touching the enum. Engineer B instead extends `domain.shared.error.ErrorKind` with a new `FORBIDDEN` constant and adds a 403 mapping in the `@RestControllerAdvice`, reasoning that AD-12 never closes the `ErrorKind` set.

**Why it breaks.** Both are AD-12-compliant readings. A ships a semantically wrong 409 for an authorization failure; B correctly ships 403 but has silently expanded a "shared, one home" enum/handler that the next resource may or may not know exists, and there is no rule for what belongs in `ErrorKind` vs. what doesn't (is `FORBIDDEN` now canonical for everyone, or did Engineer B just invent a resource-local convention that a third resource won't discover from AD-12 alone?).

**Recommendation.** Either explicitly close `ErrorKind` at four values and state the intended mapping for business-authorization denials (reuse `CONFLICT`? add a fifth kind now, in the spine, rather than leaving it to be discovered per-resource), removing the ambiguity before the second resource needs it.

---

## Finding 7 — Cache TTL configuration: one global knob or per-resource? (MEDIUM)

**Binds implicated:** AD-6, AD-16

**Scenario.** AD-6 says "TTL is a configuration value with a documented default," singular, illustrated only by the one-resource `widgets` slice. Engineer A, building `Widget`, adds `app.cache.ttl-seconds` as one global property consumed by `WidgetCachePort`'s adapter. Engineer B later adds `Order`, whose data is more volatile and needs a shorter TTL; reusing the existing global key would wrongly change Widget's TTL too, so B adds a new, differently-shaped key `order.cache.ttl-seconds` (or `app.cache.order.ttl-seconds` — B has to guess the convention since none exists).

**Why it breaks.** Both engineers followed AD-6's Rule to the letter ("TTL is a configuration value with a documented default"); the spine never states whether that's one process-wide value or one-per-resource, or what the property key grammar is. AD-16 ("The Four Parameters, and nothing else... Each has exactly one documented home") doesn't cover this because TTL isn't a clone-time parameter — but that makes it easy to assume TTL is *not* supposed to vary per-resource at all, which conflicts with the realistic need for different TTLs per resource's volatility.

**Recommendation.** Tighten AD-6 with an explicit property-key grammar, e.g. `app.cache.<resource>.ttl-seconds` per resource, each with its own documented default, so two engineers land on the same shape independently.

---

## Finding 8 — Cross-resource DTO reuse vs. duplication for nested views (MEDIUM)

**Binds implicated:** AD-9, AD-1

**Scenario.** `OrderResponse` needs to show a summary of the referenced widget (name, price) alongside the order. Engineer A defines `WidgetSummary` inside `adapter.in.web.widget` (next to `WidgetResponse`) and has `OrderResponse` import and embed it — reuse. Engineer B, treating each resource's adapter package as self-contained (mirroring the "resource occupies `<layer>.x`" framing, same ambiguity as Finding 5 but at the DTO layer), instead defines a redundant `OrderWidgetView` inside `adapter.in.web.order` with the same three fields, independently maintained.

**Why it breaks.** AD-9's Rule only names the top-level `<Resource>Request`/`<Resource>Response` pattern; it says nothing about nested/embedded view types. A's approach means a change to `Widget`'s public shape silently ripples into `Order`'s API contract (a cross-resource coupling nobody decided on); B's approach means the two views can drift out of sync (e.g. Widget adds a `discontinued` flag that never reaches `OrderWidgetView`) with no compiler signal either way. Both are "to the letter" AD-9-compliant.

**Recommendation.** State a convention for embedded/nested response fragments — e.g. "a resource may expose a `<Resource>Summary` DTO in its own `adapter.in.web.x` package for other resources to embed; no resource duplicates another's fields inline" (or the opposite, if duplication-for-decoupling is actually the intended pattern) — either answer is fine, silence isn't.

---

## Finding 9 — Shared-container integration tests + per-test cleanup, with FK relationships across resources (MEDIUM)

**Binds implicated:** AD-21

**Scenario.** AD-21 mandates one shared Postgres/Redis container pair per JVM and "per-test data cleanup, not per-test containers," without specifying the cleanup mechanism. `WidgetIT` (Engineer A, first resource) does `TRUNCATE widgets` in `@AfterEach`. Engineer B later adds `Order` with a FK to `widgets`; `OrderIT`'s cleanup truncates only `orders`. Both are AD-21-compliant in isolation ("adds nothing infrastructural" beyond extending the shared base class).

**Why it breaks.** Run in the same JVM/suite (which AD-21 guarantees, since containers are shared once per JVM), `WidgetIT`'s `TRUNCATE widgets` will fail or cascade unexpectedly once an `orders` row FKs into `widgets` and B's `OrderIT` didn't clean up first, or vice versa — depending on run order, one resource's leftover rows leak into another's assertions (e.g. a `GET /api/v1/widgets` list-count assertion in `WidgetIT` becomes flaky depending on whether `OrderIT` ran first and left widget rows behind it created as fixtures). AD-21 never specifies whether cleanup is table-scoped-per-test-class (fragile once FKs exist) or a shared truncate-everything hook in the base class.

**Recommendation.** Tighten AD-21 with an explicit cleanup mechanism owned by the shared base class (e.g. `TRUNCATE ... CASCADE` across all app tables between tests, or a fixed truncation order list maintained centrally) rather than leaving each resource's IT to invent its own scope.

---

## Lower-severity notes (flagged, not written up in full)

- **Sorting/filtering on list endpoints** is unaddressed by AD-10 and, unlike cursor pagination, isn't even named in Deferred — two resources will likely invent different `?sort=` conventions. (LOW-MEDIUM)
- **Migration number collisions across parallel branches** — AD-14's sequential-integer scheme has no reservation mechanism; two engineers on separate branches adding resource migrations will both claim `V2__...sql` and one PR has to renumber at merge time. Process hazard more than an architecture hole, but worth a one-line mitigation note (e.g. timestamp-prefixed migrations) since AD-14 currently invites the collision. (LOW)
- **Hand-written entity↔domain mapping location** (AD-4) — member function vs. companion-object factory vs. top-level extension function is unspecified; cosmetic drift only, doesn't break interop. (LOW)

---

## Summary table

| # | Finding | Severity | ADs implicated |
|---|---|---|---|
| 1 | No cross-resource orchestration/transaction-scope rule | Critical | AD-5, AD-6, AD-7 |
| 2 | No cross-resource/cross-service entity-ownership rule | High | AD-1, AD-4, AD-8 |
| 3 | Shared `ErrorCode` enum, no naming grammar | Medium-High | AD-12, AD-3 |
| 4 | AD-9 verb set ambiguously open/closed; PUT semantics undefined for partial update | High | AD-9 |
| 5 | Cross-resource id reuse vs. duplicate wrapper | Medium-High | AD-1, AD-8 |
| 6 | No ErrorKind for business-authorization denial | Medium | AD-12, AD-18 |
| 7 | Cache TTL: global vs. per-resource property shape | Medium | AD-6, AD-16 |
| 8 | Nested/embedded DTO reuse vs. duplication | Medium | AD-9, AD-1 |
| 9 | Shared-container IT cleanup ordering across FKs | Medium | AD-21 |
