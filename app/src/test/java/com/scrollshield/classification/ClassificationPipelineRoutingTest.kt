package com.scrollshield.classification

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.ScoringWeights
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.data.model.UserProfile
import com.scrollshield.error.DiagnosticLogger
import com.scrollshield.error.ErrorRecoveryManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassificationPipelineRoutingTest {

    @Test
    fun tier0aSignatureMatchShortCircuits() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sig = mockk<SignatureMatcher>()
        val lbl = mockk<LabelDetector>(relaxed = true)
        val vis = mockk<VisualClassifier>(relaxed = true)
        val txt = mockk<ContentClassifier>(relaxed = true)
        val skip = mockk<SkipDecisionEngine>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val diag = mockk<DiagnosticLogger>(relaxed = true)

        coEvery { sig.match(any()) } returns SignatureMatcher.MatchResult(
            classification = Classification.OFFICIAL_AD,
            confidence = 0.97f,
            topicVector = FloatArray(20),
            topicCategory = TopicCategory.TECH,
        )
        every { skip.decide(any(), any(), any(), any()) } returns
            com.scrollshield.data.model.SkipDecision.SKIP_AD

        val pipeline = ClassificationPipeline(context, sig, lbl, vis, txt, skip, err, diag)
        val result = pipeline.classify(feedItem(), profile())
        check(result.classification == Classification.OFFICIAL_AD)
        check(result.tier == 0)
        coVerify(exactly = 0) { lbl.detect(any()) }
        coVerify(exactly = 0) { vis.classify(any()) }
        coVerify(exactly = 0) { txt.classify(any()) }
    }

    @Test
    fun tier0bLabelMatchShortCircuits() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sig = mockk<SignatureMatcher>()
        val lbl = mockk<LabelDetector>()
        val vis = mockk<VisualClassifier>(relaxed = true)
        val txt = mockk<ContentClassifier>(relaxed = true)
        val skip = mockk<SkipDecisionEngine>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val diag = mockk<DiagnosticLogger>(relaxed = true)

        coEvery { sig.match(any()) } returns null
        every { lbl.detect(any()) } returns LabelDetector.DetectResult(
            classification = Classification.OFFICIAL_AD,
            confidence = 1.0f,
            topicVector = FloatArray(20),
            topicCategory = TopicCategory.TECH,
        )
        every { skip.decide(any(), any(), any(), any()) } returns
            com.scrollshield.data.model.SkipDecision.SKIP_AD

        val pipeline = ClassificationPipeline(context, sig, lbl, vis, txt, skip, err, diag)
        val result = pipeline.classify(feedItem(), profile())
        check(result.tier == 0)
        coVerify(exactly = 0) { vis.classify(any()) }
        coVerify(exactly = 0) { txt.classify(any()) }
    }

    @Test
    fun tier1VisualPrimaryWhenConfidenceHigh() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sig = mockk<SignatureMatcher>()
        val lbl = mockk<LabelDetector>()
        val vis = mockk<VisualClassifier>()
        val txt = mockk<ContentClassifier>(relaxed = true)
        val skip = mockk<SkipDecisionEngine>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val diag = mockk<DiagnosticLogger>(relaxed = true)

        coEvery { sig.match(any()) } returns null
        every { lbl.detect(any()) } returns null
        every { err.shouldSkipVisualClassification() } returns false
        every { err.shouldSkipTextClassification() } returns false
        coEvery { vis.classify(any()) } returns VisualClassifier.VisualResult(
            classification = Classification.INFLUENCER_PROMO,
            confidence = 0.85f,
            topicVector = FloatArray(20),
            topicCategory = TopicCategory.FASHION,
        )
        every { skip.decide(any(), any(), any(), any()) } returns
            com.scrollshield.data.model.SkipDecision.SKIP_AD

        val pipeline = ClassificationPipeline(context, sig, lbl, vis, txt, skip, err, diag)
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val result = pipeline.classify(feedItem(capture = bmp), profile())
        check(result.tier == 1)
        check(result.classification == Classification.INFLUENCER_PROMO)
        coVerify(exactly = 0) { txt.classify(any()) }
    }

    @Test
    fun tier1InconclusiveFallsToTier2() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sig = mockk<SignatureMatcher>()
        val lbl = mockk<LabelDetector>()
        val vis = mockk<VisualClassifier>()
        val txt = mockk<ContentClassifier>()
        val skip = mockk<SkipDecisionEngine>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val diag = mockk<DiagnosticLogger>(relaxed = true)

        coEvery { sig.match(any()) } returns null
        every { lbl.detect(any()) } returns null
        every { err.shouldSkipVisualClassification() } returns false
        every { err.shouldSkipTextClassification() } returns false
        coEvery { vis.classify(any()) } returns VisualClassifier.VisualResult(
            classification = Classification.UNKNOWN,
            confidence = 0.3f,
            topicVector = FloatArray(20),
            topicCategory = TopicCategory.TECH,
        )
        coEvery { txt.classify(any()) } returns ContentClassifier.ContentResult(
            classification = Classification.ENGAGEMENT_BAIT,
            confidence = 0.8f,
            topicVector = FloatArray(20),
            topicCategory = TopicCategory.NEWS,
        )
        every { skip.decide(any(), any(), any(), any()) } returns
            com.scrollshield.data.model.SkipDecision.SHOW

        val pipeline = ClassificationPipeline(context, sig, lbl, vis, txt, skip, err, diag)
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val result = pipeline.classify(feedItem(capture = bmp), profile())
        check(result.tier == 2) { "Expected tier 2, got ${result.tier}" }
        check(result.classification == Classification.ENGAGEMENT_BAIT)
    }

    @Test
    fun nullScreenCaptureRoutesToTier2() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sig = mockk<SignatureMatcher>()
        val lbl = mockk<LabelDetector>()
        val vis = mockk<VisualClassifier>(relaxed = true)
        val txt = mockk<ContentClassifier>()
        val skip = mockk<SkipDecisionEngine>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val diag = mockk<DiagnosticLogger>(relaxed = true)

        coEvery { sig.match(any()) } returns null
        every { lbl.detect(any()) } returns null
        every { err.shouldSkipVisualClassification() } returns false
        every { err.shouldSkipTextClassification() } returns false
        coEvery { txt.classify(any()) } returns ContentClassifier.ContentResult(
            classification = Classification.ORGANIC,
            confidence = 0.7f,
            topicVector = FloatArray(20),
            topicCategory = TopicCategory.MUSIC,
        )
        every { skip.decide(any(), any(), any(), any()) } returns
            com.scrollshield.data.model.SkipDecision.SHOW

        val pipeline = ClassificationPipeline(context, sig, lbl, vis, txt, skip, err, diag)
        val result = pipeline.classify(feedItem(capture = null), profile())
        check(result.tier == 2)
        coVerify(exactly = 0) { vis.classify(any()) }
    }

    @Test
    fun bothModelsUnavailableReturnsTier0Unknown() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val sig = mockk<SignatureMatcher>()
        val lbl = mockk<LabelDetector>()
        val vis = mockk<VisualClassifier>(relaxed = true)
        val txt = mockk<ContentClassifier>(relaxed = true)
        val skip = mockk<SkipDecisionEngine>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val diag = mockk<DiagnosticLogger>(relaxed = true)

        coEvery { sig.match(any()) } returns null
        every { lbl.detect(any()) } returns null
        every { err.shouldSkipVisualClassification() } returns true
        every { err.shouldSkipTextClassification() } returns true
        every { skip.decide(any(), any(), any(), any()) } returns
            com.scrollshield.data.model.SkipDecision.SHOW_LOW_CONF

        val pipeline = ClassificationPipeline(context, sig, lbl, vis, txt, skip, err, diag)
        val result = pipeline.classify(feedItem(), profile())
        check(result.tier == 0)
        check(result.classification == Classification.UNKNOWN)
        coVerify(exactly = 0) { vis.classify(any()) }
        coVerify(exactly = 0) { txt.classify(any()) }
    }

    private fun feedItem(capture: Bitmap? = null): FeedItem = FeedItem(
        id = "id", timestamp = 0, app = "test", creatorName = "c",
        captionText = "caption", hashtags = emptyList(), labelText = null,
        screenRegion = Rect(), rawNodeDump = "", feedPosition = 0,
        accessibilityNodeId = null, detectedDurationMs = null,
        screenCapture = capture,
    )

    private fun profile(): UserProfile = UserProfile(
        id = "p1", name = "T", isChildProfile = false,
        interestVector = FloatArray(20), blockedCategories = emptySet(),
        blockedClassifications = emptySet(), timeBudgets = emptyMap(),
        maskEnabled = true, counterEnabled = true, maskDismissable = false,
        pinProtected = false, parentPinHash = null,
        satisfactionHistory = emptyList(), scoringWeights = ScoringWeights(),
        createdAt = 0L, updatedAt = 0L, autoActivateSchedule = null,
    )
}
