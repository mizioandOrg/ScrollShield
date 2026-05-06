package com.scrollshield.feature.mask

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.service.OverlayHost
import com.scrollshield.service.OverlayService
import kotlinx.coroutines.delay

/**
 * Tracks consecutive skips and triggers a high-density blocking overlay
 * when too many skips occur in a row.
 *
 * Not Hilt-injected — instantiated manually by ScrollMaskManager.
 */
class ConsecutiveSkipHandler(
    private val skipFlashOverlay: SkipFlashOverlay,
    private val overlayHost: OverlayHost
) {

    companion object {
        const val MAX_CONSECUTIVE_SKIPS = 5
        const val CONSECUTIVE_DELAY_MS = 300L
        private const val KEY_HIGH_DENSITY = OverlayService.KEY_MASK_DIM
    }

    @Volatile
    var isHighDensityBlocking: Boolean = false
        private set

    private var consecutiveCount = 0
    private val accumulatedCategories = mutableSetOf<String>()

    /**
     * Called when a skip is about to happen.
     *
     * @return true if the skip should proceed, false if high-density blocking kicked in
     */
    suspend fun onSkip(item: ClassifiedItem): Boolean {
        consecutiveCount++
        accumulatedCategories.add(item.topicCategory.label)

        // 6th consecutive skip triggers high-density overlay
        if (consecutiveCount > MAX_CONSECUTIVE_SKIPS) {
            showHighDensityOverlay()
            return false
        }

        // Delay between consecutive skips (after the first)
        if (consecutiveCount > 1) {
            delay(CONSECUTIVE_DELAY_MS)
        }

        return true
    }

    /**
     * Shows a batch flash with all distinct categories accumulated during
     * the consecutive skip run, then resets.
     */
    fun showBatchFlash() {
        if (accumulatedCategories.isNotEmpty()) {
            skipFlashOverlay.show(overlayHost, accumulatedCategories.joinToString(", "))
        }
    }

    /**
     * Called when the user encounters a non-skip item, breaking the streak.
     * Shows accumulated flash if any, then resets.
     */
    fun onNonSkip() {
        if (consecutiveCount > 0) {
            showBatchFlash()
        }
        consecutiveCount = 0
        accumulatedCategories.clear()
    }

    private fun showHighDensityOverlay() {
        isHighDensityBlocking = true

        val context = skipFlashOverlay.let {
            // We need a Context — get it from overlayHost indirectly
            // The overlayHost is an OverlayService which is a Context
            return@let null
        }

        // Build programmatic full-screen overlay
        val rootLayout = LinearLayout(overlayHost as android.content.Context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE0_000000.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onHighDensityDismissed() }
        }

        val titleText = TextView(overlayHost as android.content.Context).apply {
            text = "High ad density"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(titleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(16) })

        val subtitleText = TextView(overlayHost as android.content.Context).apply {
            text = "Tap anywhere to continue"
            setTextColor(Color.parseColor("#AAAAAA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(subtitleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        overlayHost.addView(rootLayout, params, KEY_HIGH_DENSITY)
    }

    fun onHighDensityDismissed() {
        isHighDensityBlocking = false
        consecutiveCount = 0
        accumulatedCategories.clear()
        overlayHost.removeView(KEY_HIGH_DENSITY)
    }

    fun reset() {
        isHighDensityBlocking = false
        consecutiveCount = 0
        accumulatedCategories.clear()
        overlayHost.removeView(KEY_HIGH_DENSITY)
    }

    private fun dpToPx(dp: Int): Int {
        val density = (overlayHost as android.content.Context).resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
