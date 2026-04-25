package com.scrollshield.feature.mask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Looper
import android.util.Log
import com.scrollshield.classification.ClassificationPipeline
import com.scrollshield.classification.ScreenCaptureManager
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.SkipDecision
import com.scrollshield.data.model.UserProfile
import com.scrollshield.feature.counter.AdCounterManager
import com.scrollshield.profile.ProfileManager
import com.scrollshield.service.FeedInterceptionService
import com.scrollshield.service.OverlayHost
import com.scrollshield.service.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Orchestrates the full-screen mask lifecycle: pre-scan, overlay,
 * scroll interception, skip flash, consecutive-skip handling,
 * lookahead extension, interest learning, and child hard-stop.
 *
 * Not Hilt-injected — instantiated manually by OverlayService, consistent
 * with existing no-DI pattern in the service layer.
 */
class ScrollMaskManager(
    private val context: Context,
    private val feedInterceptionService: FeedInterceptionService,
    private val screenCaptureManager: ScreenCaptureManager,
    private val classificationPipeline: ClassificationPipeline,
    private val profileManager: ProfileManager,
    private val overlayHost: OverlayHost
) {

    companion object {
        private const val TAG = "ScrollMaskManager"
        private const val ACTION_CHILD_HARD_STOP = OverlayService.ACTION_CHILD_HARD_STOP
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var scanMap: ScanMapRuntime? = null
    private var loadingOverlay: LoadingOverlay? = null
    private var preScanController: PreScanController? = null
    private var isPreScanning: Boolean = false
    private var childHardStop: Boolean = false
    private var hardStopped: Boolean = false
    private var preScanJob: Job? = null

    // ---- WI-10 extension fields ----
    private var skipFlashOverlay: SkipFlashOverlay? = null
    private var consecutiveSkipHandler: ConsecutiveSkipHandler? = null
    private var lookaheadExtender: LookaheadExtender? = null
    private var interestLearner: InterestLearner? = null

    // ---- Pre-scan stats ----

    enum class PreScanStatus { IDLE, RUNNING, COMPLETE, CANCELLED }

    data class PreScanStats(
        val itemsPreScanned: Int = 0,
        val preScanDurationMs: Long = 0L,
        val status: PreScanStatus = PreScanStatus.IDLE
    )

    /**
     * Mask-lifecycle metrics ownership: preScanStats is owned by ScrollMaskManager
     * because the mask controls when pre-scan starts, completes, or is cancelled.
     * Consumers (e.g. AdCounterManager UiState) read from this flow to reflect
     * pre-scan progress in the counter overlay.
     */
    private val _preScanStats = MutableStateFlow(PreScanStats())
    val preScanStats: StateFlow<PreScanStats> = _preScanStats.asStateFlow()

    // ---- Broadcast receiver for child hard stop ----

    private val childHardStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CHILD_HARD_STOP) {
                onHardStop()
            }
        }
    }

    // ---- Lifecycle ----

    /**
     * Wiring contract: OverlayService.onCreate() creates sessionDao from Room;
     * the caller that constructs ScrollMaskManager must call initialize(sessionDao)
     * to complete dependency wiring for InterestLearner and other components
     * that require the SessionDao.
     */
    fun initialize(sessionDao: SessionDao) {
        preScanController = PreScanController(
            context, feedInterceptionService, screenCaptureManager, classificationPipeline
        )

        skipFlashOverlay = SkipFlashOverlay(context)
        consecutiveSkipHandler = ConsecutiveSkipHandler(skipFlashOverlay!!, overlayHost)
        lookaheadExtender = LookaheadExtender(
            feedInterceptionService, screenCaptureManager, classificationPipeline, scope
        )
        interestLearner = InterestLearner(profileManager, sessionDao)

        val filter = IntentFilter(ACTION_CHILD_HARD_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(childHardStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(childHardStopReceiver, filter)
        }
    }

    fun destroy() {
        lookaheadExtender?.cancel()
        try { context.unregisterReceiver(childHardStopReceiver) } catch (_: Exception) {}
        scope.cancel()
    }

    // ---- Session start ----

    fun onSessionStart(app: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "onSessionStart must be called on the main thread"
        }

        val profile = getActiveProfileSync() ?: return
        if (!profile.maskEnabled) return

        // Create runtime scan map for this session
        scanMap = ScanMapRuntime(
            sessionId = UUID.randomUUID().toString(),
            app = app
        )

        // Create and show the loading overlay SYNCHRONOUSLY before launching coroutine
        loadingOverlay = LoadingOverlay(context, profile)
        val showMs = loadingOverlay!!.show(overlayHost)
        if (showMs > LoadingOverlay.LATENCY_CONTRACT_MS) {
            Log.w(TAG, "LoadingOverlay.show() took ${showMs}ms, exceeds ${LoadingOverlay.LATENCY_CONTRACT_MS}ms contract")
        }

        _preScanStats.value = PreScanStats(status = PreScanStatus.RUNNING)
        isPreScanning = true

        preScanJob = scope.launch {
            val map = scanMap ?: return@launch
            val ctrl = preScanController ?: return@launch

            val result = ctrl.runPreScan(map, profile) { current, total ->
                loadingOverlay?.updateProgress(current, total)
            }

            // Publish classified items to AdCounterManager
            publishClassifiedItems(map)

            // Handle feed mutation: clear and re-scan
            if (result.feedMutated) {
                map.clear()
                loadingOverlay?.updateStatusText("Feed changed — re-scanning...")
                loadingOverlay?.updateProgress(0, ctrl.effectiveBufferSize())

                val retryResult = ctrl.runPreScan(map, profile) { current, total ->
                    loadingOverlay?.updateProgress(current, total)
                }
                publishClassifiedItems(map)

                _preScanStats.value = PreScanStats(
                    itemsPreScanned = retryResult.itemsScanned,
                    preScanDurationMs = result.durationMs + retryResult.durationMs,
                    status = PreScanStatus.COMPLETE
                )
            } else {
                _preScanStats.value = PreScanStats(
                    itemsPreScanned = result.itemsScanned,
                    preScanDurationMs = result.durationMs,
                    status = PreScanStatus.COMPLETE
                )
            }

            // Hide overlay — pre-scan complete
            loadingOverlay?.hide(overlayHost)
            isPreScanning = false
        }
    }

    // ---- User scroll handling ----

    fun onUserScroll(position: Int) {
        // Ignore our own programmatic gestures (Criterion 7)
        if (feedInterceptionService.isOwnGesture) return

        scope.launch {
            val map = scanMap ?: return@launch
            map.advanceUserHead(position)

            // Child hard stop check
            if (childHardStop) return@launch

            // High-density blocking check
            val handler = consecutiveSkipHandler ?: return@launch
            if (handler.isHighDensityBlocking) return@launch

            // Check if user caught up to lookahead (Criterion 6)
            val extender = lookaheadExtender
            extender?.checkCatchUp(map, position) { onCatchUp() }

            val profile = getActiveProfile() ?: return@launch

            // Skip check
            if (map.shouldSkip(position)) {
                val item = map.getItem(position) ?: return@launch
                val allowed = handler.onSkip(item)
                if (allowed) {
                    performSkip(map, position)
                    // Emit to AdCounterManager
                    AdCounterManager.classifiedItems.tryEmit(item)
                }
                // No early return — fall through to checkLookahead (Criterion 5)
            } else {
                // Non-skip: show batch flash, learn interest
                handler.onNonSkip()
                val item = map.getItem(position)
                if (item != null) {
                    interestLearner?.onItemViewed(profile, item)
                }
            }

            // Always check lookahead after both skip and non-skip paths (Criterion 5)
            checkLookahead(map, profile)
        }
    }

    /**
     * Perform a skip: scroll forward past the current item.
     * scrollForward() completes in ~150ms, within the 500ms contract (Criterion 1).
     */
    private suspend fun performSkip(map: ScanMapRuntime, position: Int) {
        feedInterceptionService.scrollForward().join()
        map.advanceUserHead(position + 1)
    }

    /**
     * Check if lookahead extension should trigger based on remaining buffer.
     * Idempotent — shouldTrigger guards via isExtending flag.
     */
    private suspend fun checkLookahead(map: ScanMapRuntime, profile: UserProfile) {
        val extender = lookaheadExtender ?: return
        val remaining = map.bufferRemaining()
        if (extender.shouldTrigger(remaining)) {
            extender.extend(map, profile) { onCatchUp() }
        }
    }

    /**
     * Called when the user catches up to the scan head during lookahead.
     * Shows the loading overlay, runs a full pre-scan, then hides it (Criterion 6).
     */
    private suspend fun onCatchUp() {
        val map = scanMap ?: return
        val ctrl = preScanController ?: return
        val profile = getActiveProfile() ?: return

        loadingOverlay?.updateStatusText("ScrollShield is scanning ahead...")
        loadingOverlay?.show(overlayHost)

        val result = ctrl.runPreScan(map, profile) { current, total ->
            loadingOverlay?.updateProgress(current, total)
        }
        publishClassifiedItems(map)

        loadingOverlay?.hide(overlayHost)
    }

    // ---- Dismiss ----

    fun requestDismiss() {
        if (hardStopped) return

        if (canDismissMask()) {
            onSessionEnd()
        } else {
            loadingOverlay?.showPinChallenge(
                onSuccess = {
                    loadingOverlay?.restoreOverlayContent()
                    onSessionEnd()
                },
                onCancel = {
                    loadingOverlay?.restoreOverlayContent()
                }
            )
        }
    }

    // ---- Hard stop ----

    fun onHardStop() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "onHardStop must be called on the main thread"
        }

        // Cancel any in-progress pre-scan
        preScanJob?.cancel()
        isPreScanning = false
        _preScanStats.value = _preScanStats.value.copy(status = PreScanStatus.CANCELLED)

        // Cancel lookahead
        lookaheadExtender?.cancel()

        childHardStop = true
        hardStopped = true

        // Show hard-stop screen — if overlay isn't shown yet, show it first
        if (loadingOverlay == null) {
            val profile = getActiveProfileSync()
            if (profile != null) {
                loadingOverlay = LoadingOverlay(context, profile)
                loadingOverlay!!.show(overlayHost)
            }
        }
        loadingOverlay?.showHardStopScreen()
    }

    // ---- Permission checks ----

    private fun canDismissMask(): Boolean {
        val profile = getActiveProfileSync() ?: return true
        return !(profile.isChildProfile && !profile.maskDismissable)
    }

    // ---- Session end ----

    fun onSessionEnd() {
        // Cancel lookahead
        lookaheadExtender?.cancel()

        scope.launch {
            scanMap?.clear()
        }
        loadingOverlay?.hide(overlayHost)
        loadingOverlay = null
        scanMap = null
        isPreScanning = false
        childHardStop = false
        hardStopped = false
        preScanJob?.cancel()
        preScanJob = null
        consecutiveSkipHandler?.reset()
        _preScanStats.value = PreScanStats()
    }

    // ---- Helpers ----

    private fun publishClassifiedItems(scanMap: ScanMapRuntime) {
        scope.launch {
            val items: List<ClassifiedItem> = scanMap.snapshot()
            for (item in items) {
                AdCounterManager.classifiedItems.tryEmit(item)
            }
        }
    }

    private suspend fun getActiveProfile(): UserProfile? {
        val profileId = try {
            profileManager.getActiveProfileId().first()
        } catch (_: Exception) { null } ?: return null
        return try {
            profileManager.getProfileById(profileId)
        } catch (_: Exception) { null }
    }

    /**
     * Synchronous profile fetch for main-thread callers that cannot suspend.
     * Uses runBlocking — only safe on main thread with short-lived Room queries.
     */
    private fun getActiveProfileSync(): UserProfile? {
        return try {
            kotlinx.coroutines.runBlocking {
                getActiveProfile()
            }
        } catch (_: Exception) { null }
    }
}
