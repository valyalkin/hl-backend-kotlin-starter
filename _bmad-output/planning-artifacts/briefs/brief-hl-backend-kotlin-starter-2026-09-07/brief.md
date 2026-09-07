---
title: "Product Brief: Spring Boot Kotlin Starter Service"
status: ready
created: 2026-09-07
updated: 2026-09-07
---

# Product Brief: Spring Boot Kotlin Starter Service

## Executive Summary

A template repository for Spring Boot + Kotlin backend services: a complete, deployable baseline with every piece of shared plumbing already solved and organized in clean-architecture layers — Postgres via JPA/Hibernate with Flyway migrations, Redis, a local Docker Compose stack, Testcontainers integration tests that run in CI with no external infrastructure, a Kubernetes-ready runtime with health probes plus Micrometer/Prometheus and OpenTelemetry, and a CI pipeline that builds, tests, and publishes the image. One example vertical slice (REST → Postgres → Redis) ships as the pattern to copy.

Alex is starting a stack of Kotlin backend services and has assembled this plumbing by hand before. Increasingly, both the setup and the feature work on top of it will be done by AI coding agents rather than only by hand. That work needs a predictable, well-tested foundation: one obvious home for each kind of code, consistent conventions across every service, and a test suite that fails loudly when something is wired wrong — so services do not drift apart and agents can extend them without guesswork.

Success is a new service going from clone to green CI and a publishable image in under 15 minutes, changing only four parameters (service name, database name + creds, port, image name), with adding a new REST resource requiring no changes to plumbing. Vault-based secret management and Auth0 authentication are deliberately out of v1, but the baseline is designed to accommodate them; a parameterized service generator follows once the baseline exists to template from.

## The Problem

Every backend service in the stack needs the same 80% of plumbing before a single line of domain code: a Spring Boot + Kotlin build, Postgres and Redis wiring, a local run profile, Docker Compose for dependencies, integration tests that pass in CI without touching real infrastructure, a container image that behaves correctly under Kubernetes (liveness/readiness probes, config from environment, graceful shutdown), and a CI pipeline to tie it together.

Assembled by hand, this is hours of fiddly, easy-to-get-subtly-wrong setup — a probe pointed at the wrong path, integration tests that only pass on the author's laptop, a local profile that quietly drifts from production. Alex has built this by hand before and is now starting a stack of services that will each need it.

Increasingly, that setup and the feature work on top of it will be done by AI coding agents, not only by hand. That raises the bar on _structure_: an agent adding functionality needs one obvious place for each kind of code, consistent conventions across every service, and a strong test suite that fails loudly when something is wired wrong. Without a shared baseline, every service — human- or agent-built — reinvents the plumbing and the services drift apart in dependency versions, package structure, and test strategy, making cross-service work and long-term maintenance harder with each one added.

## The Solution

A **template repository**: a working, deployable Spring Boot + Kotlin service with every piece of shared plumbing already solved and organized in clean-architecture layers, plus one example vertical slice showing the pattern end to end. You clone it, change a handful of parameters (service name, database name + creds, port, image name), adapt or delete the example slice, and start writing domain logic. Everything else already works.

What it provides:

- **Build & language** — Gradle (Kotlin DSL), current stable Kotlin + Spring Boot on a current JVM LTS, with lint/format and sensible compiler settings preconfigured.
- **Clean-architecture skeleton** — enforced layer boundaries (domain / application / inbound + outbound adapters), one obvious home for each kind of code. Adding functionality means adding a use case + adapters that mirror the example slice.
- **Persistence** — Postgres via JPA/Hibernate with Flyway schema migrations; Redis wired up for caching. Connection config externalized.
- **Local development** — `application-local.yaml` profile + a `docker compose` that brings up Postgres, Redis, and any other dependency; one command to run the service against them.
- **Testing** — unit tests for domain/application logic; integration tests that start real dependencies in containers (Testcontainers), so the whole suite runs in CI with no external infrastructure. The example slice is covered end to end.
- **APIs** — REST/JSON only in the baseline.
- **Observability & runtime** — liveness/readiness/health endpoints, Micrometer + Prometheus metrics, an OpenTelemetry hook, config from environment, graceful shutdown, structured logging, and a container image build. Drops into the existing Helm + Argo CD pipeline (charts live in a separate repo).
- **CI pipeline** — build, test (including integration), and publish the image on every change.
- **Docs** — short README: which parameters to change, and how to add a new feature.

