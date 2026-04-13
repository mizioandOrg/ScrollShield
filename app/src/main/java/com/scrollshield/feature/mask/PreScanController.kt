package com.scrollshield.feature.mask

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.scrollshield.classification.ClassificationPipeline
import com.scrollshield.classification.ScreenCaptureManager
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.UserProfile
import com.scrollshield.service.FeedInterceptionService
import kotlinx.coroutines.delay
import java.security.MessageDigest

/**
 * Pre-scan controller: scrolls ahead, captures, classifies, and rewinds
 * so the ScanMapRuntime is populated before the user sees any content.
 *
 * ---
 * Metrics split rationale:
 * Pre-scan metrics (itemsPreScanned, preScanDurationMs) are tracked here
 * and bubbled up via PreScanResult so the caller (ScrollMaskManager) can
 * publish them to its own StateFlow. This keeps PreScanController free of
 * UI / StateFlow concerns while letting the mask-lifecycle owner decide
 * when and how to expose metrics to the overlay or AdCounterManager.
 * ---
 */
class PreScanController(
    private val context: Context,
    private val feedInterceptionService: FeedInterceptionService,
    private val screenCaptureManager: ScreenCaptureManager,
    private val classificationPipeline: ClassificationPipeline
) {

    companion object {
        const val PRE_SCAN_BUFFER_SIZE = 10
        const val LOW_MEMORY_BUFFER_SIZE = 5
        const val FRAME_CAPTURE_SETTLE_MS = 100L
        const val FRAME_CAPTURE_BUDGET_MS = 15L
        const val LOW_MEMORY_THRESHOLD_MB = 4096L
    }

    data class PreScanResult(
        val itemsScanned: Int,
        val durationMs: Long,
        val feedMutated: Boolean,
        val earlyStop: Boolean
    )

    /**
     * Run the pre-scan loop: scroll forward, capture, classify, detect duplicates,
     * then rewind to the original position.
     */
    suspend fun runPreScan(
        scanMap: ScanMapRuntime,
        profile: UserProfile,
        onProgress: (Int, Int) -> Unit
    ): PreScanResult {
        val startTime = System.currentTimeMillis()
        val bufferSize = effectiveBufferSize()

        // Store start fingerprint for feed-mutation detection.
        // FeedInterceptionService.lastValidatedHash is private, so we use the
        // ScanMapRuntime's own validated hash as the feed-mutation anchor.
        val startFingerprint = scanMap.lastValidatedHash

        var itemsScanned = 0
        var earlyStop = false

        for (i in 0 until bufferSize) {
            // Scroll forward one position
            feedInterceptionService.scrollForwardFast(1).join()

            // Let the UI settle before capturing
            delay(FRAME_CAPTURE_SETTLE_MS)

            // Capture frame
            val frame: Bitmap? = screenCaptureManager.captureFrame()

            // Build a FeedItem from the captured data
            val feedItem = buildFeedItem(
                app = scanMap.app,
                position = i,
                capture = frame
            )

            // Classify
            val classifiedItem = classificationPipeline.classify(feedItem, profile)

            // Duplicate detection → early stop
            if (scanMap.isDuplicate(classifiedItem)) {
                earlyStop = true
                itemsScanned = i
                break
            }

            // Add to scan map
            scanMap.addItem(i, classifiedItem)
            itemsScanned = i + 1
            onProgress(itemsScanned, bufferSize)
        }

        // Rewind to original position
        if (itemsScanned > 0) {
            feedInterceptionService.scrollBackwardFast(itemsScanned).join()
        }

        // Compare fingerprints for feed mutation detection
        val endFingerprint = scanMap.lastValidatedHash
        val feedMutated = startFingerprint != null &&
            endFingerprint != null &&
            startFingerprint != endFingerprint

        val durationMs = System.currentTimeMillis() - startTime
        return PreScanResult(
            itemsScanned = itemsScanned,
            durationMs = durationMs,
            feedMutated = feedMutated,
            earlyStop = earlyStop
        )
    }

    fun effectiveBufferSize(): Int =
        if (isLowMemoryDevice()) LOW_MEMORY_BUFFER_SIZE else PRE_SCAN_BUFFER_SIZE

    fun isLowMemoryDevice(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        return totalMb < LOW_MEMORY_THRESHOLD_MB
    }

    fun shouldDisableLookahead(): Boolean = isLowMemoryDevice()

    private fun buildFeedItem(
        app: String,
        position: Int,
        capture: Bitmap?
    ): FeedItem {
        val timestamp = System.currentTimeMillis()
        val id = sha256("prescan_${app}_${position}_$timestamp")
        return FeedItem(
            id = id,
            timestamp = timestamp,
            app = app,
            creatorName = "",
            captionText = "",
            hashtags = emptyList(),
            labelText = null,
            screenRegion = Rect(),
            rawNodeDump = "",
            feedPosition = position,
            accessibilityNodeId = null,
            detectedDurationMs = null,
            screenCapture = capture
        )
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
