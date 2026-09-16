# hl-backend-kotlin-starter

A Spring Boot (Kotlin) starter service. This repository is cloned to create a new
Consumer Service.

## Package layout

The root package is `com.hl.service` and is invariant — it is never renamed when
the starter is cloned into a service.

Under the root package there are exactly seven concern packages. Each one holds
every resource's classes for that concern; there is no per-resource subpackage
requirement, though a resource may group its own files with a shared filename
prefix (`Widget*`) if a package grows large.

| Package | Holds |
| --- | --- |
| `com.hl.service.controller` | `@RestController` classes and the single `@RestControllerAdvice` `GlobalExceptionHandler` |
| `com.hl.service.service` | `@Service` classes; business logic; `@Transactional`, `@Cacheable` / `@CacheEvict` |
| `com.hl.service.repository` | Spring Data `JpaRepository` interfaces and their `@Entity` types |
| `com.hl.service.model` | Plain immutable domain classes |
| `com.hl.service.dto` | Request / response DTOs (`<Resource>Request`, `<Resource>Response`) and the shared page envelope |
| `com.hl.service.error` | Exception types (`BusinessException`, `NotFoundException`, `SystemException`) |
| `com.hl.service.config` | `@Configuration` classes and framework wiring |

The starter ships with none of these classes yet — the table describes where
each kind of code belongs as the service is built out. Every package directory
currently holds only an empty `.gitkeep` so it survives a fresh clone; delete
that file once the package contains a real class.

Dependencies point the conventional Spring direction — controller → service →
repository — but this layout is a **naming convention only**. There is no
ArchUnit test, dependency-direction check, or any other build-time boundary
enforcement: nothing fails the build if a resource places a class in a different
package.

`StarterApplication.kt` sits directly under the root package and is the single
`@SpringBootApplication` entry point.

## Local development

Following the steps below top to bottom, on a clean machine with only Docker and
a JDK installed, gets you a running service and a green `./gradlew build` — no
other setup is required.

### Prerequisites

- **Docker** (with Compose) — runs the local Postgres and Redis.
- **A JDK on `PATH`, version 17–26** — needed only to launch the Gradle wrapper
  itself; the wrapper then downloads and runs the exact Gradle version
  (9.7.1), and the build's own toolchain provisions JDK 25 to compile and run
  the service. No local Gradle install is needed or used.

### Bring up dependencies

Local Postgres and Redis run as a Compose stack. From the repo root:

```sh
docker compose up -d
```

This starts exactly two services — `postgres` and `redis` — and nothing else.
If ports 5432 or 6379 are already taken locally, stop whatever's using them
first (or edit the port mappings in `docker-compose.yaml`).

Their image versions are pinned once, in the root `.env` file
(`POSTGRES_IMAGE`, `REDIS_IMAGE`). That file is committed on purpose: it holds
only the two image references, no secrets. A shared Testcontainers base class
(`IntegrationTestBase`) reads the same `.env` for its Integration Tests, so
that local development and CI stay pinned to the identical Postgres and Redis
versions.

### Run the service

With the Compose stack up:

```sh
./gradlew bootRun
```

`bootRun` defaults `SPRING_PROFILES_ACTIVE` to `local` when the shell hasn't
already set it. That profile points the datasource at the Compose stack's
throwaway Postgres credentials; Redis needs no profile-specific credentials —
it connects with the plain `localhost:6379` default already in
`application.yaml`, which matches the Compose stack's no-auth Redis. Either
way, `bootRun` applies Flyway migrations and starts the service on port 8080
in the foreground (`Ctrl-C` to stop it). Confirm it's up:

```sh
curl http://localhost:8080/actuator/health
```

which reports `{"groups":["liveness","readiness"],"status":"UP"}` once the
readiness group (datasource + Redis) is satisfied. If port 8080 is already
taken, stop whatever's using it first. To run against something other than
the Compose stack, export `SPRING_PROFILES_ACTIVE` (or the individual
`SPRING_DATASOURCE_*` / `SPRING_DATA_REDIS_*` variables) yourself before
invoking `bootRun` — an explicit value always wins over the `local` default.

