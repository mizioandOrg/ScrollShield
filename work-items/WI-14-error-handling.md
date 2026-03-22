# WI-14: Error Handling & Diagnostics

## Source
- Error Handling & Recovery section (entire)
- Security & Privacy section
- File Structure: `error/`

## Goal
Implement centralized error handling, recovery strategies, and structured diagnostic logging.

## Context
ScrollShield must handle: accessibility service disconnection, TFLite model load failure, database corruption, gesture dispatch failure, and pre-scan timeout. Each has a specific recovery strategy defined in the spec.

## Dependencies
- **Hard**: WI-05 (accessibility service + MediaProjection), WI-06 (TFLite models — visual + text), WI-03 (database), WI-09 (pre-scan)
- **Integration**: WI-08 (pill overlay for status indicators), WI-16 (ScreenCaptureManager error states)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/error/ErrorRecoveryManager.kt`
- `app/src/main/java/com/scrollshield/error/DiagnosticLogger.kt`

## Detailed Specification

### Accessibility Service Disconnection
- Android may kill the accessibility service under memory pressure
- On disconnect: show persistent notification "ScrollShield protection paused"
- On reconnect: restart service, validate ScanMap if session was active

### TFLite Model Load Failure
- If **visual model** file is corrupted or missing: fall back to text-only classification (Tier 0 + Tier 2)
- If **text model** file is corrupted or missing: fall back to Tier 0 + Tier 1 visual only
- If **both** models fail: fall back to Tier 0 only (signature + label matching)
- Log diagnostic event
- Show subtle indicator on pill overlay

### Database Corruption
- Room database corruption: delete and recreate database
- DataStore preferences survive database reset (stored separately)
- Log corruption event for diagnostics

### Gesture Dispatch Failure
- Track consecutive gesture failures
- After 3 consecutive failures: disable scroll mask for remainder of session
- Show notification: "Scroll protection paused — feed interaction failed"

### MediaProjection Revocation
- If `MediaProjection` is revoked during a session (user revokes from system settings or notification):
  - Classification pipeline degrades to text-only (Tier 0 + Tier 2)
  - Show persistent notification: "Visual protection paused — screen capture revoked"
  - Log diagnostic event
  - On next session start, re-request MediaProjection permission

### Visual Model Load Failure
- If visual classifier TFLite model is corrupted or missing:
  - Fall back to text-only classification (Tier 0 + Tier 2)
  - Log diagnostic event
  - Show subtle indicator on pill overlay (similar to existing TFLite failure indicator)

### Pre-Scan Timeout
- If pre-scan does not complete within 15 seconds: abort pre-scan
- Fall back to live classification mode (classify each item as user scrolls)
- Show brief notification on loading overlay: "Pre-scan timed out — running in live mode"

### DiagnosticLogger
- Structured logging for debugging
- Log events: service connect/disconnect, model load/fail, gesture success/fail, pre-scan start/complete/timeout, database operations, MediaProjection grant/revoke, visual model load/fail, frame capture success/fail, visual vs text classification counts per session
- Do not log user content (captions, creator names) — only metadata

### Security Enforcement
- Verify ProGuard rules strip HTTP client classes outside signature sync module
- Verify export contains no raw content (captions, creator names) — only aggregated statistics
- Verify zero network calls during classification, session recording, and reporting flows

## Acceptance Criteria
- Accessibility service disconnection shows notification
- Service reconnection validates ScanMap
- TFLite failure gracefully degrades to Tier 1+2
- Database corruption detected and recovered
- 3 consecutive gesture failures disables mask for session
- Pre-scan timeout at 15s with fallback to live mode
- Diagnostic logs contain no user content
- ProGuard rules correctly strip HTTP classes outside sync module
- MediaProjection revocation shows notification and degrades gracefully
- Visual model failure degrades to text-only classification
- Text model failure degrades to visual-only classification
- Both model failure degrades to Tier 0 only

## Notes
- The thermal throttling fallback (skip Tier 3 if device overheating) is implemented in WI-06 but the temperature monitoring can be wired through this error handling infrastructure.
