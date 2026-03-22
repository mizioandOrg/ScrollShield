# WI-16: Screen Capture Infrastructure (MediaProjection)

## Source
- Module 1: Feed Interception Service (Fallback Strategy — elevated to primary)
- Module 2: Classification Pipeline (Tier 1 Visual Classification input)
- Architecture: Screen Capture Service box

## Goal
Implement the MediaProjection-based screen capture infrastructure that provides visual input for the primary Tier 1 visual classification path. This includes permission management, VirtualDisplay/ImageReader setup, frame acquisition, and graceful degradation when permission is denied.

## Context
MediaProjection screen capture is the foundation of ScrollShield's visual-first classification architecture. It captures the actual rendered pixels from the source app, providing input that cannot be defeated by accessibility tree manipulation. The ScreenCaptureManager is consumed by the classification pipeline (WI-06) and the feed interception service (WI-05) to attach screen captures to every FeedItem.

## Dependencies
- **Hard**: WI-01 (project scaffolding, manifest permissions including FOREGROUND_SERVICE_MEDIA_PROJECTION), WI-02 (FeedItem model with screenCapture field)
- **Integration**: WI-05 (FeedInterceptionService manages MediaProjection lifecycle alongside accessibility service), WI-06 (VisualClassifier consumes captured frames), WI-11 (onboarding requests MediaProjection permission)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt`
- `app/src/main/java/com/scrollshield/service/MediaProjectionHolder.kt`
- `app/src/main/java/com/scrollshield/di/MediaProjectionModule.kt`

## Detailed Specification

### ScreenCaptureManager
```kotlin
@Singleton
class ScreenCaptureManager @Inject constructor(
    private val context: Context,
    private val mediaProjectionHolder: MediaProjectionHolder
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    suspend fun captureFrame(): Bitmap? { /* ... */ }
    fun isAvailable(): Boolean { /* ... */ }
    fun start(mediaProjection: MediaProjection) { /* ... */ }
    fun stop() { /* ... */ }
}
```

### Frame Acquisition
1. `ImageReader` created at device display resolution with `PixelFormat.RGBA_8888`, max 2 images
2. `VirtualDisplay` created from `MediaProjection` connected to `ImageReader.surface`
3. `captureFrame()`:
   - Call `imageReader.acquireLatestImage()`
   - Convert `Image` planes to `Bitmap`
   - Crop: remove status bar (top `WindowInsets.statusBars` height) and navigation bar (bottom `WindowInsets.navigationBars` height)
   - Return cropped `Bitmap`
   - Close `Image` immediately to release buffer
4. Frame staleness check: if `Image.timestamp` is older than 200ms, skip and return null (stale frame from before gesture)

### MediaProjectionHolder
- Holds the `MediaProjection` token obtained from `onActivityResult` during onboarding
- Stores token intent in `EncryptedSharedPreferences` for session recovery
- On `MediaProjection` callback `onStop()`: set `isAvailable = false`, notify ScreenCaptureManager

### Permission Flow
- During onboarding (WI-11): `MediaProjectionManager.createScreenCaptureIntent()` → `startActivityForResult`
- On grant: store result intent, initialize ScreenCaptureManager
- On deny: store denial, show warning, proceed with text-only classification
- In settings: "Re-grant screen capture" button to re-trigger permission flow

### Foreground Service Requirement
- MediaProjection requires an active foreground service (Android 10+)
- Reuse the existing `FeedInterceptionService` foreground notification or create a dedicated `ScreenCaptureService`
- Notification: "ScrollShield visual protection active" (low priority, persistent)

### Performance Requirements
- Frame capture: < 15ms (measured from `acquireLatestImage()` to cropped Bitmap returned)
- Memory: single frame buffer ~3MB at 1080p RGBA
- No frame accumulation — acquire and release immediately

### Error Handling
- `MediaProjection` revoked: set `isAvailable = false`, ScreenCaptureManager returns null, pipeline degrades to text-only
- `ImageReader` returns null image: retry once after 50ms, then return null
- `SecurityException` on `createVirtualDisplay`: log, set unavailable, degrade gracefully

## Acceptance Criteria
- Frame capture completes in < 15ms
- Captured frames correctly exclude status bar and navigation bar
- Stale frame detection works (frames older than 200ms rejected)
- MediaProjection denial results in `isAvailable() == false`, not a crash
- MediaProjection revocation mid-session degrades gracefully
- Foreground service notification displayed while capture is active
- Memory: no frame buffer leak (Image closed after each acquisition)
- Works on API 28+ (MediaProjection available since API 21)

## Notes
- Android 14 (API 34) requires `FOREGROUND_SERVICE_MEDIA_PROJECTION` type declaration in manifest — handled by WI-01.
- Some OEMs may restrict MediaProjection in battery saver mode. Add to OEM-specific guidance in onboarding (see Open Question 9 in tech spec).
- The `MediaProjection` token from `onActivityResult` can be reused across app restarts by storing and replaying the result intent — test on API 28-34 for compatibility.
