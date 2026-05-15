package com.scrollshield.error

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticLogger @Inject constructor() {

    companion object {
        private const val TAG = "ScrollShield.Diag"
    }

    sealed class DiagnosticEvent {
        data class ServiceConnected(val timestamp: Long) : DiagnosticEvent()
        data class ServiceDisconnected(val timestamp: Long) : DiagnosticEvent()
        data class VisualModelLoaded(val loadTimeMs: Long) : DiagnosticEvent()
        data class VisualModelFailed(val error: String) : DiagnosticEvent()
        data class TextModelLoaded(val loadTimeMs: Long) : DiagnosticEvent()
        data class TextModelFailed(val error: String) : DiagnosticEvent()
        data class GestureSuccess(val position: Int) : DiagnosticEvent()
        data class GestureFailed(val position: Int, val consecutiveFailures: Int) : DiagnosticEvent()
        data class PreScanStarted(val bufferSize: Int) : DiagnosticEvent()
        data class PreScanCompleted(val itemsScanned: Int, val durationMs: Long) : DiagnosticEvent()
        data class PreScanTimeout(val elapsedMs: Long) : DiagnosticEvent()
        data class DatabaseCorruption(val dbName: String) : DiagnosticEvent()
        data class DatabaseRecreated(val dbName: String) : DiagnosticEvent()
        object MediaProjectionGranted : DiagnosticEvent()
        object MediaProjectionRevoked : DiagnosticEvent()
        data class FrameCaptureSuccess(val captureTimeMs: Long) : DiagnosticEvent()
        data class FrameCaptureFailed(val reason: String) : DiagnosticEvent()
        data class SessionClassificationSummary(
            val visualCount: Int,
            val textCount: Int,
            val tier0Count: Int
        ) : DiagnosticEvent()
    }

    @Volatile
    var visualClassificationCount: Int = 0
        private set

    @Volatile
    var textClassificationCount: Int = 0
        private set

    @Volatile
    var tier0Count: Int = 0
        private set

    @Volatile
    var frameCaptureSuccessCount: Int = 0
        private set

    @Volatile
    var frameCaptureFailCount: Int = 0
        private set

    @Volatile
    var gestureSuccessCount: Int = 0
        private set

    @Volatile
    var gestureFailCount: Int = 0
        private set

    fun log(event: DiagnosticEvent) {
        val message = when (event) {
            is DiagnosticEvent.ServiceConnected ->
                "type=ServiceConnected timestamp=${event.timestamp}"
            is DiagnosticEvent.ServiceDisconnected ->
                "type=ServiceDisconnected timestamp=${event.timestamp}"
            is DiagnosticEvent.VisualModelLoaded ->
                "type=VisualModelLoaded loadTimeMs=${event.loadTimeMs}"
            is DiagnosticEvent.VisualModelFailed ->
                "type=VisualModelFailed error=${event.error}"
            is DiagnosticEvent.TextModelLoaded ->
                "type=TextModelLoaded loadTimeMs=${event.loadTimeMs}"
            is DiagnosticEvent.TextModelFailed ->
                "type=TextModelFailed error=${event.error}"
            is DiagnosticEvent.GestureSuccess ->
                "type=GestureSuccess position=${event.position}"
            is DiagnosticEvent.GestureFailed ->
                "type=GestureFailed position=${event.position} consecutiveFailures=${event.consecutiveFailures}"
            is DiagnosticEvent.PreScanStarted ->
                "type=PreScanStarted bufferSize=${event.bufferSize}"
            is DiagnosticEvent.PreScanCompleted ->
                "type=PreScanCompleted itemsScanned=${event.itemsScanned} durationMs=${event.durationMs}"
            is DiagnosticEvent.PreScanTimeout ->
                "type=PreScanTimeout elapsedMs=${event.elapsedMs}"
            is DiagnosticEvent.DatabaseCorruption ->
                "type=DatabaseCorruption dbName=${event.dbName}"
            is DiagnosticEvent.DatabaseRecreated ->
                "type=DatabaseRecreated dbName=${event.dbName}"
            is DiagnosticEvent.MediaProjectionGranted ->
                "type=MediaProjectionGranted"
            is DiagnosticEvent.MediaProjectionRevoked ->
                "type=MediaProjectionRevoked"
            is DiagnosticEvent.FrameCaptureSuccess ->
                "type=FrameCaptureSuccess captureTimeMs=${event.captureTimeMs}"
            is DiagnosticEvent.FrameCaptureFailed ->
                "type=FrameCaptureFailed reason=${event.reason}"
            is DiagnosticEvent.SessionClassificationSummary ->
                "type=SessionClassificationSummary visualCount=${event.visualCount} textCount=${event.textCount} tier0Count=${event.tier0Count}"
        }
        Log.d(TAG, message)
    }

    fun incrementVisualClassification() {
        visualClassificationCount++
    }

    fun incrementTextClassification() {
        textClassificationCount++
    }

    fun incrementTier0Classification() {
        tier0Count++
    }

    fun incrementFrameCaptureSuccess() {
        frameCaptureSuccessCount++
    }

    fun incrementFrameCaptureFail() {
        frameCaptureFailCount++
    }

    fun incrementGestureSuccess() {
        gestureSuccessCount++
    }

    fun incrementGestureFail() {
        gestureFailCount++
    }

    fun resetSessionCounters() {
        visualClassificationCount = 0
        textClassificationCount = 0
        tier0Count = 0
        frameCaptureSuccessCount = 0
        frameCaptureFailCount = 0
        gestureSuccessCount = 0
        gestureFailCount = 0
    }

    fun logSessionSummary() {
        log(
            DiagnosticEvent.SessionClassificationSummary(
                visualCount = visualClassificationCount,
                textCount = textClassificationCount,
                tier0Count = tier0Count
            )
        )
    }
}
