# Approved Plan — WI-18 Visual Model Benchmark & Optimization

Approved at Iteration 2 with Score 10/10.

## Changes to app/src/androidTest/java/com/scrollshield/benchmark/VisualClassifierBenchmark.kt

Create the file with the following concrete contents.

- **Package & imports** (top of file):
  - `package com.scrollshield.benchmark`
  - Imports: `androidx.benchmark.junit4.BenchmarkRule`, `androidx.benchmark.junit4.measureRepeated`, `androidx.test.ext.junit.runners.AndroidJUnit4`, `androidx.test.platform.app.InstrumentationRegistry`, `org.junit.Rule`, `org.junit.Test`, `org.junit.runner.RunWith`, `org.junit.Assert.assertTrue`, `android.util.Log`, `org.tensorflow.lite.Interpreter`, `org.tensorflow.lite.nnapi.NnApiDelegate`, `org.tensorflow.lite.gpu.CompatibilityList` (loaded reflectively), plus `com.scrollshield.ml.VisualModelLoader` and `com.scrollshield.ml.VisualClassifier` (WI-17 public APIs — not modified).
- **Class declaration**: `@RunWith(AndroidJUnit4::class) class VisualClassifierBenchmark`
- **`@get:Rule val benchmarkRule = BenchmarkRule()`**
- **Companion object with reference baselines** (asserted, criterion 9):
  - `const val THRESHOLD_NNAPI_INFERENCE_MS_P95 = 60L`
  - `const val THRESHOLD_CPU_INFERENCE_MS_P95 = 100L`
  - `const val THRESHOLD_COLD_START_MS_P95 = 350L`
  - `const val WI15_TAG = "WI15_BENCH_RESULT"`
  - `const val WARMUP_ITERATIONS = 5`
  - `const val MEASURE_ITERATIONS = 50`
- **Helper `selectDelegate(interpreterOptions: Interpreter.Options): String`** — NNAPI → GPU → CPU/XNNPack fallback:
  1. Try `NnApiDelegate()`; on success attach and return `"NNAPI"`.
  2. Else `Class.forName("org.tensorflow.lite.gpu.CompatibilityList").getDeclaredConstructor().newInstance()`; call `isDelegateSupportedOnThisDevice` reflectively; if true instantiate `org.tensorflow.lite.gpu.GpuDelegate` via reflection and attach; return `"GPU"`.
  3. Else `interpreterOptions.setUseXNNPACK(true).setNumThreads(4)`; return `"CPU_XNNPACK"`.
  4. Log: `Log.i("WI18_DELEGATE", "active_delegate=$delegateName device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")`.
- **Helper `percentile(values: LongArray, p: Double): Long`** — sorts and returns the p-th percentile.
- **Helper `emitWI15Baseline(metric: String, medianMs: Long, p95Ms: Long, delegate: String)`**:
  - `Log.i(WI15_TAG, "metric=$metric median_ms=$medianMs p95_ms=$p95Ms delegate=$delegate")`.
- **`@Test fun benchmark_coldStart_modelLoad()`**: time `VisualModelLoader.load(context)`; 5 warmup + 20 measured; manual sample collection; emit `cold_start` baseline; assert `p95 <= THRESHOLD_COLD_START_MS_P95`, wrapped in try/catch that still logs baseline on failure.
- **`@Test fun benchmark_nnapiInference()`**: force NNAPI (skip via `Assume.assumeTrue` if unavailable); fixed seeded input; 5 warmup + 50 measured; emit `nnapi_inference` baseline; assert `p95 <= THRESHOLD_NNAPI_INFERENCE_MS_P95`.
- **`@Test fun benchmark_cpuXnnpackInference()`**: force CPU + XNNPack with 4 threads; emit `cpu_xnnpack_inference` baseline; assert `p95 <= THRESHOLD_CPU_INFERENCE_MS_P95`.
- **`@Test fun benchmark_gpuInference()`**: `Assume.assumeTrue` on GPU compatibility; emit `gpu_inference` baseline.

## Changes to app/src/androidTest/java/com/scrollshield/benchmark/PipelineBenchmark.kt

