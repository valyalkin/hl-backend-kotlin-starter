# Epic 2 Context: A working reference feature to copy — `widgets` end to end

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic delivers the complete `widgets` CRUD resource — the canonical pattern every Consumer Service copies when adding a domain feature. By the end of the epic, a REST client can create, read, update, delete, and list widgets over `/api/v1/widgets`. The implementation demonstrates the full stack: controller → service → Postgres (JPA + Flyway) → Redis (`@Cacheable`/`@CacheEvict`), with distinct domain and JPA entity types, application-generated UUIDs, a shared offset-pagination envelope, RFC 7807 error bodies from a single `GlobalExceptionHandler`, Bean Validation, served OpenAPI JSON in every profile, and Swagger UI only under `local`. Unit and Integration tests cover both levels. The slice is documented as the pattern to copy and removable — deleting all `Widget*` files plus the migration must leave `./gradlew build` green.

## Stories

- Story 2.1: Widget domain model, JPA entity, and first migration
- Story 2.2: Shared Testcontainers base class and a persistence Integration Test
- Story 2.3: RFC 7807 error contract and the Global Exception Handler
- Story 2.4: Widget service operations
- Story 2.5: Widget REST endpoints and DTOs
- Story 2.6: Request validation with field-level errors
- Story 2.7: Redis cache-aside on read-by-id
- Story 2.8: Cache behavior Integration Test
- Story 2.9: Example Slice coverage at both levels
- Story 2.10: Served OpenAPI and `local`-only Swagger UI
- Story 2.11: "Add a REST resource" guide and Example Slice removal

## Requirements & Constraints

- **Full CRUD under `/api/v1/widgets`.** `POST` collection → 201 + `Location` header + `WidgetResponse`; `GET /{id}` → 200; `GET` collection (paged) → 200 with the shared envelope; `PUT /{id}` → full replacement; `DELETE /{id}` → 204. Plural, lowercase path segment.
- **Offset pagination with a shared envelope.** Collection `GET` accepts `?page=` (0-based) and `?size=` (configured default). Response envelope is `{ items, page, size, totalElements, totalPages }`, defined once in `dto/PageResponse.kt` and reused by every future resource. Spring Data `Pageable`/`Page` used directly end to end — no hand-rolled pagination types.
- **Standard error body everywhere.** All 4xx and 5xx responses are `application/problem+json` (RFC 7807). Fields: `type` = `about:blank`, `instance` = request path, `detail` = exception's own message, `code` (fixed literal per exception type), `traceId`, `details` (omitted when empty), `errors` (validation only). Exactly one component produces error bodies — `GlobalExceptionHandler` in `controller/`.
- **Three exception types, no catalogue.** `error/` holds a sealed `AppException` base plus `BusinessException` (→ 400), `NotFoundException` (→ 404), `SystemException` (→ 500), each with one constructor: `message: String, details: Map<String, Any?> = emptyMap()`. No `ErrorCode` enum. HTTP status is fixed by type, not chosen per call-site. `code` literals: `BUSINESS_ERROR`, `NOT_FOUND`, `SYSTEM_ERROR`, `UNEXPECTED_ERROR` (unanticipated exceptions). The `SystemException` message and details are echoed to the client; the exception is also logged at error level server-side with the trace id.
- **Bean Validation → 400 in the same schema.** `WidgetRequest` carries at least one Bean Validation annotation. Constraint violations are rendered by `GlobalExceptionHandler` as an `errors` array of `{ field, code, message }` inside the standard Problem Detail body — not a separate error shape.
- **Domain and JPA entity are distinct types.** `Widget` in `model/` is an immutable Kotlin class, never annotated `@Entity`, never serialized directly, and never appears in a controller signature. `WidgetEntity` in `repository/` is the `@Entity`. Mapping is explicit hand-written code; no mapping framework on the classpath. DTOs (`WidgetRequest` / `WidgetResponse`) in `dto/` are the wire types.
- **Application-generated UUIDs.** IDs are `java.util.UUID.randomUUID()`, generated in the domain before persistence. Both the domain class and the `@Entity` carry the UUID; the database never generates identifiers. Postgres column type `uuid`.
- **Flyway owns the schema.** `V1__create_widgets.sql` creates the `widgets` table with a `uuid` primary key and `timestamptz` columns. `spring.jpa.hibernate.ddl-auto = validate` (set in Epic 1) passes against the migrated schema.
- **Redis is fail-fast, no fallback.** Read-by-id is `@Cacheable`; update and delete are `@CacheEvict`. No manual cache orchestration. No `CacheErrorHandler` bean — Spring's default fail-fast propagates Redis failures as `SystemException` → 500. Redis down → `GET /{id}` returns 500 and readiness reports `DOWN`; no Postgres-served fallback exists.
- **Cache TTL is configurable.** TTL is a configuration value with a documented default of 10 minutes.
- **OpenAPI JSON every profile; Swagger UI only under `local`.** `/v3/api-docs` reflects the `widgets` endpoints (and any later resource) with no extra wiring. `springdoc.swagger-ui.enabled` is `false` except on the `local` profile. Library: `springdoc-openapi-starter-webmvc-ui` 3.1.1.
- **Hermetic tests (NFR-1).** No test touches external infrastructure. Integration Tests extend the shared base class; isolation is per-test data cleanup, not per-test containers.
- **Slice is zero-Plumbing.** Adding the `widgets` slice changes no build files, no configuration, no CI workflow, no observability wiring, no container config. Deleting it (code + tests + `V1__create_widgets.sql`) leaves `./gradlew build` green.
- **Dates:** `java.time` types, UTC, ISO-8601 on the wire, `timestamptz` in Postgres.

