# WI-15: Testing Infrastructure & ML Pipeline

## Source
- Testing Strategy section (entire)
- File Structure: `ml/`, `app/src/test/`, `app/src/androidTest/`
- Memory Budget section
- Performance Benchmarks

## Goal
Set up the testing infrastructure (unit tests, integration tests, performance benchmarks, privacy tests) and the ML training/export pipeline.

## Context
This work item creates the test harnesses, test data, and the Python ML pipeline for training and exporting the TFLite classifier model.

## Dependencies
- **Hard**: All other work items (this validates them)
- **Integration**: WI-06 (model is consumed by Tier 3)

## Files to Create / Modify

- `app/src/test/` — unit test files
- `app/src/androidTest/` — integration test files
- `ml/train_classifier.py`
- `ml/export_tflite.py`
- `ml/dataset/` — synthetic test data
- `ml/eval/` — evaluation scripts

## Detailed Specification

### Unit Tests
- Classification: each tier with mock inputs
- Skip decision engine: all rules, all profile types
- ScanMap: position tracking, lookahead triggers, edge cases
- Ad counter: counting, revenue calculation, budget thresholds
- Pre-scan controller: buffer construction, rewind accuracy
- Consecutive skip handler: batching logic
- Accessibility service mock: Robolectric `ShadowAccessibilityService`

### Integration Tests
- Full pre-scan cycle: fast-forward -> classify -> rewind -> loading overlay dismiss
- Live skip: user swipe -> ScanMap lookup -> skip execution -> flash
- Lookahead extension: trigger at correct distance, complete before user catches up
- Buffer exhaustion: user outruns buffer -> loading overlay -> re-scan -> continue
- Child profile: all blocked types skipped, time budget enforced
- Session recording on background and force-kill

### App Update Regression
- Saved accessibility tree snapshot suite for each target app version
- Run against `compat/` extractors to detect breakage on app updates

### Test Data
- Synthetic feed: 200 items — 60% organic, 20% official ads, 10% influencer promos, 10% engagement bait
- Known classifications for accuracy tests
- Child-safety set: 50 items with gambling, diet, outrage — verify child profile blocks all

### Performance Benchmarks
| Benchmark | Target |
|---|---|
| Pre-scan (10 items) | < 6 seconds |
| Rewind (10 items) | < 4 seconds |
| Classification pipeline | < 60ms per item |
| Live skip (ScanMap lookup + gesture) | < 500ms |
| Skip flash render | exactly 200ms |
| Signature lookup (100K entries) | < 5ms |
| Cosine similarity | < 1ms per item |
| Counter overlay | zero frame rate impact |

### Performance Regression
- CI benchmark suite: fail build if median classification latency exceeds 80ms

### Privacy Tests
- Verify zero network calls during classification, session recording, and reporting flows
- Only signature sync and data export may make network calls

### Memory Budget Validation
| Component | Estimated Usage |
|---|---|
| TFLite model (loaded) | ~15MB |
| TFLite interpreter | ~10MB |
| SimHash index (100K entries) | ~12MB |
| ScanMap (10-item buffer) | <1MB |
| Room database connection | ~5MB |
| Overlay rendering (Compose) | ~15MB |
| Accessibility tree cache | ~8MB |
| Coroutines + Flow buffers | ~10MB |
| **Headroom** | **~74MB** |
| **Total** | **~150MB peak** |

Test: verify peak memory stays under 150MB during pre-scan + live mode.

### ML Pipeline

#### `train_classifier.py`
- Train DistilBERT-tiny (4L/128H) for 7-class classification + 20-dim topic vector
- Input: social media captions with labels
- Output: PyTorch model checkpoint
- Framework: PyTorch + HuggingFace Transformers

#### `export_tflite.py`
- Convert PyTorch model -> TFLite with float16 quantization
- Output: `scrollshield_classifier.tflite` (~15MB)
- Validate: inference < 50ms on reference device
- Input tensor: `int32[1][128]`
- Output tensors: `float32[1][7]` (classifications), `float32[1][20]` (topic vector)

## Acceptance Criteria
- All unit tests pass
- Integration tests cover all critical flows
- Performance benchmarks meet all targets
- Privacy tests verify zero unauthorized network calls
- ML pipeline produces valid TFLite model
- TFLite model inference < 50ms
- Peak memory < 150MB

## Notes
- Instrumented tests require a real device or emulator with target apps installed.
- The ML training pipeline requires Python with PyTorch and HuggingFace — separate from the Android build.
