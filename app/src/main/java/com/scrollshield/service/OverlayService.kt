package com.scrollshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.room.Room
import com.scrollshield.data.db.ScrollShieldDatabase
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.preferences.UserPreferencesStore
import com.scrollshield.feature.counter.AdCounterManager
import com.scrollshield.feature.counter.AdCounterOverlay
import com.scrollshield.feature.counter.BudgetState
import com.scrollshield.feature.counter.SessionSummaryCard
import com.scrollshield.feature.mask.ScrollMaskManager
import com.scrollshield.profile.ProfileManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Single overlay lifecycle manager.
 *
 * Hosts the AdCounter pill, the SessionSummaryCard, and acts as the OverlayHost
 * for WI-09/10 mask overlays. Receives APP_FOREGROUND/APP_BACKGROUND broadcasts
 * (with `pkg` extra) from FeedInterceptionService and forwards to AdCounterManager.
 *
 * ACTION_CHILD_HARD_STOP is the documented WI-09/10 mask-skip contract: the
 * AdCounterManager broadcasts it on transition into BudgetState.CHILD_HARD_STOP.
 */
/**
 * Note: this Service intentionally does NOT use @AndroidEntryPoint or any
 * Hilt @Inject. The application's Hilt graph currently has no @Provides
 * binding for SessionDao/ProfileDao (DI module wiring is out of scope for
 * WI-08 per the approved plan), so OverlayService instantiates its own
 * dependencies via Room directly. Future DI consolidation can replace this
 * with field injection / EntryPoint lookup once the graph is complete.
 */
class OverlayService : Service(), OverlayHost {

    private lateinit var manager: AdCounterManager
    private lateinit var profileManager: ProfileManager
    private lateinit var sessionDao: SessionDao
    private lateinit var database: ScrollShieldDatabase

    companion object {
        const val ACTION_CHILD_HARD_STOP = "com.scrollshield.action.CHILD_HARD_STOP"
        const val EXTRA_PLATFORM = "platform"
        const val ACTION_START = "com.scrollshield.OVERLAY_START"

        const val KEY_PILL = "counter_pill"
        const val KEY_SUMMARY = "summary_card"
        const val KEY_SURVEY = "survey"
        const val KEY_LOADING = "loading"
        const val KEY_SKIP_FLASH = "skip_flash"
        const val KEY_MASK_DIM = "mask_dim"
        const val KEY_MASK_HINT = "mask_hint"

        private const val ACTION_APP_FOREGROUND = "com.scrollshield.APP_FOREGROUND"
        private const val ACTION_APP_BACKGROUND = "com.scrollshield.APP_BACKGROUND"
        private const val NOTIF_CHANNEL_ID = "scrollshield_overlay"
        private const val NOTIF_ID = 2001
    }

    private lateinit var windowManager: WindowManager
    private val views = mutableMapOf<String, View>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var uiCollectJob: Job? = null

    private var pillOverlay: AdCounterOverlay? = null
    private var summaryCard: SessionSummaryCard? = null

    private var scrollMaskManager: ScrollMaskManager? = null

    fun setScrollMaskManager(m: ScrollMaskManager) { scrollMaskManager = m }

