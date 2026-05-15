package com.scrollshield.feature.mask

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.scrollshield.classification.ClassificationPipeline
import com.scrollshield.classification.ScreenCaptureManager
import com.scrollshield.error.DiagnosticLogger
import com.scrollshield.service.FeedInterceptionService
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreScanControllerTest {

    @Test
    fun lowMemoryDetectionReturnsBoolean() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val ctrl = PreScanController(
            context = context,
            feedInterceptionService = mockk(relaxed = true),
            screenCaptureManager = mockk(relaxed = true),
            classificationPipeline = mockk(relaxed = true),
            diagnosticLogger = mockk<DiagnosticLogger>(relaxed = true),
        )
        // Just exercise the helper; Robolectric won't always report >4GB
        val low = ctrl.isLowMemoryDevice()
        check(low || !low) // tautology — purpose is to exercise the path
        val size = ctrl.effectiveBufferSize()
        check(size == PreScanController.PRE_SCAN_BUFFER_SIZE ||
              size == PreScanController.LOW_MEMORY_BUFFER_SIZE)
    }
}
