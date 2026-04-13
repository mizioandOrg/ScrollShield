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
import com.scrollshield.data.model.ClassifiedItem
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
 * scroll interception, and child hard-stop.
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

    fun initialize() {
        preScanController = PreScanController(
            context, feedInterceptionService, screenCaptureManager, classificationPipeline
        )

        val filter = IntentFilter(ACTION_CHILD_HARD_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(childHardStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(childHardStopReceiver, filter)
        }
    }

    fun destroy() {
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
        scope.launch {
            val map = scanMap ?: return@launch
            map.advanceUserHead(position)

            // Child hard stop check
            if (childHardStop) {
                overlayHost.showSkipFlash()
                return@launch
            }

            // Skip check
            if (map.shouldSkip(position)) {
                overlayHost.showSkipFlash()
                AdCounterManager.classifiedItems.tryEmit(
                    map.getItem(position) ?: return@launch
                )
                return@launch
            }

            // Buffer exhaustion check
            val remaining = map.bufferRemaining()
            if (remaining <= 0) {
                onBufferExhausted()
            }
        }
    }

    private suspend fun onBufferExhausted() {
        val ctrl = preScanController ?: return
        if (ctrl.shouldDisableLookahead()) return

        val map = scanMap ?: return
        val profile = getActiveProfile() ?: return

        loadingOverlay?.updateStatusText("ScrollShield is scanning ahead...")
        loadingOverlay?.show(overlayHost)

        map.setExtending(true)
        val result = ctrl.runPreScan(map, profile) { current, total ->
            loadingOverlay?.updateProgress(current, total)
        }
        publishClassifiedItems(map)
        map.setExtending(false)

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