## What Makes This Different

There is no technical moat here, and this brief does not claim one. The value is fit and completeness.

- **Spring Initializr** produces a build file and a dependency list — you still assemble the remaining 80%: clean-architecture structure, local Docker Compose, Testcontainers integration tests, a Kubernetes-ready runtime, CI, and an example to copy. This starter is that assembly, done and tested end to end.
- **Copying from an existing repo** carries drift — stale versions, accumulated hacks, and a structure shaped for a different problem.
- **A generic community starter** rarely matches this exact stack (JPA + Flyway + Redis + Testcontainers + Micrometer/OpenTelemetry + this CI + these Kubernetes conventions) and brings along parts that must be stripped out.

The edge is that it is exactly this stack — opinionated, complete, continuously maintained, and deliberately shaped so an AI agent can extend it without guesswork. That is an execution-and-fit advantage, not a defensible one, which is the right trade for an internal foundation.

## Who This Serves

**Primary — Alex.** A solo engineer standing up and maintaining a growing stack of Kotlin backend services, who has done this setup by hand before and wants it to be a solved problem.

**Co-primary — AI coding agents.** Agents that scaffold new services from the template and implement features on top of the baseline. They are served well when the structure is predictable enough to extend without guesswork and the test suite catches wiring mistakes on its own.

**Secondary — future engineers.** If others join, the consistency across services is where it pays off. Not a driver for v1.

## Success Criteria

- A new service goes from clone to **runs locally + green CI + publishable image in under 15 minutes**, changing only: service name, database name + creds, port, image name.
- `docker compose up` plus one command runs the service locally against real Postgres and Redis, with no further setup.
- The full test suite (unit + integration) passes in CI with **zero external infrastructure** and without flakiness.
- Adding a new REST resource — use case + adapters + tests, mirroring the example slice — requires **no changes to plumbing**, and a first attempt (by Alex or an agent) lands cleanly.
- The image runs under Kubernetes and passes liveness/readiness **without probe reconfiguration**.
- Prometheus scrapes metrics and OpenTelemetry traces appear with no additional wiring.

## Scope

**In (v1):**

- The full plumbing set from The Solution, working end to end.
- One example vertical slice (REST → Postgres → Redis) with unit + integration tests.
- Single service, single Postgres database, single Redis instance.
- README covering the parameters to change and how to add a feature.

**Out (v1):**

- Rename/generator automation — deferred until an app exists to template from.
- Helm charts and Argo CD config — separate repo.
- Messaging (Kafka/RabbitMQ), gRPC.
- Multi-module builds, multiple datasources.
- **Vault integration** — roadmap. v1 keeps config fully externalized so Vault can inject secrets later with no code change.
- **Auth0 / authentication** — roadmap. v1 leaves a documented seam for OAuth2 resource-server / JWT validation but implements none.

## Vision

In two to three years, every Kotlin backend service in the stack traces back to this baseline and shares its structure — so a dependency upgrade, a probe fix, or a logging improvement worked out in one service is mechanical to apply to the rest. The deferred generator has landed: a new service is a parameterized command, not a clone-and-edit. Vault and Auth0 are folded in as the standard rather than per-service decisions. If the need becomes real, downstream services gain a way to pull baseline improvements instead of fork-and-forget. The end state is a foundation solid enough that spinning up a new backend service — by hand or by agent — is a non-event.
