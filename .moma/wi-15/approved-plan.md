# Approved Plan — WI-15 (ScrollShield2 Testing Infrastructure & ML Pipeline)

**Iterations to converge:** 1
**Final score:** 10/10
**Model:** opus
**Approved on:** 2026-05-15

---

## Overall strategy

WI-15 ships in three layers: (1) Kotlin test sources under `app/src/test` and `app/src/androidTest`, plus a Gradle wiring + CI gate task; (2) a Python text-classifier pipeline (`train_classifier.py`, `export_tflite.py`, `dataset/`, `eval/`); (3) a small set of resources (synthetic feed JSON, accessibility-tree snapshots, vocab + tiny tflite asset). All visual-pipeline files from WI-17 are read-only and untouched.

The existing `ContentClassifier.kt` already reads `scrollshield_text_classifier.tflite` and `wordpiece_vocab.txt` from assets, with the int32 input layout `(input_ids, attention_mask)` and outputs `(7-class, 20-topic)`. The Python exporter must match this exact signature so the on-device path works without any Kotlin changes.

No public-API changes are required: every component named in the WI spec already exists with the assumed constructor/method shape.

## Files out of scope (WI-17 visual pipeline — DO NOT touch)
- `ml/train_visual_classifier.py`, `ml/export_visual_tflite.py`, `ml/eval/evaluate_visual.py`
- `ml/requirements-visual.txt`
- `ml/dataset/screenshots/**` (+ `screenshots.dvc`, `annotations.json`, `splits.json`)
- `app/src/main/assets/scrollshield_visual_classifier.tflite`

## Gradle changes (`app/build.gradle.kts`)

- `testOptions { unitTests.isIncludeAndroidResources=true; isReturnDefaultValues=true; maxHeapSize=2g }`
- Source-set wiring for `src/test/resources` and `src/androidTest/assets`
- Test deps: kotlinx-coroutines-test 1.7.3, mockk 1.13.10 (jvm+android), truth 1.4.2, androidx.test core/junit, arch.core-testing, OkHttp MockWebServer 4.12.0, org.json 20231013
- AndroidTest deps: mockk-android, androidx.test runner/rules, uiautomator 2.3.0, androidx.benchmark-junit4 1.2.4, MockWebServer
- New Gradle task `perfGateCheck` (depends on `testDebugUnitTest`): reads `app/build/perf/perf-report.json`, parses `medianClassificationLatencyMs`, throws `GradleException` if > 120 ms

## Test resources (`app/src/test/resources/**`)

- `dataset/text_feed_200.jsonl` — 200 records: 120 ORGANIC / 40 OFFICIAL_AD / 20 INFLUENCER_PROMO / 20 ENGAGEMENT_BAIT, seed 20260515
- `dataset/text_child_safety_50.jsonl` — 20 gambling, 15 diet, 15 outrage; `childUnsafe:true`
- `snapshots/{instagram_v300,tiktok_v32,youtube_v18}.json` — minimal accessibility-tree snapshots (organic + sponsored per app, 6 files)
- `assets/scrollshield_text_classifier.tflite` (produced by smoke export) and `assets/wordpiece_vocab.txt`

## Unit tests under `app/src/test/java/com/scrollshield/`

