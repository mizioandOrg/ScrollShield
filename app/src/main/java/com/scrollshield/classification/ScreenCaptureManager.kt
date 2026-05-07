package com.scrollshield.classification

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import com.scrollshield.error.DiagnosticLogger
import com.scrollshield.error.ErrorRecoveryManager
import com.scrollshield.service.ScreenCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScreenCaptureManager(
    @ApplicationContext private val context: Context,
    private val mediaProjectionManager: MediaProjectionManager,
    private val errorRecoveryManager: ErrorRecoveryManager? = null,
    private val diagnosticLogger: DiagnosticLogger? = null
) {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displayWidth = 0
    private var displayHeight = 0
    private var statusBarHeight = 0
    private var navBarHeight = 0

    val isAvailable: Boolean get() = mediaProjection != null && imageReader != null

    private val revocationCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            errorRecoveryManager?.onMediaProjectionRevoked()
        }
    }

    fun start(mediaProjection: MediaProjection) {
        stop() // clean up any prior state
        this.mediaProjection = mediaProjection
        mediaProjection.registerCallback(revocationCallback, null)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            displayWidth = bounds.width()
            displayHeight = bounds.height()
            val insets = wm.currentWindowMetrics.windowInsets
            statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top
            navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            displayWidth = dm.widthPixels
            displayHeight = dm.heightPixels
            statusBarHeight = 0
            navBarHeight = 0
        }
        val reader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScrollShieldCapture", displayWidth, displayHeight,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )
        } catch (e: SecurityException) {
            android.util.Log.e("SCM", "createVirtualDisplay denied", e)
            errorRecoveryManager?.onMediaProjectionRevoked()
            reader.close()
            imageReader = null
            this.mediaProjection = null
            return
        }
        val svcIntent = Intent(context, ScreenCaptureService::class.java)
            .setAction(ScreenCaptureService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svcIntent)
        } else {
            context.startService(svcIntent)
        }
    }

    fun stop() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        val svcIntent = Intent(context, ScreenCaptureService::class.java)
            .setAction(ScreenCaptureService.ACTION_STOP)
        context.startService(svcIntent)
    }

    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
        val captureStart = System.currentTimeMillis()
        val reader = imageReader ?: run {
            diagnosticLogger?.log(DiagnosticLogger.DiagnosticEvent.FrameCaptureFailed("no imageReader"))
            diagnosticLogger?.incrementFrameCaptureFail()
            return@withContext null
        }
        var image = try { reader.acquireLatestImage() } catch (_: Exception) {
            diagnosticLogger?.log(DiagnosticLogger.DiagnosticEvent.FrameCaptureFailed("acquireLatestImage exception"))
            diagnosticLogger?.incrementFrameCaptureFail()
            return@withContext null
        }
        if (image == null) {
            delay(50)
            image = try { reader.acquireLatestImage() } catch (_: Exception) {
                diagnosticLogger?.log(DiagnosticLogger.DiagnosticEvent.FrameCaptureFailed("acquireLatestImage retry exception"))
                diagnosticLogger?.incrementFrameCaptureFail()
                return@withContext null
            }
            if (image == null) {
                diagnosticLogger?.log(DiagnosticLogger.DiagnosticEvent.FrameCaptureFailed("acquireLatestImage returned null after retry"))
                diagnosticLogger?.incrementFrameCaptureFail()
                return@withContext null
            }
        }
        try {
            val nowNs = SystemClock.elapsedRealtimeNanos()
            if (nowNs - image.timestamp > 200_000_000L) {
                diagnosticLogger?.log(DiagnosticLogger.DiagnosticEvent.FrameCaptureFailed("stale frame"))
                diagnosticLogger?.incrementFrameCaptureFail()
                return@withContext null
            }
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * image.width
            val fullBitmap = Bitmap.createBitmap(
                image.width + rowPadding / plane.pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            try {
                fullBitmap.copyPixelsFromBuffer(plane.buffer)
                val cropTop = statusBarHeight
                val cropHeight = image.height - statusBarHeight - navBarHeight
                if (cropHeight <= 0 || cropTop + cropHeight > fullBitmap.height) {
                    diagnosticLogger?.log(DiagnosticLogger.DiagnosticEvent.FrameCaptureFailed("invalid crop dimensions"))
                    diagnosticLogger?.incrementFrameCaptureFail()
                    return@withContext null
                }
                val result = Bitmap.createBitmap(fullBitmap, 0, cropTop, image.width.coerceAtMost(fullBitmap.width), cropHeight)
                val captureTimeMs = System.currentTimeMillis() - captureStart
                diagnosticLogger?.log(DiagnosticLogger.DiagnosticEvent.FrameCaptureSuccess(captureTimeMs))
                diagnosticLogger?.incrementFrameCaptureSuccess()
                result
            } finally {
                fullBitmap.recycle()
            }
        } finally {
            image.close()
        }
    }
}
