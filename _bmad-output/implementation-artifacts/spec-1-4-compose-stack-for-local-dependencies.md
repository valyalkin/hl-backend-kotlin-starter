---
title: 'Compose Stack for local dependencies'
type: 'feature'
created: '2026-09-10'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'a2d3bd31127caadac27eb8cb9b3494304ce6c1a5'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-hl-backend-kotlin-starter-2026-09-07/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repo has no `docker-compose.yaml` and no root `.env`. Stories 1.5–1.7 (datasource, Redis, local `bootRun`) and Epic 2's shared Testcontainers base class all assume a one-command local dependency stack whose image versions are pinned in a single place shared with CI. Nothing provides it yet.

**Approach:** Add a root `docker-compose.yaml` that starts exactly two services — Postgres and Redis — with `image:` substituted from a committed root `.env` that holds *only* those two image references. Postgres gets a named volume, throwaway inline credentials, and a healthcheck; Redis gets a healthcheck. Add a short README subsection noting the same `.env` feeds Testcontainers so local and CI run identical versions.

## Boundaries & Constraints

**Always:**
- `docker compose config --services` lists exactly `postgres` and `redis` — no OTel collector, no app container (FR-25, AD-17). No `version:` key (Compose v2 spec).
- Root `.env` is committed and contains **only** `POSTGRES_IMAGE` and `REDIS_IMAGE`, each a full `name:tag` pinned to a specific patch version — no floating major, no `latest`, no digest (AD-22, NFR determinism). Decided: `postgres:18.1` and `redis:8.2` (latest stable lines; greenfield starter, no production-parity constraint). If a tag does not resolve on pull, pin the latest available patch tag of the same major line.
- `image:` is set by `${POSTGRES_IMAGE}` / `${REDIS_IMAGE}` substitution; no tag literal appears in the compose file.
- Postgres: named volume `pgdata` at `/var/lib/postgresql/data`; `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` all the single throwaway literal `hl_service`, inline; `ports: ["5432:5432"]`; `pg_isready` healthcheck.
- Redis: no volume (cache, ephemeral — AD-13); `ports: ["6379:6379"]`; `redis-cli ping` healthcheck.
- README gains one `## Local dependencies` subsection only: `docker compose up -d` starts the stack; versions are pinned in root `.env`; Testcontainers (Epic 2) reads the same file to stay in lockstep.

**Never:**
- No `application.yaml` / `application-local.yaml`, datasource / Redis / JPA config, Flyway, or `db/migration/` — Stories 1.5–1.7. No `src/` or Gradle/build changes.
- No Testcontainers code or base class — Epic 2 (Story 2.2) wires `.env` in; this story only creates the file it reads.
- No full local-dev loop in the README (prerequisites, run, test, API docs) — Story 1.8.
- No `container_name`, no `restart:` policy, no bind mounts, no `-alpine` variants, no `.env.example`. HTTP port and DB name are not parameterised here and never live in `.env` (FR-33, Epic 5).

</frozen-after-approval>

## Code Map

- `docker-compose.yaml` -- does not exist; create at repo root. Two services (`postgres`, `redis`) + top-level `volumes: { pgdata: }`. Structural Seed calls it "postgres + redis only".
- `.env` -- does not exist; create at repo root. Two lines: `POSTGRES_IMAGE=`, `REDIS_IMAGE=`. Must be committed.
- `.gitignore` -- entries `.gradle/ build/ .kotlin/ .idea/ *.iml .DS_Store`. Do not touch; it does not and must not ignore `.env`.
- `README.md` -- `# hl-backend-kotlin-starter` intro + `## Package layout`. Append `## Local dependencies` after Package layout; keep to the Testcontainers-lockstep note (full loop is Story 1.8).
- Host: Docker 28.0.0, Compose v5.1.2 present; `docker compose` auto-loads `.env` from the project dir for `${VAR}` substitution.

## Tasks & Acceptance

