# Approved Plan — WI-08 Ad Counter Feature

Approved at iteration 4 with score 10/10.

## AdCounterManager.kt (com.scrollshield.feature.counter)

- `@Singleton class AdCounterManager @Inject constructor(@ApplicationContext context, sessionDao: SessionDao, profileManager: ProfileManager)`.
- Companion `MutableSharedFlow<ClassifiedItem> classifiedItems` (replay=0, extraBufferCapacity=64, BufferOverflow.DROP_OLDEST) — the in-process publication contract. Future producers (or tests) call `AdCounterManager.classifiedItems.tryEmit(item)`. ClassificationPipeline.kt is read-only and exposes no Flow/callback registry.
- Internal `data class UiState`: `sessionId`, `currentApp`, `sessionStartMs`, `adsDetected`, `adsSkipped`, `itemsSeen`, `itemsPreScanned`, `preScanDurationMs`, `brands: Set<String>`, `categories: Set<String>`, `tierCounts: IntArray(3)`, `classificationCounts: Map<Classification, Int>`, `revenue: Float`, `maskActive: Boolean`, `isChildProfile: Boolean`, `budgetMinutes: Int`, `bracket: BudgetState`. Exposed as `StateFlow<UiState>`.
- Lifecycle:
  - `fun onAppForeground(app: String)` — fresh `UiState` reset (zeros all counters/sets), `sessionId = UUID.randomUUID().toString()`, `currentApp = app`, `sessionStartMs = now`. Loads profile via `scope.launch { val pid = profileManager.getActiveProfileId().first(); val profile = pid?.let { profileManager.getProfileById(it) }; uiState.update { it.copy(isChildProfile = profile?.isChildProfile == true, budgetMinutes = profile?.timeBudgets?.get(pkgToPlatform(app)) ?: profile?.timeBudgets?.get("default") ?: 0, maskActive = profile?.maskEnabled == true) } }`. Starts checkpoint and ticker jobs. Calls overlayService.showPill().
  - `fun onAppBackground()` — cancels jobs, writes final `SessionRecord(endedNormally = true)` via `sessionDao.upsert`, calls `overlayService.showSurvey { rating -> recordSatisfaction(rating) }`, then `overlayService.hidePill()`.
  - `fun endSession(rating: Int?)` / `fun recordSatisfaction(rating: Int)` — updates the just-written SessionRecord with `satisfactionRating` via another upsert.
- Init: launches Default-dispatched coroutine collecting `classifiedItems`. On each emission:
  - `itemsSeen++`
  - if `OFFICIAL_AD || INFLUENCER_PROMO`:
    - `adsDetected++`
    - `tierCounts[item.tier.coerceIn(0,2)]++`
    - merge brand/category into sets
    - `classificationCounts[c]++`
    - `revenue = adsDetected * cpmFor(currentApp) / 1000f`
    - if `state.maskActive`, also `adsSkipped++` (the mask consumer of the same flow signals skips by emitting wasSkipped via a separate `fun onAdSkipped()` API; the manager exposes `fun onAdSkipped()` to be called by WI-09).
  - Updates `_uiState.value` via `update { ... }`. Sub-millisecond latency, well within 200ms.
- 60s checkpoint coroutine: `while (isActive) { delay(60_000); writeCheckpoint() }`. `writeCheckpoint()` builds `SessionRecord(id = state.sessionId, ..., endedNormally = false, classificationCounts = state.classificationCounts.toMap(), satisfactionRating = null)` and calls `sessionDao.upsert(record)`. Same `id` ensures REPLACE strategy overwrites on final write.
- 5s ticker coroutine: re-evaluates `bracket` via `TimeBudgetNudge.evaluate(elapsedMin, budgetMin, isChild)` and updates UiState. On transition to `CHILD_HARD_STOP`, broadcasts `Intent(OverlayService.ACTION_CHILD_HARD_STOP).setPackage(packageName).putExtra(OverlayService.EXTRA_PLATFORM, pkgToPlatform(currentApp))`.
- Tier persistence: in-memory only for live session. `SessionRecord` has no extras/notes field and `classificationCounts` is keyed on `Classification` enum, not tier ints. Documented as live-only. No sidecar JSON written. Historical sessions display "Tier breakdown: not available for historical sessions" and JSON exports them as `"tierBreakdown": null, "tierBreakdownAvailable": false`.
- `pkgToPlatform(pkg: String): String?` mapping `com.zhiliaoapp.musically -> "TikTok"`, `com.instagram.android -> "Instagram"`, `com.google.android.youtube -> "YouTube"`.
- `cpmFor(app: String): Float` — uses `pkgToPlatform(app)` then looks up CPM map: TikTok 10f, Instagram 12f, YouTube 15f, default 10f. SharedPreferences override `scrollshield_counter_prefs` key `cpm_<platform>`.
- `statusDotThresholds(): Pair<Int, Int>` — defaults `(3, 11)` from `scrollshield_counter_prefs` keys `dot_amber` / `dot_red`.
- `statusColor(state: UiState): StatusColor` — Green if `adsDetected < amber`, Amber if `< red`, else Red.
- `exportJson(state: UiState): String` — `org.json.JSONObject` with first key `"version": 1`, plus `sessionId`, `app`, `startTime`, `endTime`, `durationMinutes` (Float, matches SessionRecord), `adsDetected`, `adsSkipped`, `brands` (JSONArray), `categories` (JSONArray), `revenue`, `tierBreakdown` (`{"tier0": a, "tier1": b, "tier2": c}` or `null`), `tierBreakdownAvailable` (Boolean), `itemsSeen`, `itemsPreScanned`, `classificationCounts`.
- `exportJsonForSession(record: SessionRecord): String` — historical export sharing same v1 schema; tierBreakdown=null, tierBreakdownAvailable=false.
- `exportToDownloads(json: String, fileName: String)` — `MediaStore.Downloads` on Q+, `Environment.DIRECTORY_DOWNLOADS` fallback.
- Constants: `const val ACTION_AD_CLASSIFIED = "com.scrollshield.feature.counter.AD_CLASSIFIED"` retained as documented optional intent action.

