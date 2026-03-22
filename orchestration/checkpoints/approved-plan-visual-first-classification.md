Now I have a thorough understanding of all files. Let me produce the detailed plan.

# Improvement Plan — Iteration 2

## Overview

The current pipeline is `Tier 1: Signature Match (SimHash) -> Tier 2: Label Detection -> Tier 3: Content Analysis (DistilBERT on text)`. All three tiers operate on text extracted from the accessibility tree. Source apps can defeat this by renaming accessibility node IDs, removing text labels, or obfuscating captions.

The revised pipeline places **visual/image-based classification via MediaProjection screen capture** as the primary detection tier. Text-based methods become supplementary fast-path signals that short-circuit the pipeline when available but are never relied upon.

### Revised Pipeline Architecture

```
Tier 0 (Fast-Path) — Text Signals        < 20ms   [supplementary, short-circuit only]
  ├── Tier 0a: Signature Match (SimHash)   < 5ms
  └── Tier 0b: Label Detection             < 15ms

Tier 1 (Primary) — Visual Classification  < 80ms   [PRIMARY detection path]
  └── On-device MobileNetV3-Small on screen capture from MediaProjection

Tier 2 (Deep Text) — Content Analysis     < 50ms   [supplementary, runs if Tier 1 inconclusive]
  └── DistilBERT-tiny on tokenized text
```

### Latency Budget (Snapdragon 7-series, mid-range Android)

| Stage | Target | Notes |
|-------|--------|-------|
| Tier 0a: SimHash lookup | < 5ms | Unchanged |
| Tier 0b: Label detection | < 15ms | Unchanged |
| MediaProjection frame capture | < 15ms | Single frame via ImageReader |
| Bitmap crop + resize to 224x224 | < 5ms | GPU-accelerated on RenderScript/Bitmap API |
| Tier 1: MobileNetV3-Small inference | < 60ms | TFLite with NNAPI delegate on NPU |
| Tier 2: DistilBERT-tiny inference | < 50ms | Only if Tier 1 inconclusive |
| Skip decision | < 1ms | Unchanged |
| **Full pipeline (Tier 0 miss + Tier 1)** | **< 100ms** | Primary path |
| **Full pipeline (all tiers)** | **< 150ms** | Worst case |

---

## Detailed Pre-Scan Capture-Classify-Advance Cycle Timing

**(Addresses Reviewer Criterion 4: Pre-scan integration)**

### Revised Cycle Per Item During Pre-Scan

```
1. Gesture dispatch (swipe)        100ms   (gestureDurationMs)
2. Wait for app to settle          100ms   (half of gestureIntervalMs — reduced from 200ms wait)
3. Capture frame via ImageReader    15ms   
4. Classify: Tier 0 attempt         20ms   (may short-circuit here)
5. If Tier 0 miss: Tier 1 visual    60ms   
6. Total per item (Tier 0 hit):   ~235ms
7. Total per item (Tier 1 needed): ~295ms
```

### Revised gestureIntervalMs

The existing `gestureIntervalMs = 200` was the pause between gesture completion and the next gesture. With visual capture added, we restructure the cycle:

- **Gesture duration**: 100ms (unchanged)
- **Settle delay**: 100ms (reduced from 200ms — the visual capture itself provides the remaining wait since we capture after settle)
- **Capture + classify**: 15ms + 60ms = 75ms (overlaps with content loading)
- **Total per-item cycle**: ~295ms worst case (vs. old 260ms)

### Revised Pre-Scan Duration Math

```
10 items x 295ms = 2,950ms  (~3.0s forward scan)
+ 10 items x 200ms rewind   = 2,000ms  (~2.0s rewind)
+ Loading overlay setup      =   500ms
= Total: ~5.5 seconds
```

This is within the existing `< 6 seconds` acceptance criterion. The `gestureIntervalMs` constant is **not** increased — instead, the capture+classify time fills the existing inter-gesture gap. The loading overlay does **not** block the capture because the overlay is rendered on a separate window layer; MediaProjection captures the underlying app's window.

### Capture Timing Detail

1. After each swipe gesture completes (callback from `dispatchGesture`), wait 100ms for the app to render the new content.
2. Acquire the latest frame from `ImageReader.acquireLatestImage()`. The `ImageReader` is connected to the `MediaProjection` virtual display.
3. If the frame is identical to the previous frame (compare first 1KB of pixel data), wait an additional 50ms and retry once — handles apps that debounce rendering.
4. Convert `Image` to `Bitmap`, crop to content region (exclude status bar, navigation bar), resize to 224x224.
5. Run Tier 0 (text-based fast path) in parallel with Tier 1 (visual) — whichever returns a high-confidence result first wins.
6. Store result in ScanMap, advance to next item.

---

## Training Data Specification

**(Addresses Reviewer Criterion 5: Training data)**

### Dataset Requirements for MobileNetV3-Small Fine-Tuning

**Target classes** (7 visual classes, matching existing Classification enum):
1. `ORGANIC` — normal user-generated content
2. `OFFICIAL_AD` — platform-labeled sponsored content with branded overlays, CTA buttons, "Shop Now"/"Learn More" buttons, product cards
3. `INFLUENCER_PROMO` — influencer content with product placement, discount codes, affiliate links visible on screen
4. `ENGAGEMENT_BAIT` — content with manipulative visual patterns (fake play buttons, misleading thumbnails, ragebait text overlays)
5. `OUTRAGE_TRIGGER` — content with inflammatory visual elements (all-caps text overlays, red warning graphics)
6. `EDUCATIONAL` — educational content (diagrams, tutorials, lecture-style framing)
7. `UNKNOWN` — ambiguous/unclassifiable

**Minimum dataset size**: 15,000 labeled screenshots total
- ORGANIC: 5,000 (largest class, high variance)
- OFFICIAL_AD: 3,000 (includes platform variations)
- INFLUENCER_PROMO: 2,500
- ENGAGEMENT_BAIT: 1,500
- OUTRAGE_TRIGGER: 1,500
- EDUCATIONAL: 1,000
- UNKNOWN: 500

**Data acquisition strategy**:
1. **Agent fleet screenshots** (primary source, ~60% of data): Use the doomscrolling agent fleet described in Section 6 of the proposal. Each agent captures full-screen screenshots at each feed position. Agents span TikTok, Instagram Reels, YouTube Shorts across demographic profiles.
2. **Manual annotation** (~25% of data): Hire 3-5 annotators via Labelbox or Scale AI. Annotators label screenshots with class + bounding boxes for ad indicators (CTA buttons, "Sponsored" badges, product cards). Inter-annotator agreement target: Cohen's kappa > 0.85.
3. **Synthetic augmentation** (~15% of data): Apply standard augmentations (rotation ±5°, brightness ±20%, compression artifacts, resolution scaling 0.5x-1.5x) to simulate varying device quality. Also generate synthetic ad overlays on organic content screenshots to increase INFLUENCER_PROMO diversity.

**Annotation schema**:
```json
{
  "image_id": "string",
  "source_app": "tiktok|instagram|youtube",
  "classification": "ORGANIC|OFFICIAL_AD|INFLUENCER_PROMO|...",
  "confidence": 0.0-1.0,
  "ad_indicators": [
    {"type": "cta_button", "bbox": [x, y, w, h]},
    {"type": "sponsored_label", "bbox": [x, y, w, h]},
    {"type": "product_card", "bbox": [x, y, w, h]}
  ],
  "platform_version": "string",
  "capture_timestamp": "ISO8601",
  "annotator_id": "string"
}
```

**Platform distribution**: TikTok 40%, Instagram 30%, YouTube 30% — weighted toward TikTok as the primary target.

**Validation split**: 70% train / 15% validation / 15% test. Stratified by class and platform.

**Fine-tuning approach**:
- Base model: MobileNetV3-Small pretrained on ImageNet
- Transfer learning: freeze first 10 layers, fine-tune remaining layers + new classification head
- Input: 224x224 RGB
- Output: 7-class softmax + 20-dim topic vector (multi-head)
- Training: 50 epochs, batch size 32, learning rate 1e-4 with cosine annealing
- Hardware: single GPU (V100 or equivalent), ~4 hours training time
- Target metrics: F1 > 0.85 OFFICIAL_AD, F1 > 0.75 INFLUENCER_PROMO (matching existing text-based targets)

