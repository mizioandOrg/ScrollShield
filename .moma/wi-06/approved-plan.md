# Approved Plan — WI-06 Classification Pipeline

Approved at: Iteration 1, Score: 10/10

## Files to Create

All under `app/src/main/java/com/scrollshield/classification/`:

1. **SignatureMatcher.kt** — Tier 0a: SimHash text + PerceptualHash visual matching against SignatureDao
2. **LabelDetector.kt** — Tier 0b: Localized ad label matching (15 patterns)
3. **VisualClassifier.kt** — Tier 1 (PRIMARY): MobileNetV3-Small TFLite + NNAPI/XNNPack
4. **ContentClassifier.kt** — Tier 2: DistilBERT-tiny TFLite + WordPiece tokenization
5. **ScreenCaptureManager.kt** — MediaProjection + ImageReader lifecycle
6. **SkipDecisionEngine.kt** — Classification + UserProfile → SkipDecision mapping
7. **ClassificationPipeline.kt** — Tier cascade router + thermal throttling

## Review Score: 10/10
All 10 criteria passed.
