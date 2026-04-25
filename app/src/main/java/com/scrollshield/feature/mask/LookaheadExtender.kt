package com.scrollshield.feature.mask

import android.graphics.Bitmap
import android.graphics.Rect
import com.scrollshield.classification.ClassificationPipeline
import com.scrollshield.classification.ScreenCaptureManager
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.UserProfile
import com.scrollshield.service.FeedInterceptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Extends the pre-scan buffer at runtime when the user is approaching
 * the end of the scanned region. Scrolls forward, captures, classifies,
 * then rewinds — same pattern as PreScanController but triggered dynamically.
 *
 * Not Hilt-injected — instantiated manually by ScrollMaskManager.
 */
class LookaheadExtender(
    private val feedInterceptionService: FeedInterceptionService,
    private val screenCaptureManager: ScreenCaptureManager,
    private val classificationPipeline: ClassificationPipeline,
    private val scope: CoroutineScope
) {

    companion object {
        const val TRIGGER_THRESHOLD = 3
        const val EXTENSION_SIZE = 5
        const val FRAME_SETTLE_MS = 100L
    }

    @Volatile
    var isExtending: Boolean = false
        private set

    private var extensionJob: Job? = null

    /**
     * Returns true when the lookahead should trigger based on remaining buffer.
     */
    fun shouldTrigger(bufferRemaining: Int): Boolean {
        return bufferRemaining <= TRIGGER_THRESHOLD && !isExtending
    }

    /**
     * Extend the scan map by scrolling forward, capturing, classifying,
     * then rewinding. Detects duplicates for early stop.
     *
     * @param scanMap the current scan map to extend
     * @param profile the active user profile for classification
     * @param onCatchUp callback invoked if the user catches up during extension
     */
    fun extend(
        scanMap: ScanMapRuntime,
        profile: UserProfile,
        onCatchUp: suspend () -> Unit
    ) {
        extensionJob?.cancel()
        isExtending = true

        extensionJob = scope.launch {
            var itemsScanned = 0
            try {
                scanMap.setExtending(true)

                repeat(EXTENSION_SIZE) { i ->
                    // Scroll forward one position
                    feedInterceptionService.scrollForwardFast(1).join()

                    // Let UI settle
                    delay(FRAME_SETTLE_MS)

                    // Capture frame
                    val frame: Bitmap? = screenCaptureManager.captureFrame()

                    // Build feed item
                    val feedItem = buildFeedItem(
                        app = scanMap.app,
                        position = scanMap.scanHead,
                        capture = frame
                    )

                    // Classify
                    val classifiedItem = classificationPipeline.classify(feedItem, profile)

                    // Duplicate detection -> early stop
                    if (scanMap.isDuplicate(classifiedItem)) {
                        return@repeat
                    }

                    // Add to scan map
                    scanMap.addItem(scanMap.scanHead, classifiedItem)
                    itemsScanned++
                }
            } finally {
                // Rewind by actual items scanned
                if (itemsScanned > 0) {
                    feedInterceptionService.scrollBackwardFast(itemsScanned).join()
                }
                scanMap.setExtending(false)
                isExtending = false
            }
        }
    }

    /**
     * Check if the user has caught up to the scan head during extension.
     * If so, cancel the extension and invoke onCatchUp.
     */
    suspend fun checkCatchUp(
        scanMap: ScanMapRuntime,
        position: Int,
        onCatchUp: suspend () -> Unit
    ) {
        if (isExtending && !scanMap.isScanned(position)) {
            cancel()
            onCatchUp()
        }
    }

    fun cancel() {
        extensionJob?.cancel()
        extensionJob = null
        isExtending = false
    }

    private fun buildFeedItem(
        app: String,
        position: Int,
        capture: Bitmap?
    ): FeedItem {
        val timestamp = System.currentTimeMillis()
        val id = sha256("lookahead_${app}_${position}_$timestamp")
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
