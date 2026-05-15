package com.scrollshield.service

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import com.scrollshield.classification.ScreenCaptureManager
import com.scrollshield.error.ErrorRecoveryManager

class MediaProjectionHolder(
    private val mediaProjectionManager: MediaProjectionManager,
    private val screenCaptureManager: ScreenCaptureManager,
    private val errorRecoveryManager: ErrorRecoveryManager? = null
) {
    private var mediaProjection: MediaProjection? = null

    private val revocationCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mediaProjection = null
            screenCaptureManager.stop()
            errorRecoveryManager?.onMediaProjectionRevoked()
        }
    }

    fun setMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection?.stop()
        val projection = try {
            mediaProjectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e("MPH", "getMediaProjection failed", e)
            return
        }
        if (projection == null) {
            Log.e("MPH", "getMediaProjection returned null")
            return
        }
        projection.registerCallback(revocationCallback, null)
        mediaProjection = projection
        screenCaptureManager.start(projection)
        errorRecoveryManager?.onMediaProjectionGranted()
    }

    fun getMediaProjection(): MediaProjection? = mediaProjection

    fun stop() {
        mediaProjection?.stop()
        mediaProjection = null
        screenCaptureManager.stop()
    }
}
