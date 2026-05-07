# MoMa Summary — WI-14: Error Handling & Diagnostics

## Orchestration
- Model: opus
- Iterations: 2 of 5
- Final score: 10/10
- Date: 2026-05-06

## What was implemented

### New files
- `app/src/main/java/com/scrollshield/error/DiagnosticLogger.kt` — Structured diagnostic logging with sealed event types and per-session counters
- `app/src/main/java/com/scrollshield/error/ErrorRecoveryManager.kt` — Centralized error recovery with atomic state, notifications, and graceful degradation

### Modified files (13)
- `ClassificationPipeline.kt` — Tier 1/2 gating via degradation flags, classification count tracking
- `ContentClassifier.kt` — Nullable interpreter, text model load failure reporting
- `VisualClassifier.kt` — Nullable interpreter, visual model load failure reporting
- `ScreenCaptureManager.kt` — MediaProjection.Callback for revocation, frame capture logging
- `DatabaseModule.kt` — Force-open-and-validate corruption detection, delete & recreate
- `MediaProjectionModule.kt` — ErrorRecoveryManager/DiagnosticLogger injection
- `AdCounterOverlay.kt` — Amber warning indicator for degraded state
- `PreScanController.kt` — 15s timeout via withTimeoutOrNull, PreScanResult.timedOut
- `ScrollMaskManager.kt` — Session reset, timeout handling, gesture disable check
- `ClassificationPipelineEntryPoint.kt` — Added errorRecoveryManager/diagnosticLogger accessors
- `FeedInterceptionService.kt` — Service lifecycle logging, gesture tracking, ScanMap validation
- `MediaProjectionHolder.kt` — Revocation/grant event reporting
- `OverlayService.kt` — EntryPoint resolution for pill overlay indicator

## Error scenarios covered
1. Accessibility service disconnection — persistent notification + ScanMap validation on reconnect
2. TFLite model failures — 3-level graceful degradation (visual-only, text-only, Tier 0 only)
3. Database corruption — force-open detection, delete & recreate, DataStore survives
4. Gesture dispatch failure — 3 consecutive failures disables mask for session
5. MediaProjection revocation — text-only fallback, re-request on next session
6. Pre-scan timeout — 15s abort, live classification fallback

## Version bump
- versionCode: 16 -> 17
- versionName: 0.12.5-bugfix -> 0.13.0