When you're done, stop `bootRun` (`Ctrl-C`) and tear down the dependencies
with `docker compose down`.

### Metrics

`/actuator/prometheus` serves JVM, HTTP server, datasource, and cache metrics
in Prometheus text format, ready for a Prometheus server to scrape:

```sh
curl http://localhost:8080/actuator/prometheus
```

The response's `Content-Type` is Prometheus text format
(`text/plain;version=0.0.4`); point a Prometheus server's scrape config at
this same URL as its target and no further wiring is needed.

Actuator's web exposure is widened to exactly `health`, `info`, and
`prometheus` (Story 3.1) -- every other actuator endpoint, e.g.
`/actuator/env`, stays hidden (404) on the same main port; there is no
separate management port.

### Tracing

Distributed trace export (Story 3.2) is configuration-only -- there is no
`@Configuration` class, custom `SpanExporter`, or other tracing Kotlin code.
It is driven by two `management.*` properties:

- `management.opentelemetry.tracing.export.otlp.endpoint` -- the OTLP/HTTP
  collector URL (e.g. `http://localhost:4318/v1/traces`). **Unset by
  default** on every checked-in profile, which is a genuine no-op: Boot's
  OTLP tracing auto-configuration only creates an `OtlpHttpSpanExporter` bean
  once this property has a value, so with it unset there is no exporter bean,
  no export attempt, and no connection-refused log lines -- not merely a
  silently-failing exporter pointed at a default `localhost` address. Set it
  (env var `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`) to send
  spans to a real collector.
- `management.tracing.sampling.probability` -- fraction of traces sampled,
  **defaults to `1.0`** (every trace) in this repo, overridable per
  environment via `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`. Sampled spans
  are simply dropped, not sent anywhere, while the endpoint above is unset.

Once the endpoint is configured, an inbound HTTP request and its outbound
Redis cache-aside calls (Story 2.7) each produce a span automatically, with
zero application code: Spring MVC's request instrumentation and Lettuce's own
Micrometer Tracing integration both activate as soon as the OTel bridge
dependency puts a `Tracer` bean in the context. Outbound Postgres/JDBC calls
do **not** currently produce their own span -- Spring Boot has no built-in
JDBC-level tracing instrumentation, so a request that only touches the
database (no cache hit or miss) exports just the inbound HTTP span. The
existing `traceId` field in error responses (`GlobalExceptionHandler`) is
populated automatically too: the OTel bridge writes the active trace/span ids
into SLF4J's MDC (keys `traceId`/`spanId`) as soon as it's on the classpath,
independent of whether an endpoint is configured.

There is no OTel collector or trace viewer in the Compose stack (out of scope
for v1) -- point `management.opentelemetry.tracing.export.otlp.endpoint` at
your own collector to see spans.

### Logs

