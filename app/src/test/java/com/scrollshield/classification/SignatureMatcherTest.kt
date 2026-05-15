package com.scrollshield.classification

import android.graphics.Rect
import com.scrollshield.data.db.SignatureDao
import com.scrollshield.data.model.AdSignature
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.util.SimHash
import com.scrollshield.util.TextNormaliser
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignatureMatcherTest {

    @Test
    fun textSignatureMatchProducesOfficialAd() = runTest {
        val caption = "Sponsored. Try our new tech product today"
        val normalised = TextNormaliser.normalise(caption)
        val textHash = SimHash.hash(normalised)
        val dao = mockk<SignatureDao>()
        coEvery { dao.getActive(any()) } returns listOf(
            AdSignature(
                id = "sig-1",
                advertiser = "BrandX",
                category = "tech",
                captionHash = "abc",
                simHash = textHash,
                confidence = 0.99f,
                firstSeen = 0L,
                expires = Long.MAX_VALUE,
                source = "test",
                locale = null,
                visualHash = null,
            )
        )

        val matcher = SignatureMatcher(dao)
        val result = matcher.match(buildFeedItem(caption))

        check(result != null) { "Expected a match" }
        check(result.classification == Classification.OFFICIAL_AD) {
            "Expected OFFICIAL_AD, got ${result.classification}"
        }
        check(result.confidence >= 0.95f) { "Confidence too low: ${result.confidence}" }
    }

    @Test
    fun emptySignatureListReturnsNull() = runTest {
        val dao = mockk<SignatureDao>()
        coEvery { dao.getActive(any()) } returns emptyList()
        val matcher = SignatureMatcher(dao)
        val result = matcher.match(buildFeedItem("anything"))
        check(result == null)
    }

    private fun buildFeedItem(caption: String): FeedItem = FeedItem(
        id = "fid", timestamp = 0, app = "test",
        creatorName = "creator", captionText = caption,
        hashtags = emptyList(), labelText = null,
        screenRegion = Rect(), rawNodeDump = "",
        feedPosition = 0, accessibilityNodeId = null,
        detectedDurationMs = null, screenCapture = null,
    )
}
