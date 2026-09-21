# Epic 4 Context: A published image and a green CI pipeline

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic turns the working, observable service from Epics 1–3 into a deployable artifact with an automated quality gate: `./gradlew bootBuildImage` produces a non-root OCI image that meets the Kubernetes runtime contract (framework-default probe paths, clean SIGTERM, environment-only configuration), and GitHub Actions fully builds and tests every pull request before any code can merge, then publishes the image on every green push to `main`. It matters because the image is the operational contract the downstream charts/deployment repo depends on, and because a merge-blocking CI gate is what lets Consumer Services trust that "green build" means "safe to run."

## Stories

- Story 4.1: Image built via `bootBuildImage`
- Story 4.2: CI builds and tests every pull request
- Story 4.3: CI publishes the image on push to `main`
- Story 4.4: Kubernetes runtime contract verified locally

## Requirements & Constraints

- The image must be produced with no Dockerfile in the repo — buildpacks only (`bootBuildImage`), using a builder pinned to an explicit (never floating) tag.
- `BP_JVM_CDS_ENABLED` and the AOT cache stay off in v1 (a known Paketo defect on Java 25 + Boot 4).
- The image runs as a non-root user, takes all configuration from environment variables, and answers its liveness/readiness endpoints with no reconfiguration — same probe paths as the running app already established elsewhere in the service.
- Heap sizing is left to the buildpack's container-aware calculator; document the override knob (`JAVA_TOOL_OPTIONS` / `BPL_JVM_*`) rather than fixing a numeric memory/CPU ceiling.
- Every pull request must run the full build (format-check, Unit Tests, Integration Tests) on `ubuntu-latest`. Integration Tests use Testcontainers exclusively — no external database/cache and no service containers declared in the workflow itself. A failing check blocks merge.
- Only pushes to `main`, after a green build, build and publish the image. No image is published for other branches or failing builds.
- The published image goes to a **private** `ghcr.io` package, tagged with the git short SHA, with `latest` moved to point at it — no semver tags in v1.
- Publishing authenticates with the workflow's built-in `GITHUB_TOKEN` under `permissions: packages: write` — no stored/rotated registry credential.
- Gradle dependency and build caches must be restored between CI runs so a no-op-change PR pipeline completes within roughly a 10-minute budget; don't let hermeticity or coverage additions blow this budget.
- The Kubernetes runtime contract (probe paths, SIGTERM behavior, env-only config) must be verifiable via a documented local check (`docker run` + curl the probes + time a SIGTERM) — no cluster required to validate it. Startup-to-readiness should land under roughly 10 seconds on a modest container.
- Security posture: non-root container, no secrets baked into the image or repo — this is a hard constraint carried from earlier epics, not new scope here.

## Technical Decisions

- **No Dockerfile, ever** — `bootBuildImage` is the only path to an image; builder is `paketobuildpacks/builder-noble-java-tiny` at a pinned tag.
- **CDS/AOT off in v1** — do not attempt to re-enable pending the upstream Paketo fix; revisit later, not in this epic.
- **CI topology**: GitHub Actions on `ubuntu-latest`; the workflow file is `.github/workflows/ci.yaml` (already scaffolded structurally in Epic 1's Structural Seed). PR runs and the `main`-only publish job live in the same workflow logic, gated on branch + build success.
- **No workflow-level service containers** — Postgres/Redis for Integration Tests come exclusively from Testcontainers inside the JVM process, consistent with the shared `IntegrationTestBase` pattern from Epic 2 (AD-21); do not add `services:` blocks to the workflow.
- **Registry auth**: built-in `GITHUB_TOKEN` scoped via `permissions: packages: write`; never introduce a stored PAT or registry secret for this.
- **Tag scheme**: git short SHA + `latest` only — no semver, no per-branch tags.
- **Caching**: Gradle dependency and build caches restored between CI runs (standard GitHub Actions cache action or equivalent) — this is what keeps the ~10-minute PR budget achievable.
- **Deployment boundary**: this epic ships the image and the CI pipeline only. Cluster wiring — manifests, Helm values, secrets, ingress, `imagePullSecret` — belongs to the separate charts repository and is explicitly out of scope here; the image's contract (probe paths, SIGTERM, env config) is the interface between the two repos, so changing it is a breaking change downstream.

## Cross-Story Dependencies

- Story 4.1 (image) and Story 4.2 (PR CI) both depend on the bootable application, health endpoints, and format/lint tasks already established in Epic 1 (Stories 1.1–1.3) — this epic wires them into buildpacks and a workflow rather than introducing new app behavior.
- Story 4.2's Integration Tests reuse the shared Testcontainers base class from Epic 2 (Story 2.2 / AD-21); no new test infrastructure is introduced in this epic.
- Story 4.3 (publish on `main`) is strictly gated on Story 4.2's green build — the publish job must not run, or must no-op, when the PR/build workflow it depends on fails.
- Story 4.4 (runtime-contract verification) depends on Story 4.1's built image and exercises readiness/liveness behavior from Epic 1 (Story 1.6) and graceful shutdown from Epic 3 (Story 3.4) — it verifies existing contracts against the packaged image rather than defining new ones.
- The image and CI pipeline produced here are the artifact Epic 5's "clone-to-service" walkthrough (Story 5.3) exercises end to end, and the interface the separate charts repository consumes downstream.