Console logging is structured JSON (Story 3.3) on every profile except
`local`, driven by one property -- `logging.structured.format.console: ecs`
in `application.yaml` -- no logging encoder dependency, no owned
`logback.xml`/`logback-spring.xml`. `ecs` is Spring Boot's built-in Elastic
Common Schema formatter: the vendor-neutral choice for a starter template
with no downstream log sink specified. Every stdout line becomes single-line
JSON with at least `@timestamp`, a nested `log.level`/`log.logger`, and
`message`; a line emitted while a request's span is active also carries
top-level `traceId`/`spanId` fields, sourced automatically from the same
MDC entries [Tracing](#tracing)'s OTel bridge already populates -- this
story adds no MDC wiring of its own, only changes how the existing output is
encoded:

```json
{"@timestamp":"2026-09-16T14:54:13.052509Z","log":{"level":"ERROR","logger":"com.hl.service.controller.GlobalExceptionHandler"},"message":"...","traceId":"4be59b06a48814ba225d82b52c94fb87","spanId":"c659c01cf9dd1693"}
```

The `local` profile overrides `logging.structured.format.console` back to an
empty value in `application-local.yaml`, keeping the human-readable console
pattern (the same colorized single-line format used before this story) for
local development -- run `./gradlew bootRun` and check your terminal.

### Run tests

```sh
./gradlew build
```

runs the full test suite — unit tests plus `@Tag("integration")` Integration
Tests — and requires a working Docker daemon: every `@SpringBootTest`
Integration Test extends `IntegrationTestBase`, which starts a Postgres and a
Redis Testcontainer once per JVM via `@ServiceConnection`, so no running
Compose stack is needed but Docker itself must be available to pull and run
those images. On a clean machine, the first run also needs outbound
network/registry access to pull the pinned `postgres:18.1` and `redis:8.2.9`
images; later runs reuse the local Docker image cache. `build` also runs the
format/lint check; `./gradlew spotlessApply` auto-fixes formatting
violations.

### View API docs

`/v3/api-docs` serves the OpenAPI JSON document on every profile. Swagger UI
(`/swagger-ui/index.html`) is available only under the `local` profile
(`springdoc.swagger-ui.enabled: true`); every other profile returns 404 for
it. There is no local trace viewing in v1 either; see [Tracing](#tracing) --
trace export is configuration-only, with nothing to view without an external
collector.

### Add a REST resource

The `widgets` slice under `com.hl.service.*` is the copy-paste template for
every future resource. Adding a resource -- `Gadget` in this example -- means
creating the following files, one per concern package, mirroring the
`widgets` slice file-for-file:

| File | Concern package / location | Purpose |
| --- | --- | --- |
| `model/Gadget.kt` | `com.hl.service.model` | Plain immutable domain class |
| `repository/GadgetEntity.kt` | `com.hl.service.repository` | `@Entity` JPA mapping to its table |
| `repository/GadgetRepository.kt` | `com.hl.service.repository` | `JpaRepository<GadgetEntity, UUID>` interface |
| `service/GadgetService.kt` | `com.hl.service.service` | `@Service` business logic; owns `@Transactional`, `@Cacheable` / `@CacheEvict` |
| `controller/GadgetController.kt` | `com.hl.service.controller` | `@RestController`, e.g. `@RequestMapping("/api/v1/gadgets")` |
| `dto/GadgetRequest.kt` | `com.hl.service.dto` | Request DTO |
| `dto/GadgetResponse.kt` | `com.hl.service.dto` | Response DTO |
| `db/migration/V<next>__create_gadgets.sql` | `src/main/resources/db/migration` | New Flyway migration -- next sequential `V` version (`widgets` is `V1`, so a second resource is `V2`); immutable once merged |

Before naming `V<next>`, check the latest migration already merged on the
integration branch, not just your local checkout -- two branches picking the
same next version number in parallel will collide at merge time.

Plus tests, mirroring the `widgets` slice's own test files one-for-one:

- Unit Tests for the entity, service, and controller (e.g. `GadgetEntityTest`,
  `GadgetServiceTest`, `GadgetServiceCacheTest`, `GadgetControllerTest`),
  using a hand-written fake repository (`support/FakeGadgetRepository.kt`)
  the same way `support/FakeWidgetRepository.kt` backs the widget Unit
  Tests.
- One or more Integration Tests that extend the shared
  `support.IntegrationTestBase` (AD-21) rather than standing up their own
  Testcontainers -- covering the repository against real Postgres (a
  `GadgetRepositoryIT`), the cache against real Redis (a
  `GadgetServiceCacheIT`), and the full HTTP-to-store path (a
  `GadgetHttpToStoreIT`).

Adding a resource this way is a zero-Plumbing change: every file above is
new. Nothing about it requires editing a build file (`build.gradle.kts`,
`settings.gradle.kts`), configuration (`application.yaml`), observability
wiring, container config (`docker-compose.yaml`), or the CI workflow --
`git diff --name-only` after adding a resource should show only new files
under `src/main` and `src/test`, plus the new migration.

### Remove the Example Slice

Once a service has its own resources, the `widgets` slice can be deleted
entirely. Delete these files:

**Production**

- `src/main/kotlin/com/hl/service/model/Widget.kt`
- `src/main/kotlin/com/hl/service/repository/WidgetEntity.kt`
- `src/main/kotlin/com/hl/service/repository/WidgetRepository.kt`
- `src/main/kotlin/com/hl/service/service/WidgetService.kt`
- `src/main/kotlin/com/hl/service/controller/WidgetController.kt`
- `src/main/kotlin/com/hl/service/dto/WidgetRequest.kt`
- `src/main/kotlin/com/hl/service/dto/WidgetResponse.kt`
- `src/main/resources/db/migration/V1__create_widgets.sql` -- if Postgres has
  already applied this migration in a prior `docker compose`/`bootRun`
  session, deleting the file leaves Flyway's `schema_history` referencing a
  migration that no longer exists on disk; with `validate-on-migrate: true`
  and no repair configured, startup fails loudly. Reset the local volume
  first (`docker compose down -v`) so Flyway starts from a clean database.

**Test**

- `src/test/kotlin/com/hl/service/WidgetHttpToStoreIT.kt`
- `src/test/kotlin/com/hl/service/controller/WidgetControllerTest.kt`
- `src/test/kotlin/com/hl/service/repository/WidgetEntityTest.kt`
- `src/test/kotlin/com/hl/service/repository/WidgetRepositoryIT.kt`
- `src/test/kotlin/com/hl/service/service/WidgetServiceCacheIT.kt`
- `src/test/kotlin/com/hl/service/service/WidgetServiceCacheTest.kt`
- `src/test/kotlin/com/hl/service/service/WidgetServiceTest.kt`
- `src/test/kotlin/com/hl/service/support/FakeWidgetRepository.kt` -- not
  `Widget*`-named, but widget-only test support with no other caller.

Three more test files are not widget-named but hard-code the
`/api/v1/widgets` path (or a `widgets` cache/resource reference) and must be
edited, not deleted, or the build breaks:

- `src/test/kotlin/com/hl/service/OpenApiIT.kt` -- the assertion
  `assertThat(paths).containsKey("/api/v1/widgets")` must point at your own
  resource's path instead (or be deleted if you have not added a
  replacement resource yet).
- `src/test/kotlin/com/hl/service/RedisDownIT.kt` -- both the
  `.uri("/api/v1/widgets/$id")` request and the
  `.jsonPath("\$.instance").isEqualTo("/api/v1/widgets/$id")` assertion
  must point at your own resource's equivalent read-by-id path (or the
  test deleted if no replacement resource exists yet).
