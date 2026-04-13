# Approved Plan — WI-09: Scroll Mask Pre-Scan & Core

Score: 10/10 after 5 iterations (Planner-Reviewer loop)

## Files to Create/Modify

### 1. feature/mask/ScanMap.kt (create)
- ScanMapRuntime class — all methods Mutex-guarded
- Methods: addItem, advanceUserHead, setExtending, setLastValidatedHash, shouldSkip, getItem, bufferRemaining, isScanned, isDuplicate, clear, snapshot

### 2. feature/mask/LoadingOverlay.kt (create)
- Full-screen MATCH_PARENT overlay (TYPE_APPLICATION_OVERLAY), dark background, shield icon, progress bar, status text (child/adult variants), muted subtitle
- Comment documenting FLAG_NOT_TOUCH_MODAL safety (MATCH_PARENT = no "outside")
- showPinChallenge() delegates to existing PinEntryView; restoreOverlayContent() on cancel
- showHardStopScreen() — undismissable "Time's up" with no close button
- show() returns elapsed ms; LATENCY_CONTRACT_MS = 500L constant

### 3. feature/mask/PreScanController.kt (create)
- Constants: bufferSize=10, lowMemory=5, settleMs=100, captureBudget=15ms, threshold=4GB
- runPreScan: scrollForwardFast(1) per item, settle, captureFrame, buildFeedItem, classify, isDuplicate check (early stop), addItem, rewind, fingerprint comparison. Returns PreScanResult(itemsScanned, durationMs)
- effectiveBufferSize(), shouldDisableLookahead()
- Inline comment explaining metrics split (ad counts → classifiedItems.tryEmit → AdCounterManager; mask stats → ScrollMaskManager.preScanStats)

### 4. feature/mask/ScrollMaskManager.kt (create)
- Synchronous overlay.show() in onSessionStart() with check(Looper == MainLooper)
- Logs warning if overlay.show() exceeds 500ms
- scope.launch for pre-scan coroutine after overlay visible
- onUserScroll: shouldSkip check, buffer exhaustion → onBufferExhausted (overlay + re-scan)
- requestDismiss(): adult → endSession; child → showPinChallenge; hardStopped → no-op
- onHardStop(): cancel pre-scan, showHardStopScreen, hardStopped=true
- publishClassifiedItems: tryEmit to AdCounterManager.classifiedItems
- PreScanStats data class + preScanStats StateFlow (itemsPreScanned, preScanDurationMs, status)
- Inline doc on preScanStats explaining mask-lifecycle metrics ownership

### 5. service/OverlayService.kt (modify)
- showSkipFlash: 300ms dark flash with auto-dismiss
- showLoading: delegates to LoadingOverlay via OverlayHost
- scrollMaskManager field + setScrollMaskManager
- Lifecycle: onSessionStart on APP_FOREGROUND, onSessionEnd on APP_BACKGROUND
- ACTION_HARD_STOP in receiver + intent filter → scrollMaskManager.onHardStop()

### AdCounterManager.kt — NOT modified
- Ad counts flow via classifiedItems.tryEmit()
- Pre-scan stats owned by ScrollMaskManager.preScanStats
