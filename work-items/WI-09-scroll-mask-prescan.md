# WI-09: Scroll Mask — Pre-Scan & Core

## Source
- Module 4: Scroll Mask (Pre-Scan Phase, Configuration, Loading Overlay UI, Child Profile Behaviour, Edge Cases)
- Architecture: "Key Insight: Pre-Scan Buffer" section
- File Structure: `feature/mask/`

## Goal
Implement the Scroll Mask pre-scan phase: loading overlay, fast-forward scan, classification, ScanMap construction, rewind, and feed mutation detection.

## Context

### Key Insight: Pre-Scan Buffer (verbatim from spec)
TikTok, Instagram Reels, and YouTube Shorts all maintain a back-stack — you can scroll forward and backward through previously loaded videos. ScrollShield exploits this by pre-scrolling the feed at session start, classifying every item, then scrolling back to the beginning. When the user starts swiping, ScrollShield already knows what's ahead and can skip flagged items with zero latency — the decision was made seconds ago.

This eliminates the timing race between classification and video rendering. There is no subliminal flash of ad content. No racing the renderer. The only cost is a few seconds of loading time at session start, masked by a branded animation.

### Core Design Principle
The user never leaves the native app. TikTok renders normally — full video, audio, native UI. ScrollShield is invisible during normal playback; the only visible interruptions are the pre-scan loading animation (~5s) and brief skip flashes.

## Dependencies
- **Hard**: WI-02 (ScanMap, ClassifiedItem), WI-05 (gesture dispatch, lifecycle events), WI-06 (classification pipeline), WI-07 (active profile for skip decisions)
- **Integration**: WI-08 (OverlayService for loading overlay)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/feature/mask/ScrollMaskManager.kt`
- `app/src/main/java/com/scrollshield/feature/mask/PreScanController.kt`
- `app/src/main/java/com/scrollshield/feature/mask/ScanMap.kt` (runtime instance, distinct from data model)
- `app/src/main/java/com/scrollshield/feature/mask/LoadingOverlay.kt`

## Detailed Specification

### Pre-Scan Phase (session start)
When the user opens a target app with mask enabled:

1. **Show loading overlay**: Full-screen branded animation over the app. Shield icon with progress indicator. Text: "ScrollShield is preparing your feed." Blocks interaction during pre-scan.

2. **Fast-forward scan**: Call `feedInterceptionService.scrollForwardFast(bufferSize)` where `bufferSize` is configurable (default: 10). For each item:
   - Capture FeedItem via feed interception service
   - Classify via classification pipeline (computes skip decision)
   - Store in ScanMap

3. **Rewind to start**: Call `feedInterceptionService.scrollBackwardFast(bufferSize)`. **Feed mutation risk**: After rewind, verify position 0 fingerprint matches pre-scan snapshot. If platform mutated feed during pre-scan, re-scan from new position 0.

4. **Dismiss loading overlay**: User sees first video. ScrollShield is ready.

**Expected pre-scan duration**: 10 items x (200ms gesture + 60ms classification) = ~2.6s, plus ~2s rewind. Total: **~5 seconds**.

### ScanMap Runtime
- All mutable access guarded by `Mutex` for thread safety between pre-scan coroutine and user scroll handler
- `scanHead`: how far ahead pre-scan has reached
- `userHead`: where user currently is
- `skipIndices`: feed positions flagged for auto-skip
- `isExtending`: true while lookahead extension is running
- `lastValidatedHash`: feed fingerprint for re-entry validation

### Loading Overlay UI
- Full-screen overlay, dark background with subtle blur
- Center: animated shield icon (pulsing or rotating)
- Below: progress bar showing pre-scan progress (e.g., "Scanning 4/10")
- Below progress: "ScrollShield is preparing your feed"
- Below that (small, muted): "Filtering ads and unwanted content"
- For child profile: "Setting up a safe feed for [child name]"

### Configuration Constants
```
preScanBufferSize = 10
gestureIntervalMs = 200
gestureDurationMs = 100
```

### Edge Case: Back-Stack Limit
If `scrollForward` detects duplicate items (end of back-stack):
- Stop pre-scan early
- Adjust `extensionTriggerDistance` to trigger earlier

### Edge Case: User Scrolls Faster Than Extension
If user reaches edge of ScanMap before extension completes:
1. Loading overlay reappears: "ScrollShield is scanning ahead."
2. New pre-scan runs from current position
3. Loading overlay dismisses, user continues with full buffer

The user never sees unscanned content. For child profiles this is especially important — the child never sees an unvetted item.

### Child Profile Behaviour
- Mask always active, cannot be dismissed without PIN
- Pre-scan blocks: OFFICIAL_AD, INFLUENCER_PROMO, ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, plus all items in blocked categories
- At time budget 100%: mask skips ALL content. Skip flash shows "Time's up — ask a parent"
- Loading overlay uses child-friendly language and animation

### Safe Mode
When both ad counter and scroll mask are disabled, ScrollShield enters safe mode: observation-only. Accessibility service remains active for session recording and analytics but performs no gesture dispatch or overlay rendering.

### Re-Ranking (Deferred to V2)
Re-ranking feed items based on user interest scores is deferred to V2. The current architecture cannot reorder the feed without rebuilding the native app's UI.

### Low-Memory Fallback (<4GB device RAM)
- Reduce pre-scan buffer to 5 items
- Disable lookahead extension (rely on re-scan when user reaches edge)
- Priority: maintain classification accuracy over buffer size

## Acceptance Criteria
- **Loading overlay appears within 500ms of target app foregrounding**
- Pre-scan of 10 items completes in < 6 seconds
- Rewind returns to the first video accurately
- Loading overlay dismisses and user sees first video
- **Pre-computed skip decisions execute with zero additional classification latency**
- ScanMap correctly stores all pre-scanned items with skip decisions
- Feed mutation detection works (fingerprint mismatch triggers re-scan)
- When user outruns buffer, loading overlay reappears and a new pre-scan runs from current position
- User never sees unscanned content — no degraded mode exists
- Child profile: mask not dismissable without PIN
- Child profile: hard stop at time budget
- **No visible lag or stutter in native video playback during normal use**
- **Pre-scan does not cause TikTok to crash, rate-limit, or degrade feed quality**

## Notes
- Open Question 2 (Pre-scan detectability): Does TikTok detect rapid automated scrolling and penalise the account? May need to slow pre-scan to 300-400ms per item. A/B test with 200ms vs 350ms intervals on test accounts.
- Open Question 6 (Battery during pre-scan): The pre-scan is a burst of activity. Measure peak power draw and thermal impact.
