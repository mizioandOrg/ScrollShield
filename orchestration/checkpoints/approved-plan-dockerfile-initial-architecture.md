# Approved Plan — Initial Dockerfile Architecture

**Score: 10/10 at Iteration 2**

## Architectural Analysis

Three distinct architectural components identified from WI-01 through WI-15:

1. **Android App Builder** (WI-01 through WI-14): Kotlin/Gradle, Android SDK, JDK 17
2. **ML Training Pipeline** (WI-15, WI-06): Python, PyTorch, HuggingFace, TFLite conversion
3. **Signature Sync API Server** (WI-12): Lightweight Python/FastAPI REST server

## Files Created (16)

1. `docker/android-builder/Dockerfile` — Multi-stage: JDK 17 + Android SDK 34 + Gradle 8.5 → APK artifact
2. `docker/ml-pipeline/Dockerfile` — Multi-stage: PyTorch + TensorFlow training → TFLite artifact
3. `docker/signature-api/Dockerfile` — FastAPI server with healthcheck, non-root user
4. `docker/signature-api/main.py` — FastAPI stub with /health and /api/v1/signatures endpoints
5. `docker/signature-api/requirements.txt` — Pinned: fastapi==0.108.0, uvicorn==0.25.0, pydantic==2.5.3
6. `docker-compose.yml` — 3 services with named volumes, resource limits, healthcheck
7. `.dockerignore` — Excludes .git, docs, work-items, orchestration, __pycache__
8. `settings.gradle.kts` — Standard Android settings with google/mavenCentral repos
9. `build.gradle.kts` — Project-level with AGP 8.2.0, Kotlin 1.9.22, Hilt 2.50, KSP
10. `app/build.gradle.kts` — Full module config with all deps from WI-01
11. `app/src/main/AndroidManifest.xml` — Minimal with launcher activity
12. `app/src/main/java/com/scrollshield/app/MainActivity.kt` — Stub Compose activity
13. `app/src/main/java/com/scrollshield/app/ScrollShieldApp.kt` — @HiltAndroidApp stub
14. `ml/requirements.txt` — Pinned: torch, transformers, tensorflow, numpy, datasets, scikit-learn
15. `ml/train_classifier.py` — Stub that trains minimal model and saves to output/model.pt
16. `ml/export_tflite.py` — Stub that creates minimal TFLite model via TF Keras

## Key Design Decisions

- **Android builder**: Installs Gradle 8.5 directly in Dockerfile, runs `gradle wrapper` to generate gradlew (no wrapper JAR in repo)
- **ML pipeline**: Single trainer stage runs both training and export, artifact stage only copies output
- **docker-compose**: Uses `target: builder` for android and `target: trainer` for ML
- **All Python deps pinned** to exact versions
- **Healthcheck** uses Python urllib instead of curl to avoid extra package
- **Non-root users** in all containers (appuser, mluser, apiuser)

## Iteration History

- **Iteration 1 (8/10)**: ML pipeline multi-stage build flawed; Dockerfiles not independently buildable without stubs
- **Iteration 2 (10/10)**: Restructured ML pipeline; added all stub files for buildability
