package com.scrollshield.classification

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.SkipDecision
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.data.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassificationPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signatureMatcher: SignatureMatcher,
    private val labelDetector: LabelDetector,
    private val visualClassifier: VisualClassifier,
    private val contentClassifier: ContentClassifier,
    private val skipDecisionEngine: SkipDecisionEngine
) {
    suspend fun classify(feedItem: FeedItem, profile: UserProfile): ClassifiedItem {
        val startTime = System.currentTimeMillis()
        try {
            val thermalThrottled = isThermalThrottled()

            // Tier 0a — text + visual signature fast-path
            val t0a = signatureMatcher.match(feedItem)
            if (t0a != null && t0a.confidence > 0.95f) {
                return buildClassifiedItem(
                    feedItem, t0a.classification, t0a.confidence,
                    t0a.topicVector, t0a.topicCategory, tier = 0,
                    startTime, profile
                )
            }

            // Tier 0b — label fast-path
            val t0b = labelDetector.detect(feedItem)
            if (t0b != null) {
                return buildClassifiedItem(
                    feedItem, t0b.classification, t0b.confidence,
                    t0b.topicVector, t0b.topicCategory, tier = 0,
                    startTime, profile
                )
            }

            // If thermal throttled, skip Tier 1 and Tier 2
            if (thermalThrottled) {
                return buildClassifiedItem(
                    feedItem, Classification.UNKNOWN, 0.0f,
                    FloatArray(20), TopicCategory.fromIndex(0), tier = 0,
                    startTime, profile
                )
            }

            // Tier 1 — visual classification (PRIMARY)
            if (feedItem.screenCapture != null) {
                val t1 = visualClassifier.classify(feedItem.screenCapture)
                if (t1 != null && t1.confidence >= 0.7f) {
                    return buildClassifiedItem(
                        feedItem, t1.classification, t1.confidence,
                        t1.topicVector, t1.topicCategory, tier = 1,
                        startTime, profile
                    )
                }
            }

            // Tier 2 — deep text (supplementary fallback)
            val t2 = contentClassifier.classify(feedItem)
            return buildClassifiedItem(
                feedItem, t2.classification, t2.confidence,
                t2.topicVector, t2.topicCategory, tier = 2,
                startTime, profile
            )
        } catch (_: Exception) {
            return buildClassifiedItem(
                feedItem, Classification.UNKNOWN, 0.0f,
                FloatArray(20), TopicCategory.fromIndex(0), tier = 0,
                startTime, profile
            )
        }
    }

    private fun buildClassifiedItem(
        feedItem: FeedItem,
        classification: Classification,
        confidence: Float,
        topicVector: FloatArray,
        topicCategory: TopicCategory,
        tier: Int,
        startTime: Long,
        profile: UserProfile
    ): ClassifiedItem {
        val now = System.currentTimeMillis()
        val skipDecision = skipDecisionEngine.decide(classification, confidence, topicCategory, profile)
        return ClassifiedItem(
            feedItem = feedItem,
            classification = classification,
            confidence = confidence,
            topicVector = topicVector,
            topicCategory = topicCategory,
            tier = tier,
            latencyMs = now - startTime,
            classifiedAt = now,
            skipDecision = skipDecision
        )
    }

    private fun isThermalThrottled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val thermalStatus = pm.currentThermalStatus
            thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } catch (_: Exception) {
            false
        }
    }
}
