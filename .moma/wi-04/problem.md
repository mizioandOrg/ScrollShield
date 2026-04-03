# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: sonnet
Implement: yes

## Task Description

Implement five utility classes for the ScrollShield Android app in the `com.scrollshield.util` package:

1. **SimHash** — 64-bit SimHash of normalised text. Input: String. Output: Long. Includes `fun hammingDistance(a: Long, b: Long): Int`. Match threshold: Hamming distance ≤ 3.
2. **TextNormaliser** — `fun normalise(text: String): String`. Rules applied in order: (1) lowercase, (2) strip emoji (Unicode emoji ranges), (3) collapse whitespace, (4) remove URLs, (5) remove @mentions.
3. **CosineSimilarity** — `fun cosineSimilarity(a: FloatArray, b: FloatArray): Float`. Used for interest vector comparison. Target: < 1ms per call.
4. **FeedFingerprint** (lastValidatedHash algorithm) — collects visible AccessibilityNodeInfo nodes (TextView, ImageView, Button, View with contentDescription), extracts tuple (className, viewIdResourceName, text?.take(64), contentDescription?.take(64)), sorts lexicographically by viewIdResourceName then className, concatenates with `|` separator, returns SHA-256 hex digest of UTF-8 bytes. Target: < 1ms for typical < 10KB input.
5. **PerceptualHash** — pHash of a Bitmap. Algorithm: resize to 32×32 grayscale → DCT → extract top-left 8×8 coefficients → threshold at median → 64-bit hash. `fun perceptualHash(bitmap: Bitmap): Long`, `fun visualMatch(a: Long, b: Long, threshold: Int = 8): Boolean`. Reuses `SimHash.hammingDistance()`. Match threshold: Hamming ≤ 8. Target: < 5ms per image.

Unit tests for all five classes must be included.

Full specification: `work-items/WI-04-utility-classes.md` (relative to `/home/devuser/dev-worktree-1`).

## Context Files

- /home/devuser/dev-worktree-1/work-items/WI-04-utility-classes.md
- /home/devuser/dev-worktree-1/app/build.gradle.kts

## Target Files (to modify)

- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/SimHash.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/PerceptualHash.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/CosineSimilarity.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/TextNormaliser.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/FeedFingerprint.kt
- /home/devuser/dev-worktree-1/app/src/test/java/com/scrollshield/util/SimHashTest.kt
- /home/devuser/dev-worktree-1/app/src/test/java/com/scrollshield/util/PerceptualHashTest.kt
- /home/devuser/dev-worktree-1/app/src/test/java/com/scrollshield/util/CosineSimilarityTest.kt
- /home/devuser/dev-worktree-1/app/src/test/java/com/scrollshield/util/TextNormaliserTest.kt
- /home/devuser/dev-worktree-1/app/src/test/java/com/scrollshield/util/FeedFingerprintTest.kt

## Rules & Constraints

- Follow the exact algorithms specified in WI-04 (tuple extraction order, sort keys, separator character, DCT window size, etc.)
- TextNormaliser rules must be applied in the exact order given in the spec
- FeedFingerprint must collect only the node types listed (TextView, ImageView, Button, View with contentDescription)
- PerceptualHash must reuse SimHash.hammingDistance() for Hamming comparison
- Do not modify any files outside the util/ package and its test counterpart
- All code must compile with the existing build.gradle.kts (no new dependencies)

## Review Criteria

1. SimHash produces consistent 64-bit hashes for identical inputs
2. Hamming distance correctly counts differing bits (XOR popcount)
3. TextNormaliser strips emoji, URLs, @mentions; collapses whitespace; lowercases — in spec order
4. CosineSimilarity returns 1.0 for identical vectors, 0.0 for orthogonal vectors
5. FeedFingerprint produces consistent SHA-256 hex digests for the same node set
6. FeedFingerprint handles empty node lists gracefully (returns hash of empty string or defined sentinel)
7. PerceptualHash produces consistent 64-bit hashes for identical images
8. PerceptualHash hash Hamming distance ≤ 8 for visually similar images at different resolutions
9. Unit tests cover all five classes with meaningful, non-trivial assertions
10. All code compiles cleanly with no new external dependencies added

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "com.scrollshield.util.*"
```
