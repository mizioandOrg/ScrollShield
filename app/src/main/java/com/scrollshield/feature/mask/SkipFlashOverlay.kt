package com.scrollshield.feature.mask

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.scrollshield.service.OverlayHost
import com.scrollshield.service.OverlayService

/**
 * Brief full-screen flash overlay shown when a feed item is auto-skipped.
 * Displays "ScrollShield" + category labels, then fades out and self-removes.
 *
 * Not Hilt-injected — instantiated manually by ScrollMaskManager.
 */
class SkipFlashOverlay(private val context: Context) {

    companion object {
        const val DISPLAY_MS = 200L
        const val FADE_OUT_MS = 150L
        private const val KEY = OverlayService.KEY_SKIP_FLASH
    }

    /**
     * Show the skip-flash overlay with the given category label(s).
     * Auto-removes after [DISPLAY_MS] + [FADE_OUT_MS].
     */
    fun show(host: OverlayHost, categories: String) {
        if (host.hasView(KEY)) return

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC_000000.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Shield text
        val shieldText = TextView(context).apply {
            text = "ScrollShield"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(shieldText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(8) })

        // Category text
        val categoryText = TextView(context).apply {
            text = categories
            setTextColor(Color.parseColor("#AAAAAA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(categoryText, LinearLayout.LayoutParams(
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

        host.addView(rootLayout, params, KEY)

        // After DISPLAY_MS, fade out then remove
        rootLayout.postDelayed({
            val animator = ObjectAnimator.ofFloat(rootLayout, "alpha", 1f, 0f).apply {
                duration = FADE_OUT_MS
                interpolator = AccelerateInterpolator()
            }
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    host.removeView(KEY)
                }
            })
            animator.start()
        }, DISPLAY_MS)
    }

    fun hide(host: OverlayHost) {
        host.removeView(KEY)
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
