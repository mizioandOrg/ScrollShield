package com.scrollshield.error

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorRecoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticLogger: DiagnosticLogger
) {

    companion object {
        private const val NOTIF_CHANNEL_ID = "feed_interception"
        private const val NOTIF_PROTECTION_PAUSED_ID = 1003
        private const val NOTIF_GESTURE_FAILED_ID = 1004
        private const val NOTIF_VISUAL_PAUSED_ID = 1005
        private const val MAX_CONSECUTIVE_GESTURE_FAILURES = 3
    }

    enum class ModelDegradationState {
        FULL,
        VISUAL_ONLY,
        TEXT_ONLY,
        TIER0_ONLY
    }

    private val _consecutiveGestureFailures = AtomicInteger(0)
    private val _gestureDisabledForSession = AtomicBoolean(false)
    private val _visualModelAvailable = AtomicBoolean(true)
    private val _textModelAvailable = AtomicBoolean(true)
    private val _mediaProjectionAvailable = AtomicBoolean(true)
    private val _needsMediaProjectionReRequest = AtomicBoolean(false)

    fun getModelDegradationState(): ModelDegradationState {
        val visualOk = _visualModelAvailable.get() && _mediaProjectionAvailable.get()
        val textOk = _textModelAvailable.get()
        return when {
            visualOk && textOk -> ModelDegradationState.FULL
            !visualOk && textOk -> ModelDegradationState.TEXT_ONLY
            visualOk && !textOk -> ModelDegradationState.VISUAL_ONLY
            else -> ModelDegradationState.TIER0_ONLY
        }
    }

    fun onServiceDisconnected() {
        diagnosticLogger.log(
            DiagnosticLogger.DiagnosticEvent.ServiceDisconnected(System.currentTimeMillis())
        )
        showNotification(
            NOTIF_PROTECTION_PAUSED_ID,
            "ScrollShield protection paused",
            "Accessibility service disconnected."
        )
    }

    fun onServiceReconnected(): Boolean {
        diagnosticLogger.log(
            DiagnosticLogger.DiagnosticEvent.ServiceConnected(System.currentTimeMillis())
        )
        cancelNotification(NOTIF_PROTECTION_PAUSED_ID)
        return true
    }

    fun onVisualModelLoadFailed(error: String): ModelDegradationState {
        _visualModelAvailable.set(false)
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.VisualModelFailed(error))
        return getModelDegradationState()
    }

    fun onTextModelLoadFailed(error: String): ModelDegradationState {
        _textModelAvailable.set(false)
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.TextModelFailed(error))
        return getModelDegradationState()
    }

    fun onVisualModelLoaded(loadTimeMs: Long) {
        _visualModelAvailable.set(true)
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.VisualModelLoaded(loadTimeMs))
    }

    fun onTextModelLoaded(loadTimeMs: Long) {
        _textModelAvailable.set(true)
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.TextModelLoaded(loadTimeMs))
    }

    fun onGestureResult(success: Boolean, position: Int): Boolean {
        return if (success) {
            _consecutiveGestureFailures.set(0)
            diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.GestureSuccess(position))
            diagnosticLogger.incrementGestureSuccess()
            true
        } else {
            val failures = _consecutiveGestureFailures.incrementAndGet()
            diagnosticLogger.log(
                DiagnosticLogger.DiagnosticEvent.GestureFailed(position, failures)
            )
            diagnosticLogger.incrementGestureFail()
            if (failures >= MAX_CONSECUTIVE_GESTURE_FAILURES) {
                _gestureDisabledForSession.set(true)
                showNotification(
                    NOTIF_GESTURE_FAILED_ID,
                    "Scroll protection paused",
                    "Scroll protection paused \u2014 feed interaction failed"
                )
            }
            false
        }
    }

    fun isGestureDisabledForSession(): Boolean = _gestureDisabledForSession.get()

    fun onMediaProjectionRevoked() {
        _mediaProjectionAvailable.set(false)
        _needsMediaProjectionReRequest.set(true)
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.MediaProjectionRevoked)
        showNotification(
            NOTIF_VISUAL_PAUSED_ID,
            "Visual protection paused",
            "Visual protection paused \u2014 screen capture revoked"
        )
    }

    fun onMediaProjectionGranted() {
        _mediaProjectionAvailable.set(true)
        _needsMediaProjectionReRequest.set(false)
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.MediaProjectionGranted)
        cancelNotification(NOTIF_VISUAL_PAUSED_ID)
    }

    fun needsMediaProjectionReRequest(): Boolean = _needsMediaProjectionReRequest.get()

    fun shouldSkipVisualClassification(): Boolean =
        !_visualModelAvailable.get() || !_mediaProjectionAvailable.get()

    fun shouldSkipTextClassification(): Boolean = !_textModelAvailable.get()

    fun isDegraded(): Boolean =
        !_visualModelAvailable.get() || !_textModelAvailable.get() || !_mediaProjectionAvailable.get()

    fun onDatabaseCorruption(dbName: String) {
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.DatabaseCorruption(dbName))
    }

    fun onDatabaseRecreated(dbName: String) {
        diagnosticLogger.log(DiagnosticLogger.DiagnosticEvent.DatabaseRecreated(dbName))
    }

    fun resetSession() {
        _consecutiveGestureFailures.set(0)
        _gestureDisabledForSession.set(false)
        cancelNotification(NOTIF_GESTURE_FAILED_ID)
        diagnosticLogger.resetSessionCounters()
    }

    private fun showNotification(id: Int, title: String, text: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = Notification.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .build()
            nm.notify(id, notification)
        } catch (_: Exception) {
            // Notification channel may not exist yet; non-critical
        }
    }

    private fun cancelNotification(id: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(id)
        } catch (_: Exception) {}
    }
}
