# Problem Definition — Initial Dockerfile Architecture

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Identify and write the Dockerfiles needed for the composite architecture of ScrollShield. Analyze the work items (WI-01 through WI-15) to understand the distinct architectural components and their dependencies. Do not create a single universal Dockerfile — instead, split Dockerfiles according to the architecture. The number of Dockerfiles is an open problem to be determined by analyzing the work items.

ScrollShield is an Android app with multiple subsystems: a Kotlin/Gradle Android app, a Python ML training pipeline (PyTorch/HuggingFace), and a signature sync API server. Each component has different build and runtime dependencies.

## Context Files

- work-items/WI-01-project-scaffolding.md
- work-items/WI-02-data-models.md
- work-items/WI-03-database-daos-preferences.md
- work-items/WI-04-utility-classes.md
- work-items/WI-05-feed-interception-service.md
- work-items/WI-06-classification-pipeline.md
- work-items/WI-07-profile-management.md
- work-items/WI-08-ad-counter.md
- work-items/WI-09-scroll-mask-prescan.md
- work-items/WI-10-scroll-mask-live.md
- work-items/WI-11-onboarding-settings.md
- work-items/WI-12-signature-sync.md
- work-items/WI-13-session-analytics.md
- work-items/WI-14-error-handling.md
- work-items/WI-15-testing-ml-pipeline.md
- docs/technical-spec.md
- README.md

## Target Files (to modify)

- To be determined by the Planner — the number and paths of Dockerfiles are part of the open problem. Expected outputs include Dockerfiles for each architectural component and a docker-compose.yml for orchestration.

## Rules & Constraints

- Do not create a single universal Dockerfile — split by architectural component
- The number and scope of Dockerfiles is determined by analyzing the work items
- Each Dockerfile should be production-ready with multi-stage builds where appropriate
- Dockerfiles must cover all dependencies referenced in the work items (Kotlin/Gradle, TFLite, Python/PyTorch, Room/SQLite, etc.)

## Review Criteria

1. Each architectural component has its own Dockerfile (no monolithic single-Dockerfile approach)
2. All dependencies from the work items are covered (Kotlin, Gradle, TFLite, Python, PyTorch, HuggingFace, Room, etc.)
3. Multi-stage builds used where appropriate to minimize image size
4. Dockerfiles follow best practices (layer caching, minimal base images, non-root users)
5. A docker-compose.yml (or equivalent orchestration) ties the components together
6. The ML pipeline container can produce and export the TFLite model artifact
7. The Android build container can compile the project with `./gradlew assembleDebug`
8. Each Dockerfile is self-contained and independently buildable
9. Security best practices (no secrets baked in, no unnecessary packages, pinned base image versions)
10. Clear separation of concerns — build-time vs runtime containers where applicable

## Implementation Instructions

```
cd /home/devuser/dockerfile-worktree
docker compose build
docker compose up --dry-run
```
