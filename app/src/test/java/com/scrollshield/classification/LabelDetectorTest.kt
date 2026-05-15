package com.scrollshield.classification

import android.graphics.Rect
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import org.junit.Test

class LabelDetectorTest {

    private val detector = LabelDetector()

    @Test
    fun detectsEnglishSponsoredLabel() {
        val result = detector.detect(itemWithLabel("Sponsored"))
        check(result != null && result.classification == Classification.OFFICIAL_AD)
        check(result!!.confidence == 1.0f)
    }

    @Test
    fun detectsLocalisedJapaneseAdLabel() {
        val result = detector.detect(itemWithLabel("広告"))
        check(result != null && result.classification == Classification.OFFICIAL_AD)
    }

    @Test
    fun returnsNullForOrganic() {
        val result = detector.detect(itemWithLabel(null))
        check(result == null)
    }

    @Test
    fun returnsNullForUnknownLabel() {
        val result = detector.detect(itemWithLabel("hello there"))
        check(result == null)
    }

    private fun itemWithLabel(label: String?): FeedItem = FeedItem(
        id = "id", timestamp = 0, app = "test", creatorName = "creator",
        captionText = "caption", hashtags = emptyList(), labelText = label,
        screenRegion = Rect(), rawNodeDump = "", feedPosition = 0,
        accessibilityNodeId = null, detectedDurationMs = null,
        screenCapture = null,
    )
}