---

## Changes to `docs/technical-spec.md`

### 1. Architecture Diagram (line 31)

Replace:
```
│   │     Classification Pipeline          │    │
│   │     (Signature → Label → ML)         │    │
```
With:
```
│   │     Classification Pipeline          │    │
│   │     (Visual-First + Text Signals)    │    │
```

### 2. Architecture — add MediaProjection to diagram (after line 43)

Add a new box between Classification Pipeline and Feed Interception Service:
```
│   ┌──────────────────────────────────────┐    │
│   │     Screen Capture Service           │    │
│   │     (MediaProjection + ImageReader)   │    │
│   └──────────────────────────────────────┘    │
```

### 3. Constraints section (line 61)

Change:
```
- Target latency for classification pipeline: < 60ms per item.
```
To:
```
- Target latency for classification pipeline: < 100ms per item (visual primary path); < 150ms worst case (all tiers).
```

### 4. FeedItem data model (after line 101, add new field)

Add field to `FeedItem`:
```kotlin
val screenCapture: Bitmap?,        // Screen capture from MediaProjection (null if capture unavailable)
```

### 5. ClassifiedItem data model (line 113)

Change the `tier` field comment:
```kotlin
val tier: Int,                 // Which tier classified it (0 = text fast-path, 1 = visual, 2 = deep text)
```

### 6. Module 2: Classification Pipeline — complete rewrite (lines 336-409)

Replace the entire Module 2 section with:

```markdown
## Module 2: Classification Pipeline

**What**: Visual-first pipeline that classifies each FeedItem. Screen capture via MediaProjection is the primary detection path. Text-based methods are supplementary fast-path signals.

### Pipeline Architecture

```
Tier 0 (Fast-Path) — Text Signals        < 20ms   [supplementary, short-circuit only]
  ├── Tier 0a: Signature Match (SimHash)   < 5ms
  └── Tier 0b: Label Detection             < 15ms

Tier 1 (Primary) — Visual Classification  < 80ms   [PRIMARY detection path]
  └── MobileNetV3-Small on screen capture

Tier 2 (Deep Text) — Content Analysis     < 50ms   [supplementary, runs if Tier 1 inconclusive]
  └── DistilBERT-tiny on tokenized text
```

### Tier 0a — Signature Match (< 5ms) [Supplementary Fast-Path]

- 64-bit SimHash of normalised caption against local `ad_signatures` table.
- Normalisation rules: lowercase, strip emoji, collapse whitespace, remove URLs, remove @mentions.
- Match criteria: Hamming distance ≤ 3 bits.
- If match with confidence > 0.95, short-circuit — return immediately without visual classification.
- Expected catch: 40–60% of known ads (text available and signature cached).
- **Evasion risk**: Apps can rename/remove text nodes. This tier is opportunistic, not relied upon.

### Tier 0b — Label Detection (< 15ms) [Supplementary Fast-Path]

- Check `FeedItem.labelText` against known patterns (case-insensitive): `{"Sponsored", "Ad", "Paid partnership", "Promoted", "Anzeige", "Sponsorisé", "Sponsorizzato", "Reklame", "広告", "광고", "Patrocinado", "Реклама", "إعلان"}`.
- If match: `OFFICIAL_AD`, confidence 1.0, short-circuit.
- **Evasion risk**: Apps can rename or remove accessibility labels. This tier catches legally-required labels but is not relied upon.

### Tier 1 — Visual Classification (< 80ms) [PRIMARY]

- **Screen capture**: Acquire frame from `MediaProjection` via `ImageReader`. Crop to content region, resize to 224×224 RGB.
- **Model**: MobileNetV3-Small (~3.4MB TFLite, int8 quantization), fine-tuned on social media ad screenshots.
- **Input tensor**: `uint8[1][224][224][3]` — RGB pixel values.
- **Output tensors**:
  - `float32[1][7]` — 7-class probability vector (maps to Classification enum)
  - `float32[1][20]` — 20-dimensional topic vector (maps to TopicCategory)
- **Inference**: TFLite with NNAPI delegate for NPU acceleration on supported devices. Falls back to CPU if NNAPI unavailable.
- **Latency target**: < 60ms inference on Snapdragon 7-series (confirmed feasible: MobileNetV3-Small achieves ~40ms on Snapdragon 695 with NNAPI).
- Max probability < 0.7 → fall through to Tier 2 if text is available, otherwise `UNKNOWN`.
- **Error handling**: If MediaProjection is not granted or capture fails, skip Tier 1 and proceed to Tier 2.

### Tier 2 — Content Analysis (< 50ms) [Supplementary Deep Text]

- Runs only if Tier 0 did not short-circuit AND Tier 1 returned inconclusive (max probability < 0.7).
- On-device TFLite model (`scrollshield_text_classifier.tflite`), float16 quantization.
- Architecture: DistilBERT-tiny (4L/128H, ~15MB).
- Input/output unchanged from original spec.
- **Error handling**: Catch all exceptions → return `UNKNOWN` with confidence 0.0.

### Pipeline Router (`ClassificationPipeline.kt`)

```kotlin
suspend fun classify(feedItem: FeedItem, profile: UserProfile): ClassifiedItem {
    // Tier 0a — text fast-path (supplementary)
    val t0a = signatureMatcher.match(feedItem)
    if (t0a != null && t0a.confidence > 0.95) return t0a.withTier(0).withSkipDecision(profile)

    // Tier 0b — label fast-path (supplementary)
    val t0b = labelDetector.detect(feedItem)
    if (t0b != null) return t0b.withTier(0).withSkipDecision(profile)

    // Tier 1 — visual classification (PRIMARY)
    val t1 = visualClassifier.classify(feedItem)
    if (t1 != null && t1.confidence >= 0.7) return t1.withTier(1).withSkipDecision(profile)

    // Tier 2 — deep text analysis (supplementary fallback)
    val t2 = contentClassifier.classify(feedItem)
    return t2.withTier(2).withSkipDecision(profile)
}
```

### Thermal Throttling Fallback

If device is overheating, skip Tier 1 visual classification (GPU/NPU intensive) and Tier 2 — rely on Tier 0 only.

### Dual-OCR Strategy

Unchanged — still applies when text extraction from accessibility tree fails. OCR extracts text for Tier 0 and Tier 2; it is not needed for Tier 1 visual classification.
```

### 7. File Structure (lines 786-793)

Replace classification section:
```
│   ├── classification/
│   │   ├── ClassificationPipeline.kt       # Visual-first pipeline router
│   │   ├── SignatureMatcher.kt             # Tier 0a (supplementary fast-path)
│   │   ├── LabelDetector.kt               # Tier 0b (supplementary fast-path)
│   │   ├── VisualClassifier.kt            # Tier 1 (PRIMARY — MobileNetV3-Small)
│   │   ├── ContentClassifier.kt           # Tier 2 (supplementary deep text)
│   │   ├── ScreenCaptureManager.kt        # MediaProjection frame acquisition
│   │   └── SkipDecisionEngine.kt          # Computes skip/show per profile
```

Also add under `app/src/main/assets/`:
```
│   ├── scrollshield_visual_classifier.tflite    # MobileNetV3-Small visual model
│   └── scrollshield_text_classifier.tflite      # DistilBERT-tiny text model (renamed)
```

### 8. Technology Stack table (lines 910-912)

Change the ML-related rows:
```
| On-device ML (visual) | TensorFlow Lite + MobileNetV3-Small | 2.14+ | Primary visual classification, NPU-accelerated |
| On-device ML (text) | TensorFlow Lite + DistilBERT-tiny | 2.14+ | Supplementary text classification |
| Screen capture | MediaProjection API | API 21+ | Visual classification input |
```

### 9. Memory Budget table (lines 978-989)

Update:
```
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
| **Total** | **~150MB** |
```

Note: Total stays at 150MB. The MobileNetV3-Small model is much smaller than DistilBERT (~4MB vs ~15MB). The MediaProjection frame buffer adds ~3MB. Headroom reduced from 74MB to 59MB — still adequate.

