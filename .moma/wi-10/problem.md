# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement WI-10: Scroll Mask — Live Mode & Extensions for the ScrollShield Android app.

After the pre-scan phase (WI-09), the user scrolls through the feed. This work item handles:

1. **Live Interception** — On each user swipe, look up the item at the new position in ScanMap. If `skipDecision` is any `SKIP_*` variant, call `feedInterceptionService.scrollForward()` to advance past it, show a skip flash overlay (200ms), and record the skipped item. Use `isOwnGesture` flag to prevent infinite skip loops.

2. **Consecutive Skip Handling** — When multiple adjacent items are flagged, execute skips at 300ms intervals, show a single combined flash ("Skipped 3 ads"), and enforce a maximum of 5 consecutive skips. If >5, pause and show "High ad density detected" overlay; resume on user tap.

3. **Lookahead Extension** — When user reaches position `scanHead - 3`, trigger a background extension scan of 5 items ahead using the same fast-forward + classify technique. Add results to ScanMap. If user catches up before extension completes, re-engage full pre-scan flow (loading overlay reappears).

4. **Skip Flash UI** — Small semi-transparent overlay at bottom of screen with shield icon + text ("Skipped: Ad" or "Skipped: [category]"). Duration 200ms, fades out. For consecutive skips: "Skipped N ads" with count.

5. **Interest Learning** — At session end, for default profiles only (not child profiles), compute which items user watched vs. manually skipped. Update interest vector: `interestVector = (1 - alpha) * interestVector + alpha * mean(watchedItemTopicVectors)`. Alpha = 0.05 normally, 0.2 for first 5 sessions.

### Configuration Constants
```
extensionSize = 5
extensionTriggerDistance = 3
skipFlashDurationMs = 200
consecutiveSkipIntervalMs = 300
liveGestureDurationMs = 150
maxConsecutiveSkips = 5
frameCaptureSettleMs = 100
```

## Context Files

- app/src/main/java/com/scrollshield/feature/mask/ScanMap.kt
- app/src/main/java/com/scrollshield/feature/mask/PreScanController.kt
- app/src/main/java/com/scrollshield/feature/mask/LoadingOverlay.kt
- app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt
- app/src/main/java/com/scrollshield/service/OverlayService.kt
- app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt
- app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt
- app/src/main/java/com/scrollshield/data/model/UserProfile.kt
- app/src/main/java/com/scrollshield/profile/ProfileManager.kt
- app/src/main/java/com/scrollshield/feature/counter/AdCounterManager.kt

## Target Files (to modify)

- app/src/main/java/com/scrollshield/feature/mask/LookaheadExtender.kt (create)
- app/src/main/java/com/scrollshield/feature/mask/SkipFlashOverlay.kt (create)
- app/src/main/java/com/scrollshield/feature/mask/ConsecutiveSkipHandler.kt (create)
- app/src/main/java/com/scrollshield/feature/mask/InterestLearner.kt (create)
- app/src/main/java/com/scrollshield/feature/mask/ScrollMaskManager.kt (modify)

## Rules & Constraints

- Use `FeedInterceptionService.isOwnGesture` flag to distinguish user scrolls from programmatic skips — without it, a skip gesture would trigger another skip lookup creating an infinite loop
- All new classes must follow Hilt DI patterns (@Singleton + @Inject constructor) consistent with the existing codebase
- Do not modify context files — only target files
- Use Kotlin coroutines (not threads) for all async work, consistent with existing patterns
- Interest learning runs only for default profile, never child profiles (`UserProfile.isChildProfile == false`)
- Backward scrolling uses pre-computed decisions from ScanMap (no re-classification needed)
- The project root is `/home/devuser/dev-worktree-1`

## Review Criteria

1. `performSkip()` advances to next video by calling `feedInterceptionService.scrollForward()` and completes within 500ms
2. Skip flash overlay displays for exactly 200ms with shield icon and category text, then fades out
3. Consecutive skips execute at 300ms intervals with a single combined flash ("Skipped N ads") instead of separate flashes
4. Maximum consecutive skips (5) is enforced — exceeding it shows "High ad density detected" overlay that pauses until user tap
5. Lookahead extension triggers at exactly `scanHead - extensionTriggerDistance` (3) and scans `extensionSize` (5) items ahead in a background coroutine
6. If user catches up to scan head before extension completes, full pre-scan flow re-engages with loading overlay
7. `isOwnGesture` flag is checked before processing scroll events to prevent infinite skip loops
8. Interest vector updates correctly at session end using the formula with alpha = 0.2 for first 5 sessions, 0.05 thereafter — only for non-child profiles
9. All new classes integrate cleanly with ScrollMaskManager without breaking existing pre-scan, hard-stop, or dismiss flows
10. The project compiles successfully with `./gradlew assembleDebug` and no new warnings in the target files

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew assembleDebug
```
