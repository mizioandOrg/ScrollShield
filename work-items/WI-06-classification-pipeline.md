# WI-06: Classification Pipeline

## Source
- Module 2: Classification Pipeline (entire section)
- File Structure: `classification/`
- Architecture: "How the Two Features Relate" section

## Goal
Implement the visual-first classification pipeline (Text Fast-Path -> Visual Classification -> Deep Text Analysis) and the skip decision engine. Screen capture via MediaProjection is the primary detection path; text-based methods are supplementary fast-path signals.

## Context
The classification pipeline is the shared intelligence layer consumed by both features. Both the Ad Counter and Scroll Mask depend on it. The pipeline uses a visual-first architecture: on-device image classification of screen captures is the primary detection method, resistant to evasion by source apps. Text-based methods (SimHash, label matching, DistilBERT) serve as supplementary fast-path signals that can short-circuit the pipeline when text is available but are not relied upon as the primary detection method.

### How the Two Features Relate (verbatim from spec)
- The **Ad Counter** is passive — observes and counts. Never modifies the experience.
- The **Scroll Mask** is active — pre-scans and auto-skips unwanted content.
- Both consume events from the same classification pipeline.
- They run independently or together. Counter is on by default. Mask is opt-in.
- When both are active, the counter shows all ads the platform *attempted* to serve, including those the mask skipped.

## Dependencies
- **Hard**: WI-02 (ClassifiedItem, Classification, SkipDecision, AdSignature), WI-04 (SimHash, TextNormaliser)
- **Integration**: WI-03 (SignatureDao for Tier 0a lookups), WI-05 (FeedItem events from service including screenCapture), WI-16 (ScreenCaptureManager, VisualClassifier)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/classification/ClassificationPipeline.kt`
- `app/src/main/java/com/scrollshield/classification/SignatureMatcher.kt` (Tier 0a)
- `app/src/main/java/com/scrollshield/classification/LabelDetector.kt` (Tier 0b)
- `app/src/main/java/com/scrollshield/classification/VisualClassifier.kt` (Tier 1 — NEW, PRIMARY)
- `app/src/main/java/com/scrollshield/classification/ContentClassifier.kt` (Tier 2 — supplementary)
- `app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt` (NEW)
- `app/src/main/java/com/scrollshield/classification/SkipDecisionEngine.kt`
- `app/src/main/assets/scrollshield_visual_classifier.tflite` (MobileNetV3-Small, ~3.4MB)
- `app/src/main/assets/scrollshield_text_classifier.tflite` (DistilBERT-tiny, ~15MB, renamed from scrollshield_classifier.tflite)

## Detailed Specification

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

## Notes
- Open Question 5 (Visual model sizing): MobileNetV3-Small benchmark needed on Pixel 4a with NNAPI. Consider MobileNetV2 if inference exceeds 60ms on target hardware.
- Two placeholder TFLite model files should be created in this WI: `scrollshield_visual_classifier.tflite` (visual, primary) and `scrollshield_text_classifier.tflite` (text, supplementary). The actual trained models are produced by WI-17 (visual) and WI-15 (text).
