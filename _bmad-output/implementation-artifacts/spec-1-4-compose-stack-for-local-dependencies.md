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