- **Package, runner, rule, imports** same pattern as VisualClassifierBenchmark, plus `com.scrollshield.capture.ScreenCaptureManager` (WI-16) and `com.scrollshield.classification.ClassificationPipeline` (WI-06).
- **Companion baselines**:
  - `const val THRESHOLD_FRAME_CAPTURE_MS_P95 = 15L`
  - `const val THRESHOLD_CROP_RESIZE_MS_P95 = 8L`
  - `const val THRESHOLD_E2E_TIER1_MS_P95 = 80L`
  - `const val THRESHOLD_E2E_FULL_MS_P95 = 120L`
  - `const val WI15_TAG = "WI15_BENCH_RESULT"`
- **`@Test fun benchmark_frameCapture()`**: `ScreenCaptureManager.captureSingleFrame()`; 5 warmup + 50 measured; emit `frame_capture` baseline; assert p95 ≤ 15ms.
- **`@Test fun benchmark_cropAndResize()`**: pre-create fixed 1080×2400 Bitmap (deterministic); time `ClassificationPipeline.cropAndResize(bitmap)`; emit `crop_resize` baseline; assert p95 ≤ 8ms.
- **`@Test fun benchmark_endToEndTier1()`**: text-only Tier-1 path; 5 warmup + 50 measured; emit `e2e_tier1` baseline; assert p95 ≤ 80ms.
- **`@Test fun benchmark_endToEndFullPipeline()`**: full pipeline with visual; emit `e2e_full` baseline; assert p95 ≤ 120ms (informational).

## Changes to app/src/androidTest/java/com/scrollshield/benchmark/MemoryBenchmark.kt

Combined memory + thermal benchmark (target-file list permits only 3 Kotlin files).

- **Class KDoc** explicitly: combined memory budget + thermal characterization, lists thermal logcat tag `WI14_THERMAL_FEEDBACK` for WI-14 scrape, lists CI regression tag `WI15_BENCH_RESULT`.
- **Companion baselines & log tags**:
  - `const val THRESHOLD_PEAK_MEMORY_MB = 150L`
  - `const val THRESHOLD_LEAK_DRIFT_MB = 5L`
  - `const val THRESHOLD_THERMAL_LATENCY_DRIFT_PCT = 25`
  - `const val WI14_THERMAL_TAG = "WI14_THERMAL_FEEDBACK"`
  - `const val WI15_TAG = "WI15_BENCH_RESULT"`
- **Structured WI-14 logcat lines**:
  - Per-iter: `Log.w(WI14_THERMAL_TAG, "event=thermal_sample iter=$i temp_c=$tempC inference_ms=$infMs drift_pct=$driftPct throttled=$wasThrottled")`
  - Summary: `Log.w(WI14_THERMAL_TAG, "event=thermal_summary total_iters=50 throttle_trigger_iter=$triggerIter peak_temp_c=$peakTempC start_p95_ms=$startP95 end_p95_ms=$endP95 drift_pct=$totalDriftPct")`
  - Temperature source: HardwarePropertiesManager via reflection → `/sys/class/thermal/thermal_zone0/temp` fallback → `temp_c=-1` sentinel.
- **`@Test fun memory_preScanWithVisualClassification()`**: WI-17 model + 20 classifications; sample peak via `Debug.MemoryInfo` and `Runtime.getRuntime().totalMemory()-freeMemory()`; emit `mem_prescan` baseline; assert peak ≤ 150MB.
- **`@Test fun memory_liveModeLookaheadExtension()`**: simulate WI-16 live-capture with lookahead, 30 frames; emit `mem_live` baseline; assert.
- **`@Test fun memory_bothModelsLoadedSimultaneously()`**: text + visual model loaded simultaneously, 10 mixed classifications; emit `mem_both_models` baseline; assert.
- **`@Test fun memory_leakCheck_100Items()`**: warmup 5, baseline RSS, 100 full-pipeline classifications, two GC + finalize cycles with 100ms sleeps, final RSS; `driftMb = (final - baseline)/1024/1024`; emit `mem_leak_drift` baseline; assert ≤ 5MB.
- **`@Test fun thermal_50IterationRun()`**: 50 sequential inferences no sleep; capture temp + inference time + sliding 10-iter-window p95; trigger = first iter where window p95 > 1.25 × initial-window p95; emit per-iter `event=thermal_sample` + final `event=thermal_summary`; also emit `WI15_BENCH_RESULT metric=thermal_drift_pct value=… trigger_iter=…`; assert drift ≤ 25% (soft, baseline still emitted on failure).

