package com.scrollshield.classification

import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.TopicCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelDetector @Inject constructor() {

    data class DetectResult(
        val classification: Classification,
        val confidence: Float,
        val topicVector: FloatArray,
        val topicCategory: TopicCategory
    )

    companion object {
        private val AD_LABELS = setOf(
            "sponsored", "ad", "promoted", "paid partnership",
            "publicité", "gesponsert", "patrocinado",
            "広告", "광고", "реклама", "إعلان",
            "anzeige", "reklam", "sponsorizzato", "promowane"
        )
    }

    fun detect(feedItem: FeedItem): DetectResult? {
        val label = feedItem.labelText?.trim()?.lowercase() ?: return null
        if (label in AD_LABELS) {
            return DetectResult(
                classification = Classification.OFFICIAL_AD,
                confidence = 1.0f,
                topicVector = FloatArray(20),
                topicCategory = TopicCategory.fromIndex(0)
            )
        }
        return null
    }
}