- `classification/SignatureMatcherTest.kt` — Hamming ≤ 3 → OFFICIAL_AD conf > 0.95; visual-hash ≤ 8; empty/no-match → null
- `classification/LabelDetectorTest.kt` — AD_LABEL → OFFICIAL_AD 1.0; unknown/null → null; case-insensitive
- `classification/VisualClassifierTest.kt` (Robolectric) — 1080×1920 bitmap; reflection-checks preprocess returns 224×224; classify returns VisualResult or null
- `classification/ContentClassifierTest.kt` (Robolectric) — dummy tflite → UNKNOWN, 0.0 fallback
- `classification/SkipDecisionEngineTest.kt` — table-driven: EDUCATIONAL→SHOW; conf<0.5→SHOW_LOW_CONF; child×{OFFICIAL_AD,ENGAGEMENT_BAIT,OUTRAGE_TRIGGER,INFLUENCER_PROMO}→SKIP_CHILD; adult+OFFICIAL_AD→SKIP_AD; adult+ENGAGEMENT_BAIT→SKIP_BLOCKED; topic-blocked→SKIP_BLOCKED; mask disabled→SHOW
- `classification/ClassificationPipelineRoutingTest.kt` (Robolectric) — Tier 0a/0b short-circuit (`verify(visualClassifier, never())` style), Tier 1 visual primary at conf ≥ 0.7, Tier 1 inconclusive → Tier 2, null screenCapture → Tier 2, thermal throttle → tier 0 UNKNOWN, both models unavailable → tier 0 UNKNOWN
- `feature/mask/ScanMapTest.kt` — addItem, shouldSkip, bufferRemaining, isDuplicate, isExtending, clear, padding; `LookaheadExtender.shouldTrigger(2)=true / shouldTrigger(4)=false`
- `feature/mask/PreScanControllerTest.kt` — runPreScan returns 10; scrollForward×10; scrollBackward×1; feed-mutation via lastValidatedHash; duplicate → early stop; low-memory → buffer 5
- `feature/mask/ConsecutiveSkipHandlerTest.kt` — 5 onSkip → true; 6th → false + isHighDensityBlocking=true; onNonSkip after 3 resets; onHighDensityDismissed resets
- `feature/counter/AdCounterManagerTest.kt` — emit 5 OFFICIAL_AD → adsDetected=5, revenue, brands, categories, tierCounts; statusColor budget threshold; 1000 emissions ≤ 100 ms wall (overlay-zero-fps proxy)
- `accessibility/ScrollShieldAccessibilityServiceTest.kt` (Robolectric `ShadowAccessibilityService`) — Robolectric.buildService → dispatch TYPE_VIEW_SCROLLED → onInterrupt callable
- `classification/ScreenCaptureManagerTest.kt` (Robolectric) — isAvailable=false before start; captureFrame=null; stop idempotent
- `util/PerceptualHashSimilarityTest.kt` (Robolectric) — gradient+brightness Hamming ≤ 8; white vs black Hamming > 8
- `compat/SnapshotRegressionTest.kt` (Robolectric, parameterized) — loads snapshot JSON; FakeAccessibilityNode via MockK; InstagramCompat/TikTokCompat/YouTubeCompat extract caption/creator/labelText
- `privacy/NetworkInterceptorTest.kt` (Robolectric) — `Socket.setSocketImplFactory` (URLStreamHandlerFactory fallback) installed once in `@BeforeClass`; classificationFlow=0, sessionRecording=0, reportExport=0, signatureSync>0 against MockWebServer. **Canonical metric: total socket-creation counter.**
- `perf/MemoryBudgetTest.kt` (Robolectric) — synthetic pre-scan+live flow. **Canonical metric: `Debug.MemoryInfo.totalPss` (KB) when Robolectric returns non-zero, else `Runtime.totalMemory()` heap delta in KB.** Threshold: < 150 × 1024 KB.
- `perf/PerfBenchmarkSuite.kt` (Robolectric) — N=200 invocations; writes `app/build/perf/perf-report.json` with all medians; only `medianClassificationLatencyMs` enforced by `perfGateCheck`
- `perf/CounterOverlayFpsTest.kt` (Robolectric) — dedicated 1000-emission benchmark on `AdCounterManager.classifiedItems`; asserts the wall-time ≤ 100 ms AND that no main-dispatcher dispatch blocks (uses `TestDispatcher.scheduler.currentTime` delta as the zero-fps proxy)

## Integration tests under `app/src/androidTest/java/com/scrollshield/`

- `integration/PreScanIntegrationTest.kt` — full cycle < 6 s for 10 items
- `integration/LiveSkipIntegrationTest.kt` — swipe → shouldSkip → SkipFlashOverlay; < 500 ms; flash 200 ± 20 ms
- `integration/LookaheadExtensionIntegrationTest.kt` — extend at bufferRemaining==3
- `integration/BufferExhaustionIntegrationTest.kt` — userHead > scanHead → LoadingOverlay → rerun
- `integration/ChildProfileEnforcementTest.kt` — 50 child-safety items → SKIP_CHILD; 90-min budget → CHILD_HARD_STOP
- `integration/SessionRecordingTest.kt` — background → endedNormally=true; force-kill variant → endedNormally=false
- `integration/VisualPipelineIntegrationTest.kt` — 3 screenshots, real visual TFLite, median < 60 ms, full pipeline < 100 ms
- `integration/MediaProjectionDenialTest.kt` — screenCapture=null → tier 0 or 2, no crash
- `integration/ThermalThrottleTest.kt` — mock PowerManager THERMAL_STATUS_SEVERE → tier 0 UNKNOWN × 10
- `perf/MemoryBudgetInstrumentedTest.kt` — real `Debug.getMemoryInfo()`; **canonical metric: `totalPss`**; threshold `< 150 * 1024` KB
- `perf/MacrobenchmarkPlaceholder.kt` — AndroidX Benchmark driver; not required for CI gate

### androidTest assets
- `dataset/text_feed_200.jsonl`, `dataset/text_child_safety_50.jsonl` — copies of JVM fixtures (byte-identical)
- `screenshots/{official_ad,organic,educational}_sample.png` — deterministic copies from `ml/dataset/screenshots/`

