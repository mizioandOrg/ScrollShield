# Problem Definition — Visual-First ML Pipeline Docker Infrastructure

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Review and update the existing Dockerfiles to incorporate the visual-first classification architecture. The ML pipeline now needs MobileNetV3-Small training with image datasets, torchvision, and DVC. New work items WI-16 (screen capture), WI-17 (visual classifier training), and WI-18 (on-device benchmarking) have been added, and existing work items (WI-06, WI-15, etc.) have been restructured for the visual-first approach.

Existing Dockerfiles are already in place (docker/android-builder/Dockerfile, docker/ml-pipeline/Dockerfile, docker/signature-api/Dockerfile, docker-compose.yml). Update them to support the new visual training pipeline while preserving existing functionality.

## Context Files

- work-items/WI-01-project-scaffolding.md through WI-18-visual-signature-matching.md (all 18)
- docs/technical-spec.md
- README.md
- docker/android-builder/Dockerfile
- docker/ml-pipeline/Dockerfile
- docker/signature-api/Dockerfile
- docker-compose.yml
- ml/requirements.txt

## Target Files (to modify)

- docker/ml-pipeline/Dockerfile
- ml/requirements.txt
- docker-compose.yml
- docker/android-builder/Dockerfile
- Any additional Dockerfiles the Planner determines are needed

## Rules & Constraints

- Update existing Dockerfiles rather than replacing from scratch where possible
- The visual ML pipeline needs image processing capabilities (torchvision, Pillow, OpenCV)
- Support GPU passthrough for ML training containers
- Dataset volume mounts for large screenshot datasets (not baked into images)
- Maintain all existing review criteria (multi-stage builds, non-root users, pinned versions, etc.)

## Review Criteria

1. All new dependencies from WI-16, WI-17, WI-18 are covered (torchvision, Pillow, DVC, MobileNetV3, ONNX, OpenCV)
2. ML pipeline Dockerfile supports visual model training (MobileNetV3-Small with image dataset input)
3. Dataset volumes are externally mounted, not baked into Docker images
4. GPU passthrough configured for ML training container (NVIDIA runtime)
5. Existing Dockerfiles are updated rather than rewritten — preserve working functionality
6. Multi-stage builds maintained or improved to minimize image size
7. Docker best practices preserved (layer caching, non-root users, pinned base images, .dockerignore)
8. docker-compose.yml updated to support both text and visual ML pipelines
9. Security best practices maintained (no secrets, minimal packages, pinned versions)
10. Each Dockerfile remains self-contained and independently buildable

## Implementation Instructions

```
cd /home/devuser/dockerfile-worktree
docker compose build
docker compose up --dry-run
```