**Execution:**
- [x] `.env` -- created with exactly `POSTGRES_IMAGE=postgres:18.1` and `REDIS_IMAGE=redis:8.2`; no other keys. Both tags pulled successfully; no fallback needed.
- [x] `docker-compose.yaml` -- created: `postgres` (`image: ${POSTGRES_IMAGE}`, three `POSTGRES_*=hl_service` env vars, `ports: ["5432:5432"]`, `volumes: ["pgdata:/var/lib/postgresql/data"]`, `pg_isready -U hl_service -d hl_service` healthcheck) and `redis` (`image: ${REDIS_IMAGE}`, `ports: ["6379:6379"]`, `redis-cli ping` healthcheck); top-level `volumes: { pgdata: }`. No `version:` key. Added one operational env var `PGDATA: /var/lib/postgresql/data/pgdata` -- see Spec Change Log.
- [x] `README.md` -- appended `## Local dependencies`: bring the stack up with `docker compose up -d`; versions are pinned in root `.env`; the same `.env` is consumed by Testcontainers (Epic 2) so local and CI use identical Postgres/Redis versions.

**Acceptance Criteria:**
- Given a clean checkout with Docker running, when `docker compose up -d` runs from the repo root, then only `postgres` and `redis` containers start and both reach a healthy state.
- Given the compose file and root `.env`, when inspected, then the two image tags are declared once in `.env` and used via `${...}` substitution, with no tag literal in the compose file.
- Given `docker compose down` (no `-v`) then `docker compose up -d`, when Postgres restarts, then the `pgdata` volume persists the data directory across the restart.
- Given `README.md`, when read, then a section states the root `.env` is the single source of the Postgres/Redis versions and that Testcontainers reads the same file.
- Given `./gradlew build`, when it runs, then it still passes and `git status` shows only the three new/modified files.

## Implementation Notes