### 10. Pre-scan duration (line 494)

Change:
```
**Expected pre-scan duration**: 10 items × (200ms gesture interval + 60ms classification) = ~2.6 seconds, plus ~2 seconds for the rewind. Total: **~5 seconds**.
```
To:
```
**Expected pre-scan duration**: 10 items × (~295ms per item: 100ms gesture + 100ms settle + 15ms capture + 80ms classify) = ~3.0 seconds, plus ~2.0 seconds for the rewind, plus ~0.5s overlay setup. Total: **~5.5 seconds**. Masked by the loading animation.
```

### 11. Acceptance criteria for Module 2 (lines 401-408)

Replace with:
```
**Acceptance criteria**:

- Tier 0a: < 5ms with 100K entries.
- Tier 0b: identifies all listed localised labels.
- Tier 1 (visual): model loads < 1s cold start, inference < 60ms on Snapdragon 7-series, captures frame < 15ms.
- Tier 2 (text): loads < 2s cold start, inference < 50ms on Snapdragon 7-series.
- Full pipeline (visual path): < 100ms.
- Full pipeline (all tiers): < 150ms worst case.
- F1: > 85% OFFICIAL_AD, > 75% INFLUENCER_PROMO on visual test set.
- Skip decision computed in < 1ms.
- EDUCATIONAL items never auto-skipped.
- Tier 1/Tier 2 failure gracefully falls back to lower tiers or UNKNOWN/SHOW_LOW_CONF.
- MediaProjection permission denial degrades gracefully to text-only classification.
```

### 12. Open Questions (add new question 13)

Add after Open Question 12:
```
13. **Visual model accuracy across devices**: Screen capture resolution and color profile vary across Android devices. The visual classifier must be robust to resolution differences (720p to 1440p), color temperature variation, and night mode filters. *Resolution strategy*: Include resolution/color augmentation in training data. Test on 5 representative devices spanning 720p to 1440p.
```

### 13. Pipeline description in "Constraints" section (line 61)

Add new constraint:
```
- Visual-first classification: Screen capture via MediaProjection is the primary classification method. Text-based detection (SimHash, label matching, DistilBERT) is supplementary and not relied upon — source apps can defeat text-based detection by renaming accessibility node IDs or removing labels.
```

### 14. Alignment with Proposal section (after line 1094)

Add:
```
- **Visual-first classification**: The demo proposal's "Content analysis: On-device vision and language models identify product placements, brand logos, discount codes, and call-to-action patterns" is now the primary detection path (Tier 1 visual), not a secondary method. This aligns with the proposal's intent of using "on-device vision models" and the "Neural Engine" for feed interception.
```

---

## Changes to Work Items

### WI-01: Project Scaffolding & Build Configuration

**Section: `app/build.gradle.kts` Dependencies** (lines 60-73)

Add new dependency:
```
| MediaProjection API | (system API, no external dep) |
```

**Section: `accessibility_service_config.xml`** — no change needed (MediaProjection is separate from accessibility service).

**Section: AndroidManifest.xml** (lines 97-104)

Add permission:
```
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` (required for MediaProjection on API 34+)
```

Change the comment for `FOREGROUND_SERVICE`:
```
- `FOREGROUND_SERVICE` (MediaProjection screen capture — primary classification path)
```

**Section: DI modules** (line 95)

Add to `ClassificationModule.kt` description:
```
- `ClassificationModule.kt`: provide TFLite interpreters (visual + text models), MediaProjection manager, pipeline components
```

**Section: Milestones** (lines 108-112)

Update milestone mapping:
```
- **MVP** (original steps 1-5): WI-01, WI-02, WI-03, WI-04, WI-05, WI-06 (Tier 0b + Skip Decision), WI-08, WI-16
- **V1** (original steps 1-9): MVP + WI-06 (full pipeline with Tier 1 visual), WI-07, WI-09, WI-10, WI-11
- **V1.1** (original steps 10-12): V1 + WI-12, WI-13, WI-14, WI-15, WI-17, WI-18
```

**Section: Files to Create / Modify** (line 28)

Add:
```
- `app/src/main/java/com/scrollshield/di/MediaProjectionModule.kt`
```

### WI-02: Data Models & Enums

**Section: FeedItem** (lines 28-43)

Add new field after `detectedDurationMs`:
```kotlin
val screenCapture: Bitmap?,        // Screen capture from MediaProjection, null if unavailable
```

Update the field count comment from "12 fields" to "13 fields".

**Section: ClassifiedItem** (lines 48-59)

Change `tier` field comment:
```kotlin
val tier: Int,                 // Which tier classified it (0 = text fast-path, 1 = visual, 2 = deep text)
```

**Section: AdSignature** (lines 162-175)

Add new field after `locale`:
```kotlin
val visualHash: String?            // Perceptual hash of ad screenshot for visual signature matching
```

**Section: Acceptance Criteria** (lines 201-207)

Add:
```
- FeedItem has 13 fields including `screenCapture`
- AdSignature includes `visualHash` field
- ClassifiedItem `tier` field documents tiers 0, 1, 2
```

### WI-03: Database, DAOs & Preferences Store

**Section: UserPreferencesStore** (lines 116-118)

Add to the stored preferences list:
```
- Stores: active profile ID, onboarding completed flag, feature toggles, advanced settings (CPM overrides, buffer sizes, status dot thresholds), **mediaProjectionGranted flag**, **visualClassificationEnabled flag** (default: true)
```

**Section: SignatureDao** (lines 77-94)

Add new query method:
```kotlin
@Query("SELECT visualHash FROM ad_signatures WHERE visualHash IS NOT NULL AND expires > :now")
suspend fun getActiveVisualHashes(now: Long): List<String>
```

**Section: Acceptance Criteria** (lines 125-132)

Add:
```
- `mediaProjectionGranted` and `visualClassificationEnabled` preferences persist across app restarts
- `getActiveVisualHashes()` returns only non-null, non-expired visual hashes
```

**Dependencies** — unchanged (still Hard: WI-02, Integration: WI-01).

### WI-04: Utility Classes

**Section: Files to Create / Modify** (lines 19-24)

Add:
```
- `app/src/main/java/com/scrollshield/util/PerceptualHash.kt`
```

**Section: Detailed Specification** — add new subsection after FeedFingerprint:

```markdown
### PerceptualHash
- Compute perceptual hash (pHash) of a Bitmap for visual signature matching
- Algorithm: resize to 32×32 grayscale, compute DCT, extract top-left 8×8 coefficients, threshold at median → 64-bit hash
- Comparison: Hamming distance function (reuses `SimHash.hammingDistance()`)
- Match threshold: Hamming distance ≤ 8 bits (more permissive than text SimHash due to visual variation)
- Performance target: < 5ms per image

```kotlin
fun perceptualHash(bitmap: Bitmap): Long
fun visualMatch(a: Long, b: Long, threshold: Int = 8): Boolean
```
```

**Section: Acceptance Criteria** (lines 64-71)

Add:
```
- PerceptualHash produces consistent 64-bit hashes for identical images
- PerceptualHash produces similar hashes (Hamming distance ≤ 8) for visually similar images with different resolutions
- PerceptualHash < 5ms per image
```

**Section: Context** (line 12)

Update to mention visual classification:
```
These utilities are used across multiple modules. SimHash powers Tier 0a text-based signature matching. PerceptualHash powers visual signature matching in Tier 1. CosineSimilarity is used for interest vector comparison. TextNormaliser prepares text for hashing and text-based classification. The lastValidatedHash algorithm validates ScanMap reuse on app re-entry.
```

**Section: Goal** (line 9)

Update:
```
Implement SimHash, PerceptualHash, CosineSimilarity, TextNormaliser, and the lastValidatedHash algorithm.
```

**Dependencies** — unchanged.

### WI-05: Feed Interception Service

**Section: Context** (line 14)

Add after the existing context paragraph:
```
The service also manages the MediaProjection session for screen capture. MediaProjection provides the visual input for Tier 1 (primary) visual classification. The service acquires frames via ImageReader and passes them to the classification pipeline alongside the FeedItem extracted from the accessibility tree.
```

**Section: Core Capabilities** — add new capability after item 7:

```markdown
8. **Screen capture**: Manage `MediaProjection` session and `ImageReader` for visual classification input.
   - Acquire `MediaProjection` token via `MediaProjectionManager.createScreenCaptureIntent()` (one-time user permission during onboarding).
   - Create `VirtualDisplay` connected to `ImageReader` at device resolution.
   - On each feed item encounter: call `ImageReader.acquireLatestImage()`, convert to `Bitmap`, attach to `FeedItem.screenCapture`.
   - Frame capture budget: < 15ms.
   - If MediaProjection not granted: `FeedItem.screenCapture = null`, pipeline proceeds with text-only classification (Tier 0 + Tier 2).