    private val lifecycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_CHILD_HARD_STOP -> {
                    scrollMaskManager?.onHardStop()
                }
                else -> {
                    val pkg = intent.getStringExtra("pkg") ?: return
                    when (action) {
                        ACTION_APP_FOREGROUND -> {
                            manager.onAppForeground(pkg)
                            showPill()
                            scrollMaskManager?.onSessionStart(pkg)
                        }
                        ACTION_APP_BACKGROUND -> {
                            scrollMaskManager?.onSessionEnd()
                            val finalState = manager.uiState.value
                            showSummary(finalState)
                            showSurvey { rating ->
                                if (rating != null) manager.recordSatisfaction(rating)
                            }
                            manager.onAppBackground()
                            hidePill()
                        }
                    }
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
        fun getHost(): OverlayHost = this@OverlayService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            applicationContext,
            ScrollShieldDatabase::class.java,
            "scrollshield.db"
        )
            .fallbackToDestructiveMigration()
            .build()
        sessionDao = database.sessionDao()
        val profileDao = database.profileDao()
        val prefsStore = UserPreferencesStore(applicationContext)
        profileManager = ProfileManager(profileDao, prefsStore)
        manager = AdCounterManager(applicationContext, sessionDao, profileManager)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_APP_FOREGROUND)
            addAction(ACTION_APP_BACKGROUND)
            addAction(ACTION_CHILD_HARD_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(lifecycleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(lifecycleReceiver, filter)
        }

        // Producer wiring: collect classifiedItems is a no-op consumer; the
        // AdCounterManager itself collects the SharedFlow. We expose a static
        // publish() seam for future producers / tests.
        scope.launch {
            AdCounterManager.classifiedItems.collect { /* observed by manager */ }
        }

        // Drive overlay visuals from UiState (pill text, status, bracket).
        uiCollectJob = scope.launch {
            manager.uiState.collectLatest { state ->
                pillOverlay?.render(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ensurePermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(lifecycleReceiver) } catch (_: Exception) {}
        for ((_, v) in views.toMap()) {
            try { windowManager.removeView(v) } catch (_: Exception) {}
        }
        views.clear()
        scope.cancel()
    }

    // ---- OverlayHost ----

    override fun showPill() {
        if (pillOverlay == null) {
            pillOverlay = AdCounterOverlay(this, manager) { /* onTap */
                showSummary(manager.uiState.value)
            }
        }
        val overlay = pillOverlay!!
        addView(overlay.view, overlay.layoutParams(), KEY_PILL)
        overlay.render(manager.uiState.value)
    }

    override fun hidePill() {
        removeView(KEY_PILL)
    }

    override fun showSummary(state: AdCounterManager.UiState) {
        if (summaryCard == null) {
            summaryCard = SessionSummaryCard(this, manager, sessionDao)
        }
        summaryCard!!.show(state, this)
    }

    override fun hideSummary() {
        summaryCard?.hide(this)
    }

    override fun showSurvey(onResult: (Int?) -> Unit) {
        // Delegated to SessionSummaryCard's nested SatisfactionSurveyView.
        if (summaryCard == null) {
            summaryCard = SessionSummaryCard(this, manager, sessionDao)
        }
        summaryCard!!.showSurvey(this, onResult)
    }

    override fun showLoading() {
        // Delegated to ScrollMaskManager's LoadingOverlay via OverlayHost interface
    }

    override fun hideLoading() {
        removeView(KEY_LOADING)
    }

    override fun showSkipFlash() {
        if (hasView(KEY_SKIP_FLASH)) return
        val flashView = View(this).apply {
            setBackgroundColor(0xCC_000000.toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        addView(flashView, params, KEY_SKIP_FLASH)
        Handler(Looper.getMainLooper()).postDelayed({ removeView(KEY_SKIP_FLASH) }, 300L)
    }

    override fun addView(view: View, params: WindowManager.LayoutParams, key: String) {
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        try {
            val existing = views[key]
            if (existing != null) {
                try { windowManager.updateViewLayout(existing, params) } catch (_: Exception) {}
                return
            }
            windowManager.addView(view, params)
            views[key] = view
        } catch (_: WindowManager.BadTokenException) {
        } catch (_: Exception) {
        }
    }

    override fun updateView(key: String, params: WindowManager.LayoutParams) {
        val v = views[key] ?: return
        try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
    }

    override fun removeView(key: String) {
        val v = views.remove(key) ?: return
        try { windowManager.removeView(v) } catch (_: Exception) {}
    }

    override fun hasView(key: String): Boolean = views.containsKey(key)

    override fun ensurePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
            return false
        }
        return true
    }

    // ---- Notification ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "ScrollShield Overlay",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("ScrollShield overlay")
                .setContentText("Counter overlay active")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("ScrollShield overlay")
                .setContentText("Counter overlay active")
                .setOngoing(true)
                .build()
        }
    }
}

/**
 * Reusable overlay host contract — implemented by OverlayService and consumed
 * by WI-09/10 mask overlays as well as the WI-08 counter pill / summary card.
 */
interface OverlayHost {
    fun showPill()
    fun hidePill()
    fun showSummary(state: AdCounterManager.UiState)
    fun hideSummary()
    fun showSurvey(onResult: (Int?) -> Unit)
    fun showLoading()
    fun hideLoading()
    fun showSkipFlash()
    fun addView(view: View, params: WindowManager.LayoutParams, key: String)
    fun updateView(key: String, params: WindowManager.LayoutParams)
    fun removeView(key: String)
    fun hasView(key: String): Boolean
    fun ensurePermission(): Boolean
}
