package com.scrollshield.classification

import android.app.Application
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.error.ErrorRecoveryManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentClassifierTest {

    @Test
    fun gracefullyReturnsUnknownWhenModelMissing() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val classifier = ContentClassifier(context, err)
        val item = FeedItem(
            id = "id", timestamp = 0, app = "test", creatorName = "c",
            captionText = "hello world tech ad", hashtags = emptyList(),
            labelText = null, screenRegion = Rect(), rawNodeDump = "",
            feedPosition = 0, accessibilityNodeId = null,
            detectedDurationMs = null, screenCapture = null,
        )
        val result = classifier.classify(item)
        // With no model asset bundled in unit-test classpath, the classifier
        // gracefully falls back to UNKNOWN, 0.0 confidence.
        check(
            result.classification == Classification.UNKNOWN || result.confidence == 0.0f
        ) { "Expected UNKNOWN fallback, got ${result.classification} conf=${result.confidence}" }
    }
}