```

**Section: Fallback Strategy** (lines 69-72)

Replace:
```
If accessibility tree is too shallow:
1. Screen-capture OCR via `MediaProjection` API (one-time user permission). Requires foreground service notification (persistent, low-priority). Uses ML Kit Text Recognition as primary OCR engine.
2. If OCR insufficient: degrade to label-only detection (Tier 2).
```
With:
```
MediaProjection is now the **primary** screen capture mechanism for visual classification (Tier 1). It is always active when granted, not just as a fallback.

If accessibility tree is too shallow for text extraction:
1. Visual classification (Tier 1) continues normally — it does not depend on the accessibility tree.
2. Text-based tiers (Tier 0, Tier 2) degrade: OCR via ML Kit / Tesseract extracts text from the screen capture for Tier 0/Tier 2 input.
3. If OCR also fails: rely on Tier 1 visual classification alone.

If MediaProjection is not granted:
1. Fall back to text-only classification (Tier 0 + Tier 2). Tier 1 visual is unavailable.
2. Show persistent notification: "ScrollShield visual protection unavailable — grant screen capture for full protection."
```

**Section: Dependencies** (line 17)

Update:
```
- **Hard**: WI-02 (FeedItem model including screenCapture field), WI-04 (FeedFingerprint for lastValidatedHash)
- **Integration**: WI-01 (manifest declares service + MediaProjection permission), WI-09 (pre-scan uses gesture dispatch), WI-16 (ScreenCaptureManager for frame acquisition)
```

**Section: Acceptance Criteria** (lines 79-89)

Add:
```
- MediaProjection frame capture completes in < 15ms
- FeedItem.screenCapture populated when MediaProjection is granted
- FeedItem.screenCapture is null (not crash) when MediaProjection is not granted
- Foreground service notification displayed during MediaProjection session
```

### WI-06: Classification Pipeline

**This is the most heavily modified work item.** Replace the Goal, Context, Files, Detailed Specification, and Acceptance Criteria sections.

**Section: Goal** (line 9)

Replace:
```
Implement the three-tier classification pipeline (Signature Match -> Label Detection -> Content Analysis) and the skip decision engine.
```
With:
```
Implement the visual-first classification pipeline (Text Fast-Path -> Visual Classification -> Deep Text Analysis) and the skip decision engine. Screen capture via MediaProjection is the primary detection path; text-based methods are supplementary fast-path signals.
```

**Section: Context** (lines 12-19)

Replace:
```
The classification pipeline is the shared intelligence layer consumed by both features. Both the Ad Counter and Scroll Mask depend on it.
```
With:
```
The classification pipeline is the shared intelligence layer consumed by both features. Both the Ad Counter and Scroll Mask depend on it. The pipeline uses a visual-first architecture: on-device image classification of screen captures is the primary detection method, resistant to evasion by source apps. Text-based methods (SimHash, label matching, DistilBERT) serve as supplementary fast-path signals that can short-circuit the pipeline when text is available but are not relied upon as the primary detection method.
```

**Section: Dependencies** (lines 22-23)

Replace:
```
- **Hard**: WI-02 (ClassifiedItem, Classification, SkipDecision, AdSignature), WI-04 (SimHash, TextNormaliser)
- **Integration**: WI-03 (SignatureDao for Tier 0a lookups), WI-05 (FeedItem events from service including screenCapture), WI-16 (ScreenCaptureManager, VisualClassifier)
```

**Section: Files to Create / Modify** (lines 26-33)

Replace with:
```
- `app/src/main/java/com/scrollshield/classification/ClassificationPipeline.kt`
- `app/src/main/java/com/scrollshield/classification/SignatureMatcher.kt` (Tier 0a)
- `app/src/main/java/com/scrollshield/classification/LabelDetector.kt` (Tier 0b)
- `app/src/main/java/com/scrollshield/classification/VisualClassifier.kt` (Tier 1 — NEW, PRIMARY)
- `app/src/main/java/com/scrollshield/classification/ContentClassifier.kt` (Tier 2 — supplementary)
- `app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt` (NEW)
- `app/src/main/java/com/scrollshield/classification/SkipDecisionEngine.kt`
- `app/src/main/assets/scrollshield_visual_classifier.tflite` (MobileNetV3-Small, ~3.4MB)
- `app/src/main/assets/scrollshield_text_classifier.tflite` (DistilBERT-tiny, ~15MB, renamed from scrollshield_classifier.tflite)
```

**Section: Detailed Specification** — complete replacement:

```markdown
### Tier 0a — Signature Match (< 5ms) [Supplementary Fast-Path]
- Compute 64-bit SimHash of normalised caption (using TextNormaliser + SimHash from WI-04)
- Compare against local `ad_signatures` table
- Match criteria: Hamming distance <= 3 bits
- If match with confidence > 0.95, short-circuit — return immediately
- Also check visual signature: compute PerceptualHash of screenCapture, compare against `visualHash` column
- Visual match criteria: Hamming distance <= 8 bits
- If either text or visual signature matches: short-circuit
- Expected catch: 40-60% of known ads
- **Evasion resilience**: Text signatures can be evaded; visual signatures based on rendered pixels are harder to evade

### Tier 0b — Label Detection (< 15ms) [Supplementary Fast-Path]
- Check `FeedItem.labelText` against known patterns (case-insensitive): unchanged list
- If match: `OFFICIAL_AD`, confidence 1.0, short-circuit
- **Evasion risk**: Apps can rename/remove labels. Legally required but not guaranteed.

### Tier 1 — Visual Classification (< 80ms) [PRIMARY]
- **Screen capture input**: `FeedItem.screenCapture` Bitmap from MediaProjection (provided by WI-05/WI-16)
- **Preprocessing**: Crop to content region (exclude status bar top 24dp, navigation bar bottom 48dp), resize to 224×224 RGB using bilinear interpolation. < 5ms.
- **Model**: MobileNetV3-Small, int8 quantization, ~3.4MB TFLite file
- **Input tensor**: `uint8[1][224][224][3]`
- **Output tensors**:
  - `float32[1][7]` — 7-class probability vector (maps to Classification enum including UNKNOWN)
  - `float32[1][20]` — 20-dimensional topic vector (maps to TopicCategory)
- **Inference**: TFLite with NNAPI delegate. Fallback to XNNPack CPU delegate if NNAPI unavailable.
- **Latency**: < 60ms inference on Snapdragon 7-series (MobileNetV3-Small benchmarks at ~40ms with NNAPI on Snapdragon 695)
- Max probability < 0.7 → inconclusive, fall through to Tier 2
- EDUCATIONAL classification is never auto-skipped regardless of profile settings
- **Error handling**: If screenCapture is null (MediaProjection not granted) or inference fails, skip Tier 1, proceed to Tier 2.
- **What it detects from pixels**: ad creative patterns (product overlays, branded frames), CTA buttons ("Shop Now", "Learn More", "Swipe Up"), sponsored badges rendered as pixels, influencer promo indicators (discount code overlays, product placements), engagement bait patterns (fake buttons, misleading thumbnails)

### Tier 2 — Content Analysis (< 50ms) [Supplementary Deep Text]
- Runs only if Tier 0 did not short-circuit AND Tier 1 returned inconclusive (max probability < 0.7)
- On-device TFLite model (`scrollshield_text_classifier.tflite`), float16 quantization
- Architecture: DistilBERT-tiny (4L/128H, ~15MB)
- Tokenizer: WordPiece, max 128 tokens
- Input/output: unchanged from original Tier 3 spec
- **Error handling**: Catch all exceptions → return `UNKNOWN` with confidence 0.0, fail open (`SHOW_LOW_CONF`)

