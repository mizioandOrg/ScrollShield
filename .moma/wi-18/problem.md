# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement work-item WI-18 (Visual Model On-Device Benchmark & Optimization) for the ScrollShield2 Android project. The full specification lives at `/home/devuser/ScrollShield2/work-items/WI-18-visual-signature-matching.md`. The goal is to benchmark the MobileNetV3-Small visual classifier on target Android hardware, optimize inference latency via NNAPI/GPU/CPU delegate selection, validate memory budget compliance (< 150MB peak), and document thermal-throttle behavior. Deliverables are three Android microbenchmarks under `app/src/androidTest/java/com/scrollshield/benchmark/` plus a TFLite benchmark shell script under `ml/benchmark/`.

## Context Files

- /home/devuser/ScrollShield2/work-items/WI-18-visual-signature-matching.md
- /home/devuser/ScrollShield2/work-items/WI-17-visual-model-training.md
- /home/devuser/ScrollShield2/work-items/WI-16-screen-capture-service.md
- /home/devuser/ScrollShield2/work-items/WI-06-classification-pipeline.md

## Target Files (to modify)

- app/src/androidTest/java/com/scrollshield/benchmark/VisualClassifierBenchmark.kt
- app/src/androidTest/java/com/scrollshield/benchmark/PipelineBenchmark.kt
- app/src/androidTest/java/com/scrollshield/benchmark/MemoryBenchmark.kt
- ml/benchmark/run_tflite_benchmark.sh

## Rules & Constraints

- Do not modify files outside the listed target files.
- Keep existing public APIs / function signatures of dependency modules (WI-06 classification pipeline, WI-16 ScreenCaptureManager, WI-17 visual-model artifact loader) stable — benchmarks consume them, they must not be changed.
- Use the `androidx.benchmark` library for microbenchmarks (per spec note).
- Use Kotlin for Android test code; bash for the TFLite benchmark shell script.
- Benchmark results must be reproducible: fixed iteration counts, defined warmup, report median and p95.
- Do not commit large model binaries or device-specific result blobs into the source tree. Reference baselines must be small (JSON or Markdown only).

## Review Criteria

1. Plan covers all four target files with concrete contents (no placeholders).
2. Latency benchmarks cover every metric in the spec table (cold start, NNAPI inference, CPU/XNNPack inference, frame capture, crop+resize, end-to-end Tier 1, end-to-end full pipeline).
3. Delegate selection logic (NNAPI → GPU → CPU fallback) is specified, including runtime detection and diagnostic logging of the active delegate.
4. Memory benchmark validates all three scenarios (pre-scan with visual classification, live mode with lookahead extension, both models loaded simultaneously) and includes a 100-item leak check.
5. Thermal benchmark covers a 50-iteration run measuring temperature rise, latency degradation over time, and throttle trigger point — and feeds findings to WI-14 error handling.
6. Uses `androidx.benchmark` correctly: proper `BenchmarkRule`, defined warmup, appropriate measurement modes.
7. Integrates with WI-17 (model artifact), WI-16 (frame capture), and WI-06 (pipeline) without modifying their public APIs.
8. `run_tflite_benchmark.sh` is portable, parameterized per device (model path, delegate, threads, iterations), and produces parseable output (e.g. JSON or stable text).
9. Acceptance-criteria thresholds (60ms NNAPI, 100ms CPU, 15ms capture, 80ms E2E Tier 1, 150MB peak) are explicitly asserted in the benchmark code or documented as reference baselines.
10. Plan addresses CI regression integration (WI-15) and specifies where baseline results are stored in the repo.

## Implementation Instructions

```
cd /home/devuser/ScrollShield2
./gradlew :app:assembleAndroidTest
./gradlew :app:compileDebugAndroidTestKotlin
chmod +x ml/benchmark/run_tflite_benchmark.sh
bash -n ml/benchmark/run_tflite_benchmark.sh
```
