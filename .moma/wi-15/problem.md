# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement work item **WI-15: Testing Infrastructure & ML Pipeline** for the ScrollShield2 Android project at `/home/devuser/ScrollShield2`.

The work item sets up the complete testing harness and the text-classifier ML pipeline:

1. **Unit tests** (`app/src/test/`) covering: classification tiers, skip decision engine, ScanMap, ad counter, pre-scan controller, consecutive skip handler, accessibility-service mock (Robolectric `ShadowAccessibilityService`), visual classifier wrapper, ScreenCaptureManager (mock MediaProjection), PerceptualHash, and pipeline tier routing (Tier 0 short-circuit / Tier 1 visual primary / Tier 2 text fallback).
2. **Integration tests** (`app/src/androidTest/`) covering: full pre-scan cycle, live skip, lookahead extension, buffer exhaustion, child profile enforcement, session recording on background/force-kill, full visual pipeline with real screenshots, MediaProjection denial → graceful text-only degradation, and thermal throttle → Tier 0 only.
3. **Synthetic test data**: 200-item feed (60% organic, 20% official ads, 10% influencer promos, 10% engagement bait) with known classifications, plus a 50-item child-safety set (gambling, diet, outrage).
4. **Performance benchmarks** (JUnit + AndroidX Benchmark or Macrobenchmark): pre-scan (10 items) < 6s, rewind (10 items) < 4s, full visual pipeline < 100ms/item, all-tiers pipeline < 150ms/item, visual inference < 60ms, text inference < 50ms, frame capture < 15ms, live skip < 500ms, skip flash exactly 200ms, signature lookup (100K) < 5ms, perceptual hash < 5ms, cosine similarity < 1ms, counter overlay 0 fps impact. CI gate: median classification latency must not exceed 120ms.
5. **Privacy tests**: assert zero network calls during classification, session recording, and reporting; only the signature-sync and data-export paths may touch the network.
6. **Memory-budget validation**: peak memory < 150MB during pre-scan + live mode (use Android profiler hooks or `Debug.getMemoryInfo()` in an instrumented test).
7. **App-update regression**: a saved accessibility-tree snapshot suite per target app version under `app/src/test/resources/snapshots/`, run against the `compat/` extractors.
8. **Text ML pipeline**:
   - `ml/train_classifier.py` — fine-tune DistilBERT-tiny (4L/128H) for 7-class text classification + 20-dim topic regression. Output: PyTorch checkpoint.
   - `ml/export_tflite.py` — convert PyTorch model → `app/src/main/assets/scrollshield_text_classifier.tflite` (~15MB) with int8 quantization. Validate inference < 50ms.
   - `ml/dataset/` — synthetic text training data + the 200-item / 50-item evaluation sets above.
   - `ml/eval/` — evaluation scripts that report per-class precision/recall and end-to-end latency.

The visual classifier pipeline (`ml/train_visual_classifier.py`, `ml/export_visual_tflite.py`, `ml/dataset/screenshots/`, `ml/eval/evaluate_visual.py`) is **already complete** from WI-17 and must not be re-implemented or regressed.

## Context Files

- /home/devuser/ScrollShield2/work-items/WI-15-testing-ml-pipeline.md
- /home/devuser/ScrollShield2/work-items/WI-01-project-scaffolding.md
- /home/devuser/ScrollShield2/work-items/WI-06-classification-pipeline.md
- /home/devuser/ScrollShield2/work-items/WI-16-screen-capture-service.md
- /home/devuser/ScrollShield2/work-items/WI-17-visual-model-training.md
- /home/devuser/ScrollShield2/build.gradle.kts
- /home/devuser/ScrollShield2/app/build.gradle.kts
- /home/devuser/ScrollShield2/settings.gradle.kts
- /home/devuser/ScrollShield2/gradle.properties
- /home/devuser/ScrollShield2/ml/train_visual_classifier.py
- /home/devuser/ScrollShield2/ml/export_visual_tflite.py

## Target Files (to modify)