### Skip Decision Engine
Unchanged from original spec.

### Pipeline Router (`ClassificationPipeline.kt`)
```kotlin
suspend fun classify(feedItem: FeedItem, profile: UserProfile): ClassifiedItem {
    // Tier 0a — text signature fast-path
    val t0a = signatureMatcher.match(feedItem)
    if (t0a != null && t0a.confidence > 0.95) return t0a.withTier(0).withSkipDecision(profile)

    // Tier 0b — label fast-path
    val t0b = labelDetector.detect(feedItem)
    if (t0b != null) return t0b.withTier(0).withSkipDecision(profile)

    // Tier 1 — visual classification (PRIMARY)
    if (feedItem.screenCapture != null) {
        val t1 = visualClassifier.classify(feedItem.screenCapture)
        if (t1 != null && t1.confidence >= 0.7) return t1.withTier(1).withSkipDecision(profile)
    }

    // Tier 2 — deep text (supplementary fallback)
    val t2 = contentClassifier.classify(feedItem)
    return t2.withTier(2).withSkipDecision(profile)
}
```

### ScreenCaptureManager
- Manages `MediaProjection` lifecycle and `ImageReader`
- Provides `suspend fun captureFrame(): Bitmap?`
- Frame acquisition: < 15ms
- Handles `MediaProjection` revocation gracefully (returns null)
- Crops to content region, excludes system UI bars

### Thermal Throttling Fallback
If device is overheating, skip Tier 1 (GPU/NPU intensive) and Tier 2 — rely on Tier 0 only.

### Dual-OCR Strategy (when text extraction from accessibility tree fails)
Unchanged — OCR provides text input for Tier 0 and Tier 2. Not needed for Tier 1 visual classification.
```

**Section: Acceptance Criteria** — replace entirely:
```
## Acceptance Criteria
- Tier 0a: < 5ms with 100K entries (text + visual signature matching)
- Tier 0b: identifies all listed localised labels
- Tier 1 (visual, PRIMARY): loads < 1s cold start, inference < 60ms on Snapdragon 7-series, frame capture < 15ms
- Tier 2 (text): loads < 2s cold start, inference < 50ms on Snapdragon 7-series
- Full pipeline (visual path): < 100ms per item
- Full pipeline (all tiers): < 150ms worst case
- F1: > 85% OFFICIAL_AD, > 75% INFLUENCER_PROMO on visual test set
- Skip decision computed in < 1ms
- EDUCATIONAL items never auto-skipped
- Tier 1 failure gracefully falls back to Tier 2
- MediaProjection denial degrades to text-only (Tier 0 + Tier 2)
- Thermal throttle degrades to Tier 0 only
```

**Section: Notes** (lines 121-122)

Replace:
```
- Open Question 5 (Model sizing): DistilBERT-tiny benchmark needed on Pixel 4a. Consider smaller alternatives if inference exceeds 50ms.
- The placeholder TFLite model file should be created in this WI. The actual trained model is produced by WI-15's ML pipeline.
```
With:
```
- Open Question 5 (Visual model sizing): MobileNetV3-Small benchmark needed on Pixel 4a with NNAPI. Consider MobileNetV2 if inference exceeds 60ms on target hardware.
- Two placeholder TFLite model files should be created in this WI: `scrollshield_visual_classifier.tflite` (visual, primary) and `scrollshield_text_classifier.tflite` (text, supplementary). The actual trained models are produced by WI-17 (visual) and WI-15 (text).
```

### WI-07: Profile Management

No changes required. Profile management is not directly affected by the classification pipeline architecture. The `UserProfile` model consumed by the skip decision engine is unchanged.

### WI-08: Ad Counter Feature

**Section: Dependencies** (line 14)

Update:
```
- **Hard**: WI-02 (ClassifiedItem, SessionRecord), WI-05 (lifecycle events, APP_FOREGROUND/BACKGROUND), WI-06 (ClassifiedItem events from visual-first pipeline)
- **Integration**: WI-03 (SessionDao for persistence), WI-07 (active profile for budget thresholds), WI-16 (ScreenCaptureManager provides visual input to pipeline)
```

**Section: Counting Logic** (lines 33-42)

Add a line after "Tracks brands, categories":
```
- Tracks classification tier used (Tier 0 text fast-path vs. Tier 1 visual vs. Tier 2 deep text) for analytics
```

**Section: UI — Summary Card** (lines 58-62)

Add to the "Shows:" list:
```
- Shows: ads detected, ads skipped, ad-to-content ratio, brand pills, category pills, revenue, duration, **detection method breakdown (visual vs. text)**
```

**Section: Acceptance Criteria** (lines 76-87)

Add:
```
- Detection method breakdown accurately reflects Tier 0/1/2 counts
```

### WI-09: Scroll Mask — Pre-Scan & Core

**Section: Context — "Key Insight"** — no change (the pre-scan buffer concept is independent of classification method).

**Section: Pre-Scan Phase** (lines 34-48)

Update step 2:
```
2. **Fast-forward scan**: Call `feedInterceptionService.scrollForwardFast(bufferSize)` where `bufferSize` is configurable (default: 10). For each item:
   - Wait 100ms for app to render new content after gesture
   - Capture screen frame via ScreenCaptureManager (< 15ms)
   - Capture FeedItem via feed interception service (attach screenCapture)
   - Classify via visual-first classification pipeline (Tier 0 → Tier 1 → Tier 2 as needed)
   - Store in ScanMap
```

**Section: Expected pre-scan duration** (line 48)

Replace:
```
**Expected pre-scan duration**: 10 items x (200ms gesture + 60ms classification) = ~2.6s, plus ~2s rewind. Total: **~5 seconds**.
```
With:
```
**Expected pre-scan duration**: 10 items × (~295ms per item: 100ms gesture + 100ms settle + 15ms capture + 80ms classify) = ~3.0s forward scan, plus ~2.0s rewind, plus ~0.5s overlay setup. Total: **~5.5 seconds**. The loading overlay does not interfere with screen capture — MediaProjection captures the underlying app window, not the overlay layer.
```

**Section: Configuration Constants** (lines 67-71)

Update:
```
preScanBufferSize = 10
gestureIntervalMs = 200          // Total inter-gesture budget (100ms settle + capture/classify fills remainder)
gestureDurationMs = 100
frameCaptureSettleMs = 100       // Wait after gesture for app to render before capture
frameCaptureBudgetMs = 15        // Target frame acquisition time
```

**Section: Dependencies** (line 22)

Update:
```
- **Hard**: WI-02 (ScanMap, ClassifiedItem), WI-05 (gesture dispatch, lifecycle events, screen capture), WI-06 (visual-first classification pipeline), WI-07 (active profile for skip decisions)
- **Integration**: WI-08 (OverlayService for loading overlay), WI-16 (ScreenCaptureManager for frame capture during pre-scan)
```

**Section: Acceptance Criteria** — update pre-scan time:
```
- Pre-scan of 10 items completes in < 6 seconds (accommodates visual capture overhead)
```

**Section: Notes** — add:
```
- During pre-scan, the loading overlay is rendered on a separate window layer. MediaProjection captures the underlying app content, not the overlay, so frame capture is unaffected by the loading animation.
```

### WI-10: Scroll Mask — Live Mode & Extensions

**Section: Lookahead Extension** (lines 44-65)

Update the timing calculation:
```
The extension uses the same fast-forward technique but operates beyond the user's current position. It scrolls ahead, classifies (with visual capture), then scrolls back. 5 items × 295ms = ~1.5 seconds (vs. old 1.3s with text-only). This still completes before the user naturally scrolls 3 more items.
```

**Section: Configuration Constants** (lines 85-93)

Add:
```
frameCaptureSettleMs = 100       // Wait after gesture for app to render before capture
```

**Section: Dependencies** (line 14)

Update:
```
- **Hard**: WI-09 (ScanMap, PreScanController, ScrollMaskManager)
- **Integration**: WI-05 (gesture dispatch for skip execution), WI-08 (OverlayService for skip flash), WI-16 (ScreenCaptureManager for extension frame capture)
```

**Section: Acceptance Criteria** — no changes to timing requirements (extension timing within existing bounds).

### WI-11: Onboarding & Settings UI

**Section: Onboarding Flow — step 8 (Permissions)** (lines 37-40)

Add MediaProjection permission:
```
8. **Permissions**:
   - Accessibility Service: navigate user to system settings, verify activation on return, re-prompt with explanation if not enabled
   - Overlay: `Settings.canDrawOverlays()` check, navigate to `ACTION_MANAGE_OVERLAY_PERMISSION` if needed
   - **Screen Capture (MediaProjection)**: Launch `MediaProjectionManager.createScreenCaptureIntent()`. Explain: "ScrollShield uses screen capture to visually detect ads from the actual pixels on screen — this is the primary protection method and cannot be defeated by apps hiding their labels." If denied: show warning that visual classification is unavailable and protection will rely on text-only detection.
   - Clear explanations for each permission