## `app/src/test/java/com/scrollshield/testdata/SyntheticFeedGenerator.kt`

Deterministic Kotlin generator using `Random(20260515)`; emits FeedItem + expectedClassification/Topic; emits identical JSONL bytes as Python `generate_text_data.py`.

## `ml/requirements.txt`

```
torch==2.1.2
transformers==4.36.2
tokenizers==0.15.0
tensorflow==2.14.1
numpy==1.26.3
datasets==2.16.1
scikit-learn==1.3.2
onnx==1.15.0
onnx-tf==1.10.0
tflite-support==0.4.4
sentencepiece==0.1.99
```

## `ml/train_classifier.py` (full rewrite)

CLI: `--epochs` (5), `--smoke`, `--data` (`dataset/text_train.jsonl`), `--out` (`checkpoints/text_classifier.pt`), `--seed` 20260515, `--batch-size` 16, `--lr` 5e-5.

Dataset: JSONL `{caption, classification, topic_vector[20]}`. Tokenizer: `AutoTokenizer.from_pretrained("prajjwal1/bert-tiny")` (4L/128H, documented substitution for distilbert-tiny). Model: BertModel + `Linear(128,7)` softmax + `Linear(128,20)` sigmoid. Loss: `0.7*CrossEntropyLoss + 0.3*MSELoss`. Optimizer: AdamW at `args.lr`, `CosineAnnealingLR(T_max=epochs)`, gradient clipping 1.0, 10 % eval split each epoch. Dumps `app/src/main/assets/wordpiece_vocab.txt`. Checkpoint: `torch.save({state_dict, config, tokenizer_name}, args.out)`. Smoke run ≤ 60 s on CPU.

## `ml/export_tflite.py` (full rewrite)

CLI: `--input`, `--output` (default `../app/src/main/assets/scrollshield_text_classifier.tflite`), `--calibration` (`dataset/text_eval.jsonl`), `--max-tokens` 128.

1. Load PyTorch → wrap as `forward(input_ids, attention_mask) -> (logits_7, topic_20)`
2. ONNX export: `int32[1,128]` × 2 → `float32[1,7]` + `float32[1,20]`, opset 13, dynamic batch
3. `onnx_tf.backend.prepare(...).export_graph(...)` → SavedModel
4. TFLite int8 with `representative_dataset` over 100 calibration samples; `supported_ops=[TFLITE_BUILTINS_INT8]`; `inference_input_type=int32`; `inference_output_type=float32`
5. Validation: 50 inferences via `tf.lite.Interpreter`; assert median < 50 ms (non-zero exit otherwise)
6. Rename guard: delete legacy `scrollshield_classifier.tflite` if present

## `ml/dataset/`

- `text_train.jsonl` — 2000 deterministic captions
- `text_eval.jsonl` — byte-identical to `app/src/test/resources/dataset/text_feed_200.jsonl`
- `text_child_safety.jsonl` — byte-identical to `app/src/test/resources/dataset/text_child_safety_50.jsonl`
- `generate_text_data.py` — deterministic generator (seed 20260515)

## `ml/eval/`

- `evaluate_text.py` — CLI `--tflite`, `--dataset`; per-class precision/recall/F1 via sklearn; median end-to-end latency; writes `_artifacts/text_eval_report.json`; accuracy gating only with `--enforce`

`evaluate_visual.py` is **untouched**.

## Smoke TFLite asset

After `train_classifier.py --smoke && export_tflite.py`, the asset lands at `app/src/main/assets/scrollshield_text_classifier.tflite`. A copy is placed at `app/src/test/resources/assets/scrollshield_text_classifier.tflite`. `ContentClassifierTest` gracefully handles a missing file (UNKNOWN, 0.0 fallback).

## Implementation Instructions (verbatim from problem.md)

```
cd /home/devuser/ScrollShield2
./gradlew --no-daemon clean
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:perfGateCheck || (echo "FAIL: classification latency gate" && exit 1)
cd ml
python -m pip install -r requirements.txt
python train_classifier.py --epochs 1 --smoke
python export_tflite.py --input checkpoints/text_classifier.pt --output ../app/src/main/assets/scrollshield_text_classifier.tflite
python eval/evaluate_text.py --tflite ../app/src/main/assets/scrollshield_text_classifier.tflite --dataset dataset/text_eval.jsonl
test -f ../app/src/main/assets/scrollshield_text_classifier.tflite
python -c "import os,sys; sz=os.path.getsize('../app/src/main/assets/scrollshield_text_classifier.tflite'); sys.exit(0 if sz < 20*1024*1024 else 1)"
cd ..
echo "WI-15 implementation: OK"
```
