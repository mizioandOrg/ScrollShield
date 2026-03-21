# WI-05: Feed Interception Service

## Source
- Module 1: Feed Interception Service (entire section)
- File Structure: `service/FeedInterceptionService.kt`
- Accessibility Service Configuration
- Per-App Extraction Strategy
- Compat layer: `compat/` package

## Goal
Implement the AccessibilityService that monitors target apps, extracts feed content, dispatches gestures, and tracks feed position.

## Context
This is the foundational service that powers both features. It runs as an Android AccessibilityService, monitors TikTok/Instagram/YouTube, extracts FeedItem data from the accessibility tree, and provides gesture dispatch for pre-scanning and auto-skipping. The user never leaves the native app.

## Dependencies
- **Hard**: WI-02 (FeedItem model), WI-04 (FeedFingerprint for lastValidatedHash)
- **Integration**: WI-01 (manifest declares service), WI-09 (pre-scan uses gesture dispatch)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt`
- `app/src/main/java/com/scrollshield/compat/AppCompatLayer.kt`
- `app/src/main/java/com/scrollshield/compat/TikTokCompat.kt`
- `app/src/main/java/com/scrollshield/compat/InstagramCompat.kt`
- `app/src/main/java/com/scrollshield/compat/YouTubeCompat.kt`

## Detailed Specification

### Target packages
- `com.zhiliaoapp.musically` (TikTok)
- `com.instagram.android`
- `com.google.android.youtube`

### Per-App Extraction Strategy
- **TikTok**: Creator name from `com.zhiliaoapp.musically:id/title`, caption from `com.zhiliaoapp.musically:id/desc`, "Sponsored" label from `com.zhiliaoapp.musically:id/ad_label`
- **Instagram**: Creator from `com.instagram.android:id/reel_viewer_title`, caption from `com.instagram.android:id/reel_viewer_caption`, "Sponsored" from `com.instagram.android:id/sponsored_label`
- **YouTube**: Creator from `com.google.android.youtube:id/reel_channel_name`, caption from `com.google.android.youtube:id/reel_multi_format_title`

These resource IDs may change between app versions — the `compat/` package provides version-adaptive strategies.

### Core Capabilities

1. **Content extraction**: On content change events, traverse accessibility tree to extract text, creator names, hashtags, ad labels. Build `FeedItem` with SHA-256 ID of `(captionText + creatorName + app + feedPosition)`.

2. **Lifecycle events**: Emit `APP_FOREGROUND` and `APP_BACKGROUND` when target apps enter/leave foreground.

3. **Gesture dispatch** via `AccessibilityService.dispatchGesture()` with `GestureDescription`:
   - `scrollForward()`: Swipe-up gesture to advance to next video
   - `scrollBackward()`: Swipe-down gesture to return to previous video
   - `scrollForwardFast(n)`: Advance n items in rapid succession (for pre-scanning)
   - `scrollBackwardFast(n)`: Return n items (to reset to start after pre-scan)
   - Swipe-up path: start at `(screenWidth/2, screenHeight*0.75)`, end at `(screenWidth/2, screenHeight*0.25)`
   - Duration: 100ms for pre-scan mode, 150ms for live skip mode
   - `scrollForwardFast(n)`: chain n swipe gestures with 200ms pause between each
   - **Error handling**: On gesture dispatch failure, retry once. If retry fails, fall back to live classification mode (no pre-scan).
   - Dynamic orientation handling: compute coordinates from `getRealMetrics()` rather than fixed values

4. **Position tracking**: Integer counter incremented/decremented on scroll events, verified via content fingerprint comparison to detect feed mutations. The `isOwnGesture` flag distinguishes user-initiated scrolls from programmatic scrolls.

5. **Modal dialog detection**: During pre-scan, detect modal dialogs (check class hierarchy for `android.app.Dialog`, `isModal` flag). If detected, auto-dismiss with back gesture; timeout after 2s and skip the item.

6. **WebView detection**: If a WebView is detected in the accessibility tree (e.g., in-app browser), pause interception. Resume on WebView close with ScanMap validation via `lastValidatedHash`.

### Privacy constraint
- `onAccessibilityEvent()` performs early return for non-target packages — no data captured from other apps.
- **Service processes zero data when in the background or when no target app is in the foreground.** This means no accessibility tree traversal, no gesture dispatch, no classification, and no session recording when the service is running but no target app is foregrounded.

### Fallback Strategy
If accessibility tree is too shallow:
1. Screen-capture OCR via `MediaProjection` API (one-time user permission). Requires foreground service notification (persistent, low-priority). Uses ML Kit Text Recognition as primary OCR engine.
2. If OCR insufficient: degrade to label-only detection (Tier 2).

### Compat Layer
- `AppCompatLayer.kt`: Base abstract class with methods `extractCreator()`, `extractCaption()`, `extractAdLabel()`, `extractHashtags()`
- `TikTokCompat.kt`, `InstagramCompat.kt`, `YouTubeCompat.kt`: Concrete implementations with versioned resource ID mappings

## Acceptance Criteria
- Activates/deactivates on target app foreground/background
- Extracts creator name, caption, hashtags from TikTok (or falls back gracefully)
- Detects "Sponsored" label with > 95% recall
- `scrollForward()` reliably advances TikTok to the next video
- `scrollBackward()` reliably returns to the previous video
- `scrollForwardFast(10)` advances 10 items in < 4 seconds
- `scrollBackwardFast(10)` returns 10 items in < 4 seconds
- Position tracking is accurate after forward/backward navigation
- No ANR under rapid gesture dispatch
- Battery impact: < 3% additional drain per hour (~20-30mW sustained)
- Service processes zero data when no target app is in the foreground

## Notes
- Open Question 1 (Back-stack depth): How many items does TikTok keep in its back-stack before evicting? If limit < 10, reduce `preScanBufferSize` dynamically. Implement back-stack limit detection (detect duplicate items during scrollForward).
- Open Question 3 (Accessibility tree during fast scroll): Does the accessibility tree update reliably at 200ms intervals? Some apps debounce accessibility events. Implement retry with backoff — if tree unchanged after gesture, wait 100ms and re-read before advancing.
- Open Question 4 (Feed position tracking accuracy): After scrollForwardFast(10) + scrollBackwardFast(10), is the user reliably back at position 0? Off-by-one errors would misalign ScanMap. Verify via content fingerprint comparison.
- Open Question 8 (TikTok version fragmentation): Accessibility tree structure may differ across TikTok versions. The compat layer must adapt. Test on the 3 most recent TikTok versions.
- Open Question 9 (Accessibility service kill by OEMs): Some OEMs aggressively kill background services. May need OEM-specific battery optimization exemption guidance in onboarding.
- Open Question 11 (Accessibility service conflicts): Test coexistence with TalkBack, LastPass, 1Password.
- Open Question 12 (X/Twitter V2): X/Twitter uses non-vertical feed architecture. Deferred to V2 with dedicated `XCompat` implementation. Key challenges: horizontal scroll within vertical feed, variable-height items, threaded conversations.