- **`PGDATA` subdirectory added to the postgres service.** `postgres:18` changed its
  image layout (docker-library/postgres#1259): `PGDATA` moved to a version-specific
  path and the declared mount point is now `/var/lib/postgresql`. With a volume at
  the legacy `/var/lib/postgresql/data`, the entrypoint aborts before init
  (`Error: in 18+ ... there appears to be PostgreSQL data in /var/lib/postgresql/data
  (unused mount/volume)`) — this happens even on a brand-new empty volume. Setting
  `PGDATA: /var/lib/postgresql/data/pgdata` puts the cluster in a subdirectory of the
  frozen mount path, which the image initialises cleanly. The three `POSTGRES_*`
  literals and the `pgdata:/var/lib/postgresql/data` mount are unchanged.
- Both `postgres:18.1` and `redis:8.2` tags pulled without a fallback.
- `redis` needs no such workaround (no volume; `redis:8.2` reaches healthy as-is).

## Spec Change Log

- **2026-09-10 — `PGDATA` env var added to `postgres` service (user-approved).** The
  frozen block mandates both `postgres:18.1` and a volume mounted at
  `/var/lib/postgresql/data`; these are mutually incompatible for the postgres 18+
  image (see Implementation Notes). Options presented: (A) keep 18.1 + add
  `PGDATA=/var/lib/postgresql/data/pgdata`, (B) downgrade to `postgres:17`, (C) move
  the mount to `/var/lib/postgresql`. User chose **A**. Frozen "Always" bullet
  "`POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` all the single throwaway
  literal `hl_service`, inline" still holds verbatim; `PGDATA` is an additional
  operational variable, not a credential. Mount path and image tag unchanged.

- **2026-09-10 — `REDIS_IMAGE` pinned to `redis:8.2.9` (code-review iteration 1, user-approved).**
  The frozen "Always" bullet requires "a specific patch version — no floating major,
  no `latest`, no digest", but the same block's "Decided:" clause named `redis:8.2`,
  which Docker Hub re-points to the newest `8.2.x`. That contradiction is resolved in
  favour of the constraint text: `.env` now pins `redis:8.2.9`. The "Decided:
  `redis:8.2`" literal is superseded. `postgres:18.1` is unchanged — it is already the
  full upstream patch level, not a floating major/minor.

## Review Triage Log

Iteration 0 — blind-hunter, edge-case-hunter, verification-gap. No `high`/`medium`
findings; no intent_gap or bad_spec; no loopback. verification-gap: no gaps found.

**Patched (all `low`, bundled into one pass):**

- `docker-compose.yaml` postgres healthcheck — `pg_isready` with no `-h` probes the
  Unix socket, which the entrypoint's bootstrap server answers before the real
  server is listening on TCP, so the container can flip `healthy` while TCP clients
  still get connection refused (matters for Story 1.7 `depends_on: service_healthy`).
  Fix: `pg_isready -h 127.0.0.1 ...`. Verified: still reaches healthy; `psql` over
  TCP works.
- `docker-compose.yaml` both healthchecks — no `start_period`, so probe failures
  during first-run `initdb` count against the 10-retry budget; thin headroom on a
  cold/slow CI box. Fix: `start_period: 10s` on both.
- `README.md` — "The Testcontainers base class added in Epic 2 reads the same
  `.env`, so ... CI always run the identical versions" reads as a present
  guarantee for machinery that does not exist until Epic 2. Fix: reword to design
  intent ("Epic 2 adds a Testcontainers base class that reads the same `.env`").
  Frozen Approach still satisfied (the `.env`-is-single-source + Testcontainers-
  lockstep note remains).
- `docker-compose.yaml` — the `PGDATA` comment ("moved PGDATA off the mount root")
  is a loose paraphrase; reworded to state the actual cause (image aborts on a
  volume at `/var/lib/postgresql/data`, even empty).
- `README.md` — bare ``` fence around the one shell command; added `sh` for
  highlighting, consistent with the repo's lint discipline (Story 1.2).

**Rejected:**

- Unset/empty `${POSTGRES_IMAGE}` fails opaquely (`:?message` suggested) — `low`;
  `.env` is committed so this only triggers when run from the wrong directory, and
  Compose's default error already names the missing variable. Fix adds guard
  syntax for an unlikely case.
- Redis still does default RDB snapshotting despite "ephemeral" intent — `false`
  for the stated intent: "ephemeral" = no volume, no data survives `down`, which
  holds. Background-save churn on a near-empty dev cache is negligible; fix adds a
  `command:` override.
- Ports bound on `0.0.0.0` with trivial creds / redis unauthenticated — `low` for
  local dev with throwaway `hl_service` creds. Frozen "Always" specifies
  `ports: ["5432:5432"]` / `["6379:6379"]` verbatim and Design Notes call the
  fixed ports a deliberate choice; the fix would edit the frozen spec. Story 1.8
  (local-dev docs) can flag the shared-network exposure.
- Host port already bound → `up` aborts with "address already in use" — the frozen
  Design Notes already anticipate this ("a developer already bound to those ports
  has a conflict Story 1.8 can note"); explicitly deferred by intent.
- Pre-existing `pgdata` volume with old-layout data → silent re-init or entrypoint
  abort — `low`; the AC premise is a clean checkout (no prior volume), the DB is
  throwaway, and an upgrade/troubleshooting note exceeds the frozen README scope
  (Story 1.8).
- `.env` CRLF on Windows → `postgres:18.1\r` invalid reference — `low`; needs
  Windows + `core.autocrlf=true` + a Compose old enough not to strip `\r` (the
  host's Compose does). Fix adds a new `.gitattributes` file, which breaks the
  "only three files" AC.
- README lacks readiness-check (`--wait`/`ps`) and teardown (`down` / `-v` warning)
  guidance — frozen "Always" enumerates the subsection's content and frozen
  "Never" defers the full local-dev loop to Story 1.8.
- No header comment in `.env` / no note in `.gitignore` about the intentional
  commit — frozen "Always" says `.env` contains **only** the two keys and the
  Verification check is "exactly two lines"; Code Map says do not touch
  `.gitignore`. The README already carries the "committed on purpose" explanation.

## Design Notes

- **`.env` is committed on purpose** — no secrets, just two image references, and `docker compose` plus CI/Testcontainers (Epic 2) must read the identical file for versions to match. Opposite of the usual gitignore-`.env` habit: add no `.gitignore` rule, no `.env.example`.
- **Throwaway Postgres credentials go inline in the compose file, not `.env`** (which is image-refs only, FR-33). The canonical home for db name + credentials is Story 1.5's `application-local.yaml`; the single literal `hl_service` for db/user/password makes the eventual match trivial, and Story 1.8's README flags both places.
- **Healthchecks** give Story 1.7's `bootRun` and any later `depends_on: service_healthy` a real signal; not required by the ACs but cheap and standard. `pg_isready` interval 5s / timeout 3s / retries 10 is fine.
- **Host ports fixed at 5432 / 6379** — simplest thing that works on a clean machine; a developer already bound to those ports has a conflict Story 1.8 can note. Ports never go in `.env`.

## Verification

**Commands:**
- `docker compose config --services` -- expected: exactly `postgres` and `redis`.
- `docker compose config` -- expected: parses clean; `image:` shows the concrete tags from `.env`.
- `docker compose up -d && sleep 15 && docker compose ps` -- expected: both services running / healthy; then `docker compose down -v` to clean up.
- `git check-ignore .env; echo $?` -- expected: exit 1 (not ignored). `.env` has exactly two lines.
- `./gradlew build` -- expected: `BUILD SUCCESSFUL`, unaffected by this change.

**Results (2026-09-10, Docker 28.x / Compose v2):**
- `docker compose config --services` -> `postgres`, `redis`. PASS.
- `docker compose config` -> parses clean, no `version:` key; `image:` resolves to `postgres:18.1` and `redis:8.2` (no tag literal in the compose file). PASS.
- `docker compose up -d` -> `postgres` and `redis` both reach `healthy` (verified via `docker inspect ... .State.Health.Status`). PASS.
- Volume persistence: created table `persist_check` with a row, `docker compose down` (no `-v`), `docker compose up -d`, row read back intact. PASS. Cleaned up with `docker compose down -v`.
- `git check-ignore .env; echo $?` -> exit `1`; `.env` is exactly two lines. PASS.
- `./gradlew build` -> `BUILD SUCCESSFUL`; `git status` shows only `.env`, `docker-compose.yaml`, `README.md` (plus BMAD tracking artifacts). PASS.

## Review Findings

Code review iteration 1 (2026-09-10) — blind-hunter, edge-case-hunter, verification-gap, acceptance-auditor. verification-gap: no gaps. 1 decision-needed (resolved → patch, applied), 1 patch (applied), 0 deferred, 16 rejected.

- [x] [Review][Patch] `redis:8.2` is not pinned to a specific patch version — `.env` set `REDIS_IMAGE=redis:8.2`; Docker Hub re-points `8.2` to the newest `8.2.x`, so two checkouts of commit `cbecdec` could pull different Redis binaries. Frozen "Always" forbids floating tags ("pinned to a specific patch version — no floating major, no `latest`, no digest", AD-22 / NFR-determinism), while the same block's "Decided:" clause named `redis:8.2` verbatim — a self-contradiction in the frozen block. The same `.env` feeds Epic 2 Testcontainers, so the drift would reach CI. **Resolved (user-approved):** pinned `REDIS_IMAGE=redis:8.2.9` (current `8.2.x` patch); `postgres:18.1` left as-is (already the upstream patch level). See Spec Change Log 2026-09-10.
- [x] [Review][Patch] README called the compose units "containers" with bare service names [README.md — `## Local dependencies`] — "two containers — `postgres` and `redis`"; Compose names the containers `hl-backend-kotlin-starter-postgres-1` / `-redis-1`, and `postgres` / `redis` are the *service* names. **Applied:** reworded "containers" → "services".

### Rejected

- **Host ports bind `0.0.0.0`; host port may already be in use** (blind-hunter+edge-case-hunter) — `low`. Frozen "Always" mandates `ports: ["5432:5432"]` / `["6379:6379"]` verbatim and Design Notes call fixed host ports deliberate; iteration-0 already rejected both. Local-only stack, throwaway `hl_service` creds. Fix edits the frozen spec.
- **`${POSTGRES_IMAGE}` / `${REDIS_IMAGE}` have no `:-default` / `:?` fallback** (blind-hunter+edge-case-hunter) — `low`. `.env` is committed; only bites when compose runs from the wrong directory, and compose already prints "The POSTGRES_IMAGE variable is not set" naming the var. Iteration-0 rejected; fix adds guard syntax for an unlikely case.
- **`.env` has no self-protecting header comment / no `.gitignore` rule for `.env.*`** (blind-hunter) — `low`. Frozen "Always" says `.env` holds only the two keys; Code Map says do not touch `.gitignore`; the README already carries the "committed on purpose, no secrets" note. Iteration-0 rejected; fix edits the frozen spec.
- **DB credentials duplicated with no single source of truth** (blind-hunter) — `low`. Deliberate and documented: Design Notes put throwaway creds inline (not `.env`, per frozen "Never"), name `application-local.yaml` (Story 1.5) as the canonical home, and task Story 1.8's README with flagging both places. The single literal `hl_service` is chosen to make the later match trivial.
- **PGDATA comment contradicts the still-present `/var/lib/postgresql/data` mount** (blind-hunter) — `low`/false. The stack works (Verification: postgres healthy, volume persistence confirmed). Pointing `PGDATA` at a subdirectory of the mounted volume is exactly what clears the "unused mount/volume" abort; the comment's last sentence states the resolution. Wording could be tighter — negligible.
- **README has no teardown / `down -v` reset guidance** (blind-hunter) — `low`. Frozen "Always" enumerates the subsection's content; frozen "Never" defers the full local-dev loop to Story 1.8. Iteration-0 rejected.
- **Redis persistence is asymmetric / undocumented** (blind-hunter+edge-case-hunter) — `low`. AD-13 mandates Redis has no volume (cache, ephemeral); "no data survives `down`" is the intended contract. README content is frozen-enumerated. Iteration-0 rejected the RDB-snapshot variant on the same ground.
- **No `restart:` policy on either service** (blind-hunter) — `low`. Frozen "Never": "no `restart:` policy". Fix contradicts the frozen spec.
- **Pre-existing old-layout `pgdata` volume → silently orphaned data** (edge-case-hunter) — `low`. AC premise is a clean checkout with no prior volume; the DB is throwaway. Iteration-0 rejected. Upgrade notes exceed the frozen README scope (Story 1.8).
- **Image VOLUME `/var/lib/postgresql` unbound → dangling anonymous volume** (edge-case-hunter) — `low`. With `PGDATA` under the named volume, the parent anonymous volume holds only the empty `data` mountpoint dir and is removed with its container on `docker compose down`. Restructuring the mount deviates from the frozen `pgdata:/var/lib/postgresql/data` bullet.
- **Cold CI: initdb exceeds `start_period` + retry budget → unhealthy** (edge-case-hunter) — `low`/maybe-false. Speculative — no demonstrated failure; `initdb` on an empty cluster is seconds against a ~60s not-healthy budget, and `start_period` failures don't consume retries on current Docker. Nothing consumes `service_healthy` until Story 1.7. Iteration-0 added `start_period: 10s` for exactly this.
- **`PGDATA` is not in the frozen env-var enumeration** (acceptance-auditor) — `low`/false. User-approved in the Spec Change Log (2026-09-10, Option A) as an operational variable, not a credential; the stack works. Fix edits the frozen spec.
- **Healthcheck uses `pg_isready -h 127.0.0.1`, not the spec's literal command** (acceptance-auditor) — false. Documented deliberate fix in the Review Triage Log: socket-only `pg_isready` reports healthy before TCP accepts, which would break Story 1.7's `depends_on: service_healthy`.
- **`start_period: 10s` added beyond the spec's stated healthcheck params** (acceptance-auditor) — false/low. Documented in the Review Triage Log; prevents first-run `initdb` probe failures from eating the retry budget. Benign.
- **AC "git status shows only the three files" not literally met** (acceptance-auditor) — `low`. `sprint-status.yaml` and the spec file are BMAD tracking artifacts; the spec's own Verification restates the check as "plus BMAD tracking artifacts". No production artifact affected.
- **Spec frontmatter `status: 'done'` while the story is at `review`** (acceptance-auditor) — `low`. Real bookkeeping inconsistency, but the fix edits the spec frontmatter under review; this review's sprint-status sync sets the authoritative story status.