## AdCounterOverlay.kt (com.scrollshield.feature.counter)

- `class AdCounterOverlay(context: Context, manager: AdCounterManager)` exposing `val view: View`, `fun layoutParams(): WindowManager.LayoutParams`, `fun applyBudgetState(state: BudgetState)`.
- View: horizontal `LinearLayout`, height `32.dp`, `GradientDrawable` background (semi-transparent dark `#CC101418`, corner radius `16.dp`).
- Children L→R: status dot (8dp circle `View` with `GradientDrawable` oval), `TextView` adCount (bold white, `Typeface.MONOSPACE`, `11sp`), separator dot, `TextView` revenue (muted `#CCCCCC`), separator, `TextView` sessionTime (muted MM:SS).
- LayoutParams: `type = TYPE_APPLICATION_OVERLAY` (`TYPE_PHONE` pre-O), `flags = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL`, `format = PixelFormat.TRANSLUCENT`, `gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL`, `width = WRAP_CONTENT`, `height = 32.dp`.
- Position persisted via `SharedPreferences("ad_counter_overlay", MODE_PRIVATE)` keys `pos_x`, `pos_y`. Loaded in `init`, saved on `ACTION_UP`.
- Drag handler: `OnTouchListener` tracking `ACTION_DOWN` raw coords + initial params x/y; `ACTION_MOVE` updates `params.x/y` and calls `windowManager.updateViewLayout(view, params)`; `ACTION_UP` either persists position (if drag) or invokes `onTap` callback (if total movement < touchSlop and elapsed < 300ms).
- Status dot pulse: `ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.4f, 1f).apply { duration = 1500; repeatCount = INFINITE }.start()` — started once for amber/red.
- Collects `manager.uiState` on `Dispatchers.Main.immediate` and `render(state)`s:
  - `adCount.text` = if `maskActive` "Ads detected: ${adsDetected} / Ads skipped: ${adsSkipped}" else "${adsDetected} ads".
  - `revenue.text` = `String.format("$%.2f", revenue)`.
  - `sessionTime.text` = `formatMmSs(now - sessionStartMs)`.
  - `statusDot` color from `manager.statusColor(state)`.
  - `applyBudgetState(state.bracket)` — AT_80 appends "5 min left" badge; AT_100 flashes (alpha animation) + appends "Budget reached"; AT_120 sets background tint to red `#B00020`; CHILD_HARD_STOP shows "Time's up" and the broadcast (already sent by manager) lets WI-09/10 mask skip remaining content.

## SessionSummaryCard.kt (com.scrollshield.feature.counter)

- `class SessionSummaryCard(context: Context, manager: AdCounterManager, sessionDao: SessionDao)` exposing `fun show(state: UiState, host: OverlayHost)`, `fun hide(host: OverlayHost)`.
- LayoutParams: `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL`, `gravity = Gravity.BOTTOM`, `width = MATCH_PARENT`, `height = (resources.displayMetrics.heightPixels * 0.4f).toInt()`.
- View: `LinearLayout(VERTICAL)`, GradientDrawable rounded `#EE000000`. Header w/ close button (×) → `host.removeView(...)`. Rows:
  - Ads detected, ads skipped
  - Ad-to-content ratio = `adsDetected.toFloat() / max(1, itemsSeen)` formatted as "1:N"
  - Revenue
  - Duration `formatMmSs`
  - **Detection method breakdown** rows: "Visual (Tier 1): X", "Text fast-path (Tier 0): Y", "Deep text (Tier 2): Z" populated from `state.tierCounts`
  - Brand chips (horizontal LinearLayout of rounded TextViews)
  - Category chips (same)
  - Classification counts recap (small muted text)
- Buttons: Close, Export → `manager.exportToDownloads(manager.exportJson(state), "scrollshield_session_${state.sessionId}.json")` + toast.
- History toggle lists `sessionDao.recent(20)` (or whatever the closest existing query is — fall back to nothing if no such method); each row has its own Export button calling `manager.exportJsonForSession(record)`. Historical rows display "Tier breakdown: not available for historical sessions".
- Nested private `SatisfactionSurveyView`: 5-star `RatingBar` (`stepSize = 1f`), prompt "How was this session?", auto-dismiss via `Handler(Looper.getMainLooper()).postDelayed(::dismiss, 10_000L)`. On rating change, calls `onRated(rating.toInt())` and dismisses. If never rated, callback NOT invoked.

