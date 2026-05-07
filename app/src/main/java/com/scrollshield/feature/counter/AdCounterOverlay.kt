package com.scrollshield.feature.counter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.scrollshield.error.ErrorRecoveryManager

/**
 * Floating pill overlay. Pure view + render — no flow collection here.
 * The OverlayService subscribes to manager.uiState and calls render() on the
 * Main dispatcher.
 */
class AdCounterOverlay(
    private val context: Context,
    private val manager: AdCounterManager,
    private val errorRecoveryManager: ErrorRecoveryManager? = null,
    private val onTap: (() -> Unit)? = null
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(AdCounterManager.PREFS_OVERLAY, Context.MODE_PRIVATE)

    private val statusDot: View
    private val warningIndicator: TextView
    private val adCount: TextView
    private val sep1: TextView
    private val revenue: TextView
    private val sep2: TextView
    private val sessionTime: TextView
    private val container: LinearLayout
    private val background: GradientDrawable

    private var pulseAnimator: ObjectAnimator? = null
    private var flashAnimator: ValueAnimator? = null
    private var lastBracket: BudgetState? = null
    private var lastStatusColor: StatusColor? = null

    val view: View
        get() = container

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f)
            setColor(Color.parseColor("#CC101418"))
        }

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = this@AdCounterOverlay.background
            val padH = dp(12f).toInt()
            setPadding(padH, 0, padH, 0)
            minimumHeight = dp(32f).toInt()
        }

        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#4CAF50"))
        }
        statusDot = View(context).apply {
            background = dotDrawable
            layoutParams = LinearLayout.LayoutParams(dp(8f).toInt(), dp(8f).toInt()).apply {
                rightMargin = dp(6f).toInt()
            }
        }

        warningIndicator = TextView(context).apply {
            text = "\u26A0"
            setTextColor(Color.parseColor("#FFC107"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(4f).toInt()
            }
            visibility = View.GONE
        }

        adCount = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            text = "0 ads"
        }

        sep1 = makeSeparator()
        revenue = TextView(context).apply {
            setTextColor(Color.parseColor("#CCCCCC"))
            setTypeface(Typeface.MONOSPACE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            text = "$0.00"
        }
        sep2 = makeSeparator()
        sessionTime = TextView(context).apply {
            setTextColor(Color.parseColor("#CCCCCC"))
            setTypeface(Typeface.MONOSPACE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            text = "00:00"
        }

        container.addView(statusDot)
        container.addView(warningIndicator)
        container.addView(adCount)
        container.addView(sep1)
        container.addView(revenue)
        container.addView(sep2)
        container.addView(sessionTime)

        attachTouchListener()
    }

    fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(32f).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = prefs.getInt("pos_x", 0)
        lp.y = prefs.getInt("pos_y", dp(8f).toInt())
        currentParams = lp
        return lp
    }

    private var currentParams: WindowManager.LayoutParams? = null

    fun render(state: AdCounterManager.UiState) {
        adCount.text = if (state.maskActive)
            "Ads detected: ${state.adsDetected} / Ads skipped: ${state.adsSkipped}"
        else
            "${state.adsDetected} ads"
        revenue.text = String.format("$%.2f", state.revenue)
        val elapsedMs = if (state.sessionStartMs > 0)
            System.currentTimeMillis() - state.sessionStartMs else 0L
        sessionTime.text = formatMmSs(elapsedMs)

        val color = manager.statusColor(state)
        if (color != lastStatusColor) {
            lastStatusColor = color
            val tint = when (color) {
                StatusColor.GREEN -> Color.parseColor("#4CAF50")
                StatusColor.AMBER -> Color.parseColor("#FFC107")
                StatusColor.RED -> Color.parseColor("#F44336")
            }
            (statusDot.background as? GradientDrawable)?.setColor(tint)
            if (color == StatusColor.AMBER || color == StatusColor.RED) {
                startPulse()
            } else {
                stopPulse()
            }
        }

        applyBudgetState(state.bracket)

        // Show/hide degradation warning indicator
        val degraded = errorRecoveryManager?.isDegraded() == true
        warningIndicator.visibility = if (degraded) View.VISIBLE else View.GONE
    }

    fun applyBudgetState(state: BudgetState) {
        if (state == lastBracket) return
        lastBracket = state
        when (state) {
            BudgetState.UNDER -> {
                background.setColor(Color.parseColor("#CC101418"))
                stopFlash()
            }
            BudgetState.AT_80 -> {
                background.setColor(Color.parseColor("#CC101418"))
                stopFlash()
                sessionTime.text = sessionTime.text.toString() + "  5 min left"
            }
            BudgetState.AT_100 -> {
                background.setColor(Color.parseColor("#CC332200"))
                startFlash()
                sessionTime.text = "Budget reached"
            }
            BudgetState.AT_120 -> {
                background.setColor(Color.parseColor("#CCB00020"))
                stopFlash()
            }
            BudgetState.CHILD_HARD_STOP -> {
                background.setColor(Color.parseColor("#CCB00020"))
                stopFlash()
                sessionTime.text = "Time's up"
            }
        }
    }

    private fun startPulse() {
        if (pulseAnimator?.isStarted == true) return
        pulseAnimator = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        statusDot.alpha = 1f
    }

    private fun startFlash() {
        if (flashAnimator?.isStarted == true) return
        flashAnimator = ObjectAnimator.ofFloat(container, "alpha", 1f, 0.3f, 1f).apply {
            duration = 800
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopFlash() {
        flashAnimator?.cancel()
        flashAnimator = null
        container.alpha = 1f
    }

    private fun makeSeparator(): TextView = TextView(context).apply {
        setTextColor(Color.parseColor("#666666"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        text = " · "
    }

    private fun attachTouchListener() {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTime = 0L
        var dragged = false

        container.setOnTouchListener { _, event ->
            val params = currentParams ?: return@setOnTouchListener false
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!dragged && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        dragged = true
                    }
                    if (dragged) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { wm.updateViewLayout(container, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - downTime
                    if (dragged) {
                        prefs.edit().putInt("pos_x", params.x).putInt("pos_y", params.y).apply()
                    } else if (elapsed < 300) {
                        onTap?.invoke()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)

    private fun formatMmSs(ms: Long): String {
        val totalSec = ms / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        return String.format("%02d:%02d", mm, ss)
    }
}
