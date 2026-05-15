package com.scrollshield.classification

import android.app.Application
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenCaptureManagerTest {

    @Test
    fun unstartedManagerIsUnavailable() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mgr = ScreenCaptureManager(context, mpm)
        check(!mgr.isAvailable)
    }

    @Test
    fun stopOnUnstartedIsSafe() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mgr = ScreenCaptureManager(context, mpm)
        // Should not throw
        mgr.stop()
    }
}