- /home/devuser/ScrollShield2/app/build.gradle.kts
- /home/devuser/ScrollShield2/app/src/test/java/com/scrollshield/**/*.kt
- /home/devuser/ScrollShield2/app/src/test/resources/**
- /home/devuser/ScrollShield2/app/src/androidTest/java/com/scrollshield/**/*.kt
- /home/devuser/ScrollShield2/app/src/androidTest/assets/**
- /home/devuser/ScrollShield2/ml/train_classifier.py
- /home/devuser/ScrollShield2/ml/export_tflite.py
- /home/devuser/ScrollShield2/ml/dataset/**
- /home/devuser/ScrollShield2/ml/eval/**
- /home/devuser/ScrollShield2/ml/requirements.txt

## Rules & Constraints

- Do not modify or regress the visual pipeline shipped in WI-17 (`ml/train_visual_classifier.py`, `ml/export_visual_tflite.py`, `ml/eval/evaluate_visual.py`, `app/src/main/assets/scrollshield_visual_classifier.tflite`, anything under `ml/dataset/screenshots/`).
- Do not change public APIs of components owned by other WIs (WI-02..WI-14, WI-16) — tests must use the existing class/method signatures. If a signature must change, the Planner must flag this explicitly in the plan.
- The text-classifier asset must be written to `app/src/main/assets/scrollshield_text_classifier.tflite` (renamed from any legacy `scrollshield_classifier.tflite`).
- All unit tests must be deterministic — no real network, no real MediaProjection, no real TFLite GPU delegate. Use mocks, fakes, or Robolectric shadows.
- Privacy tests must use `OkHttp` MockWebServer or `HttpURLConnection` interception to assert zero network calls during the classification/recording/reporting paths.
- Performance benchmarks must run on JVM (Robolectric/JUnit) for CI gating; AndroidX Macrobenchmark variants may be added but must not be required for the CI gate.
- The ML pipeline must be runnable end-to-end inside the existing dev container — do not introduce new system packages beyond what `pip install` can provide. Reuse Python deps from WI-17 where possible.
- All new Kotlin files must use the existing package conventions (`com.scrollshield.*`) and Hilt where the unit under test is `@Inject`-constructed.
- The Implementer must run the full verification chain (see Implementation Instructions). If any step fails, the implementation is not complete.
- No TODO/FIXME placeholders in checked-in code — every test body must contain real assertions.
- Stay inside `/home/devuser/ScrollShield2`. Do not modify the agent-orchestration repo or the ScrollShield (v1) repo.

## Review Criteria

1. Plan creates concrete, named test classes for every bullet under "Unit Tests" in the WI spec, each with at least the assertions implied by the spec.
2. Plan creates concrete, named instrumented test classes for every bullet under "Integration Tests", and explicitly distinguishes JVM-only (Robolectric) from on-device (`androidTest`) variants.
3. Plan specifies how performance benchmarks are wired into Gradle (`testOptions`, benchmark sourceSet or AndroidX Benchmark plugin) and how the CI gate (≤120ms median classification) is enforced — a script or Gradle task that exits non-zero on violation.
4. Plan specifies how privacy tests prove the negative ("zero network calls"): which interception mechanism, which code paths are exercised, which paths are explicitly allowed.
5. Plan specifies the memory-budget assertion mechanism (Robolectric vs. instrumented), the measurement API used (`Debug.getMemoryInfo()`, `ActivityManager.MemoryInfo`, or AndroidX Macrobenchmark `TraceMetric`), and the 150MB threshold.
6. Plan specifies the full `train_classifier.py` design: dataset format, tokenizer, model config (DistilBERT-tiny 4L/128H), training loop, optimizer/schedule, multi-head output (7-class softmax + 20-dim regression), checkpoint format.
7. Plan specifies `export_tflite.py` end-to-end: PyTorch → ONNX → TF SavedModel → TFLite int8, input/output tensor shapes/dtypes, the validation step that asserts < 50ms inference, and where the asset is written.
8. Plan specifies the synthetic-data generator (200 items + 50 child-safety items): label distribution, deterministic seed, on-disk format consumed by both Python eval and Kotlin tests.
9. Plan reuses the WI-17 visual pipeline rather than touching its files, and explicitly lists the visual files that are out of scope.
10. Plan's Implementation Instructions section is executable end-to-end: it builds the app, runs all unit tests, runs the Python pipeline, produces the text TFLite asset, and reports pass/fail without manual intervention.

## Implementation Instructions

```
cd /home/devuser/ScrollShield2

# Android: build + unit tests + privacy + memory + benchmark JVM gate
./gradlew --no-daemon clean
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:testDebugUnitTest

# CI perf gate (must exit 0)
./gradlew --no-daemon :app:perfGateCheck || (echo "FAIL: classification latency gate" && exit 1)

# ML pipeline: text classifier
cd ml
python -m pip install -r requirements.txt
python train_classifier.py --epochs 1 --smoke   # smoke run, real training is opt-in
python export_tflite.py --input checkpoints/text_classifier.pt --output ../app/src/main/assets/scrollshield_text_classifier.tflite
python eval/evaluate_text.py --tflite ../app/src/main/assets/scrollshield_text_classifier.tflite --dataset dataset/text_eval.jsonl

# Verify asset is present and within size budget
test -f ../app/src/main/assets/scrollshield_text_classifier.tflite
python -c "import os,sys; sz=os.path.getsize('../app/src/main/assets/scrollshield_text_classifier.tflite'); sys.exit(0 if sz < 20*1024*1024 else 1)"

cd ..
echo "WI-15 implementation: OK"
```
