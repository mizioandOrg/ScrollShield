# WI-10: Scroll Mask — Live Mode & Extensions

## Source
- Module 4: Scroll Mask (Live Interception Phase, Lookahead Extension, Consecutive Skip Handling, Skip Flash UI, Interest Learning)
- File Structure: `feature/mask/`

## Goal
Implement live skip execution, lookahead extension, consecutive skip handling, skip flash overlay, and interest learning.

## Context
After the pre-scan phase (WI-09), the user scrolls through the feed. This work item handles: looking up skip decisions in the ScanMap, executing skips via gesture dispatch, extending the scan buffer ahead of the user, batching consecutive skips, and updating interest vectors at session end.

## Dependencies
- **Hard**: WI-09 (ScanMap, PreScanController, ScrollMaskManager)
- **Integration**: WI-05 (gesture dispatch for skip execution), WI-08 (OverlayService for skip flash), WI-16 (ScreenCaptureManager for extension frame capture)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/feature/mask/LookaheadExtender.kt`
- `app/src/main/java/com/scrollshield/feature/mask/SkipFlashOverlay.kt`
- `app/src/main/java/com/scrollshield/feature/mask/ConsecutiveSkipHandler.kt`
- `app/src/main/java/com/scrollshield/feature/mask/InterestLearner.kt`

## Detailed Specification

### Live Interception Phase
On each user swipe:
1. Feed interception service detects scroll and reports new feed position
2. Look up item at new position in ScanMap
3. If `skipDecision` is any `SKIP_*` variant:
   - Call `feedInterceptionService.scrollForward()` to advance past it
   - Show skip flash overlay (200ms): shield icon + "Skipped: Ad" or "Skipped: [category]"
   - Ad counter records the skipped item
   - If next item also flagged, skip again (consecutive skip handling)
4. If `SHOW` or `SHOW_LOW_CONF`: do nothing, video plays normally

### Consecutive Skip Handling
When multiple adjacent items are flagged:
- Execute skips at 300ms intervals (`consecutiveSkipIntervalMs = 300`)
- Show single combined flash: "Skipped 3 ads" rather than three separate flashes
- User sees brief fast-forward effect, lands on next clean item
- **Maximum consecutive skips**: `maxConsecutiveSkips = 5`. If >5 consecutive items would be skipped, pause and show "High ad density detected" overlay. Resume on user tap.

### Lookahead Extension
When user reaches position `scanHead - 3` (`extensionTriggerDistance = 3`):
- Trigger background extension scan (no scroll suppression, runs in background coroutine)
- Scan next `extensionSize` items (default: 5) ahead
- Add results to ScanMap
- If user catches up before extension completes, re-engage full pre-scan flow (loading overlay reappears)

```
User position:  [0] [1] [2] [3] [4] [5] [6] [7] [8] [9]
Pre-scanned:    [v] [v] [v] [v] [v] [v] [v] [v] [v] [v]
                                             ^user is here
                                                      ^scan head

User reaches position 7 (scanHead - 3 = trigger):
  -> Background extension scans positions 10-14
  -> ScanMap now covers 0-14
  -> User continues scrolling unaware
```

The extension uses the same fast-forward technique but operates beyond the user's current position. It scrolls ahead, classifies (with visual capture), then scrolls back. 5 items × 295ms = ~1.5 seconds (vs. old 1.3s with text-only). This still completes before the user naturally scrolls 3 more items.

**Note on scroll-back visibility**: During lookahead extension, the user may briefly see the feed jump forward and back. For V1, this brief visual disruption is accepted as a known trade-off.

### Skip Flash UI
- Small overlay at bottom of screen, semi-transparent
- Shield icon + text: "Skipped: Ad" or "Skipped: Gambling" etc.
- Duration: 200ms (`skipFlashDurationMs = 200`), fades out
- For consecutive skips: "Skipped 3 ads" with count

### Edge Case: User Scrolls Backward
If user swipes down to revisit a previous video, ScanMap already has that item classified. No additional work needed.

### Interest Learning (session end)
- Runs at session end for default profile only, not child profiles
- Computes which items user watched fully vs. manually skipped
- Updates interest vector:
  ```
  interestVector = (1 - alpha) * interestVector + alpha * mean(watchedItemTopicVectors)
  ```
- Alpha values: `alpha = 0.05` for normal sessions (conservative), `alpha = 0.2` for the first 5 sessions (faster initial calibration)

### Configuration Constants
```
extensionSize = 5
extensionTriggerDistance = 3
skipFlashDurationMs = 200
consecutiveSkipIntervalMs = 300
liveGestureDurationMs = 150
maxConsecutiveSkips = 5
frameCaptureSettleMs = 100       // Wait after gesture for app to render before capture
```

## Acceptance Criteria
- **`performSkip()` advances to next video within 500ms**
- Skip flash displays for 200ms and fades
- Consecutive skips batch correctly with combined flash
- Maximum consecutive skips (5) enforced with "High ad density" overlay
- Lookahead extension triggers at correct distance from scan head (scanHead - 3)
- Extension completes before user reaches scan head in normal scrolling
- Backward scrolling uses pre-computed decisions (no re-classification)
- Interest vector updates correctly at session end with appropriate alpha
- Alpha = 0.2 for first 5 sessions, 0.05 thereafter
- **No visible lag or stutter in native video playback during normal use**

## Notes
- The `isOwnGesture` flag (from WI-05) is critical here to distinguish user scrolls from programmatic skips — without it, a skip gesture would trigger another skip lookup creating an infinite loop.
