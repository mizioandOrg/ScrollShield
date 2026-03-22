# WI-04: Utility Classes

## Source
- File Structure: `util/`
- Module 2: Tier 1 (SimHash, normalisation)
- Data Models: ScanMap (lastValidatedHash algorithm)

## Goal
Implement SimHash, PerceptualHash, CosineSimilarity, TextNormaliser, and the lastValidatedHash algorithm.

## Context
These utilities are used across multiple modules. SimHash powers Tier 0a text-based signature matching. PerceptualHash powers visual signature matching in Tier 1. CosineSimilarity is used for interest vector comparison. TextNormaliser prepares text for hashing and text-based classification. The lastValidatedHash algorithm validates ScanMap reuse on app re-entry.

## Dependencies
- **Hard**: WI-01 (project compiles)
- **Integration**: None.

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/util/SimHash.kt`
- `app/src/main/java/com/scrollshield/util/PerceptualHash.kt`
- `app/src/main/java/com/scrollshield/util/CosineSimilarity.kt`
- `app/src/main/java/com/scrollshield/util/TextNormaliser.kt`
- `app/src/main/java/com/scrollshield/util/FeedFingerprint.kt` (lastValidatedHash)

## Detailed Specification

### SimHash
- Compute 64-bit SimHash of normalised text
- Input: String (already normalised)
- Output: Long (64-bit hash)
- Comparison: Hamming distance function `fun hammingDistance(a: Long, b: Long): Int`
- Match threshold: Hamming distance <= 3 bits

### TextNormaliser
Normalisation rules (from spec):
1. Lowercase
2. Strip emoji (Unicode emoji ranges)
3. Collapse whitespace
4. Remove URLs (regex)
5. Remove @mentions (regex)

```kotlin
fun normalise(text: String): String
```

### CosineSimilarity
```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float
```
- Used for interest vector comparison
- Performance target: < 1ms per item

### FeedFingerprint (lastValidatedHash algorithm)
Verbatim from spec:
1. Collect visible `AccessibilityNodeInfo` nodes (TextView, ImageView, Button, View with contentDescription)
2. Extract tuple: `(className, viewIdResourceName, text?.take(64), contentDescription?.take(64))`
3. Sort lexicographically by `viewIdResourceName` then `className`
4. Concatenate with `|` separator
5. SHA-256 hex digest of UTF-8 bytes

Usage: On re-entry, recompute on `TYPE_WINDOW_STATE_CHANGED`, compare; match = reuse ScanMap, mismatch = re-scan.
Performance: <1ms for typical <10KB input.

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

## Acceptance Criteria
- SimHash produces consistent 64-bit hashes
- Hamming distance correctly counts differing bits
- TextNormaliser strips emoji, URLs, @mentions, collapses whitespace, lowercases
- CosineSimilarity returns 1.0 for identical vectors, 0.0 for orthogonal
- CosineSimilarity < 1ms per comparison
- FeedFingerprint produces consistent SHA-256 hex digests
- FeedFingerprint handles empty node lists gracefully
- PerceptualHash produces consistent 64-bit hashes for identical images
- PerceptualHash produces similar hashes (Hamming distance ≤ 8) for visually similar images with different resolutions
- PerceptualHash < 5ms per image

## Notes
- Unit tests for all utility functions should be part of this work item.