## Changes to ml/benchmark/run_tflite_benchmark.sh

`#!/usr/bin/env bash` + `set -euo pipefail`.

- **Header comment block** = canonical CI contract:
  - Documents dual purpose (TFLite native bench + `--ci` microbenchmark harvest).
  - Harvested log line formats:
    - `WI15_BENCH_RESULT metric=<name> median_ms=<int> p95_ms=<int> delegate=<str>`
    - `WI15_BENCH_RESULT metric=mem_<name> peak_mb=<int> delegate=<str>`
    - `WI15_BENCH_RESULT metric=mem_leak_drift drift_mb=<int>`
    - `WI15_BENCH_RESULT metric=thermal_drift_pct value=<int> trigger_iter=<int>`
    - `WI14_THERMAL_FEEDBACK event=thermal_summary total_iters=50 throttle_trigger_iter=<int> peak_temp_c=<int> start_p95_ms=<int> end_p95_ms=<int> drift_pct=<int>`
  - Reference baselines (not separate files): cold_start p95 ≤ 350, nnapi_inference p95 ≤ 60, cpu_xnnpack_inference p95 ≤ 100, frame_capture p95 ≤ 15, crop_resize p95 ≤ 8, e2e_tier1 p95 ≤ 80, mem_prescan/live/both_models peak ≤ 150, mem_leak_drift ≤ 5, thermal_drift_pct ≤ 25.
  - Exit codes: 0 pass / 1 usage / 2 infra / 3 regression / 4 thermal-early-throttle.
  - CI invocation: `ml/benchmark/run_tflite_benchmark.sh --ci --device $ANDROID_SERIAL --model app/src/main/assets/visual_classifier.tflite --delegate nnapi --threads 4 --iterations 50 --warmup 5 --output build/wi18-bench.json`.
  - JSON schema: device, delegate, threads, iterations, tflite_native, microbench[], memory[], thermal, regressions[], exit_code.
- **Arg parsing** (`while-case`): `--model`, `--device`, `--delegate` (nnapi|gpu|xnnpack|cpu), `--threads` (default 4), `--iterations` (default 50), `--warmup` (default 5), `--output`, `--ci`, `--no-thermal-fail`, `--help`. Unknown flag → exit 1.
- **`die()`** helper, **`require_cmd adb jq`** (exit 2 if missing).
- **TFLite native run**: push `benchmark_model` binary, invoke with appropriate `--use_nnapi=`/`--use_gpu=`/`--use_xnnpack=` flags, parse `Inference timings in us:` line.
- **CI mode (`--ci`)**:
  1. `./gradlew :app:assembleAndroidTest -q` (fail → exit 2).
  2. `adb -s $DEVICE logcat -c`.
  3. `./gradlew :app:connectedBenchmarkAndroidTest -PandroidTestSerial=$DEVICE` (fail → exit 2).
  4. `adb -s $DEVICE logcat -d -s WI15_BENCH_RESULT:I WI14_THERMAL_FEEDBACK:W > /tmp/wi18-logcat.txt`.
  5. Parse via `grep | awk` into bash arrays.
  6. Compare values against in-script `BASELINE` associative array; append breaches to `REGRESSIONS=()`.
  7. Emit JSON via `jq -n --arg ... '{...}'` to `$OUTPUT`.
  8. Exit 3 if `REGRESSIONS` non-empty; exit 4 if thermal `trigger_iter < 30` and `--no-thermal-fail` unset (else 0).
- **Non-CI mode**: TFLite native bench only → JSON stub with `tflite_native` section.
- **ERR trap** emits JSON error blob to stderr.
- **Portability**: bash + adb + awk + grep + sed + jq; no GNU-only flags; `${VAR:-default}` throughout.
