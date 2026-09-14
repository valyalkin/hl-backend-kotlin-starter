# Epic 3 Context: A service that's observable and well-behaved in production

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Make the running service observable and well-behaved without any code path that exists only in production: Prometheus metrics, OTLP distributed tracing (a clean no-op when unconfigured), structured JSON logs correlated by trace id, and graceful shutdown on SIGTERM. Everything is driven by configuration, not code, so behavior is identical across `local`, CI, and production save for endpoint configuration and log formatting — this is what keeps the observability surface actually tested rather than a production-only leap of faith.

## Stories

- Story 3.1: Prometheus metrics endpoint
- Story 3.2: OTLP trace export, no-op when unconfigured
- Story 3.3: Structured JSON logging with trace correlation
- Story 3.4: Graceful shutdown on SIGTERM

## Requirements & Constraints

- Actuator exposes exactly three endpoints — `health`, `info`, `prometheus` — and nothing else, all on the main port (no separate management port).
- The Prometheus endpoint returns Prometheus-format text covering JVM, HTTP server, datasource, and cache metrics, scrapeable with no extra application wiring.
- Tracing exports over OTLP (Micrometer Tracing over the OTel bridge). When no OTLP endpoint is configured (the default, including local and CI), the exporter is a genuine no-op — no retry-against-localhost errors in logs. When an endpoint is configured, spans for an inbound request and its outbound DB/cache calls are exported. Sampling probability is a configuration value with a documented default.
- Logs are single-line JSON on stdout via Spring Boot's built-in structured logging (`logging.structured.format.console`) — no logging encoder dependency, no logback XML file. Each line carries at minimum timestamp, level, logger, and message; lines emitted within a request also carry the trace id and span id. The `local` profile keeps human-readable console output instead. No secondary log sink or file appender.
- Graceful shutdown is enabled (`server.shutdown=graceful`). On SIGTERM the service stops accepting new requests, lets in-flight requests finish within a bounded phase timeout, then exits. `spring.lifecycle.timeout-per-shutdown-phase` has a documented default of 30s and is environment-overridable.
- Startup-to-readiness target: under ~10s on a typical CI/runtime container (measured via the documented local check used for the Kubernetes runtime contract in Epic 4).
- Observability parity is a hard constraint: metrics, traces, and logs must behave the same across `local`, CI, and production, differing only in endpoint configuration and log formatting — never in which code path runs.
- Heap sizing is left to the buildpack's container-aware calculator, not fixed here; only the configuration knob needs documenting (covered by Epic 5 README work, not this epic's code).

## Technical Decisions

- Observability is configuration, never code (AD-17): every behavior above is toggled by Spring configuration properties, not conditional code paths, so nothing is production-only and therefore untested.
- No encoder dependency and no logback XML — rely solely on Spring Boot 4's built-in structured JSON logging support.
- Readiness health group already includes datasource + Redis (established in Epic 1); this epic does not change health group membership, only adds/confirms the actuator endpoint exposure list.
- Actuator stays on the main port in v1 (a separate management port is explicitly deferred, not part of this epic).
- No local OpenTelemetry collector or trace viewer is part of this epic or the Compose Stack; OTLP export is exercised only insofar as configuration allows, not via a bundled local backend.

## Cross-Story Dependencies

- All four stories build on the actuator/health/config foundation already established in Epic 1 (Story 1.3 bootable app with actuator, Story 1.6 readiness gating) and are independent of each other otherwise.
- The startup-to-readiness (<10s) target set here is verified end-to-end later via Epic 4's documented local Kubernetes-contract check (`docker run` + curl probes + SIGTERM timing), which also exercises this epic's graceful-shutdown behavior (Story 3.4) against the built image.
