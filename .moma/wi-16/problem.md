# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: sonnet
Implement: yes

## Task Description

Implement WI-16: Screen Capture Infrastructure (MediaProjection) for the ScrollShield Android project at `/home/devuser/dev-worktree-1`.

Implement the MediaProjection-based screen capture infrastructure that provides visual input for the primary Tier 1 visual classification path. This includes:

- `ScreenCaptureManager`: manages `ImageReader` / `VirtualDisplay` lifecycle, acquires and crops frames (remove status bar top inset and navigation bar bottom inset), performs stale-frame detection (reject if timestamp > 200ms old), retries once on null image after 50ms, exposes `captureFrame(): Bitmap?`, `isAvailable(): Boolean`, `start(MediaProjection)`, and `stop()`.
- `MediaProjectionHolder`: holds the `MediaProjection` token, stores the result intent in `EncryptedSharedPreferences` for session recovery, and notifies `ScreenCaptureManager` on `MediaProjection.Callback.onStop()`.
- `MediaProjectionModule`: Hilt module wiring `MediaProjectionHolder` and `ScreenCaptureManager` as singletons (expand the existing stub).

Frame acquisition spec:
- `ImageReader` at device display resolution, `PixelFormat.RGBA_8888`, max 2 images
- `VirtualDisplay` connected to `ImageReader.surface`
- Crop: remove status bar (top `WindowInsets.statusBars` height) and navigation bar (bottom `WindowInsets.navigationBars` height)
- Stale check: if `Image.timestamp` is older than 200ms, return null
- On null image: retry once after 50ms, then return null
- Close `Image` immediately after each acquisition
- Performance target: < 15ms from `acquireLatestImage()` to cropped Bitmap

Foreground service: reuse `FeedInterceptionService` foreground notification or show "ScrollShield visual protection active" (low priority, persistent) while capture is active.

Graceful degradation: `SecurityException` on `createVirtualDisplay` or `MediaProjection` revocation → set unavailable, return null from `captureFrame()`, do not crash.

## Context Files

- work-items/WI-16-screen-capture-service.md
- app/src/main/java/com/scrollshield/data/model/FeedItem.kt
- app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt

## Target Files (primary; planner may modify additional files if needed)

- app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt
- app/src/main/java/com/scrollshield/service/MediaProjectionHolder.kt
- app/src/main/java/com/scrollshield/di/MediaProjectionModule.kt
- app/src/main/AndroidManifest.xml
- app/build.gradle.kts

## Rules & Constraints

- Keep the public API of `ScreenCaptureManager` compatible with how it is consumed by `ClassificationPipeline` and `FeedInterceptionService`
- Use Hilt (`@Singleton`, `@Inject`) for all dependency injection
- Must compile and run correctly on API 28+ (minSdk = 28, targetSdk = 34)
- Use `EncryptedSharedPreferences` (androidx.security.crypto) for storing the MediaProjection result intent in `MediaProjectionHolder`
- Do not accumulate frames — acquire and release immediately

## Review Criteria

1. `ScreenCaptureManager.captureFrame()` correctly acquires, crops (status bar + nav bar removed), and returns a Bitmap within the 15ms budget
2. Stale frame detection: frames with `Image.timestamp` older than 200ms are rejected (return null) rather than returned
3. Null image retry: if `acquireLatestImage()` returns null, retries exactly once after 50ms before returning null
4. MediaProjection denial results in `isAvailable()` returning false — no crash or exception propagated to caller
5. MediaProjection revocation mid-session (via `Callback.onStop()`) gracefully sets unavailable and causes `captureFrame()` to return null
6. Foreground service notification is shown while screen capture is active and removed when stopped
7. No frame buffer leak — every acquired `Image` is closed in a finally block regardless of success or exception
8. `MediaProjectionHolder` persists the result intent to `EncryptedSharedPreferences` and can recover it across app restarts
9. All three target files compile cleanly on API 28+ with no use of APIs above targetSdk = 34
10. `ScreenCaptureManager` and `MediaProjectionHolder` are properly provided as Hilt singletons in `MediaProjectionModule`, with no missing or duplicate bindings

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew :app:compileDebugKotlin
```