## TimeBudgetNudge.kt (com.scrollshield.feature.counter)

- `enum class BudgetState { UNDER, AT_80, AT_100, AT_120, CHILD_HARD_STOP }`.
- `object TimeBudgetNudge`:
  - `fun evaluate(elapsedMinutes: Float, budgetMinutes: Float, isChild: Boolean): BudgetState`. Computes `ratio = elapsedMinutes / budgetMinutes`. `>= 1.2 -> AT_120`; `>= 1.0 -> if (isChild) CHILD_HARD_STOP else AT_100`; `>= 0.8 -> AT_80`; else `UNDER`. `if (budgetMinutes <= 0) return UNDER`.
  - `fun applyToOverlay(overlay: AdCounterOverlay, state: BudgetState)` — delegates to `overlay.applyBudgetState(state)`. No blocking.
  - `fun shouldHardStopMask(state: BudgetState): Boolean = state == BudgetState.CHILD_HARD_STOP` — WI-09/10 contract.

## OverlayService.kt (com.scrollshield.service)

- `@AndroidEntryPoint class OverlayService : Service()` injecting `AdCounterManager`, `ProfileManager`, `SessionDao`, `WindowManager`.
- `interface OverlayHost { fun showPill(); fun hidePill(); fun showSummary(state: UiState); fun hideSummary(); fun showSurvey(onResult: (Int?) -> Unit); fun showLoading(); fun hideLoading(); fun showSkipFlash(); fun addView(view: View, params: WindowManager.LayoutParams, key: String); fun updateView(key: String, params: WindowManager.LayoutParams); fun removeView(key: String); fun hasView(key: String): Boolean; fun ensurePermission(): Boolean }` — reusable by WI-09/10.
- `OverlayService` implements `OverlayHost`. Tracks `views = mutableMapOf<String, View>()`.
- `companion object`: `ACTION_CHILD_HARD_STOP = "com.scrollshield.action.CHILD_HARD_STOP"`, `EXTRA_PLATFORM = "platform"`, `KEY_PILL = "counter_pill"`, `KEY_SUMMARY = "summary_card"`, `KEY_SURVEY = "survey"`, `KEY_LOADING = "loading"`, `KEY_SKIP_FLASH = "skip_flash"`, `KEY_MASK_DIM = "mask_dim"`, `KEY_MASK_HINT = "mask_hint"`, `ACTION_START = "com.scrollshield.OVERLAY_START"`. KDoc declares ACTION_CHILD_HARD_STOP as the WI-09/10 mask-skip contract.
- `ensurePermission()`: `if (Build.VERSION.SDK_INT >= M && !Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(FLAG_ACTIVITY_NEW_TASK)); return false } else true`.
- `addView`: enforces `params.flags = params.flags or FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL`; if `key` already in map, `windowManager.updateViewLayout`; else `windowManager.addView` and store. Try/catch `WindowManager.BadTokenException`.
- `updateView` / `removeView` / `hasView` operate on the map. `onDestroy` removes all views and cancels scope.
- Holds a single instance of `AdCounterOverlay` and `SessionSummaryCard`.
- Receives `com.scrollshield.APP_FOREGROUND` / `APP_BACKGROUND` broadcasts (with `pkg` extra) via a registered `BroadcastReceiver`. Forwards to `manager.onAppForeground(pkg)` / `manager.onAppBackground()`.
- Producer wiring: launches a coroutine collecting `AdCounterManager.classifiedItems` SharedFlow (no-op until something publishes — but the seam is documented). Companion `fun publish(item: ClassifiedItem)` static seam forwards to `AdCounterManager.classifiedItems.tryEmit(item)`. ClassificationPipeline.kt is read-only and has no callback hook; this is the documented integration seam.
- Coroutine in `onCreate` collects `manager.uiState` to drive overlay visuals (pill text, status color, budget bracket transitions).
- `onStartCommand`: `ensurePermission()`; if false, `stopSelf()`. Otherwise foreground itself with a low-importance notification on channel `"scrollshield_overlay"`. Returns `START_STICKY`.
- `onBind`: returns a `LocalBinder` exposing the service as `OverlayHost`.
- `onDestroy`: removes all views, cancels the service scope.

## Notes for the Implementer

- The application Hilt graph wiring (DI module entries, Application.onCreate startService/bindService) is OUT OF SCOPE — only the listed target files may be modified. Implementer should NOT touch DI modules or Application.kt. The classes should be self-contained enough that future DI wiring is straightforward.
- If a referenced ProfileManager method (e.g. `getAdvancedSettings()`, `recent(20)` on SessionDao) does not exist, fall back gracefully (default values, omit history list) rather than modifying read-only files.
- Use only existing imports/libraries. Avoid adding new Gradle dependencies.
- The build target is `./gradlew :app:assembleDebug` from `/home/devuser/dev-worktree-1`.