```

**Section: Settings Screen** (lines 51-55)

Add to Advanced section:
```
- Advanced: scoring weight sliders, CPM overrides, pre-scan buffer size, extension size, status dot thresholds, **visual classification toggle** (default: on), **MediaProjection re-grant button**
```

**Section: Acceptance Criteria** (lines 62-70)

Add:
```
- MediaProjection permission requested during onboarding with clear explanation
- Visual classification toggle in Advanced settings
- MediaProjection denial shows warning about reduced protection
```

**Section: Dependencies** — unchanged (onboarding does not directly depend on WI-16, it just requests the system permission).

### WI-12: Signature Sync & Local Learning

**Section: Local Learning** (lines 46-52)

Update:
```
### Local Learning
- Ads detected by Tier 0b, Tier 1, or Tier 2 that are not in the signature cache → generate `AdSignature`:
  - `source = "local_detection"`
  - `simHash` computed via SimHash utility (from caption text, if available)
  - `visualHash` computed via PerceptualHash utility (from screenCapture, if available)
  - `confidence` from the classification result
  - `expires` = current time + 30 days
- Stored locally, used in subsequent Tier 0a lookups (both text and visual signatures)
- Not shared unless user opts in (future feature)
```

**Section: Sync** (lines 25-31)

Add to the endpoint description:
```
- Synced signatures may include `visualHash` field for visual signature matching
```

**Section: Dependencies** (lines 13-14)

Update:
```
- **Hard**: WI-02 (AdSignature model including visualHash), WI-03 (SignatureDao including getActiveVisualHashes), WI-06 (Tier 0a uses signatures — text and visual, local learning generates them)
- **Integration**: WI-04 (SimHash for text signatures, PerceptualHash for visual signatures)
```

**Section: Acceptance Criteria** (lines 58-66)

Add:
```
- Local learning generates visual signatures (visualHash) alongside text signatures
- Visual signatures used in subsequent Tier 0a lookups
```

### WI-13: Session Analytics & Reporting UI

**Section: Weekly Report** (lines 26-30)

Add to contents:
```
- Contents: total time (split by profile), ads detected and skipped, top 5 targeting brands, ad-to-content ratio trend, satisfaction average, **classification method breakdown (Tier 0 text / Tier 1 visual / Tier 2 deep text)**
```

**Section: Monthly Aggregates** (lines 32-34)

Add:
```
- Also tracks: classification tier distribution (percentage of items classified by visual vs. text methods), visual classifier accuracy feedback (items manually reviewed by user)
```

**Section: Acceptance Criteria** (lines 51-60)

Add:
```
- Classification method breakdown included in weekly report
- Tier distribution tracked in monthly aggregates
```

**Section: Dependencies** — unchanged.

### WI-14: Error Handling & Diagnostics

**Section: Detailed Specification** — add new subsection after "Pre-Scan Timeout":

```markdown
### MediaProjection Revocation
- If `MediaProjection` is revoked during a session (user revokes from system settings or notification):
  - Classification pipeline degrades to text-only (Tier 0 + Tier 2)
  - Show persistent notification: "Visual protection paused — screen capture revoked"
  - Log diagnostic event
  - On next session start, re-request MediaProjection permission

### Visual Model Load Failure
- If visual classifier TFLite model is corrupted or missing:
  - Fall back to text-only classification (Tier 0 + Tier 2)
  - Log diagnostic event
  - Show subtle indicator on pill overlay (similar to existing TFLite failure indicator)
```

**Section: TFLite Model Load Failure** (lines 31-33)

Update:
```
### TFLite Model Load Failure
- If **visual model** file is corrupted or missing: fall back to text-only classification (Tier 0 + Tier 2)
- If **text model** file is corrupted or missing: fall back to Tier 0 + Tier 1 visual only
- If **both** models fail: fall back to Tier 0 only (signature + label matching)
- Log diagnostic event
- Show subtle indicator on pill overlay
```

**Section: DiagnosticLogger** (lines 51-53)

Add to logged events:
```
- Log events: ..., MediaProjection grant/revoke, visual model load/fail, frame capture success/fail, visual vs text classification counts per session
```

**Section: Dependencies** (line 15)

Update:
```
- **Hard**: WI-05 (accessibility service + MediaProjection), WI-06 (TFLite models — visual + text), WI-03 (database), WI-09 (pre-scan)
- **Integration**: WI-08 (pill overlay for status indicators), WI-16 (ScreenCaptureManager error states)
```

**Section: Acceptance Criteria** (lines 60-68)

Add:
```
- MediaProjection revocation shows notification and degrades gracefully
- Visual model failure degrades to text-only classification
- Text model failure degrades to visual-only classification
- Both model failure degrades to Tier 0 only
```

### WI-15: Testing Infrastructure & ML Pipeline

**Section: Performance Benchmarks** (lines 56-67)

Update:
```
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
```

**Section: Performance Regression** (lines 68-69)

Update:
```
- CI benchmark suite: fail build if median classification latency exceeds 120ms (was 80ms, increased for visual tier)
```

**Section: Memory Budget** (lines 76-89)

Update:
```
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
```

**Section: ML Pipeline** (lines 91-104)

Update `train_classifier.py` description and add `train_visual_classifier.py`:

```markdown
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
```

**Section: Unit Tests** (lines 30-37)

Add:
```
- Visual classifier: mock Bitmap input, verify output shape and confidence thresholds
- ScreenCaptureManager: mock MediaProjection, verify frame acquisition and error handling
- PerceptualHash: hash consistency, similarity matching
- Pipeline tier routing: verify Tier 0 short-circuit, Tier 1 visual primary, Tier 2 fallback
```

**Section: Integration Tests** (lines 39-45)

Add:
```
- Visual classification: full pipeline with real screenshots — frame capture → visual inference → skip decision
- MediaProjection denial: verify graceful degradation to text-only
- Thermal throttle: verify degradation to Tier 0 only
```

**Section: Dependencies** (line 14)

Update:
```
- **Hard**: All other work items (this validates them), WI-17 (visual model training produces the visual classifier model)
- **Integration**: WI-06 (models consumed by pipeline), WI-16 (ScreenCaptureManager tested)
```

**Section: Files to Create / Modify** (lines 18-26)

Add:
```
- `ml/train_visual_classifier.py`
- `ml/export_visual_tflite.py`
- `ml/dataset/screenshots/` — labeled screenshot dataset
```

---

## New Work Items

### WI-16: Screen Capture Infrastructure (MediaProjection)

```markdown
# WI-16: Screen Capture Infrastructure (MediaProjection)

## Source
- Module 1: Feed Interception Service (Fallback Strategy — elevated to primary)
- Module 2: Classification Pipeline (Tier 1 Visual Classification input)
- Architecture: Screen Capture Service box

## Goal
Implement the MediaProjection-based screen capture infrastructure that provides visual input for the primary Tier 1 visual classification path. This includes permission management, VirtualDisplay/ImageReader setup, frame acquisition, and graceful degradation when permission is denied.

