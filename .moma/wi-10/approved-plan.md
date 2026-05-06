# Approved Plan — WI-10: Scroll Mask — Live Mode & Extensions

**Score: 10/10 (Iteration 6)**
**Score progression:** 4 → 7 → 6 → 7 → 9 → 10

---

## FILE 1: SkipFlashOverlay.kt (CREATE)

**Path:** `app/src/main/java/com/scrollshield/feature/mask/SkipFlashOverlay.kt`

- Class `SkipFlashOverlay(context: Context)`, not Hilt-injected
- Constants: `DISPLAY_MS=200L`, `FADE_OUT_MS=150L`, key = `OverlayService.KEY_SKIP_FLASH`
- `show(host: OverlayHost, categories: String)`:
  - Builds LinearLayout (vertical, centered, semi-transparent black 0xCC000000)
  - Shield text "ScrollShield" (bold, white, 28sp)
  - Category text (gray #AAAAAA, 16sp)
  - TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE
  - After 200ms: ObjectAnimator alpha 1→0 over 150ms, removes view in onAnimationEnd
- `hide(host)`: removes view
- Private `dpToPx` helper
- **Replaces** all `overlayHost.showSkipFlash()` calls in ScrollMaskManager

## FILE 2: ConsecutiveSkipHandler.kt (CREATE)

**Path:** `app/src/main/java/com/scrollshield/feature/mask/ConsecutiveSkipHandler.kt`

- Class `ConsecutiveSkipHandler(skipFlashOverlay: SkipFlashOverlay, overlayHost: OverlayHost)`, not Hilt-injected
- Constants: `MAX_CONSECUTIVE_SKIPS=5`, `CONSECUTIVE_DELAY_MS=300L`
- `@Volatile var isHighDensityBlocking: Boolean`
- `suspend fun onSkip(item: ClassifiedItem): Boolean`:
  - Increments consecutiveCount, accumulates category
  - If count > 5: shows high-density overlay, returns false (6th attempt triggers)
  - If count > 1: delays 300ms between skips
  - Returns true (skip allowed)
- `fun showBatchFlash()`: shows combined flash with distinct categories joined
- `fun onNonSkip()`: shows batch flash if accumulated, resets count
- `private fun showHighDensityOverlay()`: full-screen "High ad density" + "Tap anywhere to continue", OnClickListener → onHighDensityDismissed()
- `fun onHighDensityDismissed()`: resets state, removes overlay
- `fun reset()`: resets all state

## FILE 3: LookaheadExtender.kt (CREATE)

**Path:** `app/src/main/java/com/scrollshield/feature/mask/LookaheadExtender.kt`

- Class `LookaheadExtender(feedInterceptionService, screenCaptureManager, classificationPipeline, scope: CoroutineScope)`, not Hilt-injected
- Constants: `TRIGGER_THRESHOLD=3`, `EXTENSION_SIZE=5`, `FRAME_SETTLE_MS=100L`
- `@Volatile var isExtending: Boolean`
- `fun shouldTrigger(bufferRemaining: Int): Boolean`: true when <=3 and not extending
- `fun extend(scanMap, profile, onCatchUp: suspend () -> Unit)`:
  - Launches coroutine in scope
  - `repeat(EXTENSION_SIZE)`: scrollForwardFast(1).join(), delay(100), captureFrame(), buildFeedItem(), classify(), addItem()
  - Duplicate detection → early stop
  - Rewinds by actual items scanned
  - Sets extending flags in finally block
- `suspend fun checkCatchUp(scanMap, position, onCatchUp)`: if extending && !isScanned → cancel + onCatchUp()
- `fun cancel()`: cancels job, resets state
- Private helpers: `buildFeedItem(app, position, capture)` and `sha256(input)` (replicated from PreScanController — private methods, can't access)

## FILE 4: InterestLearner.kt (CREATE)

**Path:** `app/src/main/java/com/scrollshield/feature/mask/InterestLearner.kt`

- Class `InterestLearner(profileManager: ProfileManager, sessionDao: SessionDao)`, not Hilt-injected
- Constants: `ALPHA_EARLY=0.2f`, `ALPHA_MATURE=0.05f`, `EARLY_SESSION_THRESHOLD=5`, `VECTOR_SIZE=20`
- `suspend fun onItemViewed(profile: UserProfile, item: ClassifiedItem)`:
  - Guard: `if (profile.isChildProfile) return`
  - One-hot observed vector at `item.topicCategory.index`
  - EMA: `newVector[i] = (1 - alpha) * old[i] + alpha * observed[i]`
  - Persists via `profileManager.updateProfile(profile.copy(interestVector = newVector))`
- `private suspend fun computeAlpha(profileId: String): Float`:
  - `sessionDao.getSessionsByProfileSince(profileId, 0L).size`
  - <5 → 0.2f, >=5 → 0.05f
- Wiring KDoc: receives SessionDao via ScrollMaskManager.initialize()

## FILE 5: ScrollMaskManager.kt (MODIFY — full rewrite)

**Path:** `app/src/main/java/com/scrollshield/feature/mask/ScrollMaskManager.kt`

### Constructor — UNCHANGED (6 params)
```
ScrollMaskManager(context, feedInterceptionService, screenCaptureManager,
                  classificationPipeline, profileManager, overlayHost)
```

### New fields (after existing private fields)
```kotlin
private var skipFlashOverlay: SkipFlashOverlay? = null
private var consecutiveSkipHandler: ConsecutiveSkipHandler? = null
private var lookaheadExtender: LookaheadExtender? = null
private var interestLearner: InterestLearner? = null
```

### initialize(sessionDao: SessionDao)
- KDoc documents wiring contract: OverlayService.onCreate() creates sessionDao from Room; caller that constructs ScrollMaskManager must call initialize(sessionDao)
- Creates: preScanController, skipFlashOverlay, consecutiveSkipHandler, lookaheadExtender, interestLearner
- Registers broadcast receiver (existing)
- No existing callers in context files — signature change is safe

### onUserScroll(position: Int) — rewritten
1. `if (feedInterceptionService.isOwnGesture) return` — first line (Criterion 7)
2. scope.launch: advanceUserHead, check childHardStop, check isHighDensityBlocking
3. `checkCatchUp(map, position)` → onCatchUp() if caught up (Criterion 6)
4. If shouldSkip: `handler.onSkip(item)` → if allowed, `performSkip(map, position)` + emit to AdCounterManager. **No early return** — falls through to checkLookahead (Criterion 5 fix)
5. If !shouldSkip: `handler.onNonSkip()`, `interestLearner.onItemViewed(profile, item)` (Criterion 8)
6. **Always:** `checkLookahead()` — runs after BOTH skip and non-skip paths (Criterion 5)

### performSkip(map, position) — private suspend
- `feedInterceptionService.scrollForward().join()` (150ms, within 500ms — Criterion 1)
- `map.advanceUserHead(position + 1)`

### checkLookahead() — private suspend
- Gets bufferRemaining from scanMap
- If `shouldTrigger(remaining)`: calls `extender.extend(map, profile) { onCatchUp() }`
- Idempotent — shouldTrigger guards via isExtending flag

### onCatchUp() — private suspend
- Shows loading overlay (Criterion 6)
- Runs full `runPreScan`
- Hides overlay

### Preserved flows (no changes)
- onSessionStart — existing pre-scan flow intact
- requestDismiss — existing PIN challenge flow intact
- onHardStop — existing hard-stop flow intact + cancels lookahead
- onSessionEnd — clears map, resets handlers, cancels lookahead

### destroy()
- Cancels lookahead + scope

### New imports
```kotlin
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.model.SkipDecision
```
