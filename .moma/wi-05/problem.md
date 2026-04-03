# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: sonnet
Implement: yes

## Task Description

Implement WI-05: Feed Interception Service for the ScrollShield Android project located at `/home/devuser/dev-worktree-1`.

Create `FeedInterceptionService.kt` (an `AccessibilityService`) that:
1. Monitors TikTok (`com.zhiliaoapp.musically`), Instagram (`com.instagram.android`), and YouTube (`com.google.android.youtube`) — early return for all other packages.
2. Extracts feed content from the accessibility tree on content-change events: creator name, caption text, hashtags, and ad/sponsored label. Builds a `FeedItem` with `id = SHA-256(captionText + creatorName + app + feedPosition)`.
3. Emits `APP_FOREGROUND` / `APP_BACKGROUND` lifecycle events when target apps enter/leave foreground.
4. Dispatches gestures via `AccessibilityService.dispatchGesture()`:
   - `scrollForward()`: swipe-up (screenWidth/2, screenHeight*0.75) → (screenWidth/2, screenHeight*0.25), 150ms
   - `scrollBackward()`: swipe-down same path reversed, 150ms
   - `scrollForwardFast(n)`: chain n swipe-up gestures, 100ms each, 200ms pause between; retry once on failure, fall back to live classification mode
   - `scrollBackwardFast(n)`: chain n swipe-down gestures same timing
   - Use `getRealMetrics()` for dynamic orientation handling
5. Tracks feed position: integer counter incremented/decremented on scroll, verified via `FeedFingerprint`. `isOwnGesture` flag distinguishes user vs. programmatic scrolls.
6. Modal dialog detection during pre-scan: detect class hierarchy for `android.app.Dialog` / `isModal`; auto-dismiss with back gesture; timeout after 2s and skip item.
7. WebView detection: pause interception when WebView present in tree; resume on close with ScanMap validation via `lastValidatedHash`.
8. MediaProjection integration: manage session and `ImageReader`; attach `Bitmap` to `FeedItem.screenCapture`; frame capture budget < 15ms; show foreground notification during session; if not granted, set `FeedItem.screenCapture = null` and show persistent "visual protection unavailable" notification.
9. Service processes zero data (no traversal, no gestures, no classification) when no target app is foregrounded.

Also create the compat layer:
- `AppCompatLayer.kt`: abstract base with `extractCreator()`, `extractCaption()`, `extractAdLabel()`, `extractHashtags()`
- `TikTokCompat.kt`, `InstagramCompat.kt`, `YouTubeCompat.kt`: concrete implementations using the resource IDs specified in WI-05

## Context Files

- `app/src/main/java/com/scrollshield/data/model/FeedItem.kt`
- `app/src/main/java/com/scrollshield/util/FeedFingerprint.kt`
- `app/src/main/java/com/scrollshield/accessibility/ScrollShieldAccessibilityService.kt`
- `work-items/WI-05-feed-interception-service.md`

All paths relative to `/home/devuser/dev-worktree-1`.

## Target Files (to modify)

All paths relative to `/home/devuser/dev-worktree-1`:

- `app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt` (create)
- `app/src/main/java/com/scrollshield/compat/AppCompatLayer.kt` (create)
- `app/src/main/java/com/scrollshield/compat/TikTokCompat.kt` (create)
- `app/src/main/java/com/scrollshield/compat/InstagramCompat.kt` (create)
- `app/src/main/java/com/scrollshield/compat/YouTubeCompat.kt` (create)

## Rules & Constraints

- Do not modify any file outside the 5 target files listed above
- Do not modify `FeedItem.kt`, `FeedFingerprint.kt`, or any existing source files
- Keep public method signatures stable — WI-09 (pre-scan) and WI-16 (ScreenCaptureManager) will depend on them
- Use only Android SDK APIs and libraries already present in the project's `build.gradle.kts`; do not add new dependencies
- The existing `ScrollShieldAccessibilityService.kt` stub is separate and must remain untouched
- `FeedInterceptionService` must be declared in the manifest — but do not modify the manifest (it was done in WI-01); assume it is already declared
- All Kotlin code must compile without errors under `./gradlew :app:compileDebugKotlin`

## Review Criteria

1. `FeedInterceptionService` extends `AccessibilityService` and `onAccessibilityEvent()` performs an early return for non-target packages — no data captured from other apps.
2. Service activates (begins traversal) only when a target app is foregrounded, and deactivates completely (zero processing) when no target app is in the foreground.
3. Content extraction is correct for all three target apps using the exact resource IDs from WI-05; `FeedItem.id` is SHA-256 of `(captionText + creatorName + app + feedPosition)`.
4. All four gesture methods (`scrollForward`, `scrollBackward`, `scrollForwardFast(n)`, `scrollBackwardFast(n)`) implement the correct swipe path and timing from the spec; coordinates derived from `getRealMetrics()`.
5. `scrollForwardFast`/`scrollBackwardFast` retry once on failure and fall back to live classification mode; modal dialogs detected and auto-dismissed with back gesture (2s timeout).
6. Feed position counter is correctly incremented/decremented and verified via `FeedFingerprint`; `isOwnGesture` flag correctly distinguishes user vs. programmatic scrolls.
7. WebView detection pauses interception and resumes with `lastValidatedHash` validation on WebView close.
8. `AppCompatLayer` is an abstract class with all four extraction methods; `TikTokCompat`, `InstagramCompat`, `YouTubeCompat` are concrete implementations with the correct per-app resource ID mappings.
9. MediaProjection integration: `FeedItem.screenCapture` is populated from `ImageReader.acquireLatestImage()` when permission granted; is `null` (not a crash) when not granted; foreground notification shown; "visual protection unavailable" notification shown when not granted.
10. All 5 files compile without errors under `./gradlew :app:compileDebugKotlin`.

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -80
```