## Context
MediaProjection screen capture is the foundation of ScrollShield's visual-first classification architecture. It captures the actual rendered pixels from the source app, providing input that cannot be defeated by accessibility tree manipulation. The ScreenCaptureManager is consumed by the classification pipeline (WI-06) and the feed interception service (WI-05) to attach screen captures to every FeedItem.

## Dependencies
- **Hard**: WI-01 (project scaffolding, manifest permissions including FOREGROUND_SERVICE_MEDIA_PROJECTION), WI-02 (FeedItem model with screenCapture field)
- **Integration**: WI-05 (FeedInterceptionService manages MediaProjection lifecycle alongside accessibility service), WI-06 (VisualClassifier consumes captured frames), WI-11 (onboarding requests MediaProjection permission)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt`
- `app/src/main/java/com/scrollshield/service/MediaProjectionHolder.kt`
- `app/src/main/java/com/scrollshield/di/MediaProjectionModule.kt`

## Detailed Specification

### ScreenCaptureManager
```kotlin
@Singleton
class ScreenCaptureManager @Inject constructor(
    private val context: Context,
    private val mediaProjectionHolder: MediaProjectionHolder
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    suspend fun captureFrame(): Bitmap? { /* ... */ }
    fun isAvailable(): Boolean { /* ... */ }
    fun start(mediaProjection: MediaProjection) { /* ... */ }
    fun stop() { /* ... */ }
}
```

### Frame Acquisition
1. `ImageReader` created at device display resolution with `PixelFormat.RGBA_8888`, max 2 images
2. `VirtualDisplay` created from `MediaProjection` connected to `ImageReader.surface`
3. `captureFrame()`:
   - Call `imageReader.acquireLatestImage()`
   - Convert `Image` planes to `Bitmap`
   - Crop: remove status bar (top `WindowInsets.statusBars` height) and navigation bar (bottom `WindowInsets.navigationBars` height)
   - Return cropped `Bitmap`
   - Close `Image` immediately to release buffer
4. Frame staleness check: if `Image.timestamp` is older than 200ms, skip and return null (stale frame from before gesture)

### MediaProjectionHolder
- Holds the `MediaProjection` token obtained from `onActivityResult` during onboarding
- Stores token intent in `EncryptedSharedPreferences` for session recovery
- On `MediaProjection` callback `onStop()`: set `isAvailable = false`, notify ScreenCaptureManager

### Permission Flow
- During onboarding (WI-11): `MediaProjectionManager.createScreenCaptureIntent()` → `startActivityForResult`
- On grant: store result intent, initialize ScreenCaptureManager
- On deny: store denial, show warning, proceed with text-only classification
- In settings: "Re-grant screen capture" button to re-trigger permission flow

### Foreground Service Requirement
- MediaProjection requires an active foreground service (Android 10+)
- Reuse the existing `FeedInterceptionService` foreground notification or create a dedicated `ScreenCaptureService`
- Notification: "ScrollShield visual protection active" (low priority, persistent)

### Performance Requirements
- Frame capture: < 15ms (measured from `acquireLatestImage()` to cropped Bitmap returned)
- Memory: single frame buffer ~3MB at 1080p RGBA
- No frame accumulation — acquire and release immediately

### Error Handling
- `MediaProjection` revoked: set `isAvailable = false`, ScreenCaptureManager returns null, pipeline degrades to text-only
- `ImageReader` returns null image: retry once after 50ms, then return null
- `SecurityException` on `createVirtualDisplay`: log, set unavailable, degrade gracefully

## Acceptance Criteria
- Frame capture completes in < 15ms
- Captured frames correctly exclude status bar and navigation bar
- Stale frame detection works (frames older than 200ms rejected)
- MediaProjection denial results in `isAvailable() == false`, not a crash
- MediaProjection revocation mid-session degrades gracefully
- Foreground service notification displayed while capture is active
- Memory: no frame buffer leak (Image closed after each acquisition)
- Works on API 28+ (MediaProjection available since API 21)

## Notes
- Android 14 (API 34) requires `FOREGROUND_SERVICE_MEDIA_PROJECTION` type declaration in manifest — handled by WI-01.
- Some OEMs may restrict MediaProjection in battery saver mode. Add to OEM-specific guidance in onboarding (see Open Question 9 in tech spec).
- The `MediaProjection` token from `onActivityResult` can be reused across app restarts by storing and replaying the result intent — test on API 28-34 for compatibility.
```

### WI-17: Visual Classifier Dataset & Training

```markdown
# WI-17: Visual Classifier Dataset & Training

## Source
- Module 2: Classification Pipeline (Tier 1 Visual Classification)
- Demo Proposal Section 6 (Doomscrolling Agent Fleet)
- Training data requirements for MobileNetV3-Small fine-tuning

## Goal
Build the labeled screenshot dataset and train the MobileNetV3-Small visual classifier that powers Tier 1 (primary) visual classification. Produce the `scrollshield_visual_classifier.tflite` model file.

## Context
The visual classifier is the primary detection path in ScrollShield's visual-first architecture. It classifies screen captures of social media feeds into 7 classes (ORGANIC, OFFICIAL_AD, INFLUENCER_PROMO, ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, EDUCATIONAL, UNKNOWN) and produces a 20-dimensional topic vector. The model must be small enough for on-device inference (< 5MB) and fast enough for real-time classification (< 60ms on Snapdragon 7-series).

## Dependencies
- **Hard**: WI-02 (Classification enum defines the 7 target classes, TopicCategory enum defines the 20 topic dimensions)
- **Integration**: WI-06 (VisualClassifier consumes the trained model), WI-15 (testing infrastructure validates the model), WI-18 (visual model benchmark validates on-device performance)

## Files to Create / Modify

- `ml/train_visual_classifier.py`
- `ml/export_visual_tflite.py`
- `ml/dataset/screenshots/` — raw screenshot images organized by class
- `ml/dataset/annotations.json` — annotation file
- `ml/dataset/splits.json` — train/val/test split definitions
- `ml/eval/evaluate_visual.py` — evaluation metrics script
- `app/src/main/assets/scrollshield_visual_classifier.tflite` — output model

## Detailed Specification

### Dataset Requirements

**Target classes** (7, matching Classification enum):
1. ORGANIC — normal user-generated content
2. OFFICIAL_AD — platform-labeled sponsored content with branded overlays, CTA buttons, product cards
3. INFLUENCER_PROMO — influencer content with product placements, discount codes, affiliate links
4. ENGAGEMENT_BAIT — manipulative visual patterns (fake buttons, misleading thumbnails, ragebait)
5. OUTRAGE_TRIGGER — inflammatory visual elements (all-caps overlays, warning graphics)
6. EDUCATIONAL — educational content (diagrams, tutorials, lecture framing)
7. UNKNOWN — ambiguous/unclassifiable

**Minimum dataset size**: 15,000 labeled screenshots
- ORGANIC: 5,000
- OFFICIAL_AD: 3,000
- INFLUENCER_PROMO: 2,500
- ENGAGEMENT_BAIT: 1,500
- OUTRAGE_TRIGGER: 1,500
- EDUCATIONAL: 1,000
- UNKNOWN: 500

**Platform distribution**: TikTok 40%, Instagram 30%, YouTube 30%

**Data acquisition**:
1. Agent fleet screenshots (60%): Capture full-screen screenshots from doomscrolling agents (Section 6 of proposal) across demographic profiles on TikTok, Instagram Reels, YouTube Shorts.
2. Manual annotation (25%): 3-5 annotators via Labelbox/Scale AI. Inter-annotator agreement target: Cohen's kappa > 0.85.
3. Synthetic augmentation (15%): rotation ±5°, brightness ±20%, compression artifacts, resolution scaling 0.5x-1.5x, synthetic ad overlays on organic content.

**Annotation schema**:
```json
{
  "image_id": "string",
  "source_app": "tiktok|instagram|youtube",
  "classification": "ORGANIC|OFFICIAL_AD|...",
  "confidence": 0.0-1.0,
  "ad_indicators": [
    {"type": "cta_button|sponsored_label|product_card|discount_code|brand_logo", "bbox": [x, y, w, h]}
  ],
  "topic_categories": [0.0, ...],  // 20-dim topic vector ground truth
  "platform_version": "string",
  "device_resolution": "1080x2340",
  "capture_timestamp": "ISO8601",
  "annotator_id": "string"
}
```

**Validation split**: 70% train / 15% validation / 15% test, stratified by class and platform.

### Training Pipeline

#### `train_visual_classifier.py`
- Base model: MobileNetV3-Small pretrained on ImageNet (torchvision)
- Transfer learning: freeze first 10 convolutional layers, fine-tune remaining layers
- New classification head: `nn.Linear(576, 7)` (7-class softmax)
- New topic head: `nn.Linear(576, 20)` (20-dim regression, sigmoid activation)
- Loss: CrossEntropyLoss (classification) + MSELoss (topic vector), weighted 0.7 / 0.3
- Training: 50 epochs, batch size 32, lr 1e-4, cosine annealing scheduler
- Data augmentation: RandomHorizontalFlip, RandomRotation(5), ColorJitter(brightness=0.2, contrast=0.2), RandomResizedCrop(224, scale=(0.8, 1.0))
- Early stopping: patience 10 epochs on validation F1
- Hardware: single V100 GPU, ~4 hours training time

#### `export_visual_tflite.py`
- Convert PyTorch → ONNX → TFLite
- Int8 quantization (post-training quantization with representative dataset of 500 images)
- Output: `scrollshield_visual_classifier.tflite` (~3.4MB)
- Input tensor: `uint8[1][224][224][3]`
- Output tensors: `float32[1][7]`, `float32[1][20]`
- Validation: inference < 60ms on Snapdragon 7-series (via TFLite benchmark tool)

#### `evaluate_visual.py`
- Compute per-class precision, recall, F1
- Compute confusion matrix
- Compute topic vector MSE
- Target metrics:
  - F1 > 0.85 OFFICIAL_AD
  - F1 > 0.75 INFLUENCER_PROMO
  - F1 > 0.70 ENGAGEMENT_BAIT
  - Overall accuracy > 0.80
  - Topic vector MSE < 0.05

## Acceptance Criteria
- Dataset contains ≥ 15,000 labeled screenshots across 3 platforms
- Inter-annotator agreement (Cohen's kappa) > 0.85 on a 500-image validation set
- Trained model achieves F1 > 0.85 OFFICIAL_AD, F1 > 0.75 INFLUENCER_PROMO on test set
- Exported TFLite model < 5MB
- TFLite inference < 60ms on Snapdragon 7-series
- Model correctly outputs both 7-class probabilities and 20-dim topic vector
- Evaluation script produces per-class metrics and confusion matrix

## Notes
- The agent fleet (Section 6 of proposal) is the primary data source. If the fleet is not yet operational, use manual screenshot collection from test devices as a bootstrap.
- Dataset versioning: use DVC (Data Version Control) to track dataset versions alongside model versions.
- The visual model should be retrained monthly as platforms evolve their ad creative patterns.
- Consider federated learning in V2 for on-device model improvement without sharing raw data.
```

