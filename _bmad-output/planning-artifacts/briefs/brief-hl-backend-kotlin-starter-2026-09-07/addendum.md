---
title: "Addendum: Spring Boot Kotlin Starter Service"
status: draft
created: 2026-09-07
updated: 2026-09-07
---

# Addendum

Depth captured during discovery that belongs downstream (PRD / architecture) rather than in the brief itself.

## Roadmap items deliberately out of v1

### Rename / generator automation
- Goal: stand up a new service in minutes by supplying parameters (service name, database name + creds, port, image name) rather than manual find-replace.
- Deferred on purpose: revisit once the baseline app exists, so the generator templates from something real.
- Candidate forms: a Gradle `init`/rename task, a shell script, or a cookiecutter-style generator. Not decided.

### Vault secret management
- Vault is the intended secret manager for the stack.
- v1 requirement that protects this path: **all configuration externalized** (Spring config / environment), no secrets baked into the image or committed files, so Vault (or Vault Agent / sidecar / CSI) can inject later with no code change.
- Architecture step should confirm the injection mechanism and how local dev (`application-local.yaml`) diverges from Vault-backed environments.

### Auth0 authentication
- Likely direction: OAuth2 resource server validating JWTs issued by Auth0.
- v1 requirement that protects this path: a documented seam (where the security filter chain / resource-server config would attach) without implementing it.
- Architecture step should decide: per-service audience/issuer config, how integration tests handle auth (mint test tokens vs. profile that disables the filter), and Actuator endpoint exposure/protection.

## Deployment context

- Services deploy to a self-hosted Kubernetes cluster (Alex's server).
- GitOps: Helm charts live in a **separate charts repository**, synced by Argo CD.
- The starter's deployment responsibility ends at: a well-behaved container image (probes, env config, graceful shutdown, non-root where practical) plus CI that builds, tests, and publishes it. Manifests/values are the charts repo's concern.

## Consumers

- Primary human user: Alex (solo for now).
- Co-primary: AI coding agents that scaffold services and add features — the reason predictable structure and strong test guardrails are first-class requirements, not polish.