## Technical Decisions

- **Layering pattern (AD-1, AD-4).** Seven concern packages; no per-resource subpackage. `controller/` → `service/` → `repository/` by convention only — no build-time enforcement. Each concern package holds the matching class for every resource.
- **Shared Testcontainers base class (AD-21).** `src/test/kotlin/com/hl/service/support/IntegrationTestBase.kt` owns `static` Postgres and Redis containers annotated `@ServiceConnection`, started once per JVM and shared across all Integration Tests. Tests carry `@Tag("integration")` and extend this class; they add nothing infrastructural. Unit Tests use hand-written in-memory fakes — no mocking framework dependency.
- **One `@RestControllerAdvice` (AD-11).** `GlobalExceptionHandler` in `controller/` is the sole producer of error bodies. It assigns the `code` literal per exception type and handles: `BusinessException`, `NotFoundException`, `SystemException`, Bean Validation failures, and any unrecognized exception (→ `UNEXPECTED_ERROR` / 500).
- **`@Cacheable`/`@CacheEvict` directly on service methods (AD-13).** Framework-managed cache-aside only — no hand-written orchestration, no `CacheErrorHandler` bean.
- **`@Transactional` on mutating service methods** by convention; `WidgetService` is `@Service`.
- **`WidgetService` returns domain types only** — never entities, never DTOs. Controllers map domain types to DTOs; the service orchestrates the domain and the repository.
- **`JpaRepository<WidgetEntity, UUID>`** is the repository interface; no custom query methods beyond what Spring Data infers, unless list paging requires an explicit `findAll(Pageable)`.
- **`PageResponse<T>` in `dto/`** is the generic pagination wrapper; built once, reused for every future pageable resource.

## UX & Interaction Patterns

Not applicable — this is a headless backend service with no UI.

## Cross-Story Dependencies

- **Story 2.1 (domain model + migration) precedes 2.2, 2.4, 2.5, 2.7, 2.8, 2.9.** The `Widget`, `WidgetEntity`, and `V1__create_widgets.sql` are the data foundation every later story builds on.
- **Story 2.2 (shared Testcontainers base class) precedes 2.8 and 2.9.** Integration Tests in those stories extend `IntegrationTestBase`.
- **Story 2.3 (error contract + `GlobalExceptionHandler`) precedes 2.5 and 2.6.** The controller's 404 response (Story 2.5) and 400 validation response (Story 2.6) both delegate to the handler introduced here.
- **Story 2.4 (service layer) precedes 2.5, 2.7, 2.8, and 2.9.** All REST, cache, and higher-level test stories require the service to exist.
- **Story 2.5 (REST controller + DTOs) precedes 2.6, 2.9, 2.10, and 2.11.** Validation (2.6), HTTP-level tests (2.9), OpenAPI reflection (2.10), and the "add a resource" guide (2.11) all depend on the controller being wired.
- **Story 2.7 (cache annotations) precedes 2.8.** The cache Integration Test targets the annotations introduced in 2.7.
- **Stories 2.9, 2.10, and 2.11 depend on the rest of the slice being complete** and should land last.
- **Upstream (Epic 1):** this epic consumes the concern packages, Flyway wiring, `ddl-auto=validate`, Compose/`.env` convention, and Redis readiness wiring established there.
- **Downstream:** Epic 3 extends the actuator/observability surface; Epic 5 documents how to add a REST resource (ties to Story 2.11) and wires the Auth Seam over the endpoints introduced here.