### WI-18: Visual Model On-Device Benchmark & Optimization

```markdown
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
| Bitmap crop + resize | < 5ms | 1080p → 224×224 |
| End-to-end Tier 1 | < 80ms | Capture + preprocess + inference |
| End-to-end full pipeline | < 100ms | Tier 0 + Tier 1 (no Tier 2 needed) |

### Delegate Optimization
- Test NNAPI delegate, GPU delegate (OpenCL), and CPU (XNNPack) on each device
- Select best delegate per device family
- Implement runtime delegate selection: try NNAPI → fall back to GPU → fall back to CPU
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
- Delegate selection logic correctly falls back from NNAPI → GPU → CPU

## Notes
- Use Android Benchmark library (`androidx.benchmark`) for reliable microbenchmarks.
- The TFLite benchmark tool (`benchmark_model`) can be used for isolated model benchmarks without the full app.
- If Snapdragon 730G (Pixel 4a) fails the 60ms target, consider: (a) MobileNetV2 as smaller alternative, (b) further quantization (int4), (c) input resolution reduction to 192×192.
- Benchmark results should be committed to the repo as reference baselines for CI regression detection.
```

---

## Dependency Summary

### Complete Dependency Map (including reverse dependencies)

| Work Item | Hard Dependencies | Integration Dependencies |
|-----------|------------------|--------------------------|
| WI-01 | None | None |
| WI-02 | WI-01 | None |
| WI-03 | WI-02 | WI-01 |
| WI-04 | WI-01 | None |
| WI-05 | WI-02, WI-04 | WI-01, WI-09, WI-16 |
| WI-06 | WI-02, WI-04 | WI-03, WI-05, WI-16 |
| WI-07 | WI-02, WI-03 | WI-01 |
| WI-08 | WI-02, WI-05, WI-06 | WI-03, WI-07, WI-16 |
| WI-09 | WI-02, WI-05, WI-06, WI-07 | WI-08, WI-16 |
| WI-10 | WI-09 | WI-05, WI-08, WI-16 |
| WI-11 | WI-02, WI-03, WI-07 | WI-08, WI-09 |
| WI-12 | WI-02, WI-03, WI-06 | WI-04 |
| WI-13 | WI-02, WI-03, WI-08 | WI-07 |
| WI-14 | WI-05, WI-06, WI-03, WI-09 | WI-08, WI-16 |
| WI-15 | All other WIs, WI-17 | WI-06, WI-16 |
| **WI-16** | **WI-01, WI-02** | **WI-05, WI-06, WI-11** |
| **WI-17** | **WI-02** | **WI-06, WI-15, WI-18** |
| **WI-18** | **WI-17, WI-16, WI-06** | **WI-15, WI-01** |

### Milestone Assignment for New Work Items

- **WI-16** (Screen Capture Infrastructure): **MVP** milestone — required for visual classification, which is the primary detection path
- **WI-17** (Visual Classifier Dataset & Training): **V1.1** milestone — model can use placeholder during MVP/V1 development, trained model needed for V1.1 release
- **WI-18** (Visual Model Benchmark): **V1.1** milestone — validates model performance, runs after WI-17 produces the model

### Implementation Order Update

Add to the Implementation Order table:
```
| 3c | Screen Capture Infrastructure (WI-16) | 1, 2 | 2 days |
| 12b | Visual Classifier Dataset & Training (WI-17) | 2 | 5 days |
| 12c | Visual Model Benchmark (WI-18) | 12b, 3c, 4b | 2 days |
```

Step 3c runs parallel with steps 3/3b/4. Step 12b runs parallel with steps 10-12. Step 12c depends on 12b.

---

## Evasion Resilience Summary

The revised architecture is demonstrably harder for source apps to defeat:

1. **Text-based evasion (current vulnerability)**: Apps can rename `com.zhiliaoapp.musically:id/ad_label`, remove "Sponsored" text, or obfuscate captions. This defeats Tier 0a (SimHash) and Tier 0b (label detection) and Tier 2 (DistilBERT on text).

2. **Visual evasion (new primary path)**: To defeat Tier 1 visual classification, apps would need to change what the user actually sees on screen — the rendered pixels. This means altering the visual appearance of ads (removing CTA buttons, brand logos, product overlays) which would make the ads less effective for advertisers. This creates a fundamental conflict: the more evasion-resistant the ad becomes, the less commercially effective it is. Visual classification from rendered pixels exploits this trade-off.

3. **Defense in depth**: Even if text evasion succeeds, visual classification still operates on the actual rendered output. Even if visual classification is defeated (e.g., MediaProjection denied), text methods provide baseline detection. The layered approach ensures no single evasion technique disables all detection.
--- Iteration 3 Addendum ---

All changes from Iteration 2 plan apply unchanged, with the following additional fix:

**File:** docs/technical-spec.md
**Line:** 65
**Current text:**
- Thermal throttling fallback: if device is overheating, skip Tier 3 classification and rely on Tier 1 + Tier 2 only.

**Replacement text:**
- Thermal throttling fallback: if device is overheating, skip Tier 1 (Visual Classification) and Tier 2 (Deep Text) and rely on Tier 0 (Text Signals: SimHash + Label Detection) only.
