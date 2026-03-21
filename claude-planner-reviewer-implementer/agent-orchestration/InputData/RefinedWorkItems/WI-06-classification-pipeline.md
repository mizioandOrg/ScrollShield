# WI-06: Classification Pipeline

## Source
- Module 2: Classification Pipeline (entire section)
- File Structure: `classification/`
- Architecture: "How the Two Features Relate" section

## Goal
Implement the three-tier classification pipeline (Signature Match -> Label Detection -> Content Analysis) and the skip decision engine.

## Context
The classification pipeline is the shared intelligence layer consumed by both features. Both the Ad Counter and Scroll Mask depend on it.

### How the Two Features Relate (verbatim from spec)
- The **Ad Counter** is passive — observes and counts. Never modifies the experience.
- The **Scroll Mask** is active — pre-scans and auto-skips unwanted content.
- Both consume events from the same classification pipeline.
- They run independently or together. Counter is on by default. Mask is opt-in.
- When both are active, the counter shows all ads the platform *attempted* to serve, including those the mask skipped.

## Dependencies
- **Hard**: WI-02 (ClassifiedItem, Classification, SkipDecision, AdSignature), WI-04 (SimHash, TextNormaliser)
- **Integration**: WI-03 (SignatureDao for Tier 1 lookups), WI-05 (FeedItem events from service)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/classification/ClassificationPipeline.kt`
- `app/src/main/java/com/scrollshield/classification/SignatureMatcher.kt`
- `app/src/main/java/com/scrollshield/classification/LabelDetector.kt`
- `app/src/main/java/com/scrollshield/classification/ContentClassifier.kt`
- `app/src/main/java/com/scrollshield/classification/SkipDecisionEngine.kt`
- `app/src/main/assets/scrollshield_classifier.tflite` (placeholder)

## Detailed Specification

### Tier 1 — Signature Match (< 5ms)
- Compute 64-bit SimHash of normalised caption (using TextNormaliser + SimHash from WI-04)
- Compare against local `ad_signatures` table
- Match criteria: Hamming distance <= 3 bits
- If match with confidence > 0.95, return immediately
- Expected catch: 40-60% of ads

### Tier 2 — Label Detection (< 15ms)
- Check `FeedItem.labelText` against known patterns (case-insensitive):
  ```
  {"Sponsored", "Ad", "Paid partnership", "Promoted", "Anzeige",
   "Sponsorise", "Sponsorizzato", "Reklame", "広告", "광고",
   "Patrocinado", "Реклама", "إعلان"}
  ```
- If match: `OFFICIAL_AD`, confidence 1.0
- Expected catch: 30-40% of remaining ads

### Tier 3 — Content Analysis (< 50ms)
- On-device TFLite model (`scrollshield_classifier.tflite`), float16 quantization
- Architecture: DistilBERT-tiny (4L/128H, ~15MB)
- Tokenizer: WordPiece, max 128 tokens. Input text truncated if longer.
- Input tensor: `int32[1][128]` — token IDs from WordPiece tokenizer of `[captionText] [SEP] [hashtags joined] [SEP] [creatorName]`
- Output tensors:
  - `float32[1][7]` — 7-class probability vector (maps to Classification enum including UNKNOWN)
  - `float32[1][20]` — 20-dimensional topic vector (maps to TopicCategory)
- Max probability < 0.7 -> `UNKNOWN` classification, confidence 0.0, fail open (`SHOW_LOW_CONF`)
- EDUCATIONAL classification is never auto-skipped regardless of profile settings
- **Error handling**: Catch all Tier 3 exceptions (model load failure, inference error) -> return `UNKNOWN` with confidence 0.0

### Skip Decision Engine
```kotlin
fun computeSkipDecision(item: ClassifiedItem, profile: UserProfile): SkipDecision {
    if (item.confidence < 0.7) return SHOW_LOW_CONF
    if (item.classification == OFFICIAL_AD) return SKIP_AD
    if (item.classification == INFLUENCER_PROMO
        && INFLUENCER_PROMO in profile.blockedClassifications) return SKIP_AD
    if (item.topicCategory in profile.blockedCategories) return SKIP_BLOCKED
    if (profile.isChildProfile
        && item.classification in setOf(ENGAGEMENT_BAIT, OUTRAGE_TRIGGER)) return SKIP_CHILD
    return SHOW
}
```

### Dual-OCR Strategy (when MediaProjection fallback is active)
- **Primary**: ML Kit Text Recognition (requires Google Play Services)
- **Fallback**: Tesseract4Android
  - Library: `io.github.nicepay:tesseract4android:4.7.0`
  - Trained data: `eng.traineddata` (fast variant, ~4.2MB) in `assets/tessdata/`, copied to `filesDir/tessdata/` on first use
  - APK impact: ~11MB total (native `.so` ~6.8MB + trained data ~4.2MB)
  - Latency: ML Kit 35-60ms vs Tesseract 180-350ms per frame
  - Mitigations: Crop capture to ROI before OCR, run in background coroutine, cache per-node results
  - Accuracy: ~5-8% higher word error rate than ML Kit; acceptable for classification
  - Language: English only for V1

### Thermal Throttling Fallback
If device is overheating, skip Tier 3 classification and rely on Tier 1 + Tier 2 only.

### Pipeline Router (`ClassificationPipeline.kt`)
```kotlin
suspend fun classify(feedItem: FeedItem, profile: UserProfile): ClassifiedItem {
    // Tier 1
    val t1 = signatureMatcher.match(feedItem)
    if (t1 != null && t1.confidence > 0.95) return t1.withSkipDecision(profile)

    // Tier 2
    val t2 = labelDetector.detect(feedItem)
    if (t2 != null) return t2.withSkipDecision(profile)

    // Tier 3
    val t3 = contentClassifier.classify(feedItem)
    return t3.withSkipDecision(profile)
}
```

## Acceptance Criteria
- Tier 1: < 5ms with 100K entries
- Tier 2: identifies all listed localised labels
- Tier 3: loads < 2s cold start, inference < 50ms on Snapdragon 7-series
- Full pipeline: < 60ms worst case
- F1: > 85% OFFICIAL_AD, > 75% INFLUENCER_PROMO
- Skip decision computed in < 1ms
- EDUCATIONAL items never auto-skipped
- Tier 3 failure gracefully falls back to UNKNOWN/SHOW_LOW_CONF

## Notes
- Open Question 5 (Model sizing): DistilBERT-tiny benchmark needed on Pixel 4a. Consider smaller alternatives if inference exceeds 50ms.
- The placeholder TFLite model file should be created in this WI. The actual trained model is produced by WI-15's ML pipeline.
