package com.scrollshield.feature.mask

import android.graphics.Rect
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.SkipDecision
import com.scrollshield.data.model.TopicCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScanMapTest {

    @Test
    fun addItemMarksSkipIndex() = runTest {
        val map = ScanMapRuntime("s1", "app")
        map.addItem(0, classified(Classification.OFFICIAL_AD, SkipDecision.SKIP_AD))
        map.addItem(1, classified(Classification.ORGANIC, SkipDecision.SHOW))

        check(map.shouldSkip(0))
        check(!map.shouldSkip(1))
        check(map.size == 2)
        check(map.scanHead == 2)
    }

    @Test
    fun duplicateDetectionWorks() = runTest {
        val map = ScanMapRuntime("s1", "app")
        val item = classified(Classification.ORGANIC, SkipDecision.SHOW)
        map.addItem(0, item)
        check(map.isDuplicate(item))
    }

    @Test
    fun bufferRemainingComputedCorrectly() = runTest {
        val map = ScanMapRuntime("s1", "app")
        map.addItem(0, classified(Classification.ORGANIC, SkipDecision.SHOW))
        map.addItem(1, classified(Classification.ORGANIC, SkipDecision.SHOW))
        map.advanceUserHead(0)
        check(map.bufferRemaining() == 2)
    }

    @Test
    fun clearResetsState() = runTest {
        val map = ScanMapRuntime("s1", "app")
        map.addItem(0, classified(Classification.OFFICIAL_AD, SkipDecision.SKIP_AD))
        map.clear()
        check(map.size == 0)
        check(map.scanHead == 0)
        check(!map.shouldSkip(0))
    }

    private fun classified(c: Classification, sd: SkipDecision): ClassifiedItem {
        val fi = FeedItem(
            id = "fi-${System.nanoTime()}-${(0..9999).random()}",
            timestamp = 0, app = "app", creatorName = "c", captionText = "",
            hashtags = emptyList(), labelText = null, screenRegion = Rect(),
            rawNodeDump = "", feedPosition = 0, accessibilityNodeId = null,
            detectedDurationMs = null, screenCapture = null,
        )
        return ClassifiedItem(
            feedItem = fi, classification = c, confidence = 0.9f,
            topicVector = FloatArray(20), topicCategory = TopicCategory.COMEDY,
            tier = 0, latencyMs = 0L, classifiedAt = 0L, skipDecision = sd,
        )
    }
}
