# Deferred work

Open items only. Items settled on 2026-10-04 (README filename rule and datasource env vars; lint-gate proof in CI; test failure logging; Kotlin JPA entities and DB-backed persistence tests; readiness and cache Integration Tests; framework-error `code`/`traceId`; other Redis exception types; `URI.create` guard; cache-after-commit advisor order; `jackson-module-kotlin`; main-vs-test YAML parity test; stale README package-layout text; and, after the owner's decisions, no LICENSE by design, optimistic locking via `WidgetEntity.version`, and JDBC tracing via `datasource-micrometer-spring-boot`) were fixed or found already done and removed from this file; `git log -p` has the history.

## Known, accepted trade-offs

- source_spec: `spec-2-8-cache-behavior-integration-test.md`
  summary: With Redis down, `update`/`delete` persist to Postgres and then fail on eviction, so the client sees a 500 for a write that committed and the stale cached value survives once Redis recovers.
  evidence: Eviction now runs after the transaction commits (cache advisor ordered outside the transaction advisor), which removes the pre-commit repopulation race but not this case. Fixing it would need TTL-bounded staleness to be accepted explicitly or an outbox-style approach; not warranted for the Starter.

## Open, unverified

- source_spec: `spec-2-10-served-openapi-and-local-only-swagger-ui.md`
  summary: `WidgetServiceCacheIT`'s "update evicts the Redis entry..." test failed once on GitHub's runner (assertion on the name after the update, line 67) and passes on repeated local runs, including under CPU load.
  evidence: Root cause not found. Not reproduced locally in 4 plain and 3 CPU-stressed full-suite runs after the pool cap and the advisor-order fix. The Gradle test log now prints full failure messages, so the next CI occurrence will show the actual value. Re-open if it recurs.

- source_spec: `spec-2-8-cache-behavior-integration-test.md`
  summary: `RedisDownIT` stops a live Redis mid-test; an already-open Lettuce connection could in principle still succeed on the next command, risking a flaky pass.
  evidence: Plausible only; passed in every CI run so far. Settle by repeated CI runs or an await-until-unreachable step if it ever flakes.

- source_spec: `spec-1-7-run-the-service-locally-on-the-local-profile.md`
  summary: No automated guard covers `bootRun`'s default-to-`local`-profile behavior in `build.gradle.kts`.
  evidence: Needs a Gradle TestKit functional test; the project has no TestKit infrastructure and the one-line default is easy to review.

- source_spec: `spec-2-1-widget-domain-model-jpa-entity-and-first-migration.md`
  summary: `WidgetEntity` is a final Kotlin class; a future lazy association or `getReferenceById()` may hit a Hibernate proxying failure.
  evidence: maybe-false; nothing reachable today. Check when the first story adds either.
