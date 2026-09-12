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

Not available yet — the OpenAPI JSON and `local`-only Swagger UI ship with the
first REST resource in Epic 2. There is no local trace viewing in v1 either;
trace export wiring (Epic 3) is configuration-only locally, with nothing to
view without an external collector.
