# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement work-item WI-09: Scroll Mask — Pre-Scan & Core for the ScrollShield Android app.

This involves building the pre-scan phase that runs at session start when a user opens a target app (TikTok, Instagram Reels, YouTube Shorts) with mask enabled. The system must:

1. **Show a loading overlay** — full-screen branded animation over the app with shield icon, progress bar ("Scanning 4/10"), and text "ScrollShield is preparing your feed." Blocks interaction during pre-scan. For child profiles: "Setting up a safe feed for [child name]".

2. **Fast-forward scan** — call `feedInterceptionService.scrollForwardFast(bufferSize)` where `bufferSize` defaults to 10. For each item: wait 100ms for render, capture screen frame via ScreenCaptureManager (< 15ms), capture FeedItem, classify via ClassificationPipeline (Tier 0 -> Tier 1 -> Tier 2), store in ScanMap runtime.

3. **Rewind to start** — call `feedInterceptionService.scrollBackwardFast(bufferSize)`. Verify position 0 fingerprint matches pre-scan snapshot. If platform mutated feed during pre-scan, re-scan from new position 0.

4. **Dismiss loading overlay** — user sees first video with pre-computed skip decisions ready.

Expected pre-scan duration: ~5.5 seconds (10 items x ~295ms + ~2s rewind + ~0.5s overlay setup).

### Key Design Principles
- The user never leaves the native app. TikTok renders normally.
- Pre-computed skip decisions execute with zero additional classification latency.
- The user never sees unscanned content. No degraded mode exists.
- For child profiles: mask always active, cannot be dismissed without PIN, hard stop at time budget.

### Edge Cases
- **Back-stack limit**: if `scrollForward` detects duplicate items, stop pre-scan early.
- **User outruns buffer**: loading overlay reappears with "ScrollShield is scanning ahead.", new pre-scan from current position.
- **Low memory (<4GB)**: reduce buffer to 5 items, disable lookahead extension.
- **Feed mutation**: fingerprint mismatch after rewind triggers re-scan.

### Configuration Constants
```
preScanBufferSize = 10
gestureIntervalMs = 200
gestureDurationMs = 100
frameCaptureSettleMs = 100
frameCaptureBudgetMs = 15
```

## Context Files

- work-items/WI-09-scroll-mask-prescan.md
- app/src/main/java/com/scrollshield/data/model/ScanMap.kt
- app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt
- app/src/main/java/com/scrollshield/data/model/FeedItem.kt
- app/src/main/java/com/scrollshield/data/model/UserProfile.kt
- app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt
- app/src/main/java/com/scrollshield/service/OverlayService.kt
- app/src/main/java/com/scrollshield/classification/ClassificationPipeline.kt
- app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt
- app/src/main/java/com/scrollshield/profile/ProfileManager.kt
- app/src/main/java/com/scrollshield/profile/ChildProfileConfig.kt
- app/src/main/java/com/scrollshield/feature/counter/AdCounterManager.kt
- app/src/main/java/com/scrollshield/feature/counter/TimeBudgetNudge.kt
- app/src/main/java/com/scrollshield/util/FeedFingerprint.kt

## Target Files (to modify)

- app/src/main/java/com/scrollshield/feature/mask/ScrollMaskManager.kt (create)
- app/src/main/java/com/scrollshield/feature/mask/PreScanController.kt (create)
- app/src/main/java/com/scrollshield/feature/mask/ScanMap.kt (create — runtime instance, distinct from data model)
- app/src/main/java/com/scrollshield/feature/mask/LoadingOverlay.kt (create)
- app/src/main/java/com/scrollshield/service/OverlayService.kt (modify — wire showLoading/hideLoading/showSkipFlash stubs)

## Rules & Constraints

- Never write to files outside the target list
- Follow existing code patterns (no Hilt in OverlayService, coroutine scopes with SupervisorJob)
- ScanMap runtime must be distinct from the data model ScanMap in data/model/
- User must never see unscanned content — no degraded mode
- Child profile mask cannot be dismissed without PIN
- All paths relative to /home/devuser/dev-worktree-1/
- MediaProjection captures the underlying app window, not the overlay layer — loading overlay does not interfere with screen capture

## Review Criteria

1. Loading overlay appears within 500ms of target app foregrounding
2. Pre-scan of 10 items completes in < 6 seconds (design, not runtime test)
3. ScanMap correctly stores all pre-scanned items with skip decisions
4. Feed mutation detection triggers re-scan on fingerprint mismatch
5. When user outruns buffer, loading overlay reappears and new pre-scan runs
6. Child profile: mask not dismissable without PIN, hard stop at time budget
7. Low-memory fallback reduces buffer to 5 and disables lookahead
8. Back-stack limit detection stops pre-scan early on duplicates
9. Thread safety: all ScanMap access guarded by Mutex
10. Integration: OverlayService stubs wired, AdCounterManager updated with pre-scan metrics

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew assembleDebug
```
