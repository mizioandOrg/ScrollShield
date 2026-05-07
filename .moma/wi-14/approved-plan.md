# Approved Plan — WI-14: Error Handling & Diagnostics

Approved at Iteration 2, Score: 10/10

## New File: `app/src/main/java/com/scrollshield/error/DiagnosticLogger.kt`

- **Package**: `com.scrollshield.error`
- **Annotation**: `@Singleton` with `@Inject constructor` (Hilt-managed)
- **Sealed class `DiagnosticEvent`** with subtypes: ServiceConnected, ServiceDisconnected, VisualModelLoaded, VisualModelFailed, TextModelLoaded, TextModelFailed, GestureSuccess, GestureFailed, PreScanStarted, PreScanCompleted, PreScanTimeout, DatabaseCorruption, DatabaseRecreated, MediaProjectionGranted, MediaProjectionRevoked, FrameCaptureSuccess, FrameCaptureFailed, SessionClassificationSummary
- **Core method**: `fun log(event: DiagnosticEvent)` — structured `Log.d("ScrollShield.Diag", ...)` format
- **Per-session counters**: visual/text/tier0 classification counts, frame capture success/fail, gesture success/fail
- **Privacy enforcement**: sealed class hierarchy prevents freeform string logging

## New File: `app/src/main/java/com/scrollshield/error/ErrorRecoveryManager.kt`

- **Package**: `com.scrollshield.error`
- **Annotation**: `@Singleton` with `@Inject constructor`
- **Dependencies**: `@ApplicationContext context: Context`, `DiagnosticLogger`
- **Atomic state**: consecutiveGestureFailures, gestureDisabledForSession, visualModelAvailable, textModelAvailable, mediaProjectionAvailable, needsMediaProjectionReRequest
- **Notification IDs**: 1003 (protection paused), 1004 (gesture failed), 1005 (visual paused)
- **Enum ModelDegradationState**: FULL, VISUAL_ONLY, TEXT_ONLY, TIER0_ONLY
- **Methods**: onServiceDisconnected, onServiceReconnected, onVisualModelLoadFailed, onTextModelLoadFailed, onVisualModelLoaded, onTextModelLoaded, onGestureResult, isGestureDisabledForSession, onMediaProjectionRevoked, onMediaProjectionGranted, needsMediaProjectionReRequest, shouldSkipVisualClassification, shouldSkipTextClassification, isDegraded, onDatabaseCorruption, onDatabaseRecreated, resetSession

## Changes to DatabaseModule.kt

Force-open-and-validate pattern: call `db.openHelper.writableDatabase` after build, catch SQLiteDatabaseCorruptException, delete and recreate. DataStore survives (separate file path).

## Changes to ClassificationPipelineEntryPoint

Expand with `errorRecoveryManager()` and `diagnosticLogger()` methods.

## Changes to ClassificationPipeline.kt

Add ErrorRecoveryManager and DiagnosticLogger. Gate Tier 1 with `shouldSkipVisualClassification()`, gate Tier 2 with `shouldSkipTextClassification()`. Track classification counts.

## Changes to VisualClassifier.kt / ContentClassifier.kt

Add ErrorRecoveryManager. Wrap model loading with error reporting. Nullable interpreter handling.

## Changes to ScreenCaptureManager.kt

Add optional ErrorRecoveryManager and DiagnosticLogger. Register MediaProjection.Callback for revocation. Log frame capture events.

## Changes to FeedInterceptionService.kt

Resolve ErrorRecoveryManager/DiagnosticLogger via EntryPoint. Log service lifecycle. Report gesture results. Check gestureDisabledForSession. Pass error managers to ScrollMaskManager.

## Changes to PreScanController.kt

Add optional DiagnosticLogger. Wrap runPreScan in withTimeoutOrNull(15_000L). Add timedOut to PreScanResult.

## Changes to ScrollMaskManager.kt

Add optional ErrorRecoveryManager and DiagnosticLogger. Call resetSession on session start. Handle pre-scan timeout. Check gestureDisabledForSession.

## Changes to AdCounterOverlay.kt

Add optional ErrorRecoveryManager. Add amber warning indicator. Show/hide in render() based on isDegraded().

## Changes to OverlayService.kt

Resolve ErrorRecoveryManager via EntryPointAccessors in showPill(). Pass to AdCounterOverlay.

## Changes to MediaProjectionHolder.kt / MediaProjectionModule.kt

Add ErrorRecoveryManager. Report revocation/grant events.
