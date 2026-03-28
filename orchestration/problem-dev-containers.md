# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: sonnet
Implement: yes

## Task Description

The existing Docker files in `docker/` are designed as CI build pipelines — they COPY source in, run build commands, and produce artifacts. They are not suitable for interactive development.

Create a new `docker/dev/` folder containing dev environment Docker files for each of the three existing CI containers. Dev containers must be designed for interactive development:
- Source code mounted as a volume at `/workspace` (not copied in)
- No baked-in build commands
- SSH server (sshd) running so developers can connect remotely
- SSH restricted to 127.0.0.1 via port binding
- Entrypoint writes `authorized_keys` on first start, then runs `sshd -D`
- Named volume mounted at `/home/devuser` for persistence across restarts

The three dev containers needed:
1. **android-dev** — for Android development (matching android-builder CI versions), SSH on port 2244
2. **ml-dev** — for ML/Python development (matching ml-pipeline CI versions), SSH on port 2243
3. **signature-api-dev** — for signature API development (matching signature-api CI versions), SSH on port 2242

Also create a `docker-compose.dev.yml` that orchestrates all three, and a `README.md` explaining usage.

## Context Files

- docker/android-builder/Dockerfile
- docker/ml-pipeline/Dockerfile
- docker/signature-api/Dockerfile
- docker-compose.yml
- work-items/WI-01-project-scaffolding.md
- work-items/WI-17-visual-model-training.md

## Target Files (to modify)

- docker/dev/android-dev/Dockerfile
- docker/dev/ml-dev/Dockerfile
- docker/dev/signature-api-dev/Dockerfile
- docker/dev/docker-compose.dev.yml
- docker/dev/README.md

## Rules & Constraints

- Dev-first design — Containers must be interactive (source mounted as volume, not copied in). No baked-in build commands.
- Version alignment — Dev containers must use the same SDK, tool, and dependency versions as their CI counterparts.
- No duplication — Don't replicate the CI Dockerfiles. Dev containers extend or complement them.
- Non-root user — All containers run as `devuser` (non-root).
- Volume mount pattern — Source code mounted at `/workspace` via volume, not COPY.
- Persistent home — Each container mounts a named volume at `/home/devuser` so the user's home directory persists across restarts.
- SSH restricted — SSH is restricted to 127.0.0.1 only, via port binding (android-dev: 2244, ml-dev: 2243, signature-api-dev: 2242).
- SSH entrypoint — The entrypoint writes `authorized_keys` on first start, then runs `sshd -D`.
- Compose integration — `docker-compose.dev.yml` must work standalone (`docker compose -f docker/dev/docker-compose.dev.yml up`).

## Review Criteria

1. Dev-first design — Containers use volume mounts, not COPY, and have no baked-in build commands
2. Version alignment — Dev containers use identical SDK/tool/Python versions as their CI counterparts
3. Persistent home — Named volume mounted at `/home/devuser` on all three containers
4. SSH access — sshd runs on correct ports (2244/2243/2242), bound to 127.0.0.1 only
5. SSH entrypoint — Entrypoint correctly writes `authorized_keys` on first start then runs `sshd -D`
6. Compose integration — `docker-compose.dev.yml` works standalone and defines named volumes and port bindings correctly
7. No duplication — Dev Dockerfiles complement CI, not replicate it
8. Non-root user — All containers run as `devuser` (non-root)
9. README — Documents how to start containers and connect via SSH for each container
10. All three containers covered — android-dev, ml-dev, and signature-api-dev all present and correct

## Implementation Instructions

No build commands — this is a Docker and documentation task.
