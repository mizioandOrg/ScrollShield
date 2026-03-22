# WI-18: Visual Model On-Device Benchmark & Optimization

## Source
- Module 2: Classification Pipeline (Tier 1 latency targets)
- Performance Benchmarks section
- Memory Budget section

## Goal
Benchmark the MobileNetV3-Small visual classifier on target Android hardware, optimize inference latency via NNAPI/GPU delegates, and validate memory budget compliance. Ensure the visual-first pipeline meets latency targets on mid-range Android devices (Snapdragon 7-series).

## Context
The visual classifier (Tier 1) is the primary classification path and must meet strict latency requirements (< 60ms inference on Snapdragon 7-series). This work item validates those targets on real hardware, tunes delegate selection (NNAPI vs. GPU vs. CPU), and measures end-to-end pipeline latency including frame capture. It also validates that the total memory budget (150MB peak) is not exceeded with the addition of the visual model and MediaProjection frame buffer.

## Dependencies
- **Hard**: WI-17 (produces the trained visual TFLite model), WI-16 (ScreenCaptureManager for frame capture benchmarks), WI-06 (classification pipeline for end-to-end benchmarks)
- **Integration**: WI-15 (benchmark results feed into CI performance regression tests), WI-01 (build configuration for benchmark variant)

## Files to Create / Modify

- `app/src/androidTest/java/com/scrollshield/benchmark/VisualClassifierBenchmark.kt`
- `app/src/androidTest/java/com/scrollshield/benchmark/PipelineBenchmark.kt`
- `app/src/androidTest/java/com/scrollshield/benchmark/MemoryBenchmark.kt`
- `ml/benchmark/run_tflite_benchmark.sh` — script to run TFLite benchmark tool on device

## Detailed Specification

### Target Devices
Benchmark on at minimum:
1. Pixel 4a (Snapdragon 730G) — low-end target
2. Pixel 6a (Tensor G1) — mid-range
3. Samsung Galaxy A54 (Exynos 1380) — mid-range, different chipset
4. Pixel 8 (Tensor G3) — high-end reference

### Latency Benchmarks
For each device, measure:
| Metric | Target | Method |
|--------|--------|--------|
| Visual model cold start | < 1s | Time from `Interpreter()` constructor to first inference |
| Visual model inference (NNAPI) | < 60ms | Median of 100 inferences, p95 < 80ms |
| Visual model inference (CPU/XNNPack) | < 100ms | Fallback when NNAPI unavailable |
| Frame capture | < 15ms | Median of 100 captures |
| Bitmap crop + resize | < 5ms | 1080p -> 224x224 |
| End-to-end Tier 1 | < 80ms | Capture + preprocess + inference |
| End-to-end full pipeline | < 100ms | Tier 0 + Tier 1 (no Tier 2 needed) |

### Delegate Optimization
- Test NNAPI delegate, GPU delegate (OpenCL), and CPU (XNNPack) on each device
- Select best delegate per device family
- Implement runtime delegate selection: try NNAPI -> fall back to GPU -> fall back to CPU
- Log which delegate is active in diagnostic logs

### Memory Validation
- Measure peak memory during:
  1. Pre-scan with visual classification (10 items)
  2. Live mode with lookahead extension
  3. Both models loaded simultaneously (visual + text)
- Verify total peak < 150MB on all target devices
- Verify no memory leak over 100-item classification run

### Thermal Impact
- Run 50 consecutive classifications and measure:
  - Device temperature rise
  - Inference latency degradation over time
  - Thermal throttle trigger point
- Document thermal throttle behavior for WI-14 error handling

## Acceptance Criteria
- Visual model inference < 60ms (median) on Snapdragon 7-series with NNAPI
- Visual model inference < 100ms (median) on CPU fallback
- Frame capture < 15ms on all target devices
- End-to-end Tier 1 < 80ms on all target devices
- Peak memory < 150MB during pre-scan + visual classification
- No memory leak over 100-item classification run
- Thermal throttle point documented for each target device
- Delegate selection logic correctly falls back from NNAPI -> GPU -> CPU

## Notes
- Use Android Benchmark library (`androidx.benchmark`) for reliable microbenchmarks.
- The TFLite benchmark tool (`benchmark_model`) can be used for isolated model benchmarks without the full app.
- If Snapdragon 730G (Pixel 4a) fails the 60ms target, consider: (a) MobileNetV2 as smaller alternative, (b) further quantization (int4), (c) input resolution reduction to 192x192.
- Benchmark results should be committed to the repo as reference baselines for CI regression detection.