- `src/test/kotlin/com/hl/service/PrometheusMetricsIT.kt` -- imports
  `WidgetRequest` and hard-codes `POST`/`GET /api/v1/widgets` to warm a
  cache before scraping `/actuator/prometheus` (Story 3.1); point it at
  your own resource's create/read-by-id calls instead (or drop that
  warm-up and rely on your own resource's own cache Integration Test to
  cover cache metrics, if none exists yet).

All three files' class/method doc comments also reference widgets by name
(and `OpenApiIT.kt`'s test still has a `widgets path reflected` display
name, plus `RedisDownIT.kt`'s class doc still cites the now-deleted
`WidgetHttpToStoreIT`) -- update those doc comments and test names to match
your own resource too, not just the path/request assertions above.

`SwaggerUiLocalProfileIT.kt` needs no edit -- it has no widget-specific
assertions.

`application.yaml`, `PageResponse.kt`, and `CacheConfig.kt` still mention
"widget" in a doc comment after following this guide -- expected, cosmetic,
unrelated Plumbing comments with no compile or runtime coupling; leave them
as-is. The one exception is `spring.cache.cache-names: widgets` in both
`src/main/resources/application.yaml` and `src/test/resources/application.yaml`
(Story 3.1): that's a real runtime coupling, not cosmetic -- update it to
your own resource's cache name (or drop the `widgets` entry) or its cache
metrics silently stop appearing on `/actuator/prometheus`.

After deleting the files and applying the two edits above, run
`./gradlew build` and confirm it stays green.
