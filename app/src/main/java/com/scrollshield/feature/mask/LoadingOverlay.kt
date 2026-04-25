package com.scrollshield.feature.mask

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.scrollshield.data.model.UserProfile
import com.scrollshield.service.OverlayHost
import com.scrollshield.service.OverlayService

/**
 * Full-screen loading overlay shown while pre-scan is running.
 *
 * Guarantees: the user never sees unscanned content — this overlay
 * covers the entire screen until the pre-scan completes.
 *
 * FLAG_NOT_TOUCH_MODAL safety note: we use MATCH_PARENT for both
 * dimensions so the overlay fills the screen. FLAG_NOT_TOUCH_MODAL
 * is still applied by OverlayHost.addView(), but because the overlay
 * is full-screen, touches cannot leak to the app behind it. When the
 * overlay is hidden, touches resume normally.
 */
class LoadingOverlay(
    private val context: android.content.Context,
    private val profile: UserProfile
) {

    companion object {
        /** Maximum acceptable latency for show() to complete on the main thread. */
        const val LATENCY_CONTRACT_MS = 500L
        private const val KEY_LOADING = OverlayService.KEY_LOADING
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var rootLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var shieldIcon: TextView

    // Saved references for PIN challenge swap
    private var savedContentViews: List<View>? = null

    /**
     * Build and show the overlay synchronously on the main thread.
     * Returns elapsed time in milliseconds (for latency contract enforcement).
     */
    fun show(host: OverlayHost): Long {
        val t0 = System.currentTimeMillis()

        rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE0_000000.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Shield icon
        shieldIcon = TextView(context).apply {
            text = "ScrollShield"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(shieldIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(24) })

        // Progress bar
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 10
            progress = 0
            isIndeterminate = false
        }
        rootLayout.addView(progressBar, LinearLayout.LayoutParams(
            dpToPx(250), LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(12) })

        // Progress text
        progressText = TextView(context).apply {
            text = "Scanning 0/10"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(progressText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(16) })

        // Status text (child vs. adult)
        val childName = if (profile.isChildProfile) profile.name else null
        statusText = TextView(context).apply {
            text = if (childName != null) {
                "Setting up a safe feed for $childName"
            } else {
                "ScrollShield is preparing your feed."
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(8) })

        // Muted subtitle
        subtitleText = TextView(context).apply {
            text = "Filtering ads and unwanted content"
            setTextColor(Color.parseColor("#AAAAAA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
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

        host.addView(rootLayout, params, KEY_LOADING)
        return System.currentTimeMillis() - t0
    }

    fun updateProgress(current: Int, total: Int) {
        if (!::progressBar.isInitialized) return
        mainHandler.post {
            progressBar.max = total
            progressBar.progress = current
            progressText.text = "Scanning $current/$total"
        }
    }

    fun updateStatusText(text: String) {
        if (!::statusText.isInitialized) return
        mainHandler.post {
            statusText.text = text
        }
    }

    fun hide(host: OverlayHost) {
        host.removeView(KEY_LOADING)
    }

    /**
     * Swap the overlay content to a PIN entry view.
     * Uses programmatic views (no layout XML dependency).
     * Verifies against [profile.parentPinHash].
     */
    fun showPinChallenge(onSuccess: () -> Unit, onCancel: () -> Unit) {
        if (!::rootLayout.isInitialized) return

        mainHandler.post {
            // Save current views for restore
            savedContentViews = (0 until rootLayout.childCount).map { rootLayout.getChildAt(it) }
            rootLayout.removeAllViews()

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(32), dpToPx(32), dpToPx(32), dpToPx(32))
            }

            val title = TextView(context).apply {
                text = "Enter parent PIN to continue"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            container.addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(24) })

            // 4-digit PIN entry row
            val pinRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val pinDigits = mutableListOf<EditText>()
            repeat(4) { idx ->
                val digit = EditText(context).apply {
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    setBackgroundColor(0x40_FFFFFF)
                    gravity = Gravity.CENTER
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    filters = arrayOf(InputFilter.LengthFilter(1))
                    setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                pinDigits.add(digit)
                pinRow.addView(digit, LinearLayout.LayoutParams(
                    dpToPx(48), LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = if (idx > 0) dpToPx(8) else 0 })
            }

            container.addView(pinRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) })

            val errorText = TextView(context).apply {
                text = ""
                setTextColor(Color.RED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                visibility = View.GONE
            }
            container.addView(errorText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16) })

            // Buttons row
            val buttonRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val cancelBtn = TextView(context).apply {
                text = "Cancel"
                setTextColor(Color.parseColor("#AAAAAA"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                setOnClickListener { onCancel() }
            }
            buttonRow.addView(cancelBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dpToPx(24) })

            val submitBtn = TextView(context).apply {
                text = "Submit"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, Typeface.BOLD)
                setBackgroundColor(0xFF_4CAF50.toInt())
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                setOnClickListener {
                    val enteredPin = pinDigits.joinToString("") { d -> d.text.toString() }
                    if (enteredPin.length < 4) {
                        errorText.text = "Please enter all 4 digits"
                        errorText.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    val enteredHash = sha256(enteredPin)
                    if (enteredHash == profile.parentPinHash) {
                        onSuccess()
                    } else {
                        errorText.text = "Incorrect PIN"
                        errorText.visibility = View.VISIBLE
                        pinDigits.forEach { d -> d.text.clear() }
                    }
                }
            }
            buttonRow.addView(submitBtn)

            container.addView(buttonRow)
            rootLayout.addView(container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ))

            // Request focus — need FLAG_NOT_FOCUSABLE cleared for PIN input.
            // The caller (ScrollMaskManager) should handle window param updates if needed.
            pinDigits.firstOrNull()?.requestFocus()
        }
    }

    /** Restore the normal mask content after PIN challenge. */
    fun restoreOverlayContent() {
        if (!::rootLayout.isInitialized) return
        mainHandler.post {
            rootLayout.removeAllViews()
            savedContentViews?.forEach { rootLayout.addView(it) }
            savedContentViews = null
        }
    }

    /** Replace content with hard-stop screen — no close button. */
    fun showHardStopScreen() {
        if (!::rootLayout.isInitialized) return
        mainHandler.post {
            rootLayout.removeAllViews()

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(32), dpToPx(32), dpToPx(32), dpToPx(32))
            }

            val icon = TextView(context).apply {
                text = "\u26D4" // no-entry symbol
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
                gravity = Gravity.CENTER
            }
            container.addView(icon, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(24) })

            val message = TextView(context).apply {
                text = "Time\u2019s up \u2014 ask a parent"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            container.addView(message)

            rootLayout.addView(container, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
