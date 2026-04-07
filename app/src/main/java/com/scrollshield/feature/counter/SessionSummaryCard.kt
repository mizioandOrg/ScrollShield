package com.scrollshield.feature.counter

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.model.SessionRecord
import com.scrollshield.service.OverlayHost
import com.scrollshield.service.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Expanded summary card overlay. Shown when the user taps the pill or when
 * a session ends. Pure view layer — no Hilt — constructed by OverlayService.
 */
class SessionSummaryCard(
    private val context: Context,
    private val manager: AdCounterManager,
    private val sessionDao: SessionDao
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun show(state: AdCounterManager.UiState, host: OverlayHost) {
        val view = buildView(state, host)
        host.addView(view, layoutParams(), OverlayService.KEY_SUMMARY)

        // Best-effort load of recent history. SessionDao has no `recent(20)` —
        // fall back to getSessionsSince(0) which is the closest method available,
        // then take(20). If anything fails, the history list stays empty.
        scope.launch {
            val records: List<SessionRecord> = try {
                withContext(Dispatchers.IO) {
                    sessionDao.getSessionsSince(0L).take(20)
                }
            } catch (_: Exception) { emptyList() }
            if (records.isNotEmpty()) {
                appendHistory(view, records)
            }
        }
    }

    fun hide(host: OverlayHost) {
        host.removeView(OverlayService.KEY_SUMMARY)
    }

    fun showSurvey(host: OverlayHost, onResult: (Int?) -> Unit) {
        val view = buildSurveyView(host, onResult)
        host.addView(view, surveyLayoutParams(), OverlayService.KEY_SURVEY)
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val height = (context.resources.displayMetrics.heightPixels * 0.4f).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM
        return lp
    }

    private fun surveyLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        return lp
    }

    private fun buildView(state: AdCounterManager.UiState, host: OverlayHost): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12f)
            setColor(Color.parseColor("#EE000000"))
        }

        val scroll = ScrollView(context).apply {
            background = bg
            setPadding(dp(16f).toInt(), dp(16f).toInt(), dp(16f).toInt(), dp(16f).toInt())
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(root)

        // Header w/ close
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "Session Summary"
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(context).apply {
            text = "×"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(12f).toInt(), 0, dp(12f).toInt(), 0)
            setOnClickListener { host.removeView(OverlayService.KEY_SUMMARY) }
        })
        root.addView(header)
        root.addView(divider())

        root.addView(textRow("Ads detected", state.adsDetected.toString()))
        root.addView(textRow("Ads skipped", state.adsSkipped.toString()))
        val ratio = if (state.itemsSeen > 0)
            "1:${(state.itemsSeen.toFloat() / kotlin.math.max(1, state.adsDetected)).toInt()}"
        else "1:0"
        root.addView(textRow("Ad-to-content ratio", ratio))
        root.addView(textRow("Revenue", String.format("$%.2f", state.revenue)))
        val elapsedMs = if (state.sessionStartMs > 0)
            System.currentTimeMillis() - state.sessionStartMs else 0L
        root.addView(textRow("Duration", formatMmSs(elapsedMs)))

        // Detection method breakdown
        root.addView(divider())
        root.addView(sectionHeader("Detection method breakdown"))
        root.addView(textRow("Visual (Tier 1)", state.tierCounts.getOrNull(1)?.toString() ?: "0"))
        root.addView(textRow("Text fast-path (Tier 0)", state.tierCounts.getOrNull(0)?.toString() ?: "0"))
        root.addView(textRow("Deep text (Tier 2)", state.tierCounts.getOrNull(2)?.toString() ?: "0"))

        // Brand chips
        if (state.brands.isNotEmpty()) {
            root.addView(divider())
            root.addView(sectionHeader("Brands"))
            root.addView(chipRow(state.brands.toList()))
        }

        // Category chips
        if (state.categories.isNotEmpty()) {
            root.addView(divider())
            root.addView(sectionHeader("Categories"))
            root.addView(chipRow(state.categories.toList()))
        }

        // Classification counts recap
        if (state.classificationCounts.isNotEmpty()) {
            root.addView(divider())
            val summary = state.classificationCounts.entries.joinToString(", ") { "${it.key.name}=${it.value}" }
            root.addView(TextView(context).apply {
                text = summary
                setTextColor(Color.parseColor("#888888"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            })
        }

        // Buttons
        root.addView(divider())
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttonRow.addView(Button(context).apply {
            text = "Close"
            setOnClickListener { host.removeView(OverlayService.KEY_SUMMARY) }
        })
        buttonRow.addView(Button(context).apply {
            text = "Export"
            setOnClickListener {
                try {
                    val json = manager.exportJson(state)
                    manager.exportToDownloads(json, "scrollshield_session_${state.sessionId}.json")
                    Toast.makeText(context, "Exported to Downloads", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        })
        root.addView(buttonRow)

        // History container placeholder, populated asynchronously by show()
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "history_container"
        })

        return scroll
    }

    private fun appendHistory(rootView: View, records: List<SessionRecord>) {
        val scroll = rootView as? ScrollView ?: return
        val root = scroll.getChildAt(0) as? LinearLayout ?: return
        val container = (0 until root.childCount)
            .map { root.getChildAt(it) }
            .firstOrNull { it.tag == "history_container" } as? LinearLayout ?: return

        container.removeAllViews()
        container.addView(divider())
        container.addView(sectionHeader("Recent sessions"))
        for (record in records) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(context).apply {
                text = "${record.app}  ${record.adsDetected} ads  ${"%.1f".format(record.durationMinutes)}m"
                setTextColor(Color.parseColor("#CCCCCC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(context).apply {
                text = "Export"
                setOnClickListener {
                    try {
                        val json = manager.exportJsonForSession(record)
                        manager.exportToDownloads(json, "scrollshield_session_${record.id}.json")
                        Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            container.addView(row)
            container.addView(TextView(context).apply {
                text = "Tier breakdown: not available for historical sessions"
                setTextColor(Color.parseColor("#666666"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            })
        }
    }

    private fun buildSurveyView(host: OverlayHost, onResult: (Int?) -> Unit): View {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12f)
            setColor(Color.parseColor("#EE101418"))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(20f).toInt(), dp(16f).toInt(), dp(20f).toInt(), dp(16f).toInt())
            gravity = Gravity.CENTER
        }
        container.addView(TextView(context).apply {
            text = "How was this session?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
        })

        var rated = false
        val handler = Handler(Looper.getMainLooper())

        val ratingBar = RatingBar(context).apply {
            numStars = 5
            stepSize = 1f
            setIsIndicator(false)
        }
        container.addView(ratingBar)

        val dismiss = Runnable {
            host.removeView(OverlayService.KEY_SURVEY)
            if (!rated) {
                // No callback when never rated.
            }
        }

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            if (rating > 0f && !rated) {
                rated = true
                onResult(rating.toInt())
                handler.removeCallbacksAndMessages(null)
                host.removeView(OverlayService.KEY_SURVEY)
            }
        }

        handler.postDelayed(dismiss, 10_000L)
        return container
    }

    // ---- Helpers ----

    private fun textRow(label: String, value: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2f).toInt(), 0, dp(2f).toInt())
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#AAAAAA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(context).apply {
            text = value
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.MONOSPACE)
        })
        return row
    }

    private fun sectionHeader(title: String): View = TextView(context).apply {
        text = title
        setTextColor(Color.parseColor("#FFFFFF"))
        setTypeface(null, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(4f).toInt(), 0, dp(2f).toInt())
    }

    private fun chipRow(items: List<String>): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (item in items) {
            val chipBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10f)
                setColor(Color.parseColor("#33FFFFFF"))
            }
            row.addView(TextView(context).apply {
                text = item
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                background = chipBg
                setPadding(dp(8f).toInt(), dp(2f).toInt(), dp(8f).toInt(), dp(2f).toInt())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.rightMargin = dp(4f).toInt()
                layoutParams = lp
            })
        }
        return row
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Color.parseColor("#22FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1f).toInt()
        ).apply {
            topMargin = dp(6f).toInt()
            bottomMargin = dp(6f).toInt()
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
