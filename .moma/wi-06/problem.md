# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement WI-06: Classification Pipeline for the ScrollShield Android app (project at `/home/devuser/dev-worktree-1`).

Create the visual-first classification pipeline with four tiers:
- **Tier 0a — Signature Match** (< 5ms): Compute 64-bit SimHash of normalised caption via `TextNormaliser` + `SimHash` (from `util/`). Compare against local `ad_signatures` table (via `SignatureDao`). Text match: Hamming distance <= 3 bits. Also compute `PerceptualHash` of `screenCapture` and compare against `visualHash` column. Visual match: Hamming distance <= 8 bits. If either matches with confidence > 0.95, short-circuit.
- **Tier 0b — Label Detection** (< 15ms): Check `FeedItem.labelText` against known ad label patterns (case-insensitive): "Sponsored", "Ad", "Promoted", "Paid partnership", "Publicité", "Gesponsert", "Patrocinado", "広告", "광고", "Реклама", "إعلان", "Anzeige", "Reklam", "Sponsorizzato", "Promowane". If match: `OFFICIAL_AD`, confidence 1.0, short-circuit.
- **Tier 1 — Visual Classification** (< 80ms, PRIMARY): Takes `FeedItem.screenCapture` Bitmap from MediaProjection. Preprocess: crop content region (exclude status bar top 24dp, nav bar bottom 48dp), resize to 224×224 RGB bilinear. Model: MobileNetV3-Small int8 quantized TFLite (~3.4MB). Input: `uint8[1][224][224][3]`. Output: `float32[1][7]` (7-class → Classification enum) + `float32[1][20]` (topic vector → TopicCategory). Use NNAPI delegate, fallback to XNNPack CPU. If max probability < 0.7, fall through to Tier 2. If screenCapture is null or inference fails, skip to Tier 2.
- **Tier 2 — Content Analysis** (< 50ms, supplementary): Runs only if Tier 0 didn't short-circuit AND Tier 1 was inconclusive. DistilBERT-tiny TFLite (4L/128H, ~15MB, float16). WordPiece tokenizer, max 128 tokens. On failure: return `UNKNOWN` with confidence 0.0, `SHOW_LOW_CONF`.

Also implement:
- **ScreenCaptureManager**: Manages MediaProjection lifecycle and ImageReader. Provides `suspend fun captureFrame(): Bitmap?`. Frame acquisition < 15ms. Handles revocation gracefully (returns null). Crops to content region.
- **SkipDecisionEngine**: Computes SkipDecision from Classification + UserProfile. EDUCATIONAL is never auto-skipped. Child profiles enforce stricter rules. Low confidence fails open to `SHOW_LOW_CONF`.
- **ClassificationPipeline**: Router that orchestrates the tier cascade per the spec's `classify(feedItem, profile)` suspend function. Includes thermal throttling fallback (skip Tier 1 + Tier 2, rely on Tier 0 only).

Pipeline router pseudocode:
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

## Context Files

- /home/devuser/dev-worktree-1/work-items/WI-06-classification-pipeline.md
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/model/AdSignature.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/model/FeedItem.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/model/UserProfile.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/SimHash.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/TextNormaliser.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/PerceptualHash.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/db/SignatureDao.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/di/ClassificationModule.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/di/MediaProjectionModule.kt
- /home/devuser/dev-worktree-1/app/build.gradle.kts

## Target Files (to modify)

- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/ClassificationPipeline.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/SignatureMatcher.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/LabelDetector.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/VisualClassifier.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/ContentClassifier.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/SkipDecisionEngine.kt

## Rules & Constraints

- All files go under `app/src/main/java/com/scrollshield/classification/` package
- Use existing model classes verbatim — do not modify ClassifiedItem, FeedItem, UserProfile, AdSignature, or any other existing files
- Use existing util classes (SimHash, TextNormaliser, PerceptualHash) — do not duplicate their logic
- Use existing SignatureDao for database lookups — do not create new DAOs
- TFLite dependencies already in build.gradle.kts — do not add new dependencies
- NNAPI delegate with XNNPack CPU fallback for visual classifier
- All classification must be suspend functions running on coroutine dispatchers
- Thermal throttling: skip Tier 1 and Tier 2 when device is overheating — rely on Tier 0 only
- EDUCATIONAL classification must never be auto-skipped regardless of profile settings
- Fail open: any unhandled exception returns UNKNOWN/SHOW_LOW_CONF
- Do not create placeholder TFLite model files — those come from WI-15 and WI-17
- ScreenCaptureManager must handle MediaProjection revocation gracefully (return null)

## Review Criteria

1. Pipeline routing follows correct tier order (0a → 0b → 1 → 2) with proper short-circuit logic and confidence thresholds matching the spec exactly
2. SignatureMatcher implements both text SimHash (Hamming <= 3) and visual PerceptualHash (Hamming <= 8) matching against SignatureDao, with confidence > 0.95 short-circuit
3. LabelDetector checks all specified localised ad label strings case-insensitively and returns OFFICIAL_AD with confidence 1.0
4. VisualClassifier preprocesses correctly (crop system bars, resize 224×224 bilinear), uses TFLite with NNAPI delegate + XNNPack fallback, parses 7-class + 20-dim topic output tensors
5. ContentClassifier implements DistilBERT-tiny TFLite inference with WordPiece tokenization (max 128 tokens) and catches all exceptions returning UNKNOWN/SHOW_LOW_CONF
6. ScreenCaptureManager manages MediaProjection + ImageReader lifecycle, provides suspend captureFrame(), handles revocation gracefully returning null
7. SkipDecisionEngine correctly maps Classification + UserProfile to SkipDecision — EDUCATIONAL never auto-skipped, child profiles stricter, low confidence fails open
8. Thermal throttling fallback implemented: when device is overheating, skip Tier 1 and Tier 2, rely on Tier 0 only
9. ClassifiedItem construction is correct — tier field, timing, topic vector argmax for topicCategory, all fields populated properly including classifiedAt timestamp
10. All classes use proper Kotlin coroutine patterns (suspend functions), handle null screenCapture gracefully (degrade to text-only path: Tier 0 + Tier 2), and integrate with existing project types without modification

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew assembleDebug 2>&1
```
