---
title: 'Image built via bootBuildImage'
type: 'feature'
created: '2026-09-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '400d298b4c3ffac30547d7cba94a14499d1acbca'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repo has no way to produce a runnable OCI image yet -- every Consumer Service that adopts this starter needs the same well-behaved, non-root, environment-configured image with nothing hand-maintained to drift.

**Approach:** Configure Gradle's `bootBuildImage` task (AD-19) with the pinned `paketobuildpacks/builder-noble-java-tiny` builder and `BP_JVM_CDS_ENABLED`/AOT-cache disabled (open Paketo defect on Java 25 + Boot 4) -- no Dockerfile, no application code. Document the resulting image in the README: how to build it, that it runs non-root from environment configuration alone, and the buildpack's container-aware heap calculator plus its `JAVA_TOOL_OPTIONS`/`BPL_JVM_*` override knob.

## Boundaries & Constraints

**Always:**
- `bootBuildImage` (Spring Boot's Gradle plugin task) is the only path to an image -- no Dockerfile anywhere in the repo, now or as a fallback.
- The builder is `paketobuildpacks/builder-noble-java-tiny` pinned to an explicit, non-floating tag.
- `BP_JVM_CDS_ENABLED` and the JVM AOT cache are both explicitly disabled via `bootBuildImage`'s `environment` map.
- Heap sizing stays the buildpack's container-aware calculator -- no fixed numeric memory/CPU ceiling anywhere in this repo.

**Never:**
- `bootBuildImage` is not wired into `check`/`build`/`test` -- it stays a standalone task, consistent with Spring Boot's own default and the ~10-minute PR budget (Story 4.2 is the separate, non-image-building CI gate).
- No registry-specific image name/tag here -- publishing to `ghcr.io` with a SHA/`latest` tag is Story 4.3's job, not this story's.
- No Kubernetes/cluster verification here -- the documented local runtime-contract check (probes, SIGTERM timing) against the built image is Story 4.4's job.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Repo inspected | Any state | No `Dockerfile` exists anywhere in the repo; `bootBuildImage` is configured with the pinned builder | N/A |
| `./gradlew bootBuildImage` | Run locally | Completes and produces a runnable local image; the image runs as a non-root user by default | N/A |
| Built image run with env-only config | `docker run` supplying `SPRING_DATASOURCE_*`/`SPRING_DATA_REDIS_*` etc. pointed at reachable Postgres/Redis, no other config | Image starts and answers `/actuator/health/liveness` and `/actuator/health/readiness` as `UP` | N/A |
| README inspected for heap sizing | Any state | Documents the buildpack's container-aware calculator and the `JAVA_TOOL_OPTIONS`/`BPL_JVM_*` override knob, no fixed numeric default | N/A |

</frozen-after-approval>

## Code Map

- `build.gradle.kts` -- add a `tasks.named<BootBuildImage>("bootBuildImage") { ... }` block (import `org.springframework.boot.gradle.tasks.bundling.BootBuildImage`, same import-and-configure pattern already used for `tasks.named<BootRun>("bootRun")` at the bottom of this file) setting `builder` to the pinned tag and `environment` to disable CDS/AOT.
- `README.md` (after `### Remove the Example Slice`, end of file) -- add a new top-level `## Container image` section: how to build (`./gradlew bootBuildImage`), the builder pin and CDS/AOT-off rationale (one line, mirrors the terse style of `### Shutdown`/`### Logs`), non-root + env-only confirmation, and the heap-sizing knob.
- `.env` / `docker-compose.yaml` -- reference only, for the manual verification step (run the built image against the already-running Compose stack's Postgres/Redis to prove env-only config reaches liveness/readiness).
- No `Dockerfile` exists in the repo today (confirmed) -- nothing to remove; the "never introduce one" boundary is preventive, not corrective.

## Tasks & Acceptance

**Execution:**
- [x] `build.gradle.kts` -- configure `bootBuildImage`'s `builder` (pinned `paketobuildpacks/builder-noble-java-tiny` tag) and `environment` (`BP_JVM_CDS_ENABLED=false` plus whatever env var(s) the Paketo Java buildpack uses to disable the JDK 25 AOT cache -- confirm exact key(s) empirically, see Design Notes) -- produces a reproducible, non-floating image build with the known-defective caches off.
- [x] `README.md` -- add `## Container image` documenting the build command, the pin/CDS/AOT rationale, non-root + env-only operation, and the heap knob -- makes the image buildable and understandable with no undocumented step (mirrors FR-27's "no undocumented steps" bar already applied to local dev).

**Acceptance Criteria:**
- Given the repo, when inspected, then there is no `Dockerfile`, and `bootBuildImage` is configured with the pinned builder tag and `BP_JVM_CDS_ENABLED`/AOT cache off.
- Given `./gradlew bootBuildImage`, when it completes, then it produces a runnable local image that runs as a non-root user by default.
- Given the built image, when run with configuration supplied entirely from environment variables (pointed at the Compose stack's Postgres/Redis), then it starts and answers its liveness and readiness health endpoints.
- Given heap sizing, when the README is inspected, then it documents the buildpack's container-aware calculator and the `JAVA_TOOL_OPTIONS`/`BPL_JVM_*` knob rather than a fixed number.

## Implementation Notes

Added a `tasks.named<BootBuildImage>("bootBuildImage") { ... }` block to
`build.gradle.kts`, importing `org.springframework.boot.gradle.tasks.bundling.BootBuildImage`,
mirroring the existing `tasks.named<BootRun>("bootRun")` pattern at the
bottom of the file. No Dockerfile was added (none existed before, confirmed
via `ls Dockerfile 2>/dev/null; echo $?` returning `1`).

Both Design Notes questions were confirmed empirically rather than guessed:

1. **Builder tag.** `docker manifest inspect paketobuildpacks/builder-noble-java-tiny:latest`
   resolved to digest `sha256:7688d7b91bd...4127673`; cross-referencing Docker
   Hub's tags API for the same repository
   (`https://hub.docker.com/v2/repositories/paketobuildpacks/builder-noble-java-tiny/tags`)
   showed that digest is also tagged `0.0.190` (a real, monotonically
   increasing release tag, not `latest`). Pinned `builder` to
   `paketobuildpacks/builder-noble-java-tiny:0.0.190`.
2. **AOT cache env var(s).** `paketo-buildpacks/spring-boot`'s own README
   (fetched from GitHub) documents that `BP_JVM_CDS_ENABLED` is now
   deprecated in favor of `BP_JVM_AOTCACHE_ENABLED`, both controlling the
   same underlying CDS/AOT-cache training run (`application.jsa`) that the
   Spring Boot buildpack performs at build time; both already default to
   `false` upstream. A real `./gradlew bootBuildImage` run's log output for
   "Paketo Buildpack for Spring Boot 5.37.0" confirmed both keys by name in
   its "Build Configuration" listing (`$BP_JVM_CDS_ENABLED` /
   `$BP_JVM_AOTCACHE_ENABLED`, both showing `false`, matching the values set
   in `environment`). Set both explicitly in `build.gradle.kts` rather than
   relying on the upstream default, so the build stays self-documenting and
   immune to a future default change.

Verified all four Acceptance Criteria end-to-end:
- `ls Dockerfile 2>/dev/null; echo $?` -- exit code `1`, no Dockerfile.
- `./gradlew bootBuildImage` -- `BUILD SUCCESSFUL`, produced
  `docker.io/library/hl-backend-kotlin-starter:0.0.1-SNAPSHOT`; log output
  confirms the pinned builder and both CDS/AOT-cache keys off.
- `docker inspect ... --format '{{.Config.User}}'` -- `1002:1001` (non-root)
  both on the built image's metadata and via `docker top` on a running
  container (`UID 1002`).
- Full runtime check: `docker compose up -d`, then `docker run` the built
  image on the Compose network with only `SPRING_DATASOURCE_URL`/
  `SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD`/
  `SPRING_DATA_REDIS_HOST` set -- the container started, applied the Flyway
  migration, and both `curl http://localhost:8080/actuator/health/liveness`
  and `.../health/readiness` returned `{"status":"UP"}`.
- `./gradlew build` -- green, and `bootBuildImage` does not appear in its
  task graph, confirming it stays a standalone task outside `check`/`test`.
- README's `## Container image` section documents the buildpack's
  container-aware memory calculator and the `JAVA_TOOL_OPTIONS`/`BPL_JVM_*`
  override knobs, with no fixed numeric heap value anywhere in the section.

No automated Gradle test was added, consistent with this story's own
Design Notes rationale (image build/run is Docker-dependent and slow; the
ACs above were verified with the manual commands the spec's Verification
section already prescribes).

Nothing left incomplete. One residual risk: the `0.0.190` builder tag will
age -- Paketo publishes a new builder release roughly weekly, so this pin
will eventually need a manual bump (by design: that's the tradeoff for
reproducible, non-floating builds, not a defect).

## Spec Change Log

## Review Triage Log

- **medium / patch** — The README's `docker run` example uses `host.docker.internal` with no `--add-host=host.docker.internal:host-gateway` flag (edge-case-hunter). Verified: Docker Desktop (macOS/Windows) resolves that hostname automatically, but native Linux Docker Engine does not by default (requires the explicit `--add-host` flag, Docker 20.10+) -- the documented command as written fails to reach Postgres/Redis on a Linux dev machine. Added the flag to the example (harmless no-op where the hostname already resolves).
- **low / patch** — `### Prerequisites` says Docker is needed only to "run the local Postgres and Redis" (blind-hunter); `bootBuildImage` also needs a running Docker daemon, and this isn't called out anywhere. Extended the bullet.
- **low / patch** — The new heap-sizing paragraph describes the `JAVA_TOOL_OPTIONS`/`BPL_JVM_*` override mechanism with no runnable example, unlike the section's own `docker run` block and every sibling README subsection (blind-hunter). Added a `-e BPL_JVM_HEAD_ROOM=...` line to the existing example.
- **false / reject** — No mention of how the image name/tag changes if the starter is renamed for a Consumer Service (blind-hunter). Out of scope: AD-16 and Epic 5's Story 5.1 ("The four parameters, each with one documented home") already own documenting image name as one of the Four Parameters -- not this story's, which explicitly scopes out "registry-specific image name/tag" in its own frozen Boundaries.
- **false / reject** — No mention of amd64/arm64 build-architecture mismatch between a local Apple Silicon build and CI's `ubuntu-latest` publish (blind-hunter). Refuted: nothing in the diff claims the locally-built image is what gets deployed -- the new README section only documents building/running locally for a smoke check. Cross-platform CI build behavior is Story 4.3's (not-yet-built) publish job, not this story's.
- **low / patch** — The "open defect" justifying `BP_JVM_CDS_ENABLED`/`BP_JVM_AOTCACHE_ENABLED` being off has no link anywhere (blind-hunter) -- a future maintainer can't tell when it's safe to revisit. Found and verified the actual issue via web search: [paketo-buildpacks/spring-boot#581](https://github.com/paketo-buildpacks/spring-boot/issues/581) ("unable to contribute spring-performance layer" after successful AOT Cache creation, Java 25 + Spring Boot) matches this repo's exact symptom. Added the link to both the README and the `build.gradle.kts` comment.
- **low / reject** — No automated check guards the pinned `builder`/`environment` values against accidental regression, e.g. someone reverting the pin to `latest` (blind-hunter). Real gap, but the fix requires introducing an entirely new testing pattern this repo has never used (Gradle TestKit/GradleRunner, confirmed absent repo-wide by the verification-gap reviewer) for two string values -- more than a direct correction, and a reverted pin is also a one-line, easily-caught code-review diff, not a silent runtime hazard. Rejected per the low-finding policy (unlikely in practice + non-trivial fix).
- **low / patch** — The `build.gradle.kts` comment asserting the builder tag's digest was cross-checked has no date, and the pin is explicitly expected to age (Implementation Notes' own "residual risk" note) (blind-hunter). Added the verification date to the comment.
- **low / patch** — No note that `bootBuildImage`'s first run pulls builder/run images from Docker Hub and can take noticeably longer, unlike this README's own established precedent in `### Run tests` for Testcontainers' first-run pulls (blind-hunter). Added one sentence.

## Design Notes

Two facts need empirical confirmation during implementation (mirrors Stories 3.1-3.4's precedent of confirming uncertain framework/tool specifics against real output rather than guessing):
1. The current pinned, non-floating tag for `paketobuildpacks/builder-noble-java-tiny` (e.g. via `docker pull`/`docker manifest inspect` against the registry, or the builder's own release notes) -- must not be `latest` or another floating reference.
2. The exact `bootBuildImage` `environment` key(s) that disable the JDK 25 "AOT cache" alongside `BP_JVM_CDS_ENABLED=false` -- confirm against a real `./gradlew bootBuildImage` run's output/logs (e.g. Paketo Java buildpack detect/build log lines naming the relevant `BP_*` variable) rather than assuming a name.

No automated Gradle test exercises image build/run -- packaging and running a real OCI image is comparatively slow and Docker-dependent in a way the existing Testcontainers-based Integration Test suite deliberately isn't (AD-21); this story's ACs are verified by the manual commands below instead, consistent with how Story 4.4 documents its own local runtime-contract check as a manual procedure rather than an automated test.

## Verification

**Commands:**
- `./gradlew build` -- expected: unaffected by this story (bootBuildImage is not wired into `check`); full suite still green.
- `ls Dockerfile 2>/dev/null; echo $?` -- expected: no match, confirms no Dockerfile exists.
- `./gradlew bootBuildImage` -- expected: completes successfully, produces a local image.
- `docker compose up -d` (Compose stack running), then `docker run --rm -p 8080:8080 -e SPRING_DATASOURCE_URL=... -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... -e SPRING_DATA_REDIS_HOST=... <built-image-tag>`, then `curl http://localhost:8080/actuator/health/liveness` and `.../health/readiness` -- expected: both report `{"status":"UP"}`.
- Inside the running container (`docker exec ... whoami` or `docker inspect` the running container's user) -- expected: a non-root user, not `root`/uid 0.
