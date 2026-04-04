package com.scrollshield.classification

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaProjectionManager: MediaProjectionManager
) {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    private val revocationCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            release()
        }
    }

    fun attach(resultCode: Int, data: Intent) {
        release()
        val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
        mediaProjection = projection
        projection.registerCallback(revocationCallback, null)

        val (w, h) = getScreenDimensions()
        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        projection.createVirtualDisplay(
            "ScrollShieldClassification", w, h,
            context.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
    }

    suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
        val reader = imageReader ?: return@withContext null
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            return@withContext null
        } ?: return@withContext null

        try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * image.width
            val bmp = Bitmap.createBitmap(
                image.width + rowPadding / plane.pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)
            if (bmp.width > image.width) {
                val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
                bmp.recycle()
                cropped
            } else {
                bmp
            }
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
    }

    fun release() {
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    val isAvailable: Boolean get() = mediaProjection != null && imageReader != null

    private fun getScreenDimensions(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }
}
