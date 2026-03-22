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
- **Hard**: All other work items (this validates them), WI-17 (visual model training produces the visual classifier model)
- **Integration**: WI-06 (models consumed by pipeline), WI-16 (ScreenCaptureManager tested)

## Files to Create / Modify

- `app/src/test/` — unit test files
- `app/src/androidTest/` — integration test files
- `ml/train_classifier.py`
- `ml/export_tflite.py`
- `ml/dataset/` — synthetic test data
- `ml/train_visual_classifier.py`
- `ml/export_visual_tflite.py`
- `ml/dataset/screenshots/` — labeled screenshot dataset
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
- Visual classifier: mock Bitmap input, verify output shape and confidence thresholds
- ScreenCaptureManager: mock MediaProjection, verify frame acquisition and error handling
- PerceptualHash: hash consistency, similarity matching
- Pipeline tier routing: verify Tier 0 short-circuit, Tier 1 visual primary, Tier 2 fallback

### Integration Tests
- Full pre-scan cycle: fast-forward -> classify -> rewind -> loading overlay dismiss
- Live skip: user swipe -> ScanMap lookup -> skip execution -> flash
- Lookahead extension: trigger at correct distance, complete before user catches up
- Buffer exhaustion: user outruns buffer -> loading overlay -> re-scan -> continue
- Child profile: all blocked types skipped, time budget enforced
- Session recording on background and force-kill
- Visual classification: full pipeline with real screenshots — frame capture → visual inference → skip decision
- MediaProjection denial: verify graceful degradation to text-only
- Thermal throttle: verify degradation to Tier 0 only

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
| Classification pipeline (visual path) | < 100ms per item |
| Classification pipeline (all tiers) | < 150ms per item |
| Visual model inference | < 60ms on Snapdragon 7-series |
| Text model inference | < 50ms on Snapdragon 7-series |
| Frame capture (MediaProjection) | < 15ms |
| Live skip (ScanMap lookup + gesture) | < 500ms |
| Skip flash render | exactly 200ms |
| Signature lookup (100K entries) | < 5ms |
| Perceptual hash computation | < 5ms |
| Cosine similarity | < 1ms per item |
| Counter overlay | zero frame rate impact |

### Performance Regression
- CI benchmark suite: fail build if median classification latency exceeds 120ms (was 80ms, increased for visual tier)

### Privacy Tests
- Verify zero network calls during classification, session recording, and reporting flows
- Only signature sync and data export may make network calls

### Memory Budget Validation
| Component | Estimated Usage |
|---|---|
| MobileNetV3-Small model (loaded) | ~4MB |
| MobileNetV3-Small interpreter | ~8MB |
| DistilBERT-tiny model (loaded) | ~15MB |
| DistilBERT-tiny interpreter | ~10MB |
| MediaProjection frame buffer | ~3MB |
| SimHash index (100K entries) | ~12MB |
| ScanMap (10-item buffer) | <1MB |
| Room database connection | ~5MB |
| Overlay rendering (Compose) | ~15MB |
| Accessibility tree cache | ~8MB |
| Coroutines + Flow buffers | ~10MB |
| **Headroom** | **~59MB** |
| **Total** | **~150MB peak** |

Test: verify peak memory stays under 150MB during pre-scan + live mode.

### ML Pipeline

#### `train_visual_classifier.py` (NEW — PRIMARY)
- Fine-tune MobileNetV3-Small (pretrained on ImageNet) for 7-class visual classification + 20-dim topic vector
- Input: 224×224 RGB screenshots of social media feeds
- Multi-head output: 7-class softmax + 20-dim topic regression
- Transfer learning: freeze first 10 layers, fine-tune remaining + new heads
- Training: 50 epochs, batch size 32, lr 1e-4, cosine annealing
- Output: PyTorch checkpoint

#### `export_visual_tflite.py` (NEW)
- Convert visual PyTorch model → TFLite with int8 quantization
- Output: `scrollshield_visual_classifier.tflite` (~3.4MB)
- Validate: inference < 60ms on reference device
- Input tensor: `uint8[1][224][224][3]`
- Output tensors: `float32[1][7]` (classifications), `float32[1][20]` (topic vector)

#### `train_classifier.py` (RENAMED context: text classifier)
- Train DistilBERT-tiny (4L/128H) for 7-class text classification + 20-dim topic vector
- Unchanged from original spec — now explicitly labeled as "supplementary text classifier"

#### `export_tflite.py` (RENAMED context: text classifier)
- Output: `scrollshield_text_classifier.tflite` (~15MB) — renamed from `scrollshield_classifier.tflite`
- Otherwise unchanged

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
